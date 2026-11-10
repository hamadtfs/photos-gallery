package com.rasheed.album

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rasheed.album.ui.AlbumApp
import com.rasheed.album.ui.AlbumViewModel
import com.rasheed.album.ui.theme.AlbumTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AlbumViewModel = viewModel()
            val state = viewModel.ui.collectAsStateWithLifecycle()
            AlbumTheme(state.value.theme) {
                AlbumApp(viewModel)
            }
        }
    }
}
