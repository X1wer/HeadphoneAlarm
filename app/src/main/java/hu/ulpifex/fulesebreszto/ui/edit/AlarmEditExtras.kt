package hu.ulpifex.fulesebreszto.ui.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.AlarmColors
import hu.ulpifex.fulesebreszto.domain.AlarmOutput
import hu.ulpifex.fulesebreszto.domain.PlaylistEnd
import hu.ulpifex.fulesebreszto.domain.PlaylistOrder
import hu.ulpifex.fulesebreszto.integration.ZengoTrack
import hu.ulpifex.fulesebreszto.ui.components.AppIcons
import hu.ulpifex.fulesebreszto.ui.components.LoudRed

/** Headphones-only vs. loud speaker alarm. */
@Composable
fun OutputSelector(output: AlarmOutput, onSelect: (AlarmOutput) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutputOption(
            selected = output == AlarmOutput.HEADPHONES,
            title = stringResource(R.string.output_headphones),
            description = stringResource(R.string.output_headphones_desc),
            accent = MaterialTheme.colorScheme.primary,
            thick = false,
            icon = { Icon(AppIcons.Headphones, contentDescription = null) },
            onClick = { onSelect(AlarmOutput.HEADPHONES) },
        )
        OutputOption(
            selected = output == AlarmOutput.SPEAKER,
            title = stringResource(R.string.output_speaker),
            description = stringResource(R.string.output_speaker_desc),
            accent = LoudRed,
            thick = true,
            icon = { Icon(AppIcons.Speaker, contentDescription = null, tint = LoudRed) },
            onClick = { onSelect(AlarmOutput.SPEAKER) },
        )
    }
}

@Composable
private fun OutputOption(
    selected: Boolean,
    title: String,
    description: String,
    accent: Color,
    thick: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(if (thick) 3.dp else 2.dp, accent) else null,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
            icon()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Colour circles; the first one ("Automatic") follows the output mode (teal / red). */
@Composable
fun ColorPicker(alarm: Alarm, onSelect: (Int?) -> Unit) {
    val names = stringArrayResource(R.array.alarm_color_names)
    val selectedText = stringResource(R.string.day_selected)
    val autoLabel = stringResource(R.string.color_auto)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ColorDot(
            color = Color(AlarmColors.defaultFor(alarm.output)),
            selected = alarm.color == null,
            label = autoLabel,
            selectedText = selectedText,
            showLetter = true,
            onClick = { onSelect(null) },
        )
        AlarmColors.PALETTE.forEachIndexed { index, argb ->
            ColorDot(
                color = Color(argb),
                selected = alarm.color == argb,
                label = names.getOrElse(index) { "" },
                selectedText = selectedText,
                showLetter = false,
                onClick = { onSelect(argb) },
            )
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, label: String, selectedText: String, showLetter: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(5.dp)
            .background(color, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                if (selected) stateDescription = selectedText
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black.copy(alpha = 0.75f))
            showLetter -> Text("A", color = Color.Black.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Generic labelled dropdown for enum-like choices. */
@Composable
fun <T> ChoiceDropdown(title: String, value: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(options.firstOrNull { it.first == value }?.second.orEmpty(), style = MaterialTheme.typography.titleMedium)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (option, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

/** Order, end-of-list behaviour and first song of a Zengő playlist alarm. */
@Composable
fun PlaylistOptionsSection(
    alarm: Alarm,
    onOrder: (PlaylistOrder) -> Unit,
    onEnd: (PlaylistEnd) -> Unit,
    onStartTrack: (Long?, String?) -> Unit,
    loadTracks: suspend () -> List<ZengoTrack>?,
) {
    var pickStart by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceDropdown(
            title = stringResource(R.string.playlist_order),
            value = alarm.playlist.order,
            options = listOf(
                PlaylistOrder.FOLLOW_APP to stringResource(R.string.order_follow),
                PlaylistOrder.IN_ORDER to stringResource(R.string.order_in_order),
                PlaylistOrder.SHUFFLE to stringResource(R.string.order_shuffle),
            ),
            onSelect = onOrder,
        )
        ChoiceDropdown(
            title = stringResource(R.string.playlist_end),
            value = alarm.playlist.end,
            options = listOf(
                PlaylistEnd.FOLLOW_APP to stringResource(R.string.end_follow),
                PlaylistEnd.RESTART_LIST to stringResource(R.string.end_restart),
                PlaylistEnd.REPEAT_LAST to stringResource(R.string.end_repeat_last),
                PlaylistEnd.BUILTIN_TONE to stringResource(R.string.end_builtin),
                PlaylistEnd.VIBRATE_ONLY to stringResource(R.string.end_vibrate),
                PlaylistEnd.STOP_ALARM to stringResource(R.string.end_stop),
            ),
            onSelect = onEnd,
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.playlist_start)) },
            supportingContent = {
                Text(
                    alarm.playlist.startTrackTitle ?: stringResource(R.string.playlist_start_default),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.clickable { pickStart = true },
        )
        Text(
            stringResource(R.string.playlist_follow_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (pickStart) {
        StartSongDialog(
            selectedId = alarm.playlist.startTrackId,
            loadTracks = loadTracks,
            onPick = { id, title -> pickStart = false; onStartTrack(id, title) },
            onDismiss = { pickStart = false },
        )
    }
}

@Composable
private fun StartSongDialog(
    selectedId: Long?,
    loadTracks: suspend () -> List<ZengoTrack>?,
    onPick: (Long?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var tracks by remember { mutableStateOf<List<ZengoTrack>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val loaded = loadTracks()
        if (loaded == null) failed = true else tracks = loaded
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_start)) },
        text = {
            when {
                failed -> Text(stringResource(R.string.start_song_unavailable))
                tracks == null -> Text("…")
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        StartSongRow(stringResource(R.string.playlist_start_default), null, selectedId == null) { onPick(null, null) }
                    }
                    items(tracks.orEmpty(), key = { it.id }) { track ->
                        val title = if (track.artist.isNullOrBlank()) track.title else "${track.title} – ${track.artist}"
                        StartSongRow(track.title, track.artist, selectedId == track.id) { onPick(track.id, title) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun StartSongRow(title: String, artist: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!artist.isNullOrBlank()) {
                Text(artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
