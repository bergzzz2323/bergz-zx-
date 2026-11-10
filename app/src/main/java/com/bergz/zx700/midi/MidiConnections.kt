package com.bergz.zx700.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf

/**
 * Listens to every MIDI source the phone can see and feeds it to the synth:
 *  - USB-C keyboards, controllers and sequencers plugged into the Pixel
 *  - another phone or a computer on a USB-C cable with this phone in USB "MIDI" mode
 *    (shows up as the Android USB peripheral port)
 *  - other apps on this phone that publish a MIDI output (e.g. Nightstepper)
 * Devices are connected automatically when plugged in and released when removed.
 */
class MidiConnections(context: Context) {
    private val manager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager?
    private val handler = Handler(Looper.getMainLooper())
    private val open = mutableMapOf<Int, Pair<MidiDevice, List<MidiOutputPort>>>()
    private val labels = mutableMapOf<Int, String>()

    /** Names of connected sources, for the panel's header. */
    val names = mutableStateListOf<String>()

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) = connect(info)
        override fun onDeviceRemoved(info: MidiDeviceInfo) = disconnect(info.id)
    }

    val available: Boolean get() = manager != null

    @Suppress("DEPRECATION")
    fun start() {
        val m = manager ?: return
        m.registerDeviceCallback(callback, handler)
        m.devices.forEach { connect(it) }
    }

    fun stop() {
        manager?.unregisterDeviceCallback(callback)
        open.keys.toList().forEach { disconnect(it) }
    }

    private fun isSelf(info: MidiDeviceInfo): Boolean =
        info.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) == "Bergz"

    private fun label(info: MidiDeviceInfo): String {
        val p = info.properties
        val name = p.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: p.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
            ?: "MIDI ${info.id}"
        return when {
            name.contains("Peripheral", ignoreCase = true) -> "USB-C CABLE"
            info.type == MidiDeviceInfo.TYPE_USB -> "USB · $name"
            info.type == MidiDeviceInfo.TYPE_BLUETOOTH -> "BT · $name"
            else -> name
        }.uppercase()
    }

    private fun connect(info: MidiDeviceInfo) {
        val m = manager ?: return
        if (info.outputPortCount == 0 || isSelf(info) || open.containsKey(info.id)) return
        m.openDevice(info, { device ->
            if (device == null) return@openDevice
            val ports = (0 until info.outputPortCount).mapNotNull { i ->
                device.openOutputPort(i)?.also { it.connect(BergzMidiReceiver()) }
            }
            open[info.id] = device to ports
            val l = label(info)
            labels[info.id] = l
            names.add(l)
        }, handler)
    }

    private fun disconnect(id: Int) {
        open.remove(id)?.let { (device, ports) ->
            ports.forEach { runCatching { it.close() } }
            runCatching { device.close() }
        }
        labels.remove(id)?.let { names.remove(it) }
    }
}
