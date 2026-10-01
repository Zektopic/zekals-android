package org.zektopic.zekals;
public final class CalibrationTest {
    public static void main(String[] args) {
        double[][] targets={{.1,.1},{.9,.1},{.5,.5},{.1,.9},{.9,.9}}, raw=new double[5][2];
        for(int i=0;i<5;i++)for(int axis=0;axis<2;axis++)raw[i][axis]=targets[i][axis]/2+.2;
        Calibration exact=Calibration.fit(raw,targets);
        if(exact.error()>1e-9)throw new AssertionError("Exact affine data reported an error");
        double[] result=exact.map(.45,.45);
        if(Math.abs(result[0]-.5)>1e-8||Math.abs(result[1]-.5)>1e-8)throw new AssertionError("Affine calibration failed");
        double[] restored=Calibration.restore(Calibration.fit(raw,targets).values()).map(.45,.45);
        if(Math.abs(restored[0]-.5)>1e-8||Math.abs(restored[1]-.5)>1e-8)throw new AssertionError("Calibration round trip failed");
        try{Calibration.restore(new double[]{1,0,0,0,1,Double.NaN});throw new AssertionError("Non-finite calibration restored");}catch(IllegalArgumentException expected){}
        double[][] noisy=new double[5][];for(int i=0;i<5;i++)noisy[i]=new double[]{raw[i][0]+(i%2==0?.01:-.01),raw[i][1]};
        double noise=Calibration.fit(noisy,targets).error();
        if(!(noise>0&&noise<.1))throw new AssertionError("Noisy fit should be accepted with a small error, got "+noise);
        for(int i=0;i<5;i++){raw[i][0]=.5;raw[i][1]=.5;}
        try{Calibration.fit(raw,targets);throw new AssertionError("Degenerate calibration accepted");}catch(IllegalArgumentException expected){}
        System.out.println("Calibration affine mapping and degeneracy checks passed");
    }
}
