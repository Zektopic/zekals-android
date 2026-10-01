package org.zektopic.zekals;

import android.content.Context;
import android.graphics.Bitmap;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.onnxruntime.providers.NNAPIFlags;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;

/** Optional custom model: mirrored RGB NCHW float32 -> normalized x,y,confidence. */
final class OnnxEstimator implements GazeEstimator {
    private final OrtEnvironment environment=OrtEnvironment.getEnvironment();
    private OrtSession session;
    private final File model;
    private final String input;
    private final int width,height;
    private final int[] pixels;
    private final float[] tensor;
    private String provider;
    OnnxEstimator(Context context,boolean nnapi) throws Exception {
        String checksum;
        try(InputStream stream=context.getAssets().open("gaze.sha256")){
            byte[] bytes=new byte[66];int count=stream.read(bytes);checksum=new String(bytes,0,Math.max(count,0),StandardCharsets.US_ASCII).trim();
        }
        if(!checksum.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid custom model checksum");
        MediaPipeEstimator.verify(context,"gaze.onnx",checksum);
        model=File.createTempFile("gaze-"+checksum+"-", ".onnx", context.getCacheDir());
        try {
        try(InputStream stream=context.getAssets().open("gaze.onnx");FileOutputStream output=new FileOutputStream(model)){
            byte[] buffer=new byte[8192];int count;while((count=stream.read(buffer))!=-1)output.write(buffer,0,count);
        }
        try{session=open(nnapi);provider=nnapi?"ONNX NNAPI requested (device assignment varies)":"ONNX CPU";}
        catch(Exception error){session=open(false);provider="ONNX CPU (NNAPI unavailable)";}
        input=session.getInputNames().iterator().next();
        TensorInfo info=(TensorInfo)session.getInputInfo().get(input).getInfo();long[] shape=info.getShape();
        if(session.getNumInputs()!=1||shape.length!=4||shape[0]!=1||shape[1]!=3||shape[2]<16||shape[2]>1024||shape[3]<16||shape[3]>1024||!info.type.toString().equals("FLOAT")){
            throw new IllegalArgumentException("Expected float input [1,3,H,W]");
        }
        if(session.getNumOutputs()!=1){throw new IllegalArgumentException("Expected one output");}
        TensorInfo outputInfo=(TensorInfo)session.getOutputInfo().values().iterator().next().getInfo();
        long[] outputShape=outputInfo.getShape();
        if(outputShape.length!=2||outputShape[0]!=1||outputShape[1]!=3||!outputInfo.type.toString().equals("FLOAT"))throw new IllegalArgumentException("Expected float output [1,3]");
        height=(int)shape[2];width=(int)shape[3];pixels=new int[width*height];tensor=new float[width*height*3];
        } catch(Exception | LinkageError error) { close(); throw error; }
    }
    private OrtSession open(boolean nnapi) throws Exception {
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
            options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);
            if(nnapi)options.addNnapi(EnumSet.of(NNAPIFlags.CPU_DISABLED));
            return environment.createSession(model.getAbsolutePath(),options);
        }
    }
    @Override public double[] predict(Bitmap source,long timestamp) throws Exception {
        Bitmap resized=Bitmap.createScaledBitmap(source,width,height,true);resized.getPixels(pixels,0,width,0,0,width,height);
        if(resized!=source)resized.recycle();
        int plane=width*height;
        for(int i=0;i<plane;i++){tensor[i]=((pixels[i]>>16)&255)/255f;tensor[plane+i]=((pixels[i]>>8)&255)/255f;tensor[2*plane+i]=(pixels[i]&255)/255f;}
        try{return run();}catch(Exception error){
            if(provider.equals("ONNX CPU"))throw error;
            session.close();session=open(false);provider="ONNX CPU";return run();
        }
    }
    private double[] run() throws Exception {
        try(OnnxTensor values=OnnxTensor.createTensor(environment,FloatBuffer.wrap(tensor),new long[]{1,3,height,width});
            OrtSession.Result result=session.run(Collections.singletonMap(input,values))){
            float[] output=((float[][])result.get(0).getValue())[0];
            double x=output[0],y=output[1],confidence=output[2];
            return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(confidence)&&x>=0&&x<=1&&y>=0&&y<=1&&confidence>=.7&&confidence<=1?new double[]{x,y}:null;
        }
    }
    @Override public String provider(){return provider;}
    @Override public void close(){if(session!=null){try{session.close();}catch(Exception ignored){}session=null;}if(model!=null)model.delete();}
}
