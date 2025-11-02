package xyz.sandboiii.agentcooper.presentation.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    
    val state by viewModel.state.collectAsStateWithLifecycle()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // Scroll to bottom when new messages arrive
    LaunchedEffect(state) {
        if (state is ChatState.Success) {
            val messages = (state as ChatState.Success).messages
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
    Scaffold(
        topBar = {
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
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(currentState.messages) { message ->
                        MessageBubble(
                            message = message,
                            isStreaming = currentState.isStreaming && 
                                    message.id == "streaming"
                        )
                    }
                    
                    // Typing indicator
                    if (currentState.isStreaming && currentState.messages.none { it.id == "streaming" }) {
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
                    enabled = !currentState.isStreaming
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: xyz.sandboiii.agentcooper.domain.model.ChatMessage,
    isStreaming: Boolean
) {
    val isUser = message.role == MessageRole.USER
    
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(16.dp)),
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                tonalElevation = 2.dp
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            placeholder = { Text("Напишите сообщение...") },
            shape = RoundedCornerShape(24.dp),
            singleLine = false,
            maxLines = 4
        )
        
        FloatingActionButton(
            onClick = onSend,
            modifier = Modifier.size(56.dp),
            enabled = enabled && text.isNotBlank()
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Отправить"
            )
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

