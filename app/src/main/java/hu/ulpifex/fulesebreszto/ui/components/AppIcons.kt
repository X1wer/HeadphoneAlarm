package hu.ulpifex.fulesebreszto.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small hand-drawn icon set (24×24) for symbols not in material-icons-core. */
object AppIcons {

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(width: Float = 2f, pathBuilder: PathBuilder.() -> Unit) =
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )

    private fun ImageVector.Builder.fill(pathBuilder: PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.Black), pathBuilder = pathBuilder)

    private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y)
        horizontalLineTo(x + w - r)
        arcTo(r, r, 0f, false, true, x + w, y + r)
        verticalLineTo(y + h - r)
        arcTo(r, r, 0f, false, true, x + w - r, y + h)
        horizontalLineTo(x + r)
        arcTo(r, r, 0f, false, true, x, y + h - r)
        verticalLineTo(y + r)
        arcTo(r, r, 0f, false, true, x + r, y)
        close()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        close()
    }

    val Headphones: ImageVector = icon("Headphones") {
        stroke {
            moveTo(4f, 15f)
            verticalLineTo(12f)
            arcTo(8f, 8f, 0f, false, true, 20f, 12f)
            verticalLineTo(15f)
        }
        fill { roundRect(3f, 13f, 5f, 7f, 1.5f) }
        fill { roundRect(16f, 13f, 5f, 7f, 1.5f) }
    }

    val HeadphonesOff: ImageVector = icon("HeadphonesOff") {
        stroke {
            moveTo(4f, 15f)
            verticalLineTo(12f)
            arcTo(8f, 8f, 0f, false, true, 20f, 12f)
            verticalLineTo(15f)
        }
        fill { roundRect(3f, 13f, 5f, 7f, 1.5f) }
        fill { roundRect(16f, 13f, 5f, 7f, 1.5f) }
        stroke(2.2f) {
            moveTo(3f, 3f)
            lineTo(21f, 21f)
        }
    }

    val Alarm: ImageVector = icon("Alarm") {
        stroke { circle(12f, 13f, 7f) }
        stroke {
            moveTo(12f, 9.5f)
            verticalLineTo(13f)
            lineTo(14.5f, 15f)
        }
        stroke {
            moveTo(3.5f, 6f)
            lineTo(6.5f, 3.5f)
        }
        stroke {
            moveTo(20.5f, 6f)
            lineTo(17.5f, 3.5f)
        }
    }

    val Vibration: ImageVector = icon("Vibration") {
        stroke { roundRect(8f, 4f, 8f, 16f, 1.5f) }
        stroke {
            moveTo(4.5f, 8f)
            verticalLineTo(16f)
        }
        stroke {
            moveTo(19.5f, 8f)
            verticalLineTo(16f)
        }
        stroke {
            moveTo(1.5f, 10f)
            verticalLineTo(14f)
        }
        stroke {
            moveTo(22.5f, 10f)
            verticalLineTo(14f)
        }
    }

    val Bluetooth: ImageVector = icon("Bluetooth") {
        stroke {
            moveTo(7f, 7f)
            lineTo(17f, 16f)
            lineTo(12f, 21f)
            verticalLineTo(3f)
            lineTo(17f, 8f)
            lineTo(7f, 17f)
        }
    }

    val Usb: ImageVector = icon("Usb") {
        stroke {
            moveTo(12f, 3f)
            verticalLineTo(17f)
        }
        fill { circle(12f, 19.5f, 2.2f) }
        stroke {
            moveTo(12f, 13f)
            lineTo(7f, 10.5f)
            verticalLineTo(8f)
        }
        stroke {
            moveTo(12f, 11f)
            lineTo(17f, 8.5f)
            verticalLineTo(6.5f)
        }
        stroke {
            moveTo(10f, 5f)
            lineTo(12f, 3f)
            lineTo(14f, 5f)
        }
    }

    val Stop: ImageVector = icon("Stop") {
        fill { roundRect(6f, 6f, 12f, 12f, 2f) }
    }

    val Snooze: ImageVector = icon("Snooze") {
        stroke {
            moveTo(7f, 8f)
            horizontalLineTo(13f)
            lineTo(7f, 16f)
            horizontalLineTo(13f)
        }
        stroke(1.6f) {
            moveTo(15f, 5f)
            horizontalLineTo(19f)
            lineTo(15f, 10f)
            horizontalLineTo(19f)
        }
    }

    val Keyboard: ImageVector = icon("Keyboard") {
        stroke { roundRect(2.5f, 6f, 19f, 12f, 2f) }
        stroke(1.6f) {
            moveTo(6f, 10f); horizontalLineTo(6.5f)
            moveTo(9.5f, 10f); horizontalLineTo(10f)
            moveTo(13.5f, 10f); horizontalLineTo(14f)
            moveTo(17.5f, 10f); horizontalLineTo(18f)
            moveTo(8f, 14.5f); horizontalLineTo(16f)
        }
    }
}
