package com.bergz.zx700.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bergz.zx700.audio.NativeEngine
import com.bergz.zx700.audio.NativeEngine.P

private val glow = { c: Color -> Shadow(color = c, blurRadius = 14f) }

/** The big DATA wheel (dp). */
private val WHEEL = 100.dp

/** Thin walnut cheeks, the same both sides. */
private val CHEEK = 10.dp

/** Display plate width; row 1 is laid out around it. */
private val DISPLAY_W = 236.dp

/** The whole instrument on one landscape screen, like a piece of hardware. */
@Composable
fun PanelScreen(state: BergzState) {
    // Drive playhead, motion LEDs and knob positions from the engine every frame.
    LaunchedEffect(Unit) { while (true) { withFrameNanos { }; state.poll() } }

    // Edge to edge with slim cheeks. The punch-hole camera sits half-way down the
    // left or right edge in landscape. Instead of widening the cheeks, the middle
    // row keeps a clear strip on both outer edges (beside PATCH / MUTATE on the
    // left and the scene morph on the right), so the camera never covers a control.
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout
    val cameraDepth = with(density) { maxOf(cutout.getLeft(this, dir).toDp(), cutout.getRight(this, dir).toDp()) }
    val camStrip = if (cameraDepth > 0.dp) (cameraDepth - CHEEK - 14.dp).coerceIn(16.dp, 28.dp) else 0.dp

    Box(
        Modifier
            .fillMaxSize()
            .brushedSteel(),
    ) {
    Row(Modifier.fillMaxSize()) {
    WalnutCheek(Modifier.width(CHEEK))
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Header(state)
        // row 1: display and the sound
        Row(Modifier.fillMaxWidth().weight(112f), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ScrewPlate(Modifier.width(DISPLAY_W).fillMaxHeight()) { Display(state, Modifier.fillMaxSize()) }
            Section("SOUND · FM", Modifier.weight(3f).fillMaxHeight()) {
                K(state, P.FM_DEPTH, Theme.AccentA); K(state, P.HARMONIC, Theme.AccentB); K(state, P.FEEDBACK, Theme.AccentB)
            }
            Section("TONE · FILTER", Modifier.weight(3f).fillMaxHeight()) {
                K(state, P.CUTOFF, Theme.AccentB); K(state, P.RESO, Theme.AccentB); K(state, P.ENV_AMT, Theme.AccentB)
            }
            FxSection(state, Modifier.weight(2.3f).fillMaxHeight())
            Section("OUT", Modifier.weight(1.3f).fillMaxHeight()) { K(state, P.VOLUME, Theme.AccentA, size = 50) }
        }
        // row 2: buttons, transpose, envelope, movement, then the scene morph for the right hand
        Row(Modifier.fillMaxWidth().weight(137f), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NavCluster(state, Modifier.padding(start = camStrip).width(134.dp).fillMaxHeight())
            TransposeSection(state, Modifier.width(94.dp).fillMaxHeight())
            Section("SHAPE · ENVELOPE", Modifier.width(206.dp).fillMaxHeight()) {
                K(state, P.ATTACK, Theme.AccentB, size = 47); K(state, P.DECAY, Theme.AccentB, size = 47)
                K(state, P.SUSTAIN, Theme.AccentB, size = 47); K(state, P.RELEASE, Theme.AccentB, size = 47)
            }
            Section("MOVEMENT", Modifier.width(170.dp).fillMaxHeight()) {
                K(state, P.LFO_RATE, Theme.AccentA, size = 47); K(state, P.LFO_DEPTH, Theme.AccentA, size = 47)
                K(state, P.GATE, Theme.AccentA, size = 47)
            }
            MorphSection(state, Modifier.weight(1f).fillMaxHeight().padding(end = camStrip))
        }
        // row 3: transport and DATA for the left hand, the 16 steps beside them
        Row(Modifier.fillMaxWidth().weight(116f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Transport(state, Modifier.fillMaxHeight())
            Steps(state, Modifier.weight(1f).fillMaxHeight())
        }
    }
    WalnutCheek(Modifier.width(CHEEK))
    }
    if (state.fxSettingsOpen) FxSettings(state)
    if (state.browserOpen) PatchBrowser(state)
    if (state.keyPickerOpen) KeyPicker(state)
    }
}

/** Pop-up panel with the extra options for the active effect. The pattern keeps playing. */
@Composable
private fun FxSettings(state: BergzState) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { state.closeFxSettings() },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 36.dp)
                .fillMaxWidth(0.66f)
                .fillMaxHeight(0.6f)
                .shadow(16.dp, shape)
                .background(Theme.Popup, shape)
                .border(1.dp, Theme.Line, shape)
                // swallow taps so only the dimmed area outside closes the panel
                .clickable(remember { MutableInteractionSource() }, indication = null) { }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.fx.name} · SETTINGS", color = Theme.AccentA, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed, letterSpacing = 2.sp,
                    style = TextStyle(shadow = glow(Theme.AccentA)), modifier = Modifier.weight(1f),
                )
                FxLight(state.fxOn(state.fxSelected), big = true) { state.toggleFx() }
                Spacer(Modifier.width(10.dp))
                HwButton("", Modifier.width(76.dp), active = true, activeColor = Theme.AccentB, height = 30.dp, caption = "DONE") {
                    state.closeFxSettings()
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Theme.Line))
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val off = !state.fxOn(state.fxSelected)
                when (state.fxSelected) {
                    1 -> {  // reverb
                        K(state, P.REV_MIX, Theme.AccentA, "AMOUNT", size = 56, dim = off)
                        K(state, P.REV_SIZE, Theme.AccentA, "SIZE", size = 56, dim = off)
                        K(state, P.REV_TONE, Theme.AccentB, size = 56, dim = off)
                        K(state, P.REV_WIDTH, Theme.AccentB, size = 56, dim = off)
                    }
                    0 -> {  // delay
                        K(state, P.DLY_MIX, Theme.AccentA, "AMOUNT", size = 52, dim = off)
                        K(state, P.DLY_FB, Theme.AccentA, "FEEDBACK", size = 52, dim = off)
                        K(state, P.DLY_TONE, Theme.AccentB, size = 52, dim = off)
                        Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PanelLabel("TIME")
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                NativeEngine.DELAY_TIMES.forEachIndexed { i, label ->
                                    HwButton(
                                        "", Modifier.weight(1f), active = state.delayTimeIndex == i,
                                        activeColor = Theme.AccentB, height = 32.dp, caption = label,
                                    ) { state.setDelayTime(i) }
                                }
                            }
                            HwButton(
                                "", Modifier.fillMaxWidth(), active = state.params[P.DLY_PING] > 0.5f,
                                activeColor = Theme.AccentA, height = 32.dp, caption = "PING-PONG",
                            ) { state.togglePingPong() }
                        }
                    }
                    else -> {  // phaser
                        K(state, P.PH_MIX, Theme.AccentA, "AMOUNT", size = 56, dim = off)
                        K(state, P.PH_RATE, Theme.AccentA, "RATE", size = 56, dim = off)
                        K(state, P.PH_DEPTH, Theme.AccentB, size = 56, dim = off)
                        K(state, P.PH_FEEDBACK, Theme.AccentB, size = 56, dim = off)
                    }
                }
            }
            Text(
                "Tap the FX name to switch effect · the light turns it on/off · effects that are on all play together",
                color = Theme.Muted, fontSize = 11.sp, fontFamily = PanelFonts.Condensed,
            )
        }
    }
}

/** A knob bound to an engine parameter, with its friendly label. */
@Composable
private fun K(state: BergzState, id: Int, ring: Color, labelOverride: String? = null, size: Int = 46, dim: Boolean = false) {
    Knob(
        label = labelOverride ?: PARAM_NAMES[id]?.friendly ?: "",
        value = state.params[id],
        ring = if (dim) ring.copy(alpha = 0.3f) else ring,
        knobSize = size.dp,
        onTouch = { state.touch(id, it) },
        onChange = { state.setParam(id, it) },
    )
}

@Composable
private fun Header(state: BergzState) {
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "BERGZ", color = Theme.AccentA, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic, fontFamily = PanelFonts.Condensed, letterSpacing = 4.sp,
            style = TextStyle(shadow = Shadow(Theme.AccentA.copy(alpha = 0.55f), blurRadius = 16f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "ZX·700", color = Theme.AccentB, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic, fontFamily = PanelFonts.Condensed, letterSpacing = 2.sp,
            style = TextStyle(shadow = Shadow(Theme.AccentB.copy(alpha = 0.5f), blurRadius = 12f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "FM MOTION SYNTHESIZER", color = Theme.AccentA, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            fontFamily = PanelFonts.Condensed, letterSpacing = 2.sp,
        )
        Spacer(Modifier.width(12.dp))
        // AccentA-to-AccentB line that fades in and out (the scene-morph fade uses the same colours)
        Box(
            Modifier.weight(1f).height(2.dp).background(
                Brush.horizontalGradient(
                    0f to Color.Transparent, 0.18f to Theme.AccentA, 0.72f to Theme.AccentB, 1f to Color.Transparent,
                ),
            ),
        )
        Spacer(Modifier.width(12.dp))
        // MIDI: activity light + connected sources, then where the sound is going.
        LedLens(if (state.midiActive) Theme.AccentA else null, unlit = Theme.AccentAOff, diameter = 6.dp)
        Spacer(Modifier.width(4.dp))
        val sources = state.midiSources
        Text(
            if (sources.isEmpty()) "MIDI READY" else "MIDI · " + sources.joinToString(", "),
            color = Theme.AccentB, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed,
            letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "OUT · ${state.outputName}", color = Theme.AccentA, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            fontFamily = PanelFonts.Condensed, letterSpacing = 1.sp,
        )
        if (!state.audioOk) {
            Spacer(Modifier.width(8.dp))
            Text("AUDIO OFF", color = Theme.RecRed, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed)
        }
    }
}


/** The amber display, set behind a dark bezel. */
@Composable
private fun Display(state: BergzState, modifier: Modifier) {
    val shape = RoundedCornerShape(4.dp)
    val amber = TextStyle(
        fontFamily = PanelFonts.Display, color = Theme.DisplayAmber,
        shadow = Shadow(Theme.DisplayAmber.copy(alpha = 0.85f), blurRadius = 12f),
    )
    Box(
        modifier
            .background(Theme.Bezel, shape)
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(6.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Theme.Screen, RoundedCornerShape(2.dp))
                .drawBehind {
                    // glass: dark inner edge
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent), endY = 10.dp.toPx()))
                }
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${state.presetLabel}${if (state.edited) "*" else ""}", style = amber, fontSize = 16.sp, maxLines = 1)
                val step = if (state.playhead >= 0) "%02d".format(state.playhead + 1) else "--"
                Text("ALG ${(state.params[P.ALGORITHM] + 1).toInt()} · $step/16", style = amber, fontSize = 16.sp, maxLines = 1)
            }
            Text(state.presetName, style = amber, fontSize = 30.sp, maxLines = 1, overflow = TextOverflow.Clip)
            Text(state.message, style = amber, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    state.keyScaleName,
                    style = amber.copy(color = Theme.DisplayAccent, shadow = Shadow(Theme.DisplayAccent.copy(alpha = 0.85f), blurRadius = 12f)),
                    fontSize = 16.sp, maxLines = 1,
                    modifier = Modifier.clickable { state.openKeyPicker() },
                )
                Text(if (state.externalSync) "EXT ${state.tempo} BPM" else "${state.tempo} BPM", style = amber, fontSize = 16.sp)
            }
        }
    }
}

/** PATCH / ALGO / MUTATE / SHIFT: four big buttons that fill their corner. */
@Composable
private fun NavCluster(state: BergzState, modifier: Modifier) {
    BoxWithConstraints(modifier.padding(vertical = 2.dp)) {
        // two rows of label (~13dp) + button, with an 8dp gap between the rows
        val btnH = ((maxHeight - 8.dp - 13.dp * 2) / 2).coerceIn(32.dp, 60.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HwButton(if (state.shift) "NEXT" else "PATCH", Modifier.weight(1f), height = btnH, caption = "☰", captionSize = 17.sp) { state.patchPressed() }
                HwButton(if (state.shift) "KEY" else "ALGO", Modifier.weight(1f), height = btnH, caption = "▸", captionSize = 17.sp) { state.algoOrKey() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HwButton("MUTATE", Modifier.weight(1f), height = btnH, activeColor = Theme.AccentA, caption = "✦", captionSize = 17.sp) { state.mutate() }
                HwButton("SHIFT", Modifier.weight(1f), height = btnH, active = state.shift, activeColor = Theme.AccentA) { state.toggleShift() }
            }
        }
    }
}

/**
 * Scene morph for the right hand: REC light and CLEAR in the title bar, a long
 * horizontal crossfader, A under its left end and B under its right end.
 */
@Composable
private fun MorphSection(state: BergzState, modifier: Modifier) {
    SectionPlate(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("SCENE MORPH", Modifier.weight(1f))
            LedLens(if (state.recording) Theme.Rec else null, unlit = Color(0xFF4A1A22), diameter = 7.dp)
            Spacer(Modifier.width(4.dp))
            HwButton("", Modifier.width(50.dp), height = 20.dp, caption = "CLEAR") { state.clearMotion() }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.Line))
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
            MorphFader(
                value = state.morph,
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp),
                onStart = { state.morphTouch(true) },
                onChange = { state.morphTo(it) },
                onEnd = { state.morphTouch(false) },
            )
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SceneButton("A", state.morph < 0.01f) { state.scenePressed(false) }
                SceneButton("B", state.morph > 0.99f) { state.scenePressed(true) }
            }
        }
    }
}

/** Transpose: +1 / readout / -1 next to a small fader with a centre detent at 0. */
@Composable
private fun TransposeSection(state: BergzState, modifier: Modifier) {
    SectionPlate(modifier) {
        SectionTitle("TRANSPOSE", Modifier.padding(bottom = 2.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.Line))
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HwButton("", Modifier.width(38.dp), height = 26.dp, caption = "+1", captionSize = 13.sp) { state.nudgeTranspose(1) }
                Box(
                    Modifier
                        .width(38.dp)
                        .height(20.dp)
                        .background(Color(0xFF07080A), RoundedCornerShape(3.dp))
                        .border(1.dp, Theme.Line, RoundedCornerShape(3.dp))
                        .clickable { state.setTranspose(0) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        transposeText(state.transpose), color = Theme.AccentA, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed,
                    )
                }
                HwButton("", Modifier.width(38.dp), height = 26.dp, caption = "−1", captionSize = 13.sp) { state.nudgeTranspose(-1) }
            }
            Spacer(Modifier.width(6.dp))
            TransposeFader(state.transpose, Modifier.width(30.dp).fillMaxHeight().padding(vertical = 6.dp)) { state.setTranspose(it) }
        }
    }
}


@Composable
private fun SceneButton(name: String, lit: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    HwButton("", modifier.width(38.dp), active = lit, activeColor = if (name == "A") Theme.AccentA else Theme.AccentB,
        height = 30.dp, caption = name, captionSize = 15.sp, onClick = onClick)
}


/** Bottom-left, for the left hand: REC and STOP stacked, a big PLAY, then the DATA wheel. */
@Composable
private fun Transport(state: BergzState, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        // Each button is label (~12dp) + 3dp gap + an 8dp ring around the dome; size the domes
        // so the stacked REC/STOP pair and the big PLAY always fit the row's height.
        val small = ((maxHeight - 46.dp) / 2).coerceIn(26.dp, 38.dp)
        val big = (maxHeight - 30.dp).coerceIn(48.dp, 68.dp)
        val wheel = (maxHeight - 14.dp).coerceIn(72.dp, WHEEL)
        Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TransportButton(TransportKind.REC, state.recording, size = small) { state.toggleRecord() }
                TransportButton(TransportKind.STOP, false, size = small) { state.stop() }
            }
            TransportButton(TransportKind.PLAY, state.playing, size = big) { state.play() }
            DataWheel(Modifier.width(wheel + 6.dp), wheelSize = wheel, labelOnTop = true) { state.dataWheel(it) }
        }
    }
}


@Composable
private fun Steps(state: BergzState, modifier: Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.25f), shape)
            .border(1.dp, Theme.Line, shape)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), endY = 5.dp.toPx()))
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        for (i in 0 until NativeEngine.STEPS) {
            val on = state.notes[i][0] >= 0
            val chord = state.notes[i].count { it >= 0 }
            val chordMark = if (chord > 1) "+" else ""
            val ghost = state.ghosts[i]
            val text = when {
                on -> noteName(state.notes[i][0]) + chordMark
                ghost[0] >= 0 -> noteName(ghost[0]) + if (ghost.count { it >= 0 } > 1) "+" else ""
                else -> ""
            }
            StepKey(
                number = i + 1,
                ledColor = if (on) Theme.AccentB else null,
                noteDim = !on && ghost[0] >= 0,
                isPlayhead = state.playhead == i,
                isSelected = state.selectedStep == i,
                isBeat = i % 4 == 0,
                noteText = text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) { state.stepPressed(i) }
        }
    }
}

/**
 * Patch browser: categories down the left, that category's sounds on the right.
 * Both lists scroll, every tile is a full-size touch target, and nothing is cut off.
 * Tap a sound to hear it straight away; DONE or tap outside to close.
 */
@Composable
private fun PatchBrowser(state: BergzState) {
    val shape = RoundedCornerShape(14.dp)
    Overlay(onDismiss = { state.closeBrowser() }) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.94f)
                .shadow(16.dp, shape)
                .background(Theme.Popup, shape)
                .border(1.dp, Theme.Line, shape)
                .clickable(remember { MutableInteractionSource() }, indication = null) { }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PATCHES", color = Theme.AccentB, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed,
                    letterSpacing = 2.sp, style = TextStyle(shadow = glow(Theme.AccentB)),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "NOW: ${state.presetName}", color = Theme.AccentA, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    fontFamily = PanelFonts.Condensed, maxLines = 1, modifier = Modifier.weight(1f),
                )
                HwButton("", Modifier.width(84.dp), active = true, activeColor = Theme.AccentB, height = 32.dp, caption = "DONE") {
                    state.closeBrowser()
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.Line))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // categories
                val catList = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, state.browserCategory - 2))
                LazyColumn(
                    state = catList,
                    modifier = Modifier.width(118.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    itemsIndexed(state.categories) { idx, name ->
                        val count = state.library.count { it.category == idx }
                        BrowserTile(
                            title = name, subtitle = "$count sounds",
                            selected = state.browserCategory == idx, color = Theme.AccentA, height = 44,
                        ) { state.browserCategory = idx }
                    }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Theme.Line))
                // sounds in the chosen category
                val sounds = state.library.filter { it.category == state.browserCategory }
                val current = sounds.indexOfFirst { it.index == state.presetIndex }
                val grid = rememberLazyGridState(initialFirstVisibleItemIndex = maxOf(0, current))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = grid,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(sounds, key = { it.index }) { info ->
                        BrowserTile(
                            title = info.name,
                            subtitle = state.categories.getOrElse(info.category) { "" },
                            selected = state.presetIndex == info.index, color = Theme.AccentA, height = 56,
                        ) { state.loadPreset(info.index) }
                    }
                }
            }
        }
    }
}

/** Key & scale picker: 12 keys across the top, every scale below. The pattern moves with you. */
@Composable
private fun KeyPicker(state: BergzState) {
    val shape = RoundedCornerShape(14.dp)
    Overlay(onDismiss = { state.closeKeyPicker() }) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f)
                .shadow(16.dp, shape)
                .background(Theme.Popup, shape)
                .border(1.dp, Theme.Line, shape)
                .clickable(remember { MutableInteractionSource() }, indication = null) { }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "KEY & SCALE", color = Theme.AccentA, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed,
                    letterSpacing = 2.sp, style = TextStyle(shadow = glow(Theme.AccentA)),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    state.keyScaleName, color = Theme.AccentB, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    fontFamily = PanelFonts.Condensed, maxLines = 1, modifier = Modifier.weight(1f),
                )
                HwButton("", Modifier.width(84.dp), active = true, activeColor = Theme.AccentB, height = 32.dp, caption = "DONE") {
                    state.closeKeyPicker()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                KEY_NAMES.forEachIndexed { i, name ->
                    BrowserTile(
                        title = name, subtitle = null, selected = state.key == i, color = Theme.AccentB,
                        height = 44, modifier = Modifier.weight(1f),
                    ) { state.setKeyScale(i, state.scaleIndex) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.Line))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(SCALES.indices.toList()) { i ->
                    BrowserTile(
                        title = SCALES[i].name, subtitle = null, selected = state.scaleIndex == i,
                        color = Theme.AccentA, height = 46,
                    ) { state.setKeyScale(state.key, i) }
                }
            }
        }
    }
}

/** Dimmed full-screen backdrop; tapping it closes the pop-up. */
@Composable
private fun Overlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A big, clear tile for lists: neon border and glow when selected. */
@Composable
private fun BrowserTile(
    title: String,
    subtitle: String?,
    selected: Boolean,
    color: Color,
    height: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(5.dp)
    Column(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .let { if (selected) it.shadow(10.dp, shape, ambientColor = color, spotColor = color) else it }
            .background(if (selected) color.copy(alpha = 0.22f) else Theme.Body, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) color else Theme.Line, shape)
            .clickable(remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title, color = if (selected) Color.White else Theme.Text, fontSize = 14.sp,
            fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(subtitle, color = Theme.Muted, fontSize = 10.sp, fontFamily = PanelFonts.Condensed, letterSpacing = 1.sp, maxLines = 1)
        }
    }
}

/**
 * FX section: tap the effect name to show the next effect's knobs, double-tap
 * for its settings. The light turns the shown effect on or off; the three dots
 * show which effects are on, so you can see what is stacked.
 */
@Composable
private fun FxSection(state: BergzState, modifier: Modifier) {
    val on = state.fxOn(state.fxSelected)
    SectionPlate(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(
                "${state.fx.name} ▸",
                Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { state.cycleFx() }, onDoubleTap = { state.openFxSettings() })
                    },
            )
            NativeEngine.FX.indices.forEach { i ->
                Box(
                    Modifier
                        .padding(horizontal = 1.5.dp)
                        .size(if (i == state.fxSelected) 6.dp else 4.dp)
                        .background(if (state.fxOn(i)) Theme.AccentB else Theme.AccentBOff, CircleShape),
                )
            }
            Spacer(Modifier.width(4.dp))
            FxLight(on) { state.toggleFx() }
        }
        Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(1.dp).background(Theme.Line))
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            K(state, state.fx.mix, Theme.AccentA, "AMOUNT", dim = !on)
            K(state, state.fx.param, Theme.AccentA, state.fx.paramName, dim = !on)
        }
    }
}

/** Small on/off light: a red LED in a dark pill. */
@Composable
private fun FxLight(on: Boolean, big: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .height(if (big) 32.dp else 22.dp)
            .background(Color(0xFF1B1E21), shape)
            .border(1.dp, Color.Black, shape)
            .clickable(remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = if (big) 8.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LedLens(if (on) Theme.AccentB else null, unlit = Theme.AccentBOff, diameter = if (big) 9.dp else 7.dp)
        Text(
            if (on) "ON" else "OFF", color = if (on) Theme.AccentB else Theme.Dim,
            fontSize = if (big) 12.sp else 10.sp, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed,
        )
    }
}
