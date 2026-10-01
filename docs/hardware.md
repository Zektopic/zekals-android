# Hardware, models and performance

## Capability matrix

| Target | Implementation | Validation status |
| --- | --- | --- |
| ARMv7 Android | Lite native UI, speech and C++ core | Baseline APK compiled; device testing pending |
| ARM64 Android | Lite or Vision | Lite and Vision (debug and R8 release) run on a Redmi Note 13 (Android 16) and Lenovo Tab M11 (Android 15); see [validation](validation.md) |
| x86_64 Android | Lite or Vision/emulator | Baseline JNI/APK compiled; final Vision/emulator testing pending |
| CPU inference | MediaPipe Face Landmarker | Runs continuously on both test devices; gaze accuracy not yet measured |
| GPU inference | Actual MediaPipe GPU delegate, CPU fallback | Delegate initialised and ran on the Lenovo Tab M11; other GPUs untested |
| GPU/NPU via NNAPI | Optional ONNX Runtime NNAPI request | Custom compatible model required; device assignment and accuracy unverified |
| Qualcomm QNN / vendor NPU SDKs | No direct adapter bundled | Future work; do not advertise native vendor support |

NNAPI is deprecated by Android and is an opt-in compatibility path. It can route
operations to different devices or fall back. The app requests the CPU-disabled
NNAPI flag and falls back to ONNX CPU when session creation/execution fails.
An unavailable or incompatible ONNX asset falls back to the default MediaPipe CPU
model when that model is installed. Inspect the active provider display; a selector
label alone is not proof of accelerated execution. See
[Android NNAPI](https://developer.android.com/ndk/guides/neuralnetworks) and
[ONNX NNAPI](https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html).

## Resource use

Lite excludes both camera inference dependencies. Vision uses an ImageReader with
two buffers and `acquireLatestImage`; it closes every acquired image. Preprocessing
reuses a direct RGBA buffer and a Bitmap. C++ handles plane strides, rotation and
mirroring. Inference is capped at 10 Hz (5 Hz under moderate-or-higher thermal
pressure on supported Android versions). UI delivery replaces pending gaze work
instead of building a queue. Samples older than 500 ms cannot select controls.

The custom ONNX adapter reuses its tensor/pixel arrays, but resizing and runtime
outputs may allocate. MediaPipe/runtime allocations are outside the small C++ core.
Measure actual RSS, GC, inference latency, power and temperature; do not infer
whole-app allocation behavior from the native helper's allocation-free design.

## Custom ONNX contract

Put a reviewed `gaze.onnx` and its lowercase SHA-256 text in `gaze.sha256` under
`app/src/vision/assets/`. The adapter verifies the asset and bounds model size to
128 MiB. It expects exactly one float32 `[1,3,H,W]` RGB input, values in `[0,1]`,
with fixed dimensions between 16 and 1024, and one `[1,3]` output containing
normalized x, y and confidence. Confidence below 0.7 invalidates the sample.
The input has already been rotated upright and mirrored for the front camera.

No compatible trained ONNX gaze model is bundled. Arbitrary face/iris models do
not satisfy this contract. Review licenses, training population, preprocessing,
quantization and accuracy before using a custom model. Keep its runtime libraries
compatible with the device and record their versions.

## Device acceptance

Record build flavor, ABI, Android version, model checksum, runtime/delegate, actual
capture resolution, median/p95 frame-to-input latency, peak memory and ten-minute
thermal behavior. Test camera denial, disconnect, app backgrounding, rotation,
tracking loss, closed eyes, screen resizing, calibration error and sustained speech.
Include people with the intended access needs in evaluation. Physical-device
performance and gaze accuracy have not been measured in this development session.
