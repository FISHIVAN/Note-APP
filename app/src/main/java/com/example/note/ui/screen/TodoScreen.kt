package com.example.note.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.note.data.Todo
import com.example.note.viewmodel.TodoViewModel

import androidx.compose.ui.res.stringResource
import com.example.note.R

import java.text.DateFormat
import java.util.Date

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp

import com.example.note.ui.utils.BounceFloatingActionButton
import com.example.note.ui.utils.BounceTextButton

import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    viewModel: TodoViewModel,
    onAddTodoClick: () -> Unit,
    onTodoClick: (Long) -> Unit,
    bottomPadding: Dp = 0.dp
) {
    val todos by viewModel.todos.collectAsState()
    val density = LocalDensity.current
    var headerHeightPx by remember { mutableStateOf(0f) }
    val headerHeight = with(density) { headerHeightPx.toDp() }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        floatingActionButton = {
            BounceFloatingActionButton(
                onClick = onAddTodoClick,
                modifier = Modifier.padding(bottom = bottomPadding)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_todo))
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Todo List (Content) - Below Header
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f, animationSpec = tween(300)),
                modifier = Modifier.fillMaxSize()
            ) {
                TodoListContent(
                    todos = todos,
                    onToggle = { viewModel.toggleTodo(it) },
                    onDelete = { viewModel.deleteTodo(it) },
                    onTodoClick = onTodoClick,
                    topPadding = headerHeight,
                    bottomPadding = bottomPadding
                )
            }

            // 2. Header (Title) - On Top
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
                    Text(
                        text = stringResource(R.string.todos),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(top = 12.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListContent(
    todos: List<Todo>,
    onToggle: (Todo) -> Unit,
    onDelete: (Todo) -> Unit,
    onTodoClick: (Long) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: Dp
) {
    val haptic = LocalHapticFeedback.current
    var todoToDelete by remember { mutableStateOf<Todo?>(null) }
    
    if (todoToDelete != null) {
        AlertDialog(
            onDismissRequest = { todoToDelete = null },
            title = { Text(stringResource(R.string.delete_todo)) },
            text = { Text(stringResource(R.string.confirm_delete_todo)) },
            confirmButton = {
                BounceTextButton(
                    onClick = {
                        todoToDelete?.let { onDelete(it) }
                        todoToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                BounceTextButton(onClick = { todoToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp + bottomPadding, top = topPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(todos, key = { it.id }) { todo ->
            val currentTodo by rememberUpdatedState(todo)
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = {
                    if (it == SwipeToDismissBoxValue.StartToEnd) {
                        onToggle(currentTodo)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        false // Don't dismiss, just toggle
                    } else {
                        false
                    }
                },
                positionalThreshold = { it * 0.5f }
            )

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val color = when (dismissState.dismissDirection) {
                        SwipeToDismissBoxValue.StartToEnd -> if (todo.isDone) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                        else -> Color.Transparent
                    }
                    val icon = if (todo.isDone) Icons.Default.Undo else Icons.Default.Check
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.medium)
                            .background(color)
                            .padding(16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = if (todo.isDone) "Mark as Undone" else "Mark as Done",
                            tint = if (todo.isDone) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                },
                enableDismissFromStartToEnd = true,
                enableDismissFromEndToStart = false
            ) {
                TodoItem(
                    todo = todo,
                    onToggle = { onToggle(todo) },
                    onClick = { onTodoClick(todo.id) },
                    onLongClick = { 
                        todoToDelete = todo
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoItem(
    todo: Todo,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (todo.isDone) 0.dp else 2.dp),
        modifier = Modifier
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = todo.isDone,
                onCheckedChange = { onToggle() }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = todo.content,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                    color = if (todo.isDone) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (todo.isDone) FontWeight.Normal else FontWeight.Medium
                )
                if (todo.deadline != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(todo.deadline)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
