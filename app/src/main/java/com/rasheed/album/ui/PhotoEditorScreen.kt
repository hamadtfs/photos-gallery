package com.rasheed.album.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasheed.album.data.MediaFile
import com.rasheed.album.data.PhotoFilter
import com.rasheed.album.data.androidColorFilter
import com.rasheed.album.ui.theme.Blue
import com.rasheed.album.ui.theme.LocalAlbumColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PhotoEditorScreen(
    item: MediaFile,
    onClose: () -> Unit,
    onSave: suspend (
        degrees: Int,
        flipHorizontal: Boolean,
        filter: PhotoFilter,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
    ) -> Unit,
) {
    val colors = LocalAlbumColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var degrees by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(PhotoFilter.None) }
    var cropL by remember { mutableFloatStateOf(0f) }
    var cropT by remember { mutableFloatStateOf(0f) }
    var cropR by remember { mutableFloatStateOf(1f) }
    var cropB by remember { mutableFloatStateOf(1f) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onClose)

    LaunchedEffect(item.key) {
        bitmap = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(item.uri).use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            var sample = 1
            val pixels = bounds.outWidth.toLong() * bounds.outHeight.coerceAtLeast(1)
            while (sample < 16 && pixels / (sample.toLong() * sample) > 12_000_000L) sample *= 2
            context.contentResolver.openInputStream(item.uri).use { input ->
                BitmapFactory.decodeStream(
                    input,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, enabled = !saving) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                "Edit photo",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        try {
                            onSave(degrees, false, filter, cropL, cropT, cropR, cropB)
                            onClose()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            error = "Couldn't save the edited copy"
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving && bitmap != null,
            ) {
                Text("Save copy", color = Blue, fontWeight = FontWeight.SemiBold)
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val source = bitmap
            if (source == null) {
                CircularProgressIndicator(color = Blue)
            } else {
                val preview = remember(source, degrees) {
                    if (degrees % 360 == 0) {
                        source
                    } else {
                        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
                        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
                    }
                }
                val paint = remember(filter) {
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        colorFilter = androidColorFilter(filter)
                    }
                }
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(preview.width.toFloat() / preview.height.coerceAtLeast(1)),
                ) {
                    drawContext.canvas.nativeCanvas.drawBitmap(
                        preview,
                        null,
                        android.graphics.RectF(0f, 0f, size.width, size.height),
                        paint,
                    )
                    val left = size.width * cropL
                    val top = size.height * cropT
                    val right = size.width * cropR
                    val bottom = size.height * cropB
                    drawRect(Color.Black.copy(alpha = 0.45f), Offset.Zero, Size(size.width, top))
                    drawRect(Color.Black.copy(alpha = 0.45f), Offset(0f, bottom), Size(size.width, size.height - bottom))
                    drawRect(Color.Black.copy(alpha = 0.45f), Offset(0f, top), Size(left, bottom - top))
                    drawRect(Color.Black.copy(alpha = 0.45f), Offset(right, top), Size(size.width - right, bottom - top))
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }

        if (error != null) {
            Text(error!!, color = Color(0xFFFF8A80), modifier = Modifier.padding(horizontal = 16.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rotate", color = colors.ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { degrees = (degrees + 90) % 360 }) {
                    Icon(Icons.Outlined.RotateRight, "Rotate", tint = colors.ink)
                }
            }
            Text("Crop edges", color = colors.ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            CropSlider("Left", cropL, 0f..(cropR - 0.05f).coerceAtLeast(0f)) { cropL = it }
            CropSlider("Top", cropT, 0f..(cropB - 0.05f).coerceAtLeast(0f)) { cropT = it }
            CropSlider("Right", cropR, (cropL + 0.05f).coerceAtMost(1f)..1f) { cropR = it }
            CropSlider("Bottom", cropB, (cropT + 0.05f).coerceAtMost(1f)..1f) { cropB = it }
            Spacer(Modifier.height(8.dp))
            Text("Filter", color = colors.ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhotoFilter.entries.forEach { option ->
                    val selected = option == filter
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Blue.copy(alpha = 0.18f) else colors.placeholder)
                            .border(
                                width = if (selected) 1.5.dp else 0.dp,
                                color = if (selected) Blue else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { filter = option }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            option.label,
                            color = colors.ink,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            if (saving) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Saving copy…", color = colors.muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun CropSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val colors = LocalAlbumColors.current
    if (range.endInclusive <= range.start) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.muted, fontSize = 12.sp, modifier = Modifier.width(52.dp))
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
    }
}
