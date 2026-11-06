package io.github.rin.passkey.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rin.passkey.AutofillStatus
import io.github.rin.passkey.BuildConfig
import io.github.rin.passkey.PasskeyUiState
import io.github.rin.passkey.R
import io.github.rin.passkey.data.DiagnosticService
import io.github.rin.passkey.data.ProviderApp
import io.github.rin.passkey.shizuku.ShizukuStatus
import io.github.rin.passkey.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
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

private enum class Page { Main, More }

internal enum class Overlay { PasskeyPicker, AutofillPicker, Providers, Restore, Cleanup, Configuration }

@Composable
fun PasskeyScreen(state: PasskeyUiState, actions: PasskeyActions) {
    var page by rememberSaveable { mutableStateOf(Page.Main) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    // Kept here so the main page returns to the same place after visiting the other page.
    val mainScroll = MiuixScrollBehavior()
    val mainList = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val notice = state.notice?.text()
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            actions.noticeShown()
        }
    }
    BackHandler(enabled = page == Page.More) { page = Page.Main }

    Scaffold(modifier = Modifier.fillMaxSize(), snackbarHost = { SnackbarHost(state = snackbar) }) { _ ->
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState == Page.More
                val enter = slideInHorizontally(tween(260)) { width -> if (forward) width / 5 else -width / 5 } + fadeIn(tween(200))
                val exit = slideOutHorizontally(tween(260)) { width -> if (forward) -width / 5 else width / 5 } + fadeOut(tween(140))
                enter togetherWith exit
            },
            label = "page",
        ) { current ->
            when (current) {
                Page.Main -> MainPage(state, actions, mainScroll, mainList, open = { overlay = it }, openMore = { page = Page.More })
                Page.More -> MorePage(state, actions, open = { overlay = it }, back = { page = Page.Main })
            }
        }
        Overlays(state, actions, overlay, show = { overlay = it })
    }
}

@Composable
private fun MainPage(
    state: PasskeyUiState,
    actions: PasskeyActions,
    scrollBehavior: ScrollBehavior,
    listState: LazyListState,
    open: (Overlay) -> Unit,
    openMore: () -> Unit,
) {
    val busy = state.loading || state.applying
    PageScaffold(
        title = stringResource(R.string.title_main),
        subtitle = statusLine(state),
        scrollBehavior = scrollBehavior,
        listState = listState,
        actions = {
            if (busy) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(size = 20.dp) }
            else ToolbarButton(AppIcons.Refresh, stringResource(R.string.refresh), onClick = actions.refresh)
            ToolbarButton(AppIcons.More, stringResource(R.string.title_more), onClick = openMore)
        },
    ) {
        if (state.status != ShizukuStatus.READY) item { ConnectBanner(state.status, actions) }
        state.error?.let { error ->
            item {
                Banner(
                    AppIcons.Alert, error.text(), MiuixTheme.colorScheme.error, MiuixTheme.colorScheme.errorContainer,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        item {
            GroupCard {
                PasskeySlot(state, onClick = { open(Overlay.PasskeyPicker) })
                RowDivider(AppIconInset)
                AutofillSlot(state, onClick = { open(Overlay.AutofillPicker) })
            }
        }
        item { SectionLabel(stringResource(R.string.section_options)) }
        item {
            GroupCard {
                SwitchRow(
                    AppIcons.Sync, stringResource(R.string.sync_autofill), stringResource(R.string.sync_autofill_summary),
                    state.syncAutofill, enabled = !state.applying, onCheckedChange = actions.setSyncAutofill,
                )
                RowDivider()
                SwitchRow(
                    AppIcons.Layers, stringResource(R.string.keep_others), stringResource(R.string.keep_others_summary),
                    state.keepOthers, enabled = !state.applying, onCheckedChange = actions.setKeepOthers,
                )
            }
        }
        item { SectionLabel(stringResource(R.string.section_manage)) }
        item {
            val stale = state.staleComponents
            GroupCard {
                NavRow(
                    AppIcons.Toggles, stringResource(R.string.providers_sheet_title),
                    summary = if (state.settings == null) stringResource(R.string.manage_providers_unread) else null,
                    value = state.settings?.let { state.enabledPackages.size.toString() },
                    onClick = { open(Overlay.Providers) },
                )
                if (stale.isNotEmpty()) {
                    RowDivider()
                    NavRow(
                        AppIcons.Trash, stringResource(R.string.stale_title),
                        summary = pluralStringResource(R.plurals.stale_summary, stale.size, stale.size),
                        summaryColor = MiuixTheme.colorScheme.error,
                        enabled = state.canApply, onClick = { open(Overlay.Cleanup) },
                    )
                }
                RowDivider()
                NavRow(
                    AppIcons.Restore, stringResource(R.string.restore_original),
                    summary = stringResource(if (state.hasBackup) R.string.restore_summary else R.string.no_backup),
                    enabled = state.canApply && state.hasBackup, onClick = { open(Overlay.Restore) },
                )
            }
        }
    }
}

@Composable
private fun MorePage(state: PasskeyUiState, actions: PasskeyActions, open: (Overlay) -> Unit, back: () -> Unit) {
    val ready = state.status == ShizukuStatus.READY
    val diagnosticSummary = { summary: Int -> if (ready) summary else R.string.diag_requires_shizuku }
    PageScaffold(
        title = stringResource(R.string.title_more),
        scrollBehavior = MiuixScrollBehavior(),
        listState = rememberLazyListState(),
        navigationIcon = { ToolbarButton(AppIcons.Back, stringResource(R.string.back), onClick = back) },
    ) {
        item { SectionLabel(stringResource(R.string.section_diagnostics)) }
        item {
            GroupCard {
                NavRow(AppIcons.ListDetails, stringResource(R.string.view_settings), stringResource(R.string.view_settings_summary)) {
                    open(Overlay.Configuration)
                }
                RowDivider()
                NavRow(
                    AppIcons.Passkey, stringResource(R.string.diag_credential), stringResource(diagnosticSummary(R.string.diag_credential_summary)),
                    enabled = ready,
                ) { actions.openDiagnostics(DiagnosticService.CREDENTIAL) }
                RowDivider()
                NavRow(
                    AppIcons.Autofill, stringResource(R.string.diag_autofill), stringResource(diagnosticSummary(R.string.diag_autofill_summary)),
                    enabled = ready,
                ) { actions.openDiagnostics(DiagnosticService.AUTOFILL) }
            }
        }
        item { SectionLabel(stringResource(R.string.section_tools)) }
        item {
            GroupCard {
                NavRow(AppIcons.Phone, stringResource(R.string.tool_system), stringResource(R.string.tool_system_summary), external = true, onClick = actions.openSystemSettings)
                if (state.credentialApps.any { it.packageName == GOOGLE_PLAY_SERVICES }) {
                    RowDivider()
                    NavRow(AppIcons.Lock, stringResource(R.string.tool_gpm), stringResource(R.string.tool_gpm_summary), external = true, onClick = actions.openPasswordManager)
                }
                RowDivider()
                NavRow(AppIcons.SecurityKey, stringResource(R.string.tool_fido), stringResource(R.string.tool_fido_summary), external = true, onClick = actions.openFidoBridge)
            }
        }
        item { SectionLabel(stringResource(R.string.section_help)) }
        item {
            GroupCard {
                Gap(8.dp)
                listOf(R.string.help_step_one, R.string.help_step_two, R.string.help_step_three).forEachIndexed { index, step ->
                    StepRow(index + 1, stringResource(step))
                }
                Gap(4.dp)
                RowDivider(RowPadding)
                Gap(8.dp)
                listOf(R.string.help_autofill, R.string.help_hyperos, R.string.help_scope).forEach { Note(stringResource(it)) }
                Gap(12.dp)
            }
        }
        item {
            Text(
                stringResource(R.string.footer, BuildConfig.VERSION_NAME),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PageScaffold(
    title: String,
    scrollBehavior: ScrollBehavior,
    listState: LazyListState,
    subtitle: String = "",
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = title, subtitle = subtitle, scrollBehavior = scrollBehavior,
                navigationIcon = navigationIcon, actions = actions,
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            content = content,
        )
    }
}

@Composable
private fun statusLine(state: PasskeyUiState): String = stringResource(
    when {
        state.applying -> R.string.applying
        state.loading -> R.string.status_reading
        state.status == ShizukuStatus.READY -> R.string.shizuku_ready
        else -> R.string.status_disconnected
    },
)

@Composable
private fun ConnectBanner(status: ShizukuStatus, actions: PasskeyActions) {
    val (title, summary) = when (status) {
        ShizukuStatus.UNAUTHORIZED -> R.string.shizuku_unauthorized to R.string.shizuku_unauthorized_summary
        ShizukuStatus.DENIED -> R.string.shizuku_denied to R.string.shizuku_denied_summary
        ShizukuStatus.NOT_INSTALLED -> R.string.shizuku_missing to R.string.shizuku_missing_summary
        ShizukuStatus.UNSUPPORTED -> R.string.shizuku_unsupported to R.string.shizuku_unsupported_summary
        else -> R.string.shizuku_stopped to R.string.shizuku_stopped_summary
    }
    val (label, onClick) = when (status) {
        ShizukuStatus.UNAUTHORIZED -> R.string.authorize to actions.authorize
        ShizukuStatus.NOT_INSTALLED, ShizukuStatus.UNSUPPORTED -> R.string.download_shizuku to actions.getShizuku
        else -> R.string.open_shizuku to actions.openShizuku
    }
    Banner(
        AppIcons.Plug, stringResource(summary), tint = MiuixTheme.colorScheme.primary,
        container = MiuixTheme.colorScheme.surfaceContainer, title = stringResource(title),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().padding(start = RowPadding, end = RowPadding, bottom = RowPadding),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(stringResource(label), color = MiuixTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PasskeySlot(state: PasskeyUiState, onClick: () -> Unit) {
    val provider = state.selectedProvider
    val settings = state.settings
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val (value, color) = when {
        provider != null -> provider.label to MiuixTheme.colorScheme.onSurface
        settings == null -> stringResource(R.string.current_unknown) to muted
        !state.settingsConsistent -> stringResource(R.string.current_inconsistent) to MiuixTheme.colorScheme.error
        settings.primary.isNullOrEmpty() -> stringResource(R.string.current_none) to muted
        settings.primary.contains(':') -> stringResource(R.string.current_multiple) to MiuixTheme.colorScheme.onSurface
        else -> stringResource(R.string.current_unavailable) to MiuixTheme.colorScheme.error
    }
    SlotRow(
        label = stringResource(R.string.section_default), app = provider, placeholder = AppIcons.Passkey,
        value = value, valueColor = color,
        detail = if (provider != null && !provider.providesPasskeys) stringResource(R.string.tag_password_only) else null,
        detailColor = muted, loading = state.loading, enabled = !state.applying, onClick = onClick,
    )
}

@Composable
private fun AutofillSlot(state: PasskeyUiState, onClick: () -> Unit) {
    val status = state.autofillStatus
    val app = (status as? AutofillStatus.App)?.app
    val muted = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val (value, color) = when (status) {
        is AutofillStatus.App -> status.app.label to MiuixTheme.colorScheme.onSurface
        AutofillStatus.Unread -> stringResource(R.string.current_unknown) to muted
        AutofillStatus.CredentialOnly -> stringResource(R.string.autofill_credential_only) to MiuixTheme.colorScheme.onSurface
        is AutofillStatus.Unavailable -> stringResource(R.string.autofill_unavailable) to MiuixTheme.colorScheme.error
        AutofillStatus.None -> stringResource(R.string.autofill_none) to muted
    }
    val passkeyApp = state.selectedProvider
    val mismatch = app != null && passkeyApp != null && passkeyApp.providesAutofill && passkeyApp.packageName != app.packageName
    val (detail, detailColor) = when {
        mismatch -> stringResource(R.string.autofill_mismatch, passkeyApp.label) to warningColor()
        status is AutofillStatus.Unavailable -> status.value to muted
        status == AutofillStatus.CredentialOnly -> stringResource(R.string.autofill_credential_only_summary) to muted
        else -> null to muted
    }
    SlotRow(
        label = stringResource(R.string.section_autofill), app = app, placeholder = AppIcons.Autofill,
        value = value, valueColor = color, detail = detail, detailColor = detailColor,
        loading = state.loading, enabled = !state.applying, onClick = onClick,
    )
}

@Composable
private fun SlotRow(
    label: String,
    app: ProviderApp?,
    placeholder: ImageVector,
    value: String,
    valueColor: Color,
    detail: String?,
    detailColor: Color,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = RowPadding, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIcon(app, placeholder)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, fontSize = 13.sp, lineHeight = 18.sp, color = detailColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (loading) CircularProgressIndicator(size = 18.dp) else TrailingGlyph(AppIcons.Chevron)
    }
}

@Preview(locale = "zh", showBackground = true)
@Composable
private fun PasskeyPreview() {
    PasskeyTheme {
        PasskeyScreen(PasskeyUiState(loading = false), PasskeyActions())
    }
}
