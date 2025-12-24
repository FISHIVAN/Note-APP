package com.example.note.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.note.data.Note
import com.example.note.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.sourceforge.pinyin4j.PinyinHelper

enum class SortOption {
    TITLE,
    CREATED_DATE,
    MODIFIED_DATE
}

class NoteViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isGridMode = MutableStateFlow(false)
    val isGridMode: StateFlow<Boolean> = _isGridMode.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.MODIFIED_DATE)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    val notes: StateFlow<List<Note>> = combine(
        _searchQuery,
        repository.getAllNotes(),
        _sortOption
    ) { query, notes, sortOption ->
        val filteredNotes = if (query.isBlank()) {
            notes
        } else {
            notes.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.content.contains(query, ignoreCase = true) ||
                it.category?.contains(query, ignoreCase = true) == true ||
                it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            }
        }
        
        when (sortOption) {
            SortOption.TITLE -> filteredNotes.sortedWith(
                compareByDescending<Note> { it.isPinned }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { note ->
                        val title = note.title
                        if (title.isEmpty()) return@thenBy ""
                        val firstChar = title[0]
                        val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(firstChar)
                        if (pinyinArray != null && pinyinArray.isNotEmpty()) {
                            pinyinArray[0].substring(0, 1)
                        } else {
                            firstChar.toString()
                        }
                    }
                    .thenByDescending { it.timestamp }
            )
            SortOption.CREATED_DATE -> filteredNotes.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.id })
            SortOption.MODIFIED_DATE -> filteredNotes.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.timestamp })
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleGridMode() {
        _isGridMode.value = !_isGridMode.value
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun addNote(title: String, content: String, category: String?, tags: List<String>) {
        viewModelScope.launch {
            repository.insertNote(
                Note(
                    title = title,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    category = category,
                    tags = tags
                )
            )
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(timestamp = System.currentTimeMillis()))
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned))
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    suspend fun getNoteById(id: Long): Note? {
        return repository.getNoteById(id)
    }
}

class NoteViewModelFactory(private val repository: NoteRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
