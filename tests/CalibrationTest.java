package org.zektopic.zekals;
public final class CalibrationTest {
    public static void main(String[] args) {
        double[][] targets={{.1,.1},{.9,.1},{.5,.5},{.1,.9},{.9,.9}}, raw=new double[5][2];
        for(int i=0;i<5;i++)for(int axis=0;axis<2;axis++)raw[i][axis]=targets[i][axis]/2+.2;
        double[] result=Calibration.fit(raw,targets).map(.45,.45);
        if(Math.abs(result[0]-.5)>1e-8||Math.abs(result[1]-.5)>1e-8)throw new AssertionError("Affine calibration failed");
        for(int i=0;i<5;i++){raw[i][0]=.5;raw[i][1]=.5;}
        try{Calibration.fit(raw,targets);throw new AssertionError("Degenerate calibration accepted");}catch(IllegalArgumentException expected){}
        System.out.println("Calibration affine mapping and degeneracy checks passed");
    }
}
