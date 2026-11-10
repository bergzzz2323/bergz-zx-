# Bergz ZX 700 — first test build (v0.1.0)

A landscape FM groove synth for Android, tuned for the Pixel 9.
Native C++ FM engine on Oboe (low-latency audio), Jetpack Compose panel UI.
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
| **PLAY / STOP** | Start and stop the 16-step pattern |
| **PATCH** | Next patch (SHIFT + PATCH = previous) |
| **MUTATE ✦** | New variation of the current sound, kept musical. Tap repeatedly |
| **SHIFT + MUTATE** | New melody on the same steps, locked to the scale |
| **ALGO** | Cycle the FM algorithm (SHIFT + ALGO = change scale) |
| **Knobs** | Drag up/down. Display shows the friendly + technical name |
| **FX title** | Tap "FX · REVERB ▸" to switch Reverb / Delay / Phaser |
| **REC** | Motion record: while it's lit, every knob you move is recorded into the loop and replays every bar. Overdub more knobs on later passes |
| **CLEAR** | Erase all recorded motion |
| **Scene A / B + fader** | Morph between two sound snapshots (SHIFT + A/B stores the current sound). Morphing while REC is lit records the morph |
| **Step keys** | Synth: tap empty = add note, tap again = select (pink edge), tap selected = remove |
| **DATA wheel** (bottom left) | With a step selected: move its note through the scale. Otherwise: tempo |

Starter patches: Glass Bass, Neon Bleeps, Velvet Pad, Space Stabs, Chrome Bells, Dusk Choir.

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
- No MIDI clock sync, MIDI in, or audio export yet (next up for studio use).
- Per-step parameter locks exist in the engine but have no UI yet.
- Imported SysEx banks are not supported yet; keep any reference banks in a
  `reference-banks/` folder, which is git-ignored and never shipped.
