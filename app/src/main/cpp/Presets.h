// Bergz ZX 700 — starter patches. All original Bergz sounds and patterns.
// Each preset is a full snapshot: every macro, tempo, algorithm, FX type,
// the 16-step synth pattern (up to 4-note chords) and the drum pattern.
#pragma once

#include <array>
#include <initializer_list>
#include <utility>
#include <vector>

#include "SynthCore.h"

namespace bergz {

struct Preset {
    const char* name;
    std::array<float, P_COUNT> params{};
    int notes[kSteps][kNotesPerStep];
    uint8_t drums[kDrumLanes][kSteps];
};

namespace detail {
using Hits = std::initializer_list<int>;
using Chords = std::initializer_list<std::pair<int, std::initializer_list<int>>>;

inline Preset make(const char* name, std::array<float, P_COUNT> p, Chords chords,
                   Hits kick, Hits snare, Hits hat, Hits clap) {
    Preset x{};
    x.name = name;
    x.params = p;
    for (auto& row : x.notes) for (int& n : row) n = -1;
    for (const auto& c : chords) {
        int slot = 0;
        for (int n : c.second) if (slot < kNotesPerStep) x.notes[c.first][slot++] = n;
    }
    const Hits lanes[kDrumLanes] = {kick, snare, hat, clap};
    for (int l = 0; l < kDrumLanes; ++l) {
        for (int s = 0; s < kSteps; ++s) x.drums[l][s] = 0;
        for (int s : lanes[l]) x.drums[l][s] = 1;
    }
    return x;
}
}  // namespace detail

// params order: FM DEPTH, HARMONIC, FEEDBACK, CUTOFF, RESO, ENV AMT,
//               FX MIX, FX PARAM, VOLUME, ATTACK, DECAY, SUSTAIN, RELEASE,
//               LFO RATE, LFO DEPTH, GATE, TEMPO, ALGORITHM, FX TYPE
inline const std::vector<Preset>& presets() {
    using detail::make;
    static const std::vector<Preset> list = {
        make("GLASS BASS",
             {0.80f, 0.40f, 0.25f, 0.45f, 0.35f, 0.55f, 0.25f, 0.45f, 0.70f,
              0.02f, 0.30f, 0.20f, 0.20f, 0.30f, 0.15f, 0.46f, 120.f, 0.f, (float)FX_DELAY},
             {{0, {36}}, {2, {36}}, {3, {39}}, {5, {36}}, {6, {31}}, {8, {36}},
              {10, {48}}, {11, {46}}, {13, {43}}, {14, {36}}},
             {0, 4, 8, 12, 14}, {4, 12}, {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}, {7, 15}),

        make("NEON BLEEPS",
             {0.55f, 0.95f, 0.10f, 0.80f, 0.30f, 0.30f, 0.40f, 0.60f, 0.90f,
              0.00f, 0.18f, 0.00f, 0.15f, 0.60f, 0.10f, 0.30f, 124.f, 2.f, (float)FX_DELAY},
             {{0, {84}}, {2, {79}}, {3, {87}}, {6, {82}}, {8, {84}},
              {10, {91}}, {11, {87}}, {14, {79}}},
             {0, 4, 8, 12}, {4, 12}, {2, 6, 10, 14}, {}),

        make("VELVET PAD",
             {0.35f, 0.20f, 0.15f, 0.50f, 0.15f, 0.15f, 0.50f, 0.85f, 0.66f,
              0.55f, 0.65f, 0.80f, 0.65f, 0.20f, 0.25f, 0.90f, 96.f, 3.f, (float)FX_REVERB},
             {{0, {48, 51, 55, 58}}, {8, {44, 48, 51, 55}}},
             {0, 8}, {}, {2, 6, 10, 14}, {}),

        make("SPACE STABS",
             {0.60f, 0.55f, 0.20f, 0.62f, 0.40f, 0.60f, 0.45f, 0.75f, 0.64f,
              0.00f, 0.35f, 0.00f, 0.45f, 0.25f, 0.10f, 0.25f, 118.f, 2.f, (float)FX_REVERB},
             {{0, {60, 63, 67, 70}}, {3, {60, 63, 67, 70}}, {6, {58, 62, 65, 69}},
              {10, {56, 60, 63, 67}}, {14, {58, 62, 65}}},
             {0, 4, 8, 12}, {}, {2, 6, 10, 14}, {4, 12}),

        make("CHROME BELLS",
             {0.45f, 0.75f, 0.05f, 0.75f, 0.10f, 0.20f, 0.45f, 0.35f, 0.62f,
              0.00f, 0.55f, 0.00f, 0.60f, 0.15f, 0.05f, 0.40f, 110.f, 1.f, (float)FX_PHASER},
             {{0, {60, 72}}, {3, {79}}, {6, {76}}, {10, {84}}, {13, {79}}},
             {0, 8}, {}, {}, {}),

        make("DUSK CHOIR",
             {0.25f, 0.00f, 0.30f, 0.42f, 0.25f, 0.10f, 0.55f, 0.25f, 0.66f,
              0.70f, 0.70f, 0.85f, 0.75f, 0.10f, 0.30f, 0.95f, 88.f, 3.f, (float)FX_PHASER},
             {{0, {45, 52, 57, 60}}, {8, {41, 48, 53, 57}}},
             {}, {}, {}, {}),
    };
    return list;
}

// Send a preset to the engine as ordinary commands, so the UI's own copy of
// the pattern and the engine always agree. `send` is post() or command().
template <typename Send>
inline void loadPreset(const Preset& p, Send send) {
    send(Cmd{CMD_CLEAR_PATTERN, 0, 0, 0.f});
    send(Cmd{CMD_CLEAR_MOTION, -1, 0, 0.f});
    // FX settings-panel params are left as the player set them.
    for (int id = 0; id < kPresetParams; ++id) send(Cmd{CMD_PARAM, id, 0, p.params[id]});
    for (int s = 0; s < kSteps; ++s)
        for (int n = 0; n < kNotesPerStep; ++n)
            if (p.notes[s][n] >= 0) send(Cmd{CMD_STEP_NOTE, s, p.notes[s][n], (float)n});
    // Synth-only for now: drum patterns stay in the data but are not loaded.
}

}  // namespace bergz
