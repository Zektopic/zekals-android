#include <jni.h>
#include "zekals_core.h"
extern "C" JNIEXPORT void JNICALL
Java_org_zektopic_zekals_NativeCore_smooth(JNIEnv* env, jclass, jdoubleArray array,
        jdouble x, jdouble y, jdouble time, jdouble tau, jboolean valid) {
    if (!array || env->GetArrayLength(array) != 4) return;
    double values[4]; env->GetDoubleArrayRegion(array, 0, 4, values);
    auto next = zekals::smooth({values[0], values[1], values[2], values[3] != 0}, x, y, time, tau, valid);
    double result[]{next.x, next.y, next.timestamp, next.valid ? 1.0 : 0.0};
    env->SetDoubleArrayRegion(array, 0, 4, result);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_zektopic_zekals_NativeCore_dwell(JNIEnv* env, jclass, jlongArray array, jint target, jlong now, jlong duration) {
    if (!array || env->GetArrayLength(array) != 3) return JNI_FALSE;
    jlong values[3]; env->GetLongArrayRegion(array, 0, 3, values);
    zekals::Dwell state{static_cast<int>(values[0]), values[1], values[2] != 0};
    const bool selected = zekals::select(state, target, now, duration);
    jlong result[]{state.target, state.since, state.fired ? 1 : 0};
    env->SetLongArrayRegion(array, 0, 3, result);
    return selected;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_zektopic_zekals_NativeCore_rgba(JNIEnv* env, jclass,
        jobject y, jint yr, jint yp, jobject u, jint ur, jint up, jobject v, jint vr, jint vp,
        jint width, jint height, jint rotation, jboolean mirror, jobject output) {
    const auto plane = [env](jobject buffer, int row, int pixel) {
        const jlong size = buffer ? env->GetDirectBufferCapacity(buffer) : -1;
        return zekals::Plane{size >= 0 ? static_cast<const std::uint8_t*>(env->GetDirectBufferAddress(buffer)) : nullptr,
                            size >= 0 ? static_cast<std::size_t>(size) : 0, row, pixel};
    };
    const jlong output_size = output ? env->GetDirectBufferCapacity(output) : -1;
    if (output_size < 0) return JNI_FALSE;
    return zekals::rgba(plane(y, yr, yp), plane(u, ur, up), plane(v, vr, vp), width, height, rotation, mirror,
        static_cast<std::uint8_t*>(env->GetDirectBufferAddress(output)), static_cast<std::size_t>(output_size));
}
