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
        const val DLY_MIX = 6; const val DLY_FB = 7; const val VOLUME = 8
        const val ATTACK = 9; const val DECAY = 10; const val SUSTAIN = 11; const val RELEASE = 12
        const val LFO_RATE = 13; const val LFO_DEPTH = 14; const val GATE = 15
        const val REV_MIX = 16; const val REV_SIZE = 17
        const val PH_MIX = 18; const val PH_RATE = 19
        const val TEMPO = 20; const val ALGORITHM = 21; const val FX_TYPE = 22
        // FX settings panel
        const val REV_TONE = 23; const val REV_WIDTH = 24
        const val DLY_TIME = 25; const val DLY_TONE = 26; const val DLY_PING = 27
        const val PH_DEPTH = 28; const val PH_FEEDBACK = 29
        // FX on/off lights
        const val REV_ON = 30; const val DLY_ON = 31; const val PH_ON = 32
        // Sequencer transpose in semitones, -12..+12
        const val TRANSPOSE = 33
        const val COUNT = 34
        /** Params below this id can be motion-recorded and are part of scenes A/B. */
        const val MOTION_COUNT = TEMPO
        /** Presets store params below this id; FX panel settings and lights are kept across patches. */
        const val PRESET_COUNT = REV_TONE
    }

    const val STEPS = 16
    const val NOTES_PER_STEP = 4
    const val ALGORITHMS = 4

    /** The three effects in panel order (delay first). Index = FxType in SynthCore.h. */
    data class Fx(val name: String, val mix: Int, val param: Int, val paramName: String, val on: Int)
    val FX = listOf(
        Fx("DELAY", P.DLY_MIX, P.DLY_FB, "FEEDBK", P.DLY_ON),
        Fx("REVERB", P.REV_MIX, P.REV_SIZE, "SIZE", P.REV_ON),
        Fx("PHASER", P.PH_MIX, P.PH_RATE, "RATE", P.PH_ON),
    )
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
    external fun nativeResetDefaults()
    external fun nativeDefaultParam(id: Int): Float
    external fun nativeSetDrum(lane: Int, step: Int, on: Boolean)
    external fun nativeSetLock(step: Int, value: Float)
    external fun nativePlayhead(): Int
    external fun nativeMotionMask(): Int
    external fun nativeParam(id: Int): Float

    external fun nativePresetCount(): Int
    external fun nativePresetName(index: Int): String
    external fun nativePresetParams(index: Int): FloatArray
    external fun nativePresetNotes(index: Int): IntArray
    external fun nativeLoadPreset(index: Int)
    external fun nativePresetCategory(index: Int): Int
    external fun nativeCategoryCount(): Int
    external fun nativeCategoryName(index: Int): String

    // Live MIDI
    external fun nativeNoteOn(note: Int, velocity: Float)
    external fun nativeNoteOff(note: Int)
    external fun nativeAllNotesOff()
    external fun nativePitchBend(value: Float)
    external fun nativeMidiClock()
    external fun nativeMidiStart()
    external fun nativeMidiContinue()
    external fun nativeMidiStop()
    external fun nativeIsPlaying(): Boolean
    external fun nativeExternalSync(): Boolean
    external fun nativeRestartAudio(): Boolean

    @Volatile private var started = false
    @Volatile var audioOk = false
        private set

    /** Starts audio once, whoever needs it first (the screen or the MIDI service). */
    @Synchronized
    fun ensureStarted(): Boolean {
        if (!started) { audioOk = nativeStart(); started = true }
        return audioOk
    }

    @Synchronized
    fun restartAudio() {
        if (started) audioOk = nativeRestartAudio()
    }
}
