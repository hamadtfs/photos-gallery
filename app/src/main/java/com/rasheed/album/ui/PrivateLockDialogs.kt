package com.rasheed.album.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rasheed.album.ui.theme.Blue
import com.rasheed.album.ui.theme.Danger
import com.rasheed.album.ui.theme.LocalAlbumColors

@Composable
fun PinDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit,
    onBiometric: (() -> Unit)? = null,
) {
    val colors = LocalAlbumColors.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        title = { Text(title, color = colors.ink, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(message, color = colors.muted, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 8 && it.all { ch -> ch.isDigit() }) {
                            pin = it
                            error = null
                        }
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    placeholder = { Text("4–8 digit PIN", color = colors.ink.copy(alpha = 0.4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.ink,
                        unfocusedTextColor = colors.ink,
                        focusedBorderColor = Blue,
                        unfocusedBorderColor = colors.ink.copy(alpha = 0.25f),
                        cursorColor = Blue,
                    ),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = Danger, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pin.length !in 4..8) {
                        error = "Enter 4 to 8 digits"
                    } else if (!onConfirm(pin)) {
                        error = "Incorrect PIN"
                        pin = ""
                    }
                },
            ) {
                Text(confirmLabel, color = Blue)
            }
        },
        dismissButton = {
            Column {
                if (onBiometric != null) {
                    TextButton(onClick = onBiometric) {
                        Text("Use biometrics", color = colors.ink)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = colors.ink)
                }
            }
        },
    )
}
