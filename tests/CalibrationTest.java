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
        // Four features (eye x/y plus head x/y) recover a mixed linear mapping from nine targets.
        double[][] nine={{.5,.5},{.1,.12},{.9,.12},{.9,.88},{.1,.88},{.5,.12},{.9,.5},{.5,.88},{.1,.5}},features=new double[9][];
        for(int i=0;i<9;i++){double eyeX=.4+nine[i][0]*.2,eyeY=.45+nine[i][1]*.1,headX=Math.sin(i)*.05,headY=.7+Math.cos(i*1.7)*.04;
            features[i]=new double[]{eyeX-headX*.5,eyeY+headY*.3,headX,headY};nine[i]=new double[]{(features[i][0]+headX*.5-.4)/.2,(features[i][1]-headY*.3-.45)/.1};}
        Calibration head=Calibration.fit(features,nine);
        if(head.dimensions()!=4||head.error()>1e-6)throw new AssertionError("Four-feature fit failed: "+head.error());
        double[] mixed=Calibration.restore(head.values()).map(features[3]);
        if(Math.abs(mixed[0]-nine[3][0])>1e-6||Math.abs(mixed[1]-nine[3][1])>1e-6)throw new AssertionError("Four-feature round trip failed");
        try{head.map(.5,.5);throw new AssertionError("Feature count mismatch accepted");}catch(IllegalArgumentException expected){}
        for(int i=0;i<5;i++){raw[i][0]=.5;raw[i][1]=.5;}
        try{Calibration.fit(raw,targets);throw new AssertionError("Degenerate calibration accepted");}catch(IllegalArgumentException expected){}
        System.out.println("Calibration affine mapping and degeneracy checks passed");
    }
}
