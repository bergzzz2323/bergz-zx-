package com.bergz.zx700.midi

import android.media.midi.MidiDeviceService
import android.media.midi.MidiReceiver
import com.bergz.zx700.audio.NativeEngine

/**
 * Publishes "Bergz ZX 700" as a MIDI device on the phone, so other apps
 * (e.g. Nightstepper) can pick it as an output and play/sequence it.
 * Described in res/xml/midi_device_info.xml.
 */
class BergzMidiService : MidiDeviceService() {
    private val receiver = BergzMidiReceiver()

    override fun onCreate() {
        super.onCreate()
        NativeEngine.ensureStarted()  // make sure there is sound even if the screen isn't open yet
    }

    override fun onGetInputPortReceivers(): Array<MidiReceiver> = arrayOf(receiver)
}
