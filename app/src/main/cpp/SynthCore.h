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
#include <vector>

namespace bergz {

constexpr float kTwoPi = 6.283185307179586f;
constexpr float kPi = 3.14159265358979f;
constexpr int kSteps = 16;
constexpr int kOps = 6;
constexpr int kDrumLanes = 4;
constexpr int kVoices = 8;        // polyphony, so pads and chords can overlap
constexpr int kNotesPerStep = 4;  // up to a 4-note chord on any step

// ---- Parameter ids (keep in sync with NativeEngine.kt) ---------------------
enum Param : int {
    P_FM_DEPTH = 0, P_HARMONIC, P_FEEDBACK,
    P_CUTOFF, P_RESO, P_ENV_AMT,
    P_FX_MIX, P_FX_PARAM, P_VOLUME,
    P_ATTACK, P_DECAY, P_SUSTAIN, P_RELEASE,
    P_LFO_RATE, P_LFO_DEPTH,
    P_GATE,       // note length, 0.1 .. 8 steps
    P_TEMPO,      // actual BPM, not 0..1
    P_ALGORITHM,  // 0 .. kAlgorithms-1
    P_FX_TYPE,    // 0 reverb, 1 delay, 2 phaser
    P_COUNT
};
enum FxType : int { FX_REVERB = 0, FX_DELAY = 1, FX_PHASER = 2, FX_COUNT };

// Motion recording: knob moves are stored per parameter at 8 points per step.
constexpr int kTicksPerStep = 8;
constexpr int kMotionTicks = kSteps * kTicksPerStep;  // 128 per pattern
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
    CMD_CLEAR_PATTERN  // empties notes, drums and locks
};

struct Cmd { int type; int a; int b; float v; };

// ---- Lock-free single-producer / single-consumer command queue -------------
template <int N>
class SpscQueue {
public:
    bool push(const Cmd& c) {
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
    int gateLeft = 0;      // samples until note-off
    uint32_t order = 0;    // for voice stealing (oldest goes first)

    void noteOn(int midi, int gateSamples, uint32_t ord) {
        note = midi;
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
        return carriers ? carrierSum / (float)carriers : 0.f;
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
    void process(float in, float size, float& outL, float& outR) {
        const float fb = 0.70f + 0.28f * size;
        const float x = in * 0.03f;
        float l = 0.f, r = 0.f;
        for (int j = 0; j < 4; ++j) { l += cl[j].process(x, fb, 0.25f); r += cr[j].process(x, fb, 0.25f); }
        for (int j = 0; j < 2; ++j) { l = al[j].process(l); r = ar[j].process(r); }
        outL = l; outR = r;
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
    void process(float in, float rate, float sr, float& outL, float& outR) {
        lfo += (0.05f * std::pow(100.f, rate)) / sr;
        lfo -= std::floor(lfo);
        const float sl = 0.5f + 0.5f * std::sin(kTwoPi * lfo);
        const float sr2 = 0.5f + 0.5f * std::sin(kTwoPi * (lfo + 0.25f));
        const float al = coef(200.f * std::pow(15.f, sl), sr);
        const float ar = coef(200.f * std::pow(15.f, sr2), sr);
        float l = in + fbl * 0.6f, r = in + fbr * 0.6f;
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
        for (int i = 0; i < P_COUNT; ++i) mirror_[i].store(p_[i]);
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

    // ---- desktop renderer (same thread as render) ----
    void setParam(int id, float v) { applyParam(id, v); }
    void command(const Cmd& c) { applyCmd(c); }

    // ---- audio thread. Interleaved stereo float output. ----
    void render(float* out, int frames) {
        drainCommands();
        const Algorithm& alg = kAlgos[algorithm_];
        const int harmSet = std::min(kHarmSets - 1, (int)std::lround(p_[P_HARMONIC] * (kHarmSets - 1)));
        const double samplesPerStep = sr_ * 60.0 / (double)p_[P_TEMPO] / 4.0;
        const int gateSamples = std::max(1, (int)(samplesPerStep * 0.1 * std::pow(80.0, (double)p_[P_GATE])));
        const size_t delaySamples = std::min(delayL_.size() - 1, (size_t)(samplesPerStep * 3.0));
        const float lfoInc = (0.05f * std::pow(400.f, p_[P_LFO_RATE])) / sr_;
        const int fx = fxType_;

        for (int f = 0; f < frames; ++f) {
            // --- sequencer clock (sample-accurate) ---
            if (playing_) {
                if (stepCounter_ <= 0.0) {
                    triggerStep(step_, gateSamples);
                    playhead_.store(step_, std::memory_order_relaxed);
                    stepCounter_ += samplesPerStep;
                    step_ = (step_ + 1) % kSteps;
                }
                stepCounter_ -= 1.0;
                if (filterGate_ > 0 && --filterGate_ == 0) filterEnv_.gateOff();

                const int cur = playhead_.load(std::memory_order_relaxed);
                const double elapsed = samplesPerStep - stepCounter_;
                const int sub = std::min(kTicksPerStep - 1, (int)(elapsed * kTicksPerStep / samplesPerStep));
                const int tick = cur * kTicksPerStep + sub;
                if (tick != lastTick_) { lastTick_ = tick; motionTick(tick); }
            }

            // --- smoothed controls ---
            const float targetCut = activeLock_ >= 0.f ? activeLock_ : p_[P_CUTOFF];
            smCut_ += 0.002f * (targetCut - smCut_);
            smDepth_ += 0.002f * (p_[P_FM_DEPTH] - smDepth_);
            smVol_ += 0.002f * (p_[P_VOLUME] - smVol_);
            smMix_ += 0.002f * (p_[P_FX_MIX] - smMix_);

            // --- voices ---
            lfoPhase_ += lfoInc; lfoPhase_ -= std::floor(lfoPhase_);
            const float lfoRaw = std::sin(kTwoPi * lfoPhase_);
            const float vibrato = 1.f + 0.004f * lfoRaw * p_[P_LFO_DEPTH];
            float s = 0.f;
            for (auto& v : voices_)
                if (v.active()) s += v.tick(alg, harmSet, smDepth_, p_[P_FEEDBACK], vibrato, sr_);
            s *= 0.55f;

            const float lfoCut = lfoRaw * p_[P_LFO_DEPTH] * 2.f;
            const float fenv = filterEnv_.tick() * p_[P_ENV_AMT] * 6.f;
            const float cutoffHz = 30.f * std::pow(2.f, smCut_ * 9.3f + fenv + lfoCut);
            s = filter_.process(s, cutoffHz, p_[P_RESO], sr_);

            // --- effect (synth only) ---
            float wl = 0.f, wr = 0.f, dryGain = 1.f;
            switch (fx) {
                case FX_REVERB:
                    reverb_.process(s, p_[P_FX_PARAM], wl, wr);
                    wl *= 2.5f * smMix_; wr *= 2.5f * smMix_;
                    dryGain = 1.f - 0.3f * smMix_;
                    break;
                case FX_DELAY: {
                    const size_t rp = (delayPos_ + delayL_.size() - delaySamples) % delayL_.size();
                    const float dl = delayL_[rp], dr = delayR_[rp];
                    const float fb = p_[P_FX_PARAM] * 0.85f;
                    dampL_ += 0.35f * (dr * fb - dampL_);
                    dampR_ += 0.35f * (dl * fb - dampR_);
                    delayL_[delayPos_] = s + dampL_ + 1e-20f;
                    delayR_[delayPos_] = dampR_ + 1e-20f;
                    delayPos_ = (delayPos_ + 1) % delayL_.size();
                    wl = dl * smMix_; wr = dr * smMix_;
                    break;
                }
                case FX_PHASER: {
                    float pl, pr;
                    phaser_.process(s, p_[P_FX_PARAM], sr_, pl, pr);
                    dryGain = 1.f - smMix_;
                    wl = pl * smMix_; wr = pr * smMix_;
                    break;
                }
            }

            // --- drums (dry) ---
            float d = 0.f;
            for (auto& dv : drums_) d += dv.tick(noise_, sr_);

            const float L = s * dryGain + wl + 0.8f * d;
            const float R = s * dryGain + wr + 0.8f * d;
            const float g = 1.6f * smVol_;
            out[2 * f]     = std::tanh(L * g);
            out[2 * f + 1] = std::tanh(R * g);
        }
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
        for (int i = 0; i < kSteps; ++i) {
            for (int n = 0; n < kNotesPerStep; ++n) notes_[i][n] = -1;
            lock_[i] = -1.f;
            for (int l = 0; l < kDrumLanes; ++l) drumOn_[l][i] = 0;
        }
    }

    FmVoice& allocVoice() {
        FmVoice* best = &voices_[0];
        for (auto& v : voices_) {
            if (!v.active()) return v;
            if (v.order < best->order) best = &v;  // steal the oldest
        }
        return *best;
    }

    void triggerStep(int i, int gateSamples) {
        activeLock_ = lock_[i];
        bool any = false;
        for (int n = 0; n < kNotesPerStep; ++n) {
            if (notes_[i][n] < 0) continue;
            allocVoice().noteOn(notes_[i][n], gateSamples, ++voiceOrder_);
            any = true;
        }
        if (any) { filterEnv_.gateOn(); filterGate_ = gateSamples; }
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
            fxType_ = std::max(0, std::min(FX_COUNT - 1, (int)v));
            p_[id] = (float)fxType_;
        } else if (id == P_TEMPO) {
            p_[id] = std::fmax(40.f, std::fmin(240.f, v));
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
                if (c.a >= 0 && c.a < kSteps && slot >= 0 && slot < kNotesPerStep) notes_[c.a][slot] = c.b;
                break;
            }
            case CMD_DRUM:
                if (c.a >= 0 && c.a < kDrumLanes && c.b >= 0 && c.b < kSteps) drumOn_[c.a][c.b] = c.v > 0.5f;
                break;
            case CMD_LOCK: if (c.a >= 0 && c.a < kSteps) lock_[c.a] = c.v; break;
            case CMD_PLAY:
                playing_ = true; step_ = 0; stepCounter_ = 0.0; lastTick_ = -1;
                break;
            case CMD_STOP:
                playing_ = false;
                for (auto& v : voices_) { v.noteOff(); v.gateLeft = 0; }
                filterEnv_.gateOff();
                playhead_.store(-1, std::memory_order_relaxed);
                break;
            case CMD_RECORD: recording_ = c.v > 0.5f; break;
            case CMD_TOUCH: if (c.a >= 0 && c.a < kMotionParams) touched_[c.a] = c.b != 0; break;
            case CMD_CLEAR_MOTION: clearMotion(c.a); break;
            case CMD_CLEAR_PATTERN: clearPattern(); break;
        }
    }

    void drainCommands() { Cmd c; while (queue_.pop(c)) applyCmd(c); }

    float sr_ = 48000.f;
    // Defaults match the "Glass Bass" mockup.
    float p_[P_COUNT] = {
        0.80f, 0.40f, 0.25f,         // fm depth, harmonic, feedback
        0.45f, 0.35f, 0.55f,         // cutoff, reso, env amt
        0.25f, 0.45f, 0.70f,         // fx mix, fx param, volume
        0.02f, 0.30f, 0.20f, 0.20f,  // A D S R
        0.30f, 0.15f,                // lfo rate, depth
        0.46f,                       // gate (~0.75 step)
        120.f, 0.f, (float)FX_DELAY  // tempo, algorithm, fx type
    };
    int algorithm_ = 0;
    int fxType_ = FX_DELAY;

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
    float smCut_ = 0.45f, smDepth_ = 0.8f, smVol_ = 0.7f, smMix_ = 0.25f;

    std::vector<float> delayL_, delayR_;
    size_t delayPos_ = 0;
    float dampL_ = 0.f, dampR_ = 0.f;
    Reverb reverb_;
    Phaser phaser_;

    bool playing_ = false;
    int step_ = 0;
    double stepCounter_ = 0.0;
    float activeLock_ = -1.f;

    int notes_[kSteps][kNotesPerStep]{};
    float lock_[kSteps]{};
    uint8_t drumOn_[kDrumLanes][kSteps]{};

    bool recording_ = false;
    bool touched_[P_COUNT]{};
    int lastTick_ = -1;
    float motion_[kMotionParams][kMotionTicks]{};
    std::atomic<uint32_t> motionMask_{0};
    std::array<std::atomic<float>, P_COUNT> mirror_{};
};

}  // namespace bergz
