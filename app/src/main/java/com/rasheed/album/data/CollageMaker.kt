package com.rasheed.album.data

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.provider.MediaStore
import kotlin.math.max
import kotlin.math.min

data class CollageCell(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

enum class CollageLayout(val label: String, val minCount: Int, val maxCount: Int) {
    Pair("2 side by side", 2, 2),
    Stack("2 stacked", 2, 2),
    TripleTop("3 · one on top", 3, 3),
    TripleLeft("3 · one on left", 3, 3),
    Quad("4 grid", 4, 4),
    Grid("Grid", 2, 9),
}

fun layoutsFor(count: Int): List<CollageLayout> {
    return CollageLayout.entries.filter { count in it.minCount..it.maxCount }
}

fun cellsFor(layout: CollageLayout, count: Int): List<CollageCell> {
    val n = count.coerceIn(2, 9)
    return when (layout) {
        CollageLayout.Pair -> listOf(
            CollageCell(0f, 0f, 0.5f, 1f),
            CollageCell(0.5f, 0f, 1f, 1f),
        )
        CollageLayout.Stack -> listOf(
            CollageCell(0f, 0f, 1f, 0.5f),
            CollageCell(0f, 0.5f, 1f, 1f),
        )
        CollageLayout.TripleTop -> listOf(
            CollageCell(0f, 0f, 1f, 0.55f),
            CollageCell(0f, 0.55f, 0.5f, 1f),
            CollageCell(0.5f, 0.55f, 1f, 1f),
        )
        CollageLayout.TripleLeft -> listOf(
            CollageCell(0f, 0f, 0.55f, 1f),
            CollageCell(0.55f, 0f, 1f, 0.5f),
            CollageCell(0.55f, 0.5f, 1f, 1f),
        )
        CollageLayout.Quad -> listOf(
            CollageCell(0f, 0f, 0.5f, 0.5f),
            CollageCell(0.5f, 0f, 1f, 0.5f),
            CollageCell(0f, 0.5f, 0.5f, 1f),
            CollageCell(0.5f, 0.5f, 1f, 1f),
        )
        CollageLayout.Grid -> gridCells(n)
    }
}

private fun gridCells(count: Int): List<CollageCell> {
    val cols = when {
        count <= 2 -> 2
        count <= 4 -> 2
        count <= 6 -> 3
        else -> 3
    }
    val rows = (count + cols - 1) / cols
    return List(count) { index ->
        val col = index % cols
        val row = index / cols
        CollageCell(
            left = col / cols.toFloat(),
            top = row / rows.toFloat(),
            right = (col + 1) / cols.toFloat(),
            bottom = (row + 1) / rows.toFloat(),
        )
    }
}

class CollageMaker(private val resolver: ContentResolver) {
    fun render(
        items: List<MediaFile>,
        layout: CollageLayout,
        gapPx: Int,
        outputSize: Int = 1600,
        backgroundColor: Int = Color.WHITE,
    ): Bitmap {
        val photos = items.filter { !it.isVideo }.take(9)
        require(photos.size >= 2) { "Pick at least two photos" }
        val cells = cellsFor(layout, photos.size)
        val canvasBitmap = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(backgroundColor)
        val gap = gapPx.coerceIn(0, 48)
        photos.zip(cells).forEach { (item, cell) ->
            val dest = RectF(
                cell.left * outputSize + gap,
                cell.top * outputSize + gap,
                cell.right * outputSize - gap,
                cell.bottom * outputSize - gap,
            )
            if (dest.width() < 2f || dest.height() < 2f) return@forEach
            val bitmap = decodeForCell(item, dest.width().toInt(), dest.height().toInt()) ?: return@forEach
            try {
                val src = centerCrop(bitmap, dest.width() / dest.height())
                canvas.drawBitmap(bitmap, src, dest, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            } finally {
                bitmap.recycle()
            }
        }
        return canvasBitmap
    }

    fun save(bitmap: Bitmap, takenAt: Long = System.currentTimeMillis()): android.net.Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Collage ${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Collages/")
            MediaDates.putTakenAt(this, takenAt)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val dest = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Couldn't save the collage")
        try {
            resolver.openOutputStream(dest).use { stream ->
                if (stream == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                    error("Couldn't write the collage")
                }
            }
            MediaDates.writeJpegExifDate(resolver, dest, takenAt)
            resolver.update(dest, MediaDates.clearPendingKeepingDate(takenAt), null, null)
            return dest
        } catch (error: Exception) {
            resolver.delete(dest, null, null)
            throw error
        }
    }

    private fun decodeForCell(item: MediaFile, targetW: Int, targetH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(item.uri).use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val need = max(targetW, targetH).coerceAtLeast(1) * 2
        while (sample < 16 &&
            min(bounds.outWidth, bounds.outHeight) / sample > need
        ) {
            sample *= 2
        }
        return resolver.openInputStream(item.uri).use { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }
    }

    private fun centerCrop(bitmap: Bitmap, targetAspect: Float): Rect {
        val srcAspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        return if (srcAspect > targetAspect) {
            val width = (bitmap.height * targetAspect).toInt().coerceAtLeast(1)
            val left = ((bitmap.width - width) / 2).coerceAtLeast(0)
            Rect(left, 0, left + width, bitmap.height)
        } else {
            val height = (bitmap.width / targetAspect).toInt().coerceAtLeast(1)
            val top = ((bitmap.height - height) / 2).coerceAtLeast(0)
            Rect(0, top, bitmap.width, top + height)
        }
    }
}
