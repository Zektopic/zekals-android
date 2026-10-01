package org.zektopic.zekals;

/**
 * Least-squares affine calibration from gaze features (eye position, optionally head pose) to
 * screen fractions. Pure Java for deterministic host tests.
 */
public final class Calibration {
    /** coefficients[axis] = feature weights followed by the bias. */
    private final double[][] coefficients;
    private final double error;
    private Calibration(double[][] coefficients, double error) { this.coefficients = coefficients; this.error = error; }
    /** Root-mean-square fitting error in screen fractions; the caller decides what is acceptable. */
    public double error() { return error; }
    /** Number of gaze features this calibration expects. */
    public int dimensions() { return coefficients[0].length - 1; }
    public static Calibration fit(double[][] raw, double[][] targets) {
        if(raw.length < 5 || raw.length != targets.length) throw new IllegalArgumentException("Five targets required");
        int features = raw[0].length, size = features + 1;
        if(features < 2 || raw.length < size + 1) throw new IllegalArgumentException("Invalid sample");
        for(int sample=0;sample<raw.length;sample++) {
            if(raw[sample].length!=features || targets[sample].length!=2)throw new IllegalArgumentException("Invalid sample");
            for(double value:raw[sample])if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid sample");
        }
        double[][] result = new double[2][size];
        for(int axis=0;axis<2;axis++) {
            double[][] matrix = new double[size][size+1];
            for(int sample=0;sample<raw.length;sample++) {
                double[] row = new double[size]; System.arraycopy(raw[sample],0,row,0,features); row[features] = 1;
                for(int i=0;i<size;i++){for(int j=0;j<size;j++)matrix[i][j]+=row[i]*row[j];matrix[i][size]+=row[i]*targets[sample][axis];}
            }
            for(int i=0;i<size;i++) {
                int pivot=i;for(int j=i+1;j<size;j++)if(Math.abs(matrix[j][i])>Math.abs(matrix[pivot][i]))pivot=j;
                double[] swap=matrix[i];matrix[i]=matrix[pivot];matrix[pivot]=swap;
                double divisor=matrix[i][i];if(Math.abs(divisor)<1e-9)throw new IllegalArgumentException("Not enough movement between targets");
                for(int k=i;k<=size;k++)matrix[i][k]/=divisor;
                for(int j=0;j<size;j++)if(j!=i){double multiplier=matrix[j][i];for(int k=i;k<=size;k++)matrix[j][k]-=multiplier*matrix[i][k];}
            }
            for(int i=0;i<size;i++)result[axis][i]=matrix[i][size];
        }
        double error=0;
        for(int i=0;i<raw.length;i++)for(int axis=0;axis<2;axis++)error+=Math.pow(apply(result[axis],raw[i])-targets[i][axis],2);
        if(!Double.isFinite(error))throw new IllegalArgumentException("Calibration error too large");
        return new Calibration(result,Math.sqrt(error/raw.length));
    }
    private static double apply(double[] weights, double[] features) {
        double value=weights[features.length];for(int i=0;i<features.length;i++)value+=weights[i]*features[i];return value;
    }
    /** Both axes' weights and biases, for persisting a calibration between sessions. */
    public double[] values() {
        int size=coefficients[0].length;double[] values=new double[2*size];
        System.arraycopy(coefficients[0],0,values,0,size);System.arraycopy(coefficients[1],0,values,size,size);return values;
    }
    public static Calibration restore(double[] values) {
        if(values==null||values.length<6||values.length%2!=0)throw new IllegalArgumentException("Invalid calibration");
        for(double value:values)if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid calibration");
        int size=values.length/2;double[][] coefficients=new double[2][size];
        System.arraycopy(values,0,coefficients[0],0,size);System.arraycopy(values,size,coefficients[1],0,size);
        return new Calibration(coefficients,Double.NaN);
    }
    public double[] map(double... features) {
        if(features.length!=dimensions())throw new IllegalArgumentException("Wrong number of gaze features");
        for(double value:features)if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid gaze");
        double[] result=new double[2];for(int i=0;i<2;i++)result[i]=Math.max(0,Math.min(1,apply(coefficients[i],features)));return result;
    }
}
