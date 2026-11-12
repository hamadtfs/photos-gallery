package com.rasheed.album.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasheed.album.data.CollageLayout
import com.rasheed.album.data.MediaFile
import com.rasheed.album.data.layoutsFor
import com.rasheed.album.ui.theme.Blue
import com.rasheed.album.ui.theme.LocalAlbumColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CollageScreen(
    items: List<MediaFile>,
    onClose: () -> Unit,
    onPreview: suspend (CollageLayout, Int) -> Bitmap,
    onSave: suspend (CollageLayout, Int) -> Unit,
) {
    val colors = LocalAlbumColors.current
    val photos = remember(items) { items.filter { !it.isVideo }.take(9) }
    val options = remember(photos.size) { layoutsFor(photos.size) }
    var layout by remember(options) { mutableStateOf(options.firstOrNull() ?: CollageLayout.Grid) }
    var gap by remember { mutableFloatStateOf(8f) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onClose)

    LaunchedEffect(layout, gap, photos) {
        if (photos.size < 2 || options.isEmpty()) return@LaunchedEffect
        loading = true
        error = null
        try {
            preview = withContext(Dispatchers.Default) { onPreview(layout, gap.toInt()) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = "Couldn't build this collage"
            preview = null
        } finally {
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
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
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.ink)
            }
            Text(
                "Collage",
                color = colors.ink,
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
                            onSave(layout, gap.toInt())
                            onClose()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            error = "Couldn't save the collage"
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving && !loading && preview != null,
            ) {
                Text("Save", color = Blue, fontWeight = FontWeight.SemiBold)
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                photos.size < 2 -> Text(
                    "Select at least two photos for a collage.",
                    color = colors.muted,
                )
                loading && preview == null -> CircularProgressIndicator(color = Blue)
                preview != null -> {
                    Image(
                        bitmap = preview!!.asImageBitmap(),
                        contentDescription = "Collage preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White),
                    )
                    if (loading) {
                        CircularProgressIndicator(
                            color = Blue,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }

        if (error != null) {
            Text(
                error!!,
                color = Color(0xFFFF8A80),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("Layout", color = colors.ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    val selected = option == layout
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Blue.copy(alpha = 0.16f) else colors.placeholder)
                            .border(
                                width = if (selected) 1.5.dp else 0.dp,
                                color = if (selected) Blue else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable(enabled = !saving) { layout = option }
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
            Spacer(Modifier.height(14.dp))
            Text("Spacing", color = colors.ink, fontWeight = FontWeight.SemiBold)
            Slider(
                value = gap,
                onValueChange = { gap = it },
                valueRange = 0f..24f,
                enabled = !saving,
            )
            Text(
                "${photos.size} photos · saved to Pictures/Collages",
                color = colors.muted,
                fontSize = 12.sp,
            )
            if (saving) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Blue, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Saving collage…", color = colors.muted, fontSize = 13.sp)
                }
            }
        }
    }
}
