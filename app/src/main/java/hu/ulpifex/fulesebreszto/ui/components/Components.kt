package hu.ulpifex.fulesebreszto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import hu.ulpifex.fulesebreszto.audio.HeadphoneStatus
import hu.ulpifex.fulesebreszto.audio.HeadphoneStatusKind
import hu.ulpifex.fulesebreszto.ui.theme.LocalStatusColors

/** Status line under the title: "Fejhallgató csatlakoztatva" / "Nincs fejhallgató" / check needed. */
@Composable
fun HeadphoneStatusChip(status: HeadphoneStatus, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    val (text, icon, container, content) = when (status.kind) {
        HeadphoneStatusKind.CONNECTED -> Quad(
            "Fejhallgató csatlakoztatva" + (status.deviceName?.let { " · $it" } ?: ""),
            AppIcons.Headphones, colors.successContainer, colors.onSuccessContainer,
        )
        HeadphoneStatusKind.NONE -> Quad(
            "Nincs fejhallgató – ébresztéskor csak rezgés",
            AppIcons.HeadphonesOff,
            MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HeadphoneStatusKind.NEEDS_CHECK -> Quad(
            "Hangkimenet ellenőrzése szükséges",
            AppIcons.Headphones, colors.warningContainer, colors.onWarningContainer,
        )
    }
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = text + (status.detail?.let { ". $it" } ?: "")
        },
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
            status.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, start = 30.dp))
            }
        }
    }
}

private data class Quad(val text: String, val icon: ImageVector, val container: Color, val content: Color)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(top = 20.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/** Rounded settings group. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) { content() }
    }
}

@Composable
fun StatusDot(ok: Boolean, modifier: Modifier = Modifier, warn: Boolean = false) {
    val colors = LocalStatusColors.current
    val color = when {
        ok -> colors.success
        warn -> colors.warning
        else -> MaterialTheme.colorScheme.error
    }
    Spacer(
        modifier
            .size(10.dp)
            .background(color, RoundedCornerShape(50)),
    )
}
