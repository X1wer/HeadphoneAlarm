package hu.ulpifex.fulesebreszto.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.domain.Alarm
import androidx.compose.ui.res.stringResource
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.domain.TimeFormat
import hu.ulpifex.fulesebreszto.ui.rememberTimeFormat
import hu.ulpifex.fulesebreszto.ui.soundName
import java.time.Instant
import hu.ulpifex.fulesebreszto.ui.PermissionStatus
import hu.ulpifex.fulesebreszto.ui.Permissions
import hu.ulpifex.fulesebreszto.ui.components.AppIcons
import hu.ulpifex.fulesebreszto.ui.components.LoudRed
import hu.ulpifex.fulesebreszto.ui.components.alarmFrame
import androidx.compose.ui.draw.clip
import hu.ulpifex.fulesebreszto.ui.components.HeadphoneStatusChip
import hu.ulpifex.fulesebreszto.ui.theme.LocalStatusColors
import hu.ulpifex.fulesebreszto.ui.theme.TimeTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    container: AppContainer,
    incomingMessage: String?,
    onMessageShown: () -> Unit,
    onEdit: (Long) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm: AlarmListViewModel = viewModel { AlarmListViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val fmt = rememberTimeFormat()
    var permissions by remember { mutableStateOf(PermissionStatus()) }
    var pendingDelete by remember { mutableStateOf<Alarm?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissions = Permissions.read(context, container)
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(incomingMessage) {
        if (incomingMessage != null) {
            onMessageShown()
            snackbar.showSnackbar(incomingMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_alarm)) },
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeadphoneStatusChip(state.headphones, Modifier.fillMaxWidth()) }
            item {
                val next = state.next
                val title = if (next == null) {
                    stringResource(R.string.next_none)
                } else {
                    val at = Instant.ofEpochMilli(next.at).atZone(state.now.zone)
                    stringResource(
                        if (next.isSnooze) R.string.next_alarm_snooze else R.string.next_alarm,
                        fmt.relativeDayTime(at, state.now, state.use24h),
                    )
                }
                NextAlarmHeader(title, next?.let { fmt.countdown(state.now.toInstant().toEpochMilli(), it.at) })
            }
            if (permissions.criticalMissing.isNotEmpty()) {
                item { PermissionWarning(permissions.criticalMissing, onOpen = onSettings) }
            }
            if (state.loaded && state.alarms.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_alarms),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(state.alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    use24h = state.use24h,
                    fmt = fmt,
                    onClick = { onEdit(alarm.id) },
                    onToggle = { vm.setEnabled(alarm, it) },
                    onDuplicate = { vm.duplicate(alarm) },
                    onDelete = { pendingDelete = alarm },
                )
            }
        }
    }

    pendingDelete?.let { alarm ->
        DeleteAlarmDialog(
            alarm = alarm,
            use24h = state.use24h,
            fmt = fmt,
            onConfirm = {
                vm.delete(context, alarm)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun NextAlarmHeader(title: String, countdown: String?) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp).semantics(mergeDescendants = true) {}) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (countdown != null) {
            Text(countdown, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionWarning(missing: List<Int>, onOpen: () -> Unit) {
    val colors = LocalStatusColors.current
    Surface(color = colors.warningContainer, contentColor = colors.onWarningContainer, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.missing_permissions, missing.map { stringResource(it) }.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpen) { Text(stringResource(R.string.open)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(
    alarm: Alarm,
    use24h: Boolean,
    fmt: TimeFormat,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val time = fmt.time(alarm.hour, alarm.minute, use24h)
    val repeat = fmt.repeatSummary(alarm)
    val stateOn = stringResource(R.string.alarm_state_on)
    val stateOff = stringResource(R.string.alarm_state_off)
    val copyLabel = stringResource(R.string.copy)
    val deleteLabel = stringResource(R.string.delete)
    val toggleDescription = stringResource(R.string.toggle_alarm_cd, time, alarm.label)
    val contentAlpha = if (alarm.enabled) 1f else 0.55f

    val frame = alarmFrame(alarm)
    val soundLine = when {
        alarm.isPlaylist -> stringResource(R.string.card_playlist_sound, soundName(alarm))
        alarm.isLoud -> stringResource(R.string.card_loud_sound, soundName(alarm))
        else -> stringResource(R.string.headphone_alarm_sound, soundName(alarm))
    }

    Box {
        Surface(
            shape = frame.shape,
            color = frame.container,
            border = frame.border,
            modifier = Modifier
                .fillMaxWidth()
                .clip(frame.shape)
                .combinedClickable(
                    onClickLabel = stringResource(R.string.action_edit),
                    onLongClickLabel = stringResource(R.string.action_copy_or_delete),
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "$time, ${alarm.label}, $repeat"
                    stateDescription = if (alarm.enabled) stateOn else stateOff
                    customActions = listOf(
                        CustomAccessibilityAction(copyLabel) { onDuplicate(); true },
                        CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                    )
                },
        ) {
            Row(Modifier.padding(frame.padding), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).alpha(contentAlpha)) {
                    Text(time, style = TimeTextStyle)
                    Text(alarm.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(repeat, style = MaterialTheme.typography.bodyMedium, color = frame.accent)
                    if (alarm.snoozeUntil != null) {
                        Text(stringResource(R.string.snoozing), style = MaterialTheme.typography.bodySmall, color = LocalStatusColors.current.warning)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(
                            if (alarm.isLoud) AppIcons.Speaker else AppIcons.Headphones,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (alarm.isLoud) LoudRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            soundLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics { contentDescription = toggleDescription },
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(copyLabel) }, onClick = { menuOpen = false; onDuplicate() })
            DropdownMenuItem(text = { Text(deleteLabel) }, onClick = { menuOpen = false; onDelete() })
        }
    }
}

@Composable
fun DeleteAlarmDialog(alarm: Alarm, use24h: Boolean, fmt: TimeFormat, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Text(stringResource(R.string.delete_text, fmt.time(alarm.hour, alarm.minute, use24h), alarm.label)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
