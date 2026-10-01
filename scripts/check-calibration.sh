#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")/.."
mkdir -p build/calibration
javac -d build/calibration app/src/main/java/org/zektopic/zekals/Calibration.java tests/CalibrationTest.java
java -cp build/calibration org.zektopic.zekals.CalibrationTest
