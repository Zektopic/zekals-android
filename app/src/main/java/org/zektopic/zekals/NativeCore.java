package org.zektopic.zekals;

import java.nio.ByteBuffer;

/** Caller-owned state and direct buffers keep the JNI boundary small and explicit. */
public final class NativeCore {
    static { System.loadLibrary("zekals_core"); }
    private NativeCore() {}
    public static native void smooth(double[] state, double x, double y, double time, double tau, boolean valid);
    public static native boolean dwell(long[] state, int target, long now, long duration);
    public static native boolean rgba(ByteBuffer y, int yRow, int yPixel, ByteBuffer u, int uRow, int uPixel,
        ByteBuffer v, int vRow, int vPixel, int width, int height, int rotation, boolean mirror, ByteBuffer output);
}
