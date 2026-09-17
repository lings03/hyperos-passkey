package io.github.rin.passkey.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.Log
import io.github.rin.passkey.BuildConfig
import io.github.rin.passkey.R
import io.github.rin.passkey.data.CredentialSettings
import io.github.rin.passkey.data.SettingsFailure
import io.github.rin.passkey.ui.AppException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

enum class ShizukuStatus { NOT_INSTALLED, STOPPED, UNAUTHORIZED, DENIED, READY, UNSUPPORTED }

class ShizukuClient(private val context: Context) {
    private val mutableStatus = MutableStateFlow(ShizukuStatus.STOPPED)
    val status = mutableStatus.asStateFlow()
    private val connectionMutex = Mutex()
    private var remote: ICredentialSettingsService? = null
    private var pending: CompletableDeferred<ICredentialSettingsService>? = null
    private var bound = false
    private val userId = Process.myUid() / 100_000
    private val arguments = Shizuku.UserServiceArgs(ComponentName(context, CredentialSettingsService::class.java))
        .daemon(false)
        .processNameSuffix("credential_settings")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (!binder.pingBinder()) {
                disconnected()
                return
            }
            val service = ICredentialSettingsService.Stub.asInterface(binder)
            remote = service
            pending?.complete(service)
        }

        override fun onServiceDisconnected(name: ComponentName) = disconnected()
    }

    private val receivedListener = Shizuku.OnBinderReceivedListener { refreshStatus() }
    private val deadListener = Shizuku.OnBinderDeadListener {
        disconnected()
        refreshStatus()
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == REQUEST_CODE) refreshStatus()
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(receivedListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshStatus()
    }

    fun refreshStatus() {
        mutableStatus.value = try {
            when {
                !Shizuku.pingBinder() -> if (isInstalled()) ShizukuStatus.STOPPED else ShizukuStatus.NOT_INSTALLED
                Shizuku.isPreV11() || Shizuku.getVersion() < 13 -> ShizukuStatus.UNSUPPORTED
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuStatus.READY
                Shizuku.shouldShowRequestPermissionRationale() -> ShizukuStatus.DENIED
                else -> ShizukuStatus.UNAUTHORIZED
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Cannot read Shizuku status", exception)
            ShizukuStatus.STOPPED
        }
    }

    fun requestPermission() {
        refreshStatus()
        if (status.value != ShizukuStatus.UNAUTHORIZED) throw AppException(R.string.error_shizuku)
        Shizuku.requestPermission(REQUEST_CODE)
    }

    suspend fun read(): CredentialSettings {
        val service = connectedService()
        return withContext(Dispatchers.IO) { service.readSettings(userId).settingsOrThrow() }
    }

    suspend fun apply(expected: CredentialSettings, target: CredentialSettings): CredentialSettings {
        val service = connectedService()
        return withContext(Dispatchers.IO) {
            service.applySettings(userId, expected.enabled, expected.primary, target.enabled, target.primary).settingsOrThrow()
        }
    }

    private suspend fun connectedService(): ICredentialSettingsService = withContext(Dispatchers.Main.immediate) {
        connectionMutex.withLock {
            refreshStatus()
            if (status.value != ShizukuStatus.READY) throw AppException(R.string.error_shizuku)
            remote?.takeIf { it.asBinder().pingBinder() }?.let { return@withLock it }
            val deferred = CompletableDeferred<ICredentialSettingsService>()
            pending = deferred
            try {
                bound = true
                Shizuku.bindUserService(arguments, connection)
                withTimeout(12_000) { deferred.await() }
            } catch (exception: Exception) {
                disconnectService()
                throw exception
            } finally {
                pending = null
            }
        }
    }

    private fun disconnected() {
        remote = null
        pending?.completeExceptionally(AppException(R.string.error_disconnected))
    }

    private fun disconnectService() {
        if (bound) {
            try {
                Shizuku.unbindUserService(arguments, connection, true)
            } catch (exception: Exception) {
                Log.w(TAG, "Cannot unbind Shizuku service", exception)
            }
        }
        bound = false
        disconnected()
    }

    fun close() {
        Shizuku.removeBinderReceivedListener(receivedListener)
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        disconnectService()
    }

    private fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun Bundle.settingsOrThrow(): CredentialSettings {
        if (!getBoolean("ok")) {
            val resource = if (containsKey("restored")) {
                if (getBoolean("restored")) R.string.error_write_restored else R.string.error_write_restore_failed
            } else when (getString("error_code")) {
                SettingsFailure.CONFLICT.name -> R.string.error_conflict
                SettingsFailure.TIMEOUT.name -> R.string.error_timeout
                SettingsFailure.PERMISSION.name, SettingsFailure.REJECTED.name -> R.string.error_permission
                SettingsFailure.READBACK.name -> R.string.error_readback
                SettingsFailure.INVALID.name -> R.string.error_invalid
                else -> R.string.error_operation
            }
            throw AppException(resource)
        }
        return CredentialSettings(getString("enabled"), getString("primary"))
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val TAG = "PasskeyShizuku"
        private const val REQUEST_CODE = 10
    }
}
