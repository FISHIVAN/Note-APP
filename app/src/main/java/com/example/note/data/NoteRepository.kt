package com.example.note.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val noteDao: NoteDao,
    private val todoDao: TodoDao
) {

    // Note operations
    fun getAllNotes(): Flow<List<Note>> = noteDao.getAllNotes()

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query)

    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)

    suspend fun insertNote(note: Note) = noteDao.insertNote(note)

    suspend fun updateNote(note: Note) = noteDao.updateNote(note)

    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)

    // Todo operations
    fun getAllTodos(): Flow<List<Todo>> = todoDao.getAllTodos()

    suspend fun getTodoById(id: Long): Todo? = todoDao.getTodoById(id)

    suspend fun insertTodo(todo: Todo) = todoDao.insert(todo)

    suspend fun updateTodo(todo: Todo) = todoDao.update(todo)

    suspend fun deleteTodo(todo: Todo) = todoDao.delete(todo)
}
