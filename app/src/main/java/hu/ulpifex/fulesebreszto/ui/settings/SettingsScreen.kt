package hu.ulpifex.fulesebreszto.ui.settings

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
import hu.ulpifex.fulesebreszto.audio.OutputKind
import hu.ulpifex.fulesebreszto.audio.OutputPolicy
import hu.ulpifex.fulesebreszto.data.ThemeMode
import hu.ulpifex.fulesebreszto.ui.PermissionStatus
import hu.ulpifex.fulesebreszto.ui.Permissions
import hu.ulpifex.fulesebreszto.ui.components.AppIcons
import hu.ulpifex.fulesebreszto.ui.components.HeadphoneStatusChip
import hu.ulpifex.fulesebreszto.ui.components.SectionTitle
import hu.ulpifex.fulesebreszto.ui.components.SettingsCard
import hu.ulpifex.fulesebreszto.ui.components.StatusDot
import hu.ulpifex.fulesebreszto.ui.edit.CustomMinutesDialog
import hu.ulpifex.fulesebreszto.ui.edit.MinutesDropdown
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
    var customSnooze by remember { mutableStateOf(false) }
    var customRing by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissions = Permissions.read(context, container) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    DisposableEffect(Unit) { onDispose { vm.stopHeadphoneTest() } }

    val policy = remember(overview.allowedKeys) { OutputPolicy(overview.allowedKeys) }
    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beállítások") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vissza") }
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
            SectionTitle("Hangkimenet")
            HeadphoneStatusChip(overview.status, Modifier.fillMaxWidth())
            Spacer(Modifier.padding(4.dp))
            SettingsCard {
                val current = overview.strategy?.joinToString { "${it.name} (${it.kind.label})" }
                ListItem(
                    headlineContent = { Text("Aktuális médiakimenet") },
                    supportingContent = {
                        Text(current ?: "A rendszer ezen az Android-verzión nem adja meg (Android 13 alatt)")
                    },
                    colors = itemColors,
                )
                ListItem(
                    headlineContent = { Text("Médiahangerő") },
                    supportingContent = {
                        val low = overview.mediaVolumePercent < 15
                        Text(
                            "${overview.mediaVolumePercent}%" + if (low) " – túl alacsony, az ébresztés halk vagy néma lehet" else "",
                            color = if (low) LocalStatusColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = itemColors,
                )
                ListItem(
                    headlineContent = { Text("Kimenetváltozás figyelése") },
                    supportingContent = {
                        Text(
                            if (overview.monitoring) "Aktív – eseményalapú (AudioDeviceCallback, BECOMING_NOISY, útvonalváltás)"
                            else "Indítás…",
                        )
                    },
                    leadingContent = { StatusDot(ok = overview.monitoring) },
                    colors = itemColors,
                )
            }

            SectionTitle("Csatlakoztatott kimenetek")
            SettingsCard {
                if (overview.connected.isEmpty()) {
                    ListItem(headlineContent = { Text("Nincs kimenet") }, colors = itemColors)
                }
                overview.connected.forEach { device ->
                    val allowed = policy.isAllowed(device)
                    ListItem(
                        headlineContent = { Text(device.name) },
                        supportingContent = {
                            Text(
                                device.kind.label + " · " + when {
                                    allowed -> "ébresztésre engedélyezett"
                                    device.kind.needsApproval -> "nincs engedélyezve"
                                    else -> "ébresztéshez soha nem használt"
                                },
                            )
                        },
                        leadingContent = { Icon(iconFor(device.kind), contentDescription = null) },
                        trailingContent = { StatusDot(ok = allowed, warn = device.kind.needsApproval) },
                        colors = itemColors,
                    )
                }
            }

            SectionTitle("Engedélyezett Bluetooth- és USB-eszközök")
            Text(
                "A Bluetooth-kimenet lehet hangszóró vagy autós kihangosító is, ezért csak az itt bekapcsolt eszközökön szólhat ébresztés. " +
                    "Vezetékes és USB-C fejhallgatók automatikusan engedélyezettek.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SettingsCard {
                if (known.isEmpty()) {
                    ListItem(
                        headlineContent = { Text("Még nem csatlakozott Bluetooth- vagy USB-audioeszköz") },
                        supportingContent = { Text("Csatlakoztasd a fejhallgatót, és itt megjelenik.") },
                        colors = itemColors,
                    )
                }
                known.forEach { device ->
                    val connected = overview.connected.any { it.key == device.key }
                    val selectable = device.kind.needsApproval
                    ListItem(
                        headlineContent = { Text(device.name) },
                        supportingContent = {
                            Text(
                                device.kind.label + " · " + (if (connected) "csatlakoztatva" else "nincs csatlakoztatva") +
                                    if (!selectable) " · hangszóró, nem engedélyezhető" else "",
                            )
                        },
                        leadingContent = { Icon(iconFor(device.kind), contentDescription = null) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectable) {
                                    Switch(
                                        checked = device.allowed,
                                        onCheckedChange = { vm.setDeviceAllowed(device, it) },
                                        modifier = Modifier.semantics { contentDescription = "${device.name} engedélyezése" },
                                    )
                                }
                                TextButton(onClick = { vm.forgetDevice(device) }) { Text("Törlés") }
                            }
                        },
                        colors = itemColors,
                    )
                }
            }
            Spacer(Modifier.padding(4.dp))
            HeadphoneTestButton(preview.playingRef != null, preview.message, vm::testHeadphones, vm::stopHeadphoneTest)

            // ---- Alarm defaults ----
            SectionTitle("Ébresztés – alapértékek új ébresztésekhez")
            SettingsCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Alapértelmezett hangerő: ${settings.defaultVolume}%", style = MaterialTheme.typography.bodyLarge)
                    var sliderValue by remember(settings.defaultVolume) { mutableFloatStateOf(settings.defaultVolume.toFloat()) }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { vm.setDefaultVolume((sliderValue / 5f).roundToInt() * 5) },
                        valueRange = 0f..100f,
                        steps = 19,
                        modifier = Modifier.semantics { contentDescription = "Alapértelmezett hangerő" },
                    )
                    Spacer(Modifier.padding(4.dp))
                    MinutesDropdown("Alapértelmezett szundi", settings.defaultSnoozeMinutes, listOf(5, 10, 15),
                        vm::setDefaultSnooze) { customSnooze = true }
                    Spacer(Modifier.padding(4.dp))
                    MinutesDropdown("Maximális ébresztési idő", settings.defaultMaxRingMinutes, listOf(1, 3, 5, 10, 15, 20, 30),
                        vm::setDefaultMaxRing) { customRing = true }
                }
                ListItem(
                    headlineContent = { Text("Rezgés fejhallgató mellett is") },
                    supportingContent = { Text("Alapbeállítás új ébresztésekhez") },
                    trailingContent = { Switch(checked = settings.defaultVibrateWithHeadphones, onCheckedChange = vm::setDefaultVibrate) },
                    colors = itemColors,
                )
            }

            // ---- Appearance ----
            SectionTitle("Megjelenés")
            SettingsCard {
                ThemeMode.entries.forEach { mode ->
                    ListItem(
                        headlineContent = { Text(mode.label) },
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
                    headlineContent = { Text("24 órás időformátum") },
                    trailingContent = { Switch(checked = settings.use24h, onCheckedChange = vm::setUse24h) },
                    colors = itemColors,
                )
            }

            // ---- Permissions ----
            SectionTitle("Rendszerjogosultságok")
            SettingsCard {
                PermissionRow("Pontos ébresztés", permissions.exactAlarms,
                    "Szükséges a pontos időben történő megszólaláshoz.") { Permissions.open(context, Permissions.exactAlarmIntent(context)) }
                PermissionRow("Értesítések", permissions.notifications,
                    "Az ébresztés értesítése, leállítás és szundi gombok.") { Permissions.open(context, Permissions.notificationsIntent(context)) }
                PermissionRow("Teljes képernyős ébresztés", permissions.fullScreen,
                    "Lezárt képernyőn az ébresztőképernyő megjelenítése.") { Permissions.open(context, Permissions.fullScreenIntent(context)) }
                PermissionRow("Rezgés", permissions.hasVibrator,
                    if (permissions.hasVibrator) "Engedélyezve (telepítéskor megadott jogosultság)." else "A készüléken nincs rezgőmotor.",
                    onOpen = null)
                PermissionRow(
                    "Akkumulátor-optimalizálás",
                    permissions.batteryUnrestricted,
                    if (permissions.batteryUnrestricted) "Nincs korlátozva." else
                        "Az ébresztések pontos időzítésűek (setAlarmClock), így az optimalizálás általában nem akadályozza őket. " +
                            "Egyes gyártói energiatakarékos módok mégis késleltethetnek – ilyenkor vedd ki az alkalmazást az optimalizálás alól.",
                    warnOnly = true,
                ) { Permissions.open(context, Permissions.batteryIntent()) }
            }

            // ---- Testing ----
            SectionTitle("Tesztelés")
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeadphoneTestButton(preview.playingRef != null, null, vm::testHeadphones, vm::stopHeadphoneTest)
                    OutlinedButton(onClick = vm::testVibration, modifier = Modifier.fillMaxWidth()) {
                        Icon(AppIcons.Vibration, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Rezgés tesztelése")
                    }
                    OutlinedButton(onClick = { vm.testAlarm(1) }, modifier = Modifier.fillMaxWidth()) { Text("Próbaébresztés 1 perc múlva") }
                    OutlinedButton(onClick = { vm.testAlarm(2) }, modifier = Modifier.fillMaxWidth()) { Text("Próbaébresztés 2 perc múlva") }
                    TextButton(onClick = { showLog = true }, modifier = Modifier.fillMaxWidth()) { Text("Diagnosztikai napló (csak helyben)") }
                }
            }

            // ---- About ----
            SectionTitle("Névjegy")
            SettingsCard {
                ListItem(
                    headlineContent = { Text("FülesÉbresztő") },
                    supportingContent = {
                        Text("Verzió: ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE} build, ${BuildConfig.VERSION_CODE}. kiadás)")
                    },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    colors = itemColors,
                )
                Text(
                    "Az ébresztő hangja kizárólag engedélyezett, ellenőrzött fejhallgatón szólal meg. Ha a hangkimenet nem igazolható, " +
                        "a telefon csak rezeg. Az alkalmazás teljesen offline működik: nincs internet-hozzáférése, reklámja, analitikája vagy fiókja.\n\n" +
                        "Licencek: az alkalmazás forráskódja MIT-licences. Felhasznált könyvtárak: Android Jetpack (AndroidX, Jetpack Compose, " +
                        "Material 3, Room, DataStore, Navigation) és Kotlin/kotlinx.coroutines – mind Apache License 2.0. " +
                        "A beépített ébresztőhangokat a projekt saját szkriptje állítja elő (tools/generate_sounds.py), harmadik fél jogaitól mentesek.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }

    if (customSnooze) {
        CustomMinutesDialog("Egyéni szundi", settings.defaultSnoozeMinutes, 1..60,
            onConfirm = { vm.setDefaultSnooze(it); customSnooze = false }, onDismiss = { customSnooze = false })
    }
    if (customRing) {
        CustomMinutesDialog("Maximális ébresztési idő", settings.defaultMaxRingMinutes, 1..60,
            onConfirm = { vm.setDefaultMaxRing(it); customRing = false }, onDismiss = { customRing = false })
    }
    if (showLog) {
        val lines = remember { vm.diagnostics() }
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Diagnosztikai napló") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        if (lines.isEmpty()) "Üres." else lines.takeLast(150).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text("Bezárás") } },
            dismissButton = { TextButton(onClick = { vm.clearDiagnostics(); showLog = false }) { Text("Napló törlése") } },
        )
    }
}

@Composable
private fun HeadphoneTestButton(playing: Boolean, message: String?, onTest: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        FilledTonalButton(onClick = if (playing) onStop else onTest, modifier = Modifier.fillMaxWidth()) {
            Icon(AppIcons.Headphones, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (playing) "Teszthang leállítása" else "Fejhallgatóhang tesztelése")
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
            if (onOpen != null && !granted) TextButton(onClick = onOpen) { Text("Megnyitás") }
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
