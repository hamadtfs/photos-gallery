package com.rasheed.album.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

private val fullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy · h:mm a")
private val viewerDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
private val viewerTime: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

fun formatCount(count: Int): String = "%,d".format(Locale.getDefault(), count)

fun formatFullDate(millis: Long): String {
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(fullDate)
}

fun formatViewerDate(millis: Long): String {
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(viewerDate)
}

fun formatViewerTime(millis: Long): String {
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(viewerTime)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

fun formatKind(mime: String): String {
    val type = mime.lowercase(Locale.getDefault())
    return when {
        "jpeg" in type || type.endsWith("/jpg") -> "JPEG"
        "png" in type -> "PNG"
        "webp" in type -> "WebP"
        "heif" in type || "heic" in type -> "HEIF"
        "gif" in type -> "GIF"
        "mp4" in type -> "MP4"
        "webm" in type -> "WebM"
        "mpeg" in type -> "MPEG"
        "quicktime" in type -> "MOV"
        else -> mime.substringAfter('/', mime).uppercase(Locale.getDefault())
    }
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun resolutionLabel(item: MediaFile): String {
    if (item.width <= 0 || item.height <= 0) return ""
    return "${item.width} × ${item.height}"
}

fun displayPath(item: MediaFile): String {
    val relative = item.relativePath.trim().trim('/')
    return if (relative.isBlank()) item.displayName else "$relative/${item.displayName}"
}

fun daysLeftShort(expiresAt: Long, now: Long = System.currentTimeMillis()): String? {
    if (expiresAt <= 0L) return null
    val days = ceil((expiresAt - now) / 86_400_000.0).toInt().coerceAtLeast(0)
    return "${days}d"
}
