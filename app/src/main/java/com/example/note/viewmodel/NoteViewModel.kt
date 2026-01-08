package com.example.note.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.note.data.Note
import com.example.note.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.sourceforge.pinyin4j.PinyinHelper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class SortOption {
    TITLE,
    CREATED_DATE,
    MODIFIED_DATE
}

class NoteViewModel(
    private val repository: NoteRepository,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isGridMode = MutableStateFlow(false)
    val isGridMode: StateFlow<Boolean> = _isGridMode.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.MODIFIED_DATE)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "ai_auto_summary") {
            val enabled = prefs.getBoolean(key, false)
            if (enabled) {
                generateAllSummaries()
            }
        }
    }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onCleared() {
        super.onCleared()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

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
        .flowOn(Dispatchers.Default)
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
            val timestamp = System.currentTimeMillis()
            val note = Note(
                title = title,
                content = content,
                timestamp = timestamp,
                category = category,
                tags = tags
            )
            val id = repository.insertNote(note)
            generateSummary(note.copy(id = id))
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(timestamp = System.currentTimeMillis()))
            generateSummary(note)
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

    private fun generateAllSummaries() {
        viewModelScope.launch(Dispatchers.IO) {
            val allNotes = repository.getAllNotes().first()
            allNotes.forEach { note ->
                // Only generate if summary is missing or content changed (though tracking content change is hard, re-generating for all is safer for consistency)
                // To save tokens, we could check if aiSummary is null, but if user edited note while switch was off, summary would be stale.
                // So, generating for ALL eligible notes is the correct behavior for "Switch ON".
                if (note.content.length >= 50) {
                     generateSummary(note)
                }
            }
        }
    }

    private fun generateSummary(note: Note) {
        val autoSummaryEnabled = sharedPreferences.getBoolean("ai_auto_summary", false)
        if (!autoSummaryEnabled) {
            return
        }

        val apiKey = sharedPreferences.getString("ai_api_key", "") ?: ""
        val model = sharedPreferences.getString("ai_model_name", "Qwen/Qwen2.5-Coder-7B-Instruct") ?: "Qwen/Qwen2.5-Coder-7B-Instruct"

        // Only generate summary if content is long enough
        if (apiKey.isBlank() || note.content.length < 50) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://api.siliconflow.cn/v1/chat/completions"
                val language = Locale.getDefault().language
                val isChinese = language == "zh"

                val systemPrompt = if (isChinese) {
                    "你是一个助手。请为用户提供的笔记内容生成一个非常简短的摘要（50字以内）。只返回摘要文本，不要包含引号、前缀或其他解释。"
                } else {
                    "You are a helper. Generate a very short summary (under 30 words) for the note content. Return ONLY the summary text, no quotes or prefixes."
                }

                val jsonBody = JSONObject()
                jsonBody.put("model", model)
                jsonBody.put("temperature", 0.3)
                
                val messagesArray = JSONArray()
                val systemMessage = JSONObject()
                systemMessage.put("role", "system")
                systemMessage.put("content", systemPrompt)
                messagesArray.put(systemMessage)
                
                val userMessageObj = JSONObject()
                userMessageObj.put("role", "user")
                userMessageObj.put("content", note.content)
                messagesArray.put(userMessageObj)

                jsonBody.put("messages", messagesArray)
                jsonBody.put("stream", false)

                val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                response.use {
                    if (it.isSuccessful) {
                        val responseBody = it.body?.string() ?: ""
                        val jsonResponse = JSONObject(responseBody)
                        val summary = jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                            .removePrefix("\"")
                            .removeSuffix("\"")
                        
                        if (summary.isNotBlank()) {
                            // Update the note with the summary
                            // We fetch the latest version of the note to ensure we don't overwrite other fields
                            val currentNote = repository.getNoteById(note.id)
                            if (currentNote != null) {
                                repository.updateNote(currentNote.copy(aiSummary = summary))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

class NoteViewModelFactory(
    private val repository: NoteRepository,
    private val sharedPreferences: SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(repository, sharedPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
