package com.bergz.zx700

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bergz.zx700.audio.NativeEngine
import com.bergz.zx700.ui.BergzState
import com.bergz.zx700.ui.PanelScreen

class MainActivity : ComponentActivity() {
    private val state = BergzState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen landscape instrument: no status or navigation bars, screen stays on.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        state.audioOk = NativeEngine.nativeStart()
        state.loadPreset(0)

        setContent { PanelScreen(state) }
    }

    override fun onDestroy() {
        NativeEngine.nativeStop()
        super.onDestroy()
    }
}
