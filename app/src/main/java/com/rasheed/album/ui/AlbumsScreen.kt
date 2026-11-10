package com.rasheed.album.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rasheed.album.data.AlbumEntry
import com.rasheed.album.data.AlbumGroups
import com.rasheed.album.data.Sections
import com.rasheed.album.data.formatCount
import com.rasheed.album.ui.theme.LocalAlbumColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    groups: AlbumGroups,
    pinned: List<String>,
    multiRow: Boolean,
    collapsed: Set<String>,
    onToggleSection: (String) -> Unit,
    onOpen: (AlbumEntry) -> Unit,
    onPin: (String) -> Unit,
    onUnpin: (String) -> Unit,
    onHide: (String) -> Unit,
    onUnhide: (String) -> Unit,
    onRename: (AlbumEntry, String) -> Unit,
    onDeleteAlbum: (AlbumEntry) -> Unit,
    onClearCover: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuAlbum by remember { mutableStateOf<AlbumEntry?>(null) }
    var renameAlbum by remember { mutableStateOf<AlbumEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteAlbum by remember { mutableStateOf<AlbumEntry?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
    ) {
        AlbumSection(
            id = Sections.Frequent,
            title = "Frequently used",
            albums = groups.frequent,
            expanded = Sections.Frequent !in collapsed,
            multiRow = multiRow,
            onToggle = onToggleSection,
            onOpen = onOpen,
            onLongPress = { if (it.pinnable) menuAlbum = it },
        )
        AlbumSection(
            id = Sections.More,
            title = "More albums",
            albums = groups.more,
            expanded = Sections.More !in collapsed,
            multiRow = multiRow,
            onToggle = onToggleSection,
            onOpen = onOpen,
            onLongPress = { if (it.pinnable) menuAlbum = it },
        )
        AlbumSection(
            id = Sections.Media,
            title = "Media types",
            albums = groups.media,
            expanded = Sections.Media !in collapsed,
            multiRow = multiRow,
            onToggle = onToggleSection,
            onOpen = onOpen,
            onLongPress = {},
        )
        AlbumSection(
            id = "hidden",
            title = "Hidden",
            albums = groups.hidden,
            expanded = "hidden" !in collapsed,
            multiRow = multiRow,
            onToggle = onToggleSection,
            onOpen = onOpen,
            onLongPress = { onUnhide(it.key) },
        )
        AlbumSection(
            id = Sections.Other,
            title = "Other",
            albums = groups.other,
            expanded = Sections.Other !in collapsed,
            multiRow = multiRow,
            onToggle = onToggleSection,
            onOpen = onOpen,
            onLongPress = {},
        )
    }

    val album = menuAlbum
    if (album != null) {
        val pinnedNow = album.key in pinned
        ModalBottomSheet(
            onDismissRequest = { menuAlbum = null },
            containerColor = LocalAlbumColors.current.background,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                Text(
                    text = album.title,
                    color = LocalAlbumColors.current.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Text(
                    text = "${formatCount(album.count)} items",
                    color = LocalAlbumColors.current.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                TextButton(
                    onClick = {
                        renameText = album.title
                        renameAlbum = album
                        menuAlbum = null
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Text("Rename album", color = LocalAlbumColors.current.ink, fontSize = 16.sp, modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = {
                        onClearCover(album.key)
                        menuAlbum = null
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Text(
                        "Reset cover photo",
                        color = LocalAlbumColors.current.ink,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    onClick = {
                        onHide(album.key)
                        menuAlbum = null
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = "Hide album",
                        color = LocalAlbumColors.current.ink,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    onClick = {
                        if (pinnedNow) onUnpin(album.key) else onPin(album.key)
                        menuAlbum = null
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = if (pinnedNow) "Remove from frequently used" else "Pin to frequently used",
                        color = LocalAlbumColors.current.ink,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    onClick = {
                        deleteAlbum = album
                        menuAlbum = null
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = "Delete album items",
                        color = Color(0xFFD64545),
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    val renaming = renameAlbum
    if (renaming != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameAlbum = null },
            containerColor = LocalAlbumColors.current.background,
            title = { Text("Rename album", color = LocalAlbumColors.current.ink) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(40) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(renaming, renameText)
                        renameAlbum = null
                    },
                ) {
                    Text("Save", color = LocalAlbumColors.current.ink)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameAlbum = null }) {
                    Text("Cancel", color = LocalAlbumColors.current.muted)
                }
            },
        )
    }

    val deleting = deleteAlbum
    if (deleting != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteAlbum = null },
            containerColor = LocalAlbumColors.current.background,
            title = { Text("Delete album items?", color = LocalAlbumColors.current.ink) },
            text = {
                Text(
                    "Moves all ${formatCount(deleting.count)} items in “${deleting.title}” to Recently deleted. Android will ask you to allow this.",
                    color = LocalAlbumColors.current.muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAlbum(deleting)
                        deleteAlbum = null
                    },
                ) {
                    Text("Delete", color = Color(0xFFD64545))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAlbum = null }) {
                    Text("Cancel", color = LocalAlbumColors.current.ink)
                }
            },
        )
    }
}

@Composable
private fun AlbumSection(
    id: String,
    title: String,
    albums: List<AlbumEntry>,
    expanded: Boolean,
    multiRow: Boolean,
    onToggle: (String) -> Unit,
    onOpen: (AlbumEntry) -> Unit,
    onLongPress: (AlbumEntry) -> Unit,
) {
    if (albums.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onToggle(id) })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = LocalAlbumColors.current.ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = LocalAlbumColors.current.muted,
            )
        }
        if (!expanded) return
        if (multiRow) {
            albums.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { album ->
                        AlbumCard(
                            album = album,
                            onOpen = { onOpen(album) },
                            onLongPress = { onLongPress(album) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                albums.forEach { album ->
                    AlbumCard(
                        album = album,
                        onOpen = { onOpen(album) },
                        onLongPress = { onLongPress(album) },
                        modifier = Modifier.width(108.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: AlbumEntry,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        val shape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(LocalAlbumColors.current.placeholder),
            contentAlignment = Alignment.Center,
        ) {
            when {
                album.key == "private" -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1C1F2A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = "Private",
                            tint = Color(0xFFE8EAF0),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                album.coverUri != null -> {
                    val context = LocalContext.current
                    val request = remember(album.coverUri) {
                        ImageRequest.Builder(context)
                            .data(Uri.parse(album.coverUri))
                            .size(480, 480)
                            .crossfade(false)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        tint = Color(0xFFB0B3BC),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = album.title,
            color = LocalAlbumColors.current.ink,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatCount(album.count),
            color = LocalAlbumColors.current.muted,
            fontSize = 12.sp,
        )
    }
}
