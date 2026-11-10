package com.rasheed.album.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasheed.album.data.AlbumDestination
import com.rasheed.album.ui.theme.Blue
import com.rasheed.album.ui.theme.LocalAlbumColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToSheet(
    moveInstead: Boolean,
    onMoveChange: (Boolean) -> Unit,
    newAlbumName: String,
    onNameChange: (String) -> Unit,
    destinations: List<AlbumDestination>,
    plural: Boolean,
    onCreate: (String) -> Unit,
    onPick: (AlbumDestination) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAlbumColors.current
    val albums = destinations
        .distinctBy { it.directory.trimEnd('/').lowercase() }
        .sortedBy { it.title.lowercase() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = "Add to album",
                color = colors.ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            ModeToggle(moveInstead = moveInstead, onMoveChange = onMoveChange)
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (moveInstead) {
                    if (plural) {
                        "Copies go into the album you pick. Android then asks before the originals move to Recently deleted."
                    } else {
                        "A copy goes into the album you pick. Android then asks before the original moves to Recently deleted."
                    }
                } else {
                    if (plural) {
                        "Copies go into the album you pick. The originals stay where they are."
                    } else {
                        "A copy goes into the album you pick. The original stays where it is."
                    }
                },
                color = colors.ink.copy(alpha = 0.62f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "New album",
                color = colors.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newAlbumName,
                onValueChange = onNameChange,
                singleLine = true,
                placeholder = {
                    Text("Album name", color = colors.ink.copy(alpha = 0.4f))
                },
                leadingIcon = {
                    Icon(Icons.Outlined.CreateNewFolder, null, tint = colors.ink.copy(alpha = 0.55f))
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink,
                    focusedBorderColor = Blue,
                    unfocusedBorderColor = colors.hairline,
                    cursorColor = Blue,
                    focusedContainerColor = colors.placeholder.copy(alpha = 0.35f),
                    unfocusedContainerColor = colors.placeholder.copy(alpha = 0.35f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            val canCreate = newAlbumName.isNotBlank()
            Button(
                onClick = {
                    val clean = newAlbumName.trim().replace("/", " ").replace("\n", " ")
                    if (clean.isNotEmpty()) onCreate(clean)
                },
                enabled = canCreate,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Blue,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    disabledContainerColor = Blue.copy(alpha = 0.28f),
                    disabledContentColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("Create album", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            if (albums.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "Existing albums",
                    color = colors.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                albums.forEachIndexed { index, destination ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 0.5.dp, color = colors.hairline)
                    }
                    AlbumPickRow(
                        title = destination.title,
                        subtitle = destination.directory.trimEnd('/'),
                        onClick = { onPick(destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeToggle(moveInstead: Boolean, onMoveChange: (Boolean) -> Unit) {
    val colors = LocalAlbumColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.placeholder)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeChip(
            label = "Copy",
            selected = !moveInstead,
            onClick = { onMoveChange(false) },
            modifier = Modifier.weight(1f),
        )
        ModeChip(
            label = "Move",
            selected = moveInstead,
            onClick = { onMoveChange(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAlbumColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.background else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) colors.ink else colors.ink.copy(alpha = 0.55f),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun AlbumPickRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalAlbumColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.placeholder),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Folder, null, tint = Blue, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank() && !subtitle.equals(title, ignoreCase = true)) {
                Text(
                    text = subtitle,
                    color = colors.ink.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
