# Native C++ boundary

The allocation-free core implements time-based smoothing, dwell selection and
strided YUV_420_888 conversion to RGBA. It accepts caller-owned buffers, validates
sizes/strides/rotation and handles mirroring explicitly. JNI never retains camera
plane pointers. Java owns fixed filter/dwell arrays and reusable direct buffers;
there are no exposed native allocation handles to leak or double-free.

The filter shares the desktop Rust implementation's scalar time/reset contract.
Invalid or non-finite samples reset it; gaps above 500 ms snap to reacquired input.
Dwell activates once per target entry and resets on loss. YUV conversion supports
planar and interleaved chroma using each Android plane's row/pixel stride, including
padding. The BT.601 limited-range conversion is an inference preprocessing choice,
not a color-managed image export pipeline.

Host tests: `cmake -S . -B build/host -DZEKALS_SANITIZERS=ON`,
`cmake --build build/host`, `ctest --test-dir build/host --output-on-failure`.
NDK builds cover arm64-v8a, armeabi-v7a and x86_64. The application library requests
16 KiB page alignment. Third-party native libraries require separate verification.
