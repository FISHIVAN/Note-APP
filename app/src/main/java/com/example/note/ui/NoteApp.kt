package com.example.note.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.note.ui.screen.NoteEditScreen
import com.example.note.ui.screen.NoteListScreen
import com.example.note.ui.screen.TodoScreen
import com.example.note.viewmodel.NoteViewModel
import com.example.note.viewmodel.TodoViewModel

import com.example.note.ui.screen.TodoEditScreen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

@Composable
fun NoteApp(
    noteViewModel: NoteViewModel,
    todoViewModel: TodoViewModel
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable(
            route = "main",
            enterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) }
        ) {
            MainScreen(
                noteViewModel = noteViewModel,
                todoViewModel = todoViewModel,
                onNoteClick = { noteId ->
                    navController.navigate("edit/$noteId")
                },
                onAddNoteClick = {
                    navController.navigate("edit/-1")
                },
                onTodoClick = { todoId ->
                    navController.navigate("edit_todo/$todoId")
                },
                onAddTodoClick = {
                    navController.navigate("edit_todo/-1")
                }
            )
        }
        composable(
            route = "edit/{noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong("noteId") ?: -1L
            NoteEditScreen(
                noteId = noteId,
                viewModel = noteViewModel,
                onNavigateUp = {
                    navController.navigateUp()
                }
            )
        }
        composable(
            route = "edit_todo/{todoId}",
            arguments = listOf(navArgument("todoId") { type = NavType.LongType }),
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
        ) { backStackEntry ->
            val todoId = backStackEntry.arguments?.getLong("todoId") ?: -1L
            TodoEditScreen(
                todoId = todoId,
                viewModel = todoViewModel,
                onNavigateUp = {
                    navController.navigateUp()
                }
            )
        }
    }
}

@Composable
fun MainScreen(
    noteViewModel: NoteViewModel,
    todoViewModel: TodoViewModel,
    onNoteClick: (Long) -> Unit,
    onAddNoteClick: () -> Unit,
    onTodoClick: (Long) -> Unit,
    onAddTodoClick: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf("Notes", "Todos")
    val icons = listOf(Icons.Default.Description, Icons.Default.CheckCircle)

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    val isSelected = selectedTab == index
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.2f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = 0.5f,
                            stiffness = 200f
                        ),
                        label = "iconScale"
                    )

                    NavigationBarItem(
                        icon = { 
                            Icon(
                                icons[index], 
                                contentDescription = item,
                                modifier = Modifier
                                    .size(26.dp) // Slightly larger base size
                                    .scale(scale)
                            ) 
                        },
                        label = { 
                            Text(
                                text = item,
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                            ) 
                        },
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { innerPadding ->
        // We use a Box or just pass modifier to screens to handle padding
        // NoteListScreen and TodoScreen manage their own Scaffold/layout, 
        // but here they are nested inside MainScreen's Scaffold.
        // To avoid double Scaffolds (double FABs, etc), we might want to let MainScreen handle the scaffold 
        // and pass content down, OR just have MainScreen provide the navigation bar and let children be self-contained.
        // However, NoteListScreen has a FAB. TodoScreen has a FAB.
        // If we put them inside a Scaffold, the inner Scaffold will work but might need care with padding.
        // Actually, innerPadding from MainScreen should be applied to the container of the child screens.
        
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> NoteListScreen(
                    viewModel = noteViewModel,
                    onNoteClick = onNoteClick,
                    onAddNoteClick = onAddNoteClick
                )
                1 -> TodoScreen(
                    viewModel = todoViewModel,
                    onTodoClick = onTodoClick,
                    onAddTodoClick = onAddTodoClick
                )
            }
        }
    }
}
