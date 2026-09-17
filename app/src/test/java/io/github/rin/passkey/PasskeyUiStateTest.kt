package io.github.rin.passkey

import io.github.rin.passkey.data.CredentialSettings
import io.github.rin.passkey.data.PasskeyProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyUiStateTest {
    private val provider = PasskeyProvider(
        packageName = "com.vault",
        label = "Vault",
        components = listOf("com.vault/com.vault.Provider"),
        icon = null,
        isSystem = false,
    )

    @Test
    fun currentAppIsResolvedFromActualSystemValuesIncludingShortNames() {
        val state = PasskeyUiState(
            providers = listOf(provider),
            settings = CredentialSettings("com.vault/.Provider", "com.vault/.Provider"),
        )
        assertEquals(provider, state.selectedProvider)
        assertTrue(state.settingsConsistent)
    }

    @Test
    fun disabledPrimaryIsNotDisplayedAsAnActiveProvider() {
        val state = PasskeyUiState(
            providers = listOf(provider),
            settings = CredentialSettings("com.system/.Provider", "com.vault/.Provider"),
        )
        assertFalse(state.settingsConsistent)
        assertNull(state.selectedProvider)
    }

    @Test
    fun multiplePrimaryAppsDoNotMisrepresentASingleAppAsCurrent() {
        val services = "com.vault/.Provider:com.other/.Provider"
        val state = PasskeyUiState(providers = listOf(provider), settings = CredentialSettings(services, services))
        assertTrue(state.settingsConsistent)
        assertNull(state.selectedProvider)
    }

    @Test
    fun unknownOrUnreadSettingsDoNotInventADefault() {
        assertNull(PasskeyUiState(providers = listOf(provider)).selectedProvider)
        val unknown = CredentialSettings("com.missing/.Provider", "com.missing/.Provider")
        assertNull(PasskeyUiState(providers = listOf(provider), settings = unknown).selectedProvider)
    }
}
