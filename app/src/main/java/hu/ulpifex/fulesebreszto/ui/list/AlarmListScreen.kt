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
import hu.ulpifex.fulesebreszto.domain.HuFormat
import hu.ulpifex.fulesebreszto.ui.PermissionStatus
import hu.ulpifex.fulesebreszto.ui.Permissions
import hu.ulpifex.fulesebreszto.ui.components.AppIcons
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
                    Text("FülesÉbresztő", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Beállítások")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Új ébresztés") },
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
            item { NextAlarmHeader(state.nextTitle, state.nextCountdown) }
            if (permissions.criticalMissing.isNotEmpty()) {
                item { PermissionWarning(permissions.criticalMissing, onOpen = onSettings) }
            }
            if (state.loaded && state.alarms.isEmpty()) {
                item {
                    Text(
                        "Még nincs ébresztés. Az „Új ébresztés” gombbal hozhatsz létre egyet.",
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
private fun PermissionWarning(missing: List<String>, onOpen: () -> Unit) {
    val colors = LocalStatusColors.current
    Surface(color = colors.warningContainer, contentColor = colors.onWarningContainer, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Hiányzó engedély: ${missing.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpen) { Text("Megnyitás") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(
    alarm: Alarm,
    use24h: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val time = HuFormat.time(alarm.hour, alarm.minute, use24h)
    val repeat = HuFormat.repeatSummary(alarm)
    val contentAlpha = if (alarm.enabled) 1f else 0.55f

    Box {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (alarm.enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClickLabel = "Szerkesztés",
                    onLongClickLabel = "Másolás vagy törlés",
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "$time, ${alarm.label}, $repeat"
                    stateDescription = if (alarm.enabled) "Bekapcsolva" else "Kikapcsolva"
                    customActions = listOf(
                        CustomAccessibilityAction("Másolás") { onDuplicate(); true },
                        CustomAccessibilityAction("Törlés") { onDelete(); true },
                    )
                },
        ) {
            Row(Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).alpha(contentAlpha)) {
                    Text(time, style = TimeTextStyle)
                    Text(alarm.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(repeat, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    if (alarm.snoozeUntil != null) {
                        Text("Szundizik", style = MaterialTheme.typography.bodySmall, color = LocalStatusColors.current.warning)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(AppIcons.Headphones, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Fejhallgatós ébresztés · ${alarm.soundName}",
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
                    modifier = Modifier.semantics { contentDescription = "$time ${alarm.label} bekapcsolása" },
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Másolás") }, onClick = { menuOpen = false; onDuplicate() })
            DropdownMenuItem(text = { Text("Törlés") }, onClick = { menuOpen = false; onDelete() })
        }
    }
}

@Composable
fun DeleteAlarmDialog(alarm: Alarm, use24h: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Törlöd az ébresztést?") },
        text = { Text("${HuFormat.time(alarm.hour, alarm.minute, use24h)} – ${alarm.label}\nA törlés nem vonható vissza.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Törlés", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Mégse") } },
    )
}
