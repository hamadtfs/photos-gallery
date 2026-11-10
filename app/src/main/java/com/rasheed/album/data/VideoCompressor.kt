package com.rasheed.album.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class CompressQuality(val label: String, val maxHeight: Int, val hint: String) {
    Small("Smaller file", 480, "About 480p · best for sharing"),
    Medium("Balanced", 720, "About 720p · good quality, smaller size"),
    Large("Higher quality", 1080, "About 1080p · larger file"),
    Original("Keep resolution", 0, "Same size as the source · trim only"),
}

@OptIn(UnstableApi::class)
class VideoCompressor(private val context: Context) {
    private val resolver = context.contentResolver
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun compress(
        item: MediaFile,
        quality: CompressQuality,
        startMs: Long = 0L,
        endMs: Long = item.durationMs,
    ): Uri {
        if (!item.isVideo) error("Only a video can be compressed")
        val duration = item.durationMs.coerceAtLeast(1L)
        val start = startMs.coerceIn(0L, duration - 1L)
        val end = endMs.coerceIn(start + 1L, duration)
        val dir = File(context.cacheDir, "compress").apply { mkdirs() }
        val outFile = File(dir, "compressed_${System.currentTimeMillis()}.mp4")
        if (outFile.exists()) outFile.delete()
        try {
            export(item.uri, outFile, quality, start, end)
            val trimmed = start > 0L || end < duration
            return publish(item, outFile, trimmed)
        } finally {
            outFile.delete()
        }
    }

    private suspend fun export(
        input: Uri,
        output: File,
        quality: CompressQuality,
        startMs: Long,
        endMs: Long,
    ) {
        suspendCancellableCoroutine { cont ->
            val mediaItem = MediaItem.Builder()
                .setUri(input)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build(),
                )
                .build()
            val effects = if (quality.maxHeight > 0) {
                Effects(
                    /* audioProcessors = */ emptyList(),
                    /* videoEffects = */ listOf(Presentation.createForHeight(quality.maxHeight)),
                )
            } else {
                Effects(emptyList(), emptyList())
            }
            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(effects)
                .build()
            val composition = Composition.Builder(
                EditedMediaItemSequence.Builder(edited).build(),
            ).build()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            if (cont.isActive) {
                                cont.resumeWithException(exportException.cause ?: exportException)
                            }
                        }
                    },
                )
                .build()
            cont.invokeOnCancellation {
                mainHandler.post { transformer.cancel() }
            }
            mainHandler.post {
                try {
                    transformer.start(composition, output.absolutePath)
                } catch (error: Exception) {
                    if (cont.isActive) cont.resumeWithException(error)
                }
            }
        }
    }

    private fun publish(item: MediaFile, file: File, trimmed: Boolean): Uri {
        val base = item.displayName.substringBeforeLast('.').ifBlank { "Video" }
        val suffix = if (trimmed) "trimmed" else "compressed"
        val folder = item.relativePath.trim().let { path ->
            when {
                path.endsWith("/") -> path
                path.isBlank() -> "Movies/"
                else -> "$path/"
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$base $suffix.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            MediaDates.putTakenAt(this, item.takenAt)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val dest = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't save the compressed video")
        try {
            resolver.openOutputStream(dest).use { output ->
                file.inputStream().use { input ->
                    if (output == null) error("Couldn't write the compressed video")
                    input.copyTo(output)
                }
            }
            resolver.update(dest, MediaDates.clearPendingKeepingDate(item.takenAt), null, null)
            return dest
        } catch (error: Exception) {
            resolver.delete(dest, null, null)
            throw error
        }
    }
}
