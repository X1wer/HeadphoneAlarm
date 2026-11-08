package hu.ulpifex.fulesebreszto.ui.sounds

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import hu.ulpifex.fulesebreszto.AppContainer
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.ui.builtInSoundNameRes
import hu.ulpifex.fulesebreszto.domain.BuiltInSounds
import hu.ulpifex.fulesebreszto.domain.SoundRef
import hu.ulpifex.fulesebreszto.ui.components.AppIcons
import hu.ulpifex.fulesebreszto.ui.components.SectionTitle
import hu.ulpifex.fulesebreszto.ui.theme.LocalStatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundPickerScreen(
    container: AppContainer,
    currentRef: String?,
    onSelected: (ref: String, name: String) -> Unit,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: SoundPickerViewModel = viewModel { SoundPickerViewModel(app, container) }
    val customs by vm.customSounds.collectAsStateWithLifecycle()
    val preview by vm.preview.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addCustom(uri)
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    DisposableEffect(Unit) { onDispose { vm.stopPreview() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sounds_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                PreviewBanner(preview)
            }
            item { SectionTitle(stringResource(R.string.builtin_sounds)) }
            items(BuiltInSounds.ALL, key = { it.key }) { sound ->
                val name = stringResource(builtInSoundNameRes(sound.key))
                SoundRow(
                    name = name,
                    selected = currentRef == sound.ref.serialized,
                    playing = preview.playingRef == sound.ref.serialized,
                    onSelect = { onSelected(sound.ref.serialized, name) },
                    onPlay = { vm.play(sound.ref) },
                    onStop = vm::stopPreview,
                    onRemove = null,
                )
            }
            item { SectionTitle(stringResource(R.string.custom_sounds)) }
            item {
                FilledTonalButton(
                    onClick = { picker.launch(arrayOf("audio/*", "application/ogg")) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_sound_file))
                }
                Text(
                    stringResource(R.string.formats_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (customs.isEmpty()) {
                item {
                    Text(stringResource(R.string.no_custom_sounds), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            items(customs, key = { it.uri }) { sound ->
                val ref = SoundRef.Custom(sound.uri)
                SoundRow(
                    name = sound.displayName,
                    selected = currentRef == sound.uri,
                    playing = preview.playingRef == sound.uri,
                    onSelect = { onSelected(sound.uri, sound.displayName) },
                    onPlay = { vm.play(ref) },
                    onStop = vm::stopPreview,
                    onRemove = { vm.removeCustom(sound) },
                )
            }
        }
    }
}

@Composable
private fun PreviewBanner(preview: PreviewState) {
    val colors = LocalStatusColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (preview.audible) colors.successContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Headphones, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                preview.message ?: stringResource(R.string.preview_note),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SoundRow(
    name: String,
    selected: Boolean,
    playing: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    ListItem(
        headlineContent = { Text(name) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        trailingContent = {
            Row {
                if (playing) {
                    IconButton(onClick = onStop) { Icon(AppIcons.Stop, contentDescription = stringResource(R.string.stop_preview_cd, name)) }
                } else {
                    IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.play_preview_cd, name)) }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove_sound_cd, name)) }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    )
}
