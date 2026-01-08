package com.example.note.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val timestamp: Long,
    val isPinned: Boolean = false,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val aiSummary: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val markerColor: Float = 0f // Stores BitmapDescriptorFactory.HUE_AZURE etc. Default AZURE (210f)
)
