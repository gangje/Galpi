package com.galpi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.galpi.app.gallery.GalleryScreen
import com.galpi.app.ui.theme.GalpiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GalpiTheme {
                GalleryScreen()
            }
        }
    }
}
