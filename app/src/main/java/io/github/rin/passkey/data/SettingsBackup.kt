package io.github.rin.passkey.data

import android.content.Context
import androidx.core.content.edit
import io.github.rin.passkey.R
import io.github.rin.passkey.ui.AppException

/**
 * Settings saved before this app first changed them. Backups made by version 1.0 did not record
 * the autofill service, so [hasAutofill] is false for them until the next change fills it in.
 */
data class SavedSettings(val enabled: String?, val primary: String?, val autofill: String?, val hasAutofill: Boolean) {
    fun target(current: CredentialSettings) = CredentialSettings(enabled, primary, if (hasAutofill) autofill else current.autofill)
}

class SettingsBackup(context: Context) {
    private val preferences = context.getSharedPreferences("credential_settings", Context.MODE_PRIVATE)

    var keepOthers: Boolean
        get() = preferences.getBoolean("keep_others", false)
        set(value) { preferences.edit { putBoolean("keep_others", value) } }

    var syncAutofill: Boolean
        get() = preferences.getBoolean("sync_autofill", true)
        set(value) { preferences.edit { putBoolean("sync_autofill", value) } }

    fun read(): SavedSettings? {
        if (!preferences.getBoolean("has_backup", false)) return null
        return SavedSettings(
            enabled = preferences.getString("enabled", null),
            primary = preferences.getString("primary", null),
            autofill = preferences.getString("autofill", null),
            hasAutofill = preferences.getBoolean("has_autofill", false),
        )
    }

    /** Saves the original settings once, and records the autofill value into an older backup that lacks it. */
    fun saveIfAbsent(settings: CredentialSettings) {
        val saved = read()
        val editor = when {
            saved == null -> preferences.edit()
                .putBoolean("has_backup", true)
                .putString("enabled", settings.enabled)
                .putString("primary", settings.primary)
                .putBoolean("has_autofill", true)
                .putString("autofill", settings.autofill)
            !saved.hasAutofill -> preferences.edit()
                .putBoolean("has_autofill", true)
                .putString("autofill", settings.autofill)
            else -> return
        }
        if (!editor.commit()) throw AppException(R.string.error_backup)
    }
}
