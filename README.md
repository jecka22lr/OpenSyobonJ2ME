# OpenSyobonJ2ME

Syobon Action (しょぼんのアクション, Cat Mario) for Java ME phones: a MIDP 2.0 / CLDC 1.1
port of [Open Syobon Action](https://github.com/angelXwind/OpenSyobonAction), aimed at
240x320 Nokia Series 40 phones and also running on 320x240 screens.

![title](docs/title.png) ![1-1](docs/stage.png) ![hint](docs/hint.png) ![landscape](docs/landscape.png)

## Download

Grab the jar (and jad, if your phone wants one) from [Releases](../../releases):

- `OpenSyobonJ2ME.jar`: music and sound effects (~790 KB)
- `OpenSyobonJ2ME-lite.jar`: sound effects only (~220 KB), for phones with a small JAR
  limit or little heap

## Controls

| | Keys |
|---|---|
| Move | 4 / 6 or d-pad left/right |
| Jump | 2, 5, d-pad up or centre key |
| Jump while moving | 1 / 3 |
| Down (pipes) | 8 or d-pad down |
| Down while moving | 7 / 9 |
| Self-destruct | 0 |
| Double speed (hold) | # |
| Back to title | * |
| Pause menu | either soft key |

On the title screen, left/right picks the stage (1-1 to 3-1, plus `?`, the random
"mystery dungeon") and 5 starts.

Most S40 keypads only register one key at a time, which is why 1/3/7/9 combine a
direction with jump or down.

### Portrait or landscape

Set it under **Screen** in the pause menu:

- **Auto**: upright, using the screen as it is (portrait on 240x320, landscape on 320x240)
- **Portrait**
- **Landscape (keys right)**: turn the phone anticlockwise, keypad on the right
- **Landscape (keys left)**: turn it clockwise

In landscape the keys rotate with the picture, so whichever key points "up" on screen
jumps. The setting is saved.

## Sound

All audio is 8 kHz 8-bit mono WAV converted from the original OGGs. The dungeon track
held its loop twice, so only one copy is kept.

Plenty of S40 phones can only play one sampled sound at a time. When the phone reports
`supports.mixing=false`, the pause menu offers **music first** (default: music plays and
only the death, goal and clear jingles cut in, the same places the original stops the
music) or **effects first** (no music, every sound effect).

## Differences from the PC version

- The original 480x420 game logic runs unchanged and is drawn at half scale into a
  240x210 view, so physics and traps behave the same.
- Sprites are redrawn at half size by a pixel-art reduction that keeps 1px outlines and
  small features; the player's cat head is hand-drawn.
- Japanese text uses Sazanami Gothic's 12px bitmap glyphs, pre-rendered into the jar,
  so no CJK font is needed on the phone.
- Hint-block message boxes span the full width so the 12px text fits, and stay up while
  the game waits for a key (the SDL port showed an empty box there).
- Number-key stage select is replaced by left/right on the title screen.

## Building

Needs a JDK (21 works), Python 3 with Pillow, ffmpeg, git and curl.

```sh
./build.sh            # build/OpenSyobonJ2ME.jar + .jad
LITE=1 ./build.sh     # build/OpenSyobonJ2ME-lite.jar + .jad
./tools/smoke.sh      # boots the jar headless in FreeJ2ME, screenshots in build/smoke/
```

The first build runs `tools/fetch-deps.sh`, which pulls into `deps/`: Open Syobon Action
(game source and assets, pinned commit), FreeJ2ME (its `javax.microedition` classes are
the API we compile against, and it runs the smoke test), and ProGuard 7.4.2.

What the build does:

1. `tools/extract_stages.py` moves the stage tile arrays out of `main.cpp` into
   `/st/*.bin`; initialisers that big would overflow Java's 64 KB method limit.
2. `tools/convert.py` translates the C++ game logic into `G.java`, and `tools/patch.py`
   applies the hand fixes on top.
3. `javac -source 8 -target 8`, refusing any non-CLDC API (StringBuilder, boxing, class
   literals), then ProGuard with `-microedition -target 1.4` shrinks and preverifies.
4. `tools/sprites.py`, `tools/font.py` and ffmpeg generate the sprites, font and WAVs.

The runtime is in `src/java/syobon/`: `Rt` stands in for DxLib/SDL, `Cv` is the canvas
(main loop, keys, rotation, pause menu), `Snd` handles MMAPI audio and `Font` draws the
bitmap font.

GitHub Actions builds both jars on every push and attaches them to a release when a
`v*` tag is pushed.

## Credits

- Syobon Action: Chiku (ちく). If you pass this around, credit Chiku.
- Open Syobon Action (SDL port): Mathew Velasquez; fork by Karen (angelXwind)
- Sazanami Gothic font: Electronic Font Open Laboratory (/efont/) and contributors
- J2ME port: Jecka

The game's music is taken from other games, as in the original release.
