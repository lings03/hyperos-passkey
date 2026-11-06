package io.github.rin.passkey

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
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
import io.github.rin.passkey.ui.PasskeyActions
import io.github.rin.passkey.ui.PasskeyScreen
import io.github.rin.passkey.ui.PasskeyTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PasskeyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val actions = PasskeyActions(
            refresh = viewModel::refresh,
            authorize = viewModel::requestPermission,
            openShizuku = { openApp(ShizukuClient.SHIZUKU_PACKAGE, SHIZUKU_DOWNLOAD) },
            getShizuku = { openUrl(SHIZUKU_DOWNLOAD) },
            selectProvider = viewModel::selectProvider,
            selectAutofill = viewModel::selectAutofill,
            setProviderEnabled = viewModel::setProviderEnabled,
            cleanUpStale = viewModel::cleanUpStale,
            setKeepOthers = viewModel::setKeepOthers,
            setSyncAutofill = viewModel::setSyncAutofill,
            restore = viewModel::restore,
            noticeShown = viewModel::dismissNotice,
            openDiagnostics = viewModel::openDiagnostics,
            closeDiagnostics = viewModel::closeDiagnostics,
            copyText = ::copyText,
            openSystemSettings = ::openSystemSettings,
            openPasswordManager = { openApp(PASSWORD_MANAGER_PACKAGE, playStore(PASSWORD_MANAGER_PACKAGE)) },
            openFidoBridge = { openApp(FIDO_BRIDGE_PACKAGE, playStore(FIDO_BRIDGE_PACKAGE)) },
        )
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            PasskeyTheme {
                PasskeyScreen(state = state, actions = actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    /** Opens the installed app, or its download page when it is missing. */
    private fun openApp(packageName: String, fallbackUrl: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) openIntent(intent) else openUrl(fallbackUrl)
    }

    /** Opens "Passwords, passkeys & accounts", which AOSP Settings registers for ACTION_SYNC_SETTINGS. */
    private fun openSystemSettings() {
        val intents = listOf(Intent(Settings.ACTION_SYNC_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        val intent = intents.firstOrNull { it.resolveActivity(packageManager) != null } ?: intents.last()
        openIntent(intent)
    }

    private fun copyText(text: String) {
        val clip = ClipData.newPlainText(getString(R.string.app_name), text)
        // Diagnostics can include app and account details; keep them out of clipboard previews.
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
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

    private companion object {
        const val SHIZUKU_DOWNLOAD = "https://shizuku.rikka.app/download/"
        const val PASSWORD_MANAGER_PACKAGE = "com.google.android.apps.credentialmanager"
        const val FIDO_BRIDGE_PACKAGE = "com.token2.companion"

        fun playStore(packageName: String) = "https://play.google.com/store/apps/details?id=$packageName"
    }
}
