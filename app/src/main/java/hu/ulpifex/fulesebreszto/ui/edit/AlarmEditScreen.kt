package hu.ulpifex.fulesebreszto.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.domain.Alarm
import androidx.compose.ui.res.stringResource
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.ui.rememberTimeFormat
import hu.ulpifex.fulesebreszto.ui.soundName
import hu.ulpifex.fulesebreszto.domain.WeekDays
import hu.ulpifex.fulesebreszto.ui.components.AppIcons
import hu.ulpifex.fulesebreszto.ui.components.SectionTitle
import hu.ulpifex.fulesebreszto.ui.list.DeleteAlarmDialog
import hu.ulpifex.fulesebreszto.ui.theme.LocalStatusColors
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

private val SNOOZE_PRESETS = listOf(5, 10, 15)
private val RING_PRESETS = listOf(1, 3, 5, 10, 15, 20, 30)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    container: AppContainer,
    alarmId: Long,
    pickedSound: Pair<String, String>?,
    onPickedSoundConsumed: () -> Unit,
    onPickSound: (String) -> Unit,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
) {
    val vm: AlarmEditViewModel = viewModel(key = "edit-$alarmId") { AlarmEditViewModel(container, alarmId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val draft = state.draft
    val fmt = rememberTimeFormat()
    val deletedMessage = stringResource(R.string.msg_deleted)
    var keyboardMode by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var customSnooze by remember { mutableStateOf(false) }
    var customRing by remember { mutableStateOf(false) }

    LaunchedEffect(pickedSound, state.loaded) {
        if (pickedSound != null && state.loaded) {
            vm.setSound(pickedSound.first, pickedSound.second)
            onPickedSoundConsumed()
        }
    }
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is EditEvent.Saved -> onDone(event.message)
                EditEvent.Deleted -> onDone(deletedMessage)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_alarm_cd))
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = vm::save,
                    enabled = state.loaded && !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(56.dp),
                ) { Text(stringResource(R.string.save_alarm), style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // A. Time
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                if (keyboardMode) {
                    val timeState = rememberTimePickerState(draft.hour, draft.minute, is24Hour = true)
                    LaunchedEffect(timeState.hour, timeState.minute) { vm.setTime(timeState.hour, timeState.minute) }
                    TimeInput(state = timeState)
                } else {
                    WheelTimePicker(draft.hour, draft.minute, onChange = vm::setTime)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { keyboardMode = !keyboardMode }) {
                    Icon(AppIcons.Keyboard, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (keyboardMode) R.string.wheel_mode else R.string.keyboard_mode))
                }
            }
            vm.previewNext(draft)?.let { (next, now) ->
                val preview = stringResource(
                    R.string.preview_rings,
                    fmt.relativeDayTime(next, now, state.use24h),
                    fmt.countdown(now.toInstant().toEpochMilli(), next.toInstant().toEpochMilli()),
                )
                Text(preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
            }
            state.error?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp))
            }

            // B. Name
            SectionTitle(stringResource(R.string.section_name))
            OutlinedTextField(
                value = draft.label,
                onValueChange = vm::setLabel,
                label = { Text(stringResource(R.string.section_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // C. Repeat
            SectionTitle(stringResource(R.string.section_repeat))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val selectedText = stringResource(R.string.day_selected)
                val notSelectedText = stringResource(R.string.day_not_selected)
                DayOfWeek.entries.forEach { day ->
                    val selected = day in draft.days
                    FilterChip(
                        selected = selected,
                        onClick = { vm.toggleDay(day) },
                        label = { Text(fmt.shortDay(day)) },
                        modifier = Modifier.semantics {
                            contentDescription = fmt.longDay(day)
                            stateDescription = if (selected) selectedText else notSelectedText
                        },
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.setDays(WeekDays.WORKDAYS) }) { Text(stringResource(R.string.preset_weekdays)) }
                TextButton(onClick = { vm.setDays(WeekDays.WEEKEND) }) { Text(stringResource(R.string.preset_weekend)) }
                TextButton(onClick = { vm.setDays(WeekDays.EVERY_DAY) }) { Text(stringResource(R.string.preset_every_day)) }
                TextButton(onClick = { vm.setDays(WeekDays.NONE) }) { Text(stringResource(R.string.preset_once)) }
            }
            if (draft.days.isEmpty) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.one_shot_title)) },
                    supportingContent = { Text(draft.oneShotDate?.let(fmt::date) ?: stringResource(R.string.choose_date)) },
                    leadingContent = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    trailingContent = { TextButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.date_button)) } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.clickable { showDatePicker = true },
                )
            }

            // D. Sound
            SectionTitle(stringResource(R.string.section_sound))
            ListItem(
                headlineContent = { Text(stringResource(R.string.choose_sound)) },
                supportingContent = { Text(soundName(draft)) },
                leadingContent = { Icon(AppIcons.Headphones, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.open_sound_picker)) { onPickSound(draft.sound.serialized) },
            )

            // E. Volume
            SectionTitle(stringResource(R.string.section_volume))
            val volumeDescription = stringResource(R.string.volume_cd)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = draft.volumePercent.toFloat(),
                    onValueChange = { vm.setVolume((it / 5f).roundToInt() * 5) },
                    valueRange = 0f..100f,
                    steps = 19,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = volumeDescription },
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.percent_value, draft.volumePercent), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp))
            }
            if (draft.volumePercent == 0) {
                Text(stringResource(R.string.volume_zero_warning), color = LocalStatusColors.current.warning,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.volume_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // F. Vibration
            SectionTitle(stringResource(R.string.section_vibration))
            ListItem(
                headlineContent = { Text(stringResource(R.string.vibrate_with_headphones)) },
                supportingContent = { Text(stringResource(R.string.vibrate_note)) },
                leadingContent = { Icon(AppIcons.Vibration, contentDescription = null) },
                trailingContent = {
                    Switch(checked = draft.vibrateWithHeadphones, onCheckedChange = vm::setVibrateWithHeadphones)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )

            // G. Snooze
            SectionTitle(stringResource(R.string.section_snooze))
            MinutesDropdown(
                title = stringResource(R.string.snooze_duration),
                value = draft.snoozeMinutes,
                presets = SNOOZE_PRESETS,
                onSelect = vm::setSnooze,
                onCustom = { customSnooze = true },
            )

            // H. Max duration
            SectionTitle(stringResource(R.string.section_max))
            MinutesDropdown(
                title = stringResource(R.string.max_note),
                value = draft.maxRingMinutes,
                presets = RING_PRESETS,
                onSelect = vm::setMaxRing,
                onCustom = { customRing = true },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        OneShotDateDialog(
            initial = draft.oneShotDate ?: LocalDate.now(),
            onPick = { vm.setDate(it); showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showDelete) {
        DeleteAlarmDialog(draft, state.use24h, fmt, onConfirm = { showDelete = false; vm.delete() }, onDismiss = { showDelete = false })
    }
    if (customSnooze) {
        CustomMinutesDialog(stringResource(R.string.custom_snooze_title), draft.snoozeMinutes, Alarm.MIN_SNOOZE..Alarm.MAX_SNOOZE,
            onConfirm = { vm.setSnooze(it); customSnooze = false }, onDismiss = { customSnooze = false })
    }
    if (customRing) {
        CustomMinutesDialog(stringResource(R.string.custom_max_title), draft.maxRingMinutes, Alarm.MIN_RING..Alarm.MAX_RING,
            onConfirm = { vm.setMaxRing(it); customRing = false }, onDismiss = { customRing = false })
    }
}

@Composable
fun MinutesDropdown(title: String, value: Int, presets: List<Int>, onSelect: (Int) -> Unit, onCustom: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(if (value in presets) R.string.minutes_value else R.string.minutes_value_custom, value),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            presets.forEach { minutes ->
                DropdownMenuItem(text = { Text(stringResource(R.string.minutes_value, minutes)) }, onClick = { open = false; onSelect(minutes) })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.custom_value)) }, onClick = { open = false; onCustom() })
        }
    }
}

@Composable
fun CustomMinutesDialog(title: String, initial: Int, range: IntRange, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(2) },
                label = { Text(stringResource(R.string.minutes_range_label, range.first, range.last)) },
                singleLine = true,
                isError = !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onConfirm) }, enabled = valid) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneShotDateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val dayMs = 86_400_000L
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * dayMs,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis / dayMs >= today.toEpochDay()
            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / dayMs)) } }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
