package hu.ulpifex.fulesebreszto.domain

import kotlin.math.abs

/**
 * DISMISS = swipe up, SNOOZE = swipe down, CONTINUE = swipe left or right (only while a Zengő
 * playlist is playing: the alarm closes and the music keeps playing in Zengő).
 */
enum class SwipeAction { DISMISS, SNOOZE, CONTINUE }

/** Live feedback while the finger is down. [progress] is 0..1 towards the threshold. */
data class SwipeProgress(val direction: SwipeAction?, val progress: Float, val dy: Float, val dx: Float = 0f)

/**
 * Swipe recogniser for the ringing screen. It is independent of where the gesture starts,
 * requires a minimum travel, rejects diagonal motion, and reports at most one action per touch
 * sequence. Horizontal swipes count only while [allowHorizontal] says so; otherwise they are
 * rejected like any other ambiguous motion.
 */
class SwipeGestureDetector(
    private val thresholdPx: Float,
    private val dominance: Float = 1.5f,
    private val allowHorizontal: () -> Boolean = { false },
) {
    init {
        require(thresholdPx > 0f)
        require(dominance >= 1f)
    }

    private var startX = 0f
    private var startY = 0f
    private var tracking = false
    private var fired = false

    fun onDown(x: Float, y: Float) {
        startX = x
        startY = y
        tracking = true
        fired = false
    }

    fun onMove(x: Float, y: Float): SwipeProgress {
        if (!tracking) return SwipeProgress(null, 0f, 0f)
        val dx = x - startX
        val dy = y - startY
        val direction = directionOf(dx, dy)
        val travel = if (direction == SwipeAction.CONTINUE) abs(dx) else abs(dy)
        val progress = if (direction == null) 0f else (travel / thresholdPx).coerceIn(0f, 1f)
        return SwipeProgress(direction, progress, dy, dx)
    }

    /** Returns the action to perform, or null for taps, short or ambiguous moves. */
    fun onUp(x: Float, y: Float): SwipeAction? {
        if (!tracking || fired) return null
        tracking = false
        val dx = x - startX
        val dy = y - startY
        val direction = directionOf(dx, dy) ?: return null
        val travel = if (direction == SwipeAction.CONTINUE) abs(dx) else abs(dy)
        if (travel < thresholdPx) return null
        fired = true
        return direction
    }

    fun onCancel() {
        tracking = false
    }

    private fun directionOf(dx: Float, dy: Float): SwipeAction? = when {
        dy != 0f && abs(dy) >= abs(dx) * dominance -> if (dy < 0) SwipeAction.DISMISS else SwipeAction.SNOOZE
        dx != 0f && abs(dx) >= abs(dy) * dominance && allowHorizontal() -> SwipeAction.CONTINUE
        else -> null
    }
}

/**
 * Guarantees that the ringing screen sends at most one action, even if the gesture, a
 * button and an accessibility action race each other.
 */
class OneShotAction {
    private var consumed = false

    @Synchronized
    fun tryConsume(): Boolean {
        if (consumed) return false
        consumed = true
        return true
    }

    val isConsumed: Boolean @Synchronized get() = consumed
}
