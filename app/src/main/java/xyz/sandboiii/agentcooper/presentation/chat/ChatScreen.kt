package xyz.sandboiii.agentcooper.presentation.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import xyz.sandboiii.agentcooper.domain.model.MessageRole
import xyz.sandboiii.agentcooper.util.Constants
import com.daksh.mdparserkit.core.parseMarkdown

@Composable
fun ChatScreen(
    sessionId: String,
    modelId: String,
    onNavigateBack: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    // Get title based on session ID
    val screenTitle = remember(sessionId) {
        when (sessionId) {
            Constants.LOGICAL_CHAT_DIRECT_ID -> "Прямое решение"
            Constants.LOGICAL_CHAT_STEP_BY_STEP_ID -> "Пошаговое решение"
            Constants.LOGICAL_CHAT_PROMPT_WRITER_ID -> "Создатель промптов"
            Constants.LOGICAL_CHAT_EXPERTS_ID -> "Команда экспертов"
            else -> "Agent Cooper"
        }
    }
    
    // Check if this is a logical problem chat
    val isLogicalProblemChat = remember(sessionId) {
        sessionId == Constants.LOGICAL_CHAT_DIRECT_ID ||
        sessionId == Constants.LOGICAL_CHAT_STEP_BY_STEP_ID ||
        sessionId == Constants.LOGICAL_CHAT_PROMPT_WRITER_ID ||
        sessionId == Constants.LOGICAL_CHAT_EXPERTS_ID
    }
    
    // Confirmation dialog state
    var showClearDialog by remember { mutableStateOf(false) }
    LaunchedEffect(sessionId, modelId) {
        viewModel.initialize(sessionId, modelId)
    }
    
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by viewModel.state.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = ChatState.Loading)
    val welcomeMessageEnabled by viewModel.welcomeMessageEnabled.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = true)
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isInputFocused by remember { mutableStateOf(false) }
    
    // Helper function to scroll to bottom edge
    suspend fun scrollToBottomEdge(filteredMessages: List<*>) {
        if (filteredMessages.isEmpty()) return
        
        kotlinx.coroutines.delay(100) // Small delay to ensure layout is ready
        // Scroll to last item
        listState.animateScrollToItem(filteredMessages.size - 1)
        // Wait for scroll animation to complete
        kotlinx.coroutines.delay(100)
        
        // Calculate total content height and scroll to bottom edge
        val layoutInfo = listState.layoutInfo
        var totalHeight = 0
        layoutInfo.visibleItemsInfo.forEach { itemInfo ->
            totalHeight += itemInfo.size
        }
        
        // Get the last item's bottom position
        val lastItemIndex = filteredMessages.size - 1
        val lastItemInfo = layoutInfo.visibleItemsInfo.lastOrNull { it.index == lastItemIndex }
        if (lastItemInfo != null) {
            val lastItemBottom = lastItemInfo.offset + lastItemInfo.size
            val viewportHeight = layoutInfo.viewportSize.height
            val scrollOffset = lastItemBottom - viewportHeight + layoutInfo.afterContentPadding
            
            // Scroll to the calculated offset
            if (scrollOffset > 0) {
                listState.animateScrollToItem(
                    index = lastItemIndex,
                    scrollOffset = scrollOffset
                )
            }
        }
    }
    
    // Scroll to bottom when new messages arrive
    LaunchedEffect(state, welcomeMessageEnabled) {
        if (state is ChatState.Success) {
            val messages = (state as ChatState.Success).messages
            val filteredMessages = if (welcomeMessageEnabled) {
                messages
            } else {
                messages.filter { !it.id.startsWith("welcome-") }
            }
            if (filteredMessages.isNotEmpty()) {
                scrollToBottomEdge(filteredMessages)
            }
        }
    }
    
    // Scroll to bottom when keyboard appears or message text changes
    LaunchedEffect(messageText, welcomeMessageEnabled) {
        if (messageText.isNotEmpty()) {
            val currentState = state
            if (currentState is ChatState.Success) {
                val messages = currentState.messages
                val filteredMessages = if (welcomeMessageEnabled) {
                    messages
                } else {
                    messages.filter { !it.id.startsWith("welcome-") }
                }
                if (filteredMessages.isNotEmpty()) {
                    scrollToBottomEdge(filteredMessages)
                }
            }
        }
    }
    
    // Scroll to bottom when input field gets focus (keyboard opens)
    LaunchedEffect(isInputFocused) {
        if (isInputFocused) {
            val currentState = state
            if (currentState is ChatState.Success) {
                val messages = currentState.messages
                val filteredMessages = if (welcomeMessageEnabled) {
                    messages
                } else {
                    messages.filter { !it.id.startsWith("welcome-") }
                }
                if (filteredMessages.isNotEmpty()) {
                    // Delay to allow keyboard animation to start
                    kotlinx.coroutines.delay(150)
                    scrollToBottomEdge(filteredMessages)
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    if (isLogicalProblemChat) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Очистить чат"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            when (val currentState = state) {
                is ChatState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                is ChatState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Ошибка",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = currentState.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(onClick = { viewModel.handleIntent(ChatIntent.ClearError) }) {
                                Text("Повторить")
                            }
                        }
                    }
                }
                
                is ChatState.Success -> {
                    // Messages list
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(
                            top = 16.dp,
                            bottom = 16.dp // Extra padding at bottom to keep messages visible above input
                        )
                    ) {
                        // Filter messages based on welcome message toggle
                        val filteredMessages = if (welcomeMessageEnabled) {
                            currentState.messages
                        } else {
                            currentState.messages.filter { !it.id.startsWith("welcome-") }
                        }
                        
                        // Show all messages (user and assistant) - welcome message is now in the database
                        items(filteredMessages) { message ->
                            MessageBubble(
                                message = message,
                                isStreaming = currentState.isStreaming &&
                                        message.id == "streaming",
                                onSuggestionClick = { suggestion ->
                                    viewModel.handleIntent(ChatIntent.SendMessage(suggestion))
                                }
                            )
                        }
                        
                        // Error indicator for last message
                        if (currentState.lastError != null) {
                            item {
                                ErrorIndicator(
                                    errorMessage = currentState.lastError!!,
                                    onRetry = { viewModel.handleIntent(ChatIntent.RetryLastMessage) },
                                    onDismiss = { viewModel.handleIntent(ChatIntent.ClearError) }
                                )
                            }
                        }
                        
                        // Loading indicator when waiting for response
                        // Only show thinking animation if we're waiting for response AND there's no streaming message
                        // Once streaming starts (even with empty content), show the streaming message instead
                        val hasStreamingMessage = filteredMessages.any { it.id == "streaming" }
                        val shouldShowThinking = currentState.isWaitingForResponse && !hasStreamingMessage
                        
                        if (shouldShowThinking) {
                            item {
                                LoadingIndicator()
                            }
                        }
                    }
                    
                    // Input field
                    MessageInput(
                        text = messageText,
                        onTextChange = { messageText = it },
                        onSend = {
                            if (messageText.isNotBlank()) {
                                viewModel.handleIntent(ChatIntent.SendMessage(messageText))
                                messageText = ""
                            }
                        },
                        enabled = !currentState.isStreaming.also { 
                            android.util.Log.d("ChatScreen", "MessageInput enabled: ${!currentState.isStreaming}, isStreaming: ${currentState.isStreaming}")
                        },
                        onFocusChange = { isFocused ->
                            isInputFocused = isFocused
                        }
                    )
                }
            }
        }
    }
    
    // Clear messages confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text("Очистить чат")
            },
            text = {
                Text("Вы уверены, что хотите удалить все сообщения в этом чате? Это действие нельзя отменить.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.handleIntent(ChatIntent.ClearMessages)
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun MessageBubble(
    message: xyz.sandboiii.agentcooper.domain.model.ChatMessage,
    isStreaming: Boolean,
    onSuggestionClick: (String) -> Unit = {}
) {
    val isUser = message.role == MessageRole.USER
    var showJsonDialog by remember { mutableStateOf(false) }
    
    val slideIn = remember {
        slideInHorizontally(
            initialOffsetX = { if (isUser) it else -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    
    val fadeIn = remember {
        fadeIn(animationSpec = tween(300))
    }
    
    AnimatedVisibility(
        visible = true,
        enter = slideIn + fadeIn
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    color = if (isUser) {
                        // Use a darker, more contrasting color for user messages
                        // This ensures white text is always visible
                        Color(0xFF6200EE) // Material Design Purple 700
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Show mood if available - make it bigger and more visible
                        if (!isUser && message.mood != null && !isStreaming) {
                            Text(
                                text = message.mood,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isUser) {
                                    Color.White
                                } else {
                                    // Use onSurface for contrast on surfaceVariant background
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isStreaming) {
                                // Debug logging
                                android.util.Log.d("MessageBubble", "Streaming message - id: ${message.id}, content length: ${message.content.length}, content preview: ${message.content.take(100)}")
                                
                                // Show animated typing text when streaming
                                // Use stable message id as key - don't include length to avoid resetting
                                AnimatedTypingText(
                                    key = message.id,
                                    fullText = message.content,
                                    textColor = if (isUser) {
                                        Color.White
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                Spacer(modifier = Modifier.width(2.dp))
                                TypingCursor(
                                    color = if (isUser) {
                                        Color.White
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            } else {
                                // Show full text when not streaming
                                Text(
                                    text = parseMarkdown(message.content),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isUser) {
                                        // Always use white on user message bubbles for maximum contrast
                                        Color.White
                                    } else {
                                        // Use onSurface for better contrast on surfaceVariant
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                        
                        // Show response metadata (time, tokens, cost) for assistant messages
                        if (!isUser && !isStreaming && (message.responseTimeMs != null || message.promptTokens != null || message.completionTokens != null || message.totalCost != null)) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                message.responseTimeMs?.let { timeMs ->
                                    Text(
                                        text = "${timeMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                if (message.promptTokens != null || message.completionTokens != null) {
                                    val totalTokens = (message.promptTokens ?: 0) + (message.completionTokens ?: 0)
                                    Text(
                                        text = "${totalTokens} токенов",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (message.promptTokens != null && message.completionTokens != null) {
                                        Text(
                                            text = "(${message.promptTokens}+${message.completionTokens})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                
                                message.totalCost?.let { cost ->
                                    Text(
                                        text = String.format("$%.6f", cost),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        
                        // Show JSON button if raw JSON is available
                        if (!isUser && !isStreaming && message.rawJson != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { showJsonDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = "Показать JSON",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Показать JSON",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            
            // Show suggestion buttons for assistant messages (not streaming)
            if (!isUser && !isStreaming && message.suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = if (isUser) 0.dp else 16.dp, end = if (isUser) 16.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Each suggestion on a separate line
                    message.suggestions.forEach { suggestion ->
                        SuggestionButton(
                            text = suggestion,
                            onClick = { onSuggestionClick(suggestion) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
    
    // JSON Dialog
    if (showJsonDialog && message.rawJson != null) {
        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Raw JSON")
                    IconButton(
                        onClick = { showJsonDialog = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            text = {
                Column {
                    Text(
                        text = message.rawJson,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showJsonDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
fun SuggestionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
fun ErrorIndicator(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp)),
            color = MaterialTheme.colorScheme.errorContainer,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Повторить",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    onFocusChange: ((Boolean) -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Debug logging
    LaunchedEffect(enabled) {
        android.util.Log.d("MessageInput", "enabled changed: $enabled")
    }
    
    LaunchedEffect(isFocused) {
        android.util.Log.d("MessageInput", "isFocused changed: $isFocused, enabled: $enabled")
        // Notify parent about focus change
        onFocusChange?.invoke(isFocused)
        
        if (isFocused && enabled) {
            android.util.Log.d("MessageInput", "Field is focused and enabled, showing keyboard after delay")
            // Small delay to ensure focus is fully established
            kotlinx.coroutines.delay(100)
            android.util.Log.d("MessageInput", "Calling keyboardController.show(), controller: $keyboardController")
            keyboardController?.show()
            android.util.Log.d("MessageInput", "keyboardController.show() called")
        } else if (!isFocused) {
            android.util.Log.d("MessageInput", "Field lost focus")
        }
    }
    
    val isDarkTheme = isSystemInDarkTheme()
    
    // Use Material Design colors for borders, but ensure visibility
    val unfocusedBorderColor = MaterialTheme.colorScheme.outline
    // Use a darker, more visible color for focused state
    val focusedBorderColor = if (isDarkTheme) {
        Color(0xFF90CAF9) // Light blue for dark theme
    } else {
        Color(0xFF1976D2) // Material Blue 700 - clearly visible on light background
    }
    
    // Cursor color - use a dark, contrasting color that stands out
    val cursorColor = if (isDarkTheme) {
        Color(0xFF90CAF9) // Light blue for dark theme
    } else {
        Color(0xFF1976D2) // Material Blue 700 - clearly visible and distinct from placeholder
    }
    
    // Background color for text input to ensure text selection handles are visible
    // Use a darker background so that light selection handles (which use primary color) are visible
    // The theme's primary color is light (0xFFF5F5F5), so we need a darker background
    val inputBackgroundColor = if (isDarkTheme) {
        Color(0xFF2D2D2D) // Darker background for dark theme - contrasts with light selection handles
    } else {
        Color(0xFFE0E0E0) // Darker gray background for light theme - contrasts with light selection handles
    }
    
    // Text selection colors - ensure contrast with background
    val textSelectionColors = remember {
        TextSelectionColors(
            handleColor = if (isDarkTheme) {
                Color(0xFF90CAF9) // Light blue handles for dark theme - visible on dark background
            } else {
                Color(0xFF1976D2) // Dark blue handles for light theme - visible on light background
            },
            backgroundColor = if (isDarkTheme) {
                Color(0xFF1976D2).copy(alpha = 0.4f) // Blue selection background for dark theme
            } else {
                Color(0xFF1976D2).copy(alpha = 0.3f) // Blue selection background for light theme
            }
        )
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp) // Match OutlinedTextField default height
                .then(
                    // Only handle taps on the outer surface when field is not focused
                    // When focused, let BasicTextField handle taps for cursor positioning
                    if (enabled && !isFocused) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    android.util.Log.d("MessageInput", "Tap detected on outer Surface, requesting focus")
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            shape = RoundedCornerShape(24.dp),
            color = inputBackgroundColor,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (isFocused) {
                    focusedBorderColor
                } else {
                    unfocusedBorderColor
                }
            ),
            tonalElevation = 0.dp
        ) {
            CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
                BasicTextField(
                value = text,
                onValueChange = { newText ->
                    android.util.Log.d("MessageInput", "Text changed: ${newText.length} chars")
                    onTextChange(newText)
                },
                enabled = enabled,
                readOnly = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(horizontal = 16.dp, vertical = 16.dp), // Increased padding to match OutlinedTextField
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                ),
                cursorBrush = SolidColor(cursorColor), // Ensure cursor is visible
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp) // Additional vertical padding for text alignment
                            .then(
                                // Only handle taps on placeholder area (when text is empty) to request focus
                                // When text exists, let BasicTextField handle taps for cursor positioning
                                if (enabled && text.isEmpty()) {
                                    Modifier.pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = {
                                                android.util.Log.d("MessageInput", "Tap detected on placeholder area")
                                                focusRequester.requestFocus()
                                                keyboardController?.show()
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = "Напишите сообщение...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) // Lighter placeholder to distinguish from cursor
                            )
                        }
                        innerTextField()
                    }
                }
            )
            }
        }
        
        FloatingActionButton(
            onClick = onSend,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Отправить"
            )
        }
    }
}

@Composable
fun LoadingIndicator() {
    android.util.Log.d("LoadingIndicator", "LoadingIndicator composable called - rendering animation")
    
    // Animated thinking dots with pulsing effect
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1Scale"
    )
    
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1Alpha"
    )
    
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2Scale"
    )
    
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2Alpha"
    )
    
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3Scale"
    )
    
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3Alpha"
    )
    
    // Match MessageBubble layout exactly - Row with Start arrangement, no extra padding
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Думаю",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
                // Animated thinking dots with pulsing effect
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dot 1
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .graphicsLayer {
                                scaleX = dot1Scale
                                scaleY = dot1Scale
                                alpha = dot1Alpha
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(12.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {}
                    }
                    // Dot 2
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .graphicsLayer {
                                scaleX = dot2Scale
                                scaleY = dot2Scale
                                alpha = dot2Alpha
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(12.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {}
                    }
                    // Dot 3
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .graphicsLayer {
                                scaleX = dot3Scale
                                scaleY = dot3Scale
                                alpha = dot3Alpha
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(12.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha3"
    )
    
    Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .alpha(alpha1),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {}
            }
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .alpha(alpha2),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary
            ) {}
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .alpha(alpha3),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primary
            ) {}
        }
    }
}

@Composable
fun TypingCursor(
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )
    
    Box(
        modifier = Modifier
            .width(2.dp)
            .height(16.dp)
            .alpha(alpha)
            .background(color)
    )
}

@Composable
fun AnimatedTypingText(
    key: String,
    fullText: String,
    textColor: Color,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    // Reset displayedText when key changes (new message starts)
    var displayedText by remember(key) { mutableStateOf("") }
    
    // Store fullText in a mutable state that can be observed
    val fullTextState = remember(key) { mutableStateOf(fullText) }
    
    // Update fullTextState whenever fullText changes (runs during composition)
    SideEffect {
        val oldLength = fullTextState.value.length
        fullTextState.value = fullText
        if (fullText.length != oldLength) {
            android.util.Log.d("AnimatedTypingText", "fullTextState updated - key: $key, old length: $oldLength, new length: ${fullText.length}, displayedText length: ${displayedText.length}")
        }
    }
    
    // Continuous animation loop that observes fullTextState changes via snapshotFlow
    LaunchedEffect(key) {
        android.util.Log.d("AnimatedTypingText", "Starting animation loop for key: $key, initial fullText length: ${fullTextState.value.length}")
        
        // Use snapshotFlow to observe fullTextState changes
        snapshotFlow { fullTextState.value }
            .collect { targetText ->
                android.util.Log.d("AnimatedTypingText", "snapshotFlow emitted - targetText length: ${targetText.length}, displayedText length: ${displayedText.length}")
                
                // Animate from current displayed position to target length
                while (displayedText.length < targetText.length) {
                    val nextChar = targetText[displayedText.length]
                    displayedText += nextChar
                    
                    android.util.Log.v("AnimatedTypingText", "Displayed char: '$nextChar', displayedText length now: ${displayedText.length}, target length: ${targetText.length}")
                    
                    // Variable delay based on character type for more natural typing
                    val delay = when {
                        nextChar == ' ' -> 50L // Faster for spaces
                        nextChar == '\n' -> 100L // Slightly longer for newlines
                        nextChar.isLetterOrDigit() -> 30L // Normal speed for letters/numbers
                        else -> 40L // Slightly longer for punctuation
                    }
                    
                    kotlinx.coroutines.delay(delay)
                    
                    // Check if targetText has changed (new chunk arrived) - if so, break and let snapshotFlow handle it
                    val currentTarget = fullTextState.value
                    if (currentTarget.length > targetText.length) {
                        android.util.Log.d("AnimatedTypingText", "Target text changed during animation (new chunk), breaking to handle new target")
                        break
                    }
                }
            }
    }
    
    // If fullText becomes shorter (shouldn't happen, but handle it)
    if (fullText.length < displayedText.length) {
        displayedText = fullText
    }
    
    // Show text (even if empty, so cursor is visible)
    Text(
        text = parseMarkdown(displayedText),
        style = style,
        color = textColor,
        modifier = modifier
    )
}

