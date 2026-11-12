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
        // Pattern + sync settings (real values)
        const val PAT_LEN = 34      // 1..64 steps
        const val RATE = 35         // index into RATES
        const val SWING = 36        // 0.50..0.75
        const val SYNC_MODE = 37    // 0 AUTO, 1 INT, 2 EXT
        const val SYNC_OFFSET = 38  // ms -60..60
        const val START_Q = 39      // 0 next bar, 1 next beat, 2 now
        const val CLOCK_OUT = 40    // 0/1
        const val COUNT = 41
        /** Params below this id can be motion-recorded and are part of scenes A/B. */
        const val MOTION_COUNT = TEMPO
        /** Presets store params below this id; FX panel settings and lights are kept across patches. */
        const val PRESET_COUNT = REV_TONE
    }

    /** Steps on one page (and in a factory patch's pattern). */
    const val STEPS = 16
    /** Longest pattern: 4 pages. */
    const val MAX_STEPS = 64
    const val NOTES_PER_STEP = 4
    val RATES = listOf("1/32", "1/16", "1/8", "1/4", "1/2", "1 BAR")
    val LENGTHS = listOf(16, 32, 48, 64)
    const val SYNC_AUTO = 0; const val SYNC_INT = 1; const val SYNC_EXT = 2
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
    /** [timestampNs]: when the clock byte arrived (System.nanoTime base), so it can be placed exactly. */
    external fun nativeMidiClock(timestampNs: Long)
    external fun nativeSetStepProps(step: Int, chance: Int, ratchet: Int, tie: Int)
    /** bit 0 following external clock, bit 1 clock arriving, bit 2 waiting for the next bar/beat. */
    external fun nativeSyncState(): Int
    external fun nativeBeat(): Int
    /** Fills (status, timeNs) pairs of MIDI clock-out events; returns the number of pairs. */
    external fun nativePollClockOut(out: LongArray): Int
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
