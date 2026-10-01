-keepclasseswithmembernames class * { native <methods>; }
# MediaPipe Tasks ships no consumer rules. Its native code looks Java classes up by name,
# and protobuf-lite reads message fields reflectively (R8 otherwise strips e.g. `platform_`).
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-dontwarn com.google.mediapipe.**
# ONNX Runtime's JNI layer constructs these Java types from native code.
-keep class ai.onnxruntime.** { *; }
# Flogger (used by MediaPipe) finds its caller by walking the stack for its own class names
# and loads its backend reflectively; renaming fails with "no caller found on the stack".
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**
