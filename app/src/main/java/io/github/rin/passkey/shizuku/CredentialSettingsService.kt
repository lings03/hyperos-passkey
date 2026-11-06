package io.github.rin.passkey.shizuku

import android.os.Binder
import android.os.Bundle
import android.util.Log
import io.github.rin.passkey.data.CredentialSettings
import io.github.rin.passkey.data.CredentialSettingsStore
import io.github.rin.passkey.data.DiagnosticService
import io.github.rin.passkey.data.SettingsAccessException
import io.github.rin.passkey.data.SettingsFailure
import io.github.rin.passkey.data.SettingsTransaction
import io.github.rin.passkey.data.SettingsWriteException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class CredentialSettingsService : ICredentialSettingsService.Stub() {
    private val streamExecutor = Executors.newCachedThreadPool()

    @Synchronized
    override fun readSettings(userId: Int): Bundle = respond(userId) {
        settingsBundle(ShellSettingsStore(userId).read())
    }

    @Synchronized
    override fun applySettings(
        userId: Int,
        expectedEnabled: String?,
        expectedPrimary: String?,
        expectedAutofill: String?,
        enabled: String?,
        primary: String?,
        autofill: String?,
    ): Bundle = respond(userId) {
        settingsBundle(
            SettingsTransaction(ShellSettingsStore(userId)).apply(
                CredentialSettings(expectedEnabled, expectedPrimary, expectedAutofill),
                CredentialSettings(enabled, primary, autofill),
            ),
        )
    }

    @Synchronized
    override fun dumpService(userId: Int, service: String): Bundle = respond(userId) {
        val target = DiagnosticService.fromName(service) ?: throw IllegalArgumentException("Unsupported service")
        val output = execute(listOf("/system/bin/dumpsys", target.serviceName), DUMP_LIMIT, truncate = true, timeoutSeconds = 15)
        if (output.text.startsWith("Can't find service")) throw SettingsAccessException(SettingsFailure.UNAVAILABLE)
        if (output.text.startsWith("Permission Denial")) throw SettingsAccessException(SettingsFailure.PERMISSION)
        if (output.exitCode != 0) throw SettingsAccessException(SettingsFailure.REJECTED)
        Bundle().apply {
            putBoolean("ok", true)
            putString("output", output.text)
            putBoolean("truncated", output.truncated)
        }
    }

    @Synchronized
    override fun destroy() {
        streamExecutor.shutdownNow()
        exitProcess(0)
    }

    private fun settingsBundle(settings: CredentialSettings) = Bundle().apply {
        putBoolean("ok", true)
        putString("enabled", settings.enabled)
        putString("primary", settings.primary)
        putString("autofill", settings.autofill)
    }

    private fun respond(userId: Int, block: () -> Bundle): Bundle = try {
        if (userId < 0 || Binder.getCallingUid() / 100_000 != userId) throw SettingsAccessException(SettingsFailure.PERMISSION)
        block()
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
        override fun read() = CredentialSettings(
            readKey(CredentialSettings.ENABLED_KEY),
            readKey(CredentialSettings.PRIMARY_KEY),
            readKey(CredentialSettings.AUTOFILL_KEY),
        ).also { it.validate() }

        private fun readKey(key: String): String? = settings(userId, "get", key).let {
            if (it == "null") null else it
        }

        override fun write(key: String, value: String?) {
            require(key in CredentialSettings.KEYS)
            val output = if (value == null) settings(userId, "delete", key) else settings(userId, "put", key, value)
            if (output.contains("Error", ignoreCase = true) || output.contains("Exception", ignoreCase = true))
                throw SettingsAccessException(SettingsFailure.REJECTED)
        }
    }

    private fun settings(userId: Int, operation: String, key: String, value: String? = null): String {
        val arguments = mutableListOf("/system/bin/settings", "--user", userId.toString(), operation, "secure", key)
        if (value != null) arguments += value
        val output = execute(arguments, SETTINGS_LIMIT, truncate = false, timeoutSeconds = 8)
        if (output.text.contains("SecurityException") || output.text.contains("Permission denial", ignoreCase = true))
            throw SettingsAccessException(SettingsFailure.PERMISSION)
        if (output.exitCode != 0) throw SettingsAccessException(SettingsFailure.REJECTED)
        return output.text
    }

    private class Output(val text: String, val truncated: Boolean, val exitCode: Int)

    private fun execute(arguments: List<String>, limit: Int, truncate: Boolean, timeoutSeconds: Long): Output {
        val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
        val output = streamExecutor.submit<Pair<String, Boolean>> {
            process.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                val result = StringBuilder()
                var truncated = false
                while (true) {
                    val count = reader.read(buffer)
                    if (count == -1) break
                    // Keep draining after truncation so the process is not blocked on a full pipe.
                    if (truncated) continue
                    if (result.length + count > limit) {
                        check(truncate) { "Command output exceeds maximum size" }
                        result.append(buffer, 0, limit - result.length)
                        truncated = true
                    } else {
                        result.append(buffer, 0, count)
                    }
                }
                result.toString().trimEnd('\r', '\n') to truncated
            }
        }
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) throw SettingsAccessException(SettingsFailure.TIMEOUT)
            val (text, truncated) = output.get(2, TimeUnit.SECONDS)
            return Output(text, truncated, process.exitValue())
        } finally {
            output.cancel(true)
            try {
                process.destroyForcibly()
                process.inputStream.close()
            } catch (exception: Exception) {
                Log.w(TAG, "Cannot clean up command process", exception)
            }
        }
    }

    private companion object {
        const val TAG = "PasskeyService"
        const val SETTINGS_LIMIT = 32_768

        // Keeps the reply well below the binder transaction limit.
        const val DUMP_LIMIT = 98_304
    }
}
