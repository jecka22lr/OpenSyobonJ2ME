#!/bin/bash
# Builds OpenSyobonJ2ME.jar/.jad (MIDP 2.0 / CLDC 1.1) from the Open Syobon source.
#   ./build.sh            full build (music + effects)
#   LITE=1 ./build.sh     effects only, smaller jar
set -e
cd "$(dirname "$0")"
[ -d deps/OpenSyobonAction ] || ./tools/fetch-deps.sh
mkdir -p build
unset JAVA_TOOL_OPTIONS
S=${SYOBON_SRC:-deps/OpenSyobonAction}
FJ=${FREEJ2ME_CLASSES:-deps/freej2me/build/classes}   # MIDP API to compile against
PG=${PROGUARD:-deps/proguard-7.4.2/lib/proguard.jar}
NAME=OpenSyobonJ2ME
[ -n "$LITE" ] && NAME=OpenSyobonJ2ME-lite
VERSION=1.0.0

# 1. game logic: C++ -> Java
python3 tools/extract_stages.py $S/main.cpp res/st build/logic.cpp
python3 tools/convert.py build/logic.cpp $S/main.h $S/loadg.cpp src/java/syobon/G.java
python3 tools/patch.py src/java/syobon/G.java

# 2. compile (Java 8 bytecode, no StringBuilder/boxing allowed)
rm -rf build/cls build/rec && mkdir -p build/cls build/rec
javac -nowarn -encoding UTF-8 -source 8 -target 8 -cp $FJ -d build/cls src/java/syobon/*.java 2>&1 | grep -v "^warning\|^Note\|^[0-9]* warning" || true
if javap -c -p -cp build/cls $(cd build/cls && ls syobon/*.class | sed 's/\.class//;s#/#.#') | grep -q "StringBuilder\|Integer.valueOf\|java/util/[A-QS-Z]\|ldc.*class "; then
  echo "non-CLDC API used"; exit 1
fi

# 3. sprites: record rects from loadg(), then reduce
javac -nowarn -source 8 -target 8 -cp build/cls:$FJ -d build/rec tools/rec/syobon/Rec.java 2>/dev/null
java -cp build/rec:build/cls:$FJ syobon.Rec > build/rects.txt
mkdir -p res/g
python3 tools/sprites.py $S/res res/g build/rects.txt > /dev/null

# 4. font
python3 tools/font.py $S/res/sazanami-gothic.ttf res/f.bin src/java/syobon/*.java

# 5. audio: 8 kHz 8-bit mono WAV, plain 44-byte headers
mkdir -p res/a
rm -f res/a/*.wav
WAV="-ac 1 -ar 8000 -c:a pcm_u8 -map_metadata -1 -fflags +bitexact -flags:a +bitexact"
if [ -z "$LITE" ]; then
  i=1
  for n in field dungeon star4 castle puyo; do
    extra=""; [ $n = dungeon ] && extra="-t 19.072"   # file holds the loop twice
    ffmpeg -v error -y -i $S/BGM/$n.ogg $extra -af "highpass=f=60,lowpass=f=3800,loudnorm=I=-14:TP=-1:LRA=11" $WAV res/a/b$i.wav
    i=$((i+1))
  done
fi
declare -A SE=([1]=jump [3]=brockbreak [4]=coin [5]=humi [6]=koura [7]=dokan [8]=brockkinoko [9]=powerup [10]=kirra [11]=goal [12]=death [13]=Pswitch [14]=jumpBlock [15]=hintBlock [16]=4-clear [17]=allclear [18]=tekifire)
for k in "${!SE[@]}"; do
  ffmpeg -v error -y -i $S/SE/${SE[$k]}.ogg -af "highpass=f=60,lowpass=f=3800,volume=1.4,alimiter=limit=0.95" $WAV res/a/s$k.wav
done

# 6. icon
python3 - <<'EOF'
from PIL import Image
im = Image.open('res/g/player.png').convert('RGBA').crop((0, 0, 15, 18))
ic = Image.new('RGBA', (16, 18), (0, 0, 0, 0)); ic.paste(im, (0, 0))
ic.save('res/icon.png')
EOF

# 7. jar, shrink + preverify
rm -rf build/jar && mkdir -p build/jar && cp -r build/cls/* build/jar/
cp -r res/g res/st res/a res/f.bin res/icon.png build/jar/
cat > build/MANIFEST.MF <<EOF
MIDlet-1: OpenSyobonJ2ME, /icon.png, syobon.Main
MIDlet-Name: OpenSyobonJ2ME
MIDlet-Vendor: Chiku (port: Jecka)
MIDlet-Version: $VERSION
MIDlet-Icon: /icon.png
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
EOF
(cd build/jar && jar cfm ../raw.jar ../MANIFEST.MF .)
cat > build/pg.cfg <<EOF
-injars raw.jar
-outjars $NAME.jar
-libraryjars api.jar
-microedition
-target 1.4
-dontusemixedcaseclassnames
-allowaccessmodification
-overloadaggressively
-repackageclasses ''
-optimizationpasses 3
-keep public class syobon.Main
-dontnote
EOF
# library jar for proguard = MIDP API classes from FreeJ2ME (javax.microedition only) + JDK base
(cd $FJ && jar cf $OLDPWD/build/api.jar javax)
echo "-libraryjars <java.home>/jmods/java.base.jmod(!**.jar;!module-info.class)" >> build/pg.cfg
java -jar $PG @build/pg.cfg > build/pg.log 2>&1 || { cat build/pg.log; exit 1; }

JARSIZE=$(stat -c%s build/$NAME.jar)
cat > build/$NAME.jad <<EOF
MIDlet-1: OpenSyobonJ2ME, /icon.png, syobon.Main
MIDlet-Name: OpenSyobonJ2ME
MIDlet-Vendor: Chiku (port: Jecka)
MIDlet-Version: $VERSION
MIDlet-Icon: /icon.png
MIDlet-Jar-URL: $NAME.jar
MIDlet-Jar-Size: $JARSIZE
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
EOF
echo "built build/$NAME.jar ($JARSIZE bytes)"
