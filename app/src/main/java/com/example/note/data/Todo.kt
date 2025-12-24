package com.example.note.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val isDone: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val deadline: Long? = null
)
