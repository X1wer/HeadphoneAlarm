package hu.ulpifex.fulesebreszto.ui.settings

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.BuildConfig
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.audio.OutputKind
import hu.ulpifex.fulesebreszto.audio.OutputPolicy
import hu.ulpifex.fulesebreszto.data.ThemeMode
import hu.ulpifex.fulesebreszto.i18n.AppLanguage
import hu.ulpifex.fulesebreszto.i18n.AppLocale
import hu.ulpifex.fulesebreszto.ui.PermissionStatus
import hu.ulpifex.fulesebreszto.ui.Permissions
import hu.ulpifex.fulesebreszto.ui.components.AppIcons
import hu.ulpifex.fulesebreszto.ui.components.HeadphoneStatusChip
import hu.ulpifex.fulesebreszto.ui.components.SectionTitle
import hu.ulpifex.fulesebreszto.ui.components.SettingsCard
import hu.ulpifex.fulesebreszto.ui.components.StatusDot
import hu.ulpifex.fulesebreszto.ui.deviceName
import hu.ulpifex.fulesebreszto.ui.edit.CustomMinutesDialog
import hu.ulpifex.fulesebreszto.ui.edit.MinutesDropdown
import hu.ulpifex.fulesebreszto.ui.labelRes
import hu.ulpifex.fulesebreszto.ui.theme.LocalStatusColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: SettingsViewModel = viewModel { SettingsViewModel(app, container) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val overview by vm.overview.collectAsStateWithLifecycle()
    val known by vm.knownDevices.collectAsStateWithLifecycle()
    val preview by vm.preview.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var permissions by remember { mutableStateOf(PermissionStatus()) }
    var language by remember { mutableStateOf(AppLocale.selected(context)) }
    var customSnooze by remember { mutableStateOf(false) }
    var customRing by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissions = Permissions.read(context, container)
        language = AppLocale.selected(context)
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    DisposableEffect(Unit) { onDispose { vm.stopHeadphoneTest() } }

    val policy = remember(overview.allowedKeys) { OutputPolicy(overview.allowedKeys) }
    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            // ---- Audio output ----
            SectionTitle(stringResource(R.string.section_audio_output))
            HeadphoneStatusChip(overview.status, Modifier.fillMaxWidth())
            Spacer(Modifier.padding(4.dp))
            SettingsCard {
                val strategy = overview.strategy?.map { "${deviceName(it)} (${stringResource(it.kind.labelRes())})" }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.current_media_output)) },
                    supportingContent = {
                        Text(strategy?.joinToString() ?: stringResource(R.string.current_output_unavailable))
                    },
                    colors = itemColors,
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.media_volume)) },
                    supportingContent = {
                        val low = overview.mediaVolumePercent < 15
                        Text(
                            stringResource(if (low) R.string.media_volume_low else R.string.percent_value, overview.mediaVolumePercent),
                            color = if (low) LocalStatusColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = itemColors,
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.monitoring)) },
                    supportingContent = {
                        Text(stringResource(if (overview.monitoring) R.string.monitoring_active else R.string.monitoring_starting))
                    },
                    leadingContent = { StatusDot(ok = overview.monitoring) },
                    colors = itemColors,
                )
            }

            SectionTitle(stringResource(R.string.connected_outputs))
            SettingsCard {
                if (overview.connected.isEmpty()) {
                    ListItem(headlineContent = { Text(stringResource(R.string.no_outputs)) }, colors = itemColors)
                }
                overview.connected.forEach { device ->
                    val allowed = policy.isAllowed(device)
                    val verdict = stringResource(
                        when {
                            allowed -> R.string.output_allowed
                            device.kind.needsApproval -> R.string.output_not_allowed
                            else -> R.string.output_never
                        },
                    )
                    ListItem(
                        headlineContent = { Text(deviceName(device)) },
                        supportingContent = { Text(stringResource(device.kind.labelRes()) + " · " + verdict) },
                        leadingContent = { Icon(iconFor(device.kind), contentDescription = null) },
                        trailingContent = { StatusDot(ok = allowed, warn = device.kind.needsApproval) },
                        colors = itemColors,
                    )
                }
            }

            SectionTitle(stringResource(R.string.allowed_devices_title))
            Text(
                stringResource(R.string.allowed_devices_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SettingsCard {
                if (known.isEmpty()) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.no_known_devices)) },
                        supportingContent = { Text(stringResource(R.string.no_known_devices_hint)) },
                        colors = itemColors,
                    )
                }
                known.forEach { device ->
                    val connected = overview.connected.any { it.key == device.key }
                    val selectable = device.kind.needsApproval
                    val name = device.name.ifBlank { stringResource(device.kind.labelRes()) }
                    val allowDescription = stringResource(R.string.allow_device_cd, name)
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = {
                            Text(
                                stringResource(device.kind.labelRes()) + " · " +
                                    stringResource(if (connected) R.string.connected else R.string.not_connected) +
                                    if (!selectable) stringResource(R.string.speaker_cannot_allow) else "",
                            )
                        },
                        leadingContent = { Icon(iconFor(device.kind), contentDescription = null) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectable) {
                                    Switch(
                                        checked = device.allowed,
                                        onCheckedChange = { vm.setDeviceAllowed(device, it) },
                                        modifier = Modifier.semantics { contentDescription = allowDescription },
                                    )
                                }
                                TextButton(onClick = { vm.forgetDevice(device) }) { Text(stringResource(R.string.forget)) }
                            }
                        },
                        colors = itemColors,
                    )
                }
            }
            Spacer(Modifier.padding(4.dp))
            HeadphoneTestButton(preview.playingRef != null, preview.message, vm::testHeadphones, vm::stopHeadphoneTest)

            // ---- Alarm defaults ----
            SectionTitle(stringResource(R.string.section_defaults))
            SettingsCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.default_volume, settings.defaultVolume), style = MaterialTheme.typography.bodyLarge)
                    var sliderValue by remember(settings.defaultVolume) { mutableFloatStateOf(settings.defaultVolume.toFloat()) }
                    val sliderDescription = stringResource(R.string.default_volume_cd)
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { vm.setDefaultVolume((sliderValue / 5f).roundToInt() * 5) },
                        valueRange = 0f..100f,
                        steps = 19,
                        modifier = Modifier.semantics { contentDescription = sliderDescription },
                    )
                    Spacer(Modifier.padding(4.dp))
                    MinutesDropdown(stringResource(R.string.default_snooze), settings.defaultSnoozeMinutes, listOf(5, 10, 15),
                        vm::setDefaultSnooze) { customSnooze = true }
                    Spacer(Modifier.padding(4.dp))
                    MinutesDropdown(stringResource(R.string.max_ring), settings.defaultMaxRingMinutes, listOf(1, 3, 5, 10, 15, 20, 30),
                        vm::setDefaultMaxRing) { customRing = true }
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.vibrate_with_headphones)) },
                    supportingContent = { Text(stringResource(R.string.default_vibrate_note)) },
                    trailingContent = { Switch(checked = settings.defaultVibrateWithHeadphones, onCheckedChange = vm::setDefaultVibrate) },
                    colors = itemColors,
                )
            }

            // ---- Appearance and language ----
            SectionTitle(stringResource(R.string.section_appearance))
            SettingsCard {
                ThemeMode.entries.forEach { mode ->
                    ListItem(
                        headlineContent = { Text(stringResource(mode.labelRes())) },
                        leadingContent = { RadioButton(selected = settings.themeMode == mode, onClick = null) },
                        colors = itemColors,
                        modifier = Modifier.selectable(
                            selected = settings.themeMode == mode,
                            role = Role.RadioButton,
                            onClick = { vm.setTheme(mode) },
                        ),
                    )
                }
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.use_24h)) },
                    trailingContent = { Switch(checked = settings.use24h, onCheckedChange = vm::setUse24h) },
                    colors = itemColors,
                )
            }

            SectionTitle(stringResource(R.string.language))
            SettingsCard {
                AppLanguage.entries.forEach { option ->
                    ListItem(
                        headlineContent = { Text(stringResource(option.labelRes())) },
                        leadingContent = { RadioButton(selected = language == option, onClick = null) },
                        colors = itemColors,
                        modifier = Modifier.selectable(
                            selected = language == option,
                            role = Role.RadioButton,
                            onClick = {
                                if (language != option) {
                                    language = option
                                    (context as? Activity)?.let { AppLocale.select(it, option) }
                                }
                            },
                        ),
                    )
                }
            }

            // ---- Permissions ----
            SectionTitle(stringResource(R.string.section_permissions))
            SettingsCard {
                PermissionRow(stringResource(R.string.perm_exact), permissions.exactAlarms, stringResource(R.string.perm_exact_desc)) {
                    Permissions.open(context, Permissions.exactAlarmIntent(context))
                }
                PermissionRow(stringResource(R.string.perm_notifications), permissions.notifications,
                    stringResource(R.string.perm_notifications_desc)) {
                    Permissions.open(context, Permissions.notificationsIntent(context))
                }
                PermissionRow(stringResource(R.string.perm_full_screen), permissions.fullScreen,
                    stringResource(R.string.perm_full_screen_desc)) {
                    Permissions.open(context, Permissions.fullScreenIntent(context))
                }
                PermissionRow(
                    stringResource(R.string.perm_vibration),
                    permissions.hasVibrator,
                    stringResource(if (permissions.hasVibrator) R.string.perm_vibration_ok else R.string.perm_vibration_none),
                    onOpen = null,
                )
                PermissionRow(
                    stringResource(R.string.perm_battery),
                    permissions.batteryUnrestricted,
                    stringResource(if (permissions.batteryUnrestricted) R.string.perm_battery_ok else R.string.perm_battery_note),
                    warnOnly = true,
                ) { Permissions.open(context, Permissions.batteryIntent()) }
            }

            // ---- Testing ----
            SectionTitle(stringResource(R.string.section_testing))
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeadphoneTestButton(preview.playingRef != null, null, vm::testHeadphones, vm::stopHeadphoneTest)
                    OutlinedButton(onClick = vm::testVibration, modifier = Modifier.fillMaxWidth()) {
                        Icon(AppIcons.Vibration, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.test_vibration))
                    }
                    OutlinedButton(onClick = { vm.testAlarm(1) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.test_alarm_1))
                    }
                    OutlinedButton(onClick = { vm.testAlarm(2) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.test_alarm_2))
                    }
                    TextButton(onClick = { showLog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.diagnostics))
                    }
                }
            }

            // ---- About ----
            SectionTitle(stringResource(R.string.section_about))
            SettingsCard {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    supportingContent = {
                        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE, BuildConfig.VERSION_CODE))
                    },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    colors = itemColors,
                )
                Text(
                    stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }

    if (customSnooze) {
        CustomMinutesDialog(stringResource(R.string.custom_snooze_title), settings.defaultSnoozeMinutes, 1..60,
            onConfirm = { vm.setDefaultSnooze(it); customSnooze = false }, onDismiss = { customSnooze = false })
    }
    if (customRing) {
        CustomMinutesDialog(stringResource(R.string.max_ring), settings.defaultMaxRingMinutes, 1..60,
            onConfirm = { vm.setDefaultMaxRing(it); customRing = false }, onDismiss = { customRing = false })
    }
    if (showLog) {
        val lines = remember { vm.diagnostics() }
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text(stringResource(R.string.diagnostics_title)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        if (lines.isEmpty()) stringResource(R.string.diagnostics_empty) else lines.takeLast(150).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text(stringResource(R.string.close)) } },
            dismissButton = {
                TextButton(onClick = { vm.clearDiagnostics(); showLog = false }) { Text(stringResource(R.string.diagnostics_clear)) }
            },
        )
    }
}

@Composable
private fun HeadphoneTestButton(playing: Boolean, message: String?, onTest: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        FilledTonalButton(onClick = if (playing) onStop else onTest, modifier = Modifier.fillMaxWidth()) {
            Icon(AppIcons.Headphones, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (playing) R.string.stop_test else R.string.test_headphones))
        }
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    detail: String,
    warnOnly: Boolean = false,
    onOpen: (() -> Unit)?,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = { StatusDot(ok = granted, warn = warnOnly && !granted) },
        trailingContent = {
            if (onOpen != null && !granted) TextButton(onClick = onOpen) { Text(stringResource(R.string.open)) }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

private fun iconFor(kind: OutputKind) = when {
    kind.isBluetooth -> AppIcons.Bluetooth
    kind == OutputKind.USB_DEVICE || kind == OutputKind.USB_HEADSET -> AppIcons.Usb
    kind.isTrustedByType -> AppIcons.Headphones
    else -> AppIcons.HeadphonesOff
}
