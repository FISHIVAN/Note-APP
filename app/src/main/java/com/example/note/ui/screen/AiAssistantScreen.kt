package com.example.note.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalUriHandler
import com.example.note.R
import com.example.note.data.AiAction
import com.example.note.data.AiMessage
import com.example.note.viewmodel.AiAssistantViewModel
import com.example.note.viewmodel.AiLoadingState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.AutoAwesome
import com.example.note.ui.utils.BounceButton
import com.example.note.ui.utils.BounceOutlinedButton
import com.example.note.ui.utils.BounceIconButton
import com.example.note.ui.utils.bounceClick
import androidx.compose.material3.LocalRippleConfiguration

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.collectIsDraggedAsState

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.unit.Dp
import com.example.note.ui.utils.BounceTextButton

import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel,
    bottomPadding: Dp = 0.dp
) {
    val messages by viewModel.messages.collectAsState()
    val loadingState by viewModel.loadingState.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val modelName by viewModel.modelName.collectAsState()
    val savedModels by viewModel.savedModels.collectAsState()
    val autoSummary by viewModel.autoSummary.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // UI States for FAB and Input
    var isInputExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Header height calculation
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableStateOf(0f) }
    val headerHeight = with(density) { headerHeightPx.toDp() }

    // Calculate effective bottom padding for smooth keyboard transition
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottomPx = with(density) { bottomPadding.toPx() }
    val effectiveBottomPadding = with(density) { 
        (navBottomPx - imeBottom).coerceAtLeast(0f).toDp() 
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Auto-collapse input and hide keyboard on user scroll
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) {
            isInputExpanded = false
            keyboardController?.hide()
        }
    }

    BackHandler(enabled = isInputExpanded) {
        isInputExpanded = false
    }

    if (showSettingsDialog) {
        SettingsDialog(
            initialApiKey = apiKey,
            initialModelName = modelName,
            initialAutoSummary = autoSummary,
            onDismiss = { showSettingsDialog = false },
            onSave = { key, model, summary ->
                viewModel.saveSettings(key, model)
                viewModel.toggleAutoSummary(summary)
                showSettingsDialog = false
            }
        )
    }

    // Keyboard visibility detection
    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding() // Content moves up with keyboard
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Messages List with Animation
                AnimatedContent(
                    targetState = messages.isEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    transitionSpec = {
                        if (targetState) {
                            // Appearing empty state
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        } else {
                            // Disappearing empty state
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        }
                    },
                    label = "content_transition"
                ) { isEmpty ->
                    if (isEmpty) {
                        EmptyStateScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = headerHeight, bottom = 100.dp + bottomPadding),
                            isKeyboardOpen = isKeyboardOpen,
                            onSuggestionClick = { viewModel.sendMessage(it) }
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            reverseLayout = false,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(top = headerHeight + 8.dp, bottom = 100.dp + bottomPadding) // Add bottom padding for floating UI
                        ) {
                            items(messages) { message ->
                                MessageItem(
                                    message = message,
                                    loadingState = loadingState,
                                    isLastMessage = message == messages.last(),
                                    onSaveAsNote = { content -> viewModel.createNoteFromMessage(content) },
                                    onSaveAsTodo = { content -> viewModel.createTodoFromMessage(content) },
                                    onExecuteAction = { action -> viewModel.executeAction(message.id, action) },
                                    onCancelAction = { action -> viewModel.cancelAction(message.id, action) }
                                )
                            }
                            if (loadingState == AiLoadingState.Thinking && messages.lastOrNull()?.isUser == true) {
                                item {
                                    MessageItem(
                                        message = AiMessage(
                                            id = "thinking_placeholder",
                                            content = "",
                                            isUser = false
                                        ),
                                        loadingState = loadingState,
                                        isLastMessage = true,
                                        onSaveAsNote = {},
                                        onSaveAsTodo = {},
                                        onExecuteAction = {},
                                        onCancelAction = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Custom Header matching Note/Todo screens
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(1f)
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .onGloballyPositioned { coordinates ->
                        headerHeightPx = coordinates.size.height.toFloat()
                    },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.ai_assistant_title),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold
                            )
                            
                            var showModelDropdown by remember { mutableStateOf(false) }
                            var modelToDelete by remember { mutableStateOf<String?>(null) }
                            
                            if (modelToDelete != null) {
                                AlertDialog(
                                    onDismissRequest = { modelToDelete = null },
                                    title = { Text(stringResource(R.string.delete_model_title)) },
                                    text = { Text(stringResource(R.string.delete_model_confirmation, modelToDelete ?: "")) },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                modelToDelete?.let {
                                                    viewModel.deleteModel(it)
                                                }
                                                modelToDelete = null
                                                showModelDropdown = false
                                            }
                                        ) {
                                            Text(stringResource(R.string.confirm))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { modelToDelete = null }) {
                                            Text(stringResource(R.string.cancel))
                                        }
                                    }
                                )
                            }
                            
                            Box {
                                Text(
                                    text = modelName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .bounceClick(
                                            onClick = { showModelDropdown = true }
                                        )
                                        .padding(vertical = 2.dp)
                                )
                                
                                DropdownMenu(
                                    expanded = showModelDropdown,
                                    onDismissRequest = { showModelDropdown = false },
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    savedModels.forEach { model ->
                                        // Model Item Animation
                                        AnimatedVisibility(
                                            visible = true,
                                            enter = expandVertically(
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                )
                                            ) + fadeIn(animationSpec = tween(300)),
                                            exit = shrinkVertically(
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                )
                                            ) + fadeOut(animationSpec = tween(300))
                                        ) {
                                            val interactionSource = remember { MutableInteractionSource() }
                                            val isPressed by interactionSource.collectIsPressedAsState()
                                            val scale by animateFloatAsState(
                                                targetValue = if (isPressed) 0.95f else 1f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                ),
                                                label = "item_scale"
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .graphicsLayer {
                                                        scaleX = scale
                                                        scaleY = scale
                                                    }
                                                    .combinedClickable(
                                                        interactionSource = interactionSource,
                                                        indication = null,
                                                        onClick = {
                                                            viewModel.selectModel(model)
                                                            showModelDropdown = false
                                                        },
                                                        onLongClick = {
                                                            modelToDelete = model
                                                        }
                                                    )
                                                    .padding(horizontal = 16.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Text(
                                                    text = model,
                                                    fontWeight = if (model == modelName) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (model == modelName) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BounceIconButton(onClick = { viewModel.clearMessages() }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.new_chat)
                                )
                            }
                            BounceIconButton(onClick = { showSettingsDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.settings)
                                )
                            }
                        }
                    }
                }
            }

            // Floating Input Area & FAB
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = effectiveBottomPadding) // Smooth transition
                    .padding(16.dp), // Padding from screen edges
                contentAlignment = Alignment.BottomEnd
            ) {
                // FAB
                AnimatedVisibility(
                    visible = !isInputExpanded,
                    enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(),
                    exit = scaleOut(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut(),
                    modifier = Modifier.zIndex(1f)
                ) {
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
                    
                    CompositionLocalProvider(LocalRippleConfiguration provides null) {
                        FloatingActionButton(
                            onClick = { isInputExpanded = true },
                            interactionSource = interactionSource,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.type_message))
                        }
                    }
                }

                // Capsule Input
                AnimatedVisibility(
                    visible = isInputExpanded,
                    enter = scaleIn(
                        transformOrigin = TransformOrigin(1f, 1f),
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                    ) + fadeIn(),
                    exit = scaleOut(
                        transformOrigin = TransformOrigin(1f, 1f),
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
                    ) + fadeOut(),
                    modifier = Modifier.fillMaxWidth().zIndex(2f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BounceIconButton(onClick = { isInputExpanded = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cancel),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                placeholder = { Text(stringResource(R.string.type_message)) },
                                maxLines = 4,
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                )
                            )

                            BounceIconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                        keyboardController?.hide()
                                        isInputExpanded = false
                                    }
                                },
                                enabled = loadingState == AiLoadingState.Idle && inputText.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.send),
                                    tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            }
                        }
                        
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: AiMessage,
    loadingState: AiLoadingState,
    isLastMessage: Boolean,
    onSaveAsNote: (String) -> Unit,
    onSaveAsTodo: (String) -> Unit,
    onExecuteAction: (AiAction) -> Unit,
    onCancelAction: (AiAction) -> Unit
) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val elevation = if (message.isUser) 0.dp else 2.dp
    val shape = if (message.isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

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

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = shape,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { },
                    onLongClick = { showMenu = true }
                )
        ) {
            Column {
                // Loading Status Header (Top: Thinking & Answering)
                if (!message.isUser && isLastMessage && (loadingState == AiLoadingState.Thinking || loadingState == AiLoadingState.Answering)) {
                    Row(
                        modifier = Modifier
                            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 4.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val statusText = when (loadingState) {
                            AiLoadingState.Thinking -> stringResource(R.string.ai_status_thinking)
                            AiLoadingState.Answering -> stringResource(R.string.ai_status_answering)
                            else -> ""
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Box {
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            modifier = Modifier.padding(12.dp),
                            color = textColor
                        )
                    } else if (loadingState != AiLoadingState.Thinking) {
                         // Ensure empty bubble has size if not in thinking state
                         Spacer(modifier = Modifier.padding(12.dp))
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        // ... menu items ...
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy)) },
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.content))
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null
                                )
                            }
                        )
                        
                        if (!message.isUser) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_as_note)) },
                                onClick = {
                                    onSaveAsNote(message.content)
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                                        contentDescription = null
                                    )
                                }
                            )
                            
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_as_todo)) },
                                onClick = {
                                    onSaveAsTodo(message.content)
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
                
                // Organizing Status (Bottom)
                if (!message.isUser && isLastMessage && loadingState == AiLoadingState.Organizing) {
                    Row(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.ai_status_organizing),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Render all pending actions
                if (message.pendingActions.isNotEmpty()) {
                    Column(
                        modifier = Modifier.animateContentSize()
                    ) {
                        message.pendingActions.forEach { action ->
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                )
                                Column(modifier = Modifier.padding(12.dp)) {
                                     val title = when(action) {
                                         is AiAction.CreateNote -> "${stringResource(R.string.new_note)}: ${action.title}"
                                         is AiAction.CreateTodo -> stringResource(R.string.new_todo)
                                         is AiAction.CreateMapNote -> "${stringResource(R.string.create_map_note)}: ${action.locationName}"
                                         is AiAction.UpdateNote -> "${stringResource(R.string.edit_note)}: ${action.title}"
                                         is AiAction.UpdateTodo -> stringResource(R.string.edit_todo)
                                     }
                                     Text(
                                         text = title,
                                         style = MaterialTheme.typography.labelLarge,
                                         color = MaterialTheme.colorScheme.primary,
                                         fontWeight = FontWeight.Bold
                                     )
                                     
                                     val content = when(action) {
                                         is AiAction.CreateNote -> action.content
                                         is AiAction.CreateTodo -> action.content
                                         is AiAction.CreateMapNote -> action.content
                                         is AiAction.UpdateNote -> action.content
                                         is AiAction.UpdateTodo -> action.content
                                     }
                                     if (content.isNotBlank()) {
                                         Text(
                                             text = content,
                                             style = MaterialTheme.typography.bodySmall,
                                             color = MaterialTheme.colorScheme.onSurfaceVariant,
                                             maxLines = 3,
                                             overflow = TextOverflow.Ellipsis,
                                             modifier = Modifier.padding(top = 4.dp)
                                         )
                                     }
                                     
                                     Row(
                                         modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                         horizontalArrangement = Arrangement.End,
                                         verticalAlignment = Alignment.CenterVertically
                                     ) {
                                         if (loadingState != AiLoadingState.Idle && loadingState != AiLoadingState.Answering) {
                                             Row(
                                                 verticalAlignment = Alignment.CenterVertically,
                                                 modifier = Modifier.padding(end = 8.dp)
                                             ) {
                                                 CircularProgressIndicator(
                                                     modifier = Modifier.size(12.dp),
                                                     strokeWidth = 2.dp,
                                                     color = MaterialTheme.colorScheme.primary
                                                 )
                                                 Spacer(modifier = Modifier.width(4.dp))
                                                 val statusText = when (loadingState) {
                                                     AiLoadingState.Thinking -> stringResource(R.string.ai_status_thinking)
                                                     AiLoadingState.Organizing -> stringResource(R.string.ai_status_organizing)
                                                     else -> ""
                                                 }
                                                 Text(
                                                     text = statusText,
                                                     style = MaterialTheme.typography.labelSmall,
                                                     color = MaterialTheme.colorScheme.primary
                                                 )
                                             }
                                         }
                                         
                                         BounceOutlinedButton(
                                             onClick = { onCancelAction(action) },
                                             enabled = loadingState == AiLoadingState.Idle,
                                             modifier = Modifier.height(32.dp),
                                             contentPadding = PaddingValues(horizontal = 8.dp)
                                         ) {
                                             Text(stringResource(R.string.cancel), fontSize = 12.sp)
                                         }
                                         Spacer(modifier = Modifier.width(8.dp))
                                         BounceButton(
                                             onClick = { onExecuteAction(action) },
                                             enabled = loadingState == AiLoadingState.Idle,
                                             modifier = Modifier.height(32.dp),
                                             contentPadding = PaddingValues(horizontal = 8.dp)
                                         ) {
                                             Text(stringResource(R.string.save), fontSize = 12.sp)
                                         }
                                     }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    initialApiKey: String,
    initialModelName: String,
    initialAutoSummary: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var modelName by remember { mutableStateOf(initialModelName) }
    var autoSummary by remember { mutableStateOf(initialAutoSummary) }
    val uriHandler = LocalUriHandler.current

    // Animation for Dialog
    val scale = remember { Animatable(0.8f) }
    val alpha = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300)
            )
        }
    }

    AlertDialog(
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        },
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.api_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.get_api_key_guide),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable { uriHandler.openUri("https://cloud.siliconflow.cn/account/ak") }
                        .padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text(stringResource(R.string.model_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ai_auto_summary),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.ai_auto_summary_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSummary,
                        onCheckedChange = { autoSummary = it }
                    )
                }

                if (autoSummary) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ai_auto_summary_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ai_auto_summary_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            BounceTextButton(onClick = { onSave(apiKey, modelName, autoSummary) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            BounceTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedSuggestionChip(
    text: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "chip_scale"
    )

    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        SuggestionChip(
            onClick = onClick,
            label = { Text(text) },
            modifier = Modifier
                .padding(vertical = 4.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            interactionSource = interactionSource
        )
    }
}

@Composable
private fun EmptyStateScreen(
    modifier: Modifier = Modifier,
    isKeyboardOpen: Boolean = false,
    onSuggestionClick: (String) -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ai_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.ai_welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(
            visible = !isKeyboardOpen,
            enter = fadeIn(animationSpec = tween(300)) + 
                    expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + 
                    scaleIn(
                        initialScale = 0.8f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ),
            exit = fadeOut(animationSpec = tween(200)) + 
                   shrinkVertically(animationSpec = tween(200)) + 
                   scaleOut(targetScale = 0.8f, animationSpec = tween(200))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // New "Say Hello" button
                val sayHelloText = stringResource(R.string.ai_suggestion_say_hello)
                val sayHelloMessage = "你好吖Ivan"
                AnimatedSuggestionChip(
                    text = sayHelloText,
                    onClick = { onSuggestionClick(sayHelloMessage) }
                )

                val suggestions = listOf(
                    stringResource(R.string.ai_suggestion_show_notes),
                    stringResource(R.string.ai_suggestion_show_todos)
                )

                suggestions.forEach { suggestion ->
                    AnimatedSuggestionChip(
                        text = suggestion,
                        onClick = { onSuggestionClick(suggestion) }
                    )
                }
            }
        }
    }
}
