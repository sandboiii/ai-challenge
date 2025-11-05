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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
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
import xyz.sandboiii.agentcooper.domain.model.MessageRole

@Composable
fun ChatScreen(
    sessionId: String,
    modelId: String,
    onNavigateBack: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    LaunchedEffect(sessionId, modelId) {
        viewModel.initialize(sessionId, modelId)
    }
    
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by viewModel.state.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = ChatState.Loading)
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // Scroll to bottom when new messages arrive
    LaunchedEffect(state) {
        if (state is ChatState.Success) {
            val messages = (state as ChatState.Success).messages
            if (messages.isNotEmpty()) {
                kotlinx.coroutines.delay(100) // Small delay to ensure layout is ready
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
    // Scroll to bottom when keyboard appears or message text changes
    LaunchedEffect(messageText) {
        if (messageText.isNotEmpty()) {
            val currentState = state
            if (currentState is ChatState.Success) {
                val messages = currentState.messages
                if (messages.isNotEmpty()) {
                    kotlinx.coroutines.delay(100)
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Agent Cooper") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
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
                        // Show all messages (user and assistant) - welcome message is now in the database
                        items(currentState.messages) { message ->
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
                        
                        // Loading indicator when waiting for response or when SSE stream started but content is empty
                        // Show thinking animation if:
                        // 1. We're waiting for response, OR
                        // 2. We're streaming but haven't received any content yet (SSE stream just started)
                        val shouldShowThinking = currentState.isWaitingForResponse || 
                            (currentState.isStreaming && currentState.streamingContent.isEmpty())
                        
                        // Debug logging
                        android.util.Log.d("ChatScreen", "shouldShowThinking: $shouldShowThinking")
                        android.util.Log.d("ChatScreen", "isWaitingForResponse: ${currentState.isWaitingForResponse}")
                        android.util.Log.d("ChatScreen", "isStreaming: ${currentState.isStreaming}")
                        android.util.Log.d("ChatScreen", "streamingContent.isEmpty(): ${currentState.streamingContent.isEmpty()}")
                        android.util.Log.d("ChatScreen", "messages count: ${currentState.messages.size}")
                        
                        if (shouldShowThinking) {
                            item {
                                LoadingIndicator()
                            }
                        }
                        
                        // Typing indicator when streaming (after first chunk received but message not yet in list)
                        // This should rarely happen, but keep it as fallback
                        if (currentState.isStreaming && 
                            currentState.messages.none { it.id == "streaming" } && 
                            !shouldShowThinking &&
                            currentState.streamingContent.isNotEmpty()) {
                            item {
                                TypingIndicator()
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
                        }
                    )
                }
            }
        }
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
                        
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) {
                                // Always use white on user message bubbles for maximum contrast
                                Color.White
                            } else {
                                // Use onSurface for better contrast on surfaceVariant
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        
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
    enabled: Boolean
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
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp) // Match OutlinedTextField default height
                .pointerInput(enabled) {
                    if (enabled) {
                        detectTapGestures(
                            onTap = {
                                android.util.Log.d("MessageInput", "Tap detected on outer Box, enabled: $enabled")
                                android.util.Log.d("MessageInput", "Requesting focus...")
                                focusRequester.requestFocus()
                                android.util.Log.d("MessageInput", "Focus requested, current isFocused state: $isFocused")
                                android.util.Log.d("MessageInput", "Calling keyboardController.show(), controller: $keyboardController")
                                keyboardController?.show()
                                android.util.Log.d("MessageInput", "keyboardController.show() called")
                            }
                        )
                    } else {
                        android.util.Log.d("MessageInput", "Tap detected but field is disabled")
                    }
                }
                .border(
                    width = 1.dp, // Standard Material Design border width
                    color = if (isFocused) {
                        focusedBorderColor
                    } else {
                        unfocusedBorderColor
                    },
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
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
                    .padding(horizontal = 16.dp, vertical = 16.dp) // Increased padding to match OutlinedTextField
                    .then(
                        if (enabled) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        android.util.Log.d("MessageInput", "Tap detected directly on BasicTextField")
                                        android.util.Log.d("MessageInput", "Requesting focus and showing keyboard")
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    ),
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
                            .pointerInput(enabled) {
                                if (enabled) {
                                    detectTapGestures(
                                        onTap = {
                                            android.util.Log.d("MessageInput", "Tap detected on decorationBox (placeholder area), enabled: $enabled")
                                            android.util.Log.d("MessageInput", "Requesting focus...")
                                            focusRequester.requestFocus()
                                            android.util.Log.d("MessageInput", "Focus requested, calling keyboardController.show(), controller: $keyboardController")
                                            keyboardController?.show()
                                            android.util.Log.d("MessageInput", "keyboardController.show() called from decorationBox")
                                        }
                                    )
                                } else {
                                    android.util.Log.d("MessageInput", "Tap detected on decorationBox but field is disabled")
                                }
                            }
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

