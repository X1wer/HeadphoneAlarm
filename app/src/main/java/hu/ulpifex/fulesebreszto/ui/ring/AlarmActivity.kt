package hu.ulpifex.fulesebreszto.ui.ring

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.ulpifex.fulesebreszto.alarm.AlarmRingService
import hu.ulpifex.fulesebreszto.alarm.RingSessionState
import hu.ulpifex.fulesebreszto.data.ThemeMode
import hu.ulpifex.fulesebreszto.domain.OneShotAction
import hu.ulpifex.fulesebreszto.domain.SwipeAction
import hu.ulpifex.fulesebreszto.ui.theme.FulesTheme

/**
 * Full-screen ringing UI, shown over the lock screen via the notification's full-screen
 * intent. Back does not silence the alarm; only dismiss/snooze (gesture, buttons,
 * accessibility actions or notification actions) do.
 */
class AlarmActivity : ComponentActivity() {

    private val action = OneShotAction()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }

        // The session may not be published yet when the full-screen intent opens this screen;
        // AlarmRingScreen waits briefly for it and closes itself if nothing is ringing.
        setContent {
            val ui by RingSessionState.state.collectAsStateWithLifecycle()
            FulesTheme(ThemeMode.DARK) {
                AlarmRingScreen(
                    ui = ui,
                    onAction = ::perform,
                    onFinished = ::finish,
                )
            }
        }
    }

    private fun perform(swipe: SwipeAction) {
        if (!action.tryConsume()) return
        val serviceAction = when (swipe) {
            SwipeAction.DISMISS -> AlarmRingService.ACTION_DISMISS
            SwipeAction.SNOOZE -> AlarmRingService.ACTION_SNOOZE
        }
        AlarmRingService.sendAction(this, serviceAction)
    }
}
