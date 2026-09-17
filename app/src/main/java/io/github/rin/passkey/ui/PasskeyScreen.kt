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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rin.passkey.BuildConfig
import io.github.rin.passkey.PasskeyUiState
import io.github.rin.passkey.R
import io.github.rin.passkey.data.PasskeyProvider
import io.github.rin.passkey.shizuku.ShizukuStatus
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PasskeyScreen(
    state: PasskeyUiState,
    onRefresh: () -> Unit,
    onAuthorize: () -> Unit,
    onOpenShizuku: () -> Unit,
    onGetShizuku: () -> Unit,
    onSelect: (PasskeyProvider) -> Unit,
    onKeepOthersChanged: (Boolean) -> Unit,
    onRestore: () -> Unit,
    onNoticeShown: () -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showConfiguration by rememberSaveable { mutableStateOf(false) }
    var showRestore by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val notice = state.notice?.text()
    val errorMessage = state.error?.text()
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            onNoticeShown()
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
                    IconButton(onClick = onRefresh, enabled = !state.loading && !state.applying) {
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
                Text(
                    text = if (state.loading) stringResource(R.string.scan_loading) else pluralStringResource(R.plurals.apps_found, state.providers.size, state.providers.size),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp),
                )
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
                        TextButton(stringResource(R.string.refresh), onClick = onRefresh, enabled = !state.applying && !state.loading)
                    }
                }
            } }
            item { SectionTitle(stringResource(R.string.section_connection), Modifier.padding(top = 14.dp)) }
            item { ShizukuCard(state.status, onAuthorize, onOpenShizuku, onGetShizuku) }
            item { SectionTitle(stringResource(R.string.section_settings), Modifier.padding(top = 14.dp)) }
            item {
                SurfaceCard {
                    SettingRow(MiuixIcons.Settings, stringResource(R.string.keep_others), stringResource(R.string.keep_others_summary)) {
                        Switch(state.keepOthers, onKeepOthersChanged, enabled = !state.applying)
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
            item {
                SurfaceCard {
                    SettingRow(
                        MiuixIcons.Info, stringResource(R.string.view_settings), stringResource(R.string.view_settings_summary),
                        onClick = { showConfiguration = true },
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
                        Text(stringResource(R.string.help_hyperos), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Text(stringResource(R.string.help_scope), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.footer, BuildConfig.VERSION_NAME), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }

        ProviderPicker(
            show = showPicker,
            state = state,
            onDismiss = { showPicker = false },
            onSelect = { provider -> showPicker = false; onSelect(provider) },
            onRestore = { showPicker = false; showRestore = true },
        )
        OverlayDialog(
            show = showRestore,
            title = stringResource(R.string.restore_original),
            summary = stringResource(R.string.restore_confirm),
            onDismissRequest = { showRestore = false },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(stringResource(R.string.cancel), { showRestore = false }, modifier = Modifier.weight(1f))
                TextButton(
                    stringResource(R.string.restore), { showRestore = false; onRestore() },
                    modifier = Modifier.weight(1f), enabled = state.canApply,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
        ConfigurationDialog(showConfiguration, state) { showConfiguration = false }
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
    SurfaceCard(modifier = Modifier.clickable(enabled = !state.applying, onClick = onClick)) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProviderIcon(provider)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (provider != null) Text(stringResource(R.string.current_active), color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                Text(
                    provider?.packageName ?: stringResource(R.string.choose_hint),
                    fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
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
private fun ProviderPicker(show: Boolean, state: PasskeyUiState, onDismiss: () -> Unit, onSelect: (PasskeyProvider) -> Unit, onRestore: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(show) { if (!show) query = "" }
    val apps = remember(state.providers, query) {
        state.providers.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }
    val containerHeight = LocalWindowInfo.current.containerSize.height
    val maxHeight = with(LocalDensity.current) { containerHeight.toDp() * 0.72f }
    OverlayBottomSheet(
        show = show, title = stringResource(R.string.sheet_title),
        onDismissRequest = onDismiss, backgroundColor = MiuixTheme.colorScheme.surface,
        outsideMargin = DpSize(0.dp, 0.dp), insideMargin = DpSize(16.dp, 12.dp),
        cornerRadius = 30.dp,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.providers.isNotEmpty()) TextField(
                value = query, onValueChange = { query = it }, label = stringResource(R.string.search_apps),
                useLabelAsPlaceholder = true, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (!state.canApply) Text(
                stringResource(if (state.status == ShizukuStatus.READY) R.string.read_before_select else R.string.authorize_to_select),
                fontSize = 13.sp, color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp),
            )
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.hasBackup && query.isEmpty()) item {
                    SurfaceCard(Modifier.clickable(enabled = state.canApply, onClick = onRestore)) {
                        SettingRow(MiuixIcons.Undo, stringResource(R.string.restore_original), stringResource(R.string.restore_summary)) {}
                    }
                }
                if (state.loading) item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                items(apps, key = { it.packageName }) { provider ->
                    val selected = state.selectedProvider?.packageName == provider.packageName
                    Card(
                        modifier = Modifier.fillMaxWidth().selectable(selected, enabled = state.canApply, role = Role.RadioButton, onClick = { onSelect(provider) }),
                        cornerRadius = 22.dp,
                        colors = CardDefaults.defaultColors(color = if (selected) MiuixTheme.colorScheme.tertiaryContainer else MiuixTheme.colorScheme.surfaceContainer),
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ProviderIcon(provider)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(provider.label, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                                Text(provider.packageName, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (provider.isSystem) Text(stringResource(R.string.system_app), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                            if (selected) SelectedMark()
                        }
                    }
                }
                if (apps.isEmpty() && !state.loading) item {
                    SurfaceCard {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(if (query.isEmpty()) R.string.empty_title else R.string.no_search_results), fontWeight = FontWeight.Medium)
                            if (query.isEmpty()) Text(stringResource(R.string.empty_summary), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.providers_scope), modifier = Modifier.padding(8.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                if (state.skippedProviders > 0) item {
                    Text(stringResource(R.string.skipped_providers), modifier = Modifier.padding(8.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.error)
                }
            }
            TextButton(stringResource(R.string.close), onDismiss, modifier = Modifier.fillMaxWidth())
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
private fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), cornerRadius = 22.dp, colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)) { content() }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier.padding(horizontal = 14.dp, vertical = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.primary)
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, summary: String, enabled: Boolean = true, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSurface)
            Text(summary, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        trailing()
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MiuixTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp), tint = MiuixTheme.colorScheme.primary)
    }
}

@Composable
private fun ProviderIcon(provider: PasskeyProvider?) {
    val bitmap = provider?.icon
    if (bitmap != null) {
        Image(remember(bitmap) { bitmap.asImageBitmap() }, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
    } else {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MiuixTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Icon(MiuixIcons.Lock, contentDescription = null, modifier = Modifier.size(26.dp), tint = MiuixTheme.colorScheme.primary)
        }
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
        PasskeyScreen(PasskeyUiState(loading = false), {}, {}, {}, {}, {}, {}, {}, {})
    }
}
