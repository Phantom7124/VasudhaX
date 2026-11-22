package com.example.executorchllamademo

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule

data class ChatMessage(val text: String, val fromUser: Boolean)

/** What the chat was opened for: the scanned item's info (empty for a general chat). */
data class ChatRequest(val context: String, val title: String, val category: String)

/**
 * Runs the Llama model for the chat screen. One instance lives for the whole app session,
 * so the model is loaded only once.
 */
class ChatEngine {
    private var module: LlmModule? = null

    @Volatile var ready = false
    @Volatile var generating = false

    private var firstTurn = true
    private var sawHeader = false

    private val settings = ModuleSettings(
        systemPrompt = ChatLauncher.SYSTEM_PROMPT,
        userPrompt = PromptFormat.getUserPromptTemplate(ModelType.LLAMA_3),
        modelType = ModelType.LLAMA_3,
        backendType = BackendType.XNNPACK
    )

    /** Loads the model on a background thread. onDone gets null on success or an error message. */
    fun load(onDone: (String?) -> Unit) {
        Thread {
            try {
                if (module == null) {
                    val m = LlmModule(
                        ModelUtils.getModelCategory(ModelType.LLAMA_3, BackendType.XNNPACK),
                        ChatLauncher.MODEL_PATH,
                        ChatLauncher.TOKENIZER_PATH,
                        0.0f
                    )
                    m.load()
                    module = m
                }
                ready = true
                onDone(null)
            } catch (e: Exception) {
                onDone(e.message ?: "The model could not be loaded")
            }
        }.start()
    }

    /** Starts a new conversation (clears what the model remembers). */
    fun newConversation() {
        try {
            module?.resetContext()
        } catch (e: Exception) {
            // ignore
        }
        firstTurn = true
    }

    fun stop() {
        try {
            module?.stop()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun ask(question: String, scanContext: String, onToken: (String) -> Unit, onDone: () -> Unit) {
        val m = module
        if (m == null || generating) return
        generating = true

        val text = if (firstTurn && scanContext.isNotEmpty()) {
            "Information about the scanned item: $scanContext\n\nQuestion: $question"
        } else {
            question
        }
        val prompt = (if (firstTurn) settings.getFormattedSystemPrompt() else "") +
                settings.getFormattedUserPrompt(text, false)
        firstTurn = false
        sawHeader = false

        Thread {
            try {
                m.generate(prompt, AppSettings.DEFAULT_MAX_SEQ_LEN, object : LlmCallback {
                    override fun onResult(result: String) {
                        if (result == "<|eot_id|>") {
                            m.stop()
                            return
                        }
                        if (result == "<|start_header_id|>") {
                            sawHeader = true
                            return
                        }
                        if (result == "<|end_header_id|>") {
                            sawHeader = false
                            return
                        }
                        if (sawHeader) return
                        onToken(result)
                    }

                    override fun onStats(stats: String) {}
                }, false)
            } catch (e: Exception) {
                // an empty answer is replaced by a friendly message in the screen
            }
            generating = false
            onDone()
        }.start()
    }
}

@Composable
fun ChatScreen(
    engine: ChatEngine,
    request: ChatRequest,
    messages: SnapshotStateList<ChatMessage>,
    showBack: Boolean,
    onBack: () -> Unit
) {
    val accent = if (request.category.isEmpty()) MaterialTheme.colorScheme.primary
    else categoryColor(request.category)
    var input by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(engine.ready) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val listState = rememberLazyListState()

    // New conversation for this item, and make sure the model is loaded.
    LaunchedEffect(request) {
        if (messages.isEmpty()) engine.newConversation()
        if (engine.ready) {
            ready = true
        } else {
            ready = false
            engine.load { err ->
                main.post {
                    if (err == null) ready = true else loadError = err
                }
            }
        }
    }

    // Stop any running answer when the screen is closed.
    DisposableEffect(Unit) {
        onDispose { engine.stop() }
    }

    // Keep the newest text in view while the answer streams in.
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || generating || !ready) return
        messages.add(ChatMessage(q, true))
        messages.add(ChatMessage("", false))
        input = ""
        generating = true
        engine.ask(
            q,
            request.context,
            onToken = { t ->
                main.post {
                    val i = messages.lastIndex
                    if (i >= 0) messages[i] = messages[i].copy(text = messages[i].text + t)
                }
            },
            onDone = {
                main.post {
                    val i = messages.lastIndex
                    if (i >= 0 && !messages[i].fromUser && messages[i].text.isBlank()) {
                        messages[i] = messages[i].copy(
                            text = "Sorry, I couldn't answer that. Try again or tap New chat."
                        )
                    }
                    generating = false
                }
            }
        )
    }

    val suggestions = if (request.context.isNotEmpty()) {
        listOf("How do I dispose of this?", "Can this be reused?", "Why does this matter?")
    } else {
        listOf("How do I recycle plastic bottles?", "What is e-waste?", "Where should I throw old batteries?")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Header
        Surface(color = Color.White, shadowElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBack) {
                    TextButton(onClick = onBack) { Icon24("back", Color(0xFF1B6B43), 24.dp) }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon24(if (request.category.isEmpty()) "chat" else categoryIcon(request.category), accent, 22.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ask the assistant", style = MaterialTheme.typography.titleMedium)
                    Text(
                        request.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF7A857E)
                    )
                }
                TextButton(onClick = {
                    engine.stop()
                    engine.newConversation()
                    messages.clear()
                    generating = false
                }) { Text("New chat") }
            }
        }

        // Loading or error banner
        if (loadError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFDECEA))
                    .padding(14.dp)
            ) {
                Text(
                    "Could not load the assistant: $loadError\nCheck that the model files are in /data/local/tmp/llama.",
                    color = Color(0xFFB71C1C),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (!ready) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Loading the assistant. The first time takes a few seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF55605A)
                )
            }
        }

        // Messages or the welcome area
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD3EADB)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon24(if (request.category.isEmpty()) "chat" else categoryIcon(request.category), Color(0xFF1B6B43), 28.dp)
                }
                Text(
                    if (request.context.isNotEmpty()) "Ask anything about this item" else "Ask me about waste and recycling",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    "Everything runs on your phone, with no internet needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF55605A)
                )
                Spacer(Modifier.height(4.dp))
                suggestions.forEach { s ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0xFFD9E2DB), RoundedCornerShape(16.dp))
                            .clickable { send(s) }
                            .padding(16.dp)
                    ) {
                        Text(s, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    val thinking = !msg.fromUser && msg.text.isEmpty() && generating
                    Bubble(msg, thinking)
                }
            }
        }

        // Input bar
        Surface(color = Color.White, shadowElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .then(if (showBack) Modifier.navigationBarsPadding() else Modifier)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask a question") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 3,
                    enabled = ready
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (ready) MaterialTheme.colorScheme.primary else Color(0xFFB0BEC5))
                        .clickable {
                            if (generating) engine.stop() else send(input)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon24(if (generating) "stop" else "send", Color.White, 22.dp)
                }
            }
        }
    }
}

@Composable
private fun Bubble(msg: ChatMessage, thinking: Boolean) {
    val user = msg.fromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (user) 18.dp else 4.dp,
                        bottomEnd = if (user) 4.dp else 18.dp
                    )
                )
                .background(if (user) MaterialTheme.colorScheme.primary else Color.White)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                if (thinking) "Thinking…" else msg.text,
                color = if (user) Color.White else Color(0xFF1B1F1D),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}