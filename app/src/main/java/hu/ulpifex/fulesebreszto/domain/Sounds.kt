package hu.ulpifex.fulesebreszto.domain

/**
 * Reference to an alarm sound: either one of the bundled tones or a user-picked
 * document (Storage Access Framework content URI).
 */
sealed interface SoundRef {
    val serialized: String

    data class BuiltIn(val key: String) : SoundRef {
        override val serialized: String get() = PREFIX_BUILTIN + key
    }

    data class Custom(val uri: String) : SoundRef {
        override val serialized: String get() = uri
    }

    /** A playlist (or the Favourites, id 0) of the Zengő music player, read through its provider. */
    data class Zengo(val authority: String, val playlistId: Long) : SoundRef {
        override val serialized: String get() = "$PREFIX_ZENGO$authority/$playlistId"
    }

    companion object {
        private const val PREFIX_BUILTIN = "builtin:"
        private const val PREFIX_ZENGO = "zengo:"

        fun parse(value: String?): SoundRef = when {
            value.isNullOrBlank() -> BuiltInSounds.DEFAULT.ref
            value.startsWith(PREFIX_ZENGO) -> {
                val rest = value.removePrefix(PREFIX_ZENGO)
                val authority = rest.substringBeforeLast('/', "")
                val id = rest.substringAfterLast('/').toLongOrNull()
                if (authority.isNotBlank() && id != null && id >= 0) Zengo(authority, id) else BuiltInSounds.DEFAULT.ref
            }
            value.startsWith(PREFIX_BUILTIN) -> {
                val key = value.removePrefix(PREFIX_BUILTIN)
                if (BuiltInSounds.find(key) != null) BuiltIn(key) else BuiltInSounds.DEFAULT.ref
            }
            else -> Custom(value)
        }
    }
}

/**
 * A bundled tone. [displayName] is only a stored fallback; screens always show the
 * translated name for the key.
 */
data class BuiltInSound(val key: String, val displayName: String, val rawName: String) {
    val ref: SoundRef.BuiltIn get() = SoundRef.BuiltIn(key)
}

object BuiltInSounds {
    val CLASSIC = BuiltInSound("classic", "Classic alarm", "classic_alarm")
    val SOFT = BuiltInSound("soft", "Soft melody", "soft_melody")
    val BEEP = BuiltInSound("beep", "Simple beeps", "simple_beep")
    val CHIME = BuiltInSound("chime", "Morning chime", "morning_chime")

    val ALL = listOf(CLASSIC, SOFT, BEEP, CHIME)
    val DEFAULT = CLASSIC

    /** The tone used when a custom file cannot be opened or decoded. */
    val FALLBACK = CLASSIC

    fun find(key: String): BuiltInSound? = ALL.firstOrNull { it.key == key }
}

/** Outcome of resolving which sound will actually be played. */
data class ResolvedSound(val ref: SoundRef, val usedFallback: Boolean)

/**
 * Decides what to play: the configured sound when it is readable, otherwise the built-in
 * fallback. [isReadable] is injected so the decision can be tested without Android.
 */
object SoundResolver {
    fun resolve(configured: SoundRef, isReadable: (SoundRef.Custom) -> Boolean): ResolvedSound =
        when (configured) {
            is SoundRef.BuiltIn -> ResolvedSound(configured, usedFallback = false)
            // Playlists are expanded into their songs before playback; a bare one cannot be played.
            is SoundRef.Zengo -> ResolvedSound(BuiltInSounds.FALLBACK.ref, usedFallback = true)
            is SoundRef.Custom ->
                if (runCatching { isReadable(configured) }.getOrDefault(false)) {
                    ResolvedSound(configured, usedFallback = false)
                } else {
                    ResolvedSound(BuiltInSounds.FALLBACK.ref, usedFallback = true)
                }
        }
}
