package com.example.note.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.note.data.Note
import com.example.note.viewmodel.NoteViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditScreen(
    noteId: Long,
    viewModel: NoteViewModel,
    onNavigateUp: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf(TextFieldValue("")) }
    var category by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(emptyList<String>()) }
    var newTag by remember { mutableStateOf("") }
    var currentNote by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(noteId) {
        if (noteId != -1L) {
            val note = viewModel.getNoteById(noteId)
            note?.let {
                currentNote = it
                title = it.title
                content = TextFieldValue(it.content)
                category = it.category ?: ""
                tags = it.tags
            }
        }
    }

    fun insertFormatting(prefix: String, suffix: String = "") {
        val currentText = content.text
        val selection = content.selection
        val selectedText = currentText.substring(selection.start, selection.end)
        
        val newText = currentText.replaceRange(
            selection.start, 
            selection.end, 
            "$prefix$selectedText$suffix"
        )
        
        val newCursorPos = selection.start + prefix.length + selectedText.length + suffix.length
        
        content = TextFieldValue(
            text = newText,
            selection = TextRange(newCursorPos)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (title.isNotBlank() || content.text.isNotBlank()) {
                    if (currentNote != null) {
                        viewModel.updateNote(currentNote!!.copy(
                            title = title, 
                            content = content.text,
                            category = category.ifBlank { null },
                            tags = tags
                        ))
                    } else {
                        viewModel.addNote(
                            title, 
                            content.text, 
                            category.ifBlank { null }, 
                            tags
                        )
                    }
                }
                onNavigateUp()
            }) {
                Icon(Icons.Default.Check, contentDescription = "Save")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Title", style = MaterialTheme.typography.headlineMedium) },
                textStyle = MaterialTheme.typography.headlineMedium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TextField(
                value = category,
                onValueChange = { category = it },
                placeholder = { Text("Category", style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tags Section
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tags.forEach { tag ->
                    AssistChip(
                        onClick = { tags = tags - tag },
                        label = { Text(tag) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
                
                // Simple way to add tags: TextField that adds on space/enter
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextField(
                        value = newTag,
                        onValueChange = { 
                            if (it.endsWith(" ") || it.endsWith("\n")) {
                                val tag = it.trim()
                                if (tag.isNotEmpty() && tag !in tags) {
                                    tags = tags + tag
                                }
                                newTag = ""
                            } else {
                                newTag = it 
                            }
                        },
                        placeholder = { Text("Add tag...") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            // Rich Text Toolbar
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { insertFormatting("**", "**") }) {
                        Icon(Icons.Default.FormatBold, "Bold")
                    }
                    IconButton(onClick = { insertFormatting("_", "_") }) {
                        Icon(Icons.Default.FormatItalic, "Italic")
                    }
                    IconButton(onClick = { insertFormatting("# ", "") }) {
                        Icon(Icons.Default.Title, "Heading")
                    }
                    IconButton(onClick = { insertFormatting("- ", "") }) {
                        Icon(Icons.Default.FormatListBulleted, "List")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("Note content") },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
