package hu.ulpifex.fulesebreszto.integration

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import hu.ulpifex.fulesebreszto.domain.ZengoMode

/** An installed build of the Zengő (Orotund) music player that offers its playlists to alarm apps. */
data class ZengoApp(val packageName: String) {
    val authority: String get() = packageName + AUTHORITY_SUFFIX
    val permission: String get() = "$packageName.permission.ALARM_PLAYLISTS"
    val isDebugBuild: Boolean get() = packageName.endsWith(".debug")

    companion object {
        const val AUTHORITY_SUFFIX = ".alarmplaylists"
        val PACKAGES = listOf("io.github.x1wer.szabadzene", "io.github.x1wer.szabadzene.debug")

        fun forAuthority(authority: String): ZengoApp? =
            PACKAGES.firstOrNull { it + AUTHORITY_SUFFIX == authority }?.let(::ZengoApp)
    }
}

data class ZengoPlaylist(val id: Long, val name: String, val trackCount: Int, val isFavorites: Boolean)

data class ZengoTrack(val id: Long, val title: String, val artist: String?, val durationMs: Long, val audioUri: String)

/**
 * Reads Zengő's playlists through its read-only provider (contract: Zengő `integration/AlarmBridge`).
 * Every call fails soft: when Zengő is missing, the permission was revoked or the phone is still
 * locked after a reboot (Zengő's data is not available before the first unlock), it returns null.
 */
class ZengoClient(private val context: Context) {

    fun installed(): List<ZengoApp> = ZengoApp.PACKAGES.map(::ZengoApp).filter { app ->
        context.packageManager.resolveContentProvider(app.authority, 0) != null
    }

    fun hasPermission(app: ZengoApp): Boolean =
        ContextCompat.checkSelfPermission(context, app.permission) == PackageManager.PERMISSION_GRANTED

    fun playlists(app: ZengoApp): List<ZengoPlaylist>? = query(uri(app.authority, "playlists")) { c ->
        ZengoPlaylist(
            id = c.getLong(c.getColumnIndexOrThrow("_id")),
            name = c.getString(c.getColumnIndexOrThrow("name")).orEmpty(),
            trackCount = c.getInt(c.getColumnIndexOrThrow("track_count")),
            isFavorites = c.getInt(c.getColumnIndexOrThrow("is_favorites")) == 1,
        )
    }

    fun tracks(authority: String, playlistId: Long): List<ZengoTrack>? =
        query(uri(authority, "playlists", playlistId.toString(), "tracks")) { c ->
            ZengoTrack(
                id = c.getLong(c.getColumnIndexOrThrow("_id")),
                title = c.getString(c.getColumnIndexOrThrow("title")).orEmpty(),
                artist = c.getString(c.getColumnIndexOrThrow("artist")),
                durationMs = c.getLong(c.getColumnIndexOrThrow("duration_ms")),
                audioUri = c.getString(c.getColumnIndexOrThrow("audio_uri")),
            )
        }

    fun mode(authority: String): ZengoMode? = query(uri(authority, "playback_mode")) { c ->
        ZengoMode(
            shuffle = c.getInt(c.getColumnIndexOrThrow("shuffle")) == 1,
            repeat = c.getString(c.getColumnIndexOrThrow("repeat")).orEmpty(),
        )
    }?.firstOrNull()

    private fun <T> query(uri: Uri, row: (android.database.Cursor) -> T): List<T>? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) add(row(cursor)) }
        }
    }.getOrNull()

    private fun uri(authority: String, vararg path: String): Uri =
        path.fold(Uri.Builder().scheme("content").authority(authority)) { b, p -> b.appendPath(p) }.build()
}
