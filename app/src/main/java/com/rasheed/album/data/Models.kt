package com.rasheed.album.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Access { None, Partial, Full }

enum class FolderRole { Camera, Screenshots, ScreenRecordings, Downloads }

enum class TimelineMode {
    Day,
    Month,
    Year,
    ;

    companion object {
        fun from(value: String?): TimelineMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Day
    }
}

enum class MediaSort {
    Newest,
    Oldest,
    Name,
    Size,
    ;

    companion object {
        fun from(value: String?): MediaSort =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Newest
    }
}

enum class MediaKind {
    All,
    Photos,
    Videos,
    ;

    companion object {
        fun from(value: String?): MediaKind =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: All
    }
}

enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    companion object {
        fun from(value: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: System
    }
}

enum class SortMode {
    Name,
    Count,
    Recent,
    ;

    companion object {
        fun from(value: String?): SortMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Count
    }
}

@Immutable
data class MediaFile(
    val key: String,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val takenAt: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: String,
    val bucketName: String,
    val relativePath: String,
    val isFavorite: Boolean,
    val trashed: Boolean,
    val expiresAt: Long,
    val isVideo: Boolean,
    val role: FolderRole?,
    val highRes: Boolean,
    val searchText: String,
)

data class Library(
    val active: List<MediaFile>,
    val trashed: List<MediaFile>,
)

data class DaySection(
    val key: String,
    val title: String,
    val items: List<MediaFile>,
)

data class AlbumDestination(
    val title: String,
    val directory: String,
)

data class AlbumEntry(
    val key: String,
    val title: String,
    val count: Int,
    val sizeBytes: Long,
    val coverUri: String?,
    val coverTakenAt: Long,
    val pinnable: Boolean,
)

data class AlbumGroups(
    val frequent: List<AlbumEntry>,
    val more: List<AlbumEntry>,
    val media: List<AlbumEntry>,
    val hidden: List<AlbumEntry>,
    val other: List<AlbumEntry>,
)

data class ShotInfo(
    val camera: String = "",
    val location: String = "",
)

object Sections {
    const val Frequent = "frequent"
    const val More = "more"
    const val Media = "media"
    const val Other = "other"
    val all = listOf(Frequent, More, Media, Other)
}

fun folderRole(bucketName: String, relativePath: String): FolderRole? {
    val name = bucketName.trim().lowercase(Locale.getDefault())
    val path = relativePath.trim().lowercase(Locale.getDefault())
    return when {
        name == "camera" || path.contains("dcim/camera") -> FolderRole.Camera
        "screenshot" in name || "screenshot" in path -> FolderRole.Screenshots
        "screen recording" in name ||
            "screen recordings" in name ||
            "screenrecording" in name ||
            "screen_record" in name ||
            "screenrecord" in path -> FolderRole.ScreenRecordings
        name == "download" || name == "downloads" || "/download" in path -> FolderRole.Downloads
        else -> null
    }
}

fun groupByDay(items: List<MediaFile>): List<DaySection> {
    if (items.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val sameYear = DateTimeFormatter.ofPattern("MMMM d")
    val otherYear = DateTimeFormatter.ofPattern("MMMM d, yyyy")
    return items.groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }
        .entries
        .sortedByDescending { it.key }
        .map { (date, dayItems) ->
            val title = when (date) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> if (date.year == today.year) date.format(sameYear) else date.format(otherYear)
            }
            DaySection(
                key = date.toString(),
                title = title,
                items = dayItems.sortedByDescending { it.takenAt },
            )
        }
}

fun groupMedia(items: List<MediaFile>, mode: TimelineMode): List<DaySection> {
    return when (mode) {
        TimelineMode.Day -> groupByDay(items)
        TimelineMode.Month -> groupByMonth(items)
        TimelineMode.Year -> groupByYear(items)
    }
}

private fun groupByMonth(items: List<MediaFile>): List<DaySection> {
    if (items.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val now = YearMonth.now(zone)
    val format = DateTimeFormatter.ofPattern("MMMM yyyy")
    return items.groupBy { YearMonth.from(Instant.ofEpochMilli(it.takenAt).atZone(zone)) }
        .entries
        .sortedByDescending { it.key }
        .map { (month, group) ->
            val title = if (month == now) "This month" else month.format(format)
            DaySection(
                key = month.toString(),
                title = title,
                items = group.sortedByDescending { it.takenAt },
            )
        }
}

private fun groupByYear(items: List<MediaFile>): List<DaySection> {
    if (items.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val thisYear = LocalDate.now(zone).year
    return items.groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).year }
        .entries
        .sortedByDescending { it.key }
        .map { (year, group) ->
            val title = if (year == thisYear) "This year" else year.toString()
            DaySection(
                key = year.toString(),
                title = title,
                items = group.sortedByDescending { it.takenAt },
            )
        }
}

fun filterKind(items: List<MediaFile>, kind: MediaKind): List<MediaFile> {
    return when (kind) {
        MediaKind.All -> items
        MediaKind.Photos -> items.filter { !it.isVideo }
        MediaKind.Videos -> items.filter { it.isVideo }
    }
}

fun sortMedia(items: List<MediaFile>, sort: MediaSort): List<MediaFile> {
    return when (sort) {
        MediaSort.Newest -> items.sortedWith(compareByDescending<MediaFile> { it.takenAt }.thenByDescending { it.key })
        MediaSort.Oldest -> items.sortedWith(compareBy<MediaFile> { it.takenAt }.thenBy { it.key })
        MediaSort.Name -> items.sortedWith(
            compareBy<MediaFile, String>(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                .thenByDescending { it.takenAt },
        )
        MediaSort.Size -> items.sortedWith(compareByDescending<MediaFile> { it.sizeBytes }.thenByDescending { it.takenAt })
    }
}

fun largeFiles(active: List<MediaFile>, minBytes: Long = 8L * 1024L * 1024L): List<MediaFile> {
    return active.filter { it.sizeBytes >= minBytes }
        .sortedWith(compareByDescending<MediaFile> { it.sizeBytes }.thenByDescending { it.takenAt })
}

fun itemsFor(
    key: String,
    active: List<MediaFile>,
    trashed: List<MediaFile>,
    privateItems: List<MediaFile> = emptyList(),
): List<MediaFile> {
    return when (key) {
        "all" -> active
        "camera" -> active.filter { it.role == FolderRole.Camera }
        "screenshots" -> active.filter { it.role == FolderRole.Screenshots }
        "downloads" -> active.filter { it.role == FolderRole.Downloads }
        "recordings" -> active.filter { it.role == FolderRole.ScreenRecordings }
        "videos" -> active.filter { it.isVideo }
        "photos" -> active.filter { !it.isVideo }
        "recent" -> {
            val cutoff = System.currentTimeMillis() - 30L * 86_400_000L
            active.filter { it.takenAt >= cutoff }
        }
        "favorites" -> active.filter { it.isFavorite }
        "highres" -> active.filter { it.highRes }
        "large" -> largeFiles(active)
        "duplicates" -> emptyList()
        "private" -> privateItems
        "trash" -> trashed
        else -> {
            if (!key.startsWith("b:")) return emptyList()
            val id = key.removePrefix("b:")
            active.filter { it.bucketId == id && it.role == null }
        }
    }
}

fun searchItems(items: List<MediaFile>, raw: String): List<MediaFile> {
    val query = raw.trim().lowercase(Locale.getDefault())
    if (query.length < 2) return emptyList()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val yesterday = today.minusDays(1)
    return items.filter { item ->
        when (query) {
            "video", "videos" -> item.isVideo
            "photo", "photos", "picture", "pictures" -> !item.isVideo
            "favorite", "favorites", "favourite", "favourites" -> item.isFavorite
            "duplicate", "duplicates" -> false
            else -> {
                query in item.searchText ||
                    (query.length >= 3 && matchesDayWord(item.takenAt, query, zone, today, yesterday))
            }
        }
    }
}

private fun matchesDayWord(
    takenAt: Long,
    query: String,
    zone: ZoneId,
    today: LocalDate,
    yesterday: LocalDate,
): Boolean {
    val date = Instant.ofEpochMilli(takenAt).atZone(zone).toLocalDate()
    return (date == today && "today".startsWith(query)) ||
        (date == yesterday && "yesterday".startsWith(query))
}

fun buildGroups(
    active: List<MediaFile>,
    trashed: List<MediaFile>,
    pinned: List<String>,
    hidden: Set<String>,
    sort: SortMode,
    duplicateItems: List<MediaFile> = emptyList(),
    privateItems: List<MediaFile> = emptyList(),
    titles: Map<String, String> = emptyMap(),
    covers: Map<String, String> = emptyMap(),
): AlbumGroups {
    val camera = active.filter { it.role == FolderRole.Camera }
    val screenshots = active.filter { it.role == FolderRole.Screenshots }
    val downloads = active.filter { it.role == FolderRole.Downloads }
    val recordings = active.filter { it.role == FolderRole.ScreenRecordings }
    val videos = active.filter { it.isVideo }
    val photos = active.filter { !it.isVideo }
    val favorites = active.filter { it.isFavorite }
    val highRes = active.filter { it.highRes }
    val large = largeFiles(active)
    val cutoff = System.currentTimeMillis() - 30L * 86_400_000L
    val recent = active.filter { it.takenAt >= cutoff }

    val folders = active.filter { it.role == null }
        .groupBy { it.bucketId }
        .map { (id, group) ->
            val key = "b:$id"
            val defaultTitle = group.groupingBy { it.bucketName.ifBlank { "Album" } }
                .eachCount()
                .maxBy { it.value }
                .key
            entry(key, titles[key] ?: defaultTitle, group, pinnable = true, covers[key])
        }

    val pinnedKeys = pinned.toSet()
    val pinnedEntries = pinned.mapNotNull { key -> folders.find { it.key == key && it.key !in hidden } }
    val more = folders.filter { it.key !in pinnedKeys && it.key !in hidden }.sortedWith(albumComparator(sort))
    val hiddenEntries = folders.filter { it.key in hidden }.sortedWith(albumComparator(SortMode.Name))

    return AlbumGroups(
        frequent = listOfNotNull(
            entry("all", "All items", active, pinnable = false, covers["all"]),
            camera.takeIf { it.isNotEmpty() }?.let { entry("camera", "Camera", it, false, covers["camera"]) },
            screenshots.takeIf { it.isNotEmpty() }?.let {
                entry("screenshots", "Screenshots", it, false, covers["screenshots"])
            },
            downloads.takeIf { it.isNotEmpty() }?.let {
                entry("downloads", "Downloads", it, false, covers["downloads"])
            },
            recordings.takeIf { it.isNotEmpty() }?.let {
                entry("recordings", "Screen recordings", it, false, covers["recordings"])
            },
            videos.takeIf { it.isNotEmpty() }?.let { entry("videos", "Videos", it, false, covers["videos"]) },
        ) + pinnedEntries,
        more = more,
        media = listOfNotNull(
            photos.takeIf { it.isNotEmpty() }?.let { entry("photos", "Photos", it, false, covers["photos"]) },
            videos.takeIf { it.isNotEmpty() }?.let { entry("videos", "Videos", it, false, covers["videos"]) },
            recent.takeIf { it.isNotEmpty() }?.let {
                entry("recent", "Recently added", it, false, covers["recent"])
            },
            favorites.takeIf { it.isNotEmpty() }?.let {
                entry("favorites", "Favorites", it, false, covers["favorites"])
            },
            highRes.takeIf { it.isNotEmpty() }?.let {
                entry("highres", "High resolution", it, false, covers["highres"])
            },
            large.takeIf { it.isNotEmpty() }?.let { entry("large", "Large files", it, false, covers["large"]) },
            entry("duplicates", "Duplicates", duplicateItems, false, covers["duplicates"]),
        ),
        hidden = hiddenEntries,
        other = listOf(
            // Never use a private photo as the cover — show a blank locked tile instead.
            entry("private", "Private", privateItems, false, coverKey = null).copy(coverUri = null),
            entry("trash", "Recently deleted", trashed, false, covers["trash"]),
        ),
    )
}

private fun albumComparator(sort: SortMode): Comparator<AlbumEntry> = when (sort) {
    SortMode.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    SortMode.Count -> compareByDescending<AlbumEntry> { it.count }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    SortMode.Recent -> compareByDescending { it.coverTakenAt }
}

private fun entry(
    key: String,
    title: String,
    items: List<MediaFile>,
    pinnable: Boolean,
    coverKey: String? = null,
): AlbumEntry {
    val preferred = coverKey?.let { wanted -> items.find { it.key == wanted } }
    val cover = preferred ?: items.maxByOrNull { it.takenAt }
    return AlbumEntry(
        key = key,
        title = title,
        count = items.size,
        sizeBytes = items.sumOf { it.sizeBytes },
        coverUri = cover?.key,
        coverTakenAt = cover?.takenAt ?: 0L,
        pinnable = pinnable,
    )
}
