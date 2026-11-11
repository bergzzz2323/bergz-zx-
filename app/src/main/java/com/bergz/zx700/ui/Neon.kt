package com.bergz.zx700.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.bergz.zx700.audio.NativeEngine.P

/**
 * Bergz panel theme: black brushed steel, walnut end-cheeks, an amber display.
 *
 * The two accent colours are the whole colour scheme: every title, LED, knob
 * ring, lit button, the header line, the scene-morph fade and the app icon are
 * made from AccentA and AccentB. To change the scheme, change these two (and
 * the matching bergz_accent_a / bergz_accent_b in res/values/colors.xml, which
 * the app icon uses). The amber display and the REC / STOP buttons are fixed.
 */
object Theme {
    // ---- the colour scheme (v0.3: purple + mint)
    val AccentA = Color(0xFFB07CFF)      // purple: titles, scene A, clock light, lit buttons
    val AccentB = Color(0xFF3DF5B5)      // mint: assigned steps, scene B, ON lights, PLAY
    val AccentAOff = Color(0xFF2A1A40)   // unlit purple LED
    val AccentBOff = Color(0xFF123A2D)   // unlit mint LED

    // lit rubber button (SHIFT, scenes): made from AccentA
    val ActiveTop = lerp(AccentA, Color.White, 0.35f)
    val ActiveBottom = lerp(AccentA, Color.Black, 0.2f)
    val ActiveEdge = lerp(AccentA, Color.Black, 0.62f)

    // ---- fixed colours
    val RecRed = Color(0xFFFF3B3B)       // REC button
    val Rec = Color(0xFFFF4D6D)          // motion-recording light
    // steel
    val SteelTop = Color(0xFF17181A)
    val SteelMid = Color(0xFF0E0F10)
    val SteelBottom = Color(0xFF08090A)
    val Body = SteelMid
    val Panel = Color(0x06FFFFFF)        // section plate, a whisker lighter than the steel
    val Popup = Color(0xFF16181B)        // pop-up panels
    val Line = Color(0xFF2E3237)         // section outlines, dividers
    val Edge = Color(0xFF0E1012)         // rubber key edges
    // ink
    val Text = Color(0xFFECEEF0)
    val Label = Color(0xFFE2E5E8)
    val Dim = Color(0xFFA9B0B7)
    val Muted = Color(0xFFA9B0B7)
    // amber display (fixed)
    val DisplayAmber = Color(0xFFFFB547)
    val Screen = Color(0xFF1A1206)
    val Bezel = Color(0xFF2A2E31)
    val DisplayAccent = Color(0xFFFF7A2F)
    // rubber keys
    val KeyEdge = Color(0xFF040506)
    val KeyText = Color(0xFFC9CED3)
    // knobs
    val KnobSkirt = Color(0xFF141618)
    val KnobEdge = Color(0xFF3A3F45)
    val KnobCap = Color(0xFF2A2D31)
    val KnobRim = Color(0xFF4A4F55)
    val KnobInner = Color(0xFF33373C)
    val Tick = Color(0xFF8A9198)

    /** Scene morph colour: AccentA at scene A, AccentB at scene B. */
    fun morphColor(x: Float): Color = lerp(AccentA, AccentB, x.coerceIn(0f, 1f))
}

/** Friendly knob label + the technical name shown on the display while turning. */
data class ParamName(val friendly: String, val technical: String)

val PARAM_NAMES: Map<Int, ParamName> = mapOf(
    P.FM_DEPTH to ParamName("BRIGHT", "FM DEPTH"),
    P.HARMONIC to ParamName("CHARACTER", "HARMONIC"),
    P.FEEDBACK to ParamName("GRIT", "FEEDBACK"),
    P.CUTOFF to ParamName("TONE", "CUTOFF"),
    P.RESO to ParamName("PEAK", "RESONANCE"),
    P.ENV_AMT to ParamName("SWEEP", "FILTER ENV"),
    P.DLY_MIX to ParamName("DELAY", "DELAY AMOUNT"),
    P.DLY_FB to ParamName("FEEDBK", "DELAY FEEDBACK"),
    P.REV_MIX to ParamName("REVERB", "REVERB AMOUNT"),
    P.REV_SIZE to ParamName("SIZE", "REVERB SIZE"),
    P.PH_MIX to ParamName("PHASER", "PHASER AMOUNT"),
    P.PH_RATE to ParamName("RATE", "PHASER RATE"),
    P.VOLUME to ParamName("VOLUME", "MASTER"),
    P.ATTACK to ParamName("FADE IN", "ATTACK"),
    P.DECAY to ParamName("DECAY", "DECAY"),
    P.SUSTAIN to ParamName("HOLD", "SUSTAIN"),
    P.RELEASE to ParamName("TAIL", "RELEASE"),
    P.LFO_RATE to ParamName("WOBBLE SPD", "LFO RATE"),
    P.LFO_DEPTH to ParamName("WOBBLE", "LFO DEPTH"),
    P.GATE to ParamName("LENGTH", "GATE"),
    P.REV_TONE to ParamName("TONE", "REVERB TONE"),
    P.REV_WIDTH to ParamName("WIDTH", "STEREO WIDTH"),
    P.DLY_TONE to ParamName("TONE", "DELAY TONE"),
    P.PH_DEPTH to ParamName("DEPTH", "SWEEP RANGE"),
    P.PH_FEEDBACK to ParamName("INTENSITY", "PHASER FEEDBACK"),
)

private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
fun noteName(midi: Int): String = if (midi < 0) "--" else NOTE_NAMES[midi % 12] + (midi / 12 - 1)
