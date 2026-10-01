package org.zektopic.zekals;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
    interface Listener { void sample(double[] point,long captured,String provider); void unavailable(String message); }
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
    CameraTracker(Context context,String requested,int displayRotation,Listener listener){
        this.context=context.getApplicationContext();this.requested=requested;this.listener=listener;
        displayDegrees=displayRotation==Surface.ROTATION_90?90:displayRotation==Surface.ROTATION_180?180:displayRotation==Surface.ROTATION_270?270:0;
        thread.start();handler=new Handler(thread.getLooper());handler.post(this::start);
    }
    private void fail(String message){if(active){listener.unavailable(message);close();}}
    private void start(){
        try {
            if(context.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){fail("Camera permission is required for eye tracking.");return;}
            if(requested.equals("NNAPI")){
                try{estimator=new OnnxEstimator(context,true);}catch(Exception | LinkageError unavailable){estimator=new MediaPipeEstimator(context,false);}
            }else estimator=new MediaPipeEstimator(context,requested.equals("GPU"));
            if(!active)return;
            CameraManager manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
            String selected=null;CameraCharacteristics characteristics=null;
            for(String id:manager.getCameraIdList()){
                CameraCharacteristics candidate=manager.getCameraCharacteristics(id);
                Integer facing=candidate.get(CameraCharacteristics.LENS_FACING);
                if(facing!=null&&facing==CameraCharacteristics.LENS_FACING_FRONT){selected=id;characteristics=candidate;break;}
            }
            if(selected==null){fail("No front-facing camera found.");return;}
            Integer orientation=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            rotation=((orientation==null?0:orientation)+displayDegrees)%360;
            var map=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(map==null)throw new IllegalStateException("No capture formats");
            Size[] sizes=map.getOutputSizes(ImageFormat.YUV_420_888);if(sizes==null||sizes.length==0)throw new IllegalStateException("No YUV capture support");
            Size size=sizes[0];for(Size candidate:sizes)if(Math.abs((long)candidate.getWidth()*candidate.getHeight()-320L*240)<Math.abs((long)size.getWidth()*size.getHeight()-320L*240))size=candidate;
            if(size.getWidth()>4096||size.getHeight()>4096)throw new IllegalArgumentException("Camera size exceeds bound");
            reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.YUV_420_888,2);
            reader.setOnImageAvailableListener(this::onImage,handler);
            manager.openCamera(selected,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice device){
                    if(!active){device.close();return;}camera=device;
                    try{device.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
                        @Override public void onConfigured(CameraCaptureSession value){
                            if(!active){value.close();return;}session=value;
                            try{CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(reader.getSurface());request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);session.setRepeatingRequest(request.build(),null,handler);}
                            catch(CameraAccessException error){fail("Camera stream could not start.");}
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession value){value.close();fail("Camera configuration failed.");}
                    },handler);}catch(CameraAccessException error){fail("Camera access failed.");}
                }
                @Override public void onDisconnected(CameraDevice device){device.close();fail("Camera disconnected.");}
                @Override public void onError(CameraDevice device,int error){device.close();fail("Camera is unavailable. Other input still works.");}
            },handler);
        }catch(Exception | LinkageError error){fail("Tracking unavailable. Check permission and installed model assets.");}
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
            double[] point;
            try{point=estimator.predict(bitmap,lastTimestamp);}catch(Exception accelerationFailure){
                // ONNX already retries its own model on CPU. Switching model families
                // here would change coordinates underneath the user's calibration.
                if(!(estimator instanceof MediaPipeEstimator))throw accelerationFailure;
                estimator.close();estimator=new MediaPipeEstimator(context,false);point=estimator.predict(bitmap,++lastTimestamp);
            }
            if(active)listener.sample(point,now,estimator.provider());
        }catch(Exception | LinkageError error){fail("Tracking lost. Use touch, keyboard or switch access.");}
    }
    @Override public synchronized void close(){
        if(closing)return;closing=true;active=false;
        handler.post(()->{if(session!=null)session.close();if(camera!=null)camera.close();if(reader!=null)reader.close();if(estimator!=null)estimator.close();if(bitmap!=null)bitmap.recycle();thread.quitSafely();});
    }
}
