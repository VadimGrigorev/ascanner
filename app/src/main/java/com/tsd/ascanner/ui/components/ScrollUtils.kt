package com.tsd.ascanner.ui.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState

suspend fun LazyListState.animateScrollToItemCentered(index: Int) {
    scrollToItem(index)
    val layoutInfo = this.layoutInfo
    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (targetItem != null) {
        val desiredOffset = (viewportHeight - targetItem.size) / 2
        val delta = (targetItem.offset - desiredOffset).toFloat()
        animateScrollBy(delta)
    }
}
