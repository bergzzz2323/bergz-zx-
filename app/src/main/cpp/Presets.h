// Bergz ZX 700 — patch library. All original Bergz sounds and patterns.
// Each preset is a full snapshot: every macro, tempo, algorithm, FX type and
// a 16-step synth pattern (up to 4-note chords) to audition it with.
#pragma once

#include <array>
#include <initializer_list>
#include <utility>
#include <vector>

#include "SynthCore.h"

namespace bergz {

// Browser categories, in display order.
constexpr int kCategories = 13;
constexpr const char* kCategoryNames[kCategories] = {
    "LEAD", "PAD", "BASS", "STRINGS", "STABS", "PLONK", "BLEEP", "FX", "TRIPPY", "COSMIC",
    "80s", "90s", "20s",
};
// 80s: classic 1980s-inspired. 90s: dance, rave and acid house. 20s: techy trance and techno.
enum Cat { LEAD, PAD, BASS, STRINGS, STABS, PLONK, BLEEP, FXS, TRIPPY, COSMIC, EIGHTIES, NINETIES, TWENTIES };

struct Preset {
    const char* name;
    int category;
    std::array<float, P_COUNT> params{};
    int notes[kSteps][kNotesPerStep];
};

namespace detail {
using Chords = std::initializer_list<std::pair<int, std::initializer_list<int>>>;

// Short-hand for a patch, in this order:
//  fm, character(harmonic), grit(feedback), tone(cutoff), peak(reso), sweep(env amt),
//  fx amount, fx size/feedback/rate, volume, fade in, decay, hold, tail,
//  wobble speed, wobble, length(gate), tempo, algorithm(0-3), featured effect.
// "fx amount / fx size" go to the featured effect; the other two effects get
// their default amounts. Which effects are switched on is up to the player.
// Patterns are written in C natural minor and are conformed to the player's
// chosen key and scale when loaded.
struct S {
    float fm, ch, grit, tone, peak, sweep, fxAmt, fxPar, vol, a, d, s, r, lfoR, lfoD, gate, bpm;
    int alg, fx;
};

inline Preset make(const char* name, int cat, const S& v, Chords chords) {
    Preset x{};
    x.name = name;
    x.category = cat;
    for (int i = 0; i < P_COUNT; ++i) x.params[i] = kDefaultParams[i];
    auto& p = x.params;
    p[P_FM_DEPTH] = v.fm; p[P_HARMONIC] = v.ch; p[P_FEEDBACK] = v.grit;
    p[P_CUTOFF] = v.tone; p[P_RESO] = v.peak; p[P_ENV_AMT] = v.sweep; p[P_VOLUME] = v.vol;
    p[P_ATTACK] = v.a; p[P_DECAY] = v.d; p[P_SUSTAIN] = v.s; p[P_RELEASE] = v.r;
    p[P_LFO_RATE] = v.lfoR; p[P_LFO_DEPTH] = v.lfoD; p[P_GATE] = v.gate; p[P_TEMPO] = v.bpm;
    p[P_ALGORITHM] = (float)v.alg; p[P_FX_TYPE] = (float)v.fx;
    switch (v.fx) {
        case FX_DELAY:  p[P_DLY_MIX] = v.fxAmt; p[P_DLY_FB] = v.fxPar; break;
        case FX_REVERB: p[P_REV_MIX] = v.fxAmt; p[P_REV_SIZE] = v.fxPar; break;
        case FX_PHASER: p[P_PH_MIX] = v.fxAmt; p[P_PH_RATE] = v.fxPar; break;
    }
    for (auto& row : x.notes) for (int& n : row) n = -1;
    for (const auto& c : chords) {
        int slot = 0;
        for (int n : c.second) if (slot < kNotesPerStep) x.notes[c.first][slot++] = n;
    }
    return x;
}

}  // namespace detail

inline const std::vector<Preset>& presets() {
    using detail::make;
    using detail::S;
    constexpr int R = FX_REVERB, D = FX_DELAY, P = FX_PHASER;
    static const std::vector<Preset> list = {
        // ------------------------------------------------------------ LEAD
        make("SOLAR LEAD", LEAD, {.60f,.40f,.35f,.70f,.30f,.35f, .30f,.50f,.75f, .02f,.40f,.60f,.25f, .55f,.12f,.50f,126, 0,D},
             {{0,{72}},{2,{75}},{3,{77}},{4,{79}},{6,{77}},{7,{75}},{8,{72}},{10,{70}},{11,{72}},{14,{67}}}),
        make("SYNC SCREAM", LEAD, {.90f,.60f,.55f,.75f,.45f,.50f, .25f,.40f,.68f, .00f,.30f,.50f,.20f, .60f,.15f,.40f,128, 1,D},
             {{0,{60}},{2,{63}},{4,{67}},{6,{70}},{8,{72}},{10,{70}},{12,{67}},{14,{63}}}),
        make("GLIDE HERO", LEAD, {.55f,.20f,.20f,.65f,.20f,.30f, .35f,.55f,0.90f, .05f,.50f,.70f,.35f, .60f,.20f,.75f,112, 3,D},
             {{0,{72}},{4,{79}},{8,{75}},{12,{77}}}),
        make("ACID FM", LEAD, {.70f,.40f,.30f,.35f,.75f,.80f, .20f,.50f,.74f, .00f,.25f,.10f,.15f, .30f,.10f,.35f,130, 0,D},
             {{0,{48}},{1,{48}},{3,{60}},{4,{48}},{6,{51}},{7,{48}},{9,{55}},{10,{48}},{12,{58}},{14,{60}},{15,{48}}}),
        make("CHIPTUNE", LEAD, {.35f,.80f,.60f,.90f,.10f,.10f, .25f,.30f,0.85f, .00f,.20f,.50f,.05f, .80f,.05f,.30f,140, 2,D},
             {{0,{72}},{1,{75}},{2,{79}},{3,{84}},{4,{72}},{5,{75}},{6,{79}},{7,{84}},
              {8,{70}},{9,{74}},{10,{77}},{11,{82}},{12,{70}},{13,{74}},{14,{77}},{15,{82}}}),

        // ------------------------------------------------------------ PAD
        make("VELVET PAD", PAD, {.35f,.20f,.15f,.50f,.15f,.15f, .50f,.85f,.66f, .55f,.65f,.80f,.65f, .20f,.25f,.90f,96, 3,R},
             {{0,{48,51,55,58}},{8,{44,48,51,55}}}),
        make("DUSK CHOIR", PAD, {.25f,.00f,.30f,.42f,.25f,.10f, .55f,.25f,.66f, .70f,.70f,.85f,.75f, .10f,.30f,.95f,88, 3,P},
             {{0,{45,52,57,60}},{8,{41,48,53,57}}}),
        make("GLASS HALO", PAD, {.30f,.60f,.10f,.60f,.10f,.10f, .55f,.90f,.62f, .60f,.70f,.85f,.75f, .15f,.20f,.95f,84, 3,R},
             {{0,{48,55,58,62}},{8,{46,53,58,62}}}),
        make("WARM NEBULA", PAD, {.20f,.20f,.25f,.40f,.20f,.15f, .50f,.15f,.68f, .70f,.70f,.90f,.80f, .10f,.30f,1.0f,80, 3,P},
             {{0,{43,50,55,58}},{8,{44,51,55,60}}}),
        make("OCEAN FLOOR", PAD, {.25f,.00f,.40f,.35f,.30f,.20f, .60f,.95f,.68f, .80f,.80f,.85f,.85f, .08f,.35f,1.0f,72, 2,R},
             {{0,{36,43,51,55}}}),

        // ------------------------------------------------------------ BASS
        make("GLASS BASS", BASS, {.80f,.40f,.25f,.45f,.35f,.55f, .25f,.45f,.80f, .02f,.30f,.20f,.20f, .30f,.15f,.46f,120, 0,D},
             {{0,{36}},{2,{36}},{3,{39}},{5,{36}},{6,{31}},{8,{36}},{10,{48}},{11,{46}},{13,{43}},{14,{36}}}),
        make("RUBBER SUB", BASS, {.35f,.20f,.10f,.35f,.20f,.35f, .10f,.30f,.88f, .00f,.35f,.50f,.15f, .30f,.00f,.50f,118, 0,D},
             {{0,{36}},{3,{36}},{6,{39}},{8,{36}},{11,{34}},{14,{31}}}),
        make("DOCK LIGHTS", BASS, {.65f,.40f,.30f,.50f,.40f,.60f, .30f,.20f,.80f, .00f,.30f,.30f,.20f, .40f,.10f,.40f,124, 0,P},
             {{0,{36}},{1,{48}},{2,{36}},{4,{36}},{5,{48}},{7,{39}},{8,{36}},{9,{48}},{10,{36}},{12,{34}},{13,{46}},{15,{31}}}),
        make("IRON PLUCK", BASS, {.85f,.80f,.20f,.55f,.30f,.70f, .20f,.40f,0.90f, .00f,.20f,.00f,.15f, .00f,.00f,.30f,122, 0,D},
             {{0,{36}},{2,{36}},{4,{43}},{6,{36}},{8,{39}},{10,{36}},{12,{41}},{14,{43}}}),
        make("WOBBLE DEEP", BASS, {.60f,.20f,.30f,.30f,.55f,.20f, .15f,.40f,.85f, .01f,.50f,.80f,.20f, .62f,.60f,.75f,140, 0,R},
             {{0,{31}},{4,{31}},{8,{34}},{12,{29}}}),

        // ------------------------------------------------------------ STRINGS
        make("SILK STRINGS", STRINGS, {.30f,.20f,.55f,.55f,.10f,.10f, .45f,.80f,.64f, .50f,.60f,.85f,.60f, .62f,.08f,.90f,90, 2,R},
             {{0,{48,55,60,63}},{8,{44,51,56,60}}}),
        make("ORBIT ENSEMBLE", STRINGS, {.25f,.00f,.60f,.50f,.15f,.10f, .40f,.20f,.64f, .55f,.60f,.90f,.70f, .60f,.10f,.95f,86, 2,P},
             {{0,{51,55,58,62}},{8,{50,53,58,62}}}),
        make("TAPE CELLOS", STRINGS, {.40f,.20f,.50f,.40f,.20f,.20f, .35f,.60f,.72f, .35f,.50f,.80f,.45f, .58f,.12f,.75f,96, 0,R},
             {{0,{36,43}},{4,{39,46}},{8,{41,48}},{12,{43,50}}}),
        make("PIZZICATO", STRINGS, {.45f,.20f,.40f,.60f,.10f,.40f, .40f,.55f,0.95f, .00f,.25f,.00f,.25f, .00f,.00f,.30f,108, 2,R},
             {{0,{60}},{2,{63}},{4,{67}},{6,{63}},{8,{58}},{10,{62}},{12,{65}},{14,{62}}}),

        // ------------------------------------------------------------ STABS
        make("SPACE STABS", STABS, {.60f,.55f,.20f,.62f,.40f,.60f, .45f,.75f,.64f, .00f,.35f,.00f,.45f, .25f,.10f,.25f,118, 2,R},
             {{0,{60,63,67,70}},{3,{60,63,67,70}},{6,{58,62,65,69}},{10,{56,60,63,67}},{14,{58,62,65}}}),
        make("HOUSE CHORD", STABS, {.45f,.20f,.30f,.55f,.35f,.55f, .35f,.45f,.66f, .00f,.30f,.10f,.30f, .20f,.05f,.30f,124, 0,D},
             {{2,{55,58,62,65}},{6,{55,58,62,65}},{10,{53,56,60,63}},{14,{53,56,60,63}}}),
        make("DUB STAB", STABS, {.40f,.40f,.20f,.45f,.40f,.50f, .55f,.75f,.68f, .00f,.30f,.00f,.35f, .10f,.05f,.25f,100, 3,D},
             {{4,{51,55,58}},{12,{51,55,58}}}),
        make("BRASS HIT", STABS, {.70f,.20f,.50f,.50f,.20f,.60f, .30f,.60f,.66f, .03f,.35f,.30f,.30f, .55f,.06f,.35f,116, 0,R},
             {{0,{48,55,60,63}},{3,{48,55,60,63}},{10,{46,53,58,62}}}),

        // ------------------------------------------------------------ PLONK
        make("CHROME BELLS", PLONK, {.45f,.75f,.05f,.75f,.10f,.20f, .45f,.35f,.62f, .00f,.55f,.00f,.60f, .15f,.05f,.40f,110, 1,P},
             {{0,{60,72}},{3,{79}},{6,{76}},{10,{84}},{13,{79}}}),
        make("WOOD PLONK", PLONK, {.50f,.60f,.05f,.70f,.15f,.30f, .30f,.50f,1.00f, .00f,.20f,.00f,.20f, .00f,.00f,.30f,112, 2,R},
             {{0,{60}},{3,{67}},{5,{63}},{8,{70}},{11,{60}},{13,{65}}}),
        make("MARIMBA BOX", PLONK, {.40f,.60f,.00f,.80f,.10f,.20f, .30f,.40f,0.95f, .00f,.30f,.00f,.30f, .00f,.00f,.30f,118, 2,D},
             {{0,{72}},{2,{67}},{4,{70}},{6,{75}},{8,{72}},{10,{79}},{12,{77}},{14,{70}}}),
        make("KALIMBA DROP", PLONK, {.55f,.40f,.10f,.75f,.20f,.20f, .45f,.70f,0.95f, .00f,.35f,.00f,.40f, .30f,.05f,.30f,96, 3,R},
             {{0,{60}},{3,{63}},{6,{67}},{9,{72}},{12,{70}}}),
        make("STEEL DRUM", PLONK, {.50f,.80f,.10f,.80f,.10f,.20f, .35f,.50f,0.95f, .00f,.30f,.05f,.30f, .55f,.04f,.30f,110, 2,R},
             {{0,{67}},{2,{72}},{3,{75}},{6,{74}},{8,{72}},{10,{67}},{11,{70}},{14,{63}}}),

        // ------------------------------------------------------------ BLEEP
        make("NEON BLEEPS", BLEEP, {.55f,.95f,.10f,.80f,.30f,.30f, .40f,.60f,.90f, .00f,.18f,.00f,.15f, .60f,.10f,.30f,124, 2,D},
             {{0,{84}},{2,{79}},{3,{87}},{6,{82}},{8,{84}},{10,{91}},{11,{87}},{14,{79}}}),
        make("MODEM CHIRP", BLEEP, {.80f,1.0f,.40f,.90f,.30f,.40f, .45f,.65f,.80f, .00f,.10f,.00f,.08f, .90f,.20f,.20f,128, 1,D},
             {{0,{96}},{1,{91}},{4,{99}},{7,{94}},{9,{87}},{12,{100}},{13,{96}}}),
        make("ROBOT TALK", BLEEP, {.70f,.60f,.50f,.60f,.60f,.70f, .35f,.60f,.76f, .00f,.15f,.00f,.10f, .70f,.30f,.25f,120, 0,P},
             {{0,{72}},{2,{79}},{5,{75}},{6,{84}},{9,{72}},{11,{82}},{14,{77}}}),
        make("PIXEL RAIN", BLEEP, {.50f,.80f,.20f,.85f,.20f,.20f, .50f,.70f,.80f, .00f,.12f,.00f,.10f, .50f,.05f,.20f,132, 2,D},
             {{0,{84}},{1,{87}},{3,{91}},{4,{94}},{6,{96}},{7,{91}},{9,{87}},{10,{84}},{12,{99}},{13,{96}},{15,{91}}}),
        make("SONAR PING", BLEEP, {.45f,.40f,.00f,.90f,.10f,.10f, .60f,.90f,0.95f, .00f,.40f,.00f,.60f, .00f,.00f,.20f,90, 2,R},
             {{0,{84}},{8,{79}}}),

        // ------------------------------------------------------------ FX
        make("RISER", FXS, {.60f,.80f,.50f,.20f,.70f,.10f, .50f,.80f,0.80f, .90f,.90f,1.0f,.70f, .75f,.50f,1.0f,120, 1,R},
             {{0,{48,60}}}),
        make("LASER ZAP", FXS, {1.0f,1.0f,.70f,.90f,.50f,.90f, .40f,.60f,.65f, .00f,.12f,.00f,.10f, .95f,.50f,.20f,128, 1,D},
             {{0,{96}},{6,{91}},{11,{99}}}),
        make("WIND TUNNEL", FXS, {.30f,1.0f,1.0f,.30f,.65f,.00f, .60f,.10f,.62f, .80f,.90f,1.0f,.90f, .20f,.70f,1.0f,80, 1,P},
             {{0,{60,72}}}),
        make("ALARM SWEEP", FXS, {.60f,.60f,.30f,.60f,.40f,.30f, .30f,.50f,0.80f, .10f,.90f,.90f,.30f, .55f,1.0f,.90f,120, 2,D},
             {{0,{79}},{8,{79}}}),

        // ------------------------------------------------------------ TRIPPY
        make("MELT", TRIPPY, {.50f,.60f,.30f,.50f,.45f,.30f, .60f,.20f,0.80f, .20f,.60f,.60f,.60f, .35f,.45f,.60f,92, 3,P},
             {{0,{60}},{3,{67}},{6,{63}},{8,{70}},{11,{67}},{14,{62}}}),
        make("KALEIDOSCOPE", TRIPPY, {.60f,.80f,.25f,.65f,.35f,.40f, .55f,.70f,.66f, .00f,.30f,.20f,.40f, .50f,.30f,.35f,116, 2,D},
             {{0,{60}},{1,{67}},{2,{72}},{3,{75}},{4,{79}},{5,{84}},{6,{79}},{7,{75}},
              {8,{58}},{9,{65}},{10,{70}},{11,{74}},{12,{77}},{13,{82}},{14,{77}},{15,{74}}}),
        make("LIQUID MIRROR", TRIPPY, {.45f,.40f,.20f,.55f,.50f,.50f, .65f,.50f,.68f, .05f,.40f,.30f,.50f, .40f,.35f,.45f,104, 0,P},
             {{0,{60,67}},{4,{63,70}},{8,{58,65}},{12,{62,69}}}),
        make("DREAM MACHINE", TRIPPY, {.35f,.40f,.15f,.50f,.30f,.20f, .60f,.80f,.64f, .30f,.60f,.70f,.70f, .25f,.40f,.70f,88, 3,D},
             {{0,{60,63,67}},{6,{58,62,65}},{12,{56,60,63}}}),
        make("WONKY WHEEL", TRIPPY, {.70f,.60f,.40f,.50f,.55f,.65f, .40f,.35f,.72f, .00f,.25f,.20f,.20f, .45f,.40f,.40f,112, 0,P},
             {{0,{48}},{2,{51}},{3,{60}},{6,{55}},{7,{48}},{10,{58}},{11,{63}},{13,{48}}}),

        // ------------------------------------------------------------ COSMIC
        make("STARFIELD", COSMIC, {.40f,1.0f,.10f,.70f,.20f,.10f, .70f,1.0f,.72f, .30f,.80f,.40f,.95f, .10f,.20f,.50f,70, 1,R},
             {{0,{84}},{5,{91}},{10,{87}},{13,{96}}}),
        make("EVENT HORIZON", COSMIC, {.50f,1.0f,.50f,.35f,.40f,.20f, .65f,1.0f,.66f, .90f,.90f,.90f,1.0f, .08f,.45f,1.0f,60, 3,R},
             {{0,{36,43,54,61}}}),
        make("PULSAR", COSMIC, {.65f,.80f,.20f,.60f,.45f,.50f, .60f,.85f,.72f, .00f,.20f,.00f,.30f, .20f,.30f,.20f,100, 1,D},
             {{0,{72}},{3,{72}},{6,{72}},{9,{79}},{12,{72}}}),
        make("ZERO GRAVITY", COSMIC, {.30f,.40f,.20f,.55f,.25f,.15f, .55f,.08f,.64f, .60f,.70f,.80f,.85f, .12f,.30f,.95f,76, 3,P},
             {{0,{50,57,60,65}},{8,{48,55,58,64}}}),
        make("ALIEN CHOIR", COSMIC, {.35f,.60f,.30f,.45f,.55f,.10f, .60f,.95f,.64f, .70f,.80f,.90f,.85f, .58f,.35f,1.0f,68, 2,R},
             {{0,{55,58,62,66}}}),
        make("SATELLITE", COSMIC, {.55f,.80f,.10f,.80f,.20f,.30f, .55f,.75f,.72f, .00f,.50f,.00f,.70f, .20f,.10f,.25f,84, 1,D},
             {{0,{91}},{2,{96}},{7,{94}},{11,{89}}}),

        // ------------------------------------------------------------ 80s
        make("CRYSTAL KEYS", EIGHTIES, {.45f,.20f,.05f,.75f,.10f,.30f, .40f,.25f,.80f, .00f,.55f,.20f,.35f, .58f,.06f,.70f,96, 2,P},
             {{0,{48,55,58,62}},{6,{44,51,55,60}},{10,{46,53,58,62}},{14,{46,53,58}}}),
        make("NEON SLAP", EIGHTIES, {.75f,.60f,.20f,.60f,.30f,.70f, .20f,.35f,.84f, .00f,.25f,.10f,.15f, .00f,.00f,.30f,112, 0,D},
             {{0,{36}},{2,{48}},{3,{36}},{6,{46}},{7,{36}},{8,{39}},{10,{51}},{11,{41}},{13,{43}},{14,{55}}}),
        make("MIAMI BRASS", EIGHTIES, {.70f,.20f,.45f,.55f,.20f,.55f, .40f,.65f,.70f, .06f,.40f,.70f,.30f, .55f,.08f,.50f,108, 0,R},
             {{0,{48,55,60,63}},{3,{48,55,60,63}},{8,{46,53,58,62}},{11,{44,51,56,60}}}),
        make("ARCADE BELLS", EIGHTIES, {.50f,.75f,.10f,.80f,.10f,.20f, .50f,.70f,.74f, .00f,.65f,.00f,.60f, .20f,.04f,.40f,104, 1,R},
             {{0,{72}},{2,{75}},{4,{79}},{6,{84}},{8,{82}},{10,{79}},{12,{75}},{14,{77}}}),
        make("SUNSET STRINGS", EIGHTIES, {.30f,.20f,.55f,.55f,.15f,.10f, .55f,.20f,.66f, .45f,.60f,.85f,.60f, .60f,.10f,.95f,92, 2,P},
             {{0,{48,55,60,63}},{8,{44,51,56,63}}}),
        make("VHS CHOIR", EIGHTIES, {.25f,.00f,.25f,.45f,.50f,.10f, .55f,.80f,.66f, .65f,.70f,.85f,.75f, .58f,.35f,1.0f,84, 3,R},
             {{0,{55,60,63,67}},{8,{53,56,60,65}}}),

        // ------------------------------------------------------------ 90s
        make("ACID SQUELCH", NINETIES, {.50f,.20f,.25f,.25f,.85f,.90f, .25f,.45f,.82f, .00f,.20f,.00f,.12f, .25f,.05f,.30f,128, 0,D},
             {{0,{36}},{1,{36}},{2,{48}},{3,{36}},{4,{39}},{5,{36}},{6,{46}},{7,{36}},
              {8,{36}},{9,{51}},{10,{36}},{11,{43}},{12,{36}},{13,{48}},{14,{41}},{15,{36}}}),
        make("ORGAN BASS", NINETIES, {.35f,.20f,.15f,.50f,.20f,.30f, .15f,.30f,1.00f, .00f,.30f,.40f,.12f, .00f,.00f,.30f,124, 2,D},
             {{2,{36}},{3,{48}},{6,{36}},{7,{46}},{10,{36}},{11,{48}},{14,{34}},{15,{46}}}),
        make("PIANO HOUSE", NINETIES, {.50f,.40f,.10f,.70f,.15f,.35f, .35f,.60f,.70f, .00f,.50f,.10f,.35f, .00f,.00f,.40f,122, 2,R},
             {{0,{55,60,63,67}},{3,{55,60,63,67}},{6,{55,60,63,67}},{10,{53,56,60,65}},{13,{53,56,60,65}}}),
        make("RAVE HOOVER", NINETIES, {.80f,.60f,.70f,.55f,.40f,.30f, .55f,.35f,.66f, .05f,.60f,.80f,.40f, .60f,.40f,.85f,132, 0,P},
             {{0,{48,60}},{8,{46,58}},{12,{44,56}}}),
        make("STAB 92", NINETIES, {.70f,.60f,.40f,.55f,.35f,.60f, .45f,.60f,.68f, .00f,.30f,.00f,.30f, .20f,.05f,.25f,130, 0,R},
             {{0,{48,51,55,58}},{3,{48,51,55,58}},{6,{46,50,53,58}},{10,{44,48,51,55}},{12,{46,50,53,58}}}),
        make("BLEEP TECHNO", NINETIES, {.20f,.00f,.00f,.90f,.10f,.10f, .35f,.50f,1.00f, .00f,.15f,.00f,.10f, .00f,.00f,.20f,126, 2,D},
             {{0,{48}},{3,{60}},{6,{55}},{8,{48}},{10,{63}},{11,{60}},{14,{51}}}),

        // ------------------------------------------------------------ 20s
        make("TRANCE PLUCK", TWENTIES, {.50f,.40f,.15f,.60f,.30f,.60f, .45f,.65f,.78f, .00f,.25f,.00f,.25f, .00f,.00f,.30f,138, 2,D},
             {{0,{60}},{1,{67}},{2,{72}},{3,{75}},{4,{60}},{5,{67}},{6,{72}},{7,{74}},
              {8,{56}},{9,{63}},{10,{68}},{11,{72}},{12,{58}},{13,{65}},{14,{70}},{15,{74}}}),
        make("TECHNO RUMBLE", TWENTIES, {.40f,.20f,.30f,.30f,.30f,.30f, .60f,.90f,.86f, .00f,.40f,.30f,.40f, .10f,.15f,.50f,132, 0,R},
             {{2,{36}},{3,{36}},{6,{36}},{7,{36}},{10,{36}},{11,{36}},{14,{36}},{15,{36}}}),
        make("HYPNO ARP", TWENTIES, {.60f,.80f,.20f,.45f,.60f,.50f, .45f,.20f,.76f, .00f,.25f,.00f,.20f, .10f,.40f,.30f,128, 0,P},
             {{0,{48}},{1,{55}},{2,{63}},{3,{48}},{4,{55}},{5,{63}},{6,{48}},{7,{55}},
              {8,{63}},{9,{48}},{10,{55}},{11,{63}},{12,{48}},{13,{55}},{14,{63}},{15,{51}}}),
        make("MINIMAL BLIP", TWENTIES, {.30f,1.0f,.05f,.80f,.20f,.20f, .45f,.70f,1.00f, .00f,.12f,.00f,.10f, .00f,.00f,.20f,126, 2,D},
             {{0,{72}},{5,{79}},{9,{75}},{14,{82}}}),
        make("AFTERHOURS PAD", TWENTIES, {.30f,.40f,.20f,.45f,.25f,.15f, .55f,.40f,.66f, .60f,.70f,.85f,.80f, .12f,.30f,1.0f,124, 3,P},
             {{0,{48,55,58,63}},{8,{44,51,55,60}}}),
        make("METAL CLANG", TWENTIES, {.90f,1.0f,.60f,.65f,.30f,.40f, .50f,.75f,.72f, .00f,.30f,.00f,.40f, .00f,.00f,.20f,130, 1,R},
             {{0,{48}},{6,{55}},{11,{51}}}),
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
    // Tempo belongs to the player, not the patch: it starts at 120 and only
    // changes when the player changes it. (The patch's tempo stays in the data.)
    for (int id = 0; id < kPresetParams; ++id)
        if (id != P_TEMPO) send(Cmd{CMD_PARAM, id, 0, p.params[id]});
    for (int s = 0; s < kSteps; ++s)
        for (int n = 0; n < kNotesPerStep; ++n)
            if (p.notes[s][n] >= 0) send(Cmd{CMD_STEP_NOTE, s, p.notes[s][n], (float)n});
    // Synth-only: drums stay off.
}

}  // namespace bergz
