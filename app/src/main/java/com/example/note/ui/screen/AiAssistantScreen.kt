package com.example.note.ui.screen

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.clickable
import com.example.note.R
import com.example.note.data.AiAction
import com.example.note.data.AiMessage
import com.example.note.viewmodel.AiAssistantViewModel
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
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.collectIsDraggedAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val modelName by viewModel.modelName.collectAsState()
    
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
            onDismiss = { showSettingsDialog = false },
            onSave = { key, model ->
                viewModel.saveSettings(key, model)
                showSettingsDialog = false
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    reverseLayout = false,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = headerHeight + 8.dp, bottom = 100.dp) // Add bottom padding for floating UI
                ) {
                    items(messages) { message ->
                        MessageItem(
                            message = message,
                            onSaveAsNote = { content -> viewModel.createNoteFromMessage(content) },
                            onSaveAsTodo = { content -> viewModel.createTodoFromMessage(content) },
                            onExecuteAction = { viewModel.executeAction(message.id) },
                            onCancelAction = { viewModel.cancelAction(message.id) }
                        )
                    }
                    if (isLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.ai_assistant_title),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.clearMessages() }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.new_chat)
                                )
                            }
                            IconButton(onClick = { showSettingsDialog = true }) {
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
                    .padding(16.dp), // Padding from screen edges
                contentAlignment = Alignment.BottomEnd
            ) {
                // FAB
                AnimatedVisibility(
                    visible = !isInputExpanded,
                    enter = scaleIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
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

                // Capsule Input
                AnimatedVisibility(
                    visible = isInputExpanded,
                    enter = scaleIn(
                        transformOrigin = TransformOrigin(1f, 1f),
                        animationSpec = spring(stiffness = Spring.StiffnessLow)
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
                            IconButton(onClick = { isInputExpanded = false }) {
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
                            
                            val sendInteractionSource = remember { MutableInteractionSource() }
                            val isSendPressed by sendInteractionSource.collectIsPressedAsState()
                            val sendScale by animateFloatAsState(
                                targetValue = if (isSendPressed) 0.9f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "send_scale"
                            )

                            IconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                        keyboardController?.hide()
                                        isInputExpanded = false
                                    }
                                },
                                enabled = !isLoading && inputText.isNotBlank(),
                                interactionSource = sendInteractionSource,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = sendScale
                                    scaleY = sendScale
                                }
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

@Composable
fun MessageItem(
    message: AiMessage,
    onSaveAsNote: (String) -> Unit,
    onSaveAsTodo: (String) -> Unit,
    onExecuteAction: () -> Unit,
    onCancelAction: () -> Unit
) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (message.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (message.isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            shape = shape,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { showMenu = true }
                    )
                }
        ) {
            Column {
                Box {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(12.dp),
                        color = textColor
                    )
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
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
                                        imageVector = Icons.Default.NoteAdd,
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

                message.pendingAction?.let { action ->
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                    Column(modifier = Modifier.padding(12.dp)) {
                         val title = when(action) {
                             is AiAction.CreateNote -> stringResource(R.string.new_note) + ": " + action.title
                             is AiAction.CreateTodo -> stringResource(R.string.new_todo)
                             is AiAction.UpdateNote -> stringResource(R.string.edit_note) + ": " + action.title
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
                             horizontalArrangement = Arrangement.End
                         ) {
                             OutlinedButton(
                                 onClick = onCancelAction,
                                 modifier = Modifier.height(32.dp),
                                 contentPadding = PaddingValues(horizontal = 8.dp)
                             ) {
                                 Text(stringResource(R.string.cancel), fontSize = 12.sp)
                             }
                             Spacer(modifier = Modifier.width(8.dp))
                             Button(
                                 onClick = onExecuteAction,
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

@Composable
fun SettingsDialog(
    initialApiKey: String,
    initialModelName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var modelName by remember { mutableStateOf(initialModelName) }
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.api_key)) },
                    singleLine = true
                )
                Text(
                    text = stringResource(R.string.get_api_key_guide),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://cloud.siliconflow.cn/")
                    }
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text(stringResource(R.string.model_name)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(apiKey, modelName) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
