package hu.ulpifex.fulesebreszto.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import hu.ulpifex.fulesebreszto.FulesApp
import hu.ulpifex.fulesebreszto.alarm.RingSessionState
import hu.ulpifex.fulesebreszto.data.AppSettings
import hu.ulpifex.fulesebreszto.i18n.AppLocale
import hu.ulpifex.fulesebreszto.ui.ring.AlarmActivity
import hu.ulpifex.fulesebreszto.ui.theme.FulesTheme
import kotlinx.coroutines.launch

/** Tracks whether an app screen is visible (lets the service open the alarm screen directly). */
object AppVisibility {
    @Volatile
    var isForeground: Boolean = false
        internal set
}

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* state is re-read on resume */ }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as FulesApp).container

        lifecycleScope.launch {
            val settings = container.settings.current()
            container.engine.ensureFirstRunAlarm(container.alarms.count() == 0 && !settings.firstRunDone, settings)
            container.settings.markFirstRunDone()
            // Safety net: re-arm everything whenever the app is opened.
            container.engine.rescheduleAll("app start")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            savedInstanceState == null
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            FulesTheme(settings.themeMode) {
                AppNavigation(container)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isForeground = true
    }

    override fun onResume() {
        super.onResume()
        if (RingSessionState.isRinging) {
            startActivity(Intent(this, AlarmActivity::class.java))
        }
    }

    override fun onStop() {
        AppVisibility.isForeground = false
        super.onStop()
    }
}
