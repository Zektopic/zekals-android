package org.zektopic.zekals;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Range;
import android.util.Rational;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.Collections;

/** One camera worker, acquireLatestImage, 10 Hz ceiling, no frame queue or network. */
final class CameraTracker implements AutoCloseable {
    /** Failures carry a language-pack key plus an English fallback. */
    interface Listener {
        /** blink: 0 open to 1 closed, NaN when no face; eyes can be closed while point is null. */
        void sample(double[] point,long captured,String provider,double blink);
        void unavailable(String key,String fallback);
        /** A small upright, mirrored copy of what the model sees, with the eye landmarks drawn on it. */
        void preview(Bitmap frame);
    }
    private static final long PREVIEW_MS=250,DIAGNOSTIC_MS=5000;
    private static final String TAG="zekALS";
    private final Context context;
    private final Listener listener;
    private final HandlerThread thread=new HandlerThread("zekals-camera");
    private final Handler handler;
    private final String requested;
    private final int displayDegrees;
    private volatile boolean active=true;
    private boolean closing;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private GazeEstimator estimator;
    private Bitmap bitmap;
    private ByteBuffer rgba;
    private int rotation;
    private long lastFrame,lastTimestamp;
    private String providerNote="";
    private long lastPreview,diagnosticStart;
    private int frames,faces;
    private final double[] sums=new double[7],squares=new double[7];
    private CameraCharacteristics cameraInfo;
    private CaptureRequest.Builder request;
    /**
     * Software face crop in full-frame pixels (width 0 = whole frame). A sensor crop would be cheaper, but
     * some vendor ISPs (MediaTek MT6768) crash on crops that follow a face.
     */
    private float cropX,cropY,cropWidth;
    /** Face lock: the crop stays put while locked; lockSpan is the eye distance it was framed for. */
    private boolean faceLocked,reframing;
    private int faceStreak;
    private double lockSpan;
    private int usedX,usedY,usedWidth,usedHeight,baseRotation;
    private long lastFace,searchStart;
    private int searchFrames,streak;
    private boolean faceFound;
    /** Some devices report the wrong sensor orientation; the rotation that found a face is kept for the process. */
    private static final int[] ROTATION_TRIES={0,180,90,270};
    private static int rotationTry;
    private final Paint dot=new Paint(Paint.ANTI_ALIAS_FLAG);
    CameraTracker(Context context,String requested,int displayRotation,Listener listener){
        this.context=context.getApplicationContext();this.requested=requested;this.listener=listener;
        displayDegrees=displayRotation==Surface.ROTATION_90?90:displayRotation==Surface.ROTATION_180?180:displayRotation==Surface.ROTATION_270?270:0;
        thread.start();handler=new Handler(thread.getLooper());handler.post(this::start);
    }
    private void fail(String key,String fallback,Throwable error){
        android.util.Log.w(TAG,"Camera tracking stopped: "+key,error);
        if(active){listener.unavailable(key,fallback);close();}
    }
    private void start(){
        try {
            if(context.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){fail("cameraPermission","Camera permission is required for eye tracking.",null);return;}
            if(requested.equals("NNAPI")){
                try{estimator=new OnnxEstimator(context,true);}
                catch(Exception | LinkageError unavailable){android.util.Log.i(TAG,"Custom ONNX model unavailable; using MediaPipe CPU",unavailable);providerNote=" (no ONNX model)";estimator=new MediaPipeEstimator(context,false);}
            }else estimator=new MediaPipeEstimator(context,requested.equals("GPU"));
            if(!active)return;
            CameraManager manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
            String selected=null;CameraCharacteristics characteristics=null;
            for(String id:manager.getCameraIdList()){
                CameraCharacteristics candidate=manager.getCameraCharacteristics(id);
                Integer facing=candidate.get(CameraCharacteristics.LENS_FACING);
                if(facing!=null&&facing==CameraCharacteristics.LENS_FACING_FRONT){selected=id;characteristics=candidate;break;}
            }
            if(selected==null){fail("noFrontCamera","No front-facing camera found.",null);return;}
            Integer orientation=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            baseRotation=((orientation==null?0:orientation)+displayDegrees)%360;rotation=(baseRotation+ROTATION_TRIES[rotationTry])%360;cameraInfo=characteristics;
            var map=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(map==null)throw new IllegalStateException("No capture formats");
            Size[] sizes=map.getOutputSizes(ImageFormat.YUV_420_888);if(sizes==null||sizes.length==0)throw new IllegalStateException("No YUV capture support");
            // The landmark model crops the face from whatever image it receives, so a higher capture
            // resolution gives the eyes more real pixels; the software crop below keeps the cost down.
            Size size=sizes[0];for(Size candidate:sizes)if(Math.abs((long)candidate.getWidth()*candidate.getHeight()-1280L*960)<Math.abs((long)size.getWidth()*size.getHeight()-1280L*960))size=candidate;
            android.util.Log.i(TAG,"Camera "+selected+" capture "+size+", sensor "+orientation+"°, display "+displayDegrees+"°, image rotation "+rotation+"°");
            if(size.getWidth()>4096||size.getHeight()>4096)throw new IllegalArgumentException("Camera size exceeds bound");
            reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.YUV_420_888,2);
            reader.setOnImageAvailableListener(this::onImage,handler);
            manager.openCamera(selected,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice device){
                    if(!active){device.close();return;}camera=device;
                    try{device.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
                        @Override public void onConfigured(CameraCaptureSession value){
                            if(!active){value.close();return;}session=value;
                            try{request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(reader.getSurface());configureExposure();session.setRepeatingRequest(request.build(),null,handler);searchStart=SystemClock.uptimeMillis();}
                            catch(CameraAccessException error){fail("cameraFailed","The camera stopped working. Other input still works.",error);}
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession value){value.close();fail("cameraFailed","The camera stopped working. Other input still works.",null);}
                    },handler);}catch(CameraAccessException error){fail("cameraFailed","The camera stopped working. Other input still works.",error);}
                }
                @Override public void onDisconnected(CameraDevice device){device.close();fail("cameraFailed","The camera stopped working. Other input still works.",null);}
                @Override public void onError(CameraDevice device,int error){device.close();fail("cameraFailed","The camera stopped working. Other input still works.",new IllegalStateException("CameraDevice error "+error));}
            },handler);
        }catch(Exception | LinkageError error){fail("trackingUnavailable","Eye tracking could not start. Other input still works.",error);}
    }
    private void onImage(ImageReader source){
        if(!active)return;
        try(Image image=source.acquireLatestImage()){
            if(image==null)return;long now=SystemClock.uptimeMillis();int interval=100;
            if(android.os.Build.VERSION.SDK_INT>=29){android.os.PowerManager power=(android.os.PowerManager)context.getSystemService(Context.POWER_SERVICE);if(power!=null&&power.getCurrentThermalStatus()>=android.os.PowerManager.THERMAL_STATUS_MODERATE)interval=200;}
            if(now-lastFrame<interval)return;lastFrame=now;
            int width=image.getWidth(),height=image.getHeight(),outputWidth=(rotation==90||rotation==270)?height:width,outputHeight=(rotation==90||rotation==270)?width:height;
            if(bitmap==null||bitmap.getWidth()!=outputWidth||bitmap.getHeight()!=outputHeight){if(bitmap!=null)bitmap.recycle();bitmap=Bitmap.createBitmap(outputWidth,outputHeight,Bitmap.Config.ARGB_8888);rgba=ByteBuffer.allocateDirect(width*height*4);}
            Image.Plane[] planes=image.getPlanes();if(planes.length!=3)throw new IllegalStateException("Unsupported camera planes");
            if(!NativeCore.rgba(planes[0].getBuffer().slice(),planes[0].getRowStride(),planes[0].getPixelStride(),planes[1].getBuffer().slice(),planes[1].getRowStride(),planes[1].getPixelStride(),planes[2].getBuffer().slice(),planes[2].getRowStride(),planes[2].getPixelStride(),width,height,rotation,true,rgba))throw new IllegalArgumentException("Invalid plane layout");
            rgba.rewind();bitmap.copyPixelsFromBuffer(rgba);lastTimestamp=Math.max(lastTimestamp+1,now);
            Bitmap frame=faceFrame();
            double[] point;
            try{point=estimator.predict(frame,lastTimestamp);}catch(Exception accelerationFailure){
                // ONNX already retries its own model on CPU. Switching model families
                // here would change coordinates underneath the user's calibration.
                if(!(estimator instanceof MediaPipeEstimator))throw accelerationFailure;
                estimator.close();estimator=new MediaPipeEstimator(context,false);point=estimator.predict(frame,++lastTimestamp);
            }
            if(active)listener.sample(point,now,estimator.provider()+providerNote,estimator.blink());
            followFace(point==null?null:estimator.landmarks(),now);
            searchRotation(point!=null,now);
            diagnose(point,now,width,height);
            if(now-lastPreview>=PREVIEW_MS&&active){lastPreview=now;listener.preview(preview(frame,point==null?null:estimator.landmarks()));}
            if(frame!=bitmap)frame.recycle();
        }catch(Exception | LinkageError error){fail("trackingLost","Eye tracking stopped. Use touch, keyboard or switch access.",error);}
    }
    /** Logs detection rate and feature spread every few seconds; never message text or images. */
    private void diagnose(double[] point,long now,int width,int height){
        if(diagnosticStart==0)diagnosticStart=now;
        frames++;if(point!=null){faces++;for(int i=0;i<Math.min(sums.length,point.length);i++){sums[i]+=point[i];squares[i]+=point[i]*point[i];}}
        if(now-diagnosticStart<DIAGNOSTIC_MS)return;
        StringBuilder stats=new StringBuilder();String[] names=MediaPipeEstimator.FEATURES;
        for(int i=0;i<names.length&&faces>0;i++){double mean=sums[i]/faces,sd=faces<2?0:Math.sqrt(Math.max(0,squares[i]/faces-mean*mean));stats.append(String.format(java.util.Locale.ROOT,", %s %.3f±%.3f",names[i],mean,sd));}
        float zoom=usedWidth<=0?1:bitmap.getWidth()/(float)usedWidth;
        android.util.Log.i(TAG,String.format(java.util.Locale.ROOT,"tracking %dx%d rot %d zoom %.2fx: %d frames, %d with eyes%s, %s",width,height,rotation,zoom,frames,faces,stats,estimator.provider()));
        diagnosticStart=now;frames=0;faces=0;java.util.Arrays.fill(sums,0);java.util.Arrays.fill(squares,0);
    }
    private void configureExposure(){
        // Face-priority metering stops a bright lamp behind the user from leaving the face too dark to find.
        boolean facePriority=contains(cameraInfo.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES),CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY);
        if(facePriority){request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);request.set(CaptureRequest.CONTROL_SCENE_MODE,CaptureRequest.CONTROL_SCENE_MODE_FACE_PRIORITY);}
        else request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        if(contains(cameraInfo.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES),CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE))request.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE);
        Range<Integer> range=cameraInfo.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);Rational step=cameraInfo.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        int ev=0;if(range!=null&&step!=null&&step.floatValue()>0){ev=Math.max(range.getLower(),Math.min(range.getUpper(),Math.round(.5f/step.floatValue())));request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,ev);}
        android.util.Log.i(TAG,"Exposure: face priority "+facePriority+", compensation "+ev+" steps");
    }
    private static boolean contains(int[] values,int wanted){if(values!=null)for(int value:values)if(value==wanted)return true;return false;}
    /** The image handed to the model: the whole frame, or the face crop once a face is being followed. */
    private Bitmap faceFrame(){
        int width=bitmap.getWidth(),height=bitmap.getHeight();
        if(cropWidth<=0||cropWidth>=width-1){usedX=0;usedY=0;usedWidth=width;usedHeight=height;return bitmap;}
        usedWidth=Math.round(cropWidth);usedHeight=Math.round(cropWidth*height/width);
        usedX=Math.max(0,Math.min(width-usedWidth,Math.round(cropX)));usedY=Math.max(0,Math.min(height-usedHeight,Math.round(cropY)));
        return Bitmap.createBitmap(bitmap,usedX,usedY,usedWidth,usedHeight);
    }
    /**
     * Locks the camera onto the face: once the face has been steady for a few frames the zoomed crop freezes
     * on it. It only re-frames (gliding, then locking again) when the eyes near the edge of the frame or the
     * face moves much closer or further away, and it holds the lock through blinks and brief turns.
     */
    private void followFace(float[] marks,long now){
        int width=bitmap.getWidth(),height=bitmap.getHeight();
        if(marks==null||marks.length<16){
            faceStreak=0;
            if(now-lastFace>(faceLocked?3000:1500)&&cropWidth>0){cropWidth=0;faceLocked=false;reframing=false;android.util.Log.i(TAG,"Face lock released");}
            return;
        }
        lastFace=now;faceStreak++;
        double lx=usedX+marks[4]*usedWidth,ly=usedY+marks[5]*usedHeight,rx=usedX+marks[14]*usedWidth,ry=usedY+marks[15]*usedHeight;
        double span=Math.hypot(rx-lx,ry-ly);if(span<4)return;
        // Centre a little below the eyes so the nose and mouth stay in frame.
        double wantWidth=Math.max(width/3.0,Math.min(width,span/.25)),wantHeight=wantWidth*height/width;
        double wantX=(lx+rx)/2-wantWidth/2,wantY=(ly+ry)/2+span*.25-wantHeight/2;
        if(cropWidth<=0){
            if(faceStreak<3)return;
            cropWidth=(float)wantWidth;cropX=(float)wantX;cropY=(float)wantY;faceLocked=true;lockSpan=span;
            android.util.Log.i(TAG,String.format(java.util.Locale.ROOT,"Face locked at %.1fx zoom",width/wantWidth));return;
        }
        if(faceLocked&&!reframing){
            double eyeX=((lx+rx)/2-cropX)/cropWidth,eyeY=((ly+ry)/2-cropY)/(cropWidth*height/width);
            boolean far=eyeX<.25||eyeX>.75||eyeY<.2||eyeY>.7||span<lockSpan*.65||span>lockSpan*1.5;
            if(!far)return;
            reframing=true;android.util.Log.i(TAG,"Re-framing the face");
        }
        float ease=.25f;
        cropX+=(float)((wantX-cropX)*ease);cropY+=(float)((wantY-cropY)*ease);cropWidth+=(float)((wantWidth-cropWidth)*ease);
        if(Math.abs(wantX-cropX)<cropWidth*.02&&Math.abs(wantY-cropY)<cropWidth*.02&&Math.abs(wantWidth-cropWidth)<cropWidth*.03){reframing=false;faceLocked=true;lockSpan=span;}
    }
    /** Until a face is found, tries the other image rotations in case the device reports its sensor angle wrongly. */
    private void searchRotation(boolean face,long now){
        // A real face is seen frame after frame; a false detection in a ceiling or a poster is not. The
        // sensor-reported rotation is right on most devices, so it needs less evidence and gets more time.
        boolean sensorRotation=ROTATION_TRIES[rotationTry]==0;
        if(face){
            streak++;
            if(!faceFound&&streak>=(sensorRotation?3:8)){faceFound=true;android.util.Log.i(TAG,"Face found with image rotation "+rotation+"° (sensor-based "+baseRotation+"°)");}
            return;
        }
        streak=0;
        if(faceFound){
            if(!sensorRotation&&now-lastFace>10000){faceFound=false;rotationTry=0;rotation=baseRotation;searchStart=now;searchFrames=0;cropWidth=0;faceLocked=false;reframing=false;android.util.Log.i(TAG,"Face lost; back to the sensor rotation "+rotation+"°");}
            return;
        }
        if(searchStart==0||++searchFrames<12||now-searchStart<(sensorRotation?6000:2500))return;
        rotationTry=(rotationTry+1)%ROTATION_TRIES.length;rotation=(baseRotation+ROTATION_TRIES[rotationTry])%360;searchStart=now;searchFrames=0;
        cropWidth=0;faceLocked=false;reframing=false;
        android.util.Log.i(TAG,"No face yet; trying image rotation "+rotation+"°");
    }
    private Bitmap preview(Bitmap source,float[] landmarks){
        int width=200,height=Math.max(1,Math.round(200f*source.getHeight()/source.getWidth()));
        Bitmap frame=Bitmap.createScaledBitmap(source,width,height,true);
        if(landmarks!=null){
            Canvas canvas=new Canvas(frame);dot.setStyle(Paint.Style.FILL);
            for(int i=0;i+1<landmarks.length;i+=2){dot.setColor(i<4?Color.rgb(188,235,207):Color.rgb(255,221,133));canvas.drawCircle(landmarks[i]*width,landmarks[i+1]*height,i<4?3.5f:2f,dot);}
        }
        // A green border shows the camera is locked onto the face.
        if(faceLocked&&!reframing){Canvas canvas=new Canvas(frame);dot.setStyle(Paint.Style.STROKE);dot.setStrokeWidth(4);dot.setColor(Color.rgb(188,235,207));canvas.drawRect(2,2,width-2,height-2,dot);}
        return frame;
    }
    @Override public synchronized void close(){
        if(closing)return;closing=true;active=false;
        handler.post(()->{if(session!=null)session.close();if(camera!=null)camera.close();if(reader!=null)reader.close();if(estimator!=null)estimator.close();if(bitmap!=null)bitmap.recycle();thread.quitSafely();});
    }
}
