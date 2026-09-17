package io.github.rin.passkey.data

enum class SettingsFailure { CONFLICT, TIMEOUT, PERMISSION, REJECTED, READBACK, INVALID, UNKNOWN }

class SettingsAccessException(val reason: SettingsFailure) : Exception(reason.name)

interface CredentialSettingsStore {
    fun read(): CredentialSettings
    fun write(key: String, value: String?)
}

class SettingsWriteException(val restored: Boolean, cause: Exception) : Exception(
    "Settings write failed; rollback verified: $restored",
    cause,
)

class SettingsTransaction(private val store: CredentialSettingsStore) {
    fun apply(expected: CredentialSettings, target: CredentialSettings): CredentialSettings {
        target.validate()
        val before = store.read()
        if (before != expected) throw SettingsAccessException(SettingsFailure.CONFLICT)
        if (before == target) return before

        try {
            if (before.enabled != target.enabled) store.write(CredentialSettings.ENABLED_KEY, target.enabled)
            if (before.primary != target.primary) store.write(CredentialSettings.PRIMARY_KEY, target.primary)
            val actual = store.read()
            if (actual != target) throw SettingsAccessException(SettingsFailure.READBACK)
            return actual
        } catch (failure: Exception) {
            val restored = restore(before, target)
            throw SettingsWriteException(restored, failure)
        }
    }

    private fun restore(before: CredentialSettings, attempted: CredentialSettings): Boolean {
        val current = try {
            store.read()
        } catch (_: Exception) {
            return false
        }
        val changes = listOf(
            Triple(CredentialSettings.ENABLED_KEY, current.enabled, before.enabled to attempted.enabled),
            Triple(CredentialSettings.PRIMARY_KEY, current.primary, before.primary to attempted.primary),
        )
        for ((key, actual, values) in changes) {
            val (original, requested) = values
            // Do not overwrite a setting changed by another app during this operation.
            if (actual != original && actual == requested) {
                try {
                    store.write(key, original)
                } catch (_: Exception) {
                    // Still attempt to restore the other key; verify both below.
                }
            }
        }
        return try {
            store.read() == before
        } catch (_: Exception) {
            false
        }
    }
}
