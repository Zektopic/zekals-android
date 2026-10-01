package org.zektopic.zekals;
public final class CalibrationTest {
    public static void main(String[] args) {
        double[][] targets={{.1,.1},{.9,.1},{.5,.5},{.1,.9},{.9,.9}}, raw=new double[5][2];
        for(int i=0;i<5;i++)for(int axis=0;axis<2;axis++)raw[i][axis]=targets[i][axis]/2+.2;
        Calibration exact=Calibration.fit(raw,targets);
        if(exact.error()>1e-6)throw new AssertionError("Exact affine data reported an error");
        double[] result=exact.map(.45,.45);
        if(Math.abs(result[0]-.5)>1e-6||Math.abs(result[1]-.5)>1e-6)throw new AssertionError("Affine calibration failed");
        double[] restored=Calibration.decode(exact.encode()).map(.45,.45);
        if(Math.abs(restored[0]-.5)>1e-6||Math.abs(restored[1]-.5)>1e-6)throw new AssertionError("Calibration round trip failed");
        try{Calibration.decode("v2;2;0;1 0;1;0 NaN");throw new AssertionError("Malformed calibration restored");}catch(IllegalArgumentException expected){}
        double[][] noisy=new double[5][];for(int i=0;i<5;i++)noisy[i]=new double[]{raw[i][0]+(i%2==0?.01:-.01),raw[i][1]};
        double noise=Calibration.fit(noisy,targets).error();
        if(!(noise>0&&noise<.1))throw new AssertionError("Noisy fit should be accepted with a small error, got "+noise);
        // Per-axis features: x comes from eye x and head x, y from a different vertical cue; an unrelated,
        // constant feature (a missing blendshape) must not break the fit.
        double[][] nine={{.5,.5},{.1,.12},{.9,.12},{.9,.88},{.1,.88},{.5,.12},{.9,.5},{.5,.88},{.1,.5}},features=new double[9][];
        for(int i=0;i<9;i++){double headX=Math.sin(i)*.05,headY=.3+Math.cos(i*1.7)*.04,eyeX=.44+nine[i][0]*.12-headX*.4,lookY=nine[i][1]*.6-headY*.5;
            features[i]=new double[]{eyeX,.42+Math.sin(i*3)*.03,headX,headY,0,lookY,.3};}
        for(int i=0;i<9;i++){nine[i][0]=(features[i][0]+features[i][2]*.4-.44)/.12;nine[i][1]=(features[i][5]+features[i][3]*.5)/.6;}
        Calibration split=Calibration.fit(features,nine,new int[]{0,2},new int[]{1,3,5,6});
        if(split.dimensions()!=7||split.error()>1e-5)throw new AssertionError("Per-axis fit failed: "+split.error());
        double[] mixed=Calibration.decode(split.encode()).map(features[3]);
        if(Math.abs(mixed[0]-nine[3][0])>1e-5||Math.abs(mixed[1]-nine[3][1])>1e-5)throw new AssertionError("Per-axis round trip failed");
        try{split.map(.5,.5);throw new AssertionError("Feature count mismatch accepted");}catch(IllegalArgumentException expected){}
        for(int i=0;i<5;i++){raw[i][0]=.5;raw[i][1]=.5;}
        try{Calibration.fit(raw,targets);throw new AssertionError("Degenerate calibration accepted");}catch(IllegalArgumentException expected){}
        System.out.println("Calibration affine mapping and degeneracy checks passed");
    }
}
