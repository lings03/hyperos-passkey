package io.github.rin.passkey.data

enum class SettingsFailure { CONFLICT, TIMEOUT, PERMISSION, REJECTED, READBACK, INVALID, UNAVAILABLE, UNKNOWN }

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
        // An unchanged autofill value is left alone even if this app would not write it itself.
        if (before.autofill != target.autofill) target.validateAutofill()

        try {
            for ((key, value) in target.values()) {
                if (before.value(key) != value) store.write(key, value)
            }
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
        for ((key, original) in before.values()) {
            val actual = current.value(key)
            // Do not overwrite a setting changed by another app during this operation.
            if (actual != original && actual == attempted.value(key)) {
                try {
                    store.write(key, original)
                } catch (_: Exception) {
                    // Still attempt to restore the other keys; verify all of them below.
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
