// Sequencer test (v0.7): pattern length, step rate, swing, chance, ratchet,
// tie, the 16 + 8 voice pools, following a jittery external clock, starting
// on the next bar, and MIDI clock out.
// Build: g++ -O2 -std=c++17 -I../app/src/main/cpp seq_test.cpp -o seq_test
#include "SynthCore.h"

#include <cmath>
#include <cstdio>
#include <random>
#include <vector>

using namespace bergz;

static int fails = 0;
static void check(bool ok, const char* what) {
    std::printf("%-58s %s\n", what, ok ? "ok" : "FAILED");
    if (!ok) ++fails;
}

constexpr float SR = 48000.f;

// Render frame by frame and record the sample at which each step starts.
struct Run {
    SynthCore s{SR};
    std::vector<std::pair<long, int>> onsets;  // (sample, step)
    long t = 0;
    int last = -1;
    void frames(long n) {
        float buf[2];
        for (long i = 0; i < n; ++i, ++t) {
            s.render(buf, 1);
            const int ph = s.playhead();
            if (ph != last && ph >= 0) onsets.push_back({t, ph});
            last = ph;
        }
    }
};

static void note(SynthCore& s, int step, std::initializer_list<int> notes) {
    int slot = 0;
    for (int n : notes) s.command({CMD_STEP_NOTE, step, n, (float)slot++});
}
static void props(SynthCore& s, int step, int chance, int ratchet, int tie) {
    s.command({CMD_STEP_PROPS, step, chance | (ratchet << 8) | (tie << 16), 0.f});
}

int main() {
    const double sixteenth = SR * 60.0 / 120.0 / 4.0;  // 6000 samples at 120 BPM

    {   // length 32
        Run r; r.s.command({CMD_CLEAR_PATTERN, 0, 0, 0.f});
        r.s.setParam(P_PAT_LEN, 32); r.s.command({CMD_PLAY, 0, 0, 0.f});
        r.frames((long)(sixteenth * 40));
        int maxStep = 0; for (auto& o : r.onsets) maxStep = std::max(maxStep, o.second);
        check(maxStep == 31 && r.onsets[32].second == 0, "32-step pattern runs to step 32 and loops");
    }
    {   // rate 1/8 and 1 bar
        Run r; r.s.setParam(P_RATE, 2); r.s.command({CMD_PLAY, 0, 0, 0.f});
        r.frames((long)(sixteenth * 16) - 10);
        check(r.onsets.size() == 8, "rate 1/8: 8 steps per bar");
        Run q; q.s.setParam(P_RATE, 5); q.s.command({CMD_PLAY, 0, 0, 0.f});
        q.frames((long)(sixteenth * 32) - 10);
        check(q.onsets.size() == 2, "rate 1 bar: 2 steps in 2 bars");
    }
    {   // swing
        Run r; r.s.setParam(P_SWING, 0.75f); r.s.command({CMD_PLAY, 0, 0, 0.f});
        r.frames((long)(sixteenth * 4));
        const double d1 = (r.onsets[1].first - r.onsets[0].first) / sixteenth;
        const double d2 = (r.onsets[2].first - r.onsets[0].first) / sixteenth;
        check(std::fabs(d1 - 1.5) < 0.01 && std::fabs(d2 - 2.0) < 0.01, "swing 75%: off-beat 16ths land at 1.5, beats stay put");
    }
    {   // chance
        auto hits = [&](int chance) {
            Run r; r.s.command({CMD_CLEAR_PATTERN, 0, 0, 0.f});
            note(r.s, 0, {60}); props(r.s, 0, chance, 1, 1);
            r.s.setParam(P_PAT_LEN, 1); r.s.setParam(P_GATE, 0.f); r.s.setParam(P_RELEASE, 0.f);
            r.s.command({CMD_PLAY, 0, 0, 0.f});
            r.frames((long)(sixteenth * 400) - 100);
            const int count = (int)r.s.stepsTriggered();
            return count;
        };
        const int h0 = hits(0), h50 = hits(50), h100 = hits(100);
        std::printf("  chance hits over 400 steps: 0%%=%d 50%%=%d 100%%=%d\n", h0, h50, h100);
        check(h0 == 0 && h100 == 400 && h50 > 150 && h50 < 250, "chance 0 / 50 / 100% plays never / about half / always");
    }
    {   // ratchet: 4 hits inside one step
        Run r; r.s.command({CMD_CLEAR_PATTERN, 0, 0, 0.f});
        note(r.s, 0, {60}); props(r.s, 0, 100, 4, 1);
        r.s.setParam(P_RELEASE, 0.f); r.s.setParam(P_DECAY, 0.f); r.s.setParam(P_SUSTAIN, 0.f);
        r.s.command({CMD_PLAY, 0, 0, 0.f});
        int rises = 0, prev = 0;
        for (int i = 0; i < 6000; i += 25) { r.frames(25); const int a = r.s.activeVoices(false); if (a > prev) rises += a - prev; prev = a; }
        check(rises == 4, "ratchet x4: four hits in one step");
    }
    {   // tie: a tied note is still held 3 steps later
        Run r; r.s.command({CMD_CLEAR_PATTERN, 0, 0, 0.f});
        note(r.s, 0, {60}); props(r.s, 0, 100, 1, 4);
        r.s.setParam(P_GATE, 0.f); r.s.setParam(P_RELEASE, 0.f); r.s.setParam(P_SUSTAIN, 1.f);
        r.s.command({CMD_PLAY, 0, 0, 0.f});
        r.frames((long)(sixteenth * 3.5));
        check(r.s.activeVoices(false) == 1, "tie x4: the note is still held after 3.5 steps");
    }
    {   // 16 sequencer voices; live playing never steals them
        Run r; r.s.command({CMD_CLEAR_PATTERN, 0, 0, 0.f});
        for (int st = 0; st < 4; ++st) { note(r.s, st, {48 + st, 52 + st, 55 + st, 59 + st}); props(r.s, st, 100, 1, 16); }
        r.s.setParam(P_SUSTAIN, 1.f);
        r.s.command({CMD_PLAY, 0, 0, 0.f});
        r.frames((long)(sixteenth * 3.5));
        const int seq = r.s.activeVoices(false);
        for (int k = 0; k < 8; ++k) r.s.command({CMD_NOTE_ON, 72 + k, 0, 1.f});
        r.frames(100);
        check(seq == 16 && r.s.activeVoices(false) == 16 && r.s.activeVoices(true) == 8,
              "4 overlapping 4-note chords all sound; 8 live notes on top steal none");
    }
    {   // follow a jittery external clock at 124 BPM
        Run r;
        std::mt19937 rng(7);
        std::uniform_real_distribution<double> jit(0.0, 0.004);   // up to 4 ms late, random
        const double spt = 60.0 / 124.0 / 24.0;                    // seconds per tick
        r.s.command({CMD_MIDI_START, 0, 0, 0.f});
        int k = 0;
        const int block = 192;
        for (long b = 0; b < (long)(SR * 8 / block); ++b) {
            const double blockT = b * block / (double)SR;
            r.s.setBlockTime((int64_t)(blockT * 1e9), 0);
            while (k * spt + jit(rng) < blockT) {     // ticks that have arrived by now
                const double arrive = k * spt + 0.002 + (rng() % 1000) * 0.000002;
                r.s.post({CMD_MIDI_CLOCK, 0, 0, 0.f, (int64_t)(arrive * 1e9)});
                ++k;
            }
            r.frames(block);
        }
        // steps after the first 2 s (lock-in) should be evenly spaced
        std::vector<double> gaps;
        for (size_t i = 1; i < r.onsets.size(); ++i)
            if (r.onsets[i].first > SR * 2) gaps.push_back((r.onsets[i].first - r.onsets[i - 1].first) / (double)SR);
        double mean = 0, var = 0;
        for (double g : gaps) mean += g;
        mean /= gaps.size();
        for (double g : gaps) var += (g - mean) * (g - mean);
        var /= gaps.size();
        const double bpm = 60.0 / (mean * 4.0);
        std::printf("  ext clock: %.2f BPM measured, step spacing wobble %.3f ms, tempo shown %.1f\n",
                    bpm, std::sqrt(var) * 1000.0, r.s.param(P_TEMPO));
        check(std::fabs(bpm - 124.0) < 0.3 && std::sqrt(var) < 0.0005, "follows jittery clock at 124 BPM with < 0.5 ms wobble");
        check(std::fabs(r.s.param(P_TEMPO) - 124.0f) < 0.5f, "shows the clock's tempo");
    }
    {   // PLAY while the clock runs: waits for the next bar
        Run r;
        const double spt = SR * 60.0 / 120.0 / 24.0;
        long nextTick = 0; int ticks = 0;
        r.s.command({CMD_MIDI_START, 0, 0, 0.f});
        r.s.command({CMD_STOP, 0, 0, 0.f});   // host is running; Bergz is stopped
        long startAt = -1;
        for (long t = 0; t < (long)(spt * 200); ) {
            if (t >= nextTick) { r.s.post({CMD_MIDI_CLOCK, 0, 0, 0.f}); ++ticks; nextTick = (long)(ticks * spt); }
            if (ticks == 30 && startAt < 0) { r.s.post({CMD_PLAY, 0, 0, 0.f}); startAt = 0; }
            r.frames(48); t += 48;
        }
        const double startTick = r.onsets.empty() ? -1 : r.onsets[0].first / spt;
        std::printf("  first step at clock tick %.1f (pressed at tick 30)\n", startTick);
        check(std::fabs(startTick - 96.0) < 1.5, "PLAY on external clock starts on the next bar");
    }
    {   // clock out: 24 per quarter + start / stop
        Run r; r.s.setParam(P_SYNC_MODE, 1); r.s.setParam(P_CLOCK_OUT, 1);
        r.s.command({CMD_PLAY, 0, 0, 0.f});
        int ticks = 0, starts = 0, stops = 0;
        Cmd c;
        for (int i = 0; i < 100; ++i) {
            r.frames(960);   // 2 s in total
            while (r.s.popClockOut(c)) { ticks += c.type == kClockTick; starts += c.type == kClockStart; }
        }
        r.s.command({CMD_STOP, 0, 0, 0.f});
        while (r.s.popClockOut(c)) stops += c.type == kClockStop;
        check(starts == 1 && std::abs(ticks - 96) <= 1 && stops == 1, "clock out: start, 96 ticks per 2 s bar at 120, stop");
    }

    std::printf("%s\n", fails ? "SEQ TEST FAILED" : "SEQ TEST OK");
    return fails ? 1 : 0;
}
