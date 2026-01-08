package com.example.note.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.note.R
import com.example.note.data.AiAction
import com.example.note.data.AiMessage
import com.example.note.data.Note
import com.example.note.data.NoteRepository
import com.example.note.data.Todo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.Locale

import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip
import com.amap.api.maps.model.BitmapDescriptorFactory

enum class AiLoadingState {
    Idle,
    Thinking,
    Answering,
    Organizing
}

class AiAssistantViewModel(
    application: Application,
    private val repository: NoteRepository,
    private val sharedPreferences: SharedPreferences
) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _loadingState = MutableStateFlow(AiLoadingState.Idle)
    val loadingState: StateFlow<AiLoadingState> = _loadingState.asStateFlow()

    // Deprecated but kept for compatibility if needed, though we should prefer loadingState
    val isLoading: StateFlow<Boolean> = _loadingState.map { it != AiLoadingState.Idle }.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        false
    )

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _modelName = MutableStateFlow("Qwen/Qwen2.5-Coder-7B-Instruct")
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private val _savedModels = MutableStateFlow<List<String>>(emptyList())
    val savedModels: StateFlow<List<String>> = _savedModels.asStateFlow()
    private val _autoSummary = MutableStateFlow(false)
    val autoSummary: StateFlow<Boolean> = _autoSummary.asStateFlow()

    private var currentSessionId = UUID.randomUUID().toString()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        _apiKey.value = sharedPreferences.getString("ai_api_key", "") ?: ""
        val savedModel = sharedPreferences.getString("ai_model_name", "Qwen/Qwen2.5-Coder-7B-Instruct") ?: "Qwen/Qwen2.5-Coder-7B-Instruct"
        _modelName.value = savedModel
        _autoSummary.value = sharedPreferences.getBoolean("ai_auto_summary", false)

        // Load saved models list
        val savedModelsStr = sharedPreferences.getString("ai_saved_models", "") ?: ""
        val models = savedModelsStr.split(",").filter { it.isNotBlank() }.toMutableList()
        
        // Ensure current model is in the list
        if (savedModel.isNotBlank() && !models.contains(savedModel)) {
            models.add(savedModel)
        }
        // Ensure default model is in the list if empty
        if (models.isEmpty()) {
            models.add("Qwen/Qwen2.5-Coder-7B-Instruct")
        }
        _savedModels.value = models
    }

    fun toggleAutoSummary(enabled: Boolean) {
        _autoSummary.value = enabled
        sharedPreferences.edit().putBoolean("ai_auto_summary", enabled).apply()
    }

    fun saveSettings(key: String, model: String) {
        sharedPreferences.edit()
            .putString("ai_api_key", key)
            .putString("ai_model_name", model)
            .apply()
        _apiKey.value = key
        
        val oldModel = _modelName.value
        if (oldModel != model) {
            _modelName.value = model
            clearMessages() // Clear context to ensure new model/prompt takes full effect without ambiguity
        } else {
            _modelName.value = model
        }

        // Add to saved models list if new
        val currentList = _savedModels.value.toMutableList()
        if (!currentList.contains(model)) {
            currentList.add(model)
            _savedModels.value = currentList
            saveModelsList(currentList)
        }
    }

    fun selectModel(model: String) {
        if (_modelName.value != model) {
            _modelName.value = model
            sharedPreferences.edit().putString("ai_model_name", model).apply()
            clearMessages() // Clear context to ensure new model/prompt takes full effect without ambiguity
        }
    }

    fun deleteModel(model: String) {
        val currentList = _savedModels.value.toMutableList()
        if (currentList.remove(model)) {
            // If we deleted the current model, switch to another one
            if (_modelName.value == model) {
                val nextModel = currentList.firstOrNull() ?: "Qwen/Qwen2.5-Coder-7B-Instruct"
                selectModel(nextModel)
            }
            
            // Ensure the list is not empty and contains the current model
            if (currentList.isEmpty()) {
                currentList.add(_modelName.value)
            }
            
            _savedModels.value = currentList
            saveModelsList(currentList)
        }
    }

    private fun saveModelsList(models: List<String>) {
        val str = models.joinToString(",")
        sharedPreferences.edit().putString("ai_saved_models", str).apply()
    }

    fun clearMessages() {
        _messages.value = emptyList()
        currentSessionId = UUID.randomUUID().toString()
        _loadingState.value = AiLoadingState.Idle
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        val userMessage = AiMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            isUser = true
        )
        _messages.value += userMessage
        _loadingState.value = AiLoadingState.Thinking
        
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
                val useSummary = _autoSummary.value

                if (isChinese) {
                    contextBuilder.append("用户的笔记:\n")
                    if (notes.isEmpty()) {
                        contextBuilder.append("(无笔记)\n")
                    } else {
                        notes.forEach { note ->
                            val contentStr = if (useSummary) {
                                if (!note.aiSummary.isNullOrBlank()) {
                                    note.aiSummary
                                } else if (note.content.length > 50) {
                                    note.content.take(50) + "..."
                                } else {
                                    note.content
                                }
                            } else {
                                note.content
                            }
                            contextBuilder.append("- ID:${note.id}, 标题: ${note.title}, 内容: $contentStr\n")
                        }
                    }
                    
                    contextBuilder.append("\n用户的待办:\n")
                    if (todos.isEmpty()) {
                        contextBuilder.append("(无待办)\n")
                    } else {
                        todos.forEach { todo ->
                            val contentStr = if (useSummary) {
                                if (todo.content.length > 50) todo.content.take(50) + "..." else todo.content
                            } else {
                                todo.content
                            }
                            contextBuilder.append("- ID:${todo.id}, 内容: $contentStr (完成状态: ${todo.isDone})\n")
                        }
                    }

                    // Add recent conversation history (last 6 messages - 3 rounds) to context
                    // Drop the last one because it is the current user message which is sent separately as "User Question"
                    val history = _messages.value.dropLast(1).takeLast(6)
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
                            val contentStr = if (useSummary) {
                                if (!note.aiSummary.isNullOrBlank()) {
                                    note.aiSummary
                                } else if (note.content.length > 50) {
                                    note.content.take(50) + "..."
                                } else {
                                    note.content
                                }
                            } else {
                                note.content
                            }
                            contextBuilder.append("- ID:${note.id}, Title: ${note.title}, Content: $contentStr\n")
                        }
                    }
                    
                    contextBuilder.append("\nUser's Todos:\n")
                    if (todos.isEmpty()) {
                        contextBuilder.append("(No todos)\n")
                    } else {
                        todos.forEach { todo ->
                            val contentStr = if (useSummary) {
                                if (todo.content.length > 50) todo.content.take(50) + "..." else todo.content
                            } else {
                                todo.content
                            }
                            contextBuilder.append("- ID:${todo.id}, Content: $contentStr (Done: ${todo.isDone})\n")
                        }
                    }

                    // Add recent conversation history (last 6 messages - 3 rounds) to context
                    // Drop the last one because it is the current user message which is sent separately as "User Question"
                    val history = _messages.value.dropLast(1).takeLast(6)
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
                    ### 角色设定
                    你叫Ivan，是我的笔记小管家。
                    人设：温柔、俏皮、充满人情味，偶尔卖萌 (｡•̀ᴗ-)✧。把我看作朋友，聊天自然轻松。

                    ### 核心准则
                    1. **纯文本回复**：除JSON外，严禁使用Markdown（如粗体/斜体/标题/代码块）。
                    2. **指令隐形**：<ACTION>块必须放在回复的最末尾。严禁在正文中提及"ACTION"或指令细节。
                    3. **摘要模式**：除非用户明确要求查看/列出，否则不要主动展示现有笔记/待办列表。如需展示，只列标题和简要内容，**不显示ID**。
                    4. **JSON格式**：<ACTION>内必须是合法单行JSON，**严禁使用Markdown代码块包裹**。
                    5. **多指令支持**：如果是多个地点/任务，返回包含多个Action对象的JSON数组 `[...]`。

                    ### 操作指令 (Action)
                    **触发规则**：
                    1. 对于笔记/待办：只有当用户明确要求记录、提醒或修改时，才生成 <ACTION>。
                    2. **对于地点推荐/攻略**：**必须主动**为回答中提到的每一个地点生成 `create_map_note` 指令。
                       - **速度优化**：请在正文中介绍完一个地点后，**紧接着**生成该地点的 `<ACTION>`，不要等到最后。
                       - 内容拆分：不要生成一个巨大的攻略卡片。应按景点拆分，每个卡片的 `content` 是该景点的具体介绍或行程安排。

                    **格式**：
                    <ACTION>{"type":"...","...":"..."}</ACTION>
                    或多条指令：
                    <ACTION>[{"type":"...","...":"..."},{"type":"...","...":"..."}]</ACTION>

                    **指令类型**：
                    1. **创建笔记 (create_note)**
                       - title: 提取关键词（15字以内）。
                       - content: 
                         - 用户直接提供的内容 -> 原样记录。
                         - AI生成的内容（如攻略/文章） -> **必须先在正文中完整输出内容**，然后在JSON中使用 "{{LAST_RESPONSE}}"。
                         - 引用上一轮对话内容 -> 使用 "{{PREVIOUS_RESPONSE}}"。
                    
                    2. **创建待办 (create_todo)**
                       - content: 同上。

                    3. **修改 (update_note / update_todo)**
                       - 必须包含 id。

                    4. **创建地图笔记 (create_map_note)**
                       - location: 地点名称（用于搜索地图）。
                       - content: 该地点的具体介绍、游玩建议或时间安排（不要使用 {{LAST_RESPONSE}}，请直接填入精简后的文本）。
                       - **触发场景**：当用户询问地点推荐、攻略、旅行计划时，**自动**为每个地点生成一个Action。

                    ### 引用标记 (Reference Markers)
                    **仅限在JSON的值中使用，严禁出现在对话正文中！**
                    - `{{LAST_RESPONSE}}`: 代表你**刚刚生成的**全部回复内容。
                    - `{{PREVIOUS_RESPONSE}}`: 代表**上一轮**的回复内容。

                    ### 示例 (Examples)
                    用户：“记一下明天买菜”
                    AI回复：好的，已经帮你记下来啦！(｡•̀ᴗ-)✧
                    <ACTION>{"type":"create_note","title":"购物","content":"明天买菜"}</ACTION>

                    用户：“给我写一份南京攻略”
                    AI回复：南京可是个好地方呀！
                    第一天上午我们去**夫子庙**。这里是南京的文化中心...
                    <ACTION>{"type":"create_map_note","location":"南京夫子庙","content":"第一天上午：游览夫子庙，体验秦淮风光。"}</ACTION>
                    下午去**中山陵**，缅怀革命先驱...
                    <ACTION>{"type":"create_map_note","location":"中山陵","content":"第一天下午：参观中山陵，需要在公众号提前预约。"}</ACTION>

                    用户：“把刚才那个保存一下”
                    AI回复：没问题，已保存！
                    <ACTION>{"type":"create_note","title":"保存的内容","content":"{{PREVIOUS_RESPONSE}}"}</ACTION>
                    """.trimIndent()
                } else {
                    """
                    ### ROLE
                    I'm Ivan, your friendly note-taking companion.
                    Persona: Warm, playful, and human-like. Treat me as a friend.

                    ### CORE RULES
                    1. **PLAIN TEXT ONLY**: NO Markdown (bold/italics/headers/code blocks) in chat.
                    2. **INVISIBLE COMMANDS**: <ACTION> block must be at the very END. NO mention of "ACTION" or command details in the chat.
                    3. **SUMMARY MODE**: Unless explicitly asked to view/list, DO NOT show existing notes/todos. If showing, list titles/content only, **NO IDs**.
                    4. **JSON FORMAT**: <ACTION> content must be valid single-line JSON. **NO Markdown code blocks**.
                    5. **MULTIPLE ACTIONS**: If multiple places/tasks, return a JSON array `[...]` containing multiple Action objects.

                    ### COMMANDS
                    **TRIGGER RULES**:
                    1. For Notes/Todos: Generate <ACTION> ONLY when user explicitly asks to record, remind, or modify.
                    2. **For Place Recommendations/Guides**: **ALWAYS PROACTIVELY** generate `create_map_note` actions for every location mentioned.
                       - **SPEED OPTIMIZATION**: Generate the `<ACTION>` for a place **IMMEDIATELY** after describing it in the text, do not wait until the end.
                       - CONTENT SPLITTING: Do not generate one huge card. Split by place. `content` should be the specific guide/schedule for that place.

                    **Format**:
                    <ACTION>{"type":"...","...":"..."}</ACTION>
                    Or multiple actions:
                    <ACTION>[{"type":"...","...":"..."},{"type":"...","...":"..."}]</ACTION>

                    **Types**:
                    1. **create_note**
                       - title: Extract keywords (short).
                       - content: 
                         - User provided -> Record exactly.
                         - AI generated -> **Must generate full content in chat first**, then use "{{LAST_RESPONSE}}" in JSON.
                         - Previous conversation -> Use "{{PREVIOUS_RESPONSE}}".
                    
                    2. **create_todo**
                       - content: Same as above.

                    3. **update_note / update_todo**
                       - Must include id.

                    4. **create_map_note**
                       - location: Place name (for map search).
                       - content: Specific guide/intro/schedule for that place (DO NOT use {{LAST_RESPONSE}}, fill in summarized text directly).
                       - **Trigger**: AUTOMATICALLY generate one action per place when answering recommendation/guide queries.

                    ### REFERENCE MARKERS
                    **ONLY use inside JSON values, NEVER in chat text!**
                    - `{{LAST_RESPONSE}}`: Represents the FULL content you just generated in this reply.
                    - `{{PREVIOUS_RESPONSE}}`: Represents the content of the PREVIOUS reply.

                    ### EXAMPLES
                    User: "Buy milk tomorrow"
                    AI: Got it! (｡•̀ᴗ-)✧
                    <ACTION>{"type":"create_note","title":"Shopping","content":"Buy milk tomorrow"}</ACTION>

                    User: "Write a guide for Nanjing"
                    AI: Nanjing is amazing!
                    First, visit **Confucius Temple**...
                    <ACTION>{"type":"create_map_note","location":"Confucius Temple","content":"Morning: Visit Confucius Temple."}</ACTION>
                    Then go to **Sun Yat-sen Mausoleum**...
                    <ACTION>{"type":"create_map_note","location":"Sun Yat-sen Mausoleum","content":"Afternoon: Visit Sun Yat-sen Mausoleum."}</ACTION>
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
                    content = getApplication<Application>().getString(R.string.data_preparation_error, e.message),
                    isUser = false
                )
                _messages.value += errorMessage
                _loadingState.value = AiLoadingState.Idle
            }
        }
    }

    fun executeAction(messageId: String, action: AiAction) {
        val messageList = _messages.value.toMutableList()
        val index = messageList.indexOfFirst { it.id == messageId }
        if (index != -1) {
            val message = messageList[index]
            if (message.pendingActions.contains(action)) {
                _loadingState.value = AiLoadingState.Thinking // Show loading
                viewModelScope.launch {
                    try {
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
                                    content = getApplication<Application>().getString(R.string.note_saved, action.title),
                                    isUser = false
                                )
                            }
                            is AiAction.CreateTodo -> {
                                repository.insertTodo(
                                    Todo(content = action.content)
                                )
                                _messages.value += AiMessage(
                                    id = UUID.randomUUID().toString(),
                                    content = getApplication<Application>().getString(R.string.todo_saved, action.content),
                                    isUser = false
                                )
                            }
                            is AiAction.CreateMapNote -> {
                                createMapNoteFromMessage(action.locationName, action.content)
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
                                        content = getApplication<Application>().getString(R.string.note_updated, action.title),
                                        isUser = false
                                    )
                                } else {
                                    _messages.value += AiMessage(
                                        id = UUID.randomUUID().toString(),
                                        content = getApplication<Application>().getString(R.string.note_not_found, action.id),
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
                                        content = getApplication<Application>().getString(R.string.todo_updated, action.content),
                                        isUser = false
                                    )
                                } else {
                                    _messages.value += AiMessage(
                                        id = UUID.randomUUID().toString(),
                                        content = getApplication<Application>().getString(R.string.todo_not_found, action.id),
                                        isUser = false
                                    )
                                }
                            }
                        }
                        // Remove the executed action from the list
                        val currentList = _messages.value.toMutableList()
                        val currentIndex = currentList.indexOfFirst { it.id == messageId }
                        if (currentIndex != -1) {
                            val currentMessage = currentList[currentIndex]
                            val updatedActions = currentMessage.pendingActions.filter { it != action }
                            currentList[currentIndex] = currentMessage.copy(pendingActions = updatedActions)
                            _messages.value = currentList
                        }
                    } catch (e: Exception) {
                        _messages.value += AiMessage(
                            id = UUID.randomUUID().toString(),
                            content = "Error executing action: ${e.message}",
                            isUser = false
                        )
                    } finally {
                        _loadingState.value = AiLoadingState.Idle // Hide loading
                    }
                }
            }
        }
    }

    fun cancelAction(messageId: String, action: AiAction) {
        val messageList = _messages.value.toMutableList()
        val index = messageList.indexOfFirst { it.id == messageId }
        if (index != -1) {
            val currentMessage = messageList[index]
            val updatedActions = currentMessage.pendingActions.filter { it != action }
            messageList[index] = currentMessage.copy(pendingActions = updatedActions)
            _messages.value = messageList
        }
    }

    fun createNoteFromMessage(content: String) {
        viewModelScope.launch {
            _loadingState.value = AiLoadingState.Thinking
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
                content = getApplication<Application>().getString(R.string.note_created_from_message, generatedTitle),
                isUser = false
            )
            _loadingState.value = AiLoadingState.Idle
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
                content = getApplication<Application>().getString(R.string.todo_created_from_message),
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
                    content = getApplication<Application>().getString(R.string.set_api_key_message),
                    isUser = false
                )
                _loadingState.value = AiLoadingState.Idle
            }
            return
        }

        val url = "https://api.siliconflow.cn/v1/chat/completions"

        val jsonBody = JSONObject()
        jsonBody.put("model", currentModel)
        jsonBody.put("temperature", 0.5)
        
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
        jsonBody.put("stream", true)

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
                        content = getApplication<Application>().getString(R.string.network_error, e.message),
                        isUser = false
                    )
                    _loadingState.value = AiLoadingState.Idle
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                         viewModelScope.launch {
                            if (sessionId != currentSessionId) return@launch
                            _messages.value += AiMessage(
                                id = UUID.randomUUID().toString(),
                                content = getApplication<Application>().getString(R.string.api_error, response.code, response.message),
                                isUser = false
                            )
                            _loadingState.value = AiLoadingState.Idle
                        }
                        return
                    }

                    val responseId = UUID.randomUUID().toString()
                    var fullContent = ""
                    var isFirstChunk = true

                    try {
                        val reader = it.body?.byteStream()?.bufferedReader() ?: return
                        var line: String? = reader.readLine()

                        while (line != null) {
                            if (line.startsWith("data: ")) {
                                val data = line.removePrefix("data: ").trim()
                                if (data == "[DONE]") break

                                try {
                                    val json = JSONObject(data)
                                    val choice = json.getJSONArray("choices").getJSONObject(0)
                                    val delta = choice.getJSONObject("delta")
                                    if (delta.has("content")) {
                                        val contentChunk = delta.getString("content")
                                        fullContent += contentChunk

                                        viewModelScope.launch {
                                            if (sessionId != currentSessionId) return@launch
                                            
                                            // Process content to hide raw ACTION tag during streaming
                                            var displayContent = fullContent
                                            
                                            // Optimized: Hide all <ACTION> blocks (complete or incomplete) from display
                                            // Use regex to replace all <ACTION>...</ACTION> blocks with empty string
                                            // Also handle the case where <ACTION> is incomplete at the end
                                            
                                            // 1. Remove complete action blocks
                                            displayContent = displayContent.replace(Regex("<ACTION>.*?</ACTION>", RegexOption.DOT_MATCHES_ALL), "")
                                            
                                            // 2. Remove incomplete action block at the end if any
                                            val incompleteActionStart = displayContent.indexOf("<ACTION>")
                                            if (incompleteActionStart != -1) {
                                                displayContent = displayContent.substring(0, incompleteActionStart)
                                                // Switch to Organizing state as soon as we detect an action start
                                                if (_loadingState.value != AiLoadingState.Organizing) {
                                                    _loadingState.value = AiLoadingState.Organizing
                                                }
                                            }
                                            
                                            // Real-time Markdown cleanup to prevent artifacts from lingering until response completion
                                            // 1. Remove bold markers (**text**) -> text
                                            displayContent = displayContent.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                                            // 2. Remove italic markers (*text*) -> text
                                            displayContent = displayContent.replace(Regex("\\*(.*?)\\*"), "$1")
                                            // 3. Convert bullet points (* item) -> - item
                                            displayContent = displayContent.replace(Regex("^\\s*\\*\\s+", RegexOption.MULTILINE), "- ")
                                            // 4. Remove header markers (### Title) -> Title
                                            displayContent = displayContent.replace(Regex("#{1,6}\\s*"), "")
                                            
                                            displayContent = displayContent.trim()
                                            
                                            if (isFirstChunk) {
                                                _messages.value += AiMessage(
                                                    id = responseId,
                                                    content = displayContent,
                                                    isUser = false
                                                )
                                                isFirstChunk = false
                                                _loadingState.value = AiLoadingState.Answering
                                            } else {
                                                val currentList = _messages.value.toMutableList()
                                                val index = currentList.indexOfFirst { msg -> msg.id == responseId }
                                                if (index != -1) {
                                                    currentList[index] = currentList[index].copy(content = displayContent)
                                                    _messages.value = currentList
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Ignore chunk errors
                                }
                            }
                            line = reader.readLine()
                        }

                        _loadingState.value = AiLoadingState.Organizing

                        viewModelScope.launch {
                            if (sessionId != currentSessionId) return@launch
                            
                            // Find previous AI response for context reference
                            val previousAiMessage = _messages.value.lastOrNull { !it.isUser && it.id != responseId }
                            val previousContent = previousAiMessage?.content

                            val (displayContent, action, parseError) = parseAiResponse(fullContent, previousContent)
                            
                            val currentList = _messages.value.toMutableList()
                            val index = currentList.indexOfFirst { msg -> msg.id == responseId }
                            if (index != -1) {
                                val finalContent = displayContent.ifBlank { getApplication<Application>().getString(R.string.ai_prepared_item) }
                                
                                currentList[index] = currentList[index].copy(
                                    content = finalContent,
                                    pendingActions = action
                                )
                                _messages.value = currentList

                                if (parseError != null) {
                                    _messages.value += AiMessage(
                                        id = UUID.randomUUID().toString(),
                                        content = parseError,
                                        isUser = false
                                    )
                                }
                            }
                            _loadingState.value = AiLoadingState.Idle
                        }
                    } catch (e: Exception) {
                        viewModelScope.launch {
                            if (sessionId != currentSessionId) return@launch
                             _messages.value += AiMessage(
                                id = UUID.randomUUID().toString(),
                                content = getApplication<Application>().getString(R.string.parsing_error, e.message),
                                isUser = false
                            )
                            _loadingState.value = AiLoadingState.Idle
                        }
                    }
                }
            }
        })
    }

    fun createMapNoteFromMessage(locationName: String, content: String) {
        viewModelScope.launch {
            _loadingState.value = AiLoadingState.Thinking
            try {
                // Search for location
                val tips = searchLocation(locationName)
                
                if (tips.isNotEmpty()) {
                    val firstMatch = tips.first()
                    val point = firstMatch.point
                    
                    if (point != null) {
                         repository.insertNote(
                            Note(
                                title = locationName,
                                content = content,
                                timestamp = System.currentTimeMillis(),
                                latitude = point.latitude,
                                longitude = point.longitude,
                                address = firstMatch.address ?: firstMatch.name,
                                markerColor = BitmapDescriptorFactory.HUE_AZURE
                            )
                        )
                        _messages.value += AiMessage(
                            id = UUID.randomUUID().toString(),
                            content = getApplication<Application>().getString(R.string.map_note_saved, locationName),
                            isUser = false
                        )
                    } else {
                        // Fallback: Save as regular note if no coordinates
                         repository.insertNote(
                            Note(
                                title = locationName,
                                content = content,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                         _messages.value += AiMessage(
                            id = UUID.randomUUID().toString(),
                            content = getApplication<Application>().getString(R.string.location_not_found, locationName) + " (Saved as regular note)",
                            isUser = false
                        )
                    }
                } else {
                     repository.insertNote(
                        Note(
                            title = locationName,
                            content = content,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    _messages.value += AiMessage(
                        id = UUID.randomUUID().toString(),
                        content = getApplication<Application>().getString(R.string.location_not_found, locationName) + " (Saved as regular note)",
                        isUser = false
                    )
                }
            } catch (e: Exception) {
                 _messages.value += AiMessage(
                    id = UUID.randomUUID().toString(),
                    content = "Error saving map note: ${e.message}",
                    isUser = false
                )
            } finally {
                _loadingState.value = AiLoadingState.Idle
            }
        }
    }

    private suspend fun searchLocation(keyword: String): List<Tip> = suspendCancellableCoroutine { continuation ->
        val query = InputtipsQuery(keyword, "")
        query.cityLimit = false
        val inputTips = Inputtips(getApplication(), query)
        
        inputTips.setInputtipsListener { tips, rCode ->
            if (continuation.isActive) {
                if (rCode == 1000 && tips != null) {
                    continuation.resume(tips)
                } else {
                    continuation.resume(emptyList())
                }
            }
        }
        
        inputTips.requestInputtipsAsyn()
    }

    private fun resolveContent(jsonContent: String, displayContent: String, previousResponse: String?): String {
        val trimmed = jsonContent.trim()
        return if (trimmed == "{{LAST_RESPONSE}}") {
            displayContent
        } else if (trimmed == "{{PREVIOUS_RESPONSE}}") {
            previousResponse ?: displayContent
        } else {
            jsonContent
        }
    }

    private fun parseAiResponse(rawContent: String, previousAiResponse: String? = null): Triple<String, List<AiAction>, String?> {
        val actions = mutableListOf<AiAction>()
        var parseError: String? = null
        var displayContent = rawContent

        val actionRegex = "<ACTION>(.*?)(?:</ACTION>|$)".toRegex(RegexOption.DOT_MATCHES_ALL)
        
        // 1. Try to find ALL explicit <ACTION> tags (support multiple scattered actions)
        val matches = actionRegex.findAll(rawContent)
        
        // If matches found, process them and remove from display content
        if (matches.any()) {
            // Remove all action blocks from display content
            displayContent = rawContent.replace(actionRegex, "").trim()
            
            // Clean up Markdown in displayContent FIRST so we can use it for replacement
            displayContent = displayContent.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            displayContent = displayContent.replace(Regex("\\*(.*?)\\*"), "$1")
            displayContent = displayContent.replace(Regex("^\\s*\\*\\s+", RegexOption.MULTILINE), "- ")
            displayContent = displayContent.replace(Regex("#{1,6}\\s*"), "")
            
            matches.forEach { matchResult ->
                try {
                    var actionJsonStr = matchResult.groupValues[1].trim()
                    // Clean up markdown code blocks if present (common LLM behavior)
                    if (actionJsonStr.startsWith("```")) {
                         actionJsonStr = actionJsonStr.replace(Regex("^```[a-zA-Z]*\\s*"), "")
                                                     .replace(Regex("\\s*```$"), "")
                                                     .trim()
                    }

                    // Check if it's an array or a single object
                    if (actionJsonStr.startsWith("[")) {
                        if (!actionJsonStr.endsWith("]")) {
                            actionJsonStr += "]"
                        }
                        val jsonArray = JSONArray(actionJsonStr)
                        for (i in 0 until jsonArray.length()) {
                            val actionJson = jsonArray.getJSONObject(i)
                            parseActionJsonObject(actionJson, displayContent, previousAiResponse)?.let { actions.add(it) }
                        }
                    } else {
                        if (!actionJsonStr.endsWith("}")) {
                            actionJsonStr += "}"
                        }
                        val actionJson = JSONObject(actionJsonStr)
                        parseActionJsonObject(actionJson, displayContent, previousAiResponse)?.let { actions.add(it) }
                    }
                } catch (e: Exception) {
                    parseError = getApplication<Application>().getString(R.string.action_parse_failed, e.message, matchResult.groupValues[1])
                }
            }
        } else if (rawContent.contains("{\"type\":\"create_") || rawContent.contains("{\"type\": \"create_") || rawContent.contains("{\"type\":\"update_") || rawContent.contains("{\"type\": \"update_")) {
            // 2. Fallback: Try to find JSON without tags (only if <ACTION> not found)
            val jsonRegex = "\\{.*\"type\"\\s*:\\s*\"(create|update)_(note|todo|map_note)\".*\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
            val jsonMatch = jsonRegex.find(rawContent)
            if (jsonMatch != null) {
                displayContent = rawContent.replace(jsonMatch.value, "").trim()
                
                // Clean up Markdown in displayContent
                displayContent = displayContent.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                displayContent = displayContent.replace(Regex("\\*(.*?)\\*"), "$1")
                displayContent = displayContent.replace(Regex("^\\s*\\*\\s+", RegexOption.MULTILINE), "- ")
                displayContent = displayContent.replace(Regex("#{1,6}\\s*"), "")
                
                try {
                    val jsonStr = jsonMatch.value
                    val actionJson = JSONObject(jsonStr)
                    parseActionJsonObject(actionJson, displayContent, previousAiResponse)?.let { actions.add(it) }
                } catch (e: Exception) {
                    // Ignore fallback errors
                }
            } else {
                // If no JSON match, still clean up Markdown
                 displayContent = displayContent.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                 displayContent = displayContent.replace(Regex("\\*(.*?)\\*"), "$1")
                 displayContent = displayContent.replace(Regex("^\\s*\\*\\s+", RegexOption.MULTILINE), "- ")
                 displayContent = displayContent.replace(Regex("#{1,6}\\s*"), "")
            }
        } else {
             // If no action at all, clean up Markdown
             displayContent = displayContent.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
             displayContent = displayContent.replace(Regex("\\*(.*?)\\*"), "$1")
             displayContent = displayContent.replace(Regex("^\\s*\\*\\s+", RegexOption.MULTILINE), "- ")
             displayContent = displayContent.replace(Regex("#{1,6}\\s*"), "")
        }

        displayContent = displayContent.replace("<ACTION>", "").replace("</ACTION>", "")

        // 4. If display content is empty but we have a creation action, use the action content
        // This fixes the issue where AI generates a note/todo but puts the content ONLY in the action,
        // leaving the chat bubble empty.
        if (displayContent.isBlank() && actions.isNotEmpty()) {
            val firstAction = actions.first()
            when (firstAction) {
                is AiAction.CreateNote -> displayContent = firstAction.content
                is AiAction.CreateTodo -> displayContent = firstAction.content
                is AiAction.CreateMapNote -> displayContent = firstAction.content
                else -> {} // For updates, we usually don't need to echo the content back if AI didn't say anything
            }
        }

        return Triple(displayContent, actions, parseError)
    }

    private fun parseActionJsonObject(actionJson: JSONObject, displayContent: String, previousAiResponse: String?): AiAction? {
        val type = actionJson.getString("type")
        return if (type == "create_note") {
            val title = actionJson.optString("title", "New Note")
            var noteContent = actionJson.optString("content", "")
            
            noteContent = resolveContent(noteContent, displayContent, previousAiResponse)
            
            AiAction.CreateNote(title, noteContent)
        } else if (type == "create_todo") {
            var todoContent = actionJson.optString("content", "")
            
            todoContent = resolveContent(todoContent, displayContent, previousAiResponse)
            
            AiAction.CreateTodo(todoContent)
        } else if (type == "create_map_note") {
            val location = actionJson.optString("location", "")
            var content = actionJson.optString("content", "")
            
            content = resolveContent(content, displayContent, previousAiResponse)
            
            AiAction.CreateMapNote(location, content)
        } else if (type == "update_note") {
            val id = actionJson.getLong("id")
            val title = actionJson.optString("title", "")
            var content = actionJson.optString("content", "")
            
            content = resolveContent(content, displayContent, previousAiResponse)
            
            AiAction.UpdateNote(id, title, content)
        } else if (type == "update_todo") {
            val id = actionJson.getLong("id")
            var content = actionJson.optString("content", "")
            
            content = resolveContent(content, displayContent, previousAiResponse)
            
            AiAction.UpdateTodo(id, content)
        } else {
            null
        }
    }
}

class AiAssistantViewModelFactory(
    private val application: Application,
    private val repository: NoteRepository,
    private val sharedPreferences: SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AiAssistantViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AiAssistantViewModel(application, repository, sharedPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
