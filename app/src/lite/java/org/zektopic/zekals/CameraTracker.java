package org.zektopic.zekals;
import android.content.Context;
/** The Lite flavor excludes camera permissions, ML dependencies and model assets. */
final class CameraTracker implements AutoCloseable {
    interface Listener { void sample(double[] point,long captured,String provider); void unavailable(String key,String fallback); }
    CameraTracker(Context context,String provider,int rotation,Listener listener){listener.unavailable("trackingUnavailable","Install the Vision flavor for camera access.");}
    @Override public void close(){}
}
