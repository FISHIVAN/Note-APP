package com.example.note.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Surface

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.note.data.Todo
import com.example.note.viewmodel.TodoViewModel
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import androidx.compose.material3.AlertDialog

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.ui.res.stringResource
import com.example.note.R

import com.example.note.ui.utils.BounceIconButton
import com.example.note.ui.utils.BounceFloatingActionButton
import com.example.note.ui.utils.BounceOutlinedButton
import com.example.note.ui.utils.BounceTextButton

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditScreen(
    todoId: Long,
    viewModel: TodoViewModel,
    onNavigateUp: () -> Unit
) {
    var content by remember { mutableStateOf("") }
    var hasDeadline by remember { mutableStateOf(false) }
    var deadlineTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var currentTodo by remember { mutableStateOf<Todo?>(null) }
    
    // Dialog states
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(todoId) {
        if (todoId != -1L) {
            val todo = viewModel.getTodoById(todoId)
            todo?.let {
                currentTodo = it
                content = it.content
                if (it.deadline != null) {
                    hasDeadline = true
                    deadlineTimestamp = it.deadline
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                modifier = Modifier.clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
                title = { Text(if (todoId == -1L) stringResource(R.string.new_todo) else stringResource(R.string.edit_todo)) },
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
                    if (content.isNotBlank()) {
                        viewModel.saveTodo(
                            id = todoId,
                            content = content,
                            deadline = if (hasDeadline) deadlineTimestamp else null
                        )
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
            // Content Block
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text(stringResource(R.string.todo_placeholder), style = MaterialTheme.typography.headlineSmall) },
                    textStyle = MaterialTheme.typography.headlineSmall,
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

            // Deadline Block
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.set_deadline),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Switch(
                            checked = hasDeadline,
                            onCheckedChange = { hasDeadline = it }
                        )
                    }

                    AnimatedVisibility(visible = hasDeadline) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                BounceOutlinedButton(
                                    onClick = { showDatePicker = true },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                                    Spacer(modifier = Modifier.padding(4.dp))
                                    val dateText = remember(deadlineTimestamp) {
                                        val locale = Locale.getDefault()
                                        if (locale.language == "zh") {
                                            java.text.SimpleDateFormat("MM月dd日", locale).format(Date(deadlineTimestamp))
                                        } else {
                                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(deadlineTimestamp))
                                        }
                                    }
                                    Text(
                                        text = dateText,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                
                                BounceOutlinedButton(
                                    onClick = { showTimePicker = true },
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null)
                                    Spacer(modifier = Modifier.padding(4.dp))
                                    Text(
                                        text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(deadlineTimestamp)),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = deadlineTimestamp
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                BounceTextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        // Preserve time part
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = deadlineTimestamp
                        val hour = calendar.get(Calendar.HOUR_OF_DAY)
                        val minute = calendar.get(Calendar.MINUTE)
                        
                        calendar.timeInMillis = it
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        deadlineTimestamp = calendar.timeInMillis
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                BounceTextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = deadlineTimestamp
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE)
        )

        AlertDialog(
            modifier = Modifier.fillMaxWidth(),
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                BounceTextButton(onClick = {
                    val newCalendar = Calendar.getInstance()
                    newCalendar.timeInMillis = deadlineTimestamp
                    newCalendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    newCalendar.set(Calendar.MINUTE, timePickerState.minute)
                    deadlineTimestamp = newCalendar.timeInMillis
                    showTimePicker = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                BounceTextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.select_time),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    TimePicker(state = timePickerState)
                }
            }
        )
    }
}
