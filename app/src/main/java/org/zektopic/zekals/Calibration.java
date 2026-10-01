package org.zektopic.zekals;

import java.util.Arrays;

/**
 * Least-squares affine calibration from gaze features (eye position, optionally head pose) to
 * screen fractions. Each axis can use its own subset of the features. Pure Java for host tests.
 */
public final class Calibration {
    private final int features;
    /** uses[axis] = feature indices for that axis; weights[axis] = their weights followed by the bias. */
    private final int[][] uses;
    private final double[][] weights;
    private final double error;
    private Calibration(int features, int[][] uses, double[][] weights, double error) { this.features = features; this.uses = uses; this.weights = weights; this.error = error; }
    /** Root-mean-square fitting error in screen fractions; the caller decides what is acceptable. */
    public double error() { return error; }
    /** Number of gaze features this calibration expects. */
    public int dimensions() { return features; }
    public static Calibration fit(double[][] raw, double[][] targets) {
        int[] all = new int[raw.length == 0 ? 0 : raw[0].length]; for (int i = 0; i < all.length; i++) all[i] = i;
        return fit(raw, targets, all, all);
    }
    /** Fits x from the features in useX and y from those in useY. */
    public static Calibration fit(double[][] raw, double[][] targets, int[] useX, int[] useY) {
        if(raw.length < 5 || raw.length != targets.length) throw new IllegalArgumentException("Five targets required");
        int features = raw[0].length;
        for(int sample=0;sample<raw.length;sample++) {
            if(raw[sample].length!=features || targets[sample].length!=2)throw new IllegalArgumentException("Invalid sample");
            for(double value:raw[sample])if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid sample");
        }
        // Identical samples mean the eyes did not move between targets.
        double spread = 0;
        for(int feature=0;feature<features;feature++){double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;for(double[] sample:raw){min=Math.min(min,sample[feature]);max=Math.max(max,sample[feature]);}spread=Math.max(spread,max-min);}
        if(spread<1e-6)throw new IllegalArgumentException("Not enough movement between targets");
        int[][] uses = {useX.clone(), useY.clone()};
        double[][] weights = new double[2][];
        for(int axis=0;axis<2;axis++) {
            int[] use = uses[axis]; int size = use.length + 1;
            if(raw.length < size + 1) throw new IllegalArgumentException("Too few targets for the features");
            for(int index:use)if(index<0||index>=features)throw new IllegalArgumentException("Invalid feature");
            double[][] matrix = new double[size][size+1];
            for(int sample=0;sample<raw.length;sample++) {
                double[] row = new double[size]; for(int i=0;i<use.length;i++)row[i]=raw[sample][use[i]]; row[use.length]=1;
                for(int i=0;i<size;i++){for(int j=0;j<size;j++)matrix[i][j]+=row[i]*row[j];matrix[i][size]+=row[i]*targets[sample][axis];}
            }
            // A tiny ridge keeps a feature that never varied (e.g. a missing blendshape) from making the system singular.
            for(int i=0;i<use.length;i++)matrix[i][i]+=1e-7;
            for(int i=0;i<size;i++) {
                int pivot=i;for(int j=i+1;j<size;j++)if(Math.abs(matrix[j][i])>Math.abs(matrix[pivot][i]))pivot=j;
                double[] swap=matrix[i];matrix[i]=matrix[pivot];matrix[pivot]=swap;
                double divisor=matrix[i][i];if(Math.abs(divisor)<1e-12)throw new IllegalArgumentException("Not enough movement between targets");
                for(int k=i;k<=size;k++)matrix[i][k]/=divisor;
                for(int j=0;j<size;j++)if(j!=i){double multiplier=matrix[j][i];for(int k=i;k<=size;k++)matrix[j][k]-=multiplier*matrix[i][k];}
            }
            weights[axis]=new double[size];for(int i=0;i<size;i++)weights[axis][i]=matrix[i][size];
        }
        Calibration result = new Calibration(features, uses, weights, 0);
        double error=0;
        for(int i=0;i<raw.length;i++)for(int axis=0;axis<2;axis++)error+=Math.pow(result.axis(axis,raw[i])-targets[i][axis],2);
        if(!Double.isFinite(error))throw new IllegalArgumentException("Calibration error too large");
        return new Calibration(features, uses, weights, Math.sqrt(error/raw.length));
    }
    /** Per-axis RMS error, for diagnostics. */
    public double[] axisErrors(double[][] raw, double[][] targets) {
        double[] errors = new double[2];
        for(int axis=0;axis<2;axis++){double sum=0;for(int i=0;i<raw.length;i++)sum+=Math.pow(axis(axis,raw[i])-targets[i][axis],2);errors[axis]=Math.sqrt(sum/raw.length);}
        return errors;
    }
    private double axis(int axis, double[] values) {
        double value=weights[axis][uses[axis].length];for(int i=0;i<uses[axis].length;i++)value+=weights[axis][i]*values[uses[axis][i]];return value;
    }
    /** Text form for preferences: feature count, then each axis's indices and weights. */
    public String encode() {
        StringBuilder text=new StringBuilder("v2;").append(features);
        for(int axis=0;axis<2;axis++){text.append(';');for(int i=0;i<uses[axis].length;i++)text.append(i==0?"":" ").append(uses[axis][i]);text.append(';');for(int i=0;i<weights[axis].length;i++)text.append(i==0?"":" ").append(weights[axis][i]);}
        return text.toString();
    }
    public static Calibration decode(String text) {
        String[] parts=text.split(";");
        if(parts.length!=6||!parts[0].equals("v2"))throw new IllegalArgumentException("Invalid calibration");
        int features=Integer.parseInt(parts[1]);int[][] uses=new int[2][];double[][] weights=new double[2][];
        for(int axis=0;axis<2;axis++){
            uses[axis]=Arrays.stream(parts[2+2*axis].trim().split(" ")).mapToInt(Integer::parseInt).toArray();
            weights[axis]=Arrays.stream(parts[3+2*axis].trim().split(" ")).mapToDouble(Double::parseDouble).toArray();
            if(weights[axis].length!=uses[axis].length+1)throw new IllegalArgumentException("Invalid calibration");
            for(int index:uses[axis])if(index<0||index>=features)throw new IllegalArgumentException("Invalid calibration");
            for(double value:weights[axis])if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid calibration");
        }
        return new Calibration(features,uses,weights,Double.NaN);
    }
    public double[] map(double... values) {
        if(values.length!=features)throw new IllegalArgumentException("Wrong number of gaze features");
        for(double value:values)if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid gaze");
        double[] result=new double[2];for(int i=0;i<2;i++)result[i]=Math.max(0,Math.min(1,axis(i,values)));return result;
    }
}
