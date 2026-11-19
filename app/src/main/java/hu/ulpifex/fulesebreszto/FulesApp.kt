package hu.ulpifex.fulesebreszto

import android.app.Application
import android.content.Context
import hu.ulpifex.fulesebreszto.alarm.AlarmEngine
import hu.ulpifex.fulesebreszto.alarm.EngineLabels
import hu.ulpifex.fulesebreszto.alarm.AlarmNotifications
import hu.ulpifex.fulesebreszto.alarm.AndroidAlarmClock
import hu.ulpifex.fulesebreszto.audio.HeadphoneMonitor
import hu.ulpifex.fulesebreszto.data.AlarmRepository
import hu.ulpifex.fulesebreszto.data.CustomSoundRepository
import hu.ulpifex.fulesebreszto.data.DeviceRepository
import hu.ulpifex.fulesebreszto.data.DiagnosticLog
import hu.ulpifex.fulesebreszto.data.SettingsRepository
import hu.ulpifex.fulesebreszto.data.db.AppDatabase
import hu.ulpifex.fulesebreszto.i18n.AppLocale
import hu.ulpifex.fulesebreszto.integration.ZengoClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class FulesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifications.createChannels()
    }
}

/** Manual dependency container; everything persistent lives in device-protected storage. */
class AppContainer(app: Context) {
    val appContext: Context = app
    private val storage: Context = app.createDeviceProtectedStorageContext()

    /** Resources in the app language (may differ from the system language on Android 10–12). */
    fun localized(): Context = AppLocale.localized(appContext)

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val clock: Clock = LiveZoneClock
    val log = DiagnosticLog(storage)
    val database = AppDatabase.build(storage)
    val settings = SettingsRepository(storage)
    val alarms = AlarmRepository(database.alarmDao())
    val sounds = CustomSoundRepository(database.customSoundDao())
    val devices = DeviceRepository(database.knownDeviceDao())
    val notifications = AlarmNotifications(app)
    val alarmClock = AndroidAlarmClock(app, log::log)
    val headphones = HeadphoneMonitor(app, devices, appScope)
    val zengo = ZengoClient(app)

    val engine = AlarmEngine(
        store = alarms,
        gateway = alarmClock,
        notifier = notifications,
        clock = clock,
        settings = { settings.current() },
        labels = {
            val res = localized()
            EngineLabels(
                defaultLabel = res.getString(R.string.default_alarm_label),
                copySuffix = res.getString(R.string.copy_suffix),
                testLabel = res.getString(R.string.test_alarm_label),
            )
        },
        log = log::log,
    )
}

/** System clock that reads the default zone on every call, so zone changes apply at once. */
object LiveZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}
