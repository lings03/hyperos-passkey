package io.github.rin.passkey.data

import io.github.rin.passkey.ui.AppMessage

/** System services whose dumpsys output can help explain why a provider is not used. */
enum class DiagnosticService(val serviceName: String) {
    CREDENTIAL("credential"),
    AUTOFILL("autofill");

    companion object {
        fun fromName(name: String): DiagnosticService? = entries.firstOrNull { it.serviceName == name }
    }
}

data class DumpResult(val text: String, val truncated: Boolean)

data class Diagnostics(
    val service: DiagnosticService,
    val result: DumpResult? = null,
    val error: AppMessage? = null,
) {
    val loading: Boolean get() = result == null && error == null
}
