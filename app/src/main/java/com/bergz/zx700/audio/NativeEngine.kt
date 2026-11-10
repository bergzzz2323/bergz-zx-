package com.bergz.zx700.audio

/**
 * Thin Kotlin face of the C++ engine. Every call is non-blocking: setters post
 * into a lock-free queue the audio thread drains, getters read atomics.
 */
object NativeEngine {
    init { System.loadLibrary("bergz") }

    // Keep in sync with enum Param in SynthCore.h
    object P {
        const val FM_DEPTH = 0; const val HARMONIC = 1; const val FEEDBACK = 2
        const val CUTOFF = 3; const val RESO = 4; const val ENV_AMT = 5
        const val FX_MIX = 6; const val FX_PARAM = 7; const val VOLUME = 8
        const val ATTACK = 9; const val DECAY = 10; const val SUSTAIN = 11; const val RELEASE = 12
        const val LFO_RATE = 13; const val LFO_DEPTH = 14; const val GATE = 15
        const val TEMPO = 16; const val ALGORITHM = 17; const val FX_TYPE = 18
        // FX settings panel
        const val REV_TONE = 19; const val REV_WIDTH = 20
        const val DLY_TIME = 21; const val DLY_TONE = 22; const val DLY_PING = 23
        const val PH_DEPTH = 24; const val PH_FEEDBACK = 25
        const val COUNT = 26
        /** Presets store params below this id; FX panel settings are kept across patches. */
        const val PRESET_COUNT = REV_TONE
        /** Params below this id can be motion-recorded. */
        const val MOTION_COUNT = TEMPO
    }

    const val STEPS = 16
    const val NOTES_PER_STEP = 4
    const val DRUM_LANES = 4
    const val ALGORITHMS = 4
    val FX_NAMES = listOf("REVERB", "DELAY", "PHASER")
    val FX_PARAM_NAMES = listOf("SIZE", "FEEDBK", "RATE")
    val DELAY_TIMES = listOf("1/16", "1/8", "1/8.", "1/4")

    external fun nativeStart(): Boolean
    external fun nativeStop()
    external fun nativeSetParam(id: Int, value: Float)
    external fun nativeTouch(id: Int, down: Boolean)
    external fun nativeSetRecord(on: Boolean)
    external fun nativeClearMotion(id: Int)
    external fun nativePlay()
    external fun nativeStopSequencer()
    external fun nativeSetStepNote(step: Int, slot: Int, note: Int)
    external fun nativeClearPattern()
    external fun nativeSetDrum(lane: Int, step: Int, on: Boolean)
    external fun nativeSetLock(step: Int, value: Float)
    external fun nativePlayhead(): Int
    external fun nativeMotionMask(): Int
    external fun nativeParam(id: Int): Float

    external fun nativePresetCount(): Int
    external fun nativePresetName(index: Int): String
    external fun nativePresetParams(index: Int): FloatArray
    external fun nativePresetNotes(index: Int): IntArray
    external fun nativePresetDrums(index: Int): IntArray
    external fun nativeLoadPreset(index: Int)
}
