package io.github.rin.passkey.shizuku

import android.os.Binder
import android.os.Bundle
import android.util.Log
import io.github.rin.passkey.data.CredentialSettings
import io.github.rin.passkey.data.CredentialSettingsStore
import io.github.rin.passkey.data.SettingsTransaction
import io.github.rin.passkey.data.SettingsAccessException
import io.github.rin.passkey.data.SettingsFailure
import io.github.rin.passkey.data.SettingsWriteException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class CredentialSettingsService : ICredentialSettingsService.Stub() {
    private val streamExecutor = Executors.newCachedThreadPool()

    @Synchronized
    override fun readSettings(userId: Int): Bundle = respond(userId) {
        ShellSettingsStore(userId).read()
    }

    @Synchronized
    override fun applySettings(
        userId: Int,
        expectedEnabled: String?,
        expectedPrimary: String?,
        enabled: String?,
        primary: String?,
    ): Bundle = respond(userId) {
        SettingsTransaction(ShellSettingsStore(userId)).apply(
            CredentialSettings(expectedEnabled, expectedPrimary),
            CredentialSettings(enabled, primary),
        )
    }

    @Synchronized
    override fun destroy() {
        streamExecutor.shutdownNow()
        exitProcess(0)
    }

    private fun respond(userId: Int, block: () -> CredentialSettings): Bundle = try {
        if (userId < 0 || Binder.getCallingUid() / 100_000 != userId) throw SettingsAccessException(SettingsFailure.PERMISSION)
        val settings = block()
        Bundle().apply {
            putBoolean("ok", true)
            putString("enabled", settings.enabled)
            putString("primary", settings.primary)
        }
    } catch (exception: Exception) {
        Log.e(TAG, "Credential settings operation failed", exception)
        Bundle().apply {
            putBoolean("ok", false)
            val cause = if (exception is SettingsWriteException) exception.cause else exception
            val reason = when (cause) {
                is SettingsAccessException -> cause.reason
                is IllegalArgumentException -> SettingsFailure.INVALID
                else -> SettingsFailure.UNKNOWN
            }
            putString("error_code", reason.name)
            if (exception is SettingsWriteException) putBoolean("restored", exception.restored)
        }
    }

    private inner class ShellSettingsStore(private val userId: Int) : CredentialSettingsStore {
        override fun read() = CredentialSettings(readKey(CredentialSettings.ENABLED_KEY), readKey(CredentialSettings.PRIMARY_KEY)).also { it.validate() }

        private fun readKey(key: String): String? = command(userId, "get", key).let {
            if (it == "null") null else it
        }

        override fun write(key: String, value: String?) {
            require(key == CredentialSettings.ENABLED_KEY || key == CredentialSettings.PRIMARY_KEY)
            val output = if (value == null) command(userId, "delete", key) else command(userId, "put", key, value)
            if (output.contains("Error", ignoreCase = true) || output.contains("Exception", ignoreCase = true))
                throw SettingsAccessException(SettingsFailure.REJECTED)
        }
    }

    private fun command(userId: Int, operation: String, key: String, value: String? = null): String {
        val arguments = mutableListOf("/system/bin/settings", "--user", userId.toString(), operation, "secure", key)
        if (value != null) arguments += value
        val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
        val output = streamExecutor.submit<String> {
            process.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(1024)
                val result = StringBuilder()
                while (true) {
                    val count = reader.read(buffer)
                    if (count == -1) break
                    check(result.length + count <= 32_768) { "Command output exceeds maximum size" }
                    result.append(buffer, 0, count)
                }
                result.toString().trimEnd('\r', '\n')
            }
        }
        try {
            if (!process.waitFor(8, TimeUnit.SECONDS)) throw SettingsAccessException(SettingsFailure.TIMEOUT)
            val result = output.get(2, TimeUnit.SECONDS)
            if (result.contains("SecurityException") || result.contains("Permission denial", ignoreCase = true))
                throw SettingsAccessException(SettingsFailure.PERMISSION)
            if (process.exitValue() != 0) throw SettingsAccessException(SettingsFailure.REJECTED)
            return result
        } finally {
            output.cancel(true)
            try {
                process.destroyForcibly()
                process.inputStream.close()
            } catch (exception: Exception) {
                Log.w(TAG, "Cannot clean up settings process", exception)
            }
        }
    }

    private companion object {
        const val TAG = "PasskeyService"
    }
}
