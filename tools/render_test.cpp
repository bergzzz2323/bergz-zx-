// Desktop test renderer: plays every starter patch through the exact synth
// core the app uses and writes one WAV, so the sound can be checked without a
// phone. Each patch gets 4 bars; on the first bar REC is on and a "hand" moves
// the filter and FM depth, then the recorded motion replays hands-free.
// Build:  g++ -O2 -std=c++17 -I../app/src/main/cpp render_test.cpp -o render_test
#include "Presets.h"

#include <cstdio>
#include <vector>

using namespace bergz;

static void writeWav(const char* path, const std::vector<float>& data, int sr) {
    FILE* f = std::fopen(path, "wb");
    if (!f) { std::perror(path); return; }
    const uint32_t dataBytes = (uint32_t)(data.size() * 2);
    auto u32 = [&](uint32_t v) { std::fwrite(&v, 4, 1, f); };
    auto u16 = [&](uint16_t v) { std::fwrite(&v, 2, 1, f); };
    std::fwrite("RIFF", 1, 4, f); u32(36 + dataBytes); std::fwrite("WAVE", 1, 4, f);
    std::fwrite("fmt ", 1, 4, f); u32(16); u16(1); u16(2); u32(sr); u32(sr * 4); u16(4); u16(16);
    std::fwrite("data", 1, 4, f); u32(dataBytes);
    for (float s : data) {
        const float c = s > 1.f ? 1.f : (s < -1.f ? -1.f : s);
        const int16_t v = (int16_t)(c * 32767.f);
        std::fwrite(&v, 2, 1, f);
    }
    std::fclose(f);
}

int main(int argc, char** argv) {
    const char* out = argc > 1 ? argv[1] : "bergz_presets.wav";
    const int sr = 48000, block = 192;  // block size similar to a phone's burst
    std::vector<float> all;
    int failures = 0;

    for (const Preset& p : presets()) {
        SynthCore synth((float)sr);
        loadPreset(p, [&](const Cmd& c) { synth.command(c); });
        const float bpm = p.params[P_TEMPO];
        const int barFrames = (int)(sr * 60.0 / bpm * 4.0);
        const int bars = 4;
        const int total = barFrames * bars + sr;  // +1 s for tails
        std::vector<float> buf((size_t)total * 2);
        std::vector<float> trace;

        const float cut0 = p.params[P_CUTOFF], depth0 = p.params[P_FM_DEPTH];
        synth.command({CMD_PLAY, 0, 0, 0.f});
        synth.command({CMD_RECORD, 0, 0, 1.f});
        synth.command({CMD_TOUCH, P_CUTOFF, 1, 0.f});
        synth.command({CMD_TOUCH, P_FM_DEPTH, 1, 0.f});
        for (int pos = 0; pos < total; pos += block) {
            const int n = std::min(block, total - pos);
            if (pos < barFrames) {  // the "hand" on two knobs, bar 1 only
                const float t = (float)pos / (float)barFrames;
                const float wave = std::sin(kTwoPi * t);
                synth.setParam(P_CUTOFF, std::fmax(0.f, std::fmin(1.f, cut0 + 0.25f * wave)));
                synth.setParam(P_FM_DEPTH, std::fmax(0.f, std::fmin(1.f, depth0 - 0.3f * t)));
            } else if (pos == (barFrames / block) * block + block) {
                synth.command({CMD_TOUCH, P_CUTOFF, 0, 0.f});
                synth.command({CMD_TOUCH, P_FM_DEPTH, 0, 0.f});
                synth.command({CMD_RECORD, 0, 0, 0.f});
            }
            if (pos >= barFrames * bars && pos < barFrames * bars + block)
                synth.command({CMD_STOP, 0, 0, 0.f});  // let the tails ring out
            synth.render(&buf[(size_t)pos * 2], n);
            trace.push_back(synth.param(P_CUTOFF));
        }

        int bad = 0; float peak = 0.f; double rms = 0;
        for (float s : buf) {
            if (!std::isfinite(s)) ++bad;
            peak = std::fmax(peak, std::fabs(s));
            rms += (double)s * s;
        }
        rms = std::sqrt(rms / buf.size());
        const int bpb = barFrames / block;
        float diff = 0.f;
        for (int b = 8; b < bpb - 8; ++b) diff = std::fmax(diff, std::fabs(trace[b] - trace[2 * bpb + b]));
        const bool ok = bad == 0 && rms > 0.01 && diff < 0.05f;
        failures += ok ? 0 : 1;
        std::printf("%-13s peak %.2f  rms %.3f  non-finite %d  motion replay diff %.3f  %s\n",
                    p.name, peak, rms, bad, diff, ok ? "ok" : "CHECK");
        all.insert(all.end(), buf.begin(), buf.end());
    }
    writeWav(out, all, sr);
    std::printf("wrote %s (%.1f s)\n", out, all.size() / 2.0 / sr);
    return failures;
}
