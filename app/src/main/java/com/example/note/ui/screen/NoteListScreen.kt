package com.example.note.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.note.data.Note
import com.example.note.viewmodel.NoteViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.IconButton

import androidx.compose.ui.draw.clip

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface

import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

import net.sourceforge.pinyin4j.PinyinHelper

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars

import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.mutableLongStateOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteListScreen(
    viewModel: NoteViewModel,
    onNoteClick: (Long) -> Unit,
    onAddNoteClick: () -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isGridMode by viewModel.isGridMode.collectAsState()
    var active by remember { mutableStateOf(false) }
    
    // Header height state
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableStateOf(0f) }
    val headerHeight = with(density) { headerHeightPx.toDp() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNoteClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Note")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // This handles bottom bar padding if any, but we set insets to 0
        ) {
            // 1. Note List (Content) - Below Header
            AnimatedVisibility(
                visible = !active,
                enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                 NoteListContent(
                    notes = notes,
                    isGridMode = isGridMode,
                    onNoteClick = onNoteClick,
                    onDeleteNote = { viewModel.deleteNote(it) },
                    onEmptySpaceClick = null,
                    topPadding = headerHeight
                 )
            }

            // 2. Header (Title + SearchBar) - On Top
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(1f)
                    .onGloballyPositioned { coordinates ->
                        headerHeightPx = coordinates.size.height.toFloat()
                    },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(bottom = 16.dp)
                ) {
                    AnimatedVisibility(visible = !active) {
                        Text(
                            text = "Notes",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(top = 24.dp, bottom = 16.dp)
                        )
                    }

                    DockedSearchBar(
                        inputField = {
                            androidx.compose.material3.SearchBarDefaults.InputField(
                                query = searchQuery,
                                onQueryChange = { viewModel.onSearchQueryChange(it) },
                                onSearch = { active = false },
                                expanded = active,
                                onExpandedChange = { active = it },
                                placeholder = { Text("Search notes") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.toggleGridMode() }) {
                                        Icon(
                                            imageVector = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                            contentDescription = if (isGridMode) "List view" else "Grid view"
                                        )
                                    }
                                }
                            )
                        },
                        expanded = active,
                        onExpandedChange = { active = it },
                        shape = MaterialTheme.shapes.large,
                        colors = androidx.compose.material3.SearchBarDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        tonalElevation = if (active) 6.dp else 0.dp,
                        shadowElevation = if (active) 6.dp else 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Expanded Content (Search Results)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                             Text(
                                text = if (searchQuery.isBlank()) "All Notes" else "Search Results",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(16.dp, 8.dp),
                                color = MaterialTheme.colorScheme.primary
                             )
                             NoteListContent(
                                notes = notes,
                                isGridMode = isGridMode,
                                onNoteClick = { 
                                    onNoteClick(it)
                                    active = false 
                                },
                                onDeleteNote = { viewModel.deleteNote(it) },
                                onEmptySpaceClick = { active = false },
                                topPadding = 0.dp
                             )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListContent(
    notes: List<Note>,
    isGridMode: Boolean,
    onNoteClick: (Long) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onEmptySpaceClick: (() -> Unit)? = null,
    topPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    val emptySpaceInteractionSource = remember { MutableInteractionSource() }
    
    // Scroll states
    val listState = rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Index Bar state
    var currentLetter by remember { mutableStateOf<Char?>(null) }
    var filterLetter by remember { mutableStateOf<Char?>(null) }
    var showMagnifier by remember { mutableStateOf(false) }
    var lastScrollTime by remember { mutableStateOf(0L) }

    // Filter notes based on selection
    val filteredNotes = remember(notes, filterLetter) {
        val letter = filterLetter
        if (letter == null) {
            notes
        } else {
            notes.filter { note ->
                val title = note.title.trim()
                if (title.isEmpty()) return@filter false
                
                val firstChar = title.first()
                
                if (letter == '#') {
                     // Match non-letter characters (symbols, numbers)
                     // Exclude English letters
                     if (firstChar in 'A'..'Z' || firstChar in 'a'..'z') return@filter false
                     
                     // Exclude Chinese characters (check if they have Pinyin)
                     val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(firstChar)
                     if (pinyinArray != null && pinyinArray.isNotEmpty()) return@filter false
                     
                     true
                } else {
                    if (firstChar.equals(letter, ignoreCase = true)) {
                        true
                    } else {
                        val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(firstChar)
                        if (pinyinArray != null && pinyinArray.isNotEmpty()) {
                            val firstPinyinChar = pinyinArray[0][0]
                            firstPinyinChar.equals(letter, ignoreCase = true)
                        } else {
                            false
                        }
                    }
                }
            }
        }
    }

    if (noteToDelete != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete this note?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        noteToDelete?.let { onDeleteNote(it) }
                        noteToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { noteToDelete = null }) {
                    Text("Cancel")
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        if (filteredNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding)
                    .clickable(
                        interactionSource = emptySpaceInteractionSource,
                        indication = null
                    ) { 
                        if (filterLetter != null) filterLetter = null
                        onEmptySpaceClick?.invoke() 
                    },
                contentAlignment = Alignment.Center
            ) {
                 Text(
                     text = if (filterLetter != null) "No match for $filterLetter" else "No notes found",
                     style = MaterialTheme.typography.bodyLarge
                 )
            }
        } else {
            AnimatedContent(
                targetState = isGridMode to filterLetter,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                            scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                        .togetherWith(fadeOut(animationSpec = tween(90)))
                },
                label = "view_mode_transition"
            ) { (gridMode, _) ->
                if (gridMode) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = topPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = emptySpaceInteractionSource,
                                indication = null
                            ) { 
                                if (filterLetter != null) filterLetter = null
                                onEmptySpaceClick?.invoke() 
                            }
                    ) {
                        items(items = filteredNotes, key = { it.id }) { note ->
                            NoteItem(note = note, onClick = { onNoteClick(note.id) })
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = topPadding),
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = emptySpaceInteractionSource,
                                indication = null
                            ) { 
                                if (filterLetter != null) filterLetter = null
                                onEmptySpaceClick?.invoke() 
                            }
                    ) {
                        items(items = filteredNotes, key = { it.id }) { note ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it == SwipeToDismissBoxValue.EndToStart) {
                                        noteToDelete = note
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        false
                                    } else {
                                        false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    val color = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                        else -> Color.Transparent
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(MaterialTheme.shapes.medium)
                                            .background(color)
                                            .padding(16.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                },
                                enableDismissFromStartToEnd = false
                            ) {
                                 NoteItem(note = note, onClick = { onNoteClick(note.id) })
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
            
        // Alphabet Index Bar
        Box(modifier = Modifier.padding(top = topPadding)) {
            AlphabetIndexBar(
                selectedLetter = currentLetter,
                onLetterSelected = { letter ->
                    currentLetter = letter
                    showMagnifier = true
                    
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastScrollTime > 200) { // Debounce/Throttle
                        lastScrollTime = currentTime
                        filterLetter = letter
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                onFinished = {
                    showMagnifier = false
                    currentLetter = null
                }
            )
        }
            
        // Magnifier
            AnimatedVisibility(
                visible = showMagnifier && currentLetter != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .width(80.dp)
                            .height(80.dp)
                    ) {
                        Text(
                            text = currentLetter.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
    }
}

@Composable
fun AlphabetIndexBar(
    selectedLetter: Char?,
    onLetterSelected: (Char) -> Unit,
    onFinished: () -> Unit
) {
    val letters = ('A'..'Z').toList() + '#'
    val density = LocalDensity.current
    var itemHeight by remember { mutableStateOf(0f) }
    var isVisible by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }
    
    // Hide bar after inactivity
    LaunchedEffect(isVisible, lastInteractionTime) {
        if (isVisible) {
            delay(2000) // Hide after 2 seconds of inactivity
            isVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        // Touch detection area (always visible but transparent)
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isVisible = true
                            lastInteractionTime = System.currentTimeMillis()
                            val index = (offset.y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
                            onLetterSelected(letters[index])
                        },
                        onDragEnd = { 
                            onFinished()
                            // Keep visible briefly after drag ends
                        },
                        onDragCancel = { 
                            onFinished()
                        },
                        onVerticalDrag = { change, _ ->
                            isVisible = true
                            lastInteractionTime = System.currentTimeMillis()
                            val index = (change.position.y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
                            onLetterSelected(letters[index])
                        }
                    )
                }
        )

        // Visible Index Bar
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + scaleIn(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)),
            exit = fadeOut() + scaleOut(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.large
                    )
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                letters.forEach { letter ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                itemHeight = coordinates.size.height.toFloat()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (letter == selectedLetter) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (letter == selectedLetter) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteItem(note: Note, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(note.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            
            if (note.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    note.tags.forEach { tag ->
                        AssistChip(
                            onClick = { },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }
        }
    }
}
