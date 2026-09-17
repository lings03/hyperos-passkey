package io.github.rin.passkey

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.rin.passkey.data.CredentialSettings
import io.github.rin.passkey.data.PasskeyProvider
import io.github.rin.passkey.data.ProviderRepository
import io.github.rin.passkey.data.SettingsBackup
import io.github.rin.passkey.shizuku.ShizukuClient
import io.github.rin.passkey.shizuku.ShizukuStatus
import io.github.rin.passkey.ui.AppException
import io.github.rin.passkey.ui.AppMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PasskeyUiState(
    val providers: List<PasskeyProvider> = emptyList(),
    val status: ShizukuStatus = ShizukuStatus.STOPPED,
    val settings: CredentialSettings? = null,
    val hasBackup: Boolean = false,
    val keepOthers: Boolean = false,
    val loading: Boolean = true,
    val applying: Boolean = false,
    val error: AppMessage? = null,
    val notice: AppMessage? = null,
    val skippedProviders: Int = 0,
) {
    val settingsConsistent: Boolean
        get() = try {
            settings == null || CredentialSettings.components(settings.enabled).containsAll(settings.selectedComponents())
        } catch (_: IllegalArgumentException) {
            false
        }

    val selectedProvider: PasskeyProvider?
        get() {
            if (!settingsConsistent) return null
            val primary = try {
                settings?.selectedComponents().orEmpty()
            } catch (_: IllegalArgumentException) {
                emptySet()
            }
            return providers.firstOrNull { primary.isNotEmpty() && it.components.toSet().containsAll(primary) }
        }
    val canApply: Boolean get() = status == ShizukuStatus.READY && settings != null && !loading && !applying
}

class PasskeyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProviderRepository(application)
    private val backup = SettingsBackup(application)
    private val shizuku = ShizukuClient(application)
    private val mutableState = MutableStateFlow(PasskeyUiState(keepOthers = backup.keepOthers, hasBackup = backup.read() != null))
    val state = mutableState.asStateFlow()
    private var refreshing = false
    private var refreshPending = false

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
                val scan = withContext(Dispatchers.IO) { repository.scan() }
                mutableState.update { it.copy(providers = scan.providers, skippedProviders = scan.skipped) }
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

    fun selectProvider(provider: PasskeyProvider) = changeSettings(AppMessage(R.string.success_selected, listOf(provider.label))) { current ->
        val available = withContext(Dispatchers.IO) { repository.scan().providers }
            .firstOrNull { it.packageName == provider.packageName }
        if (available == null || available.components != provider.components) throw AppException(R.string.error_app_changed)
        current.select(available.components, state.value.keepOthers)
    }

    fun restore() = changeSettings(AppMessage(R.string.success_restored)) {
        backup.read() ?: throw AppException(R.string.error_no_backup)
    }

    private fun changeSettings(success: AppMessage, target: suspend (CredentialSettings) -> CredentialSettings) {
        if (!state.value.canApply) return
        mutableState.update { it.copy(applying = true, error = null, notice = null) }
        viewModelScope.launch {
            try {
                val before = shizuku.read()
                val desired = target(before)
                desired.validate()
                withContext(Dispatchers.IO) { backup.saveIfAbsent(before) }
                val actual = shizuku.apply(before, desired)
                mutableState.update { it.copy(settings = actual, notice = success) }
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
        val resource = when (exception) {
            is TimeoutCancellationException -> R.string.error_timeout
            is AppException -> exception.resource
            is IllegalArgumentException -> R.string.error_invalid
            else -> R.string.error_operation
        }
        mutableState.update { it.copy(error = AppMessage(resource)) }
    }

    override fun onCleared() {
        shizuku.close()
    }

    private companion object { const val TAG = "Passkey" }
}
