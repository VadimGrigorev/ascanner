package com.tsd.ascanner.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

private val TRACK_WIDTH = 3.5.dp
private val THUMB_WIDTH = 14.dp
private val TOUCH_TARGET_WIDTH = 56.dp
private val THUMB_MIN_HEIGHT = 48.dp
private val THUMB_CORNER = 4.dp
private val THUMB_COLOR = Color.Black.copy(alpha = 0.22f)
private const val MIN_TOTAL_ITEMS = 14

@Composable
fun LeftOverlayLazyScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    itemColors: List<Color> = emptyList()
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    var containerHeightPx by remember { mutableStateOf(0f) }

    val totalItems by remember { derivedStateOf { listState.layoutInfo.totalItemsCount } }

    val needsScrollbar by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount >= MIN_TOTAL_ITEMS &&
                info.visibleItemsInfo.size < info.totalItemsCount
        }
    }

    val thumbFraction by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) return@derivedStateOf 0f
            val visible = info.visibleItemsInfo.size.toFloat()
            (visible / info.totalItemsCount.toFloat()).coerceIn(0.05f, 1f)
        }
    }

    val scrollFraction by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) return@derivedStateOf 0f
            val first = info.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf 0f
            val scrollableItems = info.totalItemsCount - info.visibleItemsInfo.size
            if (scrollableItems <= 0) return@derivedStateOf 0f
            val itemOffset = if (first.size > 0) {
                first.index.toFloat() + (-first.offset.toFloat() / first.size.toFloat())
            } else {
                first.index.toFloat()
            }
            (itemOffset / scrollableItems.toFloat()).coerceIn(0f, 1f)
        }
    }

    if (!needsScrollbar) return

    val thumbMinPx = with(density) { THUMB_MIN_HEIGHT.toPx() }
    val cornerPx = with(density) { THUMB_CORNER.toPx() }
    val trackWidthPx = with(density) { TRACK_WIDTH.toPx() }
    val thumbWidthPx = with(density) { THUMB_WIDTH.toPx() }
    val hasTrackColors = itemColors.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(TOUCH_TARGET_WIDTH),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(TOUCH_TARGET_WIDTH)
                .onSizeChanged { containerHeightPx = it.height.toFloat() }
                .pointerInput(totalItems) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val trackHeight = containerHeightPx
                            if (trackHeight <= 0f || totalItems == 0) return@detectVerticalDragGestures
                            val thumbH = (thumbFraction * trackHeight).coerceAtLeast(thumbMinPx)
                            val scrollableTrack = trackHeight - thumbH
                            if (scrollableTrack <= 0f) return@detectVerticalDragGestures

                            val pointerY = change.position.y
                            val fraction =
                                ((pointerY - thumbH / 2f) / scrollableTrack).coerceIn(0f, 1f)
                            val info = listState.layoutInfo
                            val lastIndex = info.totalItemsCount - 1

                            if (fraction >= 0.99f) {
                                scope.launch { listState.animateScrollToItem(lastIndex) }
                            } else {
                                val scrollableItems =
                                    info.totalItemsCount - info.visibleItemsInfo.size
                                if (scrollableItems <= 0) return@detectVerticalDragGestures
                                val targetIndex = (fraction * scrollableItems).roundToInt()
                                    .coerceIn(0, lastIndex)
                                scope.launch { listState.scrollToItem(targetIndex) }
                            }
                        }
                    )
                }
                .drawBehind {
                    val trackHeight = size.height
                    if (trackHeight <= 0f) return@drawBehind

                    if (hasTrackColors && isDragging) {
                        val segmentH = trackHeight / itemColors.size
                        itemColors.forEachIndexed { i, color ->
                            val top = i * segmentH
                            val height = ceil(segmentH + 1f)
                            drawRect(
                                color = color,
                                topLeft = Offset(0f, top),
                                size = Size(trackWidthPx, height)
                            )
                        }
                    }

                    val thumbH = (thumbFraction * trackHeight).coerceAtLeast(thumbMinPx)
                    val scrollableTrack = trackHeight - thumbH
                    val thumbTop = scrollFraction * scrollableTrack

                    drawRoundRect(
                        color = THUMB_COLOR,
                        topLeft = Offset(0f, thumbTop),
                        size = Size(thumbWidthPx, thumbH),
                        cornerRadius = CornerRadius(cornerPx, cornerPx)
                    )
                }
        )
    }
}
