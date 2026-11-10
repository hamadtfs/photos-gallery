package com.rasheed.album.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rasheed.album.data.DuplicateGroup
import com.rasheed.album.data.MediaFile
import com.rasheed.album.data.formatBytes
import com.rasheed.album.data.formatDuration
import com.rasheed.album.ui.theme.Blue
import com.rasheed.album.ui.theme.LocalAlbumColors

@Composable
fun DuplicatesScreen(
    groups: List<DuplicateGroup>,
    scanning: Boolean,
    selected: Set<String>,
    selectionMode: Boolean,
    onSelectExtras: () -> Unit,
    onOpen: (MediaFile) -> Unit,
    onToggle: (MediaFile) -> Unit,
    onStartSelection: (MediaFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlbumColors.current
    val extras = remember(groups) { groups.sumOf { it.extras.size } }
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            when {
                scanning && groups.isEmpty() -> Text(
                    "Looking for exact copies…",
                    color = colors.muted,
                    fontSize = 14.sp,
                )
                groups.isEmpty() -> Text(
                    "No exact duplicate photos or videos were found.",
                    color = colors.muted,
                    fontSize = 14.sp,
                )
                else -> {
                    Text(
                        "${groups.size} sets · $extras extra ${if (extras == 1) "copy" else "copies"}",
                        color = colors.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The first item in each set is kept. Select the extras, then delete them.",
                        color = colors.muted,
                        fontSize = 13.sp,
                    )
                    if (extras > 0) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onSelectExtras) {
                            Text("Select extras")
                        }
                    }
                }
            }
            if (scanning && groups.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Blue)
                    Text(
                        " Still checking…",
                        color = colors.muted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        if (scanning && groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue)
            }
        } else if (groups.isEmpty()) {
            CenterMessage(
                title = "No duplicates",
                body = "Album compares exact copies of the same file. Similar-looking photos are not listed here.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(groups, key = { it.id }) { group ->
                    DuplicateGroupCard(
                        group = group,
                        selected = selected,
                        selectionMode = selectionMode,
                        onOpen = onOpen,
                        onToggle = onToggle,
                        onStartSelection = onStartSelection,
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    selected: Set<String>,
    selectionMode: Boolean,
    onOpen: (MediaFile) -> Unit,
    onToggle: (MediaFile) -> Unit,
    onStartSelection: (MediaFile) -> Unit,
) {
    val colors = LocalAlbumColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            "${group.items.size} copies · keep ${group.keeper.displayName}",
            color = colors.ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            formatBytes(group.keeper.sizeBytes),
            color = colors.muted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        group.items.chunked(4).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEachIndexed { index, item ->
                    val absolute = rowIndex * 4 + index
                    DuplicateThumb(
                        item = item,
                        label = if (absolute == 0) "Keep" else "Extra",
                        kept = absolute == 0,
                        selected = item.key in selected,
                        selectionMode = selectionMode,
                        onClick = {
                            if (selectionMode) onToggle(item) else onOpen(item)
                        },
                        onLongClick = { onStartSelection(item) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DuplicateThumb(
    item: MediaFile,
    label: String,
    kept: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlbumColors.current
    val context = LocalContext.current
    val request = remember(item.key) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(360, 360)
            .memoryCacheKey(item.key)
            .crossfade(false)
            .build()
    }
    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.placeholder)
                .then(
                    if (kept) Modifier.border(2.dp, Blue, RoundedCornerShape(10.dp))
                    else Modifier,
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            AsyncImage(
                model = request,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (selected) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
            }
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .background(if (kept) Blue else Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (item.isVideo && item.durationMs > 0L) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Text(
                        formatDuration(item.durationMs),
                        color = Color.White,
                        fontSize = 10.sp,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 4f)),
                    )
                }
            }
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (selected) Blue else Color.Black.copy(alpha = 0.28f))
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(Icons.Filled.Check, "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
