package com.bergz.zx700.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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

/** Left column: display, buttons, transport + the big DATA wheel. */
private val LEFT_COLUMN = 226.dp

/** The whole instrument on one landscape screen, like a piece of hardware. */
@Composable
fun PanelScreen(state: BergzState) {
    // Drive playhead, motion LEDs and knob positions from the engine every frame.
    LaunchedEffect(Unit) { while (true) { withFrameNanos { }; state.poll() } }

    Box(
        Modifier
            .fillMaxSize()
            .brushedSteel()
            .windowInsetsPadding(WindowInsets.displayCutout),  // keep controls clear of the camera
    ) {
    Row(Modifier.fillMaxSize()) {
    WalnutCheek()
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Header(state)
        Row(Modifier.fillMaxWidth().weight(1.15f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Display(state, Modifier.width(LEFT_COLUMN).fillMaxHeight())
            Section("SOUND · FM", Modifier.weight(2.9f).fillMaxHeight()) {
                K(state, P.FM_DEPTH, Theme.Amber); K(state, P.HARMONIC, Theme.Red); K(state, P.FEEDBACK, Theme.Red)
            }
            Section("TONE · FILTER", Modifier.weight(2.9f).fillMaxHeight()) {
                K(state, P.CUTOFF, Theme.Red); K(state, P.RESO, Theme.Red); K(state, P.ENV_AMT, Theme.Red)
            }
            FxSection(state, Modifier.weight(2.7f).fillMaxHeight())
            Section("OUT", Modifier.weight(1.2f).fillMaxHeight()) { K(state, P.VOLUME, Theme.Amber, size = 54) }
        }
        Row(Modifier.fillMaxWidth().weight(1.15f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavCluster(state, Modifier.width(LEFT_COLUMN).fillMaxHeight())
            Section("SHAPE · ENVELOPE", Modifier.weight(4f).fillMaxHeight()) {
                K(state, P.ATTACK, Theme.Red); K(state, P.DECAY, Theme.Red)
                K(state, P.SUSTAIN, Theme.Red); K(state, P.RELEASE, Theme.Red)
            }
            Section("MOVEMENT", Modifier.weight(3f).fillMaxHeight()) {
                K(state, P.LFO_RATE, Theme.Amber); K(state, P.LFO_DEPTH, Theme.Amber); K(state, P.GATE, Theme.Amber)
            }
            MotionSection(state, Modifier.weight(3.6f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().weight(0.95f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Transport(state, Modifier.width(LEFT_COLUMN).fillMaxHeight())
            Steps(state, Modifier.weight(1f).fillMaxHeight())
        }
    }
    WalnutCheek()
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
                    "${state.fx.name} · SETTINGS", color = Theme.Amber, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed, letterSpacing = 2.sp,
                    style = TextStyle(shadow = glow(Theme.Amber)), modifier = Modifier.weight(1f),
                )
                FxLight(state.fxOn(state.fxSelected), big = true) { state.toggleFx() }
                Spacer(Modifier.width(10.dp))
                HwButton("", Modifier.width(76.dp), active = true, activeColor = Theme.Red, height = 30.dp, caption = "DONE") {
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
                        K(state, P.REV_MIX, Theme.Amber, "AMOUNT", size = 56, dim = off)
                        K(state, P.REV_SIZE, Theme.Amber, "SIZE", size = 56, dim = off)
                        K(state, P.REV_TONE, Theme.Red, size = 56, dim = off)
                        K(state, P.REV_WIDTH, Theme.Red, size = 56, dim = off)
                    }
                    0 -> {  // delay
                        K(state, P.DLY_MIX, Theme.Amber, "AMOUNT", size = 52, dim = off)
                        K(state, P.DLY_FB, Theme.Amber, "FEEDBACK", size = 52, dim = off)
                        K(state, P.DLY_TONE, Theme.Red, size = 52, dim = off)
                        Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PanelLabel("TIME")
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                NativeEngine.DELAY_TIMES.forEachIndexed { i, label ->
                                    HwButton(
                                        "", Modifier.weight(1f), active = state.delayTimeIndex == i,
                                        activeColor = Theme.Red, height = 32.dp, caption = label,
                                    ) { state.setDelayTime(i) }
                                }
                            }
                            HwButton(
                                "", Modifier.fillMaxWidth(), active = state.params[P.DLY_PING] > 0.5f,
                                activeColor = Theme.Amber, height = 32.dp, caption = "PING-PONG",
                            ) { state.togglePingPong() }
                        }
                    }
                    else -> {  // phaser
                        K(state, P.PH_MIX, Theme.Amber, "AMOUNT", size = 56, dim = off)
                        K(state, P.PH_RATE, Theme.Amber, "RATE", size = 56, dim = off)
                        K(state, P.PH_DEPTH, Theme.Red, size = 56, dim = off)
                        K(state, P.PH_FEEDBACK, Theme.Red, size = 56, dim = off)
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
            "BERGZ", color = Theme.Text, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic, fontFamily = PanelFonts.Condensed, letterSpacing = 4.sp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "ZX·700", color = Theme.ModelRed, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
            fontStyle = FontStyle.Italic, fontFamily = PanelFonts.Condensed, letterSpacing = 2.sp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "FM MOTION SYNTHESIZER", color = Theme.Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            fontFamily = PanelFonts.Condensed, letterSpacing = 2.sp,
        )
        Spacer(Modifier.width(12.dp))
        // amber-to-red line that fades in and out
        Box(
            Modifier.weight(1f).height(2.dp).background(
                Brush.horizontalGradient(
                    0f to Color.Transparent, 0.18f to Theme.Amber, 0.72f to Theme.Red, 1f to Color.Transparent,
                ),
            ),
        )
        Spacer(Modifier.width(12.dp))
        // MIDI: activity light + connected sources, then where the sound is going.
        LedLens(if (state.midiActive) Theme.Red else null, unlit = Color(0xFF4A1A1A), diameter = 6.dp)
        Spacer(Modifier.width(4.dp))
        val sources = state.midiSources
        Text(
            if (sources.isEmpty()) "MIDI READY" else "MIDI · " + sources.joinToString(", "),
            color = Theme.Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed,
            letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "OUT · ${state.outputName}", color = Theme.Dim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            fontFamily = PanelFonts.Condensed, letterSpacing = 1.sp,
        )
        if (!state.audioOk) {
            Spacer(Modifier.width(8.dp))
            Text("AUDIO OFF", color = Theme.RedText, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed)
        }
    }
}

/** The amber display, set behind a dark bezel. */
@Composable
private fun Display(state: BergzState, modifier: Modifier) {
    val shape = RoundedCornerShape(4.dp)
    val amber = TextStyle(
        fontFamily = PanelFonts.Display, color = Theme.Amber,
        shadow = Shadow(Theme.Amber.copy(alpha = 0.85f), blurRadius = 12f),
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

@Composable
private fun NavCluster(state: BergzState, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HwButton(if (state.shift) "NEXT" else "PATCH", Modifier.weight(1f), caption = "☰") { state.patchPressed() }
                HwButton(if (state.shift) "KEY" else "ALGO", Modifier.weight(1f), caption = "▸") { state.algoOrKey() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HwButton("MUTATE", Modifier.weight(1f), activeColor = Theme.Red, caption = "✦") { state.mutate() }
                HwButton("SHIFT", Modifier.weight(1f), active = state.shift, activeColor = Theme.Red) { state.toggleShift() }
            }
        }
    }
}

@Composable
private fun MotionSection(state: BergzState, modifier: Modifier) {
    Section("MOTION · SCENE MORPH", modifier) {
        Column(Modifier.fillMaxSize().padding(top = 2.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LedLens(if (state.recording) Theme.Rec else null, unlit = Color(0xFF4A1A22), diameter = 8.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    state.motionNames(), color = if (state.motionMask != 0) Theme.RedText else Theme.Dim,
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = PanelFonts.Condensed,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                HwButton("", Modifier.width(58.dp), height = 24.dp, caption = "CLEAR") { state.clearMotion() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SceneButton("A", state.morph < 0.01f) { state.scenePressed(false) }
                Crossfader(
                    value = state.morph,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    onStart = { state.morphTouch(true) },
                    onChange = { state.morphTo(it) },
                    onEnd = { state.morphTouch(false) },
                )
                SceneButton("B", state.morph > 0.99f) { state.scenePressed(true) }
            }
        }
    }
}

@Composable
private fun SceneButton(name: String, lit: Boolean, onClick: () -> Unit) {
    HwButton("", Modifier.width(38.dp), active = lit, height = 38.dp, round = true, caption = name, onClick = onClick)
}

/** REC / STOP / PLAY on the left, the big DATA wheel next to the step keys. */
@Composable
private fun Transport(state: BergzState, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        TransportButton(TransportKind.REC, state.recording) { state.toggleRecord() }
        TransportButton(TransportKind.STOP, false) { state.stop() }
        TransportButton(TransportKind.PLAY, state.playing) { state.play() }
        DataWheel(Modifier.width(84.dp), wheelSize = 78.dp) { state.dataWheel(it) }
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
            val text = if (on) noteName(state.notes[i][0]) + chordMark else ""
            StepKey(
                number = i + 1,
                ledColor = if (on) Theme.Amber else null,
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
                    "PATCHES", color = Theme.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed,
                    letterSpacing = 2.sp, style = TextStyle(shadow = glow(Theme.Red)),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "NOW: ${state.presetName}", color = Theme.Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    fontFamily = PanelFonts.Condensed, maxLines = 1, modifier = Modifier.weight(1f),
                )
                HwButton("", Modifier.width(84.dp), active = true, activeColor = Theme.Red, height = 32.dp, caption = "DONE") {
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
                            selected = state.browserCategory == idx, color = Theme.Amber, height = 44,
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
                            selected = state.presetIndex == info.index, color = Theme.Amber, height = 56,
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
                    "KEY & SCALE", color = Theme.Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed,
                    letterSpacing = 2.sp, style = TextStyle(shadow = glow(Theme.Amber)),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    state.keyScaleName, color = Theme.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    fontFamily = PanelFonts.Condensed, maxLines = 1, modifier = Modifier.weight(1f),
                )
                HwButton("", Modifier.width(84.dp), active = true, activeColor = Theme.Red, height = 32.dp, caption = "DONE") {
                    state.closeKeyPicker()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                KEY_NAMES.forEachIndexed { i, name ->
                    BrowserTile(
                        title = name, subtitle = null, selected = state.key == i, color = Theme.Red,
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
                        color = Theme.Amber, height = 46,
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
                        .background(if (state.fxOn(i)) Theme.Amber else Theme.AmberOff, CircleShape),
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
            K(state, state.fx.mix, Theme.Amber, "AMOUNT", dim = !on)
            K(state, state.fx.param, Theme.Amber, state.fx.paramName, dim = !on)
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
        LedLens(if (on) Theme.Red else null, unlit = Color(0xFF4A1A1A), diameter = if (big) 9.dp else 7.dp)
        Text(
            if (on) "ON" else "OFF", color = if (on) Theme.RedText else Theme.Dim,
            fontSize = if (big) 12.sp else 10.sp, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed,
        )
    }
}
