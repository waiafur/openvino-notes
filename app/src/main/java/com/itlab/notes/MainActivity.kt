package com.itlab.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.itlab.ai.OpenVinoEngine
import com.itlab.notes.ui.notesApp
import com.itlab.notes.ui.theme.notesTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val openVinoEngine: OpenVinoEngine by inject()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            openVinoEngine.initialize()
        }
        setContent {
            notesTheme {
                notesApp()
            }
        }
    }
}
