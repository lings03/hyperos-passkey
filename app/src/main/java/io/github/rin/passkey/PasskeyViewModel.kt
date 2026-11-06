package io.github.rin.passkey

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.rin.passkey.data.CredentialSettings
import io.github.rin.passkey.data.DiagnosticService
import io.github.rin.passkey.data.Diagnostics
import io.github.rin.passkey.data.ProviderApp
import io.github.rin.passkey.data.ProviderRepository
import io.github.rin.passkey.data.ProviderScan
import io.github.rin.passkey.data.SettingsBackup
import io.github.rin.passkey.shizuku.ShizukuClient
import io.github.rin.passkey.shizuku.ShizukuStatus
import io.github.rin.passkey.ui.AppException
import io.github.rin.passkey.ui.AppMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AutofillStatus {
    data object Unread : AutofillStatus
    data object None : AutofillStatus

    /** No autofill app; Android Settings writes this when the preferred provider has no autofill service. */
    data object CredentialOnly : AutofillStatus
    data class App(val app: ProviderApp) : AutofillStatus
    data class Unavailable(val value: String) : AutofillStatus
}

data class PasskeyUiState(
    val scan: ProviderScan? = null,
    val status: ShizukuStatus = ShizukuStatus.STOPPED,
    val settings: CredentialSettings? = null,
    val hasBackup: Boolean = false,
    val keepOthers: Boolean = false,
    val syncAutofill: Boolean = true,
    val loading: Boolean = true,
    val applying: Boolean = false,
    val error: AppMessage? = null,
    val notice: AppMessage? = null,
    val diagnostics: Diagnostics? = null,
) {
    val apps: List<ProviderApp> get() = scan?.apps.orEmpty()
    val passkeyApps: List<ProviderApp> get() = apps.filter { it.providesPasskeys }
    val credentialApps: List<ProviderApp> get() = apps.filter { it.providesCredentials }
    val autofillApps: List<ProviderApp> get() = apps.filter { it.providesAutofill }
    val skippedProviders: Int get() = scan?.skipped ?: 0

    val settingsConsistent: Boolean
        get() = try {
            settings == null || settings.enabledComponents().containsAll(settings.selectedComponents())
        } catch (_: IllegalArgumentException) {
            false
        }

    /** The app whose credential services are exactly the preferred ones, including password-only providers. */
    val selectedProvider: ProviderApp?
        get() {
            if (!settingsConsistent) return null
            val primary = try {
                settings?.selectedComponents().orEmpty()
            } catch (_: IllegalArgumentException) {
                emptySet()
            }
            return credentialApps.firstOrNull { primary.isNotEmpty() && it.credentialComponents.toSet().containsAll(primary) }
        }

    val enabledPackages: Set<String>
        get() = try {
            settings?.enabledComponents().orEmpty().map(CredentialSettings::packageOf).toSet()
        } catch (_: IllegalArgumentException) {
            emptySet()
        }

    val primaryPackages: Set<String>
        get() = try {
            settings?.selectedComponents().orEmpty().map(CredentialSettings::packageOf).toSet()
        } catch (_: IllegalArgumentException) {
            emptySet()
        }

    val staleComponents: List<String>
        get() {
            val settings = settings ?: return emptyList()
            return scan?.staleComponents(settings).orEmpty()
        }

    val autofillStatus: AutofillStatus
        get() {
            val settings = settings ?: return AutofillStatus.Unread
            val value = settings.autofill
            if (value.isNullOrEmpty()) return AutofillStatus.None
            if (scan?.isNoAppAutofill(value) == true || value == CredentialSettings.AUTOFILL_PLACEHOLDER) return AutofillStatus.CredentialOnly
            val component = CredentialSettings.autofillComponent(value) ?: return AutofillStatus.Unavailable(value)
            return autofillApps.firstOrNull { component in it.autofillComponents }?.let { AutofillStatus.App(it) }
                ?: AutofillStatus.Unavailable(value)
        }

    val canApply: Boolean get() = status == ShizukuStatus.READY && settings != null && !loading && !applying
}

class PasskeyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProviderRepository(application)
    private val backup = SettingsBackup(application)
    private val shizuku = ShizukuClient(application)
    private val mutableState = MutableStateFlow(
        PasskeyUiState(keepOthers = backup.keepOthers, syncAutofill = backup.syncAutofill, hasBackup = backup.read() != null),
    )
    val state = mutableState.asStateFlow()
    private var refreshing = false
    private var refreshPending = false
    private var diagnosticsJob: Job? = null

    init {
        viewModelScope.launch {
            shizuku.status.collect { status ->
                mutableState.update { it.copy(status = status, settings = if (status == ShizukuStatus.READY) it.settings else null) }
                refresh()
            }
        }
    }

    fun refresh() {
        shizuku.refreshStatus()
        if (refreshing) {
            refreshPending = true
            return
        }
        if (state.value.applying) return
        refreshing = true
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null) }
            try {
                freshScan()
                val settings = if (shizuku.status.value == ShizukuStatus.READY) shizuku.read() else null
                mutableState.update { it.copy(settings = settings) }
            } catch (exception: Exception) {
                reportFailure(exception)
                mutableState.update { it.copy(settings = null) }
            } finally {
                refreshing = false
                mutableState.update { it.copy(loading = false, status = shizuku.status.value) }
                if (refreshPending) {
                    refreshPending = false
                    refresh()
                }
            }
        }
    }

    fun requestPermission() {
        try {
            shizuku.requestPermission()
        } catch (exception: Exception) {
            reportFailure(exception)
        }
    }

    fun setKeepOthers(value: Boolean) {
        backup.keepOthers = value
        mutableState.update { it.copy(keepOthers = value) }
    }

    fun setSyncAutofill(value: Boolean) {
        backup.syncAutofill = value
        mutableState.update { it.copy(syncAutofill = value) }
    }

    fun selectProvider(provider: ProviderApp) = changeSettings { current ->
        val available = freshScan().apps.firstOrNull { it.packageName == provider.packageName }
        if (available == null || !available.providesPasskeys || available.passkeyComponents != provider.passkeyComponents)
            throw AppException(R.string.error_app_changed)
        var target = current.select(available.passkeyComponents, state.value.keepOthers)
        // Like Android Settings, the preferred provider's own autofill service follows it. An app
        // without one leaves the current autofill app in place instead of turning autofill off.
        val syncAutofill = state.value.syncAutofill && available.providesAutofill
        if (syncAutofill && CredentialSettings.autofillComponent(current.autofill) !in available.autofillComponents) {
            target = target.copy(autofill = available.autofillComponents.first())
        }
        Change(target, AppMessage(if (syncAutofill) R.string.success_selected_with_autofill else R.string.success_selected, listOf(available.label)))
    }

    /** Sets the autofill app, or turns app autofill off when [provider] is null. */
    fun selectAutofill(provider: ProviderApp?) = changeSettings { current ->
        val scan = freshScan()
        if (provider == null) {
            val value = when {
                scan.isNoAppAutofill(current.autofill) && !current.autofill.isNullOrEmpty() -> current.autofill
                current.selectedComponents().isNotEmpty() -> scan.noAppAutofillValue
                else -> null
            }
            Change(current.copy(autofill = value), AppMessage(R.string.success_autofill_cleared))
        } else {
            val available = scan.apps.firstOrNull { it.packageName == provider.packageName && it.providesAutofill }
                ?: throw AppException(R.string.error_app_changed)
            val component = CredentialSettings.autofillComponent(current.autofill)
                ?.takeIf { it in available.autofillComponents } ?: available.autofillComponents.first()
            Change(current.copy(autofill = component), AppMessage(R.string.success_autofill, listOf(available.label)))
        }
    }

    fun setProviderEnabled(provider: ProviderApp, enabled: Boolean) = changeSettings { current ->
        if (!enabled) {
            if (current.selectedComponents().any { CredentialSettings.packageOf(it) == provider.packageName })
                throw AppException(R.string.error_disable_primary)
            return@changeSettings Change(current.withoutPackage(provider.packageName), AppMessage(R.string.success_disabled, listOf(provider.label)))
        }
        val available = freshScan().apps.firstOrNull { it.packageName == provider.packageName && it.providesCredentials }
            ?: throw AppException(R.string.error_app_changed)
        Change(current.withEnabled(available.credentialComponents), AppMessage(R.string.success_enabled, listOf(available.label)))
    }

    /** Removes the confirmed entries that are still unresolvable at the time of writing. */
    fun cleanUpStale(confirmed: List<String>) = changeSettings { current ->
        val stale = freshScan().staleComponents(current).filter { it in confirmed }.toSet()
        Change(current.withoutComponents(stale), AppMessage(R.string.success_cleaned, listOf(stale.size.toString())))
    }

    fun restore() = changeSettings { current ->
        val saved = backup.read() ?: throw AppException(R.string.error_no_backup)
        Change(saved.target(current), AppMessage(R.string.success_restored))
    }

    fun openDiagnostics(service: DiagnosticService) {
        diagnosticsJob?.cancel()
        mutableState.update { it.copy(diagnostics = Diagnostics(service)) }
        diagnosticsJob = viewModelScope.launch {
            val result = try {
                Diagnostics(service, result = shizuku.dump(service))
            } catch (exception: Exception) {
                if (exception is CancellationException && exception !is TimeoutCancellationException) throw exception
                Log.e(TAG, "Cannot read diagnostics", exception)
                Diagnostics(service, error = messageFor(exception))
            }
            mutableState.update { if (it.diagnostics?.service == service) it.copy(diagnostics = result) else it }
        }
    }

    fun closeDiagnostics() {
        diagnosticsJob?.cancel()
        diagnosticsJob = null
        mutableState.update { it.copy(diagnostics = null) }
    }

    private class Change(val target: CredentialSettings, val message: AppMessage)

    private suspend fun freshScan(): ProviderScan =
        withContext(Dispatchers.IO) { repository.scan() }.also { scan -> mutableState.update { it.copy(scan = scan) } }

    private fun changeSettings(change: suspend (CredentialSettings) -> Change) {
        if (!state.value.canApply) return
        mutableState.update { it.copy(applying = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                val before = shizuku.read()
                val desired = change(before)
                desired.target.validate()
                withContext(Dispatchers.IO) { backup.saveIfAbsent(before) }
                val actual = shizuku.apply(before, desired.target)
                mutableState.update { it.copy(settings = actual, notice = desired.message) }
            } catch (exception: Exception) {
                reportFailure(exception)
                try {
                    val actual = shizuku.read()
                    mutableState.update { it.copy(settings = actual) }
                } catch (readFailure: Exception) {
                    if (readFailure is CancellationException && readFailure !is TimeoutCancellationException) throw readFailure
                    Log.w(TAG, "Cannot refresh settings after failure", readFailure)
                    mutableState.update { it.copy(settings = null) }
                }
            } finally {
                shizuku.refreshStatus()
                mutableState.update { it.copy(applying = false, hasBackup = backup.read() != null) }
            }
        }
    }

    fun dismissNotice() { mutableState.update { it.copy(notice = null) } }

    private fun reportFailure(exception: Exception) {
        if (exception is CancellationException && exception !is TimeoutCancellationException) throw exception
        Log.e(TAG, "Passkey operation failed", exception)
        mutableState.update { it.copy(error = messageFor(exception)) }
    }

    private fun messageFor(exception: Exception) = AppMessage(
        when (exception) {
            is TimeoutCancellationException -> R.string.error_timeout
            is AppException -> exception.resource
            is IllegalArgumentException -> R.string.error_invalid
            else -> R.string.error_operation
        },
    )

    override fun onCleared() {
        shizuku.close()
    }

    private companion object { const val TAG = "Passkey" }
}
