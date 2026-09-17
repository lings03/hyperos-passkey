package io.github.rin.passkey.data

import android.content.Context
import androidx.core.content.edit
import io.github.rin.passkey.R
import io.github.rin.passkey.ui.AppException

class SettingsBackup(context: Context) {
    private val preferences = context.getSharedPreferences("credential_settings", Context.MODE_PRIVATE)

    var keepOthers: Boolean
        get() = preferences.getBoolean("keep_others", false)
        set(value) { preferences.edit { putBoolean("keep_others", value) } }

    fun read(): CredentialSettings? {
        if (!preferences.getBoolean("has_backup", false)) return null
        return CredentialSettings(preferences.getString("enabled", null), preferences.getString("primary", null))
    }

    fun saveIfAbsent(settings: CredentialSettings) {
        if (read() != null) return
        val saved = preferences.edit()
                .putBoolean("has_backup", true)
                .putString("enabled", settings.enabled)
                .putString("primary", settings.primary)
                .commit()
        if (!saved) throw AppException(R.string.error_backup)
    }
}
