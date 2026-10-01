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
| Final Lite/Vision Gradle builds | Outstanding: network and original checkout writes became unavailable mid-session |
| Vision model / GPU / NNAPI execution | Not tested on Android hardware or emulator |
| Physical access/voice/thermal tests | Outstanding |

The baseline APK predates the new camera/flavor integration and is not a verified
Vision build. Do not distribute it as such. The final sources need a full build,
lint, dependency review and emulator/device testing once environment access is
restored. No Android gaze accuracy, NPU execution or whole-app speed claim has
been established by the native tests.
