package io.github.rin.passkey

import io.github.rin.passkey.data.CredentialSettings
import io.github.rin.passkey.data.ProviderApp
import io.github.rin.passkey.data.ProviderScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyUiStateTest {
    private val provider = app("com.vault", passkeys = true, autofill = true)
    private val passwordsOnly = app("com.keys", passkeys = false, autofill = false)
    private val autofillOnly = ProviderApp("com.fill", "Fill", null, false, emptyList(), emptyList(), listOf("com.fill/com.fill.Autofill"))

    private fun app(packageName: String, passkeys: Boolean, autofill: Boolean): ProviderApp {
        val credential = listOf("$packageName/$packageName.Provider")
        return ProviderApp(
            packageName = packageName,
            label = packageName.substringAfter('.'),
            icon = null,
            isSystem = false,
            credentialComponents = credential,
            passkeyComponents = if (passkeys) credential else emptyList(),
            autofillComponents = if (autofill) listOf("$packageName/$packageName.Autofill") else emptyList(),
        )
    }

    private fun scan(apps: List<ProviderApp>) = ProviderScan(
        apps = apps,
        skipped = 0,
        resolvableCredentialServices = apps.flatMap { it.credentialComponents }.toSet(),
        credentialAutofillService = CREDMAN,
        noAppAutofillValue = CREDMAN,
    )

    private fun state(settings: CredentialSettings?, apps: List<ProviderApp> = listOf(provider)) =
        PasskeyUiState(scan = scan(apps), settings = settings)

    @Test
    fun currentAppIsResolvedFromActualSystemValuesIncludingShortNames() {
        val state = state(CredentialSettings("com.vault/.Provider", "com.vault/.Provider", null))
        assertEquals(provider, state.selectedProvider)
        assertTrue(state.settingsConsistent)
    }

    @Test
    fun disabledPrimaryIsNotDisplayedAsAnActiveProvider() {
        val state = state(CredentialSettings("com.system/.Provider", "com.vault/.Provider", null))
        assertFalse(state.settingsConsistent)
        assertNull(state.selectedProvider)
    }

    @Test
    fun multiplePrimaryAppsDoNotMisrepresentASingleAppAsCurrent() {
        val services = "com.vault/.Provider:com.other/.Provider"
        val state = state(CredentialSettings(services, services, null))
        assertTrue(state.settingsConsistent)
        assertNull(state.selectedProvider)
    }

    @Test
    fun unknownOrUnreadSettingsDoNotInventADefault() {
        assertNull(state(null).selectedProvider)
        val unknown = CredentialSettings("com.missing/.Provider", "com.missing/.Provider", null)
        assertNull(state(unknown).selectedProvider)
    }

    @Test
    fun passwordOnlyPrimaryIsShownByNameButNotOfferedAsAPasskeyApp() {
        val state = state(CredentialSettings("com.keys/.Provider", "com.keys/.Provider", null), listOf(provider, passwordsOnly))
        assertEquals(passwordsOnly, state.selectedProvider)
        assertEquals(listOf(provider), state.passkeyApps)
        assertEquals(listOf(provider, passwordsOnly), state.credentialApps)
    }

    @Test
    fun autofillStatusDistinguishesAppsPlaceholdersAndUnknownValues() {
        fun status(value: String?) = state(CredentialSettings(null, null, value), listOf(provider, autofillOnly)).autofillStatus
        assertEquals(AutofillStatus.Unread, state(null).autofillStatus)
        assertEquals(AutofillStatus.None, status(null))
        assertEquals(AutofillStatus.None, status(""))
        assertEquals(AutofillStatus.CredentialOnly, status(CredentialSettings.AUTOFILL_PLACEHOLDER))
        assertEquals(AutofillStatus.CredentialOnly, status(CREDMAN))
        assertEquals(AutofillStatus.App(autofillOnly), status("com.fill/.Autofill"))
        assertEquals(AutofillStatus.Unavailable("com.gone/.Autofill"), status("com.gone/.Autofill"))
    }

    @Test
    fun staleEntriesAreReportedOnlyAfterAScan() {
        val settings = CredentialSettings("com.vault/.Provider:com.gone/.Provider", "com.vault/.Provider", null)
        assertEquals(listOf("com.gone/com.gone.Provider"), state(settings).staleComponents)
        assertEquals(emptyList<String>(), PasskeyUiState(settings = settings).staleComponents)
    }

    @Test
    fun enabledAndPrimaryPackagesComeFromSystemValues() {
        val state = state(CredentialSettings("com.vault/.Provider:com.keys/.Provider", "com.vault/.Provider", null), listOf(provider, passwordsOnly))
        assertEquals(setOf("com.vault", "com.keys"), state.enabledPackages)
        assertEquals(setOf("com.vault"), state.primaryPackages)
    }

    private companion object {
        const val CREDMAN = "com.android.credentialmanager/com.android.credentialmanager.autofill.CredentialAutofillService"
    }
}
