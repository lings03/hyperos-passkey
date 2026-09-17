package io.github.rin.passkey.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

data class AppMessage(@param:StringRes val resource: Int, val arguments: List<String> = emptyList()) {
    @Composable
    fun text(): String = stringResource(resource, *arguments.toTypedArray())
}

class AppException(@param:StringRes val resource: Int) : Exception("Passkey resource error: $resource")
