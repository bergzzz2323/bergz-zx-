// MIDI engine test: external clock sync, Start/Stop, live notes and release.
// Build: g++ -O2 -std=c++17 -I../app/src/main/cpp midi_test.cpp -o midi_test
#include "Presets.h"
#include <cstdio>
using namespace bergz;
int main(){
  SynthCore s(48000.f); loadPreset(presets()[0],[&](const Cmd&c){s.command(c);});
  // External clock at 100 BPM: 24 ppqn -> one tick every 1200 samples. Block = 240.
  const int block=240, tickEvery=1200; std::vector<float> buf(block*2);
  s.command({CMD_MIDI_START,0,0,0});
  int steps=0, last=-1, firstTickBlock=-1;
  for(int b=0;b<2000;++b){ // 10 s
    if((b*block)%tickEvery==0){ s.command({CMD_MIDI_CLOCK,0,0,0}); if(firstTickBlock<0) firstTickBlock=b; }
    s.render(buf.data(),block);
    int ph=s.playhead(); if(ph!=last){ ++steps; last=ph; }
  }
  // 10 s at 100 BPM = 16.67 quarters = 66.7 sixteenths
  std::printf("ext sync=%d steps=%d (expect ~67) tempo=%.1f (expect 100)\n", s.externalSync(), steps, s.param(P_TEMPO));
  s.command({CMD_MIDI_STOP,0,0,0}); s.render(buf.data(),block);
  std::printf("after stop playing=%d\n", s.isPlaying());
  // live notes
  s.command({CMD_NOTE_ON,60,0,0.8f}); float pk=0; for(int b=0;b<200;++b){ s.render(buf.data(),block); for(float v:buf) pk=std::fmax(pk,std::fabs(v)); }
  s.command({CMD_NOTE_OFF,60,0,0}); float tail=0; for(int b=0;b<2000;++b){ s.render(buf.data(),block); if(b>1900) for(float v:buf) tail=std::fmax(tail,std::fabs(v)); }
  std::printf("live note peak=%.2f  silence after release=%.4f\n", pk, tail);
  s.command({CMD_PITCH_BEND,0,0,1.f}); s.command({CMD_NOTE_ON,60,0,1.f}); s.render(buf.data(),block); s.command({CMD_ALL_NOTES_OFF,0,0,0});
  const bool ok = s.externalSync() == 0 && steps >= 65 && steps <= 69 && tail < 1e-3f && pk > 0.1f;
  std::printf("%s\n", ok ? "MIDI TEST OK" : "MIDI TEST FAILED");
  return ok ? 0 : 1;
}
