package com.rasheed.album.data

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Keep edited / compressed copies next to the original in timeline sorts. */
object MediaDates {
    private val exifStamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    fun putTakenAt(values: ContentValues, takenAtMs: Long) {
        val ms = takenAtMs.coerceAtLeast(1L)
        val seconds = ms / 1000L
        values.put(MediaStore.MediaColumns.DATE_TAKEN, ms)
        values.put(MediaStore.MediaColumns.DATE_ADDED, seconds)
        values.put(MediaStore.MediaColumns.DATE_MODIFIED, seconds)
    }

    fun clearPendingKeepingDate(takenAtMs: Long): ContentValues {
        return ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
            putTakenAt(this, takenAtMs)
        }
    }

    fun writeJpegExifDate(resolver: ContentResolver, uri: Uri, takenAtMs: Long) {
        if (takenAtMs <= 0L) return
        val stamp = Instant.ofEpochMilli(takenAtMs)
            .atZone(ZoneId.systemDefault())
            .format(exifStamp)
        resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            ExifInterface(pfd.fileDescriptor).apply {
                setAttribute(ExifInterface.TAG_DATETIME, stamp)
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
                setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)
                saveAttributes()
            }
        }
    }
}
