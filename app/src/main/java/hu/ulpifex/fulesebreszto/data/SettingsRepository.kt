package hu.ulpifex.fulesebreszto.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) {
    DARK("Sötét téma"),
    LIGHT("Világos téma"),
    SYSTEM("Rendszertéma"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val use24h: Boolean = true,
    val defaultVolume: Int = 60,
    val defaultSnoozeMinutes: Int = 10,
    val defaultMaxRingMinutes: Int = 10,
    val defaultVibrateWithHeadphones: Boolean = false,
    val firstRunDone: Boolean = false,
)

class SettingsRepository(storageContext: Context) {

    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { storageContext.preferencesDataStoreFile("settings") },
    )

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            themeMode = p[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.DARK,
            use24h = p[USE_24H] ?: true,
            defaultVolume = (p[VOLUME] ?: 60).coerceIn(0, 100),
            defaultSnoozeMinutes = (p[SNOOZE] ?: 10).coerceIn(1, 60),
            defaultMaxRingMinutes = (p[MAX_RING] ?: 10).coerceIn(1, 60),
            defaultVibrateWithHeadphones = p[VIBRATE] ?: false,
            firstRunDone = p[FIRST_RUN_DONE] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setUse24h(value: Boolean) = store.edit { it[USE_24H] = value }
    suspend fun setDefaultVolume(value: Int) = store.edit { it[VOLUME] = value.coerceIn(0, 100) }
    suspend fun setDefaultSnooze(value: Int) = store.edit { it[SNOOZE] = value.coerceIn(1, 60) }
    suspend fun setDefaultMaxRing(value: Int) = store.edit { it[MAX_RING] = value.coerceIn(1, 60) }
    suspend fun setDefaultVibrate(value: Boolean) = store.edit { it[VIBRATE] = value }
    suspend fun markFirstRunDone() = store.edit { it[FIRST_RUN_DONE] = true }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val USE_24H = booleanPreferencesKey("use_24h")
        val VOLUME = intPreferencesKey("default_volume")
        val SNOOZE = intPreferencesKey("default_snooze")
        val MAX_RING = intPreferencesKey("default_max_ring")
        val VIBRATE = booleanPreferencesKey("default_vibrate_with_headphones")
        val FIRST_RUN_DONE = booleanPreferencesKey("first_run_done")
    }
}
