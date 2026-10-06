package io.github.zbowling.lightdeck

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import io.github.zbowling.lightdeck.ui.LightDeckApp

class MainActivity : ComponentActivity() {
    private val viewModel: LightsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) applyDebugServerExtras(intent)
        setContent { LightDeckApp(viewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyDebugServerExtras(intent)
    }

    /**
     * Typing a long token on a headset keyboard is tedious, so debug builds accept it at launch:
     * metavr adb shell am start -n io.github.zbowling.lightdeck/.MainActivity \
     *   --es ha_url http://homeassistant.local:8123 --es ha_token <token>
     */
    private fun applyDebugServerExtras(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        val url = intent.getStringExtra("ha_url") ?: return
        val token = intent.getStringExtra("ha_token") ?: return
        viewModel.connect(url, token)
    }
}
