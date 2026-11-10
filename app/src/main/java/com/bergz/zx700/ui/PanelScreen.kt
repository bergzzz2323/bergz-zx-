package com.bergz.zx700.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bergz.zx700.audio.NativeEngine
import com.bergz.zx700.audio.NativeEngine.P

private val glow = { c: Color -> Shadow(color = c, blurRadius = 14f) }

/** The whole instrument on one landscape screen, like a piece of hardware. */
@Composable
fun PanelScreen(state: BergzState) {
    // Drive playhead, motion LEDs and knob positions from the engine every frame.
    LaunchedEffect(Unit) { while (true) { withFrameNanos { }; state.poll() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(Neon.Body)
            .windowInsetsPadding(WindowInsets.displayCutout)  // keep controls clear of the camera
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Header(state)
        Row(Modifier.fillMaxWidth().weight(1.15f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Display(state, Modifier.width(214.dp).fillMaxHeight())
            Section("SOUND · FM", Modifier.weight(3f).fillMaxHeight()) {
                K(state, P.FM_DEPTH, Neon.Pink); K(state, P.HARMONIC, Neon.Blue); K(state, P.FEEDBACK, Neon.Blue)
            }
            Section("TONE · FILTER", Modifier.weight(3f).fillMaxHeight()) {
                K(state, P.CUTOFF, Neon.Blue); K(state, P.RESO, Neon.Blue); K(state, P.ENV_AMT, Neon.Blue)
            }
            Section(
                "FX · ${NativeEngine.FX_NAMES[state.fxType]} ▸",
                Modifier.weight(2.3f).fillMaxHeight(),
                onTitleClick = { state.cycleFx() },
            ) {
                K(state, P.FX_MIX, Neon.Purple)
                K(state, P.FX_PARAM, Neon.Purple, NativeEngine.FX_PARAM_NAMES[state.fxType])
            }
            Section("OUT", Modifier.weight(1.2f).fillMaxHeight()) { K(state, P.VOLUME, Neon.Pink, size = 54) }
        }
        Row(Modifier.fillMaxWidth().weight(1.15f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavCluster(state, Modifier.width(214.dp).fillMaxHeight())
            Section("SHAPE · ENVELOPE", Modifier.weight(4f).fillMaxHeight()) {
                K(state, P.ATTACK, Neon.Blue); K(state, P.DECAY, Neon.Blue)
                K(state, P.SUSTAIN, Neon.Blue); K(state, P.RELEASE, Neon.Blue)
            }
            Section("MOVEMENT", Modifier.weight(3f).fillMaxHeight()) {
                K(state, P.LFO_RATE, Neon.Purple); K(state, P.LFO_DEPTH, Neon.Purple); K(state, P.GATE, Neon.Pink)
            }
            MotionSection(state, Modifier.weight(3.6f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().weight(0.95f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Transport(state, Modifier.width(214.dp).fillMaxHeight())
            Steps(state, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** A knob bound to an engine parameter, with its friendly label. */
@Composable
private fun K(state: BergzState, id: Int, ring: Color, labelOverride: String? = null, size: Int = 46) {
    Knob(
        label = labelOverride ?: PARAM_NAMES[id]?.friendly ?: "",
        value = state.params[id],
        ring = ring,
        knobSize = size.dp,
        onTouch = { state.touch(id, it) },
        onChange = { state.setParam(id, it) },
    )
}

@Composable
private fun Header(state: BergzState) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "BERGZ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp,
            style = TextStyle(shadow = glow(Neon.Pink)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "ZX·700", color = Neon.Blue, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            style = TextStyle(shadow = glow(Neon.Blue)),
        )
        Spacer(Modifier.width(10.dp))
        Text("FM MOTION SYNTHESIZER", color = Neon.Dim, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.weight(1f).height(2.dp)
                .shadow(6.dp, RoundedCornerShape(1.dp), ambientColor = Neon.Pink, spotColor = Neon.Pink)
                .background(Neon.Pink),
        )
        Spacer(Modifier.width(12.dp))
        if (!state.audioOk) Text("AUDIO OFF", color = Neon.Rec, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Display(state: BergzState, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace, color = Neon.Blue,
        shadow = Shadow(Neon.Blue, blurRadius = 10f),
    )
    Column(
        modifier
            .shadow(10.dp, shape, ambientColor = Neon.Blue, spotColor = Neon.Blue)
            .background(Neon.Screen, shape)
            .border(1.dp, Neon.Blue, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PATCH ${state.presetIndex + 1}${if (state.edited) "*" else ""}", style = mono, fontSize = 11.sp)
            Text("ALG ${(state.params[P.ALGORITHM] + 1).toInt()} · C ${state.scale.name}", style = mono, fontSize = 11.sp)
        }
        Text(state.presetName, style = mono, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(state.message, style = mono, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val step = if (state.playhead >= 0) "%02d/16".format(state.playhead + 1) else "--/16"
            Text("STEP $step", style = mono, fontSize = 11.sp)
            Text("${state.tempo} BPM", style = mono, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NavCluster(state: BergzState, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HwButton("PATCH", Modifier.weight(1f), caption = "▸") { state.nextPatch() }
                HwButton(if (state.shift) "SCALE" else "ALGO", Modifier.weight(1f), caption = "▸") { state.algoOrScale() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HwButton("MUTATE", Modifier.weight(1f), activeColor = Neon.Blue, caption = "✦") { state.mutate() }
                HwButton("SHIFT", Modifier.weight(1f), active = state.shift, activeColor = Neon.Blue) { state.toggleShift() }
            }
        }
    }
}

@Composable
private fun MotionSection(state: BergzState, modifier: Modifier) {
    Section("MOTION · SCENE MORPH", modifier) {
        Column(Modifier.fillMaxSize().padding(top = 2.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val recColor = if (state.recording) Neon.Rec else Neon.LedOff
                Box(
                    Modifier.size(8.dp)
                        .let { if (state.recording) it.shadow(6.dp, CircleShape, ambientColor = Neon.Rec, spotColor = Neon.Rec) else it }
                        .background(recColor, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    state.motionNames(), color = if (state.motionMask != 0) Neon.Blue else Neon.Muted,
                    fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                HwButton("", Modifier.width(56.dp), height = 24.dp, caption = "CLEAR") { state.clearMotion() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SceneButton("A", state.morph < 0.01f) { state.scenePressed(false) }
                var dragging by remember { mutableStateOf(false) }
                Slider(
                    value = state.morph,
                    onValueChange = {
                        if (!dragging) { dragging = true; state.morphTouch(true) }
                        state.morphTo(it)
                    },
                    onValueChangeFinished = { dragging = false; state.morphTouch(false) },
                    colors = SliderDefaults.colors(
                        thumbColor = Neon.Blue, activeTrackColor = Neon.Pink, inactiveTrackColor = Neon.Purple,
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
                SceneButton("B", state.morph > 0.99f) { state.scenePressed(true) }
            }
        }
    }
}

@Composable
private fun SceneButton(name: String, lit: Boolean, onClick: () -> Unit) {
    HwButton("", Modifier.width(40.dp), active = lit, activeColor = Neon.Blue, height = 36.dp, round = true, caption = name, onClick = onClick)
}

@Composable
private fun Transport(state: BergzState, modifier: Modifier) {
    // DATA wheel sits low on the left, away from the camera cut-out.
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        DataWheel(Modifier.width(70.dp), wheelSize = 60.dp) { state.dataWheel(it) }
        HwButton("REC", Modifier.weight(1f), active = state.recording, activeColor = Neon.Rec, height = 46.dp, round = true, caption = "●") { state.toggleRecord() }
        HwButton("STOP", Modifier.weight(1f), height = 46.dp, round = true, caption = "■") { state.stop() }
        HwButton("PLAY", Modifier.weight(1f), active = state.playing, activeColor = Neon.Blue, height = 46.dp, round = true, caption = "▶") { state.play() }
    }
}

@Composable
private fun Steps(state: BergzState, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .shadow(8.dp, shape, ambientColor = Neon.Purple, spotColor = Neon.Purple)
            .background(Color(0xFF0A0710), shape)
            .border(1.dp, Neon.Purple, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (i in 0 until NativeEngine.STEPS) {
            val on = state.notes[i][0] >= 0
            val ledColor = if (on) Neon.Pink else null
            val chord = state.notes[i].count { it >= 0 }
            val chordMark = if (chord > 1) "+" else ""
            val text = if (on) noteName(state.notes[i][0]) + chordMark else ""
            StepKey(
                number = i + 1,
                ledColor = ledColor,
                isPlayhead = state.playhead == i,
                isSelected = state.selectedStep == i,
                isBeat = i % 4 == 0,
                noteText = text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { state.stepPressed(i) }
        }
    }
}
