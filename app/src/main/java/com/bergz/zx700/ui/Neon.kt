package com.bergz.zx700.ui

import androidx.compose.ui.graphics.Color
import com.bergz.zx700.audio.NativeEngine.P

/** The Bergz neon palette (purple / pink / electric blue on near-black). */
object Neon {
    val Body = Color(0xFF0D0915)
    val Panel = Color(0xFF160F22)
    val Screen = Color(0xFF07050B)
    val Line = Color(0xFF3B2A57)
    val Purple = Color(0xFFA45CFF)
    val Pink = Color(0xFFFF3DCB)
    val Blue = Color(0xFF3DB8FF)
    val Text = Color(0xFFE4DCF2)
    val Label = Color(0xFFCFC3E6)
    val Dim = Color(0xFFB9A9D6)
    val Muted = Color(0xFF8F80AA)
    val Key = Color(0xFF2C2734)
    val KeyBeat = Color(0xFF3A3442)
    val KeyEdge = Color(0xFF0A070E)
    val LedOff = Color(0xFF2B1A34)
    val Rec = Color(0xFFFF4D6D)
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
    P.FX_MIX to ParamName("AMOUNT", "FX MIX"),
    P.FX_PARAM to ParamName("SIZE", "FX PARAM"),
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

data class Scale(val name: String, val intervals: List<Int>)

val SCALES = listOf(
    Scale("MINOR", listOf(0, 2, 3, 5, 7, 8, 10)),
    Scale("MAJOR", listOf(0, 2, 4, 5, 7, 9, 11)),
    Scale("DORIAN", listOf(0, 2, 3, 5, 7, 9, 10)),
    Scale("PENTA", listOf(0, 3, 5, 7, 10)),
)

private val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
fun noteName(midi: Int): String = if (midi < 0) "--" else NOTE_NAMES[midi % 12] + (midi / 12 - 1)
