package com.rasheed.album.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rasheed.album.data.DaySection
import com.rasheed.album.data.MediaFile
import com.rasheed.album.data.MediaSort
import com.rasheed.album.data.TimelineMode
import com.rasheed.album.data.daysLeftShort
import com.rasheed.album.data.formatDuration
import com.rasheed.album.data.groupMedia
import com.rasheed.album.data.sortMedia
import com.rasheed.album.ui.theme.Blue
import com.rasheed.album.ui.theme.LocalAlbumColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGrid(
    items: List<MediaFile>,
    selected: Set<String>,
    selectionMode: Boolean,
    showDaysLeft: Boolean,
    columns: Int,
    timeline: TimelineMode,
    mediaSort: MediaSort = MediaSort.Newest,
    groupByDate: Boolean = true,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onColumns: (Int) -> Unit,
    onOpen: (MediaFile) -> Unit,
    onToggle: (MediaFile) -> Unit,
    onStartSelection: (MediaFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = remember(items, timeline, mediaSort, groupByDate) {
        gridSections(items, timeline, mediaSort, groupByDate)
    }
    val headerIndexes = remember(sections) {
        var cursor = 0
        sections.map { section ->
            val index = cursor
            cursor += (if (section.title.isBlank()) 0 else 1) + section.items.size
            index
        }
    }
    val gridState = rememberLazyGridState()
    var scrubbing by remember { mutableStateOf(false) }
    var pinch by remember { mutableFloatStateOf(1f) }
    val transform = rememberTransformableState { zoomChange, _, _ ->
        pinch *= zoomChange
        if (pinch > 1.22f) {
            onColumns(columns - 1)
            pinch = 1f
        } else if (pinch < 0.82f) {
            onColumns(columns + 1)
            pinch = 1f
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .transformable(state = transform, canPan = { false }),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            sections.forEach { section ->
                if (section.title.isNotBlank()) {
                    item(
                        key = "h-${section.key}",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "header",
                    ) {
                        DateHeader(section.title, section.items.size)
                    }
                }
                items(
                    items = section.items,
                    key = { it.key },
                    contentType = { "media" },
                ) { item ->
                    MediaCell(
                        item = item,
                        selected = item.key in selected,
                        selectionMode = selectionMode,
                        showDaysLeft = showDaysLeft,
                        onClick = {
                            if (selectionMode) onToggle(item) else onOpen(item)
                        },
                        onLongClick = { onStartSelection(item) },
                    )
                }
            }
        }
        FloatingDate(gridState, sections, headerIndexes, scrubbing)
        DateScrubber(
            sections = sections,
            headerIndexes = headerIndexes,
            gridState = gridState,
            onScrubbing = { scrubbing = it },
        )
    }
}

private fun gridSections(
    items: List<MediaFile>,
    timeline: TimelineMode,
    mediaSort: MediaSort,
    groupByDate: Boolean,
): List<DaySection> {
    if (items.isEmpty()) return emptyList()
    val flat = !groupByDate || mediaSort == MediaSort.Size || mediaSort == MediaSort.Name
    if (flat) {
        return listOf(DaySection(key = "all", title = "", items = sortMedia(items, mediaSort)))
    }
    return groupMedia(items, timeline).map { section ->
        section.copy(items = sortMedia(section.items, mediaSort))
    }
}

@Composable
private fun DateHeader(title: String, count: Int) {
    val colors = LocalAlbumColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = colors.ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            color = colors.muted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun MediaCell(
    item: MediaFile,
    selected: Boolean,
    selectionMode: Boolean,
    showDaysLeft: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val request = remember(item.key) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(480, 480)
            .memoryCacheKey(item.key)
            .crossfade(false)
            .build()
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(LocalAlbumColors.current.placeholder)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selected) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
        }
        if (item.isVideo && item.durationMs > 0L) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = formatDuration(item.durationMs),
                    color = Color.White,
                    fontSize = 10.sp,
                    style = TextStyle(
                        shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 4f),
                    ),
                )
            }
        }
        if (showDaysLeft) {
            val label = daysLeftShort(item.expiresAt)
            if (label != null) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (selectionMode) {
            SelectionMark(
                selected = selected,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp),
            )
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) Blue else Color.Black.copy(alpha = 0.28f))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.FloatingDate(
    gridState: LazyGridState,
    sections: List<DaySection>,
    headerIndexes: List<Int>,
    scrubbing: Boolean,
) {
    val title by remember(sections, headerIndexes) {
        derivedStateOf {
            if (!gridState.isScrollInProgress || sections.isEmpty()) return@derivedStateOf null
            val first = gridState.firstVisibleItemIndex
            val index = headerIndexes.indexOfLast { it <= first }.coerceAtLeast(0)
            sections.getOrNull(index)?.title?.takeIf { it.isNotBlank() }
        }
    }
    AnimatedVisibility(
        visible = !scrubbing && title != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF1C1C1E),
            shadowElevation = 6.dp,
        ) {
            Text(
                text = title.orEmpty(),
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BoxScope.DateScrubber(
    sections: List<DaySection>,
    headerIndexes: List<Int>,
    gridState: LazyGridState,
    onScrubbing: (Boolean) -> Unit,
) {
    if (sections.size < 8) return
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var label by remember { mutableStateOf<String?>(null) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    fun jump(index: Int) {
        val safe = index.coerceIn(0, sections.lastIndex)
        val next = sections[safe].title
        if (next != label) {
            label = next
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        scrollJob?.cancel()
        scrollJob = scope.launch { gridState.scrollToItem(headerIndexes[safe]) }
    }

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(22.dp)
            .pointerInput(headerIndexes) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        onScrubbing(true)
                        jump(indexFor(offset.y, size.height, sections.size))
                    },
                    onVerticalDrag = { change, _ ->
                        jump(indexFor(change.position.y, size.height, sections.size))
                    },
                    onDragEnd = {
                        label = null
                        onScrubbing(false)
                    },
                    onDragCancel = {
                        label = null
                        onScrubbing(false)
                    },
                )
            },
    )
    label?.let { text ->
        Surface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp),
            shape = CircleShape,
            color = Color(0xFF1C1C1E),
            shadowElevation = 8.dp,
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

private fun indexFor(y: Float, height: Int, count: Int): Int {
    if (count <= 1 || height <= 0) return 0
    val fraction = (y / height).coerceIn(0f, 1f)
    return (fraction * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

@Composable
fun CenterMessage(title: String, body: String, modifier: Modifier = Modifier) {
    val colors = LocalAlbumColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Color(0xFFEEF2FF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Photo,
                contentDescription = null,
                tint = Blue,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.size(16.dp))
        Text(title, color = colors.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(8.dp))
        Text(
            body,
            color = colors.muted,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
    }
}
