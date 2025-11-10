package xyz.sandboiii.agentcooper.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.ExperimentalComposeUiApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = "")
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = "")
    val suggestionsEnabled by viewModel.suggestionsEnabled.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    val welcomeMessageEnabled by viewModel.welcomeMessageEnabled.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = true)
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    val saveSystemPromptSuccess by viewModel.saveSystemPromptSuccess.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = null)
    val isDeletingSessions by viewModel.isDeletingSessions.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    val deleteSessionsSuccess by viewModel.deleteSessionsSuccess.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    // Initialize local state only once, sync when needed
    var localApiKey by remember { mutableStateOf("") }
    var localSystemPrompt by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Check if dark theme is active - check if onSurface is light (white-ish)
    val isDarkTheme = MaterialTheme.colorScheme.onSurface.red > 0.9f &&
            MaterialTheme.colorScheme.onSurface.green > 0.9f &&
            MaterialTheme.colorScheme.onSurface.blue > 0.9f
    
    // Text selection colors - ensure contrast with surface
    val textSelectionColors = remember(isDarkTheme) {
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
    
    // Sync local state with ViewModel state only when ViewModel state changes (not on every recomposition)
    LaunchedEffect(apiKey) {
        if (localApiKey != apiKey) {
            localApiKey = apiKey
        }
    }
    
    LaunchedEffect(systemPrompt) {
        if (localSystemPrompt != systemPrompt) {
            localSystemPrompt = systemPrompt
        }
    }
    
    // Optimize success message clearing - only trigger when value becomes true
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSaveSuccess()
        }
    }
    
    LaunchedEffect(saveSystemPromptSuccess) {
        if (saveSystemPromptSuccess) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSaveSystemPromptSuccess()
        }
    }
    
    LaunchedEffect(deleteSessionsSuccess) {
        if (deleteSessionsSuccess) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearDeleteSessionsSuccess()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
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
        val scrollState = rememberScrollState()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Key Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "API Ключ OpenRouter",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "Введите ваш API ключ от OpenRouter. Вы можете получить его на https://openrouter.ai/keys",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
                        OutlinedTextField(
                            value = localApiKey,
                            onValueChange = { 
                                localApiKey = it
                                // Don't update ViewModel on every keystroke to reduce lag
                            },
                            label = { Text("API Ключ") },
                            placeholder = { Text("sk-or-v1-...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isLoading,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = if (passwordVisible) "Скрыть" else "Показать"
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    viewModel.saveApiKey()
                                }
                            ),
                            isError = errorMessage != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    if (saveSuccess) {
                        Text(
                            text = "API ключ успешно сохранён",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            // Update ViewModel right before saving
                            viewModel.updateApiKey(localApiKey)
                            viewModel.saveApiKey()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && localApiKey.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6200EE), // Use a darker purple for better contrast
                            contentColor = Color.White, // Use white text for maximum contrast
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White
                            )
                        } else {
                            Text(
                                "Сохранить",
                                color = Color.White
                            )
                        }
                    }
                }
            }
            
            // System Prompt Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Системный промпт",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "Этот промпт определяет поведение и стиль ответов AI модели в чате.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
                        OutlinedTextField(
                            value = localSystemPrompt,
                            onValueChange = { 
                                localSystemPrompt = it
                                // Don't update ViewModel on every keystroke to reduce lag
                            },
                            label = { Text("Системный промпт") },
                            placeholder = { Text("Введите системный промпт...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 300.dp),
                            minLines = 4,
                            maxLines = 10,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    viewModel.saveSystemPrompt()
                                }
                            ),
                            isError = errorMessage != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                    
                    if (saveSystemPromptSuccess) {
                        Text(
                            text = "Системный промпт успешно сохранён",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            // Update ViewModel right before saving
                            viewModel.updateSystemPrompt(localSystemPrompt)
                            viewModel.saveSystemPrompt()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && localSystemPrompt.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6200EE),
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White
                            )
                        } else {
                            Text(
                                "Сохранить промпт",
                                color = Color.White
                            )
                        }
                    }
                }
            }
            
            // Suggestions Toggle Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Предложения ответов",
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            Text(
                                text = "Включить автоматические предложения ответов на сообщения AI",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = suggestionsEnabled,
                            onCheckedChange = { viewModel.updateSuggestionsEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6200EE), // Use the same purple as buttons
                                uncheckedThumbColor = Color(0xFF757575), // Dark gray for visibility
                                uncheckedTrackColor = Color(0xFFE0E0E0) // Light gray with contrast
                            )
                        )
                    }
                }
            }
            
            // Welcome Message Toggle Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Приветственное сообщение",
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            Text(
                                text = "Показывать приветственное сообщение в каждом новом чате",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = welcomeMessageEnabled,
                            onCheckedChange = { viewModel.updateWelcomeMessageEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6200EE),
                                uncheckedThumbColor = Color(0xFF757575),
                                uncheckedTrackColor = Color(0xFFE0E0E0)
                            )
                        )
                    }
                }
            }
            
            // Delete All Sessions Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Управление данными",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "Удалить все сохранённые сессии чата. Это действие нельзя отменить.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (deleteSessionsSuccess) {
                        Text(
                            text = "Все сессии успешно удалены",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Button(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDeletingSessions,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        if (isDeletingSessions) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Удалить все сессии",
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Удалить все сессии?") },
            text = { Text("Это действие удалит все сохранённые сессии чата и не может быть отменено.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deleteAllSessions()
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

