package com.example.note.ui

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.res.stringResource
import com.example.note.R
import com.example.note.ui.screen.NoteEditScreen
import com.example.note.ui.screen.NoteListScreen
import com.example.note.ui.screen.TodoScreen
import com.example.note.ui.screen.AiAssistantScreen
import com.example.note.viewmodel.NoteViewModel
import com.example.note.viewmodel.TodoViewModel
import com.example.note.viewmodel.AiAssistantViewModel
import com.example.note.viewmodel.MapViewModel
import com.example.note.viewmodel.SettingsViewModel

import com.example.note.ui.screen.TodoEditScreen
import com.example.note.ui.screen.AboutScreen
import com.example.note.ui.screen.MapScreen
import com.example.note.ui.screen.SettingsScreen
import androidx.compose.material.icons.filled.Map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.os.Bundle
import com.amap.api.maps.TextureMapView

import com.amap.api.maps.model.MyLocationStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.BitmapDescriptor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.LatLng
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun NoteApp(
    noteViewModel: NoteViewModel,
    todoViewModel: TodoViewModel,
    aiAssistantViewModel: AiAssistantViewModel,
    mapViewModel: MapViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()

    SideEffect {
        val activity = context as? Activity ?: return@SideEffect
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.isNavigationBarContrastEnforced = false
        val controller = WindowInsetsControllerCompat(window, view)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }

    NavHost(navController = navController, startDestination = "main") {
        composable(route = "main") {
            MainScreen(
                noteViewModel = noteViewModel,
                todoViewModel = todoViewModel,
                aiAssistantViewModel = aiAssistantViewModel,
                mapViewModel = mapViewModel,
                settingsViewModel = settingsViewModel,
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
                },
                onAboutClick = {
                    navController.navigate("about")
                }
            )
        }
        composable(route = "about") {
            AboutScreen(
                onNavigateUp = {
                    navController.navigateUp()
                }
            )
        }
        composable(
            route = "edit/{noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.LongType })
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
            arguments = listOf(navArgument("todoId") { type = NavType.LongType })
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

data class NavItem(val route: String, val icon: ImageVector, val labelRes: Int)

@Composable
fun MainScreen(
    noteViewModel: NoteViewModel,
    todoViewModel: TodoViewModel,
    aiAssistantViewModel: AiAssistantViewModel,
    mapViewModel: MapViewModel,
    settingsViewModel: SettingsViewModel,
    onNoteClick: (Long) -> Unit,
    onAddNoteClick: () -> Unit,
    onTodoClick: (Long) -> Unit,
    onAddTodoClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    val showAiAssistant by settingsViewModel.showAiAssistant.collectAsState()
    val showMapNotes by settingsViewModel.showMapNotes.collectAsState()

    val allItems = listOf(
        NavItem("notes", Icons.Default.Description, R.string.notes),
        NavItem("map", Icons.Default.Map, R.string.map_notes),
        NavItem("ai", Icons.Filled.AutoAwesome, R.string.ai_assistant),
        NavItem("todo", Icons.AutoMirrored.Filled.Assignment, R.string.todos),
        NavItem("settings", Icons.Default.Settings, R.string.settings)
    )

    val currentItems = allItems.filter { item ->
        when (item.route) {
            "ai" -> showAiAssistant
            "map" -> showMapNotes
            else -> true
        }
    }

    var selectedRoute by rememberSaveable { mutableStateOf("notes") }

    // Cache MapView to avoid recreation on tab switch
    val context = LocalContext.current
    val cachedMapView = remember {
        try {
            val options = com.amap.api.maps.AMapOptions()
            TextureMapView(context, options).apply {
                onCreate(Bundle())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // Initialize Map Settings (Once)
    LaunchedEffect(cachedMapView) {
        cachedMapView?.map?.let { aMap ->
            try {
                aMap.uiSettings?.isZoomControlsEnabled = false
                
                // Setup User Location and Center
                val myLocationStyle = MyLocationStyle()
                // LOCATION_TYPE_LOCATE: Locate once and move map center to location
                myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATE)
                aMap.myLocationStyle = myLocationStyle
                aMap.isMyLocationEnabled = true
                aMap.uiSettings?.isMyLocationButtonEnabled = false // Disable built-in button
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Manage Map Markers globally (persists across tab switches)
    val mapNotes by mapViewModel.mapNotes.collectAsState()
    val temporaryMarker by mapViewModel.temporaryMarker.collectAsState()

    // Helper function to create scaled marker icon
    fun getScaledMarkerIcon(colorHue: Float): BitmapDescriptor {
        // 1. Outline (Background)
        val outlineDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_location_on_outline)?.mutate()
        outlineDrawable?.setTint(android.graphics.Color.WHITE)
        
        // 2. Fill (Foreground)
        val vectorDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_location_on)?.mutate()
        
        // Convert Hue to Color
        val color = when(colorHue) {
            BitmapDescriptorFactory.HUE_RED -> android.graphics.Color.RED
            BitmapDescriptorFactory.HUE_ORANGE -> android.graphics.Color.rgb(255, 165, 0)
            BitmapDescriptorFactory.HUE_GREEN -> android.graphics.Color.GREEN
            BitmapDescriptorFactory.HUE_BLUE -> android.graphics.Color.BLUE
            BitmapDescriptorFactory.HUE_VIOLET -> android.graphics.Color.rgb(238, 130, 238)
            else -> android.graphics.Color.CYAN
        }
        
        vectorDrawable?.setTint(color)
        
        // Set size (make it large)
        val size = 120 
        outlineDrawable?.setBounds(0, 0, size, size)
        vectorDrawable?.setBounds(0, 0, size, size)
        
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        outlineDrawable?.draw(canvas)
        vectorDrawable?.draw(canvas)
        
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // Sync Map Markers
    LaunchedEffect(mapNotes, temporaryMarker, cachedMapView) {
        cachedMapView?.map?.let { aMap ->
            try {
                aMap.clear() // Note: This clears all markers but preserves MyLocation
                
                // Draw existing notes
                mapNotes.forEach { note ->
                    if (note.latitude != null && note.longitude != null) {
                        val latLng = LatLng(note.latitude, note.longitude)
                        val markerOptions = MarkerOptions()
                            .position(latLng)
                            .title(note.title)
                            .snippet(note.content)
                            .draggable(true)
                            // Use custom large icon
                            .icon(getScaledMarkerIcon(if (note.markerColor == 0f) BitmapDescriptorFactory.HUE_AZURE else note.markerColor))
                        
                        val marker = aMap.addMarker(markerOptions)
                        marker.`object` = note
                    }
                }
                
                // Draw temporary marker if exists
                temporaryMarker?.let { latLng ->
                    val markerOptions = MarkerOptions()
                        .position(latLng)
                        .title("New Note")
                        .snippet("Click to add content")
                        .draggable(true) // Allow dragging
                        .icon(getScaledMarkerIcon(BitmapDescriptorFactory.HUE_RED))
                    
                    val marker = aMap.addMarker(markerOptions)
                    // We mark it as temporary by NOT setting an object or setting a special tag
                    marker.`object` = "TEMP" 
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Cleanup MapView when MainScreen is destroyed
    DisposableEffect(Unit) {
        onDispose {
            try {
                cachedMapView?.onDestroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 8.dp
            ) {
                currentItems.forEach { item ->
                    val isSelected = selectedRoute == item.route
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
                                item.icon, 
                                contentDescription = stringResource(item.labelRes),
                                modifier = Modifier
                                    .size(26.dp) // Slightly larger base size
                                    .scale(scale)
                            ) 
                        },
                        label = { 
                            Text(
                                text = stringResource(item.labelRes),
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                            ) 
                        },
                        selected = isSelected,
                        onClick = { selectedRoute = item.route },
                        alwaysShowLabel = false
                    )
                }
            }
        }
    ) { innerPadding ->
        // We use a Box or just pass modifier to screens to handle padding
        // NoteListScreen and TodoScreen manage their own Scaffold/layout, 
        // but here they are nested inside MainScreen's Scaffold.
        // To allow content to scroll behind the bottom navigation bar (for rounded corners effect),
        // we do NOT apply bottom padding to the container. Instead, we pass it to the screens.
        
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
        ) {
            AnimatedContent(
                targetState = selectedRoute,
                label = "tab_animation",
                transitionSpec = {
                    val initialIndex = currentItems.indexOfFirst { it.route == initialState }
                    val targetIndex = currentItems.indexOfFirst { it.route == targetState }
                    
                    if (targetIndex > initialIndex) {
                        // Slide left
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        // Slide right
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut())
                    }
                }
            ) { route ->
                when (route) {
                    "notes" -> NoteListScreen(
                        viewModel = noteViewModel,
                        onNoteClick = onNoteClick,
                        onAddNoteClick = onAddNoteClick,
                        bottomPadding = innerPadding.calculateBottomPadding()
                    )
                    "map" -> MapScreen(
                        viewModel = mapViewModel,
                        cachedMapView = cachedMapView,
                        onNavigateUp = { /* No-op for tab */ },
                        bottomPadding = innerPadding.calculateBottomPadding()
                    )
                    "ai" -> AiAssistantScreen(
                        viewModel = aiAssistantViewModel,
                        bottomPadding = innerPadding.calculateBottomPadding()
                    )
                    "todo" -> TodoScreen(
                        viewModel = todoViewModel,
                        onTodoClick = onTodoClick,
                        onAddTodoClick = onAddTodoClick,
                        bottomPadding = innerPadding.calculateBottomPadding()
                    )
                    "settings" -> SettingsScreen(
                        viewModel = settingsViewModel,
                        onAboutClick = onAboutClick,
                        bottomPadding = innerPadding.calculateBottomPadding()
                    )
                }
            }
        }
    }
}
