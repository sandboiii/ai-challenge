package xyz.sandboiii.agentcooper.presentation.logical_problem

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import xyz.sandboiii.agentcooper.util.Constants

@Composable
fun LogicalProblemScreen(
    onNavigateBack: () -> Unit = {},
    onChatClick: (String, String) -> Unit = { _, _ -> },
    logicalProblemViewModel: LogicalProblemViewModel = hiltViewModel()
) {
    // Ensure sessions exist on first load and get model ID
    var modelId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        logicalProblemViewModel.ensureSessionsExist()
        try {
            modelId = logicalProblemViewModel.getModelId()
        } catch (e: Exception) {
            android.util.Log.e("LogicalProblemScreen", "Failed to get model ID", e)
        }
    }
    
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Логические задачи") },
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
        if (modelId == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "4 подхода к решению логических задач",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Button 1: Direct Solution
                LogicalProblemButton(
                    title = "1. Прямое решение",
                    description = "Решает задачу напрямую, без лишних объяснений",
                    onClick = {
                        onChatClick(Constants.LOGICAL_CHAT_DIRECT_ID, modelId!!)
                    }
                )
                
                // Button 2: Step by Step
                LogicalProblemButton(
                    title = "2. Пошаговое решение",
                    description = "Детально разбирает каждый шаг решения",
                    onClick = {
                        onChatClick(Constants.LOGICAL_CHAT_STEP_BY_STEP_ID, modelId!!)
                    }
                )
                
                // Button 3: Prompt Writer
                LogicalProblemButton(
                    title = "3. Создатель промптов",
                    description = "Создает промпты для других LLM",
                    onClick = {
                        onChatClick(Constants.LOGICAL_CHAT_PROMPT_WRITER_ID, modelId!!)
                    }
                )
                
                // Button 4: Experts Team
                LogicalProblemButton(
                    title = "4. Команда экспертов",
                    description = "Группа экспертов с разными специализациями",
                    onClick = {
                        onChatClick(Constants.LOGICAL_CHAT_EXPERTS_ID, modelId!!)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogicalProblemButton(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

