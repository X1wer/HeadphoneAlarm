package hu.ulpifex.fulesebreszto.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import hu.ulpifex.fulesebreszto.domain.Alarm
import hu.ulpifex.fulesebreszto.domain.AlarmColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A frame whose four edges are zig-zag lines (teeth pointing outwards). Used as both the shape and
 * the border of Zengő playlist alarms, so they are recognisable at a glance.
 */
class ZigZagShape(private val tooth: Dp = 12.dp, private val depth: Dp = 5.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = with(density) { depth.toPx() }
        val t = with(density) { tooth.toPx() }
        val left = d
        val top = d
        val right = size.width - d
        val bottom = size.height - d
        val path = Path().apply {
            moveTo(left, top)
            zigzag(t, left, top, right, top) { x, _ -> x to 0f }
            zigzag(t, right, top, right, bottom) { _, y -> size.width to y }
            zigzag(t, right, bottom, left, bottom) { x, _ -> x to size.height }
            zigzag(t, left, bottom, left, top) { _, y -> 0f to y }
            close()
        }
        return Outline.Generic(path)
    }

    /** Teeth of width [toothPx] between (x0,y0) and (x1,y1); [peak] maps a tooth's middle to its outer tip. */
    private fun Path.zigzag(
        toothPx: Float,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        peak: (Float, Float) -> Pair<Float, Float>,
    ) {
        val length = max(abs(x1 - x0), abs(y1 - y0))
        val count = max(1, (length / toothPx).roundToInt())
        for (i in 0 until count) {
            val mid = (i + 0.5f) / count
            val end = (i + 1f) / count
            val (px, py) = peak(x0 + (x1 - x0) * mid, y0 + (y1 - y0) * mid)
            lineTo(px, py)
            lineTo(x0 + (x1 - x0) * end, y0 + (y1 - y0) * end)
        }
    }
}

/** How an alarm is framed in the list and the editor preview. */
data class AlarmFrame(val shape: Shape, val border: BorderStroke, val container: Color, val accent: Color, val padding: PaddingValues)

val LoudRed = Color(AlarmColors.RED)

@Composable
fun alarmFrame(alarm: Alarm, enabled: Boolean = alarm.enabled): AlarmFrame {
    val accent = Color(alarm.displayColor)
    val base = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
    val container = if (enabled) lerp(base, accent, 0.12f) else base
    val shape: Shape = if (alarm.isPlaylist) ZigZagShape() else RoundedCornerShape(24.dp)
    val border = when {
        // Loud alarms always get a thick red frame, whatever colour they are given.
        alarm.isLoud -> BorderStroke(4.dp, if (enabled) LoudRed else LoudRed.copy(alpha = 0.5f))
        alarm.isPlaylist -> BorderStroke(2.dp, accent.copy(alpha = if (enabled) 1f else 0.45f))
        else -> BorderStroke(1.5.dp, accent.copy(alpha = if (enabled) 0.85f else 0.35f))
    }
    val extra = if (alarm.isPlaylist) 6.dp else 0.dp
    return AlarmFrame(
        shape = shape,
        border = border,
        container = container,
        accent = accent,
        padding = PaddingValues(start = 20.dp + extra, end = 16.dp + extra, top = 14.dp + extra, bottom = 14.dp + extra),
    )
}
