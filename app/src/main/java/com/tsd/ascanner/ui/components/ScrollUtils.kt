package com.tsd.ascanner.ui.components

import androidx.compose.foundation.lazy.LazyListState

suspend fun LazyListState.animateScrollToItemCentered(index: Int) {
    scrollToItem(index)
    val info = this.layoutInfo
    val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val desiredOffset = (viewportHeight - item.size) / 2
    val delta = (item.offset - desiredOffset).toFloat()
    if (delta != 0f) {
        scroll { scrollBy(delta) }
    }
}
