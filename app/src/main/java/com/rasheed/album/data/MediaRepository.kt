package com.rasheed.album.data

import android.Manifest
import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class MediaRepository(private val app: Application) {
    private val resolver = app.contentResolver

    fun access(): Access {
        fun granted(permission: String): Boolean {
            return ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
        }
        return when {
            Build.VERSION.SDK_INT >= 34 -> {
                val images = granted(Manifest.permission.READ_MEDIA_IMAGES)
                val videos = granted(Manifest.permission.READ_MEDIA_VIDEO)
                val selected = granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                when {
                    images && videos -> Access.Full
                    images || videos || selected -> Access.Partial
                    else -> Access.None
                }
            }
            Build.VERSION.SDK_INT >= 33 -> {
                val images = granted(Manifest.permission.READ_MEDIA_IMAGES)
                val videos = granted(Manifest.permission.READ_MEDIA_VIDEO)
                when {
                    images && videos -> Access.Full
                    images || videos -> Access.Partial
                    else -> Access.None
                }
            }
            else -> {
                if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) Access.Full else Access.None
            }
        }
    }

    fun permissionsToRequest(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun load(): Library {
        val images = query(
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection = imageProjection,
            video = false,
        )
        val videos = query(
            uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection = videoProjection,
            video = true,
        )
        val (trashed, active) = (images + videos)
            .distinctBy { it.key }
            .partition { it.trashed }
        return Library(
            active = active.sortedByDescending { it.takenAt },
            trashed = trashed.sortedByDescending { it.takenAt },
        )
    }

    fun favoriteSender(uris: List<Uri>, favorite: Boolean) =
        MediaStore.createFavoriteRequest(resolver, uris, favorite).intentSender

    fun trashSender(uris: List<Uri>) =
        MediaStore.createTrashRequest(resolver, uris, true).intentSender

    fun restoreSender(uris: List<Uri>) =
        MediaStore.createTrashRequest(resolver, uris, false).intentSender

    fun deleteSender(uris: List<Uri>) =
        MediaStore.createDeleteRequest(resolver, uris).intentSender

    fun copyIntoAlbum(item: MediaFile, directory: String) {
        val folder = if (directory.endsWith("/")) directory else "$directory/"
        val collection = if (item.isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            MediaDates.putTakenAt(this, item.takenAt)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val dest = resolver.insert(collection, values) ?: error("Couldn't create a copy in that album")
        try {
            resolver.openInputStream(item.uri).use { input ->
                resolver.openOutputStream(dest).use { output ->
                    if (input == null || output == null) error("Couldn't copy this file")
                    input.copyTo(output)
                }
            }
            resolver.update(dest, MediaDates.clearPendingKeepingDate(item.takenAt), null, null)
        } catch (error: Exception) {
            resolver.delete(dest, null, null)
            throw error
        }
    }

    fun saveTransformedCopy(item: MediaFile, degrees: Int, flipHorizontal: Boolean) {
        saveEditedCopy(
            item = item,
            degrees = degrees,
            flipHorizontal = flipHorizontal,
            filter = PhotoFilter.None,
            cropLeft = 0f,
            cropTop = 0f,
            cropRight = 1f,
            cropBottom = 1f,
        )
    }

    /**
     * Crop fractions are normalized 0..1 on the source bitmap after rotate/flip.
     */
    fun saveEditedCopy(
        item: MediaFile,
        degrees: Int,
        flipHorizontal: Boolean,
        filter: PhotoFilter,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
    ) {
        if (item.isVideo) error("Only a photo can be saved as a new copy")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(item.uri).use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        var sample = 1
        val pixels = bounds.outWidth.toLong() * bounds.outHeight.coerceAtLeast(1)
        while (sample < 16 && pixels / (sample.toLong() * sample) > 16_000_000L) sample *= 2
        val source = resolver.openInputStream(item.uri).use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Couldn't read this photo")
        val matrix = Matrix().apply {
            if (flipHorizontal) preScale(-1f, 1f)
            if (degrees % 360 != 0) postRotate(degrees.toFloat())
        }
        var working = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (working != source) source.recycle()
        val left = (cropLeft.coerceIn(0f, 0.95f) * working.width).toInt().coerceIn(0, working.width - 1)
        val top = (cropTop.coerceIn(0f, 0.95f) * working.height).toInt().coerceIn(0, working.height - 1)
        val right = (cropRight.coerceIn(0.05f, 1f) * working.width).toInt().coerceIn(left + 1, working.width)
        val bottom = (cropBottom.coerceIn(0.05f, 1f) * working.height).toInt().coerceIn(top + 1, working.height)
        if (left > 0 || top > 0 || right < working.width || bottom < working.height) {
            val cropped = Bitmap.createBitmap(working, left, top, right - left, bottom - top)
            if (cropped != working) working.recycle()
            working = cropped
        }
        val output = if (filter == PhotoFilter.None) {
            working
        } else {
            val painted = working.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = android.graphics.Canvas(painted)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = androidColorFilter(filter)
            }
            canvas.drawBitmap(working, 0f, 0f, paint)
            if (painted != working) working.recycle()
            painted
        }
        val base = item.displayName.substringBeforeLast('.').ifBlank { "Photo" }
        val folder = item.relativePath.trim().let { path ->
            when {
                path.endsWith("/") -> path
                path.isBlank() -> "Pictures/"
                else -> "$path/"
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$base edited.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            MediaDates.putTakenAt(this, item.takenAt)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val dest = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't save the edited copy")
        try {
            resolver.openOutputStream(dest).use { stream ->
                if (stream == null || !output.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    error("Couldn't write the edited copy")
                }
            }
            MediaDates.writeJpegExifDate(resolver, dest, item.takenAt)
            resolver.update(dest, MediaDates.clearPendingKeepingDate(item.takenAt), null, null)
        } catch (error: Exception) {
            resolver.delete(dest, null, null)
            throw error
        } finally {
            output.recycle()
        }
    }

    private fun query(uri: Uri, projection: Array<String>, video: Boolean): List<MediaFile> {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
        }
        val found = ArrayList<MediaFile>()
        resolver.query(uri, projection, args, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                found += cursor.toMedia(uri, video)
            }
        }
        return found
    }

    private fun android.database.Cursor.toMedia(collection: Uri, video: Boolean): MediaFile {
        val id = long(MediaStore.MediaColumns._ID)
        val name = text(MediaStore.MediaColumns.DISPLAY_NAME).ifBlank { if (video) "Video" else "Photo" }
        val mime = text(MediaStore.MediaColumns.MIME_TYPE).ifBlank { if (video) "video/*" else "image/*" }
        val bucketId = long(MediaStore.MediaColumns.BUCKET_ID).toString()
        val bucketName = text(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        val relativePath = text(MediaStore.MediaColumns.RELATIVE_PATH)
        val takenAt = pickTime(
            dateTaken = long(MediaStore.MediaColumns.DATE_TAKEN),
            dateAdded = long(MediaStore.MediaColumns.DATE_ADDED),
            dateModified = long(MediaStore.MediaColumns.DATE_MODIFIED),
        )
        var width = int(MediaStore.MediaColumns.WIDTH)
        var height = int(MediaStore.MediaColumns.HEIGHT)
        if (!video) {
            val orientation = int(MediaStore.MediaColumns.ORIENTATION)
            if (orientation == 90 || orientation == 270) {
                val swap = width
                width = height
                height = swap
            }
        }
        val role = folderRole(bucketName, relativePath)
        val highRes = !video && max(width, height) >= 4000
        val contentUri = ContentUris.withAppendedId(collection, id)
        return MediaFile(
            key = contentUri.toString(),
            uri = contentUri,
            displayName = name,
            mimeType = mime,
            takenAt = takenAt,
            sizeBytes = long(MediaStore.MediaColumns.SIZE),
            width = width,
            height = height,
            durationMs = if (video) long(MediaStore.Video.VideoColumns.DURATION) else 0L,
            bucketId = bucketId,
            bucketName = bucketName.ifBlank { "Album" },
            relativePath = relativePath,
            isFavorite = int(MediaStore.MediaColumns.IS_FAVORITE) == 1,
            trashed = int(MediaStore.MediaColumns.IS_TRASHED) == 1,
            expiresAt = epochMillis(long(MediaStore.MediaColumns.DATE_EXPIRES)),
            isVideo = video,
            role = role,
            highRes = highRes,
            searchText = searchText(name, bucketName, takenAt),
        )
    }

    fun shotInfo(item: MediaFile): ShotInfo = readShot(resolver, item)

    private fun android.database.Cursor.long(column: String): Long {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index) else 0L
    }

    private fun android.database.Cursor.int(column: String): Int {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getInt(index) else 0
    }

    private fun android.database.Cursor.text(column: String): String {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index).orEmpty() else ""
    }

    private fun pickTime(dateTaken: Long, dateAdded: Long, dateModified: Long): Long {
        val now = System.currentTimeMillis() + 86_400_000L
        val taken = epochMillis(dateTaken)
        if (taken in 1..now) return taken
        val added = epochMillis(dateAdded)
        if (added in 1..now) return added
        val modified = epochMillis(dateModified)
        if (modified > 0L) return modified
        return System.currentTimeMillis()
    }

    private fun epochMillis(value: Long): Long {
        if (value <= 0L) return 0L
        return if (value < 100_000_000_000L) value * 1000L else value
    }

    private fun readShot(resolver: ContentResolver, item: MediaFile): ShotInfo {
        if (item.isVideo) return ShotInfo()
        return try {
            resolver.openInputStream(item.uri).use { input ->
                if (input == null) return ShotInfo()
                val exif = ExifInterface(input)
                val make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty().trim()
                val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty().trim()
                val camera = listOf(make, model).filter { it.isNotBlank() }.distinct().joinToString(" ")
                val point = exif.latLong
                val location = if (point == null) "" else String.format(Locale.US, "%.5f, %.5f", point[0], point[1])
                ShotInfo(camera = camera, location = location)
            }
        } catch (_: Exception) {
            ShotInfo()
        }
    }

    private fun searchText(name: String, bucket: String, takenAt: Long): String {
        val formatted = Instant.ofEpochMilli(takenAt)
            .atZone(ZoneId.systemDefault())
            .format(searchDate)
        return "$name $bucket $formatted".lowercase(Locale.getDefault())
    }

    private companion object {
        val searchDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d yyyy MMM")

        val imageProjection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.MediaColumns.IS_TRASHED,
            MediaStore.MediaColumns.DATE_EXPIRES,
            MediaStore.MediaColumns.ORIENTATION,
        )

        val videoProjection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.MediaColumns.IS_TRASHED,
            MediaStore.MediaColumns.DATE_EXPIRES,
            MediaStore.Video.VideoColumns.DURATION,
        )
    }
}
