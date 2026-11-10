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
| **PATCH** | Opens the patch browser: pick a category, tap a sound to hear it. SHIFT + PATCH = next patch |
| **MUTATE ✦** | New variation of the current sound, kept musical. Tap repeatedly |
| **SHIFT + MUTATE** | New melody on the same steps, locked to the scale |
| **ALGO** | Cycle the FM algorithm (SHIFT + ALGO = change scale) |
| **Knobs** | Drag up/down. Display shows the friendly + technical name |
| **FX title** | Tap "FX · REVERB ▸" to switch Reverb / Delay / Phaser. Double-tap for that effect's settings (Reverb: tone, width · Delay: time, tone, ping-pong · Phaser: depth, intensity) |
| **REC** | Motion record: while it's lit, every knob you move is recorded into the loop and replays every bar. Overdub more knobs on later passes |
| **CLEAR** | Erase all recorded motion |
| **Scene A / B + fader** | Morph between two sound snapshots (SHIFT + A/B stores the current sound). Morphing while REC is lit records the morph |
| **Step keys** | Synth: tap empty = add note, tap again = select (pink edge), tap selected = remove |
| **DATA wheel** (bottom left) | With a step selected: move its note through the scale. Otherwise: tempo |

48 original patches in 10 categories: Lead, Pad, Bass, Strings, Stabs, Plonk, Bleep, FX, Trippy, Cosmic.

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
| CC 91 / 93 / 7 | FX amount / FX size-feedback-rate / Volume |
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
