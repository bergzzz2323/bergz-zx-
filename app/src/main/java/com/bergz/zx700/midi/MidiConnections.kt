package com.bergz.zx700.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import com.bergz.zx700.audio.NativeEngine

/**
 * Listens to every MIDI source the phone can see and feeds it to the synth:
 *  - USB-C keyboards, controllers and sequencers plugged into the Pixel
 *  - another phone or a computer on a USB-C cable with this phone in USB "MIDI" mode
 *    (shows up as the Android USB peripheral port)
 *  - other apps on this phone that publish a MIDI output (e.g. Nightstepper)
 * Devices are connected automatically when plugged in and released when removed.
 * Devices that accept MIDI also get Bergz's clock, start and stop when CLOCK OUT is on.
 */
class MidiConnections(context: Context) {
    private val manager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager?
    private val handler = Handler(Looper.getMainLooper())
    private val open = mutableMapOf<Int, Pair<MidiDevice, List<MidiOutputPort>>>()
    private val inputs = mutableMapOf<Int, List<MidiInputPort>>()
    private val inputLabels = mutableMapOf<Int, String>()

    /** Names of devices that receive Bergz's clock. */
    val clockTargets = mutableStateListOf<String>()
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
        startClockOut()
    }

    fun stop() {
        clockRunning = false
        manager?.unregisterDeviceCallback(callback)
        open.keys.toList().forEach { disconnect(it) }
    }

    // ---------------------------------------------------------------- clock out
    // The engine stamps every clock / start / stop with the moment its audio is
    // heard. A small thread collects them every few ms and hands them to Android
    // MIDI with that timestamp, so the receiving gear gets them on time rather
    // than whenever this thread happened to run.
    @Volatile private var clockRunning = false

    private fun startClockOut() {
        if (clockRunning) return
        clockRunning = true
        Thread({
            val buf = LongArray(512)
            val msg = ByteArray(1)
            while (clockRunning) {
                val n = NativeEngine.nativePollClockOut(buf)
                if (n > 0) {
                    val ports = synchronized(inputs) { inputs.values.flatten() }
                    for (k in 0 until n) {
                        msg[0] = buf[2 * k].toByte()
                        for (port in ports) runCatching { port.send(msg, 0, 1, buf[2 * k + 1]) }
                    }
                }
                try { Thread.sleep(4) } catch (_: InterruptedException) { }
            }
        }, "bergz-clock-out").apply { priority = Thread.MAX_PRIORITY; isDaemon = true; start() }
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
        if ((info.outputPortCount == 0 && info.inputPortCount == 0) || isSelf(info) || open.containsKey(info.id)) return
        m.openDevice(info, { device ->
            if (device == null) return@openDevice
            val ports = (0 until info.outputPortCount).mapNotNull { i ->
                device.openOutputPort(i)?.also { it.connect(BergzMidiReceiver()) }
            }
            open[info.id] = device to ports
            val l = label(info)
            if (ports.isNotEmpty()) { labels[info.id] = l; names.add(l) }
            val ins = (0 until info.inputPortCount).mapNotNull { i -> device.openInputPort(i) }
            if (ins.isNotEmpty()) {
                synchronized(inputs) { inputs[info.id] = ins }
                inputLabels[info.id] = l
                clockTargets.add(l)
            }
        }, handler)
    }

    private fun disconnect(id: Int) {
        synchronized(inputs) { inputs.remove(id) }?.forEach { runCatching { it.close() } }
        inputLabels.remove(id)?.let { clockTargets.remove(it) }
        open.remove(id)?.let { (device, ports) ->
            ports.forEach { runCatching { it.close() } }
            runCatching { device.close() }
        }
        labels.remove(id)?.let { names.remove(it) }
    }
}
