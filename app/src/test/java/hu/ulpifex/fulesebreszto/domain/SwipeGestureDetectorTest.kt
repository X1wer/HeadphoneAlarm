package hu.ulpifex.fulesebreszto.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeGestureDetectorTest {

    // 110 dp at 2.75x density ≈ 302 px.
    private val threshold = 302f
    private fun detector() = SwipeGestureDetector(threshold)

    private fun swipe(d: SwipeGestureDetector, x0: Float, y0: Float, x1: Float, y1: Float): SwipeAction? {
        d.onDown(x0, y0)
        d.onMove((x0 + x1) / 2, (y0 + y1) / 2)
        d.onMove(x1, y1)
        return d.onUp(x1, y1)
    }

    @Test
    fun `upward swipe dismisses, downward swipe snoozes`() {
        assertEquals(SwipeAction.DISMISS, swipe(detector(), 500f, 1500f, 520f, 1100f))
        assertEquals(SwipeAction.SNOOZE, swipe(detector(), 500f, 800f, 480f, 1200f))
    }

    @Test
    fun `start position does not matter`() {
        listOf(50f, 600f, 1900f).forEach { y ->
            assertEquals(SwipeAction.DISMISS, swipe(detector(), 300f, y, 300f, y - 400f))
            assertEquals(SwipeAction.SNOOZE, swipe(detector(), 300f, y, 300f, y + 400f))
        }
    }

    @Test
    fun `taps and short moves do nothing`() {
        assertNull(swipe(detector(), 500f, 1000f, 500f, 1000f))
        assertNull(swipe(detector(), 500f, 1000f, 505f, 940f))
        assertNull(swipe(detector(), 500f, 1000f, 500f, 1000f - threshold + 1f))
    }

    @Test
    fun `exactly the threshold is accepted`() {
        assertEquals(SwipeAction.DISMISS, swipe(detector(), 500f, 1000f, 500f, 1000f - threshold))
    }

    @Test
    fun `horizontal or diagonal motion is rejected`() {
        assertNull(swipe(detector(), 100f, 1000f, 700f, 1000f))
        assertNull(swipe(detector(), 100f, 1000f, 450f, 650f)) // 45 degrees
        assertNull(swipe(detector(), 100f, 1000f, 400f, 600f)) // |dy| = 1.33 |dx| < 1.5
        assertEquals(SwipeAction.DISMISS, swipe(detector(), 100f, 1000f, 300f, 600f)) // |dy| = 2 |dx|
    }

    @Test
    fun `dragging past the threshold and back cancels`() {
        val d = detector()
        d.onDown(500f, 1000f)
        val far = d.onMove(500f, 600f)
        assertEquals(SwipeAction.DISMISS, far.direction)
        assertEquals(1f, far.progress)
        d.onMove(500f, 950f)
        assertNull(d.onUp(500f, 990f))
    }

    @Test
    fun `progress reports direction and fraction`() {
        val d = detector()
        d.onDown(0f, 1000f)
        val half = d.onMove(0f, 1000f + threshold / 2)
        assertEquals(SwipeAction.SNOOZE, half.direction)
        assertEquals(0.5f, half.progress, 0.001f)
        assertNull(d.onMove(400f, 1010f).direction)
    }

    @Test
    fun `one gesture produces at most one action`() {
        val d = detector()
        d.onDown(500f, 1500f)
        assertEquals(SwipeAction.DISMISS, d.onUp(500f, 1000f))
        assertNull(d.onUp(500f, 900f)) // repeated up without a new down
        assertNull(d.onUp(500f, 2000f))
    }

    @Test
    fun `sideways swipe continues only while allowed`() {
        var allowed = true
        val d = SwipeGestureDetector(threshold, allowHorizontal = { allowed })
        assertEquals(SwipeAction.CONTINUE, swipe(d, 100f, 1000f, 100f + threshold, 1010f))
        assertEquals(SwipeAction.CONTINUE, swipe(d, 900f, 1000f, 400f, 990f))
        // Diagonal, short or vertical moves are unchanged.
        assertNull(swipe(d, 100f, 1000f, 450f, 650f))
        assertNull(swipe(d, 100f, 1000f, 100f + threshold - 1f, 1000f))
        assertEquals(SwipeAction.DISMISS, swipe(d, 500f, 1500f, 520f, 1100f))
        allowed = false
        assertNull(swipe(d, 100f, 1000f, 700f, 1000f))
    }

    @Test
    fun `sideways progress follows the horizontal travel`() {
        val d = SwipeGestureDetector(threshold, allowHorizontal = { true })
        d.onDown(500f, 1000f)
        val half = d.onMove(500f - threshold / 2, 1000f)
        assertEquals(SwipeAction.CONTINUE, half.direction)
        assertEquals(0.5f, half.progress, 0.001f)
        assertEquals(-threshold / 2, half.dx, 0.001f)
    }

    @Test
    fun `cancelled gesture produces nothing`() {
        val d = detector()
        d.onDown(500f, 1500f)
        d.onCancel()
        assertNull(d.onUp(500f, 900f))
    }

    @Test
    fun `one shot action guard lets only the first action through`() {
        val guard = OneShotAction()
        assertFalse(guard.isConsumed)
        assertTrue(guard.tryConsume())
        assertFalse(guard.tryConsume())
        assertFalse(guard.tryConsume())
        assertTrue(guard.isConsumed)
    }
}
