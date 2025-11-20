package xyz.sandboiii.agentcooper.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import xyz.sandboiii.agentcooper.presentation.chat.ChatScreen
import xyz.sandboiii.agentcooper.presentation.mcp.McpManagerScreen
import xyz.sandboiii.agentcooper.presentation.model_selection.ModelSelectionScreen
import xyz.sandboiii.agentcooper.presentation.scheduled_tasks.ScheduledTasksScreen
import xyz.sandboiii.agentcooper.presentation.sessions.SessionsScreen
import xyz.sandboiii.agentcooper.presentation.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Sessions : Screen("sessions")
    data class Chat(val sessionId: String, val modelId: String) : Screen("chat/{sessionId}/{modelId}") {
        companion object {
            fun createRoute(sessionId: String, modelId: String): String {
                val encodedSessionId = URLEncoder.encode(sessionId, StandardCharsets.UTF_8.toString())
                val encodedModelId = URLEncoder.encode(modelId, StandardCharsets.UTF_8.toString())
                return "chat/$encodedSessionId/$encodedModelId"
            }
        }
    }
    data object ModelSelection : Screen("model_selection")
    data object Settings : Screen("settings")
    data object McpManager : Screen("mcp_manager")
    data object ScheduledTasks : Screen("scheduled_tasks")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Sessions.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Sessions.route) {
            SessionsScreen(
                onSessionClick = { sessionId, modelId ->
                    navController.navigate(Screen.Chat.createRoute(sessionId, modelId))
                },
                onNavigateToModelSelection = {
                    navController.navigate(Screen.ModelSelection.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToScheduledTasks = {
                    navController.navigate(Screen.ScheduledTasks.route)
                },
                onNavigateToMcpManager = {
                    navController.navigate(Screen.McpManager.route)
                }
            )
        }
        
        composable(
            route = "chat/{sessionId}/{modelId}",
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("modelId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedSessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            val encodedModelId = backStackEntry.arguments?.getString("modelId") ?: return@composable
            
            // Decode URL-encoded parameters
            val sessionId = URLDecoder.decode(encodedSessionId, StandardCharsets.UTF_8.toString())
            val modelId = URLDecoder.decode(encodedModelId, StandardCharsets.UTF_8.toString())
            
            ChatScreen(
                sessionId = sessionId,
                modelId = modelId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.ModelSelection.route) {
            ModelSelectionScreen(
                onModelSelected = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.McpManager.route) {
            McpManagerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.ScheduledTasks.route) {
            ScheduledTasksScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
