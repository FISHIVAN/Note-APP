package com.example.note.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.note.data.NoteRepository
import com.example.note.data.Todo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodoViewModel(private val repository: NoteRepository) : ViewModel() {

    val todos: StateFlow<List<Todo>> = repository.getAllTodos()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addTodo(content: String, deadline: Long? = null) {
        viewModelScope.launch {
            repository.insertTodo(Todo(content = content, deadline = deadline))
        }
    }

    suspend fun getTodoById(id: Long): Todo? {
        return repository.getTodoById(id)
    }

    fun saveTodo(id: Long, content: String, deadline: Long?) {
        viewModelScope.launch {
            if (id == -1L) {
                repository.insertTodo(Todo(content = content, deadline = deadline))
            } else {
                val existing = repository.getTodoById(id)
                existing?.let {
                    repository.updateTodo(it.copy(content = content, deadline = deadline))
                }
            }
        }
    }

    fun toggleTodo(todo: Todo) {
        viewModelScope.launch {
            repository.updateTodo(todo.copy(isDone = !todo.isDone))
        }
    }

    fun deleteTodo(todo: Todo) {
        viewModelScope.launch {
            repository.deleteTodo(todo)
        }
    }
}

class TodoViewModelFactory(private val repository: NoteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TodoViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
