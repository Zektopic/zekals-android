# Architecture and lifecycle

`MainActivity` owns accessible Android views, composition/undo state, speech and
access modes. `LanguagePack` reads bounded local JSON assets. Android TTS owns voice
models; the app filters for installed offline voices matching the pack language.
UI preferences are shared preferences; message text never goes into preferences,
files, logs or automatic view-state saving. Rotation retains the message and pause
state through an in-process non-configuration instance, not a persistent bundle.
The activity is landscape-only; wide screens use a two-column board.

`NativeCore` passes caller-owned arrays/direct buffers across JNI. `native/core.cpp`
implements smoothing, dwell and image conversion. No long-lived native pointer is
exposed to Java. Bounds and strides are checked before pixel access. Native tests
cover invalid samples, reacquisition, selection locking and strided conversion.

The Lite source set supplies a small camera-disabled implementation and excludes
camera dependencies/permissions. Vision supplies `CameraTracker`, `MediaPipeEstimator`
and `OnnxEstimator`. Camera/model construction and inference share one worker
thread, including the GPU delegate's thread affinity. It consumes latest images,
closes them deterministically, rate-limits processing and coalesces UI callbacks.
Provider failures invalidate input or use an explicit CPU fallback.

The activity owns a generation counter for camera sessions. Late results from a
stopped/replaced session are ignored. A sample's capture receipt time expires after
500 ms. Stop and activity backgrounding release the camera, ImageReader, model and
worker. A camera the user started is restarted in `onResume`, and a display
listener restarts it when a landscape device is flipped, because the image rotation
is fixed per session. Backgrounding keeps the calibration; a Settings stop or a
provider change discards it. A failed session clears its tracker so one press
retries. The screen stays on only while the camera runs. The app has no foreground
camera service or hidden background recording.

Calibration fits a two-output affine transform from five normalized feature
samples. It rejects singular systems and high fitting residuals. This is a basic
webcam iris estimator and does not compensate robustly for all head motion or
clinical conditions. The native dwell state fires once per target entry. After
activation, gaze must leave the activated rectangle before another action, even
when a keyboard page rebuilds its Android views.

All speech and access controls stay available without a camera. Each row of
buttons is a scan group; scan mode brings the highlighted row into view, and the
scroll and pause controls stay outside the scrolling content. The root view handles
switch keys in pre-IME dispatch and keeps focus in touch mode, because Android
otherwise consumes the first Space/Enter to leave touch mode and a focused button
consumes Space/Enter as a click. The gaze pointer is drawn above the board and does
not take touches. The message editor uses system IME support. Native accessibility
semantics come from real Buttons, EditText and live-region TextViews.

Runtime fallback keeps the same model family and coordinate meaning: ONNX retries
its model on CPU; MediaPipe retries its model on CPU. If ONNX still fails, tracking
stops so an existing calibration cannot silently be reused with a different model.
An unavailable custom ONNX model can select MediaPipe at startup, before calibration.
