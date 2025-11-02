package xyz.sandboiii.agentcooper.presentation.sessions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SessionsScreen(
    onSessionClick: (String, String) -> Unit,
    onNavigateToModelSelection: () -> Unit = {},
    viewModel: SessionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сессии") },
                actions = {
                    IconButton(onClick = onNavigateToModelSelection) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Выбрать модель"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.createSession() }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Новая сессия"
                )
            }
        }
    ) { padding ->
        when (val currentState = state) {
            is SessionsState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            
            is SessionsState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
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
                        Button(onClick = { viewModel.loadSessions() }) {
                            Text("Повторить")
                        }
                    }
                }
            }
            
            is SessionsState.Success -> {
                if (currentState.sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Нет сессий",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "Создайте новую сессию для начала разговора",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = currentState.sessions,
                            key = { it.id }
                        ) { session ->
                            SessionItem(
                                session = session,
                                onClick = { onSessionClick(session.id, session.modelId) },
                                onDelete = { viewModel.deleteSession(session.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionItem(
    session: xyz.sandboiii.agentcooper.domain.model.ChatSession,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(Date(session.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

