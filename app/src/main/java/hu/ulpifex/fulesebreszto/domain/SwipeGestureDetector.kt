package hu.ulpifex.fulesebreszto.domain

import kotlin.math.abs

enum class SwipeAction { DISMISS, SNOOZE }

/** Live feedback while the finger is down. [progress] is 0..1 towards the threshold. */
data class SwipeProgress(val direction: SwipeAction?, val progress: Float, val dy: Float)

/**
 * Vertical swipe recogniser for the ringing screen. It is independent of where the gesture
 * starts, requires a minimum vertical travel, rejects mostly-horizontal or diagonal motion,
 * and reports at most one action per touch sequence.
 */
class SwipeGestureDetector(
    private val thresholdPx: Float,
    private val dominance: Float = 1.5f,
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
        val vertical = abs(dy) >= abs(dx) * dominance
        val direction = when {
            !vertical || dy == 0f -> null
            dy < 0 -> SwipeAction.DISMISS
            else -> SwipeAction.SNOOZE
        }
        val progress = if (direction == null) 0f else (abs(dy) / thresholdPx).coerceIn(0f, 1f)
        return SwipeProgress(direction, progress, dy)
    }

    /** Returns the action to perform, or null for taps, short or ambiguous moves. */
    fun onUp(x: Float, y: Float): SwipeAction? {
        if (!tracking || fired) return null
        tracking = false
        val dx = x - startX
        val dy = y - startY
        if (abs(dy) < thresholdPx) return null
        if (abs(dy) < abs(dx) * dominance) return null
        fired = true
        return if (dy < 0) SwipeAction.DISMISS else SwipeAction.SNOOZE
    }

    fun onCancel() {
        tracking = false
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
