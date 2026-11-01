#!/bin/bash
# Headless smoke test: boots the jar in FreeJ2ME, starts 1-1, walks right,
# saves screenshots to build/smoke/ and fails on any game exception.
set -e
cd "$(dirname "$0")/.."
JAR=${1:-build/OpenSyobonJ2ME.jar}
FJ=deps/freej2me/build/classes
mkdir -p build/harness build/smoke
javac -nowarn -cp $FJ -d build/harness tools/harness/Harness.java
cat > build/smoke/script.txt <<'S'
wait 3000
shot build/smoke/title.png
tap 53
wait 3000
shot build/smoke/stage.png
press 54
wait 2000
release 54
shot build/smoke/walk.png
S
rm -rf rms
out=$(timeout 60 java -Djava.awt.headless=true -cp build/harness:$FJ Harness "$JAR" 240 320 build/smoke/script.txt 2>&1 || true)
rm -rf rms
if echo "$out" | grep -q "Exception\|Error"; then
  echo "$out" | grep -v "wav file"
  echo "smoke test FAILED"; exit 1
fi
echo "smoke test ok"
