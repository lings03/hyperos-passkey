package io.github.rin.passkey.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rin.passkey.AutofillStatus
import io.github.rin.passkey.PasskeyUiState
import io.github.rin.passkey.R
import io.github.rin.passkey.data.DiagnosticService
import io.github.rin.passkey.data.Diagnostics
import io.github.rin.passkey.data.ProviderApp
import io.github.rin.passkey.shizuku.ShizukuStatus
import io.github.rin.passkey.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Lists with more apps than this get a search field. */
private const val SEARCH_THRESHOLD = 6

@Composable
internal fun Overlays(state: PasskeyUiState, actions: PasskeyActions, overlay: Overlay?, show: (Overlay?) -> Unit) {
    val dismiss = { show(null) }
    val autofillStatus = state.autofillStatus
    val tagPasswordOnly = stringResource(R.string.tag_password_only)
    val tagSystem = stringResource(R.string.system_app)

    AppPickerSheet(
        show = overlay == Overlay.PasskeyPicker,
        title = stringResource(R.string.sheet_title),
        apps = state.passkeyApps,
        selectedPackage = state.selectedProvider?.packageName,
        state = state,
        emptyTitle = R.string.empty_title,
        emptySummary = R.string.empty_summary,
        scope = R.string.providers_scope,
        tags = { app -> listOfNotNull(tagSystem.takeIf { app.isSystem }) },
        onDismiss = dismiss,
        onSelect = { dismiss(); actions.selectProvider(it) },
    )
    AppPickerSheet(
        show = overlay == Overlay.AutofillPicker,
        title = stringResource(R.string.autofill_sheet_title),
        apps = state.autofillApps,
        selectedPackage = (autofillStatus as? AutofillStatus.App)?.app?.packageName,
        state = state,
        emptyTitle = R.string.autofill_empty_title,
        emptySummary = R.string.autofill_empty_summary,
        scope = R.string.autofill_scope,
        tags = { app -> listOfNotNull(tagSystem.takeIf { app.isSystem }) },
        onDismiss = dismiss,
        onSelect = { dismiss(); actions.selectAutofill(it) },
        header = {
            val selected = autofillStatus == AutofillStatus.None || autofillStatus == AutofillStatus.CredentialOnly
            ListRow(
                title = stringResource(R.string.autofill_none_option),
                summary = stringResource(R.string.autofill_none_option_summary),
                titleColor = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                leading = { IconTile(AppIcons.AutofillOff, color = MiuixTheme.colorScheme.onSurfaceSecondary, container = MiuixTheme.colorScheme.secondaryContainer) },
                enabled = state.canApply,
                modifier = Modifier.selectable(selected, enabled = state.canApply, role = Role.RadioButton) { dismiss(); actions.selectAutofill(null) },
            ) { SelectionMark(selected) }
        },
    )
    ProvidersSheet(
        show = overlay == Overlay.Providers,
        state = state,
        tags = { app, primary ->
            listOfNotNull(stringResource(R.string.tag_primary).takeIf { primary }, tagPasswordOnly.takeIf { !app.providesPasskeys }, tagSystem.takeIf { app.isSystem })
        },
        onDismiss = dismiss,
        onToggle = actions.setProviderEnabled,
        onCleanUp = { show(Overlay.Cleanup) },
    )
    ConfirmDialog(
        show = overlay == Overlay.Restore,
        title = stringResource(R.string.restore_original),
        summary = stringResource(R.string.restore_confirm),
        confirm = stringResource(R.string.restore),
        enabled = state.canApply,
        onDismiss = dismiss,
        onConfirm = { dismiss(); actions.restore() },
    )
    val stale = state.staleComponents
    ConfirmDialog(
        show = overlay == Overlay.Cleanup,
        title = stringResource(R.string.stale_title),
        summary = stringResource(R.string.stale_confirm),
        confirm = stringResource(R.string.clean_up),
        enabled = state.canApply && stale.isNotEmpty(),
        onDismiss = dismiss,
        onConfirm = { dismiss(); actions.cleanUpStale(stale) },
    ) {
        CodeBlock(Modifier.heightIn(max = 220.dp)) { stale.forEach { CodeText(it) } }
    }
    ConfigurationSheet(overlay == Overlay.Configuration, state, dismiss)
    DiagnosticsSheet(state.diagnostics, onDismiss = actions.closeDiagnostics, onCopy = actions.copyText)
}

@Composable
private fun sheetMaxHeight(): Dp {
    val containerHeight = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) { containerHeight.toDp() * 0.75f }
}

@Composable
private fun Sheet(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    endAction: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    OverlayBottomSheet(
        show = show, title = title,
        startAction = { ToolbarButton(AppIcons.Close, stringResource(R.string.close), onClick = onDismiss) },
        endAction = endAction,
        onDismissRequest = onDismiss, backgroundColor = MiuixTheme.colorScheme.surface,
        outsideMargin = DpSize(0.dp, 0.dp), insideMargin = DpSize(16.dp, 8.dp),
        cornerRadius = 28.dp,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SheetHint(state: PasskeyUiState) {
    if (!state.canApply) Note(
        stringResource(if (state.status == ShizukuStatus.READY) R.string.read_before_select else R.string.authorize_to_select),
        color = MiuixTheme.colorScheme.primary,
    )
}

@Composable
private fun AppPickerSheet(
    show: Boolean,
    title: String,
    apps: List<ProviderApp>,
    selectedPackage: String?,
    state: PasskeyUiState,
    emptyTitle: Int,
    emptySummary: Int,
    scope: Int,
    tags: (ProviderApp) -> List<String>,
    onDismiss: () -> Unit,
    onSelect: (ProviderApp) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(show) { if (!show) query = "" }
    val filtered = remember(apps, query) {
        apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }
    Sheet(show, title, onDismiss) {
        if (apps.size > SEARCH_THRESHOLD) SearchField(query) { query = it }
        SheetHint(state)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            val showHeader = header != null && query.isEmpty()
            if (showHeader || filtered.isNotEmpty()) GroupCard {
                if (showHeader) {
                    header()
                    if (filtered.isNotEmpty()) RowDivider(AppIconInset)
                }
                filtered.forEachIndexed { index, app ->
                    if (index > 0) RowDivider(AppIconInset)
                    SelectableAppRow(app, tags(app), selected = selectedPackage == app.packageName, enabled = state.canApply) { onSelect(app) }
                }
            }
            when {
                state.loading && apps.isEmpty() -> Loading()
                filtered.isEmpty() -> EmptyState(
                    stringResource(if (query.isEmpty()) emptyTitle else R.string.no_search_results),
                    if (query.isEmpty()) stringResource(emptySummary) else null,
                )
            }
            Gap(8.dp)
            Note(stringResource(scope))
            if (state.skippedProviders > 0) Note(stringResource(R.string.skipped_providers), color = MiuixTheme.colorScheme.error)
            Gap(12.dp)
        }
    }
}

@Composable
private fun ProvidersSheet(
    show: Boolean,
    state: PasskeyUiState,
    tags: @Composable (ProviderApp, Boolean) -> List<String>,
    onDismiss: () -> Unit,
    onToggle: (ProviderApp, Boolean) -> Unit,
    onCleanUp: () -> Unit,
) {
    val enabledPackages = state.enabledPackages
    val primaryPackages = state.primaryPackages
    val stale = state.staleComponents
    Sheet(show, stringResource(R.string.providers_sheet_title), onDismiss) {
        SheetHint(state)
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (stale.isNotEmpty()) GroupCard {
                NavRow(
                    AppIcons.Trash, stringResource(R.string.stale_title),
                    summary = pluralStringResource(R.plurals.stale_summary, stale.size, stale.size),
                    summaryColor = MiuixTheme.colorScheme.error, enabled = state.canApply, onClick = onCleanUp,
                )
            }
            if (state.credentialApps.isNotEmpty()) GroupCard {
                state.credentialApps.forEachIndexed { index, app ->
                    if (index > 0) RowDivider(AppIconInset)
                    val primary = app.packageName in primaryPackages
                    val checked = app.packageName in enabledPackages
                    val enabled = state.canApply && !primary
                    AppRow(
                        app, tags(app, primary), selected = primary, enabled = state.canApply,
                        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch) { onToggle(app, it) },
                    ) { Switch(checked, { onToggle(app, it) }, enabled = enabled) }
                }
            } else if (state.loading) Loading() else EmptyState(stringResource(R.string.empty_title), null)
            Column {
                Note(stringResource(R.string.providers_sheet_hint))
                Gap(12.dp)
            }
        }
    }
}

@Composable
private fun ConfigurationSheet(show: Boolean, state: PasskeyUiState, onDismiss: () -> Unit) {
    val settings = state.settings
    Sheet(show, stringResource(R.string.view_settings), onDismiss) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SelectionContainer {
                GroupCard {
                    ConfigurationValue(stringResource(R.string.primary_providers), settings?.primary, settings != null)
                    RowDivider(RowPadding)
                    ConfigurationValue(stringResource(R.string.enabled_providers), settings?.enabled, settings != null)
                    RowDivider(RowPadding)
                    ConfigurationValue(stringResource(R.string.autofill_value), settings?.autofill, settings != null)
                }
            }
            Column {
                Note(stringResource(R.string.read_hint))
                Gap(12.dp)
            }
        }
    }
}

@Composable
private fun ConfigurationValue(title: String, value: String?, loaded: Boolean) {
    Column(Modifier.fillMaxWidth().padding(RowPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        val entries = value?.split(':')?.filter { it.isNotEmpty() }.orEmpty()
        when {
            !loaded -> CodeText(stringResource(R.string.value_unread), muted = true)
            value == null -> CodeText(stringResource(R.string.value_not_set), muted = true)
            entries.isEmpty() -> CodeText(stringResource(R.string.value_empty), muted = true)
            else -> entries.forEach { CodeText(it) }
        }
    }
}

@Composable
private fun DiagnosticsSheet(diagnostics: Diagnostics?, onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    // Keep showing the last content while the sheet animates out.
    var last by remember { mutableStateOf<Diagnostics?>(null) }
    LaunchedEffect(diagnostics) { if (diagnostics != null) last = diagnostics }
    val current = diagnostics ?: last
    val result = current?.result
    val error = current?.error
    val title = stringResource(if (current?.service == DiagnosticService.AUTOFILL) R.string.diag_autofill else R.string.diag_credential)
    val chunks = remember(result) { result?.text?.lines()?.chunked(60)?.map { it.joinToString("\n") }.orEmpty() }
    Sheet(
        diagnostics != null, title, onDismiss,
        endAction = {
            ToolbarButton(AppIcons.Copy, stringResource(R.string.copy), enabled = result != null && result.text.isNotEmpty()) {
                result?.let { onCopy(it.text) }
            }
        },
    ) {
        when {
            error != null -> Banner(AppIcons.Alert, error.text(), MiuixTheme.colorScheme.error, MiuixTheme.colorScheme.errorContainer)
            result == null -> Loading(stringResource(R.string.diag_loading))
            else -> {
                if (result.truncated) Note(stringResource(R.string.diag_truncated), color = warningColor())
                if (result.text.isBlank()) Note(stringResource(R.string.diag_empty))
                else GroupCard(Modifier.weight(1f, fill = false)) {
                    LazyColumn(contentPadding = PaddingValues(RowPadding)) {
                        items(chunks) { chunk -> SelectionContainer { CodeText(chunk, size = 11) } }
                    }
                }
            }
        }
        Gap(8.dp)
    }
}

@Composable
private fun ConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    confirm: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    OverlayDialog(show = show, title = title, summary = summary, onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(stringResource(R.string.cancel), onDismiss, modifier = Modifier.weight(1f))
                TextButton(confirm, onConfirm, modifier = Modifier.weight(1f), enabled = enabled, colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query, onValueChange = onQueryChange, label = stringResource(R.string.search_apps),
        useLabelAsPlaceholder = true, singleLine = true, modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(
                AppIcons.Search, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 14.dp, end = 6.dp),
            )
        },
    )
}

@Composable
private fun CodeBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    SelectionContainer {
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun CodeText(text: String, muted: Boolean = false, size: Int = 12) {
    Text(
        text, fontSize = size.sp, lineHeight = (size + 5).sp, fontFamily = FontFamily.Monospace,
        color = if (muted) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Loading(text: String? = null) {
    Row(Modifier.fillMaxWidth().padding(28.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(size = 22.dp)
        if (text != null) {
            HGap(12.dp)
            Text(text, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun EmptyState(title: String, summary: String?) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
            if (summary != null) Text(
                summary, fontSize = 13.sp, lineHeight = 19.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
