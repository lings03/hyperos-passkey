package io.github.rin.passkey.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTransactionTest {
    private val original = CredentialSettings("com.system/com.system.Provider", "com.system/.Provider")
    private val target = CredentialSettings("com.vault/com.vault.Provider", "com.vault/com.vault.Provider")

    @Test
    fun exclusiveSelectionReplacesBothKeys() {
        val selected = original.select(listOf("com.vault/.Provider"), keepOthers = false)
        assertEquals(target, selected)
        val store = FakeStore(original)
        assertEquals(target, SettingsTransaction(store).apply(original, selected))
        assertEquals(2, store.writes)
    }

    @Test
    fun retainingProvidersNormalizesAndDeduplicatesComponents() {
        val current = CredentialSettings("com.vault/.Provider:com.system/.Provider", null)
        val selected = current.select(listOf("com.vault/com.vault.Provider"), keepOthers = true)
        assertEquals("com.vault/com.vault.Provider:com.system/com.system.Provider", selected.enabled)
        assertEquals("com.vault/com.vault.Provider", selected.primary)
    }

    @Test
    fun allPasskeyServicesInSelectedPackageBecomePrimary() {
        val selected = original.select(listOf("com.vault/.First", "com.vault/.Second"), false)
        assertEquals("com.vault/com.vault.First:com.vault/com.vault.Second", selected.primary)
        assertEquals(selected.enabled, selected.primary)
    }

    @Test
    fun detectsConcurrentChangeBeforeWriting() {
        val store = FakeStore(target)
        val failure = assertThrows(SettingsAccessException::class.java) {
            SettingsTransaction(store).apply(original, target)
        }
        assertEquals(SettingsFailure.CONFLICT, failure.reason)
        assertEquals(0, store.writes)
    }

    @Test
    fun restoresBothKeysWhenSecondWriteFailsAfterChangingTheValue() {
        val store = FakeStore(original, failAfterWrites = setOf(2))
        val failure = assertThrows(SettingsWriteException::class.java) {
            SettingsTransaction(store).apply(original, target)
        }
        assertTrue(failure.restored)
        assertEquals(original, store.state)
    }

    @Test
    fun doesNotReportSuccessWhenSystemSilentlyIgnoresAWrite() {
        val store = FakeStore(original, ignoreWrites = setOf(2))
        val failure = assertThrows(SettingsWriteException::class.java) {
            SettingsTransaction(store).apply(original, target)
        }
        assertTrue(failure.restored)
        assertEquals(SettingsFailure.READBACK, (failure.cause as SettingsAccessException).reason)
        assertEquals(original, store.state)
    }

    @Test
    fun reportsWhenRollbackCannotRestoreTheOriginalValue() {
        val store = FakeStore(original, failAfterWrites = setOf(2), ignoreWrites = setOf(3))
        val failure = assertThrows(SettingsWriteException::class.java) {
            SettingsTransaction(store).apply(original, target)
        }
        assertFalse(failure.restored)
        assertEquals(original.primary, store.state.primary)
        assertEquals(target.enabled, store.state.enabled)
    }

    @Test
    fun preservesOtherAppsChangesDuringRollback() {
        val foreignValue = "com.other/com.other.Provider"
        val store = FakeStore(original, onWrite = { fake, number ->
            if (number == 2) {
                fake.state = fake.state.copy(enabled = foreignValue)
                throw SettingsAccessException(SettingsFailure.REJECTED)
            }
        })
        val failure = assertThrows(SettingsWriteException::class.java) {
            SettingsTransaction(store).apply(original, target)
        }
        assertFalse(failure.restored)
        assertEquals(foreignValue, store.state.enabled)
        assertEquals(original.primary, store.state.primary)
    }

    @Test
    fun restoresMissingAndEmptyValuesWithoutTreatingThemAsEqual() {
        val backup = CredentialSettings(null, "")
        val store = FakeStore(target)
        SettingsTransaction(store).apply(target, backup)
        assertNull(store.state.enabled)
        assertEquals("", store.state.primary)
    }

    @Test
    fun rejectsUnexpectedComponentSyntaxBeforeAnyWrite() {
        listOf("com.app/.Provider;reboot", "com.app/.Provider\n", "com.app/Provider:bad", "com.app/.Provider:", "$(reboot)").forEach { bad ->
            val store = FakeStore(original)
            assertThrows(IllegalArgumentException::class.java) {
                SettingsTransaction(store).apply(original, CredentialSettings(bad, bad))
            }
            assertEquals(0, store.writes)
        }
    }

    @Test
    fun noOpStillChecksCurrentSystemState() {
        val store = FakeStore(original)
        assertEquals(original, SettingsTransaction(store).apply(original, original))
        assertEquals(0, store.writes)
    }

    private class FakeStore(
        var state: CredentialSettings,
        private val failAfterWrites: Set<Int> = emptySet(),
        private val ignoreWrites: Set<Int> = emptySet(),
        private val onWrite: (FakeStore, Int) -> Unit = { _, _ -> },
    ) : CredentialSettingsStore {
        var writes = 0
        override fun read() = state
        override fun write(key: String, value: String?) {
            writes++
            if (writes !in ignoreWrites) state = when (key) {
                CredentialSettings.ENABLED_KEY -> state.copy(enabled = value)
                CredentialSettings.PRIMARY_KEY -> state.copy(primary = value)
                else -> error("Unexpected key")
            }
            onWrite(this, writes)
            if (writes in failAfterWrites) throw SettingsAccessException(SettingsFailure.REJECTED)
        }
    }
}
