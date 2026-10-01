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
(TB330FU, Android 15 / API 35). The phone tested the original branch and a one-line
diagnostic patch; every fix below was verified on the tablet only. Inputs were
injected with `adb`; no real switch, mouse or TalkBack session was used.

| Check | Evidence |
| --- | --- |
| Builds and lint | Lite/Vision debug and release build; lint reports no issues |
| Native core on device (phone) | `core_test` cross-compiled for arm64 and run on the phone under ASan and UBSan; 32,000 extra `rgba` conversions with odd sizes, padding, interleaved chroma and Android-minimal buffers: no out-of-bounds access, every output pixel written |
| 16 KB pages | `zipalign -P 16` and ELF `LOAD` alignment 0x4000 for every `.so`, including MediaPipe and ONNX Runtime |
| Camera tracking | Original branch stopped on the second frame on both devices (`MPImage.close()` recycled the reused bitmap). A per-frame copy kept it running on the phone; the fixed build tracks continuously on the tablet, debug and R8 release |
| R8 release | Original Vision release failed in MediaPipe on the phone (protobuf-lite fields) and, with that fixed, on the tablet (Flogger caller lookup). With keep rules the release tracks for 30 s+ on the tablet |
| Lifecycle (tablet) | Camera released in background, restarted on return; camera permission prompt no longer pauses selection; mode and pause state survive rotation |
| Switch keys (tablet) | Touch then Space resumes; Escape pauses while a button has keyboard focus; Space while paused resumes without typing (3/3 runs each) |
| Row/column scanning (tablet) | Rows highlight, Space opens a row, the next press types the scanned key |
| Composition and speech (phone, original branch) | Phrases, keys, Undo/Clear, Sinhala ශ්‍රී composition, offline English TTS with completion status, no-voice message for Sinhala |
| Telemetry (tablet) | Merged Vision manifest has no `INTERNET` and no ONNX telemetry provider; no hosts lookups observed on the tablet |
| Face tracking (tablet, with a person) | Face found at the sensor rotation; up to 38/38 frames with eyes at ~7 fps, software zoom ~1.5x. A sensor-crop zoom crashed the MT6768 camera HAL and was replaced |
| Calibration data (tablet, with a person) | Horizontal eye position tracked the targets (r = 0.98, ~5% RMS); the lid-relative vertical feature had no signal (r = −0.05), so vertical now uses iris-to-corner position, blendshape look scores and lid opening per axis — not yet re-measured |
| Lock and momentum (tablet, injected gaze) | Gaze injected into the gap between two phrase buttons locked onto the nearer one and the pointer settled at its centre; after a jump across the screen the pointer was mid-glide in the first frame and on target a second later |
| Pointer, dwell and blink (tablet, injected gaze) | Debug-only `DEBUG_GAZE` broadcast: pointer drawn at the injected point; dwell typed the phrase under it; a 700 ms blink pressed the button under the pointer, a 200 ms blink did nothing |
| CI | Native, Android Lite and Android Vision jobs pass |
| Not tested | These fixes on the phone (including landscape on a short phone screen and HyperOS key handling), gaze accuracy and calibration with a person, the gaze pointer on screen, a 180° landscape flip, NNAPI, TalkBack/Switch Access, real switches, audio quality, long-run thermals |

The device runs above establish that the Vision pipeline initialises and processes
frames; they do not establish gaze accuracy. No Android gaze accuracy, NPU execution
or whole-app speed claim has been established. Calibrate with the intended users
and record accuracy before relying on eye tracking.
