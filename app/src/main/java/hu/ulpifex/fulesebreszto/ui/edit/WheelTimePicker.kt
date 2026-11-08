package hu.ulpifex.fulesebreszto.ui.edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import hu.ulpifex.fulesebreszto.R
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter

/** 24-hour time picker made of two endless scroll wheels (hour and minute). */
@Composable
fun WheelTimePicker(hour: Int, minute: Int, onChange: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val currentHour by rememberUpdatedState(hour)
    val currentMinute by rememberUpdatedState(minute)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Wheel(count = 24, selected = hour, label = stringResource(R.string.wheel_hour), onSelected = { onChange(it, currentMinute) })
        Text(":", fontSize = 48.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 8.dp))
        Wheel(count = 60, selected = minute, label = stringResource(R.string.wheel_minute), onSelected = { onChange(currentHour, it) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Wheel(count: Int, selected: Int, label: String, onSelected: (Int) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    val itemHeight: Dp = (56f * fontScale.coerceIn(1f, 1.8f)).dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val base = remember(count) { count * 200 }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = base + selected - 1)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val onSelectedState by rememberUpdatedState(onSelected)
    val selectedState by rememberUpdatedState(selected)

    // With three visible rows the centred row is firstVisibleItemIndex + 1.
    val centred by remember {
        derivedStateOf {
            val extra = if (state.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
            (state.firstVisibleItemIndex + 1 + extra) % count
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .filter { !it }
            .collect { if (centred != selectedState) onSelectedState(centred) }
    }
    LaunchedEffect(selected) {
        if (!state.isScrollInProgress && centred != selected) {
            val currentCentre = state.firstVisibleItemIndex + 1
            val delta = ((selected - currentCentre % count) % count + count) % count
            val forward = if (delta > count / 2) delta - count else delta
            state.scrollToItem(currentCentre + forward - 1)
        }
    }

    val description = stringResource(R.string.wheel_cd, label, selected)
    val increase = stringResource(R.string.wheel_increase)
    val decrease = stringResource(R.string.wheel_decrease)
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(itemHeight * 3)
            .focusable()
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(increase) { onSelectedState((selected + 1) % count); true },
                    CustomAccessibilityAction(decrease) { onSelectedState((selected - 1 + count) % count); true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
        )
        LazyColumn(state = state, flingBehavior = fling, horizontalAlignment = Alignment.CenterHorizontally) {
            items(count * 400) { index ->
                val value = index % count
                val isCentre = value == centred
                Box(Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "%02d".format(value),
                        fontSize = 40.sp,
                        fontWeight = if (isCentre) FontWeight.Medium else FontWeight.Light,
                        color = if (isCentre) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.alpha(if (isCentre) 1f else 0.45f),
                    )
                }
            }
        }
    }
}
