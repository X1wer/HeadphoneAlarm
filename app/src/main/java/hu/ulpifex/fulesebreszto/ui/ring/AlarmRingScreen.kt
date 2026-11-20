package hu.ulpifex.fulesebreszto.ui.ring

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.ulpifex.fulesebreszto.alarm.FinishKind
import hu.ulpifex.fulesebreszto.alarm.RingUiState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import hu.ulpifex.fulesebreszto.R
import hu.ulpifex.fulesebreszto.ui.rememberTimeFormat
import hu.ulpifex.fulesebreszto.ui.ringStatusText
import hu.ulpifex.fulesebreszto.domain.RingState
import hu.ulpifex.fulesebreszto.domain.SwipeAction
import hu.ulpifex.fulesebreszto.domain.SwipeGestureDetector
import hu.ulpifex.fulesebreszto.ui.components.AppIcons
import hu.ulpifex.fulesebreszto.ui.components.LoudRed
import hu.ulpifex.fulesebreszto.ui.theme.LocalStatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

/** Minimum vertical travel for a swipe to count (spec: ~100–120 dp). */
val SWIPE_THRESHOLD = 110.dp

/** How long the screen waits for the ringing session to be published before closing. */
private const val SESSION_WAIT_MS = 5_000L

@Composable
fun AlarmRingScreen(
    ui: RingUiState?,
    onAction: (SwipeAction) -> Unit,
    onFinished: () -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { SWIPE_THRESHOLD.toPx() }
    // Sideways swipes count only while a Zengő playlist is playing (the music can then stay on).
    val canContinue by rememberUpdatedState(ui?.canContinue == true)
    val detector = remember(thresholdPx) { SwipeGestureDetector(thresholdPx, allowHorizontal = { canContinue }) }
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val dragOffsetX = remember { Animatable(0f) }
    var hint by remember { mutableStateOf<SwipeAction?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var feedback by remember { mutableStateOf<String?>(null) }

    val snoozeMinutes = ui?.snoozeMinutes ?: 10
    val dismissedText = stringResource(R.string.ring_feedback_dismissed)
    val snoozedText = stringResource(R.string.ring_feedback_snoozed, snoozeMinutes)
    val timedOutText = stringResource(R.string.ring_feedback_timed_out)
    val continuedText = stringResource(R.string.ring_feedback_continued)
    val swipeDescription = stringResource(R.string.ring_swipe_cd)
    val dismissLabel = stringResource(R.string.ring_dismiss)
    val snoozeLabel = stringResource(R.string.ring_snooze)
    val continueLabel = stringResource(R.string.ring_continue)
    val fmt = rememberTimeFormat()

    fun trigger(action: SwipeAction) {
        if (feedback != null) return
        if (action == SwipeAction.CONTINUE && !canContinue) return
        feedback = when (action) {
            SwipeAction.DISMISS -> dismissedText
            SwipeAction.SNOOZE -> snoozedText
            SwipeAction.CONTINUE -> continuedText
        }
        onAction(action)
    }

    // Close shortly after the service confirms the end, or after a safety timeout.
    LaunchedEffect(ui?.finished, feedback) {
        val finished = ui?.finished
        if (finished != null && feedback == null) {
            feedback = when (finished) {
                FinishKind.DISMISSED -> dismissedText
                FinishKind.SNOOZED -> snoozedText
                FinishKind.TIMED_OUT -> timedOutText
                FinishKind.CONTINUED -> continuedText
            }
        }
        if (finished != null || feedback != null) {
            delay(if (finished != null) 900 else 2_500)
            onFinished()
        }
    }
    LaunchedEffect(ui == null) {
        if (ui == null) {
            delay(SESSION_WAIT_MS)
            onFinished()
        }
    }

    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(5_000)
        }
    }
    val accent = Color(ui?.colorArgb ?: 0xFF4DD0E1.toInt())
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulseScale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1620), Color(0xFF12293A), Color(0xFF0B1620))))
            .testTag("ringRoot")
            .pointerInput(thresholdPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    detector.onDown(down.position.x, down.position.y)
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change.position
                        if (!change.pressed) break
                        val p = detector.onMove(last.x, last.y)
                        hint = p.direction
                        progress = p.progress
                        val limit = thresholdPx * 1.3f
                        val sideways = p.direction == SwipeAction.CONTINUE
                        scope.launch { dragOffset.snapTo(if (sideways) 0f else p.dy.coerceIn(-limit, limit)) }
                        scope.launch { dragOffsetX.snapTo(if (sideways) p.dx.coerceIn(-limit, limit) else 0f) }
                    }
                    val action = detector.onUp(last.x, last.y)
                    hint = null
                    progress = 0f
                    scope.launch { dragOffset.animateTo(0f, spring()) }
                    scope.launch { dragOffsetX.animateTo(0f, spring()) }
                    if (action != null) trigger(action)
                }
            }
            .semantics {
                contentDescription = swipeDescription
                customActions = listOfNotNull(
                    CustomAccessibilityAction(dismissLabel) { trigger(SwipeAction.DISMISS); true },
                    CustomAccessibilityAction(snoozeLabel) { trigger(SwipeAction.SNOOZE); true },
                    if (canContinue) CustomAccessibilityAction(continueLabel) { trigger(SwipeAction.CONTINUE); true } else null,
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.ring_title), color = Color(0xFF8BE3EE), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
            Text(
                fmt.time(now.hour, now.minute),
                color = Color.White,
                fontSize = 84.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                ui?.label.orEmpty() +
                    if ((ui?.extraAlarms ?: 0) > 0) pluralStringResource(R.plurals.ring_extra, ui?.extraAlarms ?: 0, ui?.extraAlarms ?: 0) else "",
                color = Color(0xFFE1E8EE),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            if (ui != null) AudioStatus(ui)
            ui?.nowPlaying?.let { song ->
                Text(
                    stringResource(R.string.ring_now_playing, song),
                    color = Color(0xFFE1E8EE),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (ui?.inZengo == true) {
                    Text(
                        stringResource(R.string.ring_in_zengo),
                        color = Color(0xFFB7C6D1),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Gesture guidance (the whole screen accepts the gesture).
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                DirectionHint(
                    up = true,
                    text = stringResource(R.string.ring_swipe_up),
                    active = hint == SwipeAction.DISMISS,
                    progress = if (hint == SwipeAction.DISMISS) progress else 0f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canContinue) SideHint(left = true, active = hint == SwipeAction.CONTINUE, progress = progress)
                    Box(
                        Modifier
                            .graphicsLayer {
                                translationY = dragOffset.value
                                translationX = dragOffsetX.value
                            }
                            .scale(if (hint == null) pulse else 1f + progress * 0.15f)
                            .size(132.dp)
                            .background(
                                when {
                                    hint == SwipeAction.DISMISS && progress >= 1f -> Color(0xFF2E7D5B)
                                    hint == SwipeAction.SNOOZE && progress >= 1f -> Color(0xFF2B5C8A)
                                    hint == SwipeAction.CONTINUE && progress >= 1f -> Color(0xFF6A4C93)
                                    else -> accent.copy(alpha = 0.22f)
                                },
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Alarm, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                    }
                    if (canContinue) SideHint(left = false, active = hint == SwipeAction.CONTINUE, progress = progress)
                }
                if (canContinue) {
                    Text(
                        stringResource(R.string.ring_swipe_sideways),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(if (hint == SwipeAction.CONTINUE) 0.6f + 0.4f * progress else 0.55f),
                    )
                }
                DirectionHint(
                    up = false,
                    text = stringResource(R.string.ring_swipe_down, snoozeMinutes),
                    active = hint == SwipeAction.SNOOZE,
                    progress = if (hint == SwipeAction.SNOOZE) progress else 0f,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { trigger(SwipeAction.SNOOZE) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Icon(AppIcons.Snooze, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(snoozeLabel, fontSize = 18.sp)
                }
                Button(
                    onClick = { trigger(SwipeAction.DISMISS) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4DD0E1), contentColor = Color(0xFF00363D)),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Icon(AppIcons.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(dismissLabel, fontSize = 18.sp)
                }
            }
        }

        feedback?.let { message ->
            Box(Modifier.fillMaxSize().background(Color(0xE60B1620)), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF1E3A4C)) {
                    Text(
                        message,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(horizontal = 32.dp, vertical = 24.dp)
                            .semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioStatus(ui: RingUiState) {
    val status = LocalStatusColors.current
    val (icon, color) = when {
        ui.state == RingState.PLAYING_SPEAKER && !ui.systemVolumeZero -> AppIcons.Speaker to LoudRed
        ui.state == RingState.PLAYING_SPEAKER -> AppIcons.Speaker to status.warning
        ui.state == RingState.PLAYING_HEADPHONES && !ui.systemVolumeZero -> AppIcons.Headphones to status.success
        ui.state == RingState.PLAYING_HEADPHONES -> AppIcons.Headphones to status.warning
        ui.state == RingState.TRIGGERED || ui.state == RingState.CHECKING_AUDIO -> AppIcons.Headphones to Color(0xFFB7C6D1)
        ui.silentReason == hu.ulpifex.fulesebreszto.domain.SilentReason.OUTPUT_LOST -> AppIcons.HeadphonesOff to MaterialTheme.colorScheme.error
        else -> AppIcons.Vibration to status.warning
    }
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.16f)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    ringStatusText(LocalContext.current, ui),
                    color = color,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (ui.usedFallbackSound) {
                Text(stringResource(R.string.ring_fallback_sound), color = Color(0xFFB7C6D1),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SideHint(left: Boolean, active: Boolean, progress: Float) {
    Icon(
        if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = Color(0xFFC9B3F2),
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(40.dp)
            .alpha(if (active) 0.6f + 0.4f * progress else 0.55f),
    )
}

@Composable
private fun DirectionHint(up: Boolean, text: String, active: Boolean, progress: Float) {
    val alpha = if (active) 0.6f + 0.4f * progress else 0.55f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(alpha)) {
        if (up) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color(0xFF7DCB8C), modifier = Modifier.size(48.dp))
        }
        Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (!up) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF8ECFF2), modifier = Modifier.size(48.dp))
        }
    }
}
