package com.rasheed.album.ui

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import com.rasheed.album.data.Access
import com.rasheed.album.data.CollageLayout
import com.rasheed.album.data.CollageMaker
import com.rasheed.album.data.CompressQuality
import com.rasheed.album.data.DuplicateGroup
import com.rasheed.album.data.DuplicateScanner
import com.rasheed.album.data.MediaFile
import com.rasheed.album.data.MediaKind
import com.rasheed.album.data.MediaRepository
import com.rasheed.album.data.MediaSort
import com.rasheed.album.data.PhotoFilter
import com.rasheed.album.data.PrivateVault
import com.rasheed.album.data.Sections
import com.rasheed.album.data.SortMode
import com.rasheed.album.data.ThemeMode
import com.rasheed.album.data.TimelineMode
import com.rasheed.album.data.VideoCompressor
import com.rasheed.album.data.filterKind
import com.rasheed.album.data.groupMedia
import com.rasheed.album.data.itemsFor
import com.rasheed.album.data.searchItems
import com.rasheed.album.data.sortMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

data class UiState(
    val access: Access = Access.None,
    val loading: Boolean = true,
    val active: List<MediaFile> = emptyList(),
    val privateItems: List<MediaFile> = emptyList(),
    val trashed: List<MediaFile> = emptyList(),
    val tab: String = "albums",
    val folderKey: String? = null,
    val folderTitle: String? = null,
    val searching: Boolean = false,
    val query: String = "",
    val viewerUri: String? = null,
    val viewerKey: String? = null,
    val pinned: List<String> = emptyList(),
    val multiRow: Boolean = true,
    val sort: SortMode = SortMode.Count,
    val collapsed: Set<String> = emptySet(),
    val timeline: TimelineMode = TimelineMode.Day,
    val mediaSort: MediaSort = MediaSort.Newest,
    val kind: MediaKind = MediaKind.All,
    val columns: Int = 0,
    val hidden: Set<String> = emptySet(),
    val albumTitles: Map<String, String> = emptyMap(),
    val albumCovers: Map<String, String> = emptyMap(),
    val theme: ThemeMode = ThemeMode.System,
    val refreshing: Boolean = false,
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val scanningDuplicates: Boolean = false,
    val privateUnlocked: Boolean = false,
    val hasPrivatePin: Boolean = false,
    val message: String? = null,
) {
    val duplicateItems: List<MediaFile>
        get() = duplicateGroups.flatMap { it.items }

    val duplicateExtras: List<MediaFile>
        get() = duplicateGroups.flatMap { it.extras }
}

fun UiState.browseList(): List<MediaFile> {
    if (access == Access.None) return emptyList()
    val raw = when {
        searching -> searchItems(active, query)
        folderKey == "duplicates" -> duplicateItems
        folderKey == "private" -> if (privateUnlocked) privateItems else emptyList()
        folderKey != null -> itemsFor(folderKey, active, trashed, privateItems)
        tab == "photos" -> active
        else -> emptyList()
    }
    val kinded = if (tab == "photos" && folderKey == null && !searching) filterKind(raw, kind) else raw
    if (folderKey == "duplicates") return raw
    val sorted = sortMedia(kinded, mediaSort)
    if (searching || folderKey != null) return sorted
    return groupMedia(sorted, timeline).flatMap { it.items }
}

fun UiState.viewerList(): List<MediaFile> {
    val key = viewerKey ?: return emptyList()
    val raw = when {
        key == "search" -> searchItems(active, query)
        key == "duplicates" -> duplicateItems
        key == "private" -> if (privateUnlocked) privateItems else emptyList()
        else -> itemsFor(key, active, trashed, privateItems)
    }
    val kinded = if (key == "all") filterKind(raw, kind) else raw
    if (key == "duplicates") return raw
    val sorted = sortMedia(kinded, mediaSort)
    if (key == "search" || key != "all") return sorted
    return groupMedia(sorted, timeline).flatMap { it.items }
}

class AlbumViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    private val compressor = VideoCompressor(app)
    private val collageMaker = CollageMaker(app.contentResolver)
    private val vault = PrivateVault(app)
    private val prefs = app.getSharedPreferences("album", Context.MODE_PRIVATE)
    private val generation = AtomicInteger()
    private var watchJob: Job? = null
    private var duplicateJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            watchJob?.cancel()
            watchJob = viewModelScope.launch {
                delay(500)
                refresh()
            }
        }
    }

    private val _ui = MutableStateFlow(readInitial())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        val resolver = app.contentResolver
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        refresh()
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        super.onCleared()
    }

    fun permissions(): Array<String> = repo.permissionsToRequest()

    fun refresh(pulled: Boolean = false) {
        val access = repo.access()
        if (access == Access.None) {
            generation.incrementAndGet()
            duplicateJob?.cancel()
            _ui.update {
                it.copy(
                    access = Access.None,
                    loading = false,
                    refreshing = false,
                    active = emptyList(),
                    privateItems = emptyList(),
                    trashed = emptyList(),
                    duplicateGroups = emptyList(),
                    scanningDuplicates = false,
                    hasPrivatePin = vault.hasPin(),
                )
            }
            return
        }
        val gen = generation.incrementAndGet()
        viewModelScope.launch {
            val showSpinner = _ui.value.active.isEmpty() && _ui.value.trashed.isEmpty()
            if (showSpinner) _ui.update { it.copy(loading = true, access = access) }
            if (pulled) _ui.update { it.copy(refreshing = true, access = access) }
            try {
                val library = withContext(Dispatchers.IO) { repo.load() }
                if (gen != generation.get()) return@launch
                val locked = vault.keys()
                val privateItems = library.active.filter { it.key in locked }
                val active = library.active.filter { it.key !in locked }
                _ui.update {
                    it.copy(
                        access = access,
                        loading = false,
                        refreshing = false,
                        active = active,
                        privateItems = privateItems,
                        trashed = library.trashed,
                        hasPrivatePin = vault.hasPin(),
                    )
                }
                scanDuplicates(active, gen)
            } catch (error: SecurityException) {
                if (gen != generation.get()) return@launch
                Log.e(TAG, "Photo permission was refused while reading the library", error)
                duplicateJob?.cancel()
                _ui.update {
                    it.copy(
                        access = Access.None,
                        loading = false,
                        refreshing = false,
                        active = emptyList(),
                        privateItems = emptyList(),
                        trashed = emptyList(),
                        duplicateGroups = emptyList(),
                        scanningDuplicates = false,
                    )
                }
            } catch (error: Exception) {
                if (gen != generation.get()) return@launch
                Log.e(TAG, "Couldn't read photos", error)
                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        access = access,
                        message = "Couldn't read photos on this phone.",
                    )
                }
            }
        }
    }

    private fun scanDuplicates(active: List<MediaFile>, gen: Int) {
        duplicateJob?.cancel()
        duplicateJob = viewModelScope.launch {
            _ui.update { it.copy(scanningDuplicates = true) }
            val groups = withContext(Dispatchers.IO) {
                DuplicateScanner.find(getApplication<Application>().contentResolver, active)
            }
            if (gen != generation.get()) return@launch
            _ui.update { it.copy(duplicateGroups = groups, scanningDuplicates = false) }
        }
    }

    fun setTab(tab: String) {
        _ui.update { it.copy(tab = tab, folderKey = null, folderTitle = null) }
    }

    fun openFolder(key: String, title: String) {
        _ui.update { it.copy(tab = "albums", folderKey = key, folderTitle = title) }
    }

    fun closeFolder() {
        _ui.update { it.copy(folderKey = null, folderTitle = null) }
    }

    fun openSearch() {
        _ui.update { it.copy(searching = true) }
    }

    fun closeSearch() {
        _ui.update { it.copy(searching = false, query = "") }
    }

    fun setQuery(query: String) {
        _ui.update { it.copy(query = query) }
    }

    fun openViewer(uri: String, key: String) {
        _ui.update { it.copy(viewerUri = uri, viewerKey = key) }
    }

    fun closeViewer() {
        _ui.update { it.copy(viewerUri = null, viewerKey = null) }
    }

    fun toggleLayout() {
        val multi = !_ui.value.multiRow
        prefs.edit().putBoolean(KEY_MULTI, multi).apply()
        _ui.update { it.copy(multiRow = multi) }
    }

    fun setSort(sort: SortMode) {
        prefs.edit().putString(KEY_SORT, sort.name).apply()
        _ui.update { it.copy(sort = sort) }
    }

    fun setTimeline(mode: TimelineMode) {
        prefs.edit().putString(KEY_TIMELINE, mode.name).apply()
        _ui.update { it.copy(timeline = mode) }
    }

    fun setMediaSort(sort: MediaSort) {
        prefs.edit().putString(KEY_MEDIA_SORT, sort.name).apply()
        _ui.update { it.copy(mediaSort = sort) }
    }

    fun setKind(kind: MediaKind) {
        prefs.edit().putString(KEY_KIND, kind.name).apply()
        _ui.update { it.copy(kind = kind) }
    }

    fun setColumns(columns: Int) {
        val next = columns.coerceIn(2, 6)
        prefs.edit().putInt(KEY_COLUMNS, next).apply()
        _ui.update { it.copy(columns = next) }
    }

    fun setTheme(theme: ThemeMode) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _ui.update { it.copy(theme = theme) }
    }

    fun hideAlbum(key: String) {
        if (!key.startsWith("b:")) return
        val nextHidden = _ui.value.hidden + key
        val nextPinned = _ui.value.pinned.filter { it != key }
        prefs.edit()
            .putStringSet(KEY_HIDDEN, nextHidden)
            .putString(KEY_PINNED, nextPinned.joinToString(","))
            .apply()
        _ui.update { it.copy(hidden = nextHidden, pinned = nextPinned) }
    }

    fun unhideAlbum(key: String) {
        val next = _ui.value.hidden - key
        prefs.edit().putStringSet(KEY_HIDDEN, next).apply()
        _ui.update { it.copy(hidden = next) }
    }

    fun renameAlbum(key: String, title: String) {
        if (!key.startsWith("b:")) return
        val clean = title.trim().take(40)
        if (clean.isEmpty()) return
        val next = _ui.value.albumTitles.toMutableMap().apply { put(key, clean) }
        writeTitles(next)
        if (_ui.value.folderKey == key) {
            _ui.update { it.copy(folderTitle = clean) }
        }
    }

    fun clearAlbumTitle(key: String) {
        val next = _ui.value.albumTitles.toMutableMap().apply { remove(key) }
        writeTitles(next)
    }

    fun setAlbumCover(albumKey: String, mediaKey: String) {
        val next = _ui.value.albumCovers.toMutableMap().apply { put(albumKey, mediaKey) }
        writeCovers(next)
    }

    fun clearAlbumCover(albumKey: String) {
        val next = _ui.value.albumCovers.toMutableMap().apply { remove(albumKey) }
        writeCovers(next)
    }

    fun albumItems(key: String): List<MediaFile> =
        itemsFor(key, _ui.value.active, _ui.value.trashed, _ui.value.privateItems)

    fun setupPrivatePin(pin: String): Boolean {
        return try {
            vault.setPin(pin)
            _ui.update { it.copy(hasPrivatePin = true) }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun unlockPrivate(pin: String): Boolean {
        if (!vault.checkPin(pin)) return false
        _ui.update { it.copy(privateUnlocked = true) }
        return true
    }

    fun unlockPrivateBiometric() {
        _ui.update { it.copy(privateUnlocked = true) }
    }

    fun lockPrivate() {
        _ui.update { it.copy(privateUnlocked = false) }
        if (_ui.value.folderKey == "private") {
            _ui.update { it.copy(folderKey = null, folderTitle = null, viewerUri = null, viewerKey = null) }
        }
    }

    fun moveToPrivate(keys: List<String>) {
        if (keys.isEmpty()) return
        vault.add(keys)
        refresh()
    }

    fun removeFromPrivate(keys: List<String>) {
        if (keys.isEmpty()) return
        vault.remove(keys)
        refresh()
    }

    fun toggleSection(id: String) {
        val next = _ui.value.collapsed.toMutableSet()
        if (!next.add(id)) next.remove(id)
        prefs.edit().putStringSet(KEY_COLLAPSED, next).apply()
        _ui.update { it.copy(collapsed = next) }
    }

    fun setCollapsed(collapsed: Boolean) {
        val next = if (collapsed) Sections.all.toSet() else emptySet()
        prefs.edit().putStringSet(KEY_COLLAPSED, next).apply()
        _ui.update { it.copy(collapsed = next) }
    }

    fun pin(key: String) {
        if (!key.startsWith("b:")) return
        val next = listOf(key) + _ui.value.pinned.filter { it != key }
        writePinned(next)
    }

    fun unpin(key: String) {
        writePinned(_ui.value.pinned.filter { it != key })
    }

    fun report(message: String) {
        _ui.update { it.copy(message = message) }
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun favoriteSender(uris: List<Uri>, favorite: Boolean) = repo.favoriteSender(uris, favorite)

    fun trashSender(uris: List<Uri>) = repo.trashSender(uris)

    fun restoreSender(uris: List<Uri>) = repo.restoreSender(uris)

    fun deleteSender(uris: List<Uri>) = repo.deleteSender(uris)

    suspend fun copyToAlbum(item: MediaFile, directory: String) {
        withContext(Dispatchers.IO) { repo.copyIntoAlbum(item, directory) }
        refresh()
    }

    suspend fun copyToAlbum(items: List<MediaFile>, directoryFor: (MediaFile) -> String) {
        withContext(Dispatchers.IO) {
            items.forEach { item -> repo.copyIntoAlbum(item, directoryFor(item)) }
        }
        refresh()
    }

    suspend fun saveTransformed(item: MediaFile, degrees: Int, flipHorizontal: Boolean) {
        withContext(Dispatchers.IO) { repo.saveTransformedCopy(item, degrees, flipHorizontal) }
        refresh()
    }

    suspend fun saveEdited(
        item: MediaFile,
        degrees: Int,
        flipHorizontal: Boolean,
        filter: PhotoFilter,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
    ) {
        withContext(Dispatchers.IO) {
            repo.saveEditedCopy(
                item = item,
                degrees = degrees,
                flipHorizontal = flipHorizontal,
                filter = filter,
                cropLeft = cropLeft,
                cropTop = cropTop,
                cropRight = cropRight,
                cropBottom = cropBottom,
            )
        }
        refresh()
    }

    suspend fun compressVideo(
        item: MediaFile,
        quality: CompressQuality,
        startMs: Long = 0L,
        endMs: Long = item.durationMs,
    ) {
        compressor.compress(item, quality, startMs, endMs)
        refresh()
    }

    suspend fun previewCollage(items: List<MediaFile>, layout: CollageLayout, gapPx: Int): Bitmap {
        return withContext(Dispatchers.IO) {
            collageMaker.render(items, layout, gapPx, outputSize = 1080)
        }
    }

    suspend fun saveCollage(items: List<MediaFile>, layout: CollageLayout, gapPx: Int) {
        withContext(Dispatchers.IO) {
            val bitmap = collageMaker.render(items, layout, gapPx, outputSize = 1600)
            try {
                val takenAt = items.filter { !it.isVideo }.maxOfOrNull { it.takenAt }
                    ?: System.currentTimeMillis()
                collageMaker.save(bitmap, takenAt)
            } finally {
                bitmap.recycle()
            }
        }
        refresh()
    }

    suspend fun shotInfo(item: MediaFile) = withContext(Dispatchers.IO) { repo.shotInfo(item) }

    private fun writePinned(keys: List<String>) {
        prefs.edit().putString(KEY_PINNED, keys.joinToString(",")).apply()
        _ui.update { it.copy(pinned = keys) }
    }

    private fun writeTitles(titles: Map<String, String>) {
        prefs.edit().putString(KEY_TITLES, encodeMap(titles)).apply()
        _ui.update { it.copy(albumTitles = titles) }
    }

    private fun writeCovers(covers: Map<String, String>) {
        prefs.edit().putString(KEY_COVERS, encodeMap(covers)).apply()
        _ui.update { it.copy(albumCovers = covers) }
    }

    private fun readInitial(): UiState {
        val pinned = prefs.getString(KEY_PINNED, "").orEmpty().split(',').filter { it.isNotBlank() }
        val collapsed = prefs.getStringSet(KEY_COLLAPSED, emptySet()).orEmpty()
        return UiState(
            access = repo.access(),
            loading = repo.access() != Access.None,
            pinned = pinned,
            multiRow = prefs.getBoolean(KEY_MULTI, true),
            sort = SortMode.from(prefs.getString(KEY_SORT, null)),
            collapsed = collapsed,
            timeline = TimelineMode.from(prefs.getString(KEY_TIMELINE, null)),
            mediaSort = MediaSort.from(prefs.getString(KEY_MEDIA_SORT, MediaSort.Newest.name)),
            kind = MediaKind.from(prefs.getString(KEY_KIND, null)),
            columns = prefs.getInt(KEY_COLUMNS, 0),
            hidden = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty(),
            albumTitles = decodeMap(prefs.getString(KEY_TITLES, null)),
            albumCovers = decodeMap(prefs.getString(KEY_COVERS, null)),
            theme = ThemeMode.from(prefs.getString(KEY_THEME, null)),
            hasPrivatePin = vault.hasPin(),
        )
    }

    private companion object {
        const val TAG = "Album"
        const val KEY_PINNED = "pinned"
        const val KEY_MULTI = "multi"
        const val KEY_SORT = "sort"
        const val KEY_COLLAPSED = "collapsed"
        const val KEY_TIMELINE = "timeline"
        const val KEY_MEDIA_SORT = "mediaSort"
        const val KEY_KIND = "kind"
        const val KEY_COLUMNS = "columns"
        const val KEY_HIDDEN = "hidden"
        const val KEY_THEME = "theme"
        const val KEY_TITLES = "albumTitles"
        const val KEY_COVERS = "albumCovers"

        fun encodeMap(map: Map<String, String>): String =
            map.entries.joinToString("\u001e") { "${it.key}\u001f${it.value}" }

        fun decodeMap(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.split('\u001e').mapNotNull { pair ->
                val parts = pair.split('\u001f', limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }.toMap()
        }
    }
}
