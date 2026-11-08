package hu.ulpifex.fulesebreszto

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hu.ulpifex.fulesebreszto.alarm.RingUiState
import hu.ulpifex.fulesebreszto.domain.RingState
import hu.ulpifex.fulesebreszto.domain.SilentReason
import hu.ulpifex.fulesebreszto.domain.SwipeAction
import hu.ulpifex.fulesebreszto.ui.ring.AlarmRingScreen
import hu.ulpifex.fulesebreszto.ui.theme.FulesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmRingScreenGestureTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<SwipeAction>()

    private val ui = RingUiState(
        alarmIds = listOf(1),
        label = "Munkahelyi ébresztés",
        alarmTime = "06:30",
        state = RingState.VIBRATING_ONLY,
        silentReason = SilentReason.NO_HEADPHONES,
        audioDevice = null,
        systemVolumeZero = false,
        usedFallbackSound = false,
        snoozeMinutes = 10,
    )

    private fun show() = compose.setContent {
        FulesTheme { AlarmRingScreen(ui = ui, onAction = { actions += it }, onFinished = {}) }
    }

    @Test
    fun swipeUpDismissesOnce() {
        show()
        compose.onNodeWithTag("ringRoot").performTouchInput { swipeUp(startY = bottom * 0.75f, endY = bottom * 0.25f) }
        compose.waitForIdle()
        assertEquals(listOf(SwipeAction.DISMISS), actions)
        compose.onNodeWithText("Ébresztés leállítva").assertExists()
        // A second gesture is ignored by the screen (one action per ringing screen).
        compose.onNodeWithTag("ringRoot").performTouchInput { swipeDown(startY = bottom * 0.25f, endY = bottom * 0.75f) }
        compose.waitForIdle()
        assertEquals(listOf(SwipeAction.DISMISS), actions)
    }

    @Test
    fun swipeDownSnoozes() {
        show()
        compose.onNodeWithTag("ringRoot").performTouchInput { swipeDown(startY = bottom * 0.3f, endY = bottom * 0.8f) }
        compose.waitForIdle()
        assertEquals(listOf(SwipeAction.SNOOZE), actions)
        compose.onNodeWithText("Szundi – 10 perc").assertExists()
    }

    @Test
    fun shortOrSidewaysMovesDoNothing() {
        show()
        compose.onNodeWithTag("ringRoot").performTouchInput { swipe(center, center + Offset(0f, -60f)) }
        compose.onNodeWithTag("ringRoot").performTouchInput { swipe(center + Offset(-300f, 0f), center + Offset(300f, -40f)) }
        compose.waitForIdle()
        assertEquals(emptyList<SwipeAction>(), actions)
    }

    @Test
    fun gestureCanStartAnywhereIncludingNearTheTop() {
        show()
        compose.onNodeWithTag("ringRoot").performTouchInput { swipeDown(startY = top + 40f, endY = top + 40f + 600f) }
        compose.waitForIdle()
        assertEquals(listOf(SwipeAction.SNOOZE), actions)
    }

    @Test
    fun buttonsAndAccessibilityActionsWork() {
        show()
        compose.onNodeWithText("Leállítás").performClick()
        compose.waitForIdle()
        assertEquals(listOf(SwipeAction.DISMISS), actions)
    }

    @Test
    fun accessibilityCustomActionSnoozes() {
        show()
        val customActions = compose.onNodeWithTag("ringRoot").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { customActions.first { it.label == "Szundi" }.action() }
        compose.waitForIdle()
        assertEquals(listOf(SwipeAction.SNOOZE), actions)
    }
}
