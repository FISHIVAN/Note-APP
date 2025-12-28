package com.example.note.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.note.data.AiAction
import com.example.note.data.AiMessage
import com.example.note.data.Note
import com.example.note.data.NoteRepository
import com.example.note.data.Todo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.Locale

class AiAssistantViewModel(
    private val repository: NoteRepository,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _modelName = MutableStateFlow("Qwen/Qwen2.5-Coder-7B-Instruct")
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private var currentSessionId = UUID.randomUUID().toString()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        _apiKey.value = sharedPreferences.getString("ai_api_key", "") ?: ""
        _modelName.value = sharedPreferences.getString("ai_model_name", "Qwen/Qwen2.5-Coder-7B-Instruct") ?: "Qwen/Qwen2.5-Coder-7B-Instruct"
    }

    fun saveSettings(key: String, model: String) {
        sharedPreferences.edit()
            .putString("ai_api_key", key)
            .putString("ai_model_name", model)
            .apply()
        _apiKey.value = key
        _modelName.value = model
    }

    fun clearMessages() {
        _messages.value = emptyList()
        currentSessionId = UUID.randomUUID().toString()
        _isLoading.value = false
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        val userMessage = AiMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            isUser = true
        )
        _messages.value += userMessage
        _isLoading.value = true
        
        val sessionId = currentSessionId

        viewModelScope.launch {
            try {
                // Check session before proceeding
                if (sessionId != currentSessionId) return@launch

                // Fetch context
                val notes = repository.getAllNotes().first()
                val todos = repository.getAllTodos().first()

                val contextBuilder = StringBuilder()
                val language = Locale.getDefault().language
                val isChinese = language == "zh"

                if (isChinese) {
                    contextBuilder.append("用户的笔记:\n")
                    if (notes.isEmpty()) {
                        contextBuilder.append("(无笔记)\n")
                    } else {
                        notes.forEach { note ->
                            contextBuilder.append("- ID:${note.id}, 标题: ${note.title}, 内容: ${note.content}\n")
                        }
                    }
                    
                    contextBuilder.append("\n用户的待办:\n")
                    if (todos.isEmpty()) {
                        contextBuilder.append("(无待办)\n")
                    } else {
                        todos.forEach { todo ->
                            contextBuilder.append("- ID:${todo.id}, 内容: ${todo.content} (完成状态: ${todo.isDone})\n")
                        }
                    }

                    // Add recent conversation history (last 10 messages) to context
                    // Drop the last one because it is the current user message which is sent separately as "User Question"
                    val history = _messages.value.dropLast(1).takeLast(10)
                    if (history.isNotEmpty()) {
                        contextBuilder.append("\n最近的对话记录:\n")
                        history.forEach { msg ->
                            val role = if (msg.isUser) "用户" else "AI"
                            contextBuilder.append("$role: ${msg.content}\n")
                        }
                    }
                } else {
                    contextBuilder.append("User's Notes:\n")
                    if (notes.isEmpty()) {
                        contextBuilder.append("(No notes)\n")
                    } else {
                        notes.forEach { note ->
                            contextBuilder.append("- ID:${note.id}, Title: ${note.title}, Content: ${note.content}\n")
                        }
                    }
                    
                    contextBuilder.append("\nUser's Todos:\n")
                    if (todos.isEmpty()) {
                        contextBuilder.append("(No todos)\n")
                    } else {
                        todos.forEach { todo ->
                            contextBuilder.append("- ID:${todo.id}, Content: ${todo.content} (Done: ${todo.isDone})\n")
                        }
                    }

                    // Add recent conversation history (last 10 messages) to context
                    // Drop the last one because it is the current user message which is sent separately as "User Question"
                    val history = _messages.value.dropLast(1).takeLast(10)
                    if (history.isNotEmpty()) {
                        contextBuilder.append("\nRecent Conversation History:\n")
                        history.forEach { msg ->
                            val role = if (msg.isUser) "User" else "AI"
                            contextBuilder.append("$role: ${msg.content}\n")
                        }
                    }
                }

                val systemInstruction = if (isChinese) {
                    """
                    你是一个集成在笔记应用中的智能助手。
                    你可以访问用户的笔记和待办事项，以及最近的对话记录。
                    
                    核心规则：
                    1. 【绝对禁止Markdown】：严禁使用 **加粗**、*斜体*、# 标题、`代码块` 等Markdown格式。
                    2. 【纯文本输出】：直接输出内容，不要使用列表符号（如 - 或 *），除非必须列举多个项目。如果列举，请使用数字编号。
                    3. 【严禁内部标签】：不要在回答中直接显示 <ACTION> 标签。也不要显示 <OPTION> 标签。
                    4. 【指令块位置】：如果需要执行操作（创建/修改），指令块必须且只能出现在回答的**最后**。
                    
                    意图识别与操作指令：
                    - 只有当用户**明确要求**记录、提醒或修改时才生成指令。
                    - “记一下”、“记录”、“保存到笔记” -> 【创建笔记】
                    - “提醒我”、“待办”、“添加任务” -> 【创建待办】
                    - “修改笔记”、“改一下那个笔记” -> 【修改笔记】（必须明确匹配到ID）
                    - “修改待办”、“改一下那个任务” -> 【修改待办】（必须明确匹配到ID）
                    
                    指令格式严格要求：
                    1. 必须使用 <ACTION> 和 </ACTION> 包裹。
                    2. 内部必须是合法的单行 JSON。
                    3. 严禁在 <ACTION> 标签外显示 JSON 内容。
                    
                    【创建笔记】指令示例：
                    <ACTION>{"type":"create_note","title":"简短概括标题","content":"笔记详细内容"}</ACTION>

                    【创建待办】指令示例：
                    <ACTION>{"type":"create_todo","content":"待办事项内容"}</ACTION>
                    
                    【修改笔记】指令示例（必须包含ID）：
                    <ACTION>{"type":"update_note","id":123,"title":"新标题","content":"新内容"}</ACTION>
                    
                    【修改待办】指令示例（必须包含ID）：
                    <ACTION>{"type":"update_todo","id":456,"content":"新内容"}</ACTION>
                    """.trimIndent()
                } else {
                    """
                    You are a helpful AI assistant integrated into a Note app.
                    You have access to the user's notes, todos, and recent conversation history.
                    
                    CORE RULES:
                    1. [NO MARKDOWN]: Do NOT use **bold**, *italics*, # headings, or `code blocks`.
                    2. [PLAIN TEXT]: Output directly. Avoid list symbols like - or * unless necessary. If listing, use numbers.
                    3. [NO INTERNAL TAGS]: Do NOT show <ACTION> tags in your main response. Do NOT use <OPTION> tags.
                    4. [ACTION BLOCK POSITION]: If an action is required, the command block must be at the very END of the response.
                    
                    Intent Recognition & Commands:
                    - Only generate a command if the user **EXPLICITLY** asks to record, remind, or modify.
                    - "Record", "Save note" -> [Create Note]
                    - "Remind me", "Todo" -> [Create Todo]
                    - "Modify note", "Change that note" -> [Update Note] (Must match ID)
                    - "Modify todo", "Change that task" -> [Update Todo] (Must match ID)
                    
                    Command Format Strict Requirements:
                    1. MUST be wrapped in <ACTION> and </ACTION>.
                    2. Inside MUST be valid single-line JSON.
                    3. Do NOT show JSON content outside <ACTION> tags.
                    
                    [Create Note] Example:
                    <ACTION>{"type":"create_note","title":"Short Title","content":"Note Content"}</ACTION>

                    [Create Todo] Example:
                    <ACTION>{"type":"create_todo","content":"Todo Content"}</ACTION>
                    
                    [Update Note] Example (Must include ID):
                    <ACTION>{"type":"update_note","id":123,"title":"New Title","content":"New Content"}</ACTION>
                    
                    [Update Todo] Example (Must include ID):
                    <ACTION>{"type":"update_todo","id":456,"content":"New Content"}</ACTION>
                    """.trimIndent()
                }

                val userPrompt = if (isChinese) {
                    """
                    上下文信息:
                    $contextBuilder
                    
                    用户问题: $content
                    """.trimIndent()
                } else {
                    """
                    Context Information:
                    $contextBuilder
                    
                    User Question: $content
                    """.trimIndent()
                }

                callAiApi(systemInstruction, userPrompt, sessionId)
            } catch (e: Exception) {
                val errorMessage = AiMessage(
                    id = UUID.randomUUID().toString(),
                    content = "Error preparing data: ${e.message}",
                    isUser = false
                )
                _messages.value += errorMessage
                _isLoading.value = false
            }
        }
    }

    fun executeAction(messageId: String) {
        val messageList = _messages.value.toMutableList()
        val index = messageList.indexOfFirst { it.id == messageId }
        if (index != -1) {
            val message = messageList[index]
            message.pendingAction?.let { action ->
                viewModelScope.launch {
                    when (action) {
                        is AiAction.CreateNote -> {
                            repository.insertNote(
                                Note(
                                    title = action.title,
                                    content = action.content,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            _messages.value += AiMessage(
                                id = UUID.randomUUID().toString(),
                                content = "✅ 笔记已保存: ${action.title}",
                                isUser = false
                            )
                        }
                        is AiAction.CreateTodo -> {
                            repository.insertTodo(
                                Todo(content = action.content)
                            )
                            _messages.value += AiMessage(
                                id = UUID.randomUUID().toString(),
                                content = "✅ 待办已保存: ${action.content}",
                                isUser = false
                            )
                        }
                        is AiAction.UpdateNote -> {
                            val existingNote = repository.getNoteById(action.id)
                            if (existingNote != null) {
                                repository.updateNote(
                                    existingNote.copy(
                                        title = action.title.ifBlank { existingNote.title },
                                        content = action.content.ifBlank { existingNote.content },
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                                _messages.value += AiMessage(
                                    id = UUID.randomUUID().toString(),
                                    content = "✅ 笔记已更新: ${action.title}",
                                    isUser = false
                                )
                            } else {
                                _messages.value += AiMessage(
                                    id = UUID.randomUUID().toString(),
                                    content = "❌ 找不到ID为 ${action.id} 的笔记",
                                    isUser = false
                                )
                            }
                        }
                        is AiAction.UpdateTodo -> {
                            val existingTodo = repository.getTodoById(action.id)
                            if (existingTodo != null) {
                                repository.updateTodo(
                                    existingTodo.copy(
                                        content = action.content.ifBlank { existingTodo.content }
                                    )
                                )
                                _messages.value += AiMessage(
                                    id = UUID.randomUUID().toString(),
                                    content = "✅ 待办已更新: ${action.content}",
                                    isUser = false
                                )
                            } else {
                                _messages.value += AiMessage(
                                    id = UUID.randomUUID().toString(),
                                    content = "❌ 找不到ID为 ${action.id} 的待办",
                                    isUser = false
                                )
                            }
                        }
                    }
                    // Remove the action from the original message so it can't be clicked again
                    messageList[index] = message.copy(pendingAction = null)
                    _messages.value = messageList
                }
            }
        }
    }

    fun cancelAction(messageId: String) {
        val messageList = _messages.value.toMutableList()
        val index = messageList.indexOfFirst { it.id == messageId }
        if (index != -1) {
            messageList[index] = messageList[index].copy(pendingAction = null)
            _messages.value = messageList
        }
    }

    fun createNoteFromMessage(content: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val generatedTitle = try {
                generateTitle(content)
            } catch (e: Exception) {
                // Fallback title if AI fails
                if (content.length > 20) content.take(20) + "..." else content
            }

            repository.insertNote(
                Note(
                    title = generatedTitle,
                    content = content,
                    timestamp = System.currentTimeMillis()
                )
            )
            _messages.value += AiMessage(
                id = UUID.randomUUID().toString(),
                content = "✅ Note created: $generatedTitle",
                isUser = false
            )
            _isLoading.value = false
        }
    }

    private suspend fun generateTitle(content: String): String = suspendCancellableCoroutine { continuation ->
        val currentApiKey = apiKey.value
        val currentModel = modelName.value
        
        if (currentApiKey.isBlank()) {
            continuation.resume(if (content.length > 20) content.take(20) + "..." else content)
            return@suspendCancellableCoroutine
        }

        val url = "https://api.siliconflow.cn/v1/chat/completions"
        val language = Locale.getDefault().language
        val isChinese = language == "zh"
        
        val systemPrompt = if (isChinese) {
            "你是一个助手。请为用户提供的内容生成一个非常简短的标题（15字以内）。只返回标题文本，不要包含引号、前缀或其他解释。"
        } else {
            "You are a helper. Generate a very short title (under 10 words) for the content. Return ONLY the title text, no quotes or prefixes."
        }

        val jsonBody = JSONObject()
        jsonBody.put("model", currentModel)
        jsonBody.put("temperature", 0.3) // Low temperature for deterministic summary
        
        val messagesArray = JSONArray()
        val systemMessage = JSONObject()
        systemMessage.put("role", "system")
        systemMessage.put("content", systemPrompt)
        messagesArray.put(systemMessage)
        
        val userMessageObj = JSONObject()
        userMessageObj.put("role", "user")
        userMessageObj.put("content", content)
        messagesArray.put(userMessageObj)

        jsonBody.put("messages", messagesArray)
        jsonBody.put("stream", false)

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $currentApiKey")
            .post(requestBody)
            .build()

        val call = client.newCall(request)
        
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IOException("API Error: ${response.code}"))
                        }
                        return
                    }

                    try {
                        val responseBody = it.body?.string() ?: ""
                        val jsonResponse = JSONObject(responseBody)
                        val title = jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            .trim()
                            .removePrefix("\"")
                            .removeSuffix("\"") // Cleanup quotes if any
                        
                        if (continuation.isActive) {
                            continuation.resume(title)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            }
        })

        continuation.invokeOnCancellation {
            call.cancel()
        }
    }

    fun createTodoFromMessage(content: String) {
        viewModelScope.launch {
            repository.insertTodo(
                Todo(content = content)
            )
            _messages.value += AiMessage(
                id = UUID.randomUUID().toString(),
                content = "✅ Todo created from message",
                isUser = false
            )
        }
    }
    
    private fun callAiApi(systemInstruction: String, userPrompt: String, sessionId: String) {
        val currentApiKey = apiKey.value
        val currentModel = modelName.value

        if (currentApiKey.isBlank()) {
             viewModelScope.launch {
                if (sessionId != currentSessionId) return@launch
                _messages.value += AiMessage(
                    id = UUID.randomUUID().toString(),
                    content = "Please set your API Key in settings.",
                    isUser = false
                )
                _isLoading.value = false
            }
            return
        }

        val url = "https://api.siliconflow.cn/v1/chat/completions"

        val jsonBody = JSONObject()
        jsonBody.put("model", currentModel)
        jsonBody.put("temperature", 0.6) // Lower temperature to reduce hallucinations
        
        val messagesArray = JSONArray()
        
        val systemMessage = JSONObject()
        systemMessage.put("role", "system")
        systemMessage.put("content", systemInstruction)
        messagesArray.put(systemMessage)
        
        val userMessageObj = JSONObject()
        userMessageObj.put("role", "user")
        userMessageObj.put("content", userPrompt)
        messagesArray.put(userMessageObj)

        jsonBody.put("messages", messagesArray)
        jsonBody.put("stream", false)

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $currentApiKey")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    if (sessionId != currentSessionId) return@launch
                    _messages.value += AiMessage(
                        id = UUID.randomUUID().toString(),
                        content = "Network Error: ${e.message}",
                        isUser = false
                    )
                    _isLoading.value = false
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                         viewModelScope.launch {
                            if (sessionId != currentSessionId) return@launch
                            _messages.value += AiMessage(
                                id = UUID.randomUUID().toString(),
                                content = "API Error: ${response.code} ${response.message}",
                                isUser = false
                            )
                            _isLoading.value = false
                        }
                        return
                    }

                    try {
                        val responseBody = it.body?.string() ?: ""
                        val jsonResponse = JSONObject(responseBody)
                        val rawContent = jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")

                        // Enhanced regex to catch ACTION tags even if slightly malformed
                        val actionRegex = "<ACTION>(.*?)(?:</ACTION>|$)".toRegex(RegexOption.DOT_MATCHES_ALL)
                        val matchResult = actionRegex.find(rawContent)
                        
                        var displayContent = if (matchResult != null) {
                            rawContent.replace(matchResult.value, "").trim()
                        } else {
                            rawContent
                        }

                        // Fallback: If no ACTION tag found, check for raw JSON structure if intent seems like action
                // This handles cases where model outputs JSON without tags
                if (matchResult == null && (rawContent.contains("{\"type\":\"create_") || rawContent.contains("{\"type\": \"create_") || rawContent.contains("{\"type\":\"update_") || rawContent.contains("{\"type\": \"update_"))) {
                    val jsonRegex = "\\{.*\"type\"\\s*:\\s*\"(create|update)_(note|todo)\".*\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
                    val jsonMatch = jsonRegex.find(rawContent)
                    if (jsonMatch != null) {
                        // Treat the whole content as action if it's mostly JSON, or strip it
                        displayContent = rawContent.replace(jsonMatch.value, "").trim()
                        // Manually construct a match result-like behavior
                        try {
                            val jsonStr = jsonMatch.value
                            val actionJson = JSONObject(jsonStr)
                            val type = actionJson.getString("type")
                            if (type == "create_note") {
                                val title = actionJson.optString("title", "New Note")
                                val noteContent = actionJson.optString("content", "")
                                action = AiAction.CreateNote(title, noteContent)
                            } else if (type == "create_todo") {
                                val todoContent = actionJson.optString("content", "")
                                action = AiAction.CreateTodo(todoContent)
                            } else if (type == "update_note") {
                                val id = actionJson.getLong("id")
                                val title = actionJson.optString("title", "")
                                val content = actionJson.optString("content", "")
                                action = AiAction.UpdateNote(id, title, content)
                            } else if (type == "update_todo") {
                                val id = actionJson.getLong("id")
                                val content = actionJson.optString("content", "")
                                action = AiAction.UpdateTodo(id, content)
                            }
                        } catch (e: Exception) {
                            // Ignore fallback parsing errors
                        }
                    }
                }

                        // Remove markdown bolding (**text**) and italics (*text*)
                        displayContent = displayContent.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // Remove bold
                        displayContent = displayContent.replace(Regex("\\*(.*?)\\*"), "$1")       // Remove italics
                        // Replace markdown bullet points (* Item) with dashes (- Item)
                        displayContent = displayContent.replace(Regex("^\\s*\\*\\s+", RegexOption.MULTILINE), "- ")
                        
                        // Clean up any residual ACTION tags that might have been missed
                        displayContent = displayContent.replace("<ACTION>", "").replace("</ACTION>", "")

                        // var action: AiAction? = null (Already defined above)
                        var parseError: String? = null

                        if (matchResult != null && action == null) {
                            try {
                                var actionJsonStr = matchResult.groupValues[1]
                                // Basic repair for common JSON errors from LLM
                                if (!actionJsonStr.trim().endsWith("}")) {
                                    actionJsonStr += "}"
                                }
                                
                                val actionJson = JSONObject(actionJsonStr)
                                val type = actionJson.getString("type")

                                if (type == "create_note") {
                                    val title = actionJson.optString("title", "New Note")
                                    val noteContent = actionJson.optString("content", "")
                                    action = AiAction.CreateNote(title, noteContent)
                                } else if (type == "create_todo") {
                                    val todoContent = actionJson.optString("content", "")
                                    action = AiAction.CreateTodo(todoContent)
                                } else if (type == "update_note") {
                                    val id = actionJson.getLong("id")
                                    val title = actionJson.optString("title", "")
                                    val content = actionJson.optString("content", "")
                                    action = AiAction.UpdateNote(id, title, content)
                                } else if (type == "update_todo") {
                                    val id = actionJson.getLong("id")
                                    val content = actionJson.optString("content", "")
                                    action = AiAction.UpdateTodo(id, content)
                                }
                            } catch (e: Exception) {
                                parseError = "Failed to parse action: ${e.message}\nRaw: ${matchResult.groupValues[1]}"
                            }
                        }

                        viewModelScope.launch {
                            if (sessionId != currentSessionId) return@launch
                            if (displayContent.isNotBlank() || action != null) {
                                _messages.value += AiMessage(
                                    id = UUID.randomUUID().toString(),
                                    content = displayContent.ifBlank { "I have prepared an item for you." },
                                    isUser = false,
                                    pendingAction = action
                                )
                            }

                            if (parseError != null) {
                                _messages.value += AiMessage(
                                    id = UUID.randomUUID().toString(),
                                    content = parseError!!,
                                    isUser = false
                                )
                            }
                            _isLoading.value = false
                        }
                    } catch (e: Exception) {
                        viewModelScope.launch {
                            if (sessionId != currentSessionId) return@launch
                            _messages.value += AiMessage(
                                id = UUID.randomUUID().toString(),
                                content = "Parsing Error: ${e.message}",
                                isUser = false
                            )
                            _isLoading.value = false
                        }
                    }
                }
            }
        })
    }
}

class AiAssistantViewModelFactory(
    private val repository: NoteRepository,
    private val sharedPreferences: SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AiAssistantViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AiAssistantViewModel(repository, sharedPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
