package hu.ulpifex.fulesebreszto.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import hu.ulpifex.fulesebreszto.domain.TextLang
import hu.ulpifex.fulesebreszto.domain.TimeFormat

/** The language chosen in the app's settings. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    HUNGARIAN("hu"),
    ENGLISH("en"),
}

/**
 * In-app language handling. Android 13+ has native per-app languages (LocaleManager), which
 * apply to every component including the alarm service. On Android 10–12 the choice is
 * stored locally and applied by wrapping the activity/service contexts ([wrap]) and by
 * reading notification texts through [localized].
 *
 * English is the default resource set, so any device language other than Hungarian shows
 * English unless Hungarian is chosen here.
 */
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY = "language"

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selected(context: Context): AppLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) return AppLanguage.SYSTEM
            return fromTag(locales[0].language)
        }
        return fromTag(runCatching { prefs(context).getString(KEY, null) }.getOrNull())
    }

    fun select(activity: Activity, language: AppLanguage) {
        prefs(activity).edit().putString(KEY, language.tag).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system recreates the activities itself.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        } else {
            activity.recreate()
        }
    }

    /** Applies the stored language on Android 10–12; a no-op on 13+. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = runCatching { prefs(base).getString(KEY, null) }.getOrNull() ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return base.createConfigurationContext(config)
    }

    /** A context whose resources use the app language, for texts built outside activities. */
    fun localized(context: Context): Context = wrap(context)

    fun textLang(context: Context): TextLang =
        if (localized(context).resources.configuration.locales[0].language == "hu") TextLang.HU else TextLang.EN

    fun timeFormat(context: Context): TimeFormat = TimeFormat.of(textLang(context))

    private fun fromTag(tag: String?): AppLanguage =
        AppLanguage.entries.firstOrNull { it.tag != null && it.tag == tag } ?: AppLanguage.SYSTEM
}
