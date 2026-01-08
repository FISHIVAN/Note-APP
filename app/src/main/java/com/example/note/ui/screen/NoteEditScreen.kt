package com.example.note.ui.screen

import androidx.compose.foundation.layout.IntrinsicSize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
// import androidx.compose.material.icons.filled.FormatListBulleted
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.note.data.Note
import com.example.note.viewmodel.NoteViewModel

import com.example.note.util.RichTextHelper

import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconToggleButtonColors
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.example.note.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableStateListOf

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt

import com.example.note.ui.utils.BounceIconButton
import com.example.note.ui.utils.BounceFloatingActionButton
import com.example.note.ui.utils.BounceIconToggleButton
import com.example.note.ui.utils.BounceTextButton
import com.example.note.ui.utils.bounceClick

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
fun NoteEditScreen(
    noteId: Long,
    viewModel: NoteViewModel,
    onNavigateUp: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    // Initialize with RichTextHelper to render Markdown as Styled Text
    var content by remember { mutableStateOf(TextFieldValue("")) }
    
    // Undo/Redo Stacks
    // We store (text, annotatedString) pairs or just TextFieldValue.
    // Storing TextFieldValue preserves selection, which is nice but maybe overkill.
    // Let's store TextFieldValue.
    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }
    
    // Debounce for undo stack
    val undoDebounceScope = rememberCoroutineScope()
    var lastSavedContent by remember { mutableStateOf<TextFieldValue?>(null) }
    
    fun addToUndoStack(value: TextFieldValue) {
        if (undoStack.size > 50) undoStack.removeAt(0)
        undoStack.add(value)
        redoStack.clear()
    }
    
    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(content)
            content = prev
        }
    }
    
    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(content)
            content = next
        }
    }
    
    // Intercept content changes to manage undo stack
    fun onContentChange(newValue: TextFieldValue) {
        if (lastSavedContent == null) {
            lastSavedContent = content
            addToUndoStack(content)
        } else {
             // Heuristic: If text length changed significantly or space/enter pressed
             val textDiff = kotlin.math.abs(newValue.text.length - (lastSavedContent?.text?.length ?: 0))
             val isSpaceOrNewline = newValue.text.isNotEmpty() && (newValue.text.last() == ' ' || newValue.text.last() == '\n') && newValue.text.length > (lastSavedContent?.text?.length ?: 0)
             
             if (textDiff > 10 || isSpaceOrNewline) {
                 addToUndoStack(lastSavedContent!!)
                 lastSavedContent = newValue
             }
        }
        content = newValue
    }

    var category by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(emptyList<String>()) }
    var newTag by remember { mutableStateOf("") }
    var currentNote by remember { mutableStateOf<Note?>(null) }
    
    // State to track active styles at cursor position
    var activeStyles by remember { mutableStateOf(setOf<RichTextHelper.RichTextStyle>()) }
    var activeColor by remember { mutableStateOf<Color?>(null) }
    var activeBg by remember { mutableStateOf<Color?>(null) }
    
    var selectedTextColor by remember { mutableStateOf(Color.Red) }
    var selectedHighlightColor by remember { mutableStateOf(Color.Yellow.copy(alpha = 0.5f)) }
    
    var showColorPicker by remember { mutableStateOf(false) }
    var showHighlightPicker by remember { mutableStateOf(false) }

    LaunchedEffect(content.selection) {
        val styles = RichTextHelper.getActiveStyles(content)
        activeStyles = styles.first
        activeColor = styles.second
        activeBg = styles.third
    }
    
    // Use the new toggleStyle logic from RichTextHelper
    fun toggleStyle(style: RichTextHelper.RichTextStyle) {
        addToUndoStack(content) // Save state before style change
        if (content.selection.collapsed && (style == RichTextHelper.RichTextStyle.Bold || style == RichTextHelper.RichTextStyle.Italic)) {
            if (activeStyles.contains(style)) {
                activeStyles = activeStyles - style
            } else {
                activeStyles = activeStyles + style
            }
        } else {
            content = RichTextHelper.toggleStyle(content, style)
        }
    }
    
    fun applyColor(color: Color) {
        addToUndoStack(content)
        content = RichTextHelper.setColor(content, color)
        // showColorPicker = false // No longer close automatically on selection, or maybe yes? 
        // User: "点击图标默认按选中的颜色... 长按进入...". 
        // If we select from picker, we probably want to apply AND set default.
    }
    
    fun applyHighlight(color: Color) {
        addToUndoStack(content)
        content = RichTextHelper.setHighlight(content, color)
    }
    LaunchedEffect(noteId) {
        if (noteId != -1L) {
            val note = viewModel.getNoteById(noteId)
            note?.let {
                currentNote = it
                title = it.title
                // Load and convert raw Markdown content to RichText
                content = TextFieldValue(RichTextHelper.markdownToRichText(it.content))
                category = it.category ?: ""
                tags = it.tags
            }
        }
    }

    // Auto-save logic (debounced)
    LaunchedEffect(content) {
        androidx.compose.runtime.snapshotFlow { content }
            .debounce(1000)
            .collect { value ->
                // Offload heavy parsing to background thread
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val markdown = RichTextHelper.richTextToMarkdown(value.annotatedString)
                    if (currentNote != null && markdown != currentNote!!.content) {
                        viewModel.updateNote(currentNote!!.copy(
                            content = markdown,
                            timestamp = System.currentTimeMillis()
                        ))
                    }
                }
            }
    }



    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                modifier = Modifier.clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
                title = { Text(if (noteId == -1L) stringResource(R.string.new_note) else stringResource(R.string.edit_note)) },
                navigationIcon = {
                    BounceIconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            BounceFloatingActionButton(
                onClick = {
                    if (title.isNotBlank() || content.text.isNotBlank()) {
                        val markdown = RichTextHelper.richTextToMarkdown(content.annotatedString)
                        if (currentNote != null) {
                            viewModel.updateNote(currentNote!!.copy(
                                title = title, 
                                content = markdown,
                                category = category.ifBlank { null },
                                tags = tags
                            ))
                        } else {
                            viewModel.addNote(
                                title, 
                                markdown, 
                                category.ifBlank { null }, 
                                tags
                            )
                        }
                    }
                    onNavigateUp()
                },
                modifier = Modifier.imePadding().padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title Block
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineMedium) },
                    textStyle = MaterialTheme.typography.headlineMedium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
            }
            
            // Rich Text Toolbar
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                FlowRow(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BounceIconToggleButton(
                        checked = activeStyles.contains(RichTextHelper.RichTextStyle.Bold),
                        onCheckedChange = { toggleStyle(RichTextHelper.RichTextStyle.Bold) }
                    ) {
                        Icon(
                            Icons.Default.FormatBold, 
                            stringResource(R.string.bold),
                            tint = if (activeStyles.contains(RichTextHelper.RichTextStyle.Bold)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    BounceIconToggleButton(
                        checked = activeStyles.contains(RichTextHelper.RichTextStyle.Italic),
                        onCheckedChange = { toggleStyle(RichTextHelper.RichTextStyle.Italic) }
                    ) {
                        Icon(
                            Icons.Default.FormatItalic, 
                            stringResource(R.string.italic),
                            tint = if (activeStyles.contains(RichTextHelper.RichTextStyle.Italic)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    BounceIconToggleButton(
                        checked = activeStyles.contains(RichTextHelper.RichTextStyle.Heading),
                        onCheckedChange = { toggleStyle(RichTextHelper.RichTextStyle.Heading) }
                    ) {
                        Icon(
                            Icons.Default.Title, 
                            stringResource(R.string.heading),
                            tint = if (activeStyles.contains(RichTextHelper.RichTextStyle.Heading)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    BounceIconToggleButton(
                        checked = activeStyles.contains(RichTextHelper.RichTextStyle.List),
                        onCheckedChange = { toggleStyle(RichTextHelper.RichTextStyle.List) }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.FormatListBulleted, 
                            stringResource(R.string.list),
                            tint = if (activeStyles.contains(RichTextHelper.RichTextStyle.List)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Color Picker
                    // Long press to open picker, Click to toggle current color
                    val colorInteractionSource = remember { MutableInteractionSource() }
                    val isColorPressed by colorInteractionSource.collectIsPressedAsState()
                    val colorScale by animateFloatAsState(
                        targetValue = if (isColorPressed) 0.9f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "color_scale"
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = colorScale
                                scaleY = colorScale
                            }
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = colorInteractionSource,
                                indication = null,
                                onClick = {
                                    if (activeColor == selectedTextColor) {
                                        applyColor(Color.Unspecified)
                                    } else {
                                        applyColor(selectedTextColor)
                                    }
                                },
                                onLongClick = { showColorPicker = true }
                            )
                            .padding(12.dp)
                    ) {
                         Icon(
                            Icons.Default.FormatColorText, 
                            stringResource(R.string.text_color),
                            tint = selectedTextColor
                        )
                    }
                    
                    // Highlight Picker
                    // Long press to open picker, Click to toggle current highlight
                    val highlightInteractionSource = remember { MutableInteractionSource() }
                    val isHighlightPressed by highlightInteractionSource.collectIsPressedAsState()
                    val highlightScale by animateFloatAsState(
                        targetValue = if (isHighlightPressed) 0.9f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "highlight_scale"
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = highlightScale
                                scaleY = highlightScale
                            }
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = highlightInteractionSource,
                                indication = null,
                                onClick = {
                                    if (activeBg == selectedHighlightColor) {
                                        applyHighlight(Color.Unspecified)
                                    } else {
                                        applyHighlight(selectedHighlightColor)
                                    }
                                },
                                onLongClick = { showHighlightPicker = true }
                            )
                            .padding(12.dp)
                    ) {
                        Icon(
                            Icons.Default.FormatPaint, 
                            stringResource(R.string.highlight_color),
                            tint = selectedHighlightColor.copy(alpha = 1f) // Show full opacity on icon for visibility
                        )
                    }
                }
            }

            // Content Block
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = content,
                    onValueChange = { newValue ->
                        val processed = RichTextHelper.applyDiff(content, newValue, activeStyles, activeColor, activeBg)
                        onContentChange(processed)
                    },
                    placeholder = { Text(stringResource(R.string.note_content)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    // VisualTransformation removed as we are using Rich Text directly
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = true,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp).padding(8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
    
    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = selectedTextColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { 
                selectedTextColor = it
                applyColor(it)
                showColorPicker = false
            }
        )
    }

    if (showHighlightPicker) {
        HighlightPickerDialog(
            initialColor = selectedHighlightColor,
            onDismiss = { showHighlightPicker = false },
            onColorSelected = { 
                selectedHighlightColor = it
                applyHighlight(it)
                showHighlightPicker = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedTabIndex by remember { mutableStateOf(0) } // 0: Standard, 1: Custom
    
    // 16 Standard Colors
    val standardColors = listOf(
        Color.Black, Color.DarkGray, Color.Gray, Color.LightGray,
        Color.Red, Color(0xFFFFA500), Color.Yellow, Color.Green,
        Color.Blue, Color.Cyan, Color.Magenta, Color(0xFF800080), // Purple
        Color(0xFFA52A2A), // Brown
        Color(0xFFFFC0CB), // Pink
        Color(0xFF008080), // Teal
        Color(0xFF000080)  // Navy
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_color), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preview
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = selectedColor,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.preview_text),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selectedColor.luminance() > 0.5) Color.Black else Color.White
                        )
                    }
                }
                
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {},
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text(stringResource(R.string.standard_color)) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text(stringResource(R.string.custom_color)) }
                    )
                }
                
                if (selectedTabIndex == 0) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.widthIn(max = 280.dp) // Constrain width to center content nicely
                        ) {
                            standardColors.forEach { color ->
                                ColorSwatch(
                                    color = color,
                                    selected = selectedColor == color,
                                    onClick = { selectedColor = color }
                                )
                            }
                        }
                    }
                } else {
                    // RGB Sliders
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RGBSlider(stringResource(R.string.color_red), selectedColor.red, Color.Red) { selectedColor = selectedColor.copy(red = it) }
                        RGBSlider(stringResource(R.string.color_green), selectedColor.green, Color.Green) { selectedColor = selectedColor.copy(green = it) }
                        RGBSlider(stringResource(R.string.color_blue), selectedColor.blue, Color.Blue) { selectedColor = selectedColor.copy(blue = it) }
                    }
                }
            }
        },
        confirmButton = {
            BounceTextButton(onClick = { onColorSelected(selectedColor) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            BounceTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HighlightPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    // 16 Standard Colors
    val standardColors = listOf(
        Color.Black, Color.DarkGray, Color.Gray, Color.LightGray,
        Color.Red, Color(0xFFFFA500), Color.Yellow, Color.Green,
        Color.Blue, Color.Cyan, Color.Magenta, Color(0xFF800080), // Purple
        Color(0xFFA52A2A), // Brown
        Color(0xFFFFC0CB), // Pink
        Color(0xFF008080), // Teal
        Color(0xFF000080)  // Navy
    )
    
    // Default to Black if Unspecified, otherwise use initialColor
    var selectedBaseColor by remember { mutableStateOf(if (initialColor == Color.Unspecified) Color.Black else initialColor.copy(alpha = 1f)) }
    var opacity by remember { mutableStateOf(if (initialColor == Color.Unspecified) 1f else initialColor.alpha) }
    var selectedTabIndex by remember { mutableStateOf(0) } // 0: Standard, 1: Custom
    
    val finalColor = selectedBaseColor.copy(alpha = opacity)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.highlight_color), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preview
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White) // Checkboard pattern would be better but white is ok
                    ) {
                         Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(finalColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.text_content), color = Color.Black, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {},
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text(stringResource(R.string.standard_color)) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text(stringResource(R.string.custom_color)) }
                    )
                }

                if (selectedTabIndex == 0) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            standardColors.forEach { color ->
                                ColorSwatch(
                                    color = color,
                                    selected = selectedBaseColor == color,
                                    onClick = { selectedBaseColor = color }
                                )
                            }
                        }
                    }
                } else {
                     // RGB Sliders
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RGBSlider(stringResource(R.string.color_red), selectedBaseColor.red, Color.Red) { selectedBaseColor = selectedBaseColor.copy(red = it) }
                        RGBSlider(stringResource(R.string.color_green), selectedBaseColor.green, Color.Green) { selectedBaseColor = selectedBaseColor.copy(green = it) }
                        RGBSlider(stringResource(R.string.color_blue), selectedBaseColor.blue, Color.Blue) { selectedBaseColor = selectedBaseColor.copy(blue = it) }
                    }
                }
                
                Text(
                    text = "${stringResource(R.string.opacity)}: ${(opacity * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                androidx.compose.material3.Slider(
                    value = opacity,
                    onValueChange = { 
                         opacity = it
                    },
                    valueRange = 0f..1f,
                    steps = 3, // 0.25, 0.5, 0.75 (implied with 0 and 1 as bounds makes 4 intervals: 0-25, 25-50, 50-75, 75-100. Wait, steps is number of tick marks between start and end. 3 steps means 0, 0.25, 0.5, 0.75, 1.0)
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        confirmButton = {
            BounceTextButton(onClick = { onColorSelected(finalColor) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            BounceTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun RGBSlider(
    label: String,
    value: Float,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(text = "${(value * 255).toInt()}", style = MaterialTheme.typography.bodySmall)
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.2f)
            )
        )
    }
}

