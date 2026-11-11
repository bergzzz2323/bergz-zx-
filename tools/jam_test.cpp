// Jam test: tempo belongs to the player (patches don't change it), transpose
// range and rounding, and start-up defaults.
// Build: g++ -O2 -std=c++17 -I../app/src/main/cpp jam_test.cpp -o jam_test
#include "Presets.h"

#include <cstdio>

using namespace bergz;

int main() {
    int fails = 0;
    auto check = [&](bool ok, const char* what) {
        std::printf("%-48s %s\n", what, ok ? "ok" : "FAILED");
        if (!ok) ++fails;
    };

    // Every bank holds exactly 15 patches (the browser shows them as a 3 x 5 grid).
    {
        int count[kCategories] = {};
        for (const Preset& p : presets()) if (p.category >= 0 && p.category < kCategories) ++count[p.category];
        bool all15 = true;
        for (int c = 0; c < kCategories; ++c) if (count[c] != 15) { all15 = false; std::printf("  %s has %d\n", kCategoryNames[c], count[c]); }
        check(all15, "every bank has 15 patches");
    }

    SynthCore s(48000.f);
    check(s.param(P_TEMPO) == 120.f, "starts at 120 BPM");
    check(s.param(P_TRANSPOSE) == 0.f, "starts with transpose 0");

    s.setParam(P_TEMPO, 97.f);
    for (const Preset& p : presets()) loadPreset(p, [&](const Cmd& c) { s.command(c); });
    check(s.param(P_TEMPO) == 97.f, "loading every patch leaves the tempo alone");

    s.setParam(P_TRANSPOSE, 30.f);  check(s.param(P_TRANSPOSE) == 12.f, "transpose clamps at +12");
    s.setParam(P_TRANSPOSE, -30.f); check(s.param(P_TRANSPOSE) == -12.f, "transpose clamps at -12");
    s.setParam(P_TRANSPOSE, 2.4f);  check(s.param(P_TRANSPOSE) == 2.f, "transpose rounds to whole semitones");
    loadPreset(presets()[0], [&](const Cmd& c) { s.command(c); });
    check(s.param(P_TRANSPOSE) == 2.f, "transpose survives a patch change");

    // Transposed pattern still renders cleanly at both extremes.
    for (float tr : {-12.f, 12.f}) {
        SynthCore t(48000.f);
        loadPreset(presets()[1], [&](const Cmd& c) { t.command(c); });
        t.setParam(P_TRANSPOSE, tr);
        t.command({CMD_PLAY, 0, 0, 0.f});
        std::vector<float> buf(48000 * 2 * 4);
        t.render(buf.data(), 48000 * 4);
        int bad = 0; float pk = 0.f;
        for (float v : buf) { if (!std::isfinite(v)) ++bad; pk = std::fmax(pk, std::fabs(v)); }
        check(bad == 0 && pk > 0.05f, tr > 0 ? "plays cleanly at +12" : "plays cleanly at -12");
    }

    s.command({CMD_RESET_DEFAULTS, 0, 0, 0.f});
    check(s.param(P_TEMPO) == 120.f && s.param(P_TRANSPOSE) == 0.f, "app start resets to 120 BPM and transpose 0");

    std::printf("%s\n", fails ? "JAM TEST FAILED" : "JAM TEST OK");
    return fails ? 1 : 0;
}
