#!/bin/bash
# Fetches what build.sh needs into deps/:
#   - Open Syobon Action (game source, sprites, sound) - pinned commit
#   - FreeJ2ME (its javax.microedition classes are the API we compile against,
#     and it runs the jar in tests) - pinned commit
#   - ProGuard 7.4.2 (shrinks and preverifies for CLDC)
set -e
cd "$(dirname "$0")/.."
mkdir -p deps && cd deps
if [ ! -d OpenSyobonAction ]; then
  git clone https://github.com/angelXwind/OpenSyobonAction.git
  git -C OpenSyobonAction checkout -q 77ca2c128784512eca10e24a453ca19c270a8af4
fi
if [ ! -d freej2me ]; then
  git clone https://github.com/hex007/freej2me.git
  git -C freej2me checkout -q eb4ac87e9a0b212ba701691eb67ee4892b2b1173
fi
if [ ! -d freej2me/build/classes ]; then
  mkdir -p freej2me/build/classes
  javac -nowarn -d freej2me/build/classes $(find freej2me/src -name "*.java") 2>&1 | grep -v "^Note" || true
fi
if [ ! -f proguard-7.4.2/lib/proguard.jar ]; then
  curl -sSfL -o pg.zip https://github.com/Guardsquare/proguard/releases/download/v7.4.2/proguard-7.4.2.zip
  unzip -q pg.zip && rm pg.zip
fi
