package com.bergz.zx700.ui

import androidx.compose.ui.graphics.Color
import com.bergz.zx700.audio.NativeEngine.P

/**
 * Bergz panel theme: black brushed steel, walnut end-cheeks, an amber display,
 * amber and red accents. Approved design, Nov 2026.
 */
object Theme {
    // accents
    val Amber = Color(0xFFFFB547)
    val Red = Color(0xFFFF3B3B)
    val RedText = Color(0xFFFF6B6B)      // red text on steel
    val ModelRed = Color(0xFFFF5C5C)     // "ZX-700"
    val Rec = Color(0xFFFF4D6D)
    val AmberOff = Color(0xFF3E2A12)     // unlit amber LED
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
    // amber display
    val Screen = Color(0xFF1A1206)
    val Bezel = Color(0xFF2A2E31)
    val DisplayAccent = Color(0xFFFF7A2F)
    // rubber keys
    val Key = Color(0xFF313438)
    val KeyBeat = Color(0xFF3D4146)
    val KeyEdge = Color(0xFF040506)
    val KeyText = Color(0xFFC9CED3)
    // knobs
    val KnobSkirt = Color(0xFF141618)
    val KnobEdge = Color(0xFF3A3F45)
    val KnobCap = Color(0xFF2A2D31)
    val KnobRim = Color(0xFF4A4F55)
    val KnobInner = Color(0xFF33373C)
    val Tick = Color(0xFF8A9198)

    /** Blend between amber (scene A) and red (scene B). */
    fun morphColor(x: Float): Color {
        val t = x.coerceIn(0f, 1f)
        return Color(
            red = Amber.red + (Red.red - Amber.red) * t,
            green = Amber.green + (Red.green - Amber.green) * t,
            blue = Amber.blue + (Red.blue - Amber.blue) * t,
        )
    }
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
