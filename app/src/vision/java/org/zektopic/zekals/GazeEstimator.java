package org.zektopic.zekals;

import android.content.Context;
import android.graphics.Bitmap;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;

interface GazeEstimator extends AutoCloseable {
    double[] predict(Bitmap bitmap,long timestamp) throws Exception;
    String provider();
    /** Normalized x,y pairs of the last eye landmarks (iris centers first), or null. */
    default float[] landmarks(){return null;}
    /** How closed the eyes are in the last frame, 0 open to 1 closed, or NaN when no face was seen. */
    default double blink(){return Double.NaN;}
    @Override void close();
}

final class MediaPipeEstimator implements GazeEstimator {
    private static final String CHECKSUM="64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff";
    private final FaceLandmarker landmarker;
    private final String provider;
    /** Iris centres, then eye corners and lids, then the nose tip; CameraTracker reads the outer corners for zoom. */
    static final int[] SHOWN={468,473,33,133,159,145,362,263,386,374,1};
    private volatile float[] lastLandmarks;
    private volatile double lastBlink=Double.NaN;
    MediaPipeEstimator(Context context,boolean gpu) throws Exception {
        verify(context,"face_landmarker.task",CHECKSUM);
        FaceLandmarker instance;
        String active;
        try { instance=create(context,gpu?Delegate.GPU:Delegate.CPU);active=gpu?"MediaPipe GPU":"MediaPipe CPU"; }
        catch(RuntimeException error){ if(!gpu)throw error;instance=create(context,Delegate.CPU);active="MediaPipe CPU (GPU unavailable)"; }
        landmarker=instance;provider=active;
    }
    private static FaceLandmarker create(Context context,Delegate delegate) {
        return FaceLandmarker.createFromOptions(context,FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").setDelegate(delegate).build())
            .setRunningMode(RunningMode.VIDEO).setNumFaces(1).setMinFaceDetectionConfidence(.6f)
            .setMinFacePresenceConfidence(.5f).setMinTrackingConfidence(.5f).setOutputFaceBlendshapes(true).build());
    }
    static void verify(Context context,String asset,String expected) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream input=context.getAssets().open(asset)) {
            byte[] buffer=new byte[8192];int read,total=0;
            while((read=input.read(buffer))!=-1){total+=read;if(total>128*1024*1024)throw new IllegalArgumentException("Model too large");digest.update(buffer,0,read);}
        }
        StringBuilder actual=new StringBuilder();for(byte value:digest.digest())actual.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
        if(!actual.toString().equals(expected))throw new IllegalArgumentException("Model checksum mismatch");
    }
    @Override public double[] predict(Bitmap bitmap,long timestamp) {
        // MPImage.close() recycles its bitmap, and the caller reuses this one for every frame.
        MPImage image=new BitmapImageBuilder(bitmap.copy(Bitmap.Config.ARGB_8888,false)).build();
        try {
            var result=landmarker.detectForVideo(image,timestamp);
            if(result.faceLandmarks().isEmpty()){lastLandmarks=null;lastBlink=Double.NaN;return null;}
            List<NormalizedLandmark> points=result.faceLandmarks().get(0);if(points.size()<478){lastLandmarks=null;return null;}
            float[] shown=new float[SHOWN.length*2];for(int i=0;i<SHOWN.length;i++){shown[2*i]=points.get(SHOWN[i]).x();shown[2*i+1]=points.get(SHOWN[i]).y();}lastLandmarks=shown;
            lastBlink=blinkScore(result,points);
            // Features (see FEATURES): eye x, eye y against the corners, head x/y, blendshape look x/y, lid opening.
            double w=bitmap.getWidth(),h=bitmap.getHeight();
            double x=0,y=0,open=0;
            int[][] eyes={{468,33,133,159,145},{473,362,263,386,374}};
            for(int[] eye:eyes){
                NormalizedLandmark iris=points.get(eye[0]),outer=points.get(eye[1]),inner=points.get(eye[2]),upper=points.get(eye[3]),lower=points.get(eye[4]);
                double width=Math.hypot((inner.x()-outer.x())*w,(inner.y()-outer.y())*h);
                double height=Math.abs(lower.y()-upper.y())*h;
                if(width<1e-3||height/width<.10)return null;
                x+=(iris.x()-Math.min(outer.x(),inner.x()))*w/width;
                // The lids follow the eye up and down, so vertical gaze is measured against the corners, which do not.
                y+=(iris.y()-(outer.y()+inner.y())/2)*h/width;
                open+=height/width;
            }
            x/=2;y/=2;open/=2;
            // Head turn: the nose tip moves against the eyes as the head yaws and pitches. Dividing by
            // the eye distance (in pixels) makes it independent of distance and of the camera crop.
            NormalizedLandmark left=points.get(33),right=points.get(263),nose=points.get(1);
            double span=Math.hypot((right.x()-left.x())*w,(right.y()-left.y())*h);if(span<1e-3)return null;
            double headX=(nose.x()-(left.x()+right.x())/2)*w/span,headY=(nose.y()-(left.y()+right.y())/2)*h/span;
            double[] look=lookScores(result);
            // Irises near the eye corners, or a nose far outside the face, mean a false detection.
            boolean ok=Double.isFinite(x)&&Double.isFinite(y)&&x>=.15&&x<=.85&&Math.abs(y)<=.5&&Double.isFinite(headX)&&Double.isFinite(headY)&&Math.abs(headX)<=.6&&headY>=0&&headY<=1;
            return ok?new double[]{x,y,headX,headY,look[0],look[1],open}:null;
        } finally { image.close(); }
    }
    /** Feature order returned by predict; MainActivity picks per-axis subsets and logs them all. */
    static final String[] FEATURES={"eyeX","eyeY","headX","headY","lookX","lookY","open"};
    /** Blendshape gaze: right minus left and down minus up, averaged over both eyes; 0 when unavailable. */
    private static double[] lookScores(com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult result){
        double[] look=new double[2];if(!result.faceBlendshapes().isPresent()||result.faceBlendshapes().get().isEmpty())return look;
        for(var category:result.faceBlendshapes().get().get(0)){
            String name=category.categoryName();double score=category.score()/2;
            if(name.equals("eyeLookInLeft")||name.equals("eyeLookOutRight"))look[0]+=score;
            if(name.equals("eyeLookOutLeft")||name.equals("eyeLookInRight"))look[0]-=score;
            if(name.startsWith("eyeLookDown"))look[1]+=score;
            if(name.startsWith("eyeLookUp"))look[1]-=score;
        }
        return look;
    }
    /** The blendshape model's eyeBlink scores; falls back to lid distance against eye width. */
    private static double blinkScore(com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult result,List<NormalizedLandmark> points){
        if(result.faceBlendshapes().isPresent()&&!result.faceBlendshapes().get().isEmpty()){
            double left=Double.NaN,right=Double.NaN;
            for(var category:result.faceBlendshapes().get().get(0)){
                if(category.categoryName().equals("eyeBlinkLeft"))left=category.score();
                if(category.categoryName().equals("eyeBlinkRight"))right=category.score();
            }
            if(!Double.isNaN(left)&&!Double.isNaN(right))return Math.min(left,right);
        }
        double open=0;int[][] eyes={{33,133,159,145},{362,263,386,374}};
        for(int[] eye:eyes){double width=Math.abs(points.get(eye[1]).x()-points.get(eye[0]).x());open+=width<1e-6?0:Math.abs(points.get(eye[3]).y()-points.get(eye[2]).y())/width/2;}
        return Math.max(0,Math.min(1,(.25-open)/.15));
    }
    @Override public String provider(){return provider;}
    @Override public float[] landmarks(){return lastLandmarks;}
    @Override public double blink(){return lastBlink;}
    @Override public void close(){landmarker.close();}
}
