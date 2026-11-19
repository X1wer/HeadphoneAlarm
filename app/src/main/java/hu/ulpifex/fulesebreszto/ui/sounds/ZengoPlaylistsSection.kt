package hu.ulpifex.fulesebreszto.ui.sounds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.domain.SoundRef
import hu.ulpifex.fulesebreszto.integration.ZengoApp
import hu.ulpifex.fulesebreszto.integration.ZengoPlaylist
import hu.ulpifex.fulesebreszto.ui.Permissions
import hu.ulpifex.fulesebreszto.ui.components.AppIcons
import hu.ulpifex.fulesebreszto.ui.components.SectionTitle
import hu.ulpifex.fulesebreszto.ui.components.ZigZagShape

/** One installed Zengő build as seen by the sound picker. */
data class ZengoSection(val app: ZengoApp, val granted: Boolean, val playlists: List<ZengoPlaylist>?)

/** The "Zengő playlists" part of the sound picker (inside its LazyColumn). */
fun LazyListScope.zengoPlaylists(
    sections: List<ZengoSection>,
    denied: Set<String>,
    currentRef: String?,
    onGrant: (ZengoApp) -> Unit,
    onSelected: (ref: String, name: String) -> Unit,
) {
    item { SectionTitle(stringResource(R.string.zengo_section)) }
    if (sections.isEmpty()) {
        item {
            Text(
                stringResource(R.string.zengo_not_installed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        return
    }
    sections.forEach { section ->
        if (sections.size > 1 || section.app.isDebugBuild) {
            item(key = "label-${section.app.packageName}") {
                Text(
                    stringResource(R.string.zengo_name) + if (section.app.isDebugBuild) stringResource(R.string.zengo_debug_suffix) else "",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
        }
        when {
            !section.granted -> item(key = "grant-${section.app.packageName}") {
                val context = LocalContext.current
                Column(Modifier.padding(bottom = 8.dp)) {
                    FilledTonalButton(onClick = { onGrant(section.app) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(AppIcons.Headphones, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.zengo_grant))
                    }
                    Text(
                        stringResource(R.string.zengo_grant_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (section.app.permission in denied) {
                        Text(stringResource(R.string.zengo_denied), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                        TextButton(onClick = { Permissions.open(context, Permissions.appDetails(context)) }) {
                            Text(stringResource(R.string.open))
                        }
                    }
                }
            }
            section.playlists == null -> item(key = "na-${section.app.packageName}") {
                Text(stringResource(R.string.zengo_unavailable), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            else -> {
                val lists = section.playlists
                if (lists.none { !it.isFavorites }) {
                    item(key = "none-${section.app.packageName}") {
                        Text(stringResource(R.string.zengo_no_lists), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                items(lists, key = { "${section.app.packageName}-${it.id}" }) { playlist ->
                    val ref = SoundRef.Zengo(section.app.authority, playlist.id).serialized
                    val name = if (playlist.isFavorites) stringResource(R.string.favorites_name) else playlist.name
                    val shape = ZigZagShape(tooth = 10.dp, depth = 3.dp)
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { Text(pluralStringResource(R.plurals.zengo_song_count, playlist.trackCount, playlist.trackCount)) },
                        leadingContent = { RadioButton(selected = currentRef == ref, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .padding(vertical = 3.dp)
                            .clip(if (playlist.trackCount > 0) shape else RoundedCornerShape(16.dp))
                            .selectable(
                                selected = currentRef == ref,
                                enabled = playlist.trackCount > 0,
                                role = Role.RadioButton,
                                onClick = { onSelected(ref, name) },
                            ),
                    )
                }
            }
        }
    }
}
