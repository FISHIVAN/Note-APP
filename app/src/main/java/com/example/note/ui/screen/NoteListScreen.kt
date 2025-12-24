package com.example.note.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.note.data.Note
import com.example.note.viewmodel.NoteViewModel

import java.text.DateFormat
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.size

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer

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
import com.example.note.util.RichTextHelper

import androidx.compose.ui.res.stringResource
import com.example.note.R

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Check
import com.example.note.viewmodel.SortOption

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
    val sortOption by viewModel.sortOption.collectAsState()
    var active by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Header height state
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableStateOf(0f) }
    val headerHeight = with(density) { headerHeightPx.toDp() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.9f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "fab_scale"
            )
            FloatingActionButton(
                onClick = onAddNoteClick,
                interactionSource = interactionSource,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_note))
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
                    onPinNote = { viewModel.togglePin(it) },
                    onEmptySpaceClick = null,
                    topPadding = headerHeight,
                    sortOption = sortOption
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
                            text = stringResource(R.string.notes),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(top = 24.dp, bottom = 16.dp)
                        )
                    }

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.94f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "search_bar_scale"
                    )

                    DockedSearchBar(
                        inputField = {
                            androidx.compose.material3.SearchBarDefaults.InputField(
                                query = searchQuery,
                                onQueryChange = { viewModel.onSearchQueryChange(it) },
                                onSearch = { active = false },
                                expanded = active,
                                onExpandedChange = { active = it },
                                placeholder = { Text(stringResource(R.string.search_notes)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = { 
                                            val nextOption = when (sortOption) {
                                                SortOption.MODIFIED_DATE -> SortOption.CREATED_DATE
                                                SortOption.CREATED_DATE -> SortOption.TITLE
                                                SortOption.TITLE -> SortOption.MODIFIED_DATE
                                            }
                                            viewModel.setSortOption(nextOption)
                                            val toastMessage = when (nextOption) {
                                                SortOption.MODIFIED_DATE -> R.string.sort_date_modified
                                                SortOption.CREATED_DATE -> R.string.sort_date_created
                                                SortOption.TITLE -> R.string.sort_title
                                            }
                                            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                                contentDescription = stringResource(R.string.sort_by)
                                            )
                                        }

                                        IconButton(onClick = { viewModel.toggleGridMode() }) {
                                            Icon(
                                                imageVector = if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                                contentDescription = if (isGridMode) stringResource(R.string.list_view) else stringResource(R.string.grid_view)
                                            )
                                        }
                                    }
                                },
                                interactionSource = interactionSource
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
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        // Expanded Content (Search Results)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                             Text(
                                text = if (searchQuery.isBlank()) stringResource(R.string.all_notes) else stringResource(R.string.search_results),
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
                                onPinNote = { viewModel.togglePin(it) },
                                onEmptySpaceClick = { active = false },
                                topPadding = 0.dp,
                                sortOption = sortOption
                             )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NoteListContent(
    notes: List<Note>,
    isGridMode: Boolean,
    onNoteClick: (Long) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onPinNote: (Note) -> Unit,
    onEmptySpaceClick: (() -> Unit)?,
    topPadding: Dp,
    sortOption: SortOption,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    val emptySpaceInteractionSource = remember { MutableInteractionSource() }
    
    // Scroll states
    val listState = rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()
    
    // State to track if we need to scroll to top after pin action
    var scrollToTopPending by remember { mutableStateOf(false) }
    var shouldAnimateScroll by remember { mutableStateOf(true) }

    // Handle pin and scroll to top
    val handlePinAndScroll: (Note) -> Unit = { note ->
        val isPinned = note.isPinned
        // Determine current scroll position
        val firstIndex = if (isGridMode) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
        
        onPinNote(note)
        
        // Optimization logic:
        // 1. If pinning (moving to top) and we are far down (> 3 items), snap to top to avoid visual chaos.
        // 2. If unpinning (moving down), we are usually at top. If so, don't force scroll (avoid conflict).
        // 3. Otherwise, animate smoothly.
        if (!isPinned) { // Action is PIN
            if (firstIndex > 3) {
                shouldAnimateScroll = false
                scrollToTopPending = true
            } else {
                shouldAnimateScroll = true
                scrollToTopPending = true
            }
        } else { // Action is UNPIN
            if (firstIndex > 0) {
                shouldAnimateScroll = true
                scrollToTopPending = true
            } else {
                // Already at top, let the list layout animation handle it naturally.
                // No need to force scroll.
                scrollToTopPending = false
            }
        }
    }

    // Scroll to top when sortOption changes
    LaunchedEffect(sortOption) {
        if (isGridMode) {
            gridState.animateScrollToItem(0)
        } else {
            listState.animateScrollToItem(0)
        }
    }
    
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

    // Handle auto scroll when data changes due to pin action
    LaunchedEffect(filteredNotes) {
        if (scrollToTopPending) {
            // Small delay to ensure list layout is updated with new order
            // If snapping, we can be faster.
            delay(if (shouldAnimateScroll) 100 else 50)
            
            if (isGridMode) {
                if (shouldAnimateScroll) gridState.animateScrollToItem(0) else gridState.scrollToItem(0)
            } else {
                if (shouldAnimateScroll) listState.animateScrollToItem(0) else listState.scrollToItem(0)
            }
            scrollToTopPending = false
        }
    }

    if (noteToDelete != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text(stringResource(R.string.delete_note)) },
            text = { Text(stringResource(R.string.confirm_delete_note)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        noteToDelete?.let { onDeleteNote(it) }
                        noteToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { noteToDelete = null }) {
                    Text(stringResource(R.string.cancel))
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
                     text = if (filterLetter != null) stringResource(R.string.no_match_for, filterLetter.toString()) else stringResource(R.string.no_notes_found),
                     style = MaterialTheme.typography.bodyLarge
                 )
            }
        } else {
            AnimatedContent(
                targetState = Pair(isGridMode, filterLetter),
                modifier = Modifier,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) +
                            scaleIn(initialScale = 0.92f, animationSpec = tween(300)))
                        .togetherWith(fadeOut(animationSpec = tween(300)))
                },
                label = "view_mode_transition"
            ) { targetState ->
                val gridMode = targetState.first
                if (gridMode) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = topPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalItemSpacing = 12.dp,
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
                        items(filteredNotes, key = { it.id }) { note ->
                            SwipeableNoteItem(
                                note = note,
                                onNoteClick = onNoteClick,
                                onDeleteNote = { noteToDelete = it },
                                onPinNote = handlePinAndScroll,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 88.dp, top = topPadding),
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
                        items(filteredNotes, key = { it.id }) { note ->
                            SwipeableNoteItem(
                                note = note,
                                onNoteClick = onNoteClick,
                                onDeleteNote = { noteToDelete = it },
                                onPinNote = handlePinAndScroll,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .animateItem(
                                        placementSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                            )
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
                .width(16.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableNoteItem(
    note: Note,
    onNoteClick: (Long) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onPinNote: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val currentNote by rememberUpdatedState(note)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            val isPinned = currentNote.isPinned
            if (it == SwipeToDismissBoxValue.StartToEnd && !isPinned) {
                onPinNote(currentNote)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                return@rememberSwipeToDismissBoxState false // Snap back
            } else if (it == SwipeToDismissBoxValue.EndToStart && isPinned) {
                onPinNote(currentNote)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                return@rememberSwipeToDismissBoxState false // Snap back
            }
            false
        },
        positionalThreshold = { it * 0.35f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.secondaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.tertiaryContainer
                else -> Color.Transparent
            }
            
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.CenterStart
            }
            
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Filled.PushPin
                SwipeToDismissBoxValue.EndToStart -> Icons.Outlined.PushPin
                else -> Icons.Filled.PushPin
            }

            val iconTint = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onSecondaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }

            AnimatedVisibility(
                visible = direction == SwipeToDismissBoxValue.StartToEnd || direction == SwipeToDismissBoxValue.EndToStart,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                 Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color, MaterialTheme.shapes.medium)
                        .padding(horizontal = 20.dp),
                    contentAlignment = alignment
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = if (note.isPinned) "Unpin" else "Pin",
                        tint = iconTint,
                        modifier = Modifier.scale(1.2f)
                    )
                }
            }
        },
        enableDismissFromStartToEnd = !note.isPinned,
        enableDismissFromEndToStart = note.isPinned,
        modifier = modifier,
        content = {
            NoteItem(
                note = note,
                onClick = { onNoteClick(note.id) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDeleteNote(note)
                }
            )
        }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun NoteItem(
    note: Note,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (note.isPinned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (note.isPinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.pinned),
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(45f), // Optional style
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Render rich text preview
            Text(
                text = RichTextHelper.markdownToRichText(note.content),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.timestamp)),
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
