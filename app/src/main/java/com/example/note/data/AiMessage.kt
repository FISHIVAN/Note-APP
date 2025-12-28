package com.example.note.data

import java.util.UUID

data class AiMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val pendingAction: AiAction? = null
)

sealed class AiAction {
    data class CreateNote(val title: String, val content: String) : AiAction()
    data class CreateTodo(val content: String) : AiAction()
    data class UpdateNote(val id: Long, val title: String, val content: String) : AiAction()
    data class UpdateTodo(val id: Long, val content: String) : AiAction()
}
