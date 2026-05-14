package com.itlab.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.itlab.ai.OpenVinoEngine
import com.itlab.notes.ui.notesApp
import com.itlab.notes.ui.theme.notesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val engine = OpenVinoEngine(context = this)

        engine.test()
        setContent {
            notesTheme {
                notesApp()
            }
        }
    }
}
