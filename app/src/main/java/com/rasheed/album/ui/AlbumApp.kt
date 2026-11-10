package com.rasheed.album.ui

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Photo
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.rasheed.album.data.Access
import com.rasheed.album.data.AlbumDestination
import com.rasheed.album.data.MediaFile
import com.rasheed.album.data.MediaKind
import com.rasheed.album.data.MediaSort
import com.rasheed.album.data.SortMode
import com.rasheed.album.data.ThemeMode
import com.rasheed.album.data.TimelineMode
import com.rasheed.album.data.buildGroups
import com.rasheed.album.data.formatBytes
import com.rasheed.album.data.formatCount
import com.rasheed.album.ui.theme.Blue
import com.rasheed.album.ui.theme.Danger
import com.rasheed.album.ui.theme.LocalAlbumColors
import android.content.IntentSender

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AlbumApp(viewModel: AlbumViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val destinations = remember(state.active) {
        state.active
            .groupBy { it.bucketId }
            .mapNotNull { (_, group) ->
                val sample = group.maxBy { it.takenAt }
                val directory = sample.relativePath.trim().trimEnd('/').ifBlank { return@mapNotNull null }
                val title = group.groupingBy { it.bucketName.ifBlank { "Album" } }
                    .eachCount()
                    .maxBy { it.value }
                    .key
                AlbumDestination(title = title, directory = directory)
            }
            .distinctBy { it.directory.trimEnd('/').lowercase() }
            .sortedBy { it.title.lowercase() }
    }
    val snackbar = remember { SnackbarHostState() }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAddTo by remember { mutableStateOf(false) }
    var moveInstead by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    var closeViewerOnSuccess by remember { mutableStateOf(false) }
    var editingPhoto by remember { mutableStateOf<MediaFile?>(null) }
    var privatePrompt by remember { mutableStateOf<PrivatePrompt?>(null) }
    val colors = LocalAlbumColors.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val columns = if (state.columns == 0) {
        if (widthDp >= 700) 6 else 4
    } else {
        state.columns
    }
    val darkPage = when (state.theme) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }

    val senderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectionMode = false
            selected = emptySet()
            if (closeViewerOnSuccess) viewModel.closeViewer()
        }
        closeViewerOnSuccess = false
        viewModel.refresh()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.message) {
        val text = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.consumeMessage()
    }
    LaunchedEffect(state.tab, state.folderKey, state.searching) {
        selectionMode = false
        selected = emptySet()
    }

    val onAlbumsRoot = state.tab == "albums" && state.folderKey == null && !state.searching
    val shown = state.browseList()
    val photosHome = state.tab == "photos" && state.folderKey == null && !state.searching && !selectionMode
    val viewerKey = when {
        state.searching -> "search"
        state.folderKey != null -> state.folderKey!!
        else -> "all"
    }
    val inTrash = state.folderKey == "trash"
    val granted = state.access != Access.None
    val showViewer = state.viewerUri != null && granted
    LaunchedEffect(showViewer, state.loading, state.active, state.trashed, state.query, state.viewerKey) {
        if (showViewer && !state.loading && state.viewerList().isEmpty()) {
            viewModel.closeViewer()
        }
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !showViewer && !darkPage
        controller.isAppearanceLightNavigationBars = !showViewer && !darkPage
    }

    val interceptBack = editingPhoto != null || selectionMode || state.searching || state.folderKey != null || showViewer
    BackHandler(enabled = interceptBack) {
        when {
            editingPhoto != null -> editingPhoto = null
            showViewer -> viewModel.closeViewer()
            selectionMode -> {
                selectionMode = false
                selected = emptySet()
            }
            state.searching -> viewModel.closeSearch()
            state.folderKey != null -> {
                if (state.folderKey == "private") viewModel.lockPrivate()
                viewModel.closeFolder()
            }
        }
    }

    fun openPrivateAlbum() {
        when {
            !state.hasPrivatePin -> privatePrompt = PrivatePrompt.Setup
            state.privateUnlocked -> viewModel.openFolder("private", "Private")
            else -> privatePrompt = PrivatePrompt.Unlock
        }
    }

    fun launchBiometricUnlock() {
        val activity = context as? androidx.fragment.app.FragmentActivity ?: return
        val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
        val prompt = androidx.biometric.BiometricPrompt(
            activity,
            executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: androidx.biometric.BiometricPrompt.AuthenticationResult,
                ) {
                    viewModel.unlockPrivateBiometric()
                    privatePrompt = null
                    viewModel.openFolder("private", "Private")
                }
            },
        )
        prompt.authenticate(
            androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Private")
                .setSubtitle("Confirm it's you to open Private")
                .setNegativeButtonText("Use PIN")
                .build(),
        )
    }

    fun launch(failure: String, closeAfter: Boolean = false, sender: () -> IntentSender) {
        try {
            closeViewerOnSuccess = closeAfter
            senderLauncher.launch(IntentSenderRequest.Builder(sender()).build())
        } catch (error: Exception) {
            closeViewerOnSuccess = false
            android.util.Log.e("Album", failure, error)
            Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
        }
    }

    fun selectedFiles(): List<MediaFile> = shown.filter { it.key in selected }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = colors.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (granted && !showViewer) {
                    Column(
                        Modifier
                            .background(colors.background)
                            .statusBarsPadding(),
                    ) {
                        when {
                            selectionMode && !onAlbumsRoot -> SelectionTopBar(
                                count = selected.size,
                                allSelected = shown.isNotEmpty() && shown.all { it.key in selected },
                                onClose = {
                                    selectionMode = false
                                    selected = emptySet()
                                },
                                onSelectAll = {
                                    selected = if (shown.all { it.key in selected }) {
                                        emptySet()
                                    } else {
                                        shown.map { it.key }.toSet()
                                    }
                                    if (selected.isEmpty()) selectionMode = false
                                },
                            )
                            state.searching -> SearchTopBar(
                                query = state.query,
                                onQuery = viewModel::setQuery,
                                onClose = viewModel::closeSearch,
                            )
                            state.folderKey != null -> FolderTopBar(
                                title = state.folderTitle ?: "Album",
                                count = shown.size,
                                onBack = viewModel::closeFolder,
                                onSearch = viewModel::openSearch,
                                onShare = if (inTrash || shown.isEmpty()) {
                                    null
                                } else {
                                    {
                                        val batch = shown.take(50)
                                        if (shown.size > batch.size) {
                                            Toast.makeText(context, "Sharing the first 50 items", Toast.LENGTH_SHORT).show()
                                        }
                                        share(context, batch)
                                    }
                                },
                            )
                            else -> RootTopBar(
                                title = if (state.tab == "albums") "Albums" else "Photos",
                                albumsMenu = state.tab == "albums",
                                menuOpen = menuOpen,
                                multiRow = state.multiRow,
                                sort = state.sort,
                                mediaSort = state.mediaSort,
                                theme = state.theme,
                                onSearch = viewModel::openSearch,
                                onMenu = { menuOpen = it },
                                onToggleLayout = viewModel::toggleLayout,
                                onSort = viewModel::setSort,
                                onMediaSort = viewModel::setMediaSort,
                                onTheme = viewModel::setTheme,
                                onLarger = { viewModel.setColumns(columns - 1) },
                                onSmaller = { viewModel.setColumns(columns + 1) },
                                onPrivate = {
                                    menuOpen = false
                                    openPrivateAlbum()
                                },
                                onStorage = {
                                    menuOpen = false
                                    viewModel.openFolder("large", "Large files")
                                },
                                onExpand = { viewModel.setCollapsed(false) },
                                onCollapse = { viewModel.setCollapsed(true) },
                            )
                        }
                        if (state.access == Access.Partial && !selectionMode) {
                            PartialBanner(
                                onAllow = { permissionLauncher.launch(viewModel.permissions()) },
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (granted && !showViewer && !state.searching && selectionMode && !onAlbumsRoot) {
                    SelectionDock(
                        trash = inTrash,
                        inPrivate = state.folderKey == "private",
                        enabled = selected.isNotEmpty(),
                        onShare = { share(context, selectedFiles()) },
                        onFavorite = {
                            val files = selectedFiles()
                            if (files.isNotEmpty()) {
                                val favorite = files.any { !it.isFavorite }
                                launch("Couldn't update the favorite.") {
                                    viewModel.favoriteSender(files.map { it.uri }, favorite)
                                }
                            }
                        },
                        onAddTo = {
                            if (selected.isNotEmpty()) {
                                moveInstead = false
                                newAlbumName = ""
                                showAddTo = true
                            }
                        },
                        onPrivate = {
                            val keys = selected.toList()
                            if (keys.isEmpty()) return@SelectionDock
                            if (state.folderKey == "private") {
                                viewModel.removeFromPrivate(keys)
                                selectionMode = false
                                selected = emptySet()
                                Toast.makeText(context, "Removed from Private", Toast.LENGTH_SHORT).show()
                            } else if (!state.hasPrivatePin) {
                                privatePrompt = PrivatePrompt.SetupThenHide(keys)
                            } else {
                                viewModel.moveToPrivate(keys)
                                selectionMode = false
                                selected = emptySet()
                                Toast.makeText(context, "Moved to Private", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = {
                            val files = selectedFiles()
                            if (files.isNotEmpty()) {
                                val uris = files.map { it.uri }
                                launch("Couldn't delete that photo.") {
                                    if (inTrash) viewModel.deleteSender(uris) else viewModel.trashSender(uris)
                                }
                            }
                        },
                        onRestore = {
                            val files = selectedFiles()
                            if (files.isNotEmpty()) {
                                launch("Couldn't restore that photo.") {
                                    viewModel.restoreSender(files.map { it.uri })
                                }
                            }
                        },
                    )
                } else if (granted && !showViewer && !state.searching && state.folderKey == null && !selectionMode) {
                    AlbumNavBar(
                        tab = state.tab,
                        onPhotos = { viewModel.setTab("photos") },
                        onAlbums = { viewModel.setTab("albums") },
                    )
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(colors.background),
            ) {
                when {
                    state.access == Access.None -> PermissionScreen(
                        onAllow = { permissionLauncher.launch(viewModel.permissions()) },
                        onSettings = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                            context.startActivity(intent)
                        },
                    )
                    state.loading && state.active.isEmpty() && state.trashed.isEmpty() -> {
                        CircularProgressIndicator(
                            color = Blue,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    state.searching && state.query.trim().length < 2 -> CenterMessage(
                        title = "Search your photos",
                        body = "Try a month, an album name, or a file name. You can also type videos or favorites.",
                    )
                    state.searching && shown.isEmpty() -> CenterMessage(
                        title = "No matches",
                        body = "Nothing on this phone matches “${state.query.trim()}”.",
                    )
                    photosHome && shown.isEmpty() -> Column(Modifier.fillMaxSize()) {
                        LibraryBar(
                            timeline = state.timeline,
                            kind = state.kind,
                            onTimeline = viewModel::setTimeline,
                            onKind = viewModel::setKind,
                        )
                        CenterMessage(
                            title = when (state.kind) {
                                MediaKind.Videos -> "No videos"
                                MediaKind.Photos -> "No photos"
                                MediaKind.All -> "No photos yet"
                            },
                            body = "Pictures and videos you save on this Pixel show up here.",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    !onAlbumsRoot && shown.isEmpty() && state.folderKey == "trash" -> CenterMessage(
                        title = "Recently deleted is empty",
                        body = "Photos you delete stay here for 30 days, then they’re removed from this phone.",
                    )
                    !onAlbumsRoot && shown.isEmpty() && state.folderKey == "private" -> CenterMessage(
                        title = "Private is empty",
                        body = "Move photos or videos here from the viewer or selection bar. They stay hidden from other albums.",
                    )
                    !onAlbumsRoot && shown.isEmpty() && state.folderKey == "large" -> CenterMessage(
                        title = "No large files",
                        body = "Photos and videos over about 8 MB show up here so you can free space.",
                    )
                    state.folderKey == "duplicates" -> {
                        DuplicatesScreen(
                            groups = state.duplicateGroups,
                            scanning = state.scanningDuplicates,
                            selected = selected,
                            selectionMode = selectionMode,
                            onSelectExtras = {
                                val extras = state.duplicateExtras.map { it.key }.toSet()
                                if (extras.isNotEmpty()) {
                                    selectionMode = true
                                    selected = extras
                                }
                            },
                            onOpen = { viewModel.openViewer(it.key, "duplicates") },
                            onToggle = { item ->
                                selected = if (item.key in selected) selected - item.key else selected + item.key
                                if (selected.isEmpty()) selectionMode = false
                            },
                            onStartSelection = { item ->
                                selectionMode = true
                                selected = selected + item.key
                            },
                        )
                    }
                    !onAlbumsRoot && shown.isEmpty() -> CenterMessage(
                        title = "This album is empty",
                        body = "Nothing is saved in this folder right now.",
                    )
                    onAlbumsRoot -> {
                        val groups = rememberGroups(state)
                        AlbumsScreen(
                            groups = groups,
                            pinned = state.pinned,
                            multiRow = state.multiRow,
                            collapsed = state.collapsed,
                            onToggleSection = viewModel::toggleSection,
                            onOpen = { album ->
                                if (album.key == "private") openPrivateAlbum()
                                else viewModel.openFolder(album.key, album.title)
                            },
                            onPin = viewModel::pin,
                            onUnpin = viewModel::unpin,
                            onHide = viewModel::hideAlbum,
                            onUnhide = viewModel::unhideAlbum,
                            onRename = { album, title -> viewModel.renameAlbum(album.key, title) },
                            onDeleteAlbum = { album ->
                                val uris = viewModel.albumItems(album.key).map { it.uri }
                                if (uris.isNotEmpty()) {
                                    launch("Couldn't delete album items.") {
                                        viewModel.trashSender(uris)
                                    }
                                }
                            },
                            onClearCover = viewModel::clearAlbumCover,
                        )
                    }
                    else -> {
                        Column(Modifier.fillMaxSize()) {
                            if (photosHome) {
                                LibraryBar(
                                    timeline = state.timeline,
                                    kind = state.kind,
                                    onTimeline = viewModel::setTimeline,
                                    onKind = viewModel::setKind,
                                )
                            }
                            if (inTrash) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Kept for 30 days",
                                        color = colors.muted,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = {
                                            launch("Couldn't empty Recently deleted.") {
                                                viewModel.deleteSender(state.trashed.map { it.uri })
                                            }
                                        },
                                    ) {
                                        Text("Empty", color = Danger)
                                    }
                                }
                            }
                            MediaGrid(
                                items = shown,
                                selected = selected,
                                selectionMode = selectionMode,
                                showDaysLeft = inTrash,
                                columns = columns,
                                timeline = if (state.folderKey == null && !state.searching) state.timeline else TimelineMode.Day,
                                refreshing = state.refreshing,
                                onRefresh = { viewModel.refresh(pulled = true) },
                                onColumns = viewModel::setColumns,
                                onOpen = { viewModel.openViewer(it.key, viewerKey) },
                                onToggle = { item ->
                                    selected = if (item.key in selected) selected - item.key else selected + item.key
                                    if (selected.isEmpty()) selectionMode = false
                                },
                                onStartSelection = { item ->
                                    selectionMode = true
                                    selected = selected + item.key
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        if (showViewer) {
            val viewerItems = state.viewerList()
            if (viewerItems.isNotEmpty()) {
                ViewerScreen(
                    items = viewerItems,
                    initialKey = state.viewerUri.orEmpty(),
                    trash = state.viewerKey == "trash",
                    onClose = viewModel::closeViewer,
                    onShare = { share(context, listOf(it)) },
                    onFavorite = {
                        launch("Couldn't update the favorite.") {
                            viewModel.favoriteSender(listOf(it.uri), !it.isFavorite)
                        }
                    },
                    onTrash = {
                        launch("Couldn't delete that photo.", closeAfter = true) {
                            viewModel.trashSender(listOf(it.uri))
                        }
                    },
                    onRestore = {
                        launch("Couldn't restore that photo.") { viewModel.restoreSender(listOf(it.uri)) }
                    },
                    onDeleteForever = {
                        launch("Couldn't delete that photo.", closeAfter = true) {
                            viewModel.deleteSender(listOf(it.uri))
                        }
                    },
                    onEdit = { item ->
                        val intent = Intent(Intent.ACTION_EDIT).apply {
                            setDataAndType(item.uri, item.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            clipData = ClipData.newRawUri("", item.uri)
                        }
                        if (intent.resolveActivity(context.packageManager) == null) {
                            viewModel.report("No other app on this phone can edit that file.")
                        } else {
                            context.startActivity(Intent.createChooser(intent, "Edit in another app"))
                        }
                    },
                    onEditInApp = { editingPhoto = it },
                    destinations = destinations,
                    onAddTo = { item, directory, title, move ->
                        scope.launch {
                            try {
                                viewModel.copyToAlbum(item, directory)
                                if (move) {
                                    launch("Couldn't move the original.") {
                                        viewModel.trashSender(listOf(item.uri))
                                    }
                                    Toast.makeText(
                                        context,
                                        "Copied to $title. Allow the prompt to move the original.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    Toast.makeText(context, "Added to $title", Toast.LENGTH_SHORT).show()
                                }
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                Toast.makeText(context, "Couldn't add this to $title", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onTransform = { item, degrees, flip ->
                        scope.launch {
                            try {
                                viewModel.saveTransformed(item, degrees, flip)
                                Toast.makeText(context, "Saved a new copy. The original is unchanged.", Toast.LENGTH_SHORT).show()
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                Toast.makeText(context, "Couldn't save that copy", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onCompress = { item, quality, startMs, endMs ->
                        viewModel.compressVideo(item, quality, startMs, endMs)
                    },
                    albumKey = state.folderKey,
                    onSetCover = { item ->
                        val key = state.folderKey
                        if (key != null) {
                            viewModel.setAlbumCover(key, item.key)
                            Toast.makeText(context, "Album cover updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onMoveToPrivate = { item ->
                        if (!state.hasPrivatePin) {
                            privatePrompt = PrivatePrompt.SetupThenHide(listOf(item.key))
                        } else {
                            viewModel.moveToPrivate(listOf(item.key))
                            viewModel.closeViewer()
                            Toast.makeText(context, "Moved to Private", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRemoveFromPrivate = { item ->
                        viewModel.removeFromPrivate(listOf(item.key))
                        Toast.makeText(context, "Removed from Private", Toast.LENGTH_SHORT).show()
                    },
                    inPrivate = state.folderKey == "private",
                )
            }
        }
        editingPhoto?.let { photo ->
            PhotoEditorScreen(
                item = photo,
                onClose = { editingPhoto = null },
                onSave = { degrees, flip, filter, left, top, right, bottom ->
                    viewModel.saveEdited(photo, degrees, flip, filter, left, top, right, bottom)
                    Toast.makeText(context, "Saved a new copy. The original is unchanged.", Toast.LENGTH_SHORT).show()
                },
            )
        }
        when (val prompt = privatePrompt) {
            PrivatePrompt.Setup, is PrivatePrompt.SetupThenHide -> {
                PinDialog(
                    title = "Create Private PIN",
                    message = "This PIN unlocks your Private album on this phone.",
                    confirmLabel = "Set PIN",
                    onConfirm = { pin ->
                        if (viewModel.setupPrivatePin(pin)) {
                            val hideKeys = (prompt as? PrivatePrompt.SetupThenHide)?.keys.orEmpty()
                            if (hideKeys.isNotEmpty()) {
                                viewModel.moveToPrivate(hideKeys)
                                viewModel.closeViewer()
                                Toast.makeText(context, "Moved to Private", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.unlockPrivateBiometric()
                                viewModel.openFolder("private", "Private")
                            }
                            privatePrompt = null
                            true
                        } else {
                            false
                        }
                    },
                    onDismiss = { privatePrompt = null },
                )
            }
            PrivatePrompt.Unlock -> {
                PinDialog(
                    title = "Unlock Private",
                    message = "Enter your PIN to open Private.",
                    confirmLabel = "Unlock",
                    onConfirm = { pin ->
                        if (viewModel.unlockPrivate(pin)) {
                            privatePrompt = null
                            viewModel.openFolder("private", "Private")
                            true
                        } else {
                            false
                        }
                    },
                    onDismiss = { privatePrompt = null },
                    onBiometric = ::launchBiometricUnlock,
                )
            }
            null -> Unit
        }
        if (showAddTo) {
            val files = selectedFiles()
            val currentPaths = files.map { it.relativePath.trim().trimEnd('/') }.toSet()
            val choices = destinations.filter { destination ->
                val path = destination.directory.trimEnd('/')
                files.size != 1 || path !in currentPaths
            }
            fun sendTo(directoryFor: (MediaFile) -> String, title: String) {
                if (files.isEmpty()) return
                showAddTo = false
                scope.launch {
                    try {
                        viewModel.copyToAlbum(files, directoryFor)
                        if (moveInstead) {
                            launch("Couldn't move the originals.") {
                                viewModel.trashSender(files.map { it.uri })
                            }
                            Toast.makeText(
                                context,
                                "Copied to $title. Allow the prompt to move the originals.",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            Toast.makeText(context, "Added to $title", Toast.LENGTH_SHORT).show()
                        }
                        selectionMode = false
                        selected = emptySet()
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        Toast.makeText(context, "Couldn't add these to $title", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            AddToSheet(
                moveInstead = moveInstead,
                onMoveChange = { moveInstead = it },
                newAlbumName = newAlbumName,
                onNameChange = { newAlbumName = it },
                destinations = choices,
                plural = files.size > 1,
                onCreate = { clean ->
                    sendTo(
                        directoryFor = { item ->
                            if (item.isVideo) "Movies/$clean" else "Pictures/$clean"
                        },
                        title = clean,
                    )
                    newAlbumName = ""
                },
                onPick = { destination ->
                    sendTo({ destination.directory }, destination.title)
                },
                onDismiss = { showAddTo = false },
            )
        }
    }
}

@Composable
private fun rememberGroups(state: UiState) = androidx.compose.runtime.remember(
    state.active,
    state.privateItems,
    state.trashed,
    state.pinned,
    state.hidden,
    state.sort,
    state.duplicateItems,
    state.albumTitles,
    state.albumCovers,
) {
    buildGroups(
        state.active,
        state.trashed,
        state.pinned,
        state.hidden,
        state.sort,
        duplicateItems = state.duplicateItems,
        privateItems = state.privateItems,
        titles = state.albumTitles,
        covers = state.albumCovers,
    )
}

private sealed class PrivatePrompt {
    data object Setup : PrivatePrompt()
    data object Unlock : PrivatePrompt()
    data class SetupThenHide(val keys: List<String>) : PrivatePrompt()
}

@Composable
private fun RootTopBar(
    title: String,
    albumsMenu: Boolean,
    menuOpen: Boolean,
    multiRow: Boolean,
    sort: SortMode,
    mediaSort: MediaSort,
    theme: ThemeMode,
    onSearch: () -> Unit,
    onMenu: (Boolean) -> Unit,
    onToggleLayout: () -> Unit,
    onSort: (SortMode) -> Unit,
    onMediaSort: (MediaSort) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onLarger: () -> Unit,
    onSmaller: () -> Unit,
    onPrivate: () -> Unit,
    onStorage: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
) {
    val colors = LocalAlbumColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = colors.ink,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, "Search", tint = colors.ink)
        }
        Box {
            IconButton(onClick = { onMenu(true) }) {
                Icon(Icons.Outlined.MoreVert, "Options", tint = colors.ink)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenu(false) }) {
                if (albumsMenu) {
                    DropdownMenuItem(
                        text = { Text(if (multiRow) "Single-row layout" else "Multiple-row layout") },
                        onClick = {
                            onMenu(false)
                            onToggleLayout()
                        },
                    )
                    SortRow("Sort by name", sort == SortMode.Name) {
                        onMenu(false)
                        onSort(SortMode.Name)
                    }
                    SortRow("Sort by count", sort == SortMode.Count) {
                        onMenu(false)
                        onSort(SortMode.Count)
                    }
                    SortRow("Sort by recent", sort == SortMode.Recent) {
                        onMenu(false)
                        onSort(SortMode.Recent)
                    }
                    DropdownMenuItem(text = { Text("Expand all") }, onClick = { onMenu(false); onExpand() })
                    DropdownMenuItem(text = { Text("Collapse all") }, onClick = { onMenu(false); onCollapse() })
                } else {
                    SortRow("Newest first", mediaSort == MediaSort.Newest) {
                        onMenu(false)
                        onMediaSort(MediaSort.Newest)
                    }
                    SortRow("Oldest first", mediaSort == MediaSort.Oldest) {
                        onMenu(false)
                        onMediaSort(MediaSort.Oldest)
                    }
                    SortRow("Name", mediaSort == MediaSort.Name) {
                        onMenu(false)
                        onMediaSort(MediaSort.Name)
                    }
                    SortRow("File size", mediaSort == MediaSort.Size) {
                        onMenu(false)
                        onMediaSort(MediaSort.Size)
                    }
                    DropdownMenuItem(text = { Text("Larger thumbnails") }, onClick = { onMenu(false); onLarger() })
                    DropdownMenuItem(text = { Text("Smaller thumbnails") }, onClick = { onMenu(false); onSmaller() })
                }
                SortRow("System theme", theme == ThemeMode.System) {
                    onMenu(false)
                    onTheme(ThemeMode.System)
                }
                SortRow("Light theme", theme == ThemeMode.Light) {
                    onMenu(false)
                    onTheme(ThemeMode.Light)
                }
                SortRow("Dark theme", theme == ThemeMode.Dark) {
                    onMenu(false)
                    onTheme(ThemeMode.Dark)
                }
                DropdownMenuItem(
                    text = { Text("Private") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = colors.ink) },
                    onClick = onPrivate,
                )
                DropdownMenuItem(text = { Text("Storage") }, onClick = onStorage)
            }
        }
    }
}

@Composable
private fun SortRow(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = if (checked) {
            { Icon(Icons.Outlined.Check, null, tint = Blue) }
        } else {
            null
        },
        onClick = onClick,
    )
}

@Composable
private fun FolderTopBar(
    title: String,
    count: Int,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onShare: (() -> Unit)?,
) {
    val colors = LocalAlbumColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.ink)
        }
        Text(
            text = title,
            color = colors.ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatCount(count),
            color = colors.muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(end = 4.dp),
        )
        if (onShare != null) {
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, "Share album", tint = colors.ink)
            }
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, "Search", tint = colors.ink)
        }
    }
}

@Composable
private fun SearchTopBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val colors = LocalAlbumColors.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.ink)
        }
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = colors.ink, fontSize = 18.sp),
            cursorBrush = SolidColor(Blue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text("Search photos", color = colors.muted, fontSize = 18.sp)
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Outlined.Close, "Clear", tint = colors.muted)
            }
        }
    }
}

@Composable
private fun SelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val colors = LocalAlbumColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, "Cancel selection", tint = colors.ink)
        }
        Text(
            text = "$count selected",
            color = colors.ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSelectAll) {
            Text(if (allSelected) "Clear" else "Select all", color = Blue)
        }
    }
}

@Composable
private fun PartialBanner(onAllow: () -> Unit) {
    val colors = LocalAlbumColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.banner)
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Only selected photos are visible",
            color = colors.ink,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAllow) {
            Text("Allow all", color = Blue)
        }
    }
}

@Composable
private fun AlbumNavBar(tab: String, onPhotos: () -> Unit, onAlbums: () -> Unit) {
    val page = LocalAlbumColors.current
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = page.ink,
        selectedTextColor = page.ink,
        unselectedIconColor = page.muted,
        unselectedTextColor = page.muted,
        indicatorColor = Color.Transparent,
    )
    Column {
        HorizontalDivider(thickness = 0.5.dp, color = page.hairline)
        NavigationBar(containerColor = page.background, tonalElevation = 0.dp) {
            NavigationBarItem(
                selected = tab == "photos",
                onClick = onPhotos,
                icon = {
                    Icon(if (tab == "photos") Icons.Filled.Photo else Icons.Outlined.Photo, null)
                },
                label = { Text("Photos") },
                colors = colors,
            )
            NavigationBarItem(
                selected = tab == "albums",
                onClick = onAlbums,
                icon = {
                    Icon(if (tab == "albums") Icons.Filled.GridView else Icons.Outlined.GridView, null)
                },
                label = { Text("Albums") },
                colors = colors,
            )
        }
    }
}

@Composable
private fun SelectionDock(
    trash: Boolean,
    inPrivate: Boolean,
    enabled: Boolean,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onAddTo: () -> Unit,
    onPrivate: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
) {
    val page = LocalAlbumColors.current
    val tint = if (enabled) page.ink else page.muted
    Surface(color = page.background) {
        Column {
            HorizontalDivider(thickness = 0.5.dp, color = page.hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (trash) {
                    DockButton("Restore", Icons.Outlined.Restore, tint, enabled, onRestore, Modifier.weight(1f))
                    DockButton("Delete", Icons.Outlined.Delete, if (enabled) Danger else page.muted, enabled, onDelete, Modifier.weight(1f))
                } else {
                    DockButton("Share", Icons.Outlined.Share, tint, enabled, onShare, Modifier.weight(1f))
                    DockButton("Favorite", Icons.Outlined.Favorite, tint, enabled, onFavorite, Modifier.weight(1f))
                    DockButton("Add to", Icons.Outlined.Add, tint, enabled, onAddTo, Modifier.weight(1f))
                    DockButton(
                        if (inPrivate) "Unhide" else "Private",
                        Icons.Outlined.Lock,
                        tint,
                        enabled,
                        onPrivate,
                        Modifier.weight(1f),
                    )
                    DockButton("Delete", Icons.Outlined.Delete, if (enabled) Danger else page.muted, enabled, onDelete, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DockButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, label, tint = tint)
        }
        Text(label, color = tint, fontSize = 12.sp)
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onSettings: () -> Unit) {
    val colors = LocalAlbumColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Color(0xFFEEF2FF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Photo, null, tint = Blue, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = "Your photos, on this phone",
            color = colors.ink,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Album sorts the pictures and videos already saved on this Pixel, by day and by folder. Nothing is uploaded.",
            color = colors.muted,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onAllow,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("Allow photo access", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        TextButton(onClick = onSettings, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
            Text("Open settings", color = Blue)
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun LibraryBar(
    timeline: TimelineMode,
    kind: MediaKind,
    onTimeline: (TimelineMode) -> Unit,
    onKind: (MediaKind) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimelineMode.entries.forEach { mode ->
            FilterChip(
                selected = timeline == mode,
                onClick = { onTimeline(mode) },
                label = { Text(mode.name) },
            )
        }
        MediaKind.entries.forEach { entry ->
            FilterChip(
                selected = kind == entry,
                onClick = { onKind(entry) },
                label = { Text(entry.name) },
            )
        }
    }
}

private fun share(context: android.content.Context, items: List<MediaFile>) {
    if (items.isEmpty()) return
    val uris = ArrayList(items.map { it.uri })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = items.first().mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val clip = ClipData.newRawUri("photos", uris.first())
    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
    intent.clipData = clip
    context.startActivity(Intent.createChooser(intent, "Share"))
}
