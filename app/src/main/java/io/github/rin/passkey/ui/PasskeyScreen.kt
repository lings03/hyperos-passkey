package io.github.rin.passkey.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rin.passkey.AutofillStatus
import io.github.rin.passkey.BuildConfig
import io.github.rin.passkey.PasskeyUiState
import io.github.rin.passkey.R
import io.github.rin.passkey.data.DiagnosticService
import io.github.rin.passkey.data.Diagnostics
import io.github.rin.passkey.data.ProviderApp
import io.github.rin.passkey.shizuku.ShizukuStatus
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

class PasskeyActions(
    val refresh: () -> Unit = {},
    val authorize: () -> Unit = {},
    val openShizuku: () -> Unit = {},
    val getShizuku: () -> Unit = {},
    val selectProvider: (ProviderApp) -> Unit = {},
    val selectAutofill: (ProviderApp?) -> Unit = {},
    val setProviderEnabled: (ProviderApp, Boolean) -> Unit = { _, _ -> },
    val cleanUpStale: (List<String>) -> Unit = {},
    val setKeepOthers: (Boolean) -> Unit = {},
    val setSyncAutofill: (Boolean) -> Unit = {},
    val restore: () -> Unit = {},
    val noticeShown: () -> Unit = {},
    val openDiagnostics: (DiagnosticService) -> Unit = {},
    val closeDiagnostics: () -> Unit = {},
    val copyText: (String) -> Unit = {},
    val openSystemSettings: () -> Unit = {},
    val openPasswordManager: () -> Unit = {},
    val openFidoBridge: () -> Unit = {},
)

const val GOOGLE_PLAY_SERVICES = "com.google.android.gms"

@Composable
fun PasskeyScreen(state: PasskeyUiState, actions: PasskeyActions) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showAutofillPicker by rememberSaveable { mutableStateOf(false) }
    var showProviders by rememberSaveable { mutableStateOf(false) }
    var showConfiguration by rememberSaveable { mutableStateOf(false) }
    var showRestore by rememberSaveable { mutableStateOf(false) }
    var showCleanup by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val notice = state.notice?.text()
    val errorMessage = state.error?.text()
    val stale = state.staleComponents
    val ready = state.status == ShizukuStatus.READY
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            actions.noticeShown()
        }
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) snackbar.showSnackbar(errorMessage, withDismissAction = true, duration = SnackbarDuration.Long)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.title_main),
                subtitle = stringResource(R.string.subtitle_main),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = actions.refresh, enabled = !state.loading && !state.applying) {
                        Icon(MiuixIcons.Refresh, stringResource(R.string.refresh), tint = MiuixTheme.colorScheme.onSurface)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(state = snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle(stringResource(R.string.section_default)) }
            item {
                DefaultProviderCard(state, onClick = { showPicker = true })
                Caption(
                    if (state.loading) stringResource(R.string.scan_loading)
                    else pluralStringResource(R.plurals.apps_found, state.passkeyApps.size, state.passkeyApps.size),
                )
            }
            item { SectionTitle(stringResource(R.string.section_autofill), Modifier.padding(top = 14.dp)) }
            item {
                AutofillCard(state, onClick = { showAutofillPicker = true })
                if (!state.loading) Caption(pluralStringResource(R.plurals.autofill_apps_found, state.autofillApps.size, state.autofillApps.size))
            }
            if (state.applying) item {
                SurfaceCard {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(size = 22.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(R.string.applying), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
            state.error?.let { error -> item {
                Card(
                    modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp,
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.error_title), color = MiuixTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        Text(error.text(), color = MiuixTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
                        TextButton(stringResource(R.string.refresh), onClick = actions.refresh, enabled = !state.applying && !state.loading)
                    }
                }
            } }
            item { SectionTitle(stringResource(R.string.section_connection), Modifier.padding(top = 14.dp)) }
            item { ShizukuCard(state.status, actions.authorize, actions.openShizuku, actions.getShizuku) }
            item { SectionTitle(stringResource(R.string.section_settings), Modifier.padding(top = 14.dp)) }
            item {
                SurfaceCard {
                    SettingRow(MiuixIcons.Settings, stringResource(R.string.keep_others), stringResource(R.string.keep_others_summary)) {
                        Switch(state.keepOthers, actions.setKeepOthers, enabled = !state.applying)
                    }
                    RowDivider()
                    SettingRow(MiuixIcons.Merge, stringResource(R.string.sync_autofill), stringResource(R.string.sync_autofill_summary)) {
                        Switch(state.syncAutofill, actions.setSyncAutofill, enabled = !state.applying)
                    }
                    RowDivider()
                    SettingRow(
                        MiuixIcons.Tune, stringResource(R.string.manage_providers),
                        if (state.settings == null) stringResource(R.string.manage_providers_unread)
                        else pluralStringResource(R.plurals.manage_providers_summary, state.enabledPackages.size, state.enabledPackages.size),
                        onClick = { showProviders = true },
                    ) { ForwardArrow() }
                    if (stale.isNotEmpty()) {
                        RowDivider()
                        SettingRow(
                            MiuixIcons.Delete, stringResource(R.string.stale_title),
                            pluralStringResource(R.plurals.stale_summary, stale.size, stale.size),
                            enabled = state.canApply, summaryColor = MiuixTheme.colorScheme.error,
                            onClick = { showCleanup = true },
                        ) { ForwardArrow() }
                    }
                    RowDivider()
                    SettingRow(
                        MiuixIcons.Undo, stringResource(R.string.restore_original),
                        stringResource(if (state.hasBackup) R.string.restore_summary else R.string.no_backup),
                        enabled = state.canApply && state.hasBackup,
                        onClick = { showRestore = true },
                    ) { ForwardArrow() }
                }
            }
            item { SectionTitle(stringResource(R.string.section_diagnostics), Modifier.padding(top = 14.dp)) }
            item {
                SurfaceCard {
                    SettingRow(
                        MiuixIcons.Info, stringResource(R.string.view_settings), stringResource(R.string.view_settings_summary),
                        onClick = { showConfiguration = true },
                    ) { ForwardArrow() }
                    RowDivider()
                    SettingRow(
                        MiuixIcons.Report, stringResource(R.string.diag_credential),
                        stringResource(if (ready) R.string.diag_credential_summary else R.string.diag_requires_shizuku),
                        enabled = ready, onClick = { actions.openDiagnostics(DiagnosticService.CREDENTIAL) },
                    ) { ForwardArrow() }
                    RowDivider()
                    SettingRow(
                        MiuixIcons.Search, stringResource(R.string.diag_autofill),
                        stringResource(if (ready) R.string.diag_autofill_summary else R.string.diag_requires_shizuku),
                        enabled = ready, onClick = { actions.openDiagnostics(DiagnosticService.AUTOFILL) },
                    ) { ForwardArrow() }
                }
            }
            item { SectionTitle(stringResource(R.string.section_tools), Modifier.padding(top = 14.dp)) }
            item {
                SurfaceCard {
                    SettingRow(
                        MiuixIcons.Settings, stringResource(R.string.tool_system), stringResource(R.string.tool_system_summary),
                        onClick = actions.openSystemSettings,
                    ) { ForwardArrow() }
                    if (state.credentialApps.any { it.packageName == GOOGLE_PLAY_SERVICES }) {
                        RowDivider()
                        SettingRow(
                            MiuixIcons.Lock, stringResource(R.string.tool_gpm), stringResource(R.string.tool_gpm_summary),
                            onClick = actions.openPasswordManager,
                        ) { ForwardArrow() }
                    }
                    RowDivider()
                    SettingRow(
                        MiuixIcons.Link, stringResource(R.string.tool_fido), stringResource(R.string.tool_fido_summary),
                        onClick = actions.openFidoBridge,
                    ) { ForwardArrow() }
                }
            }
            item { SectionTitle(stringResource(R.string.section_help), Modifier.padding(top = 14.dp)) }
            item {
                SurfaceCard {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(R.string.help_step_one, R.string.help_step_two, R.string.help_step_three).forEach {
                            Text(stringResource(it), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface)
                        }
                        listOf(R.string.help_autofill, R.string.help_hyperos, R.string.help_scope).forEach {
                            Text(stringResource(it), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.footer, BuildConfig.VERSION_NAME), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }

        AppPickerSheet(
            show = showPicker,
            title = stringResource(R.string.sheet_title),
            apps = state.passkeyApps,
            selectedPackage = state.selectedProvider?.packageName,
            state = state,
            emptyTitle = R.string.empty_title,
            emptySummary = R.string.empty_summary,
            scope = R.string.providers_scope,
            tags = { app -> if (app.providesAutofill) listOf(stringResource(R.string.tag_autofill)) else emptyList() },
            onDismiss = { showPicker = false },
            onSelect = { provider -> showPicker = false; actions.selectProvider(provider) },
            header = if (state.hasBackup) {
                {
                    SurfaceCard(Modifier.clickable(enabled = state.canApply, onClick = { showPicker = false; showRestore = true })) {
                        SettingRow(MiuixIcons.Undo, stringResource(R.string.restore_original), stringResource(R.string.restore_summary)) {}
                    }
                }
            } else null,
        )
        val autofillStatus = state.autofillStatus
        AppPickerSheet(
            show = showAutofillPicker,
            title = stringResource(R.string.autofill_sheet_title),
            apps = state.autofillApps,
            selectedPackage = (autofillStatus as? AutofillStatus.App)?.app?.packageName,
            state = state,
            emptyTitle = R.string.autofill_empty_title,
            emptySummary = R.string.autofill_empty_summary,
            scope = R.string.autofill_scope,
            tags = { app -> if (app.providesPasskeys) listOf(stringResource(R.string.tag_passkey)) else emptyList() },
            onDismiss = { showAutofillPicker = false },
            onSelect = { provider -> showAutofillPicker = false; actions.selectAutofill(provider) },
            header = {
                val selected = autofillStatus == AutofillStatus.None || autofillStatus == AutofillStatus.CredentialOnly
                SelectableCard(
                    selected = selected, enabled = state.canApply,
                    onClick = { showAutofillPicker = false; actions.selectAutofill(null) },
                ) {
                    IconTile(MiuixIcons.Edit, size = 48.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            stringResource(R.string.autofill_none_option), fontSize = 18.sp, fontWeight = FontWeight.Medium,
                            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        )
                        Text(stringResource(R.string.autofill_none_option_summary), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    if (selected) SelectedMark()
                }
            },
        )
        ProvidersSheet(
            show = showProviders,
            state = state,
            onDismiss = { showProviders = false },
            onToggle = actions.setProviderEnabled,
            onCleanUp = { showProviders = false; showCleanup = true },
        )
        OverlayDialog(
            show = showRestore,
            title = stringResource(R.string.restore_original),
            summary = stringResource(R.string.restore_confirm),
            onDismissRequest = { showRestore = false },
        ) {
            ConfirmButtons(stringResource(R.string.restore), state.canApply, onCancel = { showRestore = false }) {
                showRestore = false
                actions.restore()
            }
        }
        OverlayDialog(
            show = showCleanup,
            title = stringResource(R.string.stale_title),
            summary = stringResource(R.string.stale_confirm),
            onDismissRequest = { showCleanup = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SelectionContainer {
                    Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        stale.forEach { Text(it, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                    }
                }
                ConfirmButtons(stringResource(R.string.clean_up), state.canApply && stale.isNotEmpty(), onCancel = { showCleanup = false }) {
                    showCleanup = false
                    actions.cleanUpStale(stale)
                }
            }
        }
        ConfigurationDialog(showConfiguration, state) { showConfiguration = false }
        DiagnosticsSheet(state.diagnostics, onDismiss = actions.closeDiagnostics, onCopy = actions.copyText)
    }
}

@Composable
private fun DefaultProviderCard(state: PasskeyUiState, onClick: () -> Unit) {
    val provider = state.selectedProvider
    val title = provider?.label ?: stringResource(when {
        state.settings == null -> R.string.current_unknown
        !state.settingsConsistent -> R.string.current_inconsistent
        state.settings.primary.isNullOrEmpty() -> R.string.current_none
        state.settings.primary.contains(':') -> R.string.current_multiple
        else -> R.string.current_unavailable
    })
    val caption = provider?.let {
        if (it.providesPasskeys) stringResource(R.string.current_active)
        else stringResource(R.string.current_active) + " · " + stringResource(R.string.tag_password_only)
    }
    CurrentAppCard(provider, caption, title, provider?.packageName ?: stringResource(R.string.choose_hint), state, onClick)
}

@Composable
private fun AutofillCard(state: PasskeyUiState, onClick: () -> Unit) {
    val status = state.autofillStatus
    val app = (status as? AutofillStatus.App)?.app
    val title = app?.label ?: stringResource(when (status) {
        AutofillStatus.Unread -> R.string.current_unknown
        AutofillStatus.CredentialOnly -> R.string.autofill_credential_only
        is AutofillStatus.Unavailable -> R.string.autofill_unavailable
        else -> R.string.autofill_none
    })
    val summary = when (status) {
        is AutofillStatus.App -> status.app.packageName
        is AutofillStatus.Unavailable -> status.value
        AutofillStatus.CredentialOnly -> stringResource(R.string.autofill_credential_only_summary)
        else -> stringResource(R.string.autofill_hint)
    }
    val passkeyApp = state.selectedProvider
    val mismatch = app != null && passkeyApp != null && passkeyApp.providesAutofill && passkeyApp.packageName != app.packageName
    CurrentAppCard(
        app, if (app != null) stringResource(R.string.current_active) else null, title, summary, state, onClick,
        warning = if (mismatch) stringResource(R.string.autofill_mismatch, passkeyApp.label) else null,
    )
}

@Composable
private fun CurrentAppCard(
    app: ProviderApp?,
    caption: String?,
    title: String,
    summary: String,
    state: PasskeyUiState,
    onClick: () -> Unit,
    warning: String? = null,
) {
    SurfaceCard(modifier = Modifier.clickable(enabled = !state.applying, onClick = onClick)) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProviderIcon(app)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (caption != null) Text(caption, color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                Text(summary, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (warning != null) Text(warning, fontSize = 12.sp, color = MiuixTheme.colorScheme.error)
            }
            if (state.loading) CircularProgressIndicator(size = 20.dp) else ForwardArrow()
        }
    }
}

@Composable
private fun ShizukuCard(status: ShizukuStatus, onAuthorize: () -> Unit, onOpen: () -> Unit, onGet: () -> Unit) {
    val (title, summary) = when (status) {
        ShizukuStatus.READY -> R.string.shizuku_ready to R.string.shizuku_ready_summary
        ShizukuStatus.UNAUTHORIZED -> R.string.shizuku_unauthorized to R.string.shizuku_unauthorized_summary
        ShizukuStatus.DENIED -> R.string.shizuku_denied to R.string.shizuku_denied_summary
        ShizukuStatus.NOT_INSTALLED -> R.string.shizuku_missing to R.string.shizuku_missing_summary
        ShizukuStatus.UNSUPPORTED -> R.string.shizuku_unsupported to R.string.shizuku_unsupported_summary
        ShizukuStatus.STOPPED -> R.string.shizuku_stopped to R.string.shizuku_stopped_summary
    }
    SurfaceCard {
        SettingRow(MiuixIcons.Lock, stringResource(title), stringResource(summary)) {
            if (status == ShizukuStatus.READY) SelectedMark()
        }
        if (status != ShizukuStatus.READY) {
            val label = when (status) {
                ShizukuStatus.UNAUTHORIZED -> R.string.authorize
                ShizukuStatus.NOT_INSTALLED, ShizukuStatus.UNSUPPORTED -> R.string.download_shizuku
                else -> R.string.open_shizuku
            }
            TextButton(
                stringResource(label),
                onClick = when (status) {
                    ShizukuStatus.UNAUTHORIZED -> onAuthorize
                    ShizukuStatus.NOT_INSTALLED, ShizukuStatus.UNSUPPORTED -> onGet
                    else -> onOpen
                },
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun sheetMaxHeight(): Dp {
    val containerHeight = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) { containerHeight.toDp() * 0.72f }
}

@Composable
private fun AppSheet(show: Boolean, title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    OverlayBottomSheet(
        show = show, title = title,
        onDismissRequest = onDismiss, backgroundColor = MiuixTheme.colorScheme.surface,
        outsideMargin = DpSize(0.dp, 0.dp), insideMargin = DpSize(16.dp, 12.dp),
        cornerRadius = 30.dp,
    ) { content() }
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
    tags: @Composable (ProviderApp) -> List<String>,
    onDismiss: () -> Unit,
    onSelect: (ProviderApp) -> Unit,
    header: (@Composable () -> Unit)?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(show) { if (!show) query = "" }
    val filtered = remember(apps, query) {
        apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }
    AppSheet(show, title, onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (apps.isNotEmpty()) TextField(
                value = query, onValueChange = { query = it }, label = stringResource(R.string.search_apps),
                useLabelAsPlaceholder = true, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (!state.canApply) Text(
                stringResource(if (state.status == ShizukuStatus.READY) R.string.read_before_select else R.string.authorize_to_select),
                fontSize = 13.sp, color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp),
            )
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (header != null && query.isEmpty()) item { header() }
                if (state.loading) item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                items(filtered, key = { it.packageName }) { app ->
                    val selected = selectedPackage == app.packageName
                    SelectableCard(selected = selected, enabled = state.canApply, onClick = { onSelect(app) }) {
                        AppLabel(app, selected, tags(app) + systemTag(app), Modifier.weight(1f))
                        if (selected) SelectedMark()
                    }
                }
                if (filtered.isEmpty() && !state.loading) item {
                    SurfaceCard {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(if (query.isEmpty()) emptyTitle else R.string.no_search_results), fontWeight = FontWeight.Medium)
                            if (query.isEmpty()) Text(stringResource(emptySummary), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
                item { SheetNote(stringResource(scope)) }
                if (state.skippedProviders > 0) item { SheetNote(stringResource(R.string.skipped_providers), error = true) }
            }
            TextButton(stringResource(R.string.close), onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ProvidersSheet(
    show: Boolean,
    state: PasskeyUiState,
    onDismiss: () -> Unit,
    onToggle: (ProviderApp, Boolean) -> Unit,
    onCleanUp: () -> Unit,
) {
    val enabledPackages = state.enabledPackages
    val primaryPackages = state.primaryPackages
    val stale = state.staleComponents
    val primaryTag = stringResource(R.string.tag_primary)
    val passwordOnlyTag = stringResource(R.string.tag_password_only)
    val systemAppTag = stringResource(R.string.system_app)
    AppSheet(show, stringResource(R.string.providers_sheet_title), onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.canApply) Text(
                stringResource(if (state.status == ShizukuStatus.READY) R.string.read_before_select else R.string.authorize_to_select),
                fontSize = 13.sp, color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp),
            )
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (stale.isNotEmpty()) item {
                    SurfaceCard(Modifier.clickable(enabled = state.canApply, onClick = onCleanUp)) {
                        SettingRow(
                            MiuixIcons.Delete, stringResource(R.string.stale_title),
                            pluralStringResource(R.plurals.stale_summary, stale.size, stale.size),
                            enabled = state.canApply, summaryColor = MiuixTheme.colorScheme.error,
                        ) { ForwardArrow() }
                    }
                }
                items(state.credentialApps, key = { it.packageName }) { app ->
                    val primary = app.packageName in primaryPackages
                    val tags = listOfNotNull(
                        primaryTag.takeIf { primary },
                        passwordOnlyTag.takeIf { !app.providesPasskeys },
                        systemAppTag.takeIf { app.isSystem },
                    )
                    SurfaceCard {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            AppLabel(app, primary, tags, Modifier.weight(1f))
                            Switch(
                                app.packageName in enabledPackages, { onToggle(app, it) },
                                enabled = state.canApply && !primary,
                            )
                        }
                    }
                }
                if (state.credentialApps.isEmpty() && !state.loading) item {
                    SurfaceCard {
                        Text(stringResource(R.string.empty_title), fontWeight = FontWeight.Medium, modifier = Modifier.padding(24.dp))
                    }
                }
                item { SheetNote(stringResource(R.string.providers_sheet_hint)) }
            }
            TextButton(stringResource(R.string.close), onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DiagnosticsSheet(diagnostics: Diagnostics?, onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    // Keep showing the last content while the sheet animates out.
    var last by remember { mutableStateOf<Diagnostics?>(null) }
    LaunchedEffect(diagnostics) { if (diagnostics != null) last = diagnostics }
    val current = diagnostics ?: last
    val title = stringResource(if (current?.service == DiagnosticService.AUTOFILL) R.string.diag_autofill else R.string.diag_credential)
    val chunks = remember(current?.result) {
        current?.result?.text?.lines()?.chunked(60)?.map { it.joinToString("\n") }.orEmpty()
    }
    AppSheet(diagnostics != null, title, onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val result = current?.result
            val error = current?.error
            when {
                error != null -> Text(error.text(), fontSize = 14.sp, color = MiuixTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                result == null -> Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(size = 22.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.diag_loading), fontSize = 14.sp)
                }
                else -> {
                    if (result.truncated) SheetNote(stringResource(R.string.diag_truncated), error = true)
                    if (result.text.isBlank()) SheetNote(stringResource(R.string.diag_empty))
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(chunks) { chunk ->
                            SelectionContainer {
                                Text(chunk, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(stringResource(R.string.close), onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    stringResource(R.string.copy), { result?.let { onCopy(it.text) } },
                    modifier = Modifier.weight(1f), enabled = result != null && result.text.isNotEmpty(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun ConfigurationDialog(show: Boolean, state: PasskeyUiState, onDismiss: () -> Unit) {
    OverlayDialog(show = show, title = stringResource(R.string.view_settings), summary = stringResource(R.string.read_hint), onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SelectionContainer {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ConfigurationValue(stringResource(R.string.enabled_providers), state.settings?.enabled, state.settings != null)
                    ConfigurationValue(stringResource(R.string.primary_providers), state.settings?.primary, state.settings != null)
                    ConfigurationValue(stringResource(R.string.autofill_value), state.settings?.autofill, state.settings != null)
                }
            }
            TextButton(stringResource(R.string.close), onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ConfigurationValue(title: String, value: String?, loaded: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = MiuixTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(
            text = when {
                !loaded -> stringResource(R.string.value_unread)
                value == null -> stringResource(R.string.value_not_set)
                value.isEmpty() -> stringResource(R.string.value_empty)
                else -> value.replace(":", "\n\n")
            },
            fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun ConfirmButtons(confirm: String, enabled: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(stringResource(R.string.cancel), onCancel, modifier = Modifier.weight(1f))
        TextButton(confirm, onConfirm, modifier = Modifier.weight(1f), enabled = enabled, colors = ButtonDefaults.textButtonColorsPrimary())
    }
}

@Composable
private fun SelectableCard(selected: Boolean, enabled: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        cornerRadius = 22.dp,
        colors = CardDefaults.defaultColors(color = if (selected) MiuixTheme.colorScheme.tertiaryContainer else MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}

@Composable
private fun AppLabel(app: ProviderApp, highlighted: Boolean, tags: List<String>, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ProviderIcon(app)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(app.label, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = if (highlighted) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
            Text(app.packageName, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (tags.isNotEmpty()) Text(tags.joinToString(" · "), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun systemTag(app: ProviderApp): List<String> = if (app.isSystem) listOf(stringResource(R.string.system_app)) else emptyList()

@Composable
private fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), cornerRadius = 22.dp, colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)) { content() }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier.padding(horizontal = 14.dp, vertical = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.primary)
}

@Composable
private fun Caption(text: String) {
    Text(text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
}

@Composable
private fun SheetNote(text: String, error: Boolean = false) {
    Text(
        text, modifier = Modifier.padding(8.dp), fontSize = 12.sp,
        color = if (error) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean = true,
    summaryColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface)
            Text(summary, fontSize = 13.sp, color = summaryColor ?: MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        trailing()
    }
}

@Composable
private fun IconTile(icon: ImageVector, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(MiuixTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.53f), tint = MiuixTheme.colorScheme.primary)
    }
}

@Composable
private fun ProviderIcon(provider: ProviderApp?) {
    val bitmap = provider?.icon
    if (bitmap != null) {
        Image(remember(bitmap) { bitmap.asImageBitmap() }, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
    } else {
        IconTile(MiuixIcons.Lock, size = 48.dp)
    }
}

@Composable
private fun SelectedMark() {
    val primary = MiuixTheme.colorScheme.primary
    val foreground = MiuixTheme.colorScheme.onPrimary
    Canvas(Modifier.size(22.dp)) {
        drawCircle(primary)
        drawLine(foreground, Offset(size.width * 0.26f, size.height * 0.51f), Offset(size.width * 0.43f, size.height * 0.68f), 2.dp.toPx(), StrokeCap.Round)
        drawLine(foreground, Offset(size.width * 0.43f, size.height * 0.68f), Offset(size.width * 0.76f, size.height * 0.32f), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun ForwardArrow() {
    Icon(MiuixIcons.ChevronForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
private fun RowDivider() {
    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(0.5.dp).background(MiuixTheme.colorScheme.dividerLine))
}

@Preview(locale = "zh", showBackground = true)
@Composable
private fun PasskeyPreview() {
    PasskeyTheme {
        PasskeyScreen(PasskeyUiState(loading = false), PasskeyActions())
    }
}
