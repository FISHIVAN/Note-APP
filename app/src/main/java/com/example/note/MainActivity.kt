package com.example.note

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.note.data.AppDatabase
import com.example.note.data.NoteRepository
import com.example.note.ui.NoteApp
import com.example.note.ui.theme.NoteTheme
import com.example.note.viewmodel.NoteViewModel
import com.example.note.viewmodel.NoteViewModelFactory
import com.example.note.viewmodel.TodoViewModel
import com.example.note.viewmodel.TodoViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(this)
        val repository = NoteRepository(database.noteDao(), database.todoDao())
        
        val noteViewModelFactory = NoteViewModelFactory(repository)
        val noteViewModel = ViewModelProvider(this, noteViewModelFactory)[NoteViewModel::class.java]
        
        val todoViewModelFactory = TodoViewModelFactory(repository)
        val todoViewModel = ViewModelProvider(this, todoViewModelFactory)[TodoViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            NoteTheme {
                NoteApp(noteViewModel = noteViewModel, todoViewModel = todoViewModel)
            }
        }
    }
}
