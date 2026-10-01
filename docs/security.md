# Security and privacy

The application requests no internet permission; the manifest removes it even if a
dependency proposes it. Lite requests no camera permission. Vision requests camera
access only after an explicit start action, and camera hardware is optional.
There is no microphone access, WebView, web server, cloud API key or JavaScript
bridge. The launcher Activity is the only exported component.

Messages and undo are in memory. Android view-state message saving is disabled;
backups and device transfer are excluded using both legacy and modern Android
configuration. Preferences contain access/language/voice settings only. Process
death clears the draft. Screen capture, a trusted accessibility service or an
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
