# Development and testing

```bash
cmake -S . -B build/host -DZEKALS_SANITIZERS=ON
cmake --build build/host -j2
ctest --test-dir build/host --output-on-failure
bash scripts/check-calibration.sh
python3 scripts/validate_packs.py
./gradlew :app:assembleLiteDebug :app:lintLiteDebug
python3 scripts/download_model.py
./gradlew :app:assembleVisionDebug :app:lintVisionDebug
```

The native tests exercise production core functions under ASan and UBSan.
Calibration tests compile the production pure-Java class and check affine mapping
and rejection of degenerate samples. Language validation uses real bundled assets.
CI builds both flavors and uploads debug artifacts without signing/publishing a
release. Gradle dependencies and wrapper distribution are version-pinned.

A ptrace-based sandbox can prevent LeakSanitizer initialization. In that environment
`ASAN_OPTIONS=detect_leaks=0 ctest ...` still checks address/undefined behavior but
does not perform leak detection. Record the distinction. Do not disable checks in
normal CI to conceal a failure.

Physical-device and accessibility tests remain necessary: TalkBack/Switch Access,
real switches, font scaling, IMEs, voice installation/loss, denied permission,
backgrounding, rotation, calibration, tracking loss and battery/thermal behavior.
Use synthetic text and do not record private conversations in bug reports.

## Review sequence

1. Native C++ core, JNI and reproducible Android build foundation.
2. Accessible native communication, language assets and offline speech.
3. Lite/Vision separation, camera lifecycle, calibration and inference providers.
4. Documentation, CI, dependency updates and explicit validation record.

Merge in order and rerun CI after retargeting stacked PRs. A successful source
compile is distinct from APK packaging, model initialization and device testing.

## Planned improvements

Add emulator UI/instrumentation coverage, provider-specific device testing,
calibration validation targets independent of fitting targets, custom phrase
import/export, native-speaker review, and sustained resource benchmarks. A direct
modern vendor NPU adapter can replace the legacy NNAPI option after validation.
