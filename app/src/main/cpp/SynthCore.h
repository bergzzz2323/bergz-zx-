// Bergz ZX 700 — synth core.
// Header-only and platform-free: the Android audio callback and the desktop
// test renderer both drive this same code. Nothing here allocates or locks
// once constructed, so it is safe to call from a real-time audio thread.
#pragma once

#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <mutex>
#include <vector>

namespace bergz {

constexpr float kTwoPi = 6.283185307179586f;
constexpr float kPi = 3.14159265358979f;
constexpr int kSteps = 16;          // steps per page (and per preset pattern)
constexpr int kMaxSteps = 64;       // longest pattern: 4 pages
constexpr int kOps = 6;
constexpr int kDrumLanes = 4;
constexpr int kSeqVoices = 16;      // notes the sequencer can have sounding at once
constexpr int kLiveVoices = 8;      // plus notes played live (KEYS / MIDI), kept separate
constexpr int kVoices = kSeqVoices + kLiveVoices;
constexpr int kNotesPerStep = 4;    // up to a 4-note chord on any step
constexpr int kRates = 6;           // step length: 1/32, 1/16, 1/8, 1/4, 1/2, 1 bar
constexpr double kRateSixteenths[kRates] = {0.5, 1.0, 2.0, 4.0, 8.0, 16.0};

// ---- Parameter ids (keep in sync with NativeEngine.kt) ---------------------
enum Param : int {
    P_FM_DEPTH = 0, P_HARMONIC, P_FEEDBACK,
    P_CUTOFF, P_RESO, P_ENV_AMT,
    P_DLY_MIX, P_DLY_FB, P_VOLUME,     // delay amount / feedback
    P_ATTACK, P_DECAY, P_SUSTAIN, P_RELEASE,
    P_LFO_RATE, P_LFO_DEPTH,
    P_GATE,       // note length, 0.1 .. 8 steps
    P_REV_MIX, P_REV_SIZE,             // reverb amount / size
    P_PH_MIX, P_PH_RATE,               // phaser amount / rate
    P_TEMPO,      // actual BPM, not 0..1
    P_ALGORITHM,  // 0 .. kAlgorithms-1
    P_FX_TYPE,    // the patch's featured effect (preset data; all effects can run together)
    // Per-effect settings (FX settings panel). Not part of presets.
    P_REV_TONE, P_REV_WIDTH,
    P_DLY_TIME,   // 0..1 -> 1/16, 1/8, dotted 1/8, 1/4
    P_DLY_TONE, P_DLY_PING,
    P_PH_DEPTH, P_PH_FEEDBACK,
    P_REV_ON, P_DLY_ON, P_PH_ON,       // on/off lights; stack any combination
    P_TRANSPOSE,  // semitones -12..+12 added to the sequencer's notes (not live MIDI)
    // Pattern and sync settings (v0.7). Real values, not 0..1.
    P_PAT_LEN,    // steps in the pattern, 1..64
    P_RATE,       // step length index into kRateSixteenths (1 = 1/16)
    P_SWING,      // 0.50 straight .. 0.75 heavy shuffle
    P_SYNC_MODE,  // 0 AUTO (follow clock when present), 1 INT, 2 EXT
    P_SYNC_OFFSET,// ms, -60..+60: positive plays later
    P_START_Q,    // starting on external clock: 0 next bar, 1 next beat, 2 next clock tick
    P_CLOCK_OUT,  // 1 = send MIDI clock / start / stop while Bergz is the master
    P_COUNT
};
constexpr int kPresetParams = P_REV_TONE;  // presets store everything before this
// Panel order: delay is the first effect and the only one on at start-up.
enum FxType : int { FX_DELAY = 0, FX_REVERB = 1, FX_PHASER = 2, FX_COUNT };

// Start-up value of every parameter (also restored by CMD_RESET_DEFAULTS).
constexpr float kDefaultParams[P_COUNT] = {
    0.80f, 0.40f, 0.25f,         // fm depth, harmonic, feedback
    0.45f, 0.35f, 0.55f,         // cutoff, reso, env amt
    0.25f, 0.45f, 0.70f,         // delay mix, delay feedback, volume
    0.02f, 0.30f, 0.20f, 0.20f,  // A D S R
    0.30f, 0.15f,                // lfo rate, depth
    0.46f,                       // gate (~0.75 step)
    0.30f, 0.60f,                // reverb mix, size
    0.45f, 0.30f,                // phaser mix, rate
    120.f, 0.f, (float)FX_DELAY, // tempo, algorithm, featured fx
    0.67f, 1.0f,                 // reverb tone, width
    0.67f, 0.32f, 1.0f,          // delay time (dotted 1/8), tone, ping-pong
    0.73f, 0.67f,                // phaser depth, intensity
    0.f, 1.f, 0.f,               // reverb off, delay ON, phaser off
    0.f,                         // transpose
    16.f, 1.f, 0.5f,             // pattern length, rate (1/16), swing (straight)
    0.f, 0.f, 0.f, 0.f,          // sync AUTO, offset 0 ms, start on next bar, clock out off
};

// Motion recording: knob moves are stored per parameter at 8 points per step.
constexpr int kTicksPerStep = 8;
constexpr int kMotionTicks = kMaxSteps * kTicksPerStep;  // up to 512 (the loop follows the pattern length)
constexpr int kMotionParams = P_TEMPO;  // every macro before tempo can be recorded

enum CmdType : int {
    CMD_PARAM,         // a = param, v = value
    CMD_STEP_NOTE,     // a = step, b = MIDI note or -1, v = chord slot 0..3
    CMD_DRUM,          // a = lane, b = step, v > 0.5 on
    CMD_LOCK,          // a = step, v = cutoff lock or -1
    CMD_PLAY, CMD_STOP,
    CMD_RECORD,        // v > 0.5 arms motion recording
    CMD_TOUCH,         // a = param, b = 1 finger down / 0 finger up
    CMD_CLEAR_MOTION,  // a = param, or -1 for every lane
    CMD_CLEAR_PATTERN, // empties notes, drums and locks
    CMD_RESET_DEFAULTS,// every parameter back to kDefaultParams (app start)
    // live MIDI
    CMD_NOTE_ON,       // a = note, v = velocity 0..1
    CMD_NOTE_OFF,      // a = note
    CMD_ALL_NOTES_OFF,
    CMD_PITCH_BEND,    // v = -1..1 (+/- 2 semitones)
    CMD_MIDI_CLOCK,    // 24 per quarter note
    CMD_MIDI_START, CMD_MIDI_CONTINUE, CMD_MIDI_STOP,
    CMD_STEP_PROPS,    // a = step, b = chance% | ratchet << 8 | tie << 16
};

// Clock-out event types (MIDI status bytes), read with popClockOut().
constexpr int kClockTick = 0xF8, kClockStart = 0xFA, kClockStop = 0xFC;

// t: for MIDI clock in, the arrival time (CLOCK_MONOTONIC ns, 0 = unknown);
// for clock-out events, the engine sample the event belongs to.
struct Cmd { int type; int a; int b; float v; int64_t t = 0; };

// ---- Command queue: lock-free for the audio thread (single consumer). -------
// Several threads may post (UI, MIDI), so pushes are serialised by a mutex the
// audio thread never touches.
template <int N>
class SpscQueue {
public:
    bool push(const Cmd& c) {
        std::lock_guard<std::mutex> lock(pushMutex_);
        const int h = head_.load(std::memory_order_relaxed);
        const int next = (h + 1) % N;
        if (next == tail_.load(std::memory_order_acquire)) return false;  // full
        buf_[h] = c;
        head_.store(next, std::memory_order_release);
        return true;
    }
    bool pop(Cmd& c) {
        const int t = tail_.load(std::memory_order_relaxed);
        if (t == head_.load(std::memory_order_acquire)) return false;  // empty
        c = buf_[t];
        tail_.store((t + 1) % N, std::memory_order_release);
        return true;
    }
private:
    std::array<Cmd, N> buf_{};
    std::atomic<int> head_{0}, tail_{0};
    std::mutex pushMutex_;
};

// ---- Small DSP building blocks ---------------------------------------------
struct Noise {
    uint32_t s = 0x9E3779B9u;
    float next() {
        s ^= s << 13; s ^= s >> 17; s ^= s << 5;
        return (float)(int32_t)s * (1.0f / 2147483648.0f);
    }
};

// ADSR with linear attack and exponential decay/release.
struct Env {
    enum Stage { Idle, Attack, Decay, Sustain, Release } stage = Idle;
    float value = 0.f;
    float attackInc = 0.01f, decayCoef = 0.999f, releaseCoef = 0.999f, sustain = 0.5f;

    static float coefFor(float seconds, float sr) { return std::exp(-6.9f / (seconds * sr)); }
    void set(float a, float d, float s, float r, float sr) {
        attackInc = 1.f / (a * sr);
        decayCoef = coefFor(d, sr);
        releaseCoef = coefFor(r, sr);
        sustain = s;
    }
    void gateOn() { stage = Attack; }
    void gateOff() { if (stage != Idle) stage = Release; }
    float tick() {
        switch (stage) {
            case Attack:
                value += attackInc;
                if (value >= 1.f) { value = 1.f; stage = Decay; }
                break;
            case Decay:
                value = sustain + (value - sustain) * decayCoef;
                if (value - sustain < 1e-4f) stage = Sustain;
                break;
            case Sustain: value = sustain; break;
            case Release:
                value *= releaseCoef;
                if (value < 1e-5f) { value = 0.f; stage = Idle; }
                break;
            case Idle: break;
        }
        return value;
    }
};

// Zero-delay-feedback state-variable low-pass filter.
struct Svf {
    float ic1 = 0.f, ic2 = 0.f;
    float process(float x, float cutoffHz, float reso, float sr) {
        cutoffHz = std::fmin(cutoffHz, sr * 0.45f);
        const float g = std::tan(kPi * cutoffHz / sr);
        const float k = 2.f - 1.92f * reso;
        const float a1 = 1.f / (1.f + g * (g + k));
        const float v3 = x - ic2;
        const float v1 = a1 * ic1 + g * a1 * v3;
        const float v2 = ic2 + g * v1;
        ic1 = 2.f * v1 - ic1 + 1e-20f;
        ic2 = 2.f * v2 - ic2 + 1e-20f;
        return v2;
    }
};

// ---- FM algorithms ----------------------------------------------------------
// Op index 0 = OP1. mods[i] is a bitmask of the operators that modulate op i.
// Bergz's own starter set; the full algorithm table comes with the full engine.
struct Algorithm { std::array<uint8_t, kOps> mods; uint8_t carriers; };

constexpr int kAlgorithms = 4;
constexpr Algorithm kAlgos[kAlgorithms] = {
    // 0: two stacks  (2->1) + (6->5->4->3)        punchy basses
    {{1u << 1, 0, 1u << 3, 1u << 4, 1u << 5, 0}, (1u << 0) | (1u << 2)},
    // 1: one tall stack 6->5->4->3->2->1          bells, metallic
    {{1u << 1, 1u << 2, 1u << 3, 1u << 4, 1u << 5, 0}, (1u << 0)},
    // 2: three pairs (2->1) (4->3) (6->5)          bleeps, organs
    {{1u << 1, 0, 1u << 3, 0, 1u << 5, 0}, (1u << 0) | (1u << 2) | (1u << 4)},
    // 3: branch (2 + 3->1, 4->3) plus (6->5)       pads, evolving
    {{(1u << 1) | (1u << 2), 0, 1u << 3, 0, 1u << 5, 0}, (1u << 0) | (1u << 4)},
};

// Modulator ratio sets chosen by the HARMONIC macro.
constexpr int kHarmSets = 6;
constexpr float kHarmRatios[kHarmSets][kOps] = {
    {1, 1, 1, 1, 1, 1},
    {1, 2, 1, 2, 1, 2},
    {1, 2, 0.5f, 3, 1.41f, 7},
    {1, 3, 1, 1, 2, 4},
    {1, 4, 1, 5, 2, 9},
    {1, 7, 1, 3.5f, 5, 11},
};
constexpr float kCarrierRatio[kOps] = {1, 1, 0.5f, 1, 2, 1};
// small detune on the extra carriers thickens pads
constexpr float kCarrierDetune[kOps] = {1.f, 1.f, 1.0021f, 1.f, 0.9983f, 1.f};
constexpr float kOpLevel[kOps] = {1.f, 0.78f, 0.92f, 0.64f, 0.55f, 0.40f};

// ---- One FM voice -----------------------------------------------------------
struct FmVoice {
    std::array<double, kOps> phase{};
    std::array<float, kOps> out{};
    std::array<Env, kOps> env{};
    float fbPrev1 = 0.f, fbPrev2 = 0.f;
    float freq = 65.4f;
    int note = -1;
    int gateLeft = 0;      // samples until note-off (0 = held until noteOff)
    uint32_t order = 0;    // for voice stealing (oldest goes first)
    bool live = false;     // held from KEYS / MIDI (cleared on note-off)
    bool livePool = false; // belongs to the live pool (stays true while it rings out)
    float velocity = 1.f;

    void noteOn(int midi, int gateSamples, uint32_t ord, float vel = 1.f, bool isLive = false) {
        note = midi;
        live = isLive;
        livePool = isLive;
        velocity = vel;
        freq = 440.f * std::pow(2.f, (midi - 69) / 12.f);
        gateLeft = gateSamples;
        order = ord;
        for (auto& e : env) e.gateOn();
    }
    void noteOff() { for (auto& e : env) e.gateOff(); }
    bool active() const {
        for (auto& e : env) if (e.stage != Env::Idle) return true;
        return false;
    }
    bool releasing() const { return env[0].stage == Env::Release; }

    float tick(const Algorithm& alg, int harmSet, float fmDepth, float feedback, float pitchMul, float sr) {
        if (gateLeft > 0 && --gateLeft == 0) noteOff();
        float carrierSum = 0.f;
        int carriers = 0;
        // Modulators always have a higher index than what they modulate,
        // so computing from OP6 down to OP1 resolves every input first.
        for (int i = kOps - 1; i >= 0; --i) {
            const bool isCarrier = alg.carriers & (1u << i);
            float mod = 0.f;
            for (int m = 0; m < kOps; ++m)
                if (alg.mods[i] & (1u << m)) mod += out[m];
            if (i == kOps - 1) mod += feedback * 1.5f * (fbPrev1 + fbPrev2);

            const float ratio = isCarrier ? kCarrierRatio[i] * kCarrierDetune[i] : kHarmRatios[harmSet][i];
            phase[i] += (double)(freq * pitchMul * ratio / sr);
            phase[i] -= std::floor(phase[i]);

            const float e = env[i].tick();
            const float s = std::sin(kTwoPi * (float)phase[i] + mod);
            if (isCarrier) {
                out[i] = s * kOpLevel[i] * e;
                carrierSum += out[i];
                ++carriers;
            } else {
                out[i] = s * kOpLevel[i] * e * fmDepth * 6.f;  // phase offset in radians
            }
            if (i == kOps - 1) { fbPrev2 = fbPrev1; fbPrev1 = s * e; }
        }
        return carriers ? velocity * carrierSum / (float)carriers : 0.f;
    }
};

// ---- Drum synth -------------------------------------------------------------
struct DrumVoice {
    int kind = 0;  // 0 kick, 1 snare, 2 hat, 3 clap
    float t = 1e9f;
    float phase = 0.f, hp = 0.f, bp1 = 0.f, bp2 = 0.f;
    void trigger() { t = 0.f; phase = 0.f; }
    float tick(Noise& n, float sr) {
        if (t > 1.5f) return 0.f;
        const float dt = 1.f / sr;
        float y = 0.f;
        switch (kind) {
            case 0: {  // kick: pitch-swept sine
                const float f = 45.f + 110.f * std::exp(-t * 35.f);
                phase += f * dt; phase -= std::floor(phase);
                y = std::sin(kTwoPi * phase) * std::exp(-t * 7.f);
                break;
            }
            case 1: {  // snare: tone + noise
                phase += 190.f * dt; phase -= std::floor(phase);
                const float nz = n.next();
                hp = 0.6f * hp + 0.4f * nz;
                y = 0.45f * std::sin(kTwoPi * phase) * std::exp(-t * 28.f)
                  + 0.6f * (nz - hp) * std::exp(-t * 16.f);
                break;
            }
            case 2: {  // hat: high-passed noise
                const float nz = n.next();
                const float h = nz - hp; hp = nz;
                y = 0.35f * h * std::exp(-t * 60.f);
                break;
            }
            case 3: {  // clap: three quick bursts then a tail
                const float nz = n.next();
                bp1 += 0.25f * (nz - bp1);
                bp2 += 0.25f * (bp1 - bp2);
                const float band = bp1 - bp2;
                float envv = std::exp(-t * 14.f);
                if (t < 0.033f) envv = std::fmod(t, 0.011f) < 0.006f ? 1.f : 0.f;
                y = 1.6f * band * envv;
                break;
            }
        }
        t += dt;
        return y;
    }
};

// ---- Effects ----------------------------------------------------------------
// Small stereo reverb in the classic comb + all-pass style.
struct Reverb {
    struct Comb {
        std::vector<float> buf; size_t i = 0; float store = 0.f;
        float process(float x, float fb, float damp) {
            const float y = buf[i];
            store = y * (1.f - damp) + store * damp + 1e-20f;
            buf[i] = x + store * fb;
            if (++i >= buf.size()) i = 0;
            return y;
        }
    };
    struct AllPass {
        std::vector<float> buf; size_t i = 0;
        float process(float x) {
            const float b = buf[i];
            buf[i] = x + b * 0.5f;
            if (++i >= buf.size()) i = 0;
            return b - x;
        }
    };
    std::array<Comb, 4> cl, cr;
    std::array<AllPass, 2> al, ar;

    void init(float sr) {
        const float k = sr / 44100.f;
        const int combs[4] = {1116, 1277, 1422, 1557};
        const int aps[2] = {556, 441};
        for (int j = 0; j < 4; ++j) {
            cl[j].buf.assign((size_t)(combs[j] * k), 0.f);
            cr[j].buf.assign((size_t)((combs[j] + 23) * k), 0.f);
        }
        for (int j = 0; j < 2; ++j) {
            al[j].buf.assign((size_t)(aps[j] * k), 0.f);
            ar[j].buf.assign((size_t)((aps[j] + 23) * k), 0.f);
        }
    }
    void process(float in, float size, float tone, float width, float& outL, float& outR) {
        const float fb = 0.70f + 0.28f * size;
        const float damp = 0.65f - 0.6f * tone;  // tone up = brighter tail
        const float x = in * 0.03f;
        float l = 0.f, r = 0.f;
        for (int j = 0; j < 4; ++j) { l += cl[j].process(x, fb, damp); r += cr[j].process(x, fb, damp); }
        for (int j = 0; j < 2; ++j) { l = al[j].process(l); r = ar[j].process(r); }
        const float a = 0.5f + 0.5f * width, b = 0.5f - 0.5f * width;
        outL = l * a + r * b; outR = r * a + l * b;
    }
};

// Four-stage stereo phaser.
struct Phaser {
    float lfo = 0.f;
    float zl[4]{}, zr[4]{}, fbl = 0.f, fbr = 0.f;
    static float stage(float x, float a, float& z) {
        const float y = -a * x + z;
        z = x + a * y + 1e-20f;
        return y;
    }
    static float coef(float f, float sr) {
        const float t = std::tan(kPi * f / sr);
        return (1.f - t) / (1.f + t);
    }
    void process(float in, float rate, float depth, float feedback, float sr, float& outL, float& outR) {
        lfo += (0.05f * std::pow(100.f, rate)) / sr;
        lfo -= std::floor(lfo);
        const float sl = 0.5f + 0.5f * std::sin(kTwoPi * lfo);
        const float sr2 = 0.5f + 0.5f * std::sin(kTwoPi * (lfo + 0.25f));
        const float octaves = 1.f + 4.f * depth;
        const float al = coef(200.f * std::pow(2.f, sl * octaves), sr);
        const float ar = coef(200.f * std::pow(2.f, sr2 * octaves), sr);
        const float fbAmt = 0.9f * feedback;
        float l = in + fbl * fbAmt, r = in + fbr * fbAmt;
        for (int j = 0; j < 4; ++j) { l = stage(l, al, zl[j]); r = stage(r, ar, zr[j]); }
        fbl = l; fbr = r;
        outL = 0.5f * (in + l);
        outR = 0.5f * (in + r);
    }
};

// ---- The whole instrument ---------------------------------------------------
class SynthCore {
public:
    explicit SynthCore(float sampleRate) {
        for (int i = 0; i < P_COUNT; ++i) { p_[i] = kDefaultParams[i]; mirror_[i].store(p_[i]); }
        setSampleRate(sampleRate);
        loadDefaultPattern();
    }

    void setSampleRate(float sr) {
        sr_ = sr;
        delayL_.assign((size_t)(sr * 2.f), 0.f);
        delayR_.assign((size_t)(sr * 2.f), 0.f);
        delayPos_ = 0;
        reverb_.init(sr);
        for (int i = 0; i < kDrumLanes; ++i) drums_[i].kind = i;
        applyEnvelopes();
    }

    // ---- UI thread ----
    bool post(const Cmd& c) { return queue_.push(c); }
    int playhead() const { return playhead_.load(std::memory_order_relaxed); }
    float param(int id) const {
        return (id >= 0 && id < P_COUNT) ? mirror_[id].load(std::memory_order_relaxed) : 0.f;
    }
    uint32_t motionMask() const { return motionMask_.load(std::memory_order_relaxed); }
    bool isPlaying() const { return playingFlag_.load(std::memory_order_relaxed); }
    bool externalSync() const { return extSyncFlag_.load(std::memory_order_relaxed); }
    // Sync state for the UI: bit 0 following external clock, bit 1 clock arriving,
    // bit 2 waiting to start on the next bar / beat.
    int syncState() const { return syncState_.load(std::memory_order_relaxed); }
    // The quarter-note the clock is on (for the beat lights), or -1.
    int beat() const { return beat_.load(std::memory_order_relaxed); }

    // ---- audio thread, before render(): wall time of this block (CLOCK_MONOTONIC ns)
    // and the output latency, so clock in/out can be placed exactly.
    void setBlockTime(int64_t ns, int latencyFrames) { blockNs_ = ns; latencyFrames_ = latencyFrames; }
    uint64_t sampleTime() const { return sampleTime_; }
    // For tests: how many times a step has played notes.
    uint32_t stepsTriggered() const { return stepsTriggered_; }
    // For tests: voices currently sounding in a pool.
    int activeVoices(bool live) const {
        int n = 0;
        for (auto& v : voices_) if (v.active() && v.livePool == live) ++n;
        return n;
    }
    // Clock-out events produced by the last render(): t = engine sample.
    bool popClockOut(Cmd& c) { return clockOut_.pop(c); }

    // ---- desktop renderer (same thread as render) ----
    void setParam(int id, float v) { applyParam(id, v); }
    void command(const Cmd& c) { applyCmd(c); }

    // ---- audio thread. Interleaved stereo float output. ----
    void render(float* out, int frames) {
        drainCommands();
        const Algorithm& alg = kAlgos[algorithm_];
        const int harmSet = std::min(kHarmSets - 1, (int)std::lround(p_[P_HARMONIC] * (kHarmSets - 1)));
        // One 16th note in samples: from the external clock when following it, else the tempo.
        const double samplesPerStep = (following() && spt_ > 0.0) ? spt_ * 6.0 : sr_ * 60.0 / (double)p_[P_TEMPO] / 4.0;
        const int gateSamples = std::max(1, (int)(samplesPerStep * 0.1 * std::pow(80.0, (double)p_[P_GATE])));
        static constexpr double kDelaySteps[4] = {1.0, 2.0, 3.0, 4.0};  // 1/16 1/8 d1/8 1/4
        const int dIdx = std::max(0, std::min(3, (int)std::lround(p_[P_DLY_TIME] * 3.f)));
        const size_t delaySamples = std::min(delayL_.size() - 1, (size_t)(samplesPerStep * kDelaySteps[dIdx]));
        const float dlyCoef = 0.08f + 0.85f * p_[P_DLY_TONE];
        const bool pingPong = p_[P_DLY_PING] > 0.5f;
        const float lfoInc = (0.05f * std::pow(400.f, p_[P_LFO_RATE])) / sr_;

        for (int f = 0; f < frames; ++f) {
            // --- sequencer clock (sample-accurate) ---
            sequencerTick(f, samplesPerStep, gateSamples);

            // --- smoothed controls ---
            const float targetCut = activeLock_ >= 0.f ? activeLock_ : p_[P_CUTOFF];
            smCut_ += 0.002f * (targetCut - smCut_);
            smDepth_ += 0.002f * (p_[P_FM_DEPTH] - smDepth_);
            smVol_ += 0.002f * (p_[P_VOLUME] - smVol_);
            // effect amounts fade with their on/off light, so switching never clicks
            smDly_ += 0.002f * ((p_[P_DLY_ON] > 0.5f ? p_[P_DLY_MIX] : 0.f) - smDly_);
            smRev_ += 0.002f * ((p_[P_REV_ON] > 0.5f ? p_[P_REV_MIX] : 0.f) - smRev_);
            smPh_  += 0.002f * ((p_[P_PH_ON]  > 0.5f ? p_[P_PH_MIX]  : 0.f) - smPh_);

            // --- voices ---
            lfoPhase_ += lfoInc; lfoPhase_ -= std::floor(lfoPhase_);
            const float lfoRaw = std::sin(kTwoPi * lfoPhase_);
            const float vibrato = (1.f + 0.004f * lfoRaw * p_[P_LFO_DEPTH]) * pitchBend_;
            float s = 0.f;
            for (auto& v : voices_)
                if (v.active()) s += v.tick(alg, harmSet, smDepth_, p_[P_FEEDBACK], vibrato, sr_);
            s *= 0.55f;

            const float lfoCut = lfoRaw * p_[P_LFO_DEPTH] * 2.f;
            const float fenv = filterEnv_.tick() * p_[P_ENV_AMT] * 6.f;
            const float cutoffHz = 30.f * std::pow(2.f, smCut_ * 9.3f + fenv + lfoCut);
            s = filter_.process(s, cutoffHz, p_[P_RESO], sr_);

            // --- effects: phaser (insert) -> delay -> reverb. Any combination can run. ---
            // Every effect always runs, so buffers stay warm and turning one on is seamless;
            // its output is simply faded by the on/off light.
            float pl, pr;
            phaser_.process(s, p_[P_PH_RATE], p_[P_PH_DEPTH], p_[P_PH_FEEDBACK], sr_, pl, pr);
            const float dryL = s * (1.f - smPh_) + pl * smPh_;
            const float dryR = s * (1.f - smPh_) + pr * smPh_;
            const float toFx = 0.5f * (dryL + dryR);

            const size_t rp = (delayPos_ + delayL_.size() - delaySamples) % delayL_.size();
            const float dl = delayL_[rp], dr = delayR_[rp];
            const float fb = p_[P_DLY_FB] * 0.85f;
            const float dIn = toFx;
            if (pingPong) {  // bounce left/right
                dampL_ += dlyCoef * (dr * fb - dampL_);
                dampR_ += dlyCoef * (dl * fb - dampR_);
                delayL_[delayPos_] = dIn + dampL_ + 1e-20f;
                delayR_[delayPos_] = dampR_ + 1e-20f;
            } else {         // straight echoes, centred
                dampL_ += dlyCoef * (dl * fb - dampL_);
                dampR_ += dlyCoef * (dr * fb - dampR_);
                delayL_[delayPos_] = dIn + dampL_ + 1e-20f;
                delayR_[delayPos_] = dIn + dampR_ + 1e-20f;
            }
            delayPos_ = (delayPos_ + 1) % delayL_.size();
            const float echoL = dl * smDly_, echoR = dr * smDly_;

            float rl, rr;
            reverb_.process(toFx + 0.5f * (echoL + echoR), p_[P_REV_SIZE], p_[P_REV_TONE], p_[P_REV_WIDTH], rl, rr);
            const float wl = echoL + rl * 2.5f * smRev_;
            const float wr = echoR + rr * 2.5f * smRev_;
            const float dryGain = 1.f - 0.3f * smRev_;

            // --- drums (dry) ---
            float d = 0.f;
            for (auto& dv : drums_) d += dv.tick(noise_, sr_);

            const float L = dryL * dryGain + wl + 0.8f * d;
            const float R = dryR * dryGain + wr + 0.8f * d;
            const float g = 1.6f * smVol_;
            out[2 * f]     = std::tanh(L * g);
            out[2 * f + 1] = std::tanh(R * g);
        }
        sampleTime_ += (uint64_t)frames;
        playingFlag_.store(playing_ || pendingStart_ || waitFirstTick_, std::memory_order_relaxed);
        extSyncFlag_.store(playing_ && following(), std::memory_order_relaxed);
        syncState_.store((following() ? 1 : 0) | (clockPresent() ? 2 : 0) | ((pendingStart_ || waitFirstTick_) ? 4 : 0),
                         std::memory_order_relaxed);
        if (!playing_) beat_.store(-1, std::memory_order_relaxed);
    }

private:
    void loadDefaultPattern() {
        const int notes[kSteps] = {36, -1, 36, 39, -1, 36, 31, -1, 36, -1, 48, 46, -1, 43, 36, -1};
        const uint8_t pat[kDrumLanes][kSteps] = {
            {1,0,0,0,1,0,0,0,1,0,0,0,1,0,1,0},
            {0,0,0,0,1,0,0,0,0,0,0,0,1,0,0,0},
            {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},
            {0,0,0,0,0,0,0,1,0,0,0,0,0,0,0,1},
        };
        clearPattern();
        for (int i = 0; i < kSteps; ++i) {
            notes_[i][0] = notes[i];
            for (int l = 0; l < kDrumLanes; ++l) drumOn_[l][i] = pat[l][i];
        }
        clearMotion(-1);
    }

    void clearPattern() {
        for (int i = 0; i < kMaxSteps; ++i) {
            for (int n = 0; n < kNotesPerStep; ++n) notes_[i][n] = -1;
            lock_[i] = -1.f;
            chance_[i] = 100; ratchet_[i] = 1; tie_[i] = 1;
            for (int l = 0; l < kDrumLanes; ++l) drumOn_[l][i] = 0;
        }
    }

    // Each pool (sequencer / live) has its own share of voices, so playing KEYS
    // never cuts the pattern's notes and the pattern never cuts what you play.
    // When a pool is full it takes back a note that is already fading out first,
    // and only then the oldest note.
    FmVoice& allocVoice(bool live = false) {
        const int cap = live ? kLiveVoices : kSeqVoices;
        int used = 0;
        FmVoice* freeV = nullptr;
        FmVoice* oldRel = nullptr;
        FmVoice* oldest = nullptr;
        for (auto& v : voices_) {
            if (!v.active()) { if (!freeV) freeV = &v; continue; }
            if (v.livePool != live) continue;
            ++used;
            if (v.releasing() && (!oldRel || v.order < oldRel->order)) oldRel = &v;
            if (!oldest || v.order < oldest->order) oldest = &v;
        }
        if (used < cap && freeV) return *freeV;
        if (oldRel) return *oldRel;
        if (oldest) return *oldest;
        return freeV ? *freeV : voices_[0];
    }

    // ---- sequencer ----------------------------------------------------------
    // phase16_ counts 16th notes since the pattern started; it advances by the
    // tempo (or the external clock, which also nudges it into phase). Steps fire
    // when it reaches their position, so timing is exact to the sample.
    double rateMul() const { return kRateSixteenths[std::max(0, std::min(kRates - 1, (int)p_[P_RATE]))]; }
    int patLen() const { return std::max(1, std::min(kMaxSteps, (int)p_[P_PAT_LEN])); }
    double swingShift(int64_t k) const {
        return (k & 1) ? (p_[P_SWING] - 0.5) * 2.0 * rateMul() : 0.0;  // every second step lands late
    }
    void resetPosition() {
        phase16_ = 0.0; kAbs_ = 0; nextStepBase_ = 0.0; nextStepAt_ = 0.0;
        curStep_ = -1; ratchetLeft_ = 0; lastTick_ = -1; outTickPhase_ = 0.0;
    }
    double offsetSixteenths(double base16) const {
        double samples = p_[P_SYNC_OFFSET] * 0.001 * sr_;
        if (following()) samples -= latencyFrames_;  // play early by our own output delay
        return samples / base16;
    }

    void sequencerTick(int f, double base16, int gateSamples) {
        const uint64_t now = sampleTime_ + (uint64_t)f;
        if (filterGate_ > 0 && --filterGate_ == 0 && liveHeld_ == 0) filterEnv_.gateOff();
        if (!playing_) return;
        phase16_ += 1.0 / base16;

        // Clock out while Bergz is the master: 6 ticks per 16th (24 per quarter).
        if (p_[P_CLOCK_OUT] > 0.5f && !following()) {
            while (outTickPhase_ * (1.0 / 6.0) <= phase16_) {
                clockOut_.push(Cmd{kClockTick, 0, 0, 0.f, (int64_t)now});
                outTickPhase_ += 1.0;
            }
        }
        beat_.store((int)(std::max(0.0, phase16_) / 4.0) % 4, std::memory_order_relaxed);

        const double eff = phase16_ - offsetSixteenths(base16);
        while (eff >= nextStepAt_) fireStep(eff, base16, gateSamples);

        if (ratchetLeft_ > 0 && eff >= ratchetAt_) {
            triggerStep(ratchetStep_, ratchetGate_);
            --ratchetLeft_;
            ratchetAt_ += ratchetInterval_;
        }
        if (curStep_ >= 0) {
            const int sub = std::max(0, std::min(kTicksPerStep - 1, (int)((eff - curStepAt_) / rateMul() * kTicksPerStep)));
            const int tick = curStep_ * kTicksPerStep + sub;
            if (tick != lastTick_) { lastTick_ = tick; motionTick(tick); }
        }
    }

    void fireStep(double eff, double base16, int gateSamples) {
        const double mul = rateMul();
        const int len = patLen();
        const int idx = (int)(kAbs_ % len);
        const bool late = eff - nextStepAt_ > 1.0;  // fell far behind (clock jump): skip, don't burst
        curStep_ = idx;
        curStepAt_ = nextStepAt_;
        playhead_.store(idx, std::memory_order_relaxed);
        if (!late && (chance_[idx] >= 100 || (int)(rand01() * 100.f) < chance_[idx])) {
            const double stepSamples = mul * base16;
            const int r = std::max(1, std::min(4, (int)ratchet_[idx]));
            int gate;
            if (tie_[idx] > 1) gate = (int)(stepSamples * tie_[idx] * 0.98);       // held across the next steps
            else if (r > 1) gate = std::max(1, (int)(stepSamples / r * 0.6));      // short repeats
            else gate = std::max(1, (int)(gateSamples * mul));
            triggerStep(idx, gate);
            ratchetLeft_ = r - 1;
            if (r > 1) {
                ratchetStep_ = idx; ratchetGate_ = gate;
                ratchetInterval_ = mul / r; ratchetAt_ = curStepAt_ + ratchetInterval_;
            }
        } else {
            ratchetLeft_ = 0;
        }
        ++kAbs_;
        nextStepBase_ += mul;
        nextStepAt_ = nextStepBase_ + swingShift(kAbs_);
    }

    float rand01() {
        rng_ ^= rng_ << 13; rng_ ^= rng_ >> 17; rng_ ^= rng_ << 5;
        return (rng_ & 0xFFFFFF) / 16777216.f;
    }

    void startPlaying(uint64_t atSample) {
        playing_ = true;
        resetPosition();
        // if the start point was a moment ago (a clock tick in the past), catch up to now
        const double base16 = (following() && spt_ > 0.0) ? spt_ * 6.0 : sr_ * 60.0 / (double)p_[P_TEMPO] / 4.0;
        if (sampleTime_ > atSample) phase16_ = (double)(sampleTime_ - atSample) / base16;
        if (p_[P_CLOCK_OUT] > 0.5f && !following()) clockOut_.push(Cmd{kClockStart, 0, 0, 0.f, (int64_t)atSample});
    }

    // ---- external clock -----------------------------------------------------
    bool clockPresent() const { return lastClockAt_ > 0 && sampleTime_ - lastClockAt_ < (uint64_t)(sr_ * 0.5f); }
    bool following() const {
        const int mode = (int)p_[P_SYNC_MODE];
        return mode == 2 || (mode == 0 && clockPresent());
    }

    void triggerStep(int i, int gateSamples) {
        activeLock_ = lock_[i];
        bool any = false;
        for (int n = 0; n < kNotesPerStep; ++n) {
            if (notes_[i][n] < 0) continue;
            const int note = std::max(0, std::min(127, notes_[i][n] + (int)p_[P_TRANSPOSE]));
            allocVoice(false).noteOn(note, gateSamples, ++voiceOrder_);
            any = true;
        }
        if (any) { filterEnv_.gateOn(); filterGate_ = gateSamples; recountLive(); ++stepsTriggered_; }
        for (int l = 0; l < kDrumLanes; ++l)
            if (drumOn_[l][i]) drums_[l].trigger();
    }

    void applyEnvelopes() {
        const float a = 0.001f + 3.f * p_[P_ATTACK] * p_[P_ATTACK];
        const float d = 0.02f + 3.f * p_[P_DECAY] * p_[P_DECAY];
        const float s = p_[P_SUSTAIN];
        const float r = 0.02f + 4.f * p_[P_RELEASE] * p_[P_RELEASE];
        for (auto& v : voices_) {
            for (int i = 0; i < kOps; ++i) {
                const bool carrier = kAlgos[algorithm_].carriers & (1u << i);
                // modulators decay a little faster: brighter attack, darker tail
                v.env[i].set(a, carrier ? d : d * 0.6f, carrier ? s : s * 0.7f, r, sr_);
            }
        }
        filterEnv_.set(0.002f + a * 0.5f, d, 0.f, r, sr_);
    }

    void applyParam(int id, float v) {
        if (id < 0 || id >= P_COUNT) return;
        if (id == P_ALGORITHM) {
            algorithm_ = std::max(0, std::min(kAlgorithms - 1, (int)v));
            p_[id] = (float)algorithm_;
            applyEnvelopes();
        } else if (id == P_FX_TYPE) {
            p_[id] = (float)std::max(0, std::min(FX_COUNT - 1, (int)v));
        } else if (id == P_TEMPO) {
            p_[id] = std::fmax(40.f, std::fmin(240.f, v));
        } else if (id == P_TRANSPOSE) {
            p_[id] = std::round(std::fmax(-12.f, std::fmin(12.f, v)));
        } else if (id == P_PAT_LEN) {
            p_[id] = std::round(std::fmax(1.f, std::fmin((float)kMaxSteps, v)));
        } else if (id == P_RATE) {
            const float old = p_[id];
            p_[id] = std::round(std::fmax(0.f, std::fmin((float)(kRates - 1), v)));
            if (old != p_[id]) { nextStepBase_ = nextStepAt_ - swingShift(kAbs_); }  // keep going from here
        } else if (id == P_SWING) {
            p_[id] = std::fmax(0.5f, std::fmin(0.75f, v));
        } else if (id == P_SYNC_MODE || id == P_START_Q) {
            p_[id] = std::round(std::fmax(0.f, std::fmin(2.f, v)));
        } else if (id == P_SYNC_OFFSET) {
            p_[id] = std::round(std::fmax(-60.f, std::fmin(60.f, v)));
        } else if (id == P_CLOCK_OUT) {
            p_[id] = v > 0.5f ? 1.f : 0.f;
        } else {
            p_[id] = std::fmax(0.f, std::fmin(1.f, v));
            if (id >= P_ATTACK && id <= P_RELEASE) applyEnvelopes();
        }
        mirror_[id].store(p_[id], std::memory_order_relaxed);  // UI reads this
    }

    // Record what touched knobs are doing; replay everything else.
    void motionTick(int tick) {
        if (tick < 0 || tick >= kMotionTicks) return;
        uint32_t mask = motionMask_.load(std::memory_order_relaxed);
        for (int id = 0; id < kMotionParams; ++id) {
            if (touched_[id]) {
                if (recording_) { motion_[id][tick] = p_[id]; mask |= 1u << id; }
            } else if (motion_[id][tick] >= 0.f) {
                applyParam(id, motion_[id][tick]);
            }
        }
        motionMask_.store(mask, std::memory_order_relaxed);
    }

    void clearMotion(int id) {
        uint32_t mask = motionMask_.load(std::memory_order_relaxed);
        for (int p = 0; p < kMotionParams; ++p) {
            if (id >= 0 && p != id) continue;
            for (int t = 0; t < kMotionTicks; ++t) motion_[p][t] = -1.f;
            mask &= ~(1u << p);
        }
        motionMask_.store(mask, std::memory_order_relaxed);
    }

    void applyCmd(const Cmd& c) {
        switch (c.type) {
            case CMD_PARAM: applyParam(c.a, c.v); break;
            case CMD_STEP_NOTE: {
                const int slot = (int)c.v;
                if (c.a >= 0 && c.a < kMaxSteps && slot >= 0 && slot < kNotesPerStep) notes_[c.a][slot] = c.b;
                break;
            }
            case CMD_STEP_PROPS:
                if (c.a >= 0 && c.a < kMaxSteps) {
                    chance_[c.a] = (uint8_t)std::max(0, std::min(100, c.b & 0xFF));
                    ratchet_[c.a] = (uint8_t)std::max(1, std::min(4, (c.b >> 8) & 0xFF));
                    tie_[c.a] = (uint8_t)std::max(1, std::min(16, (c.b >> 16) & 0xFF));
                }
                break;
            case CMD_DRUM:
                if (c.a >= 0 && c.a < kDrumLanes && c.b >= 0 && c.b < kMaxSteps) drumOn_[c.a][c.b] = c.v > 0.5f;
                break;
            case CMD_LOCK: if (c.a >= 0 && c.a < kMaxSteps) lock_[c.a] = c.v; break;
            case CMD_PLAY:
                if (playing_ || pendingStart_) break;
                if (following()) {
                    pendingStart_ = true;           // start on the next bar / beat / tick of the clock
                } else {
                    startPlaying(sampleTime_);
                }
                break;
            case CMD_STOP:
                if (playing_ && p_[P_CLOCK_OUT] > 0.5f && !following())
                    clockOut_.push(Cmd{kClockStop, 0, 0, 0.f, (int64_t)sampleTime_});
                playing_ = false;
                pendingStart_ = false;
                waitFirstTick_ = false;
                for (auto& v : voices_) if (!v.live) { v.noteOff(); v.gateLeft = 0; }
                filterEnv_.gateOff();
                playhead_.store(-1, std::memory_order_relaxed);
                break;
            case CMD_RECORD: recording_ = c.v > 0.5f; break;
            case CMD_TOUCH: if (c.a >= 0 && c.a < kMotionParams) touched_[c.a] = c.b != 0; break;
            case CMD_CLEAR_MOTION: clearMotion(c.a); break;
            case CMD_CLEAR_PATTERN: clearPattern(); break;
            case CMD_RESET_DEFAULTS:
                for (int i = 0; i < P_COUNT; ++i) applyParam(i, kDefaultParams[i]);
                pitchBend_ = 1.f;
                break;

            case CMD_NOTE_ON: {
                if (c.a < 0 || c.a > 127) break;
                const float vel = 0.3f + 0.7f * std::fmax(0.f, std::fmin(1.f, c.v));
                allocVoice(true).noteOn(c.a, 0, ++voiceOrder_, vel, true);
                recountLive();
                filterEnv_.gateOn();
                filterGate_ = 0;
                break;
            }
            case CMD_NOTE_OFF:
                for (auto& v : voices_)
                    if (v.live && v.note == c.a) { v.noteOff(); v.live = false; }
                recountLive();
                if (liveHeld_ == 0) filterEnv_.gateOff();
                break;
            case CMD_ALL_NOTES_OFF:
                for (auto& v : voices_) if (v.live) { v.noteOff(); v.live = false; }
                liveHeld_ = 0;
                filterEnv_.gateOff();
                break;
            case CMD_PITCH_BEND:
                pitchBend_ = std::pow(2.f, 2.f * std::fmax(-1.f, std::fmin(1.f, c.v)) / 12.f);
                break;

            case CMD_MIDI_START:
                if ((int)p_[P_SYNC_MODE] == 1) break;   // INT: ignore the host's transport
                // The next clock tick is beat 1 of bar 1.
                playing_ = false; pendingStart_ = false; waitFirstTick_ = true;
                lastClockAt_ = std::max<uint64_t>(lastClockAt_, sampleTime_);
                break;
            case CMD_MIDI_CONTINUE:
                if ((int)p_[P_SYNC_MODE] == 1) break;
                if (!playing_) { playing_ = true; playStartTick_ = tickCount_ - (int64_t)std::llround(phase16_ * 6.0); }
                break;
            case CMD_MIDI_STOP:
                if ((int)p_[P_SYNC_MODE] == 1) break;
                applyCmd({CMD_STOP, 0, 0, 0.f});
                break;
            case CMD_MIDI_CLOCK: clockTick(c.t); break;
        }
        playingFlag_.store(playing_ || pendingStart_ || waitFirstTick_, std::memory_order_relaxed);
        extSyncFlag_.store(playing_ && following(), std::memory_order_relaxed);
        syncState_.store((following() ? 1 : 0) | (clockPresent() ? 2 : 0) | ((pendingStart_ || waitFirstTick_) ? 4 : 0),
                         std::memory_order_relaxed);
    }

    // 24 ticks per quarter note: every 6 ticks is one 16th step. The tempo is
    // measured over each quarter note so delay times and gates follow the host.
    // A held MIDI note may have been stolen by another note, so count, don't track.
    void recountLive() {
        int n = 0;
        for (auto& v : voices_) if (v.live) ++n;
        liveHeld_ = n;
    }

    // MIDI clock: 24 ticks per quarter note, 6 per 16th. Each tick carries the
    // time it arrived, so it can be placed on our sample timeline exactly
    // (audio blocks no longer add jitter). The tempo is a smoothed average of
    // the tick spacing, and while playing the sequencer is nudged a little at
    // every 16th towards where the clock says it should be.
    void clockTick(int64_t tNs) {
        double tickSample = (double)sampleTime_;
        if (tNs > 0 && blockNs_ > 0) {
            tickSample += (double)(tNs - blockNs_) * sr_ / 1e9;
            tickSample = std::fmax((double)sampleTime_ - sr_ * 0.25, std::fmin((double)sampleTime_, tickSample));
        }
        lastClockAt_ = sampleTime_;
        if (prevTickSample_ > 0.0) {
            const double gap = tickSample - prevTickSample_;
            if (gap > sr_ * 0.003 && gap < sr_ * 0.1) spt_ = spt_ > 0.0 ? spt_ + 0.06 * (gap - spt_) : gap;
            else if (gap >= sr_ * 0.1) spt_ = 0.0;  // the clock paused: measure afresh
        }
        prevTickSample_ = tickSample;
        ++tickCount_;
        if (spt_ > 0.0 && tickCount_ % 24 == 0) {
            p_[P_TEMPO] = (float)(std::round(60.0 * sr_ / (spt_ * 24.0) * 10.0) / 10.0);
            mirror_[P_TEMPO].store(p_[P_TEMPO], std::memory_order_relaxed);
        }
        if (!following()) return;

        const uint64_t at = (uint64_t)std::max(0.0, tickSample);
        if (waitFirstTick_) {                     // first tick after MIDI Start = bar 1
            waitFirstTick_ = false;
            barTick_ = tickCount_;
            playStartTick_ = tickCount_;
            startPlaying(at);
            return;
        }
        if (pendingStart_) {                      // PLAY pressed while following the clock
            const int64_t q = p_[P_START_Q] < 0.5f ? 96 : (p_[P_START_Q] < 1.5f ? 24 : 1);
            if ((tickCount_ - barTick_) % q == 0) {
                pendingStart_ = false;
                playStartTick_ = tickCount_;
                startPlaying(at);
            }
            return;
        }
        if (playing_ && spt_ > 0.0) {
            const int64_t rel = tickCount_ - playStartTick_;
            if (rel > 0 && rel % 6 == 0) {
                const double base16 = spt_ * 6.0;
                const double phaseAtTick = phase16_ - ((double)sampleTime_ - tickSample) / base16;
                const double err = (double)rel / 6.0 - phaseAtTick;
                phase16_ += std::fabs(err) > 2.0 ? err : 0.2 * err;
            }
        }
    }

    void drainCommands() { Cmd c; while (queue_.pop(c)) applyCmd(c); }

    float sr_ = 48000.f;
    float p_[P_COUNT] = {};  // filled from kDefaultParams in the constructor
    int algorithm_ = 0;

    SpscQueue<1024> queue_;
    std::atomic<int> playhead_{-1};

    std::array<FmVoice, kVoices> voices_{};
    uint32_t voiceOrder_ = 0;
    Env filterEnv_;
    int filterGate_ = 0;
    Svf filter_;
    Noise noise_;
    std::array<DrumVoice, kDrumLanes> drums_{};
    float lfoPhase_ = 0.f;
    float smCut_ = 0.45f, smDepth_ = 0.8f, smVol_ = 0.7f;
    float smDly_ = 0.f, smRev_ = 0.f, smPh_ = 0.f;

    std::vector<float> delayL_, delayR_;
    size_t delayPos_ = 0;
    float dampL_ = 0.f, dampR_ = 0.f;
    Reverb reverb_;
    Phaser phaser_;

    bool playing_ = false;
    float activeLock_ = -1.f;
    // sequencer position
    double phase16_ = 0.0, nextStepBase_ = 0.0, nextStepAt_ = 0.0, curStepAt_ = 0.0;
    int64_t kAbs_ = 0;
    int curStep_ = -1;
    int ratchetLeft_ = 0, ratchetStep_ = 0, ratchetGate_ = 0;
    double ratchetInterval_ = 0.0, ratchetAt_ = 0.0;
    uint32_t rng_ = 0x9E3779B9u;
    uint32_t stepsTriggered_ = 0;

    int notes_[kMaxSteps][kNotesPerStep]{};
    float lock_[kMaxSteps]{};
    uint8_t chance_[kMaxSteps]{}, ratchet_[kMaxSteps]{}, tie_[kMaxSteps]{};
    uint8_t drumOn_[kDrumLanes][kMaxSteps]{};

    bool recording_ = false;
    bool touched_[P_COUNT]{};
    int lastTick_ = -1;
    float motion_[kMotionParams][kMotionTicks]{};
    std::atomic<uint32_t> motionMask_{0};
    std::array<std::atomic<float>, P_COUNT> mirror_{};

    // live MIDI + external clock
    int liveHeld_ = 0;
    float pitchBend_ = 1.f;
    bool waitFirstTick_ = false, pendingStart_ = false;
    double spt_ = 0.0, prevTickSample_ = 0.0, outTickPhase_ = 0.0;
    int64_t tickCount_ = 0, barTick_ = 0, playStartTick_ = 0;
    uint64_t sampleTime_ = 0, lastClockAt_ = 0;
    int64_t blockNs_ = 0;
    int latencyFrames_ = 0;
    SpscQueue<512> clockOut_;
    std::atomic<bool> playingFlag_{false}, extSyncFlag_{false};
    std::atomic<int> syncState_{0}, beat_{-1};
};

}  // namespace bergz
