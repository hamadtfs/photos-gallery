package com.rasheed.album.data

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

enum class PhotoFilter(val label: String) {
    None("Original"),
    Vivid("Vivid"),
    Mono("Mono"),
    Warm("Warm"),
    Cool("Cool"),
    Fade("Fade"),
}

fun colorMatrixFor(filter: PhotoFilter): ColorMatrix {
    return when (filter) {
        PhotoFilter.None -> ColorMatrix()
        PhotoFilter.Vivid -> ColorMatrix().apply { setSaturation(1.45f) }
        PhotoFilter.Mono -> ColorMatrix().apply { setSaturation(0f) }
        PhotoFilter.Warm -> ColorMatrix(
            floatArrayOf(
                1.15f, 0f, 0f, 0f, 12f,
                0f, 1.05f, 0f, 0f, 6f,
                0f, 0f, 0.9f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        PhotoFilter.Cool -> ColorMatrix(
            floatArrayOf(
                0.9f, 0f, 0f, 0f, 0f,
                0f, 1.02f, 0f, 0f, 4f,
                0f, 0f, 1.18f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        PhotoFilter.Fade -> ColorMatrix(
            floatArrayOf(
                0.9f, 0.05f, 0.05f, 0f, 18f,
                0.05f, 0.9f, 0.05f, 0f, 18f,
                0.05f, 0.05f, 0.9f, 0f, 18f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
}

fun androidColorFilter(filter: PhotoFilter): ColorMatrixColorFilter =
    ColorMatrixColorFilter(colorMatrixFor(filter))
