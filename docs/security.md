# Security and privacy

The application requests no internet permission; the manifest removes it even if a
dependency proposes it. Lite requests no camera permission. Vision requests camera
access only after an explicit start action, and camera hardware is optional.

Vision dependencies carry usage telemetry. ONNX Runtime registers a launch-time
telemetry ContentProvider; the Vision manifest removes it and the estimator calls
`setTelemetry(false)`. MediaPipe Tasks always constructs a Google `datatransport`
(Clearcut) usage logger, which adds a job service, an alarm receiver and
`ACCESS_NETWORK_STATE`. Excluding that library makes `FaceLandmarker` fail with
`NoClassDefFoundError`, so it remains; without `INTERNET` it cannot upload. Treat
this as a known dependency behavior and re-check merged manifests on upgrades.
There is no microphone access, WebView, web server, cloud API key or JavaScript
bridge. The launcher Activity is the only exported component.

Messages and undo are in memory. Android view-state message saving is disabled;
backups and device transfer are excluded using both legacy and modern Android
configuration. Preferences contain access, language, voice and camera settings and
the six calibration coefficients with the screen size and rotation they belong to;
stopping the camera from Settings deletes the calibration. Process death clears the
draft. Logs record camera and model failures with their exceptions, detection
rates and eye-feature statistics, never message text or images. The camera preview
is drawn on screen only and is never stored. Screen capture, a trusted accessibility service or an
installed TTS engine can observe on-screen/spoken text and remain separate trust
boundaries. The chosen voice must be local and already installed.

The JNI boundary validates buffer dimensions, capacities, row/pixel strides,
rotation and finite samples. Camera buffers remain owned by Android and are used
only while the Image is open. The native interface exposes no allocation handles.
Native code still needs review and fuzzing; passing sanitizers does not prove that
all memory errors are absent.

Models are packaged deliberately after checksum verification. Checksums establish
artifact identity, not model safety or accuracy. Installed TTS engines, models,
vendor drivers and runtime native libraries need independent provenance and
license review. An optional ONNX model is copied into app-private cache for runtime
loading and removed on normal estimator close; no camera frames are cached.

Do not commit signing keys, personal phrases, model binaries or recordings. Debug
builds are not store releases. Before distribution, inspect merged manifests,
native-library page alignment, dependency advisories, signing and the exact target
OS. Report security issues privately with synthetic data and no message content.
