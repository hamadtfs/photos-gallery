package com.rasheed.album.ui

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.rasheed.album.R
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as ExoItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rasheed.album.data.AlbumDestination
import com.rasheed.album.data.CompressQuality
import com.rasheed.album.data.MediaFile
import com.rasheed.album.data.MediaRepository
import com.rasheed.album.data.ShotInfo
import com.rasheed.album.data.displayPath
import com.rasheed.album.data.formatBytes
import com.rasheed.album.data.formatDuration
import com.rasheed.album.data.formatFullDate
import com.rasheed.album.data.formatKind
import com.rasheed.album.data.formatViewerDate
import com.rasheed.album.data.formatViewerTime
import com.rasheed.album.data.resolutionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.rasheed.album.ui.theme.Blue
import com.rasheed.album.ui.theme.Danger
import com.rasheed.album.ui.theme.Heart
import com.rasheed.album.ui.theme.LocalAlbumColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    items: List<MediaFile>,
    initialKey: String,
    trash: Boolean,
    onClose: () -> Unit,
    onShare: (MediaFile) -> Unit,
    onFavorite: (MediaFile) -> Unit,
    onTrash: (MediaFile) -> Unit,
    onRestore: (MediaFile) -> Unit,
    onDeleteForever: (MediaFile) -> Unit,
    onEdit: (MediaFile) -> Unit,
    onEditInApp: (MediaFile) -> Unit = {},
    destinations: List<AlbumDestination> = emptyList(),
    onAddTo: (MediaFile, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onTransform: (MediaFile, Int, Boolean) -> Unit = { _, _, _ -> },
    onCompress: suspend (MediaFile, CompressQuality, Long, Long) -> Unit = { _, _, _, _ -> },
    albumKey: String? = null,
    onSetCover: (MediaFile) -> Unit = {},
    onMoveToPrivate: (MediaFile) -> Unit = {},
    onRemoveFromPrivate: (MediaFile) -> Unit = {},
    inPrivate: Boolean = false,
) {
    if (items.isEmpty()) return
    val start = items.indexOfFirst { it.key == initialKey }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = start, pageCount = { items.size })
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAddTo by remember { mutableStateOf(false) }
    var showCompress by remember { mutableStateOf(false) }
    var compressing by remember { mutableStateOf(false) }
    var trimStart by remember { mutableFloatStateOf(0f) }
    var trimEnd by remember { mutableFloatStateOf(1f) }
    var moveInstead by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    var dismissY by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun pullBy(dy: Float) {
        if (zoomed || slideshow) return
        settleJob?.cancel()
        dismissY = (dismissY + dy).coerceAtLeast(0f)
    }

    fun finishPull() {
        if (dismissY <= 1f) return
        val threshold = with(density) { 120.dp.toPx() }
        settleJob?.cancel()
        if (dismissY > threshold) {
            onClose()
        } else {
            val startOffset = dismissY
            settleJob = scope.launch {
                animate(
                    initialValue = startOffset,
                    targetValue = 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                ) { value, _ ->
                    dismissY = value
                }
            }
        }
    }

    LaunchedEffect(items.size) {
        if (items.isEmpty()) {
            onClose()
        } else if (pagerState.currentPage > items.lastIndex) {
            pagerState.scrollToPage(items.lastIndex)
        }
    }
    LaunchedEffect(slideshow, pagerState.currentPage, items.size) {
        if (!slideshow || items.isEmpty()) return@LaunchedEffect
        delay(3200)
        val next = pagerState.currentPage + 1
        if (next <= items.lastIndex) pagerState.animateScrollToPage(next) else slideshow = false
    }

    val current = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]
    DisposableEffect(slideshow, current.isVideo) {
        val window = (context as? Activity)?.window
        if (slideshow || current.isVideo) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val showChrome = chrome && !slideshow && dismissY < 8f
    val scrim = (1f - dismissY / 900f).coerceIn(0.25f, 1f)
    val dragScale = 1f - (dismissY / 2200f).coerceIn(0f, 0.12f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dismissY
                    scaleX = dragScale
                    scaleY = dragScale
                },
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !zoomed && !slideshow && dismissY == 0f,
                key = { items[it].key },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val item = items[page]
                if (item.isVideo) {
                    if (pagerState.currentPage == page) {
                        ZoomableVideo(
                            item = item,
                            controlsVisible = showChrome,
                            trash = trash,
                            onZoomed = { zoomed = it },
                            onPull = ::pullBy,
                            onPullEnd = ::finishPull,
                            onToggleChrome = { chrome = !chrome },
                            onShare = { onShare(item) },
                            onEdit = { onEdit(item) },
                            onCompress = {
                                trimStart = 0f
                                trimEnd = 1f
                                showCompress = true
                            },
                            onAddTo = { showAddTo = true },
                            onTrash = { confirmDelete = true },
                            onRestore = { onRestore(item) },
                        )
                    } else {
                        Poster(item)
                    }
                } else {
                    ZoomablePhoto(
                        item = item,
                        isCurrent = pagerState.currentPage == page,
                        onTap = {
                            if (slideshow) slideshow = false else chrome = !chrome
                        },
                        onZoomChanged = { zoomed = it },
                        onPull = ::pullBy,
                        onPullEnd = ::finishPull,
                    )
                }
            }

        }

        if (slideshow) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .clickable { slideshow = false },
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.62f),
            ) {
                Text(
                    text = "Slideshow · tap a photo to stop",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (showChrome) {
        Popup(
            alignment = Alignment.TopStart,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                clippingEnabled = false,
            ),
        ) {
            ViewerChrome(
                item = current,
                trash = trash,
                menu = menu,
                slideshow = slideshow,
                onBack = onClose,
                onMenu = { menu = it },
                onFavorite = { onFavorite(current) },
                onDetails = {
                    menu = false
                    details = true
                },
                onSlideshow = {
                    menu = false
                    slideshow = !slideshow
                    if (slideshow) chrome = false
                },
                onEditExternal = {
                    menu = false
                    onEdit(current)
                },
                onEditInApp = {
                    menu = false
                    onEditInApp(current)
                },
                onTransform = onTransform,
                onCompress = {
                    trimStart = 0f
                    trimEnd = 1f
                    showCompress = true
                },
                albumKey = albumKey,
                onSetCover = {
                    menu = false
                    onSetCover(current)
                },
                onMoveToPrivate = {
                    menu = false
                    onMoveToPrivate(current)
                },
                onRemoveFromPrivate = {
                    menu = false
                    onRemoveFromPrivate(current)
                },
                inPrivate = inPrivate,
            )
        }
        if (!current.isVideo) {
            Popup(
                alignment = Alignment.BottomCenter,
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    clippingEnabled = false,
                ),
            ) {
                PhotoDock(
                    date = formatFullDate(current.takenAt),
                    trash = trash,
                    onShare = { onShare(current) },
                    onEdit = { onEditInApp(current) },
                    onAddTo = { showAddTo = true },
                    onTrash = { confirmDelete = true },
                    onRestore = { onRestore(current) },
                )
            }
        }
    }

    if (confirmDelete) {
        val colors = LocalAlbumColors.current
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.background,
            title = {
                Text(
                    text = when {
                        trash -> "Delete permanently?"
                        current.isVideo -> "Move this video to trash?"
                        else -> "Move this photo to trash?"
                    },
                    color = colors.ink,
                )
            },
            text = {
                Text(
                    text = if (trash) {
                        "This removes it from the phone. You can't get it back."
                    } else {
                        "It moves to Recently deleted and stays there for 30 days. Android will ask you to allow this."
                    },
                    color = colors.muted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        if (trash) onDeleteForever(current) else onTrash(current)
                    },
                ) {
                    Text(if (trash) "Delete" else "Trash", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel", color = colors.ink)
                }
            },
        )
    }

    if (showAddTo) {
        val currentPath = current.relativePath.trim().trimEnd('/')
        val choices = destinations.filter { destination ->
            destination.directory.trimEnd('/') != currentPath
        }
        AddToSheet(
            moveInstead = moveInstead,
            onMoveChange = { moveInstead = it },
            newAlbumName = newAlbumName,
            onNameChange = { newAlbumName = it },
            destinations = choices,
            plural = false,
            onCreate = { clean ->
                val directory = if (current.isVideo) "Movies/$clean" else "Pictures/$clean"
                onAddTo(current, directory, clean, moveInstead)
                newAlbumName = ""
                showAddTo = false
            },
            onPick = { destination ->
                onAddTo(current, destination.directory, destination.title, moveInstead)
                showAddTo = false
            },
            onDismiss = { showAddTo = false },
        )
    }

    if (showCompress && current.isVideo && !compressing) {
        val colors = LocalAlbumColors.current
        val duration = current.durationMs.coerceAtLeast(1L)
        val startMs = (trimStart * duration).toLong()
        val endMs = (trimEnd * duration).toLong().coerceAtLeast(startMs + 1L)
        AlertDialog(
            onDismissRequest = { showCompress = false },
            containerColor = colors.background,
            title = { Text("Trim & compress", color = colors.ink) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Saves a new copy. The original stays on the phone.",
                        color = colors.ink.copy(alpha = 0.65f),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Trim · ${formatDuration(startMs)} – ${formatDuration(endMs)}",
                        color = colors.ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Start", color = colors.ink.copy(alpha = 0.55f), fontSize = 12.sp)
                    Slider(
                        value = trimStart,
                        onValueChange = { trimStart = it.coerceIn(0f, (trimEnd - 0.02f).coerceAtLeast(0f)) },
                        colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue),
                    )
                    Text("End", color = colors.ink.copy(alpha = 0.55f), fontSize = 12.sp)
                    Slider(
                        value = trimEnd,
                        onValueChange = { trimEnd = it.coerceIn((trimStart + 0.02f).coerceAtMost(1f), 1f) },
                        colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue),
                    )
                    Spacer(Modifier.height(8.dp))
                    CompressQuality.entries.forEach { quality ->
                        TextButton(
                            onClick = {
                                showCompress = false
                                scope.launch {
                                    compressing = true
                                    try {
                                        onCompress(current, quality, startMs, endMs)
                                        Toast.makeText(
                                            context,
                                            "Saved a new copy. The original is unchanged.",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } catch (error: Exception) {
                                        if (error is kotlinx.coroutines.CancellationException) throw error
                                        Toast.makeText(
                                            context,
                                            "Couldn't save this video",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } finally {
                                        compressing = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(quality.label, color = colors.ink, fontWeight = FontWeight.SemiBold)
                                Text(quality.hint, color = colors.ink.copy(alpha = 0.55f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCompress = false }) {
                    Text("Cancel", color = colors.ink)
                }
            },
        )
    }

    if (compressing) {
        val colors = LocalAlbumColors.current
        AlertDialog(
            onDismissRequest = {},
            containerColor = colors.background,
            title = { Text("Saving video…", color = colors.ink) },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Blue)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Keep Album open until this finishes.",
                        color = colors.ink.copy(alpha = 0.65f),
                        fontSize = 14.sp,
                    )
                }
            },
            confirmButton = {},
        )
    }

    if (details) {
        val colors = LocalAlbumColors.current
        ModalBottomSheet(
            onDismissRequest = { details = false },
            containerColor = colors.background,
        ) {
            DetailsSheet(current)
        }
    }
}

@Composable
private fun ViewerChrome(
    item: MediaFile,
    trash: Boolean,
    menu: Boolean,
    slideshow: Boolean,
    onBack: () -> Unit,
    onMenu: (Boolean) -> Unit,
    onFavorite: () -> Unit,
    onDetails: () -> Unit,
    onSlideshow: () -> Unit,
    onEditExternal: () -> Unit,
    onEditInApp: () -> Unit,
    onTransform: (MediaFile, Int, Boolean) -> Unit,
    onCompress: () -> Unit,
    albumKey: String?,
    onSetCover: () -> Unit,
    onMoveToPrivate: () -> Unit,
    onRemoveFromPrivate: () -> Unit,
    inPrivate: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .statusBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White)
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatViewerDate(item.takenAt),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatViewerTime(item.takenAt),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                )
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                if (!trash) {
                    IconButton(onClick = onFavorite) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (item.isFavorite) Heart else Color.White,
                        )
                    }
                }
                MoreMenu(
                    menu = menu,
                    item = item,
                    trash = trash,
                    slideshow = slideshow,
                    onMenu = onMenu,
                    onDetails = onDetails,
                    onSlideshow = onSlideshow,
                    onEditExternal = onEditExternal,
                    onEditInApp = onEditInApp,
                    onTransform = onTransform,
                    onCompress = onCompress,
                    albumKey = albumKey,
                    onSetCover = onSetCover,
                    onMoveToPrivate = onMoveToPrivate,
                    onRemoveFromPrivate = onRemoveFromPrivate,
                    inPrivate = inPrivate,
                )
            }
        }
    }
}

@Composable
private fun MoreMenu(
    menu: Boolean,
    item: MediaFile,
    trash: Boolean,
    slideshow: Boolean,
    onMenu: (Boolean) -> Unit,
    onDetails: () -> Unit,
    onSlideshow: () -> Unit,
    onEditExternal: () -> Unit,
    onEditInApp: () -> Unit,
    onTransform: (MediaFile, Int, Boolean) -> Unit,
    onCompress: () -> Unit,
    albumKey: String?,
    onSetCover: () -> Unit,
    onMoveToPrivate: () -> Unit,
    onRemoveFromPrivate: () -> Unit,
    inPrivate: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Box {
        IconButton(onClick = { onMenu(true) }) {
            Icon(Icons.Outlined.MoreVert, "More", tint = Color.White)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { onMenu(false) }) {
            DropdownMenuItem(
                text = { Text("Details") },
                leadingIcon = { Icon(Icons.Outlined.Info, null) },
                onClick = onDetails,
            )
            if (!trash && item.isVideo) {
                DropdownMenuItem(
                    text = { Text("Trim & compress") },
                    leadingIcon = { Icon(Icons.Outlined.Compress, null) },
                    onClick = {
                        onMenu(false)
                        onCompress()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Edit in another app") },
                    onClick = onEditExternal,
                )
            }
            if (!trash && !item.isVideo) {
                DropdownMenuItem(
                    text = { Text("Edit photo") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = onEditInApp,
                )
                DropdownMenuItem(
                    text = { Text(if (slideshow) "Stop slideshow" else "Slideshow") },
                    leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                    onClick = onSlideshow,
                )
                DropdownMenuItem(
                    text = { Text("Rotate") },
                    onClick = {
                        onMenu(false)
                        onTransform(item, 90, false)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Flip horizontal") },
                    onClick = {
                        onMenu(false)
                        onTransform(item, 0, true)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Set as wallpaper") },
                    onClick = {
                        onMenu(false)
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(item.uri).use { input ->
                                        if (input == null) error("Couldn't open this photo")
                                        WallpaperManager.getInstance(context).setStream(input)
                                    }
                                }
                                Toast.makeText(context, "Wallpaper updated", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Couldn't set the wallpaper", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Edit in another app") },
                    onClick = onEditExternal,
                )
            }
            if (!trash && !inPrivate) {
                DropdownMenuItem(
                    text = { Text("Move to Private") },
                    onClick = onMoveToPrivate,
                )
            }
            if (!trash && inPrivate) {
                DropdownMenuItem(
                    text = { Text("Remove from Private") },
                    onClick = onRemoveFromPrivate,
                )
            }
            if (!trash && albumKey != null && albumKey.startsWith("b:")) {
                DropdownMenuItem(
                    text = { Text("Use as album cover") },
                    onClick = onSetCover,
                )
            }
            if (!trash) {
                DropdownMenuItem(
                    text = { Text("Open with") },
                    onClick = {
                        onMenu(false)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(item.uri, item.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(intent, "Open with"))
                        }.onFailure {
                            Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(
    item: MediaFile,
    isCurrent: Boolean,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onPull: (Float) -> Unit,
    onPullEnd: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1.05f || next > 1.05f) {
            scale = if (next <= 1.02f) 1f else next
            offset = if (scale <= 1.02f) {
                Offset.Zero
            } else {
                clampOffset(offset + panChange, scale, size)
            }
            onZoomChanged(scale > 1.05f)
        } else if (isCurrent) {
            onPull(panChange.y)
        }
    }
    LaunchedEffect(transformState, isCurrent) {
        var active = false
        snapshotFlow { transformState.isTransformInProgress }.collect { now ->
            if (active && !now && isCurrent) onPullEnd()
            active = now
        }
    }
    DisposableEffect(Unit) {
        onDispose { onZoomChanged(false) }
    }
    LaunchedEffect(isCurrent) {
        if (!isCurrent && scale > 1f) {
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }
    val context = LocalContext.current
    val request = remember(item.key) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(2048, 2048)
            .crossfade(false)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = item.displayName,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged(false)
                        } else {
                            scale = 2.5f
                            onZoomChanged(true)
                        }
                    },
                )
            }
            .transformable(
                state = transformState,
                canPan = { pan -> scale > 1.05f || abs(pan.y) > abs(pan.x) },
            ),
    )
}

@Composable
private fun Poster(item: MediaFile) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(item.uri).size(1080, 1080).build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(56.dp),
        )
    }
}

private class VideoHooks {
    var onPull: (Float) -> Unit = {}
    var onPullEnd: () -> Unit = {}
    var onZoomed: (Boolean) -> Unit = {}
    var onToggleChrome: () -> Unit = {}
}

private class VideoGesture {
    var scale = 1f
    var offset = Offset.Zero
    var dismiss = false
}

@Composable
private fun ZoomableVideo(
    item: MediaFile,
    controlsVisible: Boolean,
    trash: Boolean,
    onZoomed: (Boolean) -> Unit,
    onPull: (Float) -> Unit,
    onPullEnd: () -> Unit,
    onToggleChrome: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onCompress: () -> Unit,
    onAddTo: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hooks = remember { VideoHooks() }
    hooks.onPull = onPull
    hooks.onPullEnd = onPullEnd
    hooks.onZoomed = onZoomed
    hooks.onToggleChrome = onToggleChrome
    val gesture = remember(item.key) { VideoGesture() }
    val exoPlayer = remember(item.key) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoItem.fromUri(item.uri))
            prepare()
            playWhenReady = true
        }
    }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(0L)) }
    var playing by remember { mutableStateOf(true) }
    var muted by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var scrubbing by remember { mutableStateOf(false) }
    LaunchedEffect(exoPlayer, scrubbing) {
        while (true) {
            if (!scrubbing) position = exoPlayer.currentPosition.coerceAtLeast(0L)
            val length = exoPlayer.duration
            if (length > 0L) duration = length
            playing = exoPlayer.isPlaying
            delay(200)
        }
    }
    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
            gesture.scale = 1f
            gesture.offset = Offset.Zero
            hooks.onZoomed(false)
        }
    }
    AndroidView(
        factory = { viewContext ->
            val playerView = LayoutInflater.from(viewContext)
                .inflate(R.layout.player_texture, FrameLayout(viewContext), false) as PlayerView
            playerView.apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
                useController = false
                player = exoPlayer
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowNextButton(false)
                setShowPreviousButton(false)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                isClickable = true
                isFocusable = true
                setOnTouchListener(videoTouchListener(this, gesture, hooks))
            }
        },
        update = { playerView ->
            if (playerView.player != exoPlayer) playerView.player = exoPlayer
        },
        modifier = Modifier.fillMaxSize(),
    )
    if (controlsVisible) {
        Popup(
            alignment = Alignment.BottomCenter,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                clippingEnabled = false,
            ),
        ) {
            VideoDock(
                position = position,
                duration = duration,
                playing = playing,
                muted = muted,
                speed = speed,
                trash = trash,
                onPlay = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                },
                onMute = {
                    muted = !muted
                    exoPlayer.volume = if (muted) 0f else 1f
                },
                onSpeed = {
                    speed = when (speed) {
                        1f -> 1.5f
                        1.5f -> 2f
                        2f -> 0.5f
                        else -> 1f
                    }
                    exoPlayer.setPlaybackSpeed(speed)
                },
                onScrub = { fraction ->
                    scrubbing = true
                    position = (duration * fraction).toLong()
                },
                onScrubFinished = {
                    exoPlayer.seekTo(position)
                    scrubbing = false
                },
                onShare = onShare,
                onEdit = onEdit,
                onCompress = onCompress,
                onAddTo = onAddTo,
                onTrash = onTrash,
                onRestore = onRestore,
            )
        }
    }
}

private fun videoTouchListener(view: PlayerView, gesture: VideoGesture, hooks: VideoHooks): View.OnTouchListener {
    var downRawX = 0f
    var downRawY = 0f
    var lastRawX = 0f
    var lastRawY = 0f
    val slop = android.view.ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
    val taps = GestureDetector(
        view.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                hooks.onToggleChrome()
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                if (gesture.scale > 1.05f) {
                    gesture.scale = 1f
                    gesture.offset = Offset.Zero
                } else {
                    gesture.scale = 2.5f
                }
                applyVideoTransform(view, gesture)
                hooks.onZoomed(gesture.scale > 1.05f)
                return true
            }
        },
    )
    val scaler = ScaleGestureDetector(
        view.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val next = (gesture.scale * detector.scaleFactor).coerceIn(1f, 5f)
                gesture.scale = if (next <= 1.02f) 1f else next
                if (gesture.scale <= 1.02f) {
                    gesture.offset = Offset.Zero
                }
                applyVideoTransform(view, gesture)
                hooks.onZoomed(gesture.scale > 1.05f)
                gesture.dismiss = false
                return true
            }
        },
    )
    return View.OnTouchListener { touched, event ->
        touched.parent?.requestDisallowInterceptTouchEvent(
            gesture.scale > 1.05f || event.pointerCount > 1 || scaler.isInProgress || gesture.dismiss,
        )
        val tapHandled = taps.onTouchEvent(event)
        scaler.onTouchEvent(event)
        if (scaler.isInProgress || event.pointerCount > 1) {
            gesture.dismiss = false
            return@OnTouchListener true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                gesture.dismiss = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val travelX = event.rawX - downRawX
                val travelY = event.rawY - downRawY
                val stepX = event.rawX - lastRawX
                val stepY = event.rawY - lastRawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                if (gesture.scale > 1.05f) {
                    gesture.offset = clampOffset(
                        gesture.offset + Offset(stepX, stepY),
                        gesture.scale,
                        IntSize(view.width, view.height),
                    )
                    applyVideoTransform(view, gesture)
                    true
                } else if (!gesture.dismiss && hypot(travelX, travelY) > slop && abs(travelY) > abs(travelX) && travelY > 0f) {
                    gesture.dismiss = true
                    touched.parent?.requestDisallowInterceptTouchEvent(true)
                    hooks.onPull(stepY)
                    true
                } else if (gesture.dismiss) {
                    hooks.onPull(stepY)
                    true
                } else if (hypot(travelX, travelY) > slop && abs(travelX) >= abs(travelY) && gesture.scale <= 1.05f) {
                    touched.parent?.requestDisallowInterceptTouchEvent(false)
                    false
                } else {
                    true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDismiss = gesture.dismiss
                gesture.dismiss = false
                touched.parent?.requestDisallowInterceptTouchEvent(false)
                if (wasDismiss) {
                    hooks.onPullEnd()
                    true
                } else {
                    tapHandled || true
                }
            }
            else -> true
        }
    }
}

private fun applyVideoTransform(view: View, gesture: VideoGesture) {
    view.pivotX = view.width / 2f
    view.pivotY = view.height / 2f
    view.scaleX = gesture.scale
    view.scaleY = gesture.scale
    view.translationX = gesture.offset.x
    view.translationY = gesture.offset.y
}

private fun clampOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    if (scale <= 1.02f || size.width == 0 || size.height == 0) return Offset.Zero
    val maxX = size.width * (scale - 1f) / 2f
    val maxY = size.height * (scale - 1f) / 2f
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY),
    )
}

@Composable
private fun VideoDock(
    position: Long,
    duration: Long,
    playing: Boolean,
    muted: Boolean,
    speed: Float,
    trash: Boolean,
    onPlay: () -> Unit,
    onMute: () -> Unit,
    onSpeed: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onCompress: () -> Unit,
    onAddTo: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
) {
    val span = duration.coerceAtLeast(1L)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                )
            }
            Text(
                text = "${formatDuration(position)} / ${formatDuration(duration)}",
                color = Color.White,
                fontSize = 14.sp,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSpeed) {
                Text(speedLabel(speed), color = Color.White, fontSize = 14.sp)
            }
            IconButton(onClick = onMute) {
                Icon(
                    imageVector = if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                    contentDescription = if (muted) "Unmute" else "Mute",
                    tint = Color.White,
                )
            }
        }
        Slider(
            value = (position.toFloat() / span).coerceIn(0f, 1f),
            onValueChange = onScrub,
            onValueChangeFinished = onScrubFinished,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.35f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (trash) {
                DockAction(Icons.Outlined.Restore, "Restore", onRestore)
                DockAction(Icons.Outlined.Delete, "Delete", onTrash)
            } else {
                DockAction(Icons.Outlined.Share, "Share", onShare)
                DockAction(Icons.Outlined.Compress, "Compress", onCompress)
                DockAction(Icons.Outlined.Add, "Add to", onAddTo)
                DockAction(Icons.Outlined.Delete, "Trash", onTrash)
            }
        }
    }
}

@Composable
private fun DockAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun PhotoDock(
    date: String,
    trash: Boolean,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onAddTo: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (trash) {
                DockAction(Icons.Outlined.Restore, "Restore", onRestore)
                DockAction(Icons.Outlined.Delete, "Delete", onTrash)
            } else {
                DockAction(Icons.Outlined.Share, "Share", onShare)
                DockAction(Icons.Outlined.Edit, "Edit", onEdit)
                DockAction(Icons.Outlined.Add, "Add to", onAddTo)
                DockAction(Icons.Outlined.Delete, "Trash", onTrash)
            }
        }
    }
}

private fun speedLabel(speed: Float): String {
    return when (speed) {
        0.5f -> "0.5×"
        1.5f -> "1.5×"
        2f -> "2×"
        else -> "1×"
    }
}

@Composable
private fun DetailsSheet(item: MediaFile) {
    val colors = LocalAlbumColors.current
    val context = LocalContext.current
    val shot by produceState(ShotInfo(), item.key) {
        val app = context.applicationContext as android.app.Application
        value = withContext(Dispatchers.IO) { MediaRepository(app).shotInfo(item) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp),
    ) {
        Text(
            text = "Details",
            color = colors.ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        DetailRow("Name", item.displayName)
        DetailRow("Date", formatFullDate(item.takenAt))
        DetailRow("Album", item.bucketName)
        DetailRow("Size", formatBytes(item.sizeBytes))
        DetailRow("Resolution", resolutionLabel(item))
        if (item.isVideo && item.durationMs > 0L) {
            DetailRow("Duration", formatDuration(item.durationMs))
        }
        DetailRow("Format", formatKind(item.mimeType))
        DetailRow("Camera", shot.camera)
        DetailRow("Location", shot.location)
        DetailRow("Path", displayPath(item))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    val colors = LocalAlbumColors.current
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(label, color = colors.muted, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = colors.ink, fontSize = 16.sp)
    }
}
