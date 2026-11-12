package com.bergz.zx700.midi

import android.media.midi.MidiReceiver
import android.os.SystemClock
import com.bergz.zx700.audio.NativeEngine
import com.bergz.zx700.audio.NativeEngine.P

/** Shared MIDI status the screen reads (activity light, sources). */
object MidiStatus {
    @Volatile var lastEventAt = 0L
}

/**
 * Turns raw MIDI bytes into engine calls. Listens on all channels (omni).
 *  - Note on/off with velocity, pitch bend (+/- 2 semitones)
 *  - Clock, Start, Continue, Stop: the sequencer follows the host's tempo and transport
 *  - CCs: every front-panel knob (see CC_MAP), 120/123 = all notes off
 * One instance per MIDI source, so running status never mixes between devices.
 */
class BergzMidiReceiver : MidiReceiver() {
    private var status = 0
    private val data = IntArray(2)
    private var have = 0

    /** Arrival time of the bytes being handled (System.nanoTime base), for clock timing. */
    private var stamp = 0L

    override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
        stamp = if (timestamp > 0) timestamp else System.nanoTime()
        for (i in offset until offset + count) handle(msg[i].toInt() and 0xFF)
    }

    private fun handle(b: Int) {
        if (b >= 0xF8) { realtime(b); return }          // real-time can arrive mid-message
        if (b >= 0x80) {                                 // new status byte
            status = if (b < 0xF0) b else 0              // ignore SysEx / system common
            have = 0
            return
        }
        if (status == 0) return
        data[have++] = b
        val needed = when (status and 0xF0) { 0xC0, 0xD0 -> 1; else -> 2 }
        if (have < needed) return
        have = 0                                         // keep status for running status
        dispatch(status and 0xF0, data[0], data[1])
    }

    private fun realtime(b: Int) {
        when (b) {
            0xF8 -> NativeEngine.nativeMidiClock(stamp)
            0xFA -> NativeEngine.nativeMidiStart()
            0xFB -> NativeEngine.nativeMidiContinue()
            0xFC -> NativeEngine.nativeMidiStop()
            else -> return
        }
        if (b != 0xF8) MidiStatus.lastEventAt = SystemClock.uptimeMillis()
    }

    private fun dispatch(type: Int, d1: Int, d2: Int) {
        when (type) {
            0x90 -> if (d2 > 0) NativeEngine.nativeNoteOn(d1, d2 / 127f) else NativeEngine.nativeNoteOff(d1)
            0x80 -> NativeEngine.nativeNoteOff(d1)
            0xE0 -> NativeEngine.nativePitchBend((((d2 shl 7) or d1) - 8192) / 8192f)
            0xB0 -> when (d1) {
                120, 123 -> NativeEngine.nativeAllNotesOff()
                else -> CC_MAP[d1]?.let { NativeEngine.nativeSetParam(it, d2 / 127f) }
            }
            else -> return
        }
        MidiStatus.lastEventAt = SystemClock.uptimeMillis()
    }

    companion object {
        /** MIDI CC number -> Bergz knob. Documented in the README. */
        val CC_MAP: Map<Int, Int> = mapOf(
            1 to P.FM_DEPTH,      // mod wheel = BRIGHT
            2 to P.HARMONIC,      // CHARACTER
            3 to P.FEEDBACK,      // GRIT
            74 to P.CUTOFF,       // TONE
            71 to P.RESO,         // PEAK
            76 to P.ENV_AMT,      // SWEEP
            73 to P.ATTACK,       // FADE IN
            75 to P.DECAY,
            79 to P.SUSTAIN,      // HOLD
            72 to P.RELEASE,      // TAIL
            77 to P.LFO_RATE,     // WOBBLE SPD
            78 to P.LFO_DEPTH,    // WOBBLE
            80 to P.GATE,         // LENGTH
            91 to P.REV_MIX,      // reverb amount
            92 to P.REV_SIZE,
            94 to P.DLY_MIX,      // delay amount
            95 to P.DLY_FB,
            93 to P.PH_MIX,       // phaser amount
            90 to P.PH_RATE,
            7 to P.VOLUME,
        )
    }
}
