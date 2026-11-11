package com.bergz.zx700

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bergz.zx700.audio.NativeEngine
import com.bergz.zx700.midi.MidiConnections
import com.bergz.zx700.ui.BergzState
import com.bergz.zx700.ui.PanelScreen

class MainActivity : ComponentActivity() {
    private val state = BergzState()
    private lateinit var midi: MidiConnections
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var audioCallbackReady = false

    /** When USB-C audio is plugged in or out, reopen the stream so the sound follows it. */
    private val audioDevices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = outputsChanged(added)
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = outputsChanged(removed)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen landscape instrument: no status or navigation bars, screen stays on.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        state.audioOk = NativeEngine.ensureStarted()
        state.startFresh()

        midi = MidiConnections(this)
        midi.start()
        state.midiSources = midi.names

        audioManager.registerAudioDeviceCallback(audioDevices, Handler(Looper.getMainLooper()))
        updateOutputName()

        setContent { PanelScreen(state) }
    }

    private fun isUsb(type: Int) = type == AudioDeviceInfo.TYPE_USB_DEVICE ||
        type == AudioDeviceInfo.TYPE_USB_HEADSET || type == AudioDeviceInfo.TYPE_USB_ACCESSORY

    private fun outputsChanged(devices: Array<out AudioDeviceInfo>) {
        // The first callback just lists what is already connected.
        if (audioCallbackReady && devices.any { it.isSink && isUsb(it.type) }) {
            NativeEngine.restartAudio()
            state.audioOk = NativeEngine.audioOk
        }
        audioCallbackReady = true
        updateOutputName()
    }

    private fun updateOutputName() {
        val outs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        state.outputName = when {
            outs.any { isUsb(it.type) } -> "USB-C AUDIO"
            outs.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET } -> "HEADPHONES"
            outs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } -> "BLUETOOTH"
            else -> "PHONE"
        }
    }

    override fun onDestroy() {
        audioManager.unregisterAudioDeviceCallback(audioDevices)
        midi.stop()
        super.onDestroy()
    }
}
