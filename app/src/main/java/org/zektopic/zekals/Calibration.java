package org.zektopic.zekals;

/** Five-point least-squares calibration. Pure Java for deterministic host tests. */
public final class Calibration {
    private final double[][] coefficients;
    private Calibration(double[][] coefficients) { this.coefficients = coefficients; }
    public static Calibration fit(double[][] raw, double[][] targets) {
        if(raw.length < 5 || raw.length != targets.length) throw new IllegalArgumentException("Five targets required");
        double[][] result = new double[2][3];
        for(int axis=0;axis<2;axis++) {
            double[][] matrix = new double[3][4];
            for(int sample=0;sample<raw.length;sample++) {
                if(raw[sample].length!=2 || targets[sample].length!=2)throw new IllegalArgumentException("Invalid sample");
                for(double value:raw[sample])if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid sample");
                double[] row={raw[sample][0],raw[sample][1],1};
                for(int i=0;i<3;i++){for(int j=0;j<3;j++)matrix[i][j]+=row[i]*row[j];matrix[i][3]+=row[i]*targets[sample][axis];}
            }
            for(int i=0;i<3;i++) {
                int pivot=i;for(int j=i+1;j<3;j++)if(Math.abs(matrix[j][i])>Math.abs(matrix[pivot][i]))pivot=j;
                double[] swap=matrix[i];matrix[i]=matrix[pivot];matrix[pivot]=swap;
                double divisor=matrix[i][i];if(Math.abs(divisor)<1e-6)throw new IllegalArgumentException("Not enough movement between targets");
                for(int k=i;k<4;k++)matrix[i][k]/=divisor;
                for(int j=0;j<3;j++)if(j!=i){double multiplier=matrix[j][i];for(int k=i;k<4;k++)matrix[j][k]-=multiplier*matrix[i][k];}
            }
            for(int i=0;i<3;i++)result[axis][i]=matrix[i][3];
        }
        double error=0;
        for(int i=0;i<raw.length;i++)for(int axis=0;axis<2;axis++)error+=Math.pow(result[axis][0]*raw[i][0]+result[axis][1]*raw[i][1]+result[axis][2]-targets[i][axis],2);
        if(!Double.isFinite(error)||Math.sqrt(error/raw.length)>.12)throw new IllegalArgumentException("Calibration error too large");
        return new Calibration(result);
    }
    /** Six affine coefficients, row-major, for persisting a calibration between sessions. */
    public double[] values() { return new double[]{coefficients[0][0],coefficients[0][1],coefficients[0][2],coefficients[1][0],coefficients[1][1],coefficients[1][2]}; }
    public static Calibration restore(double[] values) {
        if(values==null||values.length!=6)throw new IllegalArgumentException("Invalid calibration");
        for(double value:values)if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid calibration");
        return new Calibration(new double[][]{{values[0],values[1],values[2]},{values[3],values[4],values[5]}});
    }
    public double[] map(double x,double y) {
        if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Invalid gaze");
        double[] result=new double[2];for(int i=0;i<2;i++)result[i]=Math.max(0,Math.min(1,coefficients[i][0]*x+coefficients[i][1]*y+coefficients[i][2]));return result;
    }
}
