// FX test: every on/off combination of delay, reverb and phaser at extreme
// settings, click check while toggling, and start-up defaults (delay only).
// Build: g++ -O2 -std=c++17 -I../app/src/main/cpp fx_test.cpp -o fx_test
#include "Presets.h"
#include <cstdio>
using namespace bergz;
int main(){
  int fails=0; float worst=0;
  for(size_t pi=0; pi<presets().size(); pi+=7)
  for(int mask=0; mask<8; ++mask) for(float x : {0.f, 1.f}) {
    SynthCore s(48000.f); loadPreset(presets()[pi],[&](const Cmd&c){s.command(c);});
    s.setParam(P_REV_ON, mask&1); s.setParam(P_DLY_ON, (mask>>1)&1); s.setParam(P_PH_ON, (mask>>2)&1);
    for(int id: {P_REV_MIX,P_REV_SIZE,P_DLY_MIX,P_DLY_FB,P_PH_MIX,P_PH_RATE,P_REV_TONE,P_REV_WIDTH,P_DLY_TIME,P_DLY_TONE,P_DLY_PING,P_PH_DEPTH,P_PH_FEEDBACK}) s.setParam(id,x);
    s.command({CMD_PLAY,0,0,0}); std::vector<float> b(48000*2*6); s.render(b.data(),48000*6);
    int bad=0; float pk=0; for(float v:b){ if(!std::isfinite(v)) ++bad; pk=std::fmax(pk,std::fabs(v)); }
    if(bad) { ++fails; std::printf("BAD %s mask %d x %.0f\n", presets()[pi].name, mask, x); }
    worst=std::fmax(worst,pk);
  }
  // on/off click check: toggle delay+reverb mid-note, look for sample jumps
  SynthCore s(48000.f); loadPreset(presets()[5],[&](const Cmd&c){s.command(c);}); s.command({CMD_PLAY,0,0,0});
  std::vector<float> b(480*2); float prev=0, jump=0;
  for(int k=0;k<400;++k){ if(k%50==25){ s.command({CMD_PARAM,P_REV_ON,0,(float)((k/50)%2)}); s.command({CMD_PARAM,P_PH_ON,0,(float)((k/50)%2)}); }
    s.render(b.data(),480); for(int i=0;i<480;++i){ jump=std::fmax(jump,std::fabs(b[2*i]-prev)); prev=b[2*i]; } }
  // reset defaults restores delay-only
  s.command({CMD_RESET_DEFAULTS,0,0,0});
  std::printf("stack combos: %d failures, max peak %.2f; largest sample step while toggling %.3f; after reset rev=%.0f dly=%.0f ph=%.0f\n",
    fails, worst, jump, s.param(P_REV_ON), s.param(P_DLY_ON), s.param(P_PH_ON));
  const bool ok = fails == 0 && jump < 0.2f && s.param(P_DLY_ON) > 0.5f && s.param(P_REV_ON) < 0.5f && s.param(P_PH_ON) < 0.5f;
  std::printf("%s\n", ok ? "FX TEST OK" : "FX TEST FAILED");
  return ok ? 0 : 1;
}
