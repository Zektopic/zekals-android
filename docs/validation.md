# Validation record

October 2026 development session, Linux x86_64 host.

| Check | Evidence |
| --- | --- |
| Baseline Android communication app | Gradle `assembleDebug` and `lintDebug` succeeded with AGP 8.9.2 / JDK 17 |
| Baseline ABIs | C++/JNI compiled for arm64-v8a, armeabi-v7a and x86_64 and included in the debug APK |
| Native core | Host ASan/UBSan tests passed before environment restrictions |
| Staged native recheck | Passed with leak detection disabled because the restricted runner uses ptrace |
| Updated Lite Java source | Compiled against the actual Android API 35 SDK; generated flavor constant supplied for the offline compile |
| Calibration | Production Java affine mapping and degeneracy tests passed |
| Language packs | Six bundled packs pass the standard-library validator |
| Final Lite/Vision Gradle builds | Superseded by the device validation below |
| Vision model / GPU / NNAPI execution | Superseded by the device validation below (NNAPI still untested) |
| Physical access/voice/thermal tests | Partly covered below; accessibility-service and thermal tests outstanding |

## Device validation, 1 October 2026

Windows 11 host, AGP 8.9.2, JDK 21 (Android Studio JBR), NDK 28.2.13676358.
Devices: Redmi Note 13 (23129RAA4G, Android 16 / API 36, HyperOS) and Lenovo Tab M11
(TB330FU, Android 15 / API 35). Inputs were injected with `adb`; no real switch,
mouse or TalkBack session was used.

| Check | Evidence |
| --- | --- |
| Builds and lint | Lite/Vision debug and release build; lint reports no issues |
| Native core on device | `core_test` cross-compiled for arm64 and run on the phone under ASan and UBSan; 32,000 extra `rgba` conversions with odd sizes, padding, interleaved chroma and Android-minimal buffers: no out-of-bounds access, every output pixel written |
| 16 KB pages | `zipalign -P 16` and ELF `LOAD` alignment 0x4000 for every `.so`, including MediaPipe and ONNX Runtime |
| Camera tracking | Original branch stopped on the second frame (`MPImage.close()` recycled the reused bitmap). Fixed build tracks continuously, debug and R8 release, on both devices |
| R8 release | Original Vision release failed in MediaPipe (protobuf-lite fields, then Flogger caller lookup). Keep rules added; release tracks for 30 s+ on the tablet |
| Lifecycle | Camera released in background, restarted on return; camera permission prompt no longer pauses selection; mode and pause state survive rotation |
| Switch keys | Touch then Space resumes; Escape pauses while a button has keyboard focus; Space while paused resumes without typing (3/3 runs each) |
| Row/column scanning | Rows highlight, Space opens a row, the next press types the scanned key |
| Composition and speech | Phrases, keys, Undo/Clear, Sinhala ශ්‍රී composition, offline English TTS with completion status, no-voice message for Sinhala |
| Telemetry | Merged Vision manifest has no `INTERNET` and no ONNX telemetry provider; no hosts lookups observed on the tablet |
| Not tested | Gaze accuracy and calibration with a person, the gaze pointer on screen, a 180° landscape flip, NNAPI, TalkBack/Switch Access, real switches, audio quality, long-run thermals |

The device runs above establish that the Vision pipeline initialises and processes
frames; they do not establish gaze accuracy. No Android gaze accuracy, NPU execution
or whole-app speed claim has been established. Calibrate with the intended users
and record accuracy before relying on eye tracking.
