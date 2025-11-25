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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import xyz.sandboiii.agentcooper.util.Constants

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = "")
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = "")
    val welcomeMessageEnabled by viewModel.welcomeMessageEnabled.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = true)
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    val saveSystemPromptSuccess by viewModel.saveSystemPromptSuccess.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = null)
    val isDeletingSessions by viewModel.isDeletingSessions.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    val deleteSessionsSuccess by viewModel.deleteSessionsSuccess.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    val tokenThreshold by viewModel.tokenThreshold.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = "")
    val saveTokenThresholdSuccess by viewModel.saveTokenThresholdSuccess.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    val storageLocation by viewModel.storageLocation.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = null)
    val isMigrating by viewModel.isMigrating.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    val migrationSuccess by viewModel.migrationSuccess.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    val temperature by viewModel.temperature.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = Constants.DEFAULT_TEMPERATURE)
    val ragEnabled by viewModel.ragEnabled.collectAsStateWithLifecycle(lifecycle = lifecycle, initialValue = false)
    
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    
    // Launcher for selecting folder
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Grant persistent permission
            context.contentResolver.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setStorageLocation(it.toString())
        }
    }
    
    // Initialize local state only once, sync when needed
    var localApiKey by remember { mutableStateOf("") }
    var localSystemPrompt by remember { mutableStateOf("") }
    var localTokenThreshold by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Check if dark theme is active - check if onSurface is light (white-ish)
    val isDarkTheme = MaterialTheme.colorScheme.onSurface.red > 0.9f &&
            MaterialTheme.colorScheme.onSurface.green > 0.9f &&
            MaterialTheme.colorScheme.onSurface.blue > 0.9f
    
    // Text selection colors - use primary color
    val colorScheme = MaterialTheme.colorScheme
    val textSelectionColors = remember(colorScheme) {
        TextSelectionColors(
            handleColor = colorScheme.primary,
            backgroundColor = colorScheme.primary.copy(alpha = 0.3f)
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
    
    LaunchedEffect(tokenThreshold) {
        if (localTokenThreshold != tokenThreshold) {
            localTokenThreshold = tokenThreshold
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
    
    LaunchedEffect(saveTokenThresholdSuccess) {
        if (saveTokenThresholdSuccess) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSaveTokenThresholdSuccess()
        }
    }
    
    LaunchedEffect(migrationSuccess) {
        if (migrationSuccess) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearMigrationSuccess()
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
                            color = MaterialTheme.colorScheme.onSurface
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
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                "Сохранить",
                                color = MaterialTheme.colorScheme.onPrimary
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
                        )
                    }
                    
                    if (saveSystemPromptSuccess) {
                        Text(
                            text = "Системный промпт успешно сохранён",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
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
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                "Сохранить промпт",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
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
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
            
            // RAG Context Augmentation Toggle Section
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
                                text = "Enable RAG Context Augmentation (Local Server)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            Text(
                                text = "Augment queries with relevant context from local RAG server at http://10.0.2.2:8000",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = ragEnabled,
                            onCheckedChange = { viewModel.updateRagEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
            
            // Temperature Section
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Температура AI",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format("%.1f", temperature),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${Constants.MIN_TEMPERATURE.toInt()}-${Constants.MAX_TEMPERATURE.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Slider(
                        value = temperature,
                        onValueChange = { viewModel.updateTemperature(it) },
                        valueRange = Constants.MIN_TEMPERATURE..Constants.MAX_TEMPERATURE,
                        steps = 19, // 0.0 to 2.0 with 0.1 increments = 20 steps - 1
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.onSurface,
                            activeTrackColor = MaterialTheme.colorScheme.onSurface,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
            
            // Token Threshold Section
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
                        text = "Порог токенов для суммирования",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = "Когда количество токенов в истории чата превышает этот порог, старые сообщения будут автоматически суммированы для экономии контекста. По умолчанию используется размер контекстного окна модели.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
                        OutlinedTextField(
                            value = localTokenThreshold,
                            onValueChange = { 
                                localTokenThreshold = it
                            },
                            label = { Text("Порог токенов") },
                            placeholder = { Text("Например: 8000") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    viewModel.saveTokenThreshold()
                                }
                            ),
                            isError = errorMessage != null,
                        )
                    }
                    
                    if (saveTokenThresholdSuccess) {
                        Text(
                            text = "Порог токенов успешно сохранён",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.updateTokenThreshold(localTokenThreshold)
                            viewModel.saveTokenThreshold()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && localTokenThreshold.isNotBlank(),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                "Сохранить порог",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
            
            // Storage Location Section
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
                        text = "Локация хранения файлов",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = if (storageLocation == null) {
                            "Файлы сессий хранятся во внутреннем хранилище приложения"
                        } else {
                            "Файлы сессий хранятся во внешней папке"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (storageLocation != null) {
                        Text(
                            text = "Путь: ${storageLocation}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    
                    if (migrationSuccess) {
                        Text(
                            text = "Файлы успешно мигрированы",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                folderPickerLauncher.launch(null)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isMigrating && !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            if (isMigrating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    "Выбрать папку",
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        
                        if (storageLocation != null) {
                            Button(
                                onClick = {
                                    viewModel.resetToInternalStorage()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isMigrating && !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(
                                    "Внутреннее",
                                    color = MaterialTheme.colorScheme.onSecondary
                                )
                            }
                        }
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
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        if (isDeletingSessions) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onError
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
                                color = MaterialTheme.colorScheme.onError
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

