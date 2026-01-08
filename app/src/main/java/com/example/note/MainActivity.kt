package com.example.note

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.ViewModelProvider
import com.example.note.data.AppDatabase
import com.example.note.data.NoteRepository
import com.example.note.ui.NoteApp
import com.example.note.ui.theme.NoteTheme
import com.example.note.viewmodel.AiAssistantViewModel
import com.example.note.viewmodel.AiAssistantViewModelFactory
import com.example.note.viewmodel.MapViewModel
import com.example.note.viewmodel.MapViewModelFactory
import com.example.note.viewmodel.NoteViewModel
import com.example.note.viewmodel.NoteViewModelFactory
import com.example.note.viewmodel.TodoViewModel
import com.example.note.viewmodel.TodoViewModelFactory
import com.example.note.data.SettingsRepository
import com.example.note.viewmodel.SettingsViewModel
import com.example.note.viewmodel.SettingsViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(this)
        val repository = NoteRepository(database.noteDao(), database.todoDao())
        val sharedPreferences = getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        val settingsRepository = SettingsRepository(applicationContext)
        
        val noteViewModelFactory = NoteViewModelFactory(repository, sharedPreferences)
        val noteViewModel = ViewModelProvider(this, noteViewModelFactory)[NoteViewModel::class.java]
        
        val todoViewModelFactory = TodoViewModelFactory(repository)
        val todoViewModel = ViewModelProvider(this, todoViewModelFactory)[TodoViewModel::class.java]

        val aiAssistantViewModelFactory = AiAssistantViewModelFactory(application, repository, sharedPreferences)
        val aiAssistantViewModel = ViewModelProvider(this, aiAssistantViewModelFactory)[AiAssistantViewModel::class.java]

        val mapViewModelFactory = MapViewModelFactory(application, repository)
        val mapViewModel = ViewModelProvider(this, mapViewModelFactory)[MapViewModel::class.java]

        val settingsViewModelFactory = SettingsViewModelFactory(settingsRepository)
        val settingsViewModel = ViewModelProvider(this, settingsViewModelFactory)[SettingsViewModel::class.java]

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        // Adapt to high refresh rate screens - REMOVED to prevent SecurityException and ANR
        /*
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                display?.let {
                    val modes = it.supportedModes
                    // Find the mode with the highest refresh rate
                    val maxMode = modes.maxByOrNull { mode -> mode.refreshRate }
                    maxMode?.let { mode ->
                        val layoutParams = window.attributes
                        layoutParams.preferredDisplayModeId = mode.modeId
                        window.attributes = layoutParams
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        */

        setContent {
            NoteTheme {
                NoteApp(
                    noteViewModel = noteViewModel,
                    todoViewModel = todoViewModel,
                    aiAssistantViewModel = aiAssistantViewModel,
                    mapViewModel = mapViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}
