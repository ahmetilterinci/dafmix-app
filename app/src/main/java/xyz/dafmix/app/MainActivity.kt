package xyz.dafmix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import xyz.dafmix.app.ui.AppNav
import xyz.dafmix.app.ui.Deep
import xyz.dafmix.app.ui.DafmixTheme

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /** https://dafmix.xyz/oda/KOD davet bağlantısı */
    private fun handleLink(i: android.content.Intent?) {
        val seg = i?.data?.pathSegments ?: return
        if (seg.size >= 2 && seg[0] == "oda") Deep.room.value = seg[1].lowercase()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLink(intent)
        setContent {
            DafmixTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AppNav() }
            }
        }
    }
}
