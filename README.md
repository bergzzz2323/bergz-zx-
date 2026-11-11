# Bergz ZX 700 — test build v0.6

A landscape FM groove synth for Android, tuned for the Pixel 9.
Native C++ FM engine on Oboe (low-latency audio), Jetpack Compose panel UI.
Look: black brushed steel with walnut end-cheeks, an amber display in a screwed-down black metal plate, and purple/mint accents.
It runs edge to edge with slim walnut cheeks. The middle row keeps a thin clear strip on both outer edges, so the
punch-hole camera never covers a control, whichever way round the phone is held.
Layout for two hands: REC / STOP / PLAY and the DATA wheel bottom-left, the scene morph on the right.
Synth-only: the built-in drum machine is switched off so the focus stays on FM sounds.

## Install on your Pixel 9

1. Install **Android Studio** (any 2025/2026 release) on your computer.
2. **File ▸ Open** and choose this `BergzZX700` folder. Let it finish syncing.
   If it asks to install SDK 35, the NDK or CMake 3.22.1, click **Install**.
3. On the phone: **Settings ▸ About phone**, tap **Build number** 7 times, then
   **Settings ▸ System ▸ Developer options ▸ USB debugging** on.
4. Plug the phone in, accept the "Allow USB debugging" prompt, pick the Pixel 9
   in Android Studio's device menu and press **Run ▶**.

The app opens full-screen in landscape. Use headphones or a speaker for the bass.

## Playing it

| Control | What it does |
|---|---|
| **PLAY / STOP** (bottom left) | Start and stop the 16-step pattern. PLAY lights mint while it runs |
| **PLAY (PATT) double-tap** | Two quick taps on PLAY make a brand-new random pattern: random steps and random notes, always in your key and scale, in the patch's own register (chord patches get chords, melodic patches get melodies). One tap just plays |
| **PATCH** | Opens the patch browser: categories on the left, sounds on the right (both scroll). Tap a sound to hear it. SHIFT + PATCH = next patch |
| **MUTATE ✦** | New variation of the current sound, kept musical. Tap repeatedly |
| **SHIFT + MUTATE** | New melody on the same steps, locked to the scale |
| **ALGO** | Cycle the FM algorithm. SHIFT + ALGO (or tap the key name on the display) opens **KEY & SCALE** |
| **Knobs** | Drag up/down. Display shows the friendly + technical name |
| **FX name** | Tap "DELAY ▸" to show the next effect's knobs (Delay → Reverb → Phaser). Double-tap for that effect's settings (Delay: time, tone, ping-pong · Reverb: tone, width · Phaser: depth, intensity) |
| **FX light** | Turns the shown effect on or off. Effects that are on keep playing when you switch to another, so all three can be stacked. The three dots show which are on. Delay is on at start-up; reverb and phaser are off |
| **REC** | Motion record: while it's lit, every knob you move is recorded into the loop and replays every bar. Overdub more knobs on later passes |
| **CLEAR** | Erase all recorded motion |
| **SCENE MORPH** (middle row, right) | Morph between two versions of the sound with the horizontal fader: A on the left (purple), B on the right (mint). With the fader fully left, knob tweaks change A; fully right, they change B; in between, both move together. SHIFT + A/B copies the current sound into that scene. Morphing while REC is lit records the morph |
| **TRANSPOSE** | Vertical fader, 0 in the middle, ±12 semitones; +1 / −1 buttons nudge it. Tap the readout or double-tap the fader to go back to 0. It moves the playing sequence, not live MIDI notes |
| **Step keys** | Tap empty = add note, tap again = select (mint edge), tap selected = switch the step off. A switched-off step remembers its notes (shown dimmed): tap it again to bring them back. Mint lights = steps with notes, purple light = the playhead |
| **DATA wheel** (bottom left) | With a step selected: move its note through the scale. Otherwise: tempo. Tempo starts at 120 BPM and only changes when you change it; patches never touch it |

225 original patches: 15 in each of 15 banks, shown as a 3 x 5 grid in the patch browser.
Lead, Pad, **Keys** (FM pianos, tine and suitcase EPs, organs, clav, harpsichord), Bass, Strings, Stabs,
Plonk, Bleep, FX, Trippy, Cosmic, **Techno** (rollers, acid, dub chords, hypnotic sequences, industrial),
plus **80s**, **90s** and **20s**. Every patch comes with its own pattern so it plays the moment you load it,
and the whole library is level-matched so nothing jumps out or disappears when you browse.

## Keys and scales

The app starts in **C Major**. KEY & SCALE offers all 12 keys and 17 scales: Major, Minor,
Harmonic Minor, Melodic Minor, Dorian, Phrygian, Lydian, Mixolydian, Locrian, Major and
Minor Pentatonic, Blues, Whole Tone, Hungarian, Double Harmonic, Japanese and Chromatic.
New notes, the DATA wheel and SHIFT + MUTATE stay in the chosen key and scale. Changing
key or scale moves the current pattern with it, note by note, so a minor 7th chord
becomes a major 7th in a major scale. Patches load into whatever key and scale is set.

## Session memory

Tweaks you make to a patch (both scenes, the morph position and the algorithm) are
remembered while the app is open, so you can switch patches and come back to your version.
The steps go back to the patch's own pattern on a patch change, and remembered (switched-off)
steps are forgotten. Tempo and transpose stay where you set them. Close and reopen the app
and everything is back to its defaults (120 BPM, transpose 0, C Major, delay on).
Recorded knob motion is cleared when you change patch.

## Changing the theme colours

The whole panel is built from two accent colours: the header line, the scene-morph fade,
lights, lit buttons and the app icon. Change `AccentA` and `AccentB` in `ui/Neon.kt` and the
matching `bergz_accent_a` / `bergz_accent_b` in `res/values/colors.xml`; everything follows.

## MIDI and USB-C

Bergz ZX 700 listens on every MIDI channel and shows connected sources in the
top bar (the blue light flashes on incoming MIDI).

**Play it from Nightstepper or any other MIDI app on the same phone:** in that
app choose the MIDI output **Bergz ZX 700**. Bergz keeps playing in the
background while you switch apps.

**From a USB-C keyboard, controller or sequencer:** plug it into the Pixel
(use a USB-C hub or OTG adapter if it has a USB-A plug). It connects
automatically.

**From another phone or a computer over a USB-C cable:** on the Pixel, pull
down the notification shade, tap the USB notification and choose **MIDI**.
The other device then sees the Pixel as a MIDI device and can send to it.

What Bergz responds to:

| MIDI | Does |
|---|---|
| Notes + velocity | Play Bergz live (8 voices, chords) |
| Pitch bend | ±2 semitones |
| Clock + Start / Stop / Continue | Bergz's own pattern follows the host's tempo and transport ("EXT" on the display) |
| CC 1 / 2 / 3 | Bright / Character / Grit |
| CC 74 / 71 / 76 | Tone / Peak / Sweep |
| CC 73 / 75 / 79 / 72 | Fade in / Decay / Hold / Tail |
| CC 77 / 78 / 80 | Wobble speed / Wobble / Length |
| CC 94 / 95 | Delay amount / feedback |
| CC 91 / 92 | Reverb amount / size |
| CC 93 / 90 | Phaser amount / rate |
| CC 7 | Volume |
| CC 120, 123 | All notes off |

To play only Nightstepper's notes, leave Bergz stopped (or clear its steps).
To layer them, press PLAY on Bergz too.

**Audio out over USB-C:** plug in a USB-C audio interface, DAC or USB-C
headphones and the sound moves to it automatically (top bar shows
"OUT · USB-C AUDIO"). Note that a phone can't send audio *into* a computer
over its USB cable; use an audio interface or the headphone output for that.

## Hearing the engine without a phone

`tools/render_test.cpp` drives the exact same engine and writes a WAV:

```
cd tools
g++ -O2 -std=c++17 -I../app/src/main/cpp render_test.cpp -o render_test
./render_test bergz_presets.wav
```

## Project layout

```
app/src/main/cpp/SynthCore.h     FM voices, filter, drums, FX, sequencer, motion recording
app/src/main/cpp/Presets.h       Starter patches and patterns (all original)
app/src/main/cpp/AudioEngine.cpp Oboe stream + JNI bridge
app/src/main/java/.../ui/        Panel UI (Compose)
tools/render_test.cpp            Desktop WAV renderer / engine test
```

## Known limits in this test build

- 4 starter FM algorithms (full set later); 8-voice polyphony.
- Patterns and edits are not saved between launches yet.
- No audio export yet.
- Per-step parameter locks exist in the engine but have no UI yet.
- Imported SysEx banks are not supported yet; keep any reference banks in a
  `reference-banks/` folder, which is git-ignored and never shipped.

## Fonts

The panel uses VT323 (display) and Barlow Condensed (labels), both under the SIL
Open Font Licence. They are downloaded from the Google Fonts repository on the
first build (`fetchFonts` in `app/build.gradle.kts`), so that build needs an
internet connection. Their licences are bundled in the app under `assets/licenses`.
