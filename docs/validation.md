# Validation record

October 2026 development session, Linux x86_64 host. These checks cover the PR 1–4 Android stack currently open for review. Results from the later Android device-fix PR are identified separately because it has not merged into this stack.

| Check | Result |
| --- | --- |
| Android Lite debug | `assembleLiteDebug` passed; arm64-v8a, armeabi-v7a and x86_64 JNI libraries built |
| Android Vision debug | `assembleVisionDebug` passed; arm64-v8a and x86_64 JNI libraries built with the checksum-verified MediaPipe face model |
| Android release/R8 | `assembleLiteRelease` and `assembleVisionRelease` passed on the host; outputs are unsigned APKs |
| Android debug lint | Lite and Vision pass with zero errors and two warnings each (data extraction and one untranslated emergency string) |
| Native C++ | Host ASan/UBSan build and CTest contract pass |
| Calibration | Production Java affine mapping and degeneracy checks pass |
| Language packs | All six bundled packs pass the Python validator |
| Android PR GitHub CI | PR #4's native, Lite and Vision jobs pass after limiting SDK setup to `platform-tools` |
| Android PR #5 | Its own GitHub CI passes. Its description reports manual validation on a Lenovo Tab M11 (Android 15); those results apply to PR #5's changes, which are not in this PR 1–4 branch yet |

The debug Lite APK is approximately 92 KB and the debug Vision APK approximately 98 MiB on this build. The Vision artifact is much larger because it combines camera inference runtimes, their native libraries and the model; users who do not need camera tracking should install Lite. Both release APK outputs are unsigned and are not ready to distribute.

The host does not have a connected Android device. This session has not tested eye-tracking accuracy, actual Android CPU/GPU/NNAPI execution, camera lifecycle, switch hardware, TalkBack, translation quality or thermal behavior. PR #5 records additional device work, but its fixes must be reviewed and merged before attributing those results to the release stack. No Android build establishes that every NPU, GPU, CPU, x86 or ARM device works. Run the app with intended users and target devices before deployment.

The earlier baseline APK predates the Lite/Vision flavors and is not a Vision build. See the getting-started and hardware guides for build commands, model integrity and per-device provider limitations.
