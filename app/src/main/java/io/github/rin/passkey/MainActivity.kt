package io.github.rin.passkey

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rin.passkey.shizuku.ShizukuClient
import io.github.rin.passkey.ui.PasskeyScreen
import io.github.rin.passkey.ui.PasskeyTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PasskeyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            PasskeyTheme {
                PasskeyScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onAuthorize = viewModel::requestPermission,
                    onOpenShizuku = ::openShizuku,
                    onGetShizuku = { openUrl("https://shizuku.rikka.app/download/") },
                    onSelect = viewModel::selectProvider,
                    onKeepOthersChanged = viewModel::setKeepOthers,
                    onRestore = viewModel::restore,
                    onNoticeShown = viewModel::dismissNotice,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun openShizuku() {
        val intent = packageManager.getLaunchIntentForPackage(ShizukuClient.SHIZUKU_PACKAGE)
        if (intent != null) openIntent(intent) else openUrl("https://shizuku.rikka.app/download/")
    }

    private fun openUrl(url: String) = openIntent(Intent(Intent.ACTION_VIEW, url.toUri()))

    private fun openIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (exception: Exception) {
            Log.w("PasskeyActivity", "Cannot open external page", exception)
            Toast.makeText(this, R.string.error_open_app, Toast.LENGTH_LONG).show()
        }
    }

}
