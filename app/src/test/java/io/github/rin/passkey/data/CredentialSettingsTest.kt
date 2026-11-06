package io.github.rin.passkey.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CredentialSettingsTest {
    private val settings = CredentialSettings(
        enabled = "com.vault/.Provider:com.system/.Provider:com.gone/.Provider",
        primary = "com.vault/.Provider",
        autofill = "com.vault/.Autofill",
    )

    @Test
    fun selectingAProviderKeepsTheAutofillValue() {
        val selected = settings.select(listOf("com.system/.Provider"), keepOthers = false)
        assertEquals("com.system/com.system.Provider", selected.primary)
        assertEquals(settings.autofill, selected.autofill)
    }

    @Test
    fun enablingAddsMissingServicesOnly() {
        val enabled = settings.withEnabled(listOf("com.extra/.Provider", "com.vault/com.vault.Provider"))
        assertEquals(
            "com.vault/com.vault.Provider:com.system/com.system.Provider:com.gone/com.gone.Provider:com.extra/com.extra.Provider",
            enabled.enabled,
        )
        assertEquals(settings.primary, enabled.primary)
        assertSame(settings, settings.withEnabled(listOf("com.system/com.system.Provider")))
    }

    @Test
    fun disablingRemovesEveryServiceOfThePackage() {
        val current = settings.copy(enabled = settings.enabled + ":com.system/.Second")
        val disabled = current.withoutPackage("com.system")
        assertEquals("com.vault/com.vault.Provider:com.gone/com.gone.Provider", disabled.enabled)
        assertSame(settings, settings.withoutPackage("com.absent"))
    }

    @Test
    fun preferredProviderCannotBeDisabled() {
        assertThrows(IllegalArgumentException::class.java) { settings.withoutPackage("com.vault") }
    }

    @Test
    fun removingStaleServicesPrunesBothListsAndLeavesOthersUntouched() {
        val current = settings.copy(primary = "com.vault/.Provider:com.gone/.Provider")
        val pruned = current.withoutComponents(setOf("com.gone/com.gone.Provider"))
        assertEquals("com.vault/com.vault.Provider:com.system/com.system.Provider", pruned.enabled)
        assertEquals("com.vault/com.vault.Provider", pruned.primary)
        assertEquals(settings.autofill, pruned.autofill)
        // Nothing to remove keeps the exact stored strings, including short component names.
        assertSame(settings.enabled, settings.withoutComponents(setOf("com.other/.Provider")).enabled)
    }

    @Test
    fun autofillComponentIgnoresPlaceholderAndEmptyValues() {
        assertEquals("com.vault/com.vault.Autofill", CredentialSettings.autofillComponent("com.vault/.Autofill"))
        assertNull(CredentialSettings.autofillComponent(CredentialSettings.AUTOFILL_PLACEHOLDER))
        assertNull(CredentialSettings.autofillComponent(""))
        assertNull(CredentialSettings.autofillComponent(null))
    }

    @Test
    fun backupFromBeforeAutofillSupportKeepsTheCurrentAutofillValue() {
        val current = CredentialSettings("a.b/.C", "a.b/.C", "com.now/.Autofill")
        val old = SavedSettings("x.y/.Z", "x.y/.Z", autofill = null, hasAutofill = false)
        assertEquals(CredentialSettings("x.y/.Z", "x.y/.Z", "com.now/.Autofill"), old.target(current))
        val recorded = old.copy(hasAutofill = true)
        assertEquals(CredentialSettings("x.y/.Z", "x.y/.Z", null), recorded.target(current))
    }

    @Test
    fun staleComponentsAreThoseTheSystemCannotResolve() {
        val scan = ProviderScan(
            apps = emptyList(),
            skipped = 0,
            resolvableCredentialServices = setOf("com.vault/com.vault.Provider", "com.system/com.system.Provider"),
            credentialAutofillService = null,
            noAppAutofillValue = CredentialSettings.AUTOFILL_PLACEHOLDER,
        )
        assertEquals(listOf("com.gone/com.gone.Provider"), scan.staleComponents(settings))
        val stalePrimary = settings.copy(primary = "com.old/.Provider")
        assertEquals(listOf("com.gone/com.gone.Provider", "com.old/com.old.Provider"), scan.staleComponents(stalePrimary))
    }

    @Test
    fun noAppAutofillValuesIncludeTheSystemCredentialAutofillService() {
        val credman = "com.android.credentialmanager/com.android.credentialmanager.autofill.CredentialAutofillService"
        val scan = ProviderScan(emptyList(), 0, emptySet(), credman, credman)
        listOf(null, "", CredentialSettings.AUTOFILL_PLACEHOLDER, credman, "com.android.credentialmanager/.autofill.CredentialAutofillService")
            .forEach { assertEquals(it, true, scan.isNoAppAutofill(it)) }
        assertEquals(false, scan.isNoAppAutofill("com.vault/.Autofill"))
    }
}
