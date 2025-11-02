package xyz.sandboiii.agentcooper.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import xyz.sandboiii.agentcooper.presentation.chat.ChatScreen
import xyz.sandboiii.agentcooper.presentation.model_selection.ModelSelectionScreen
import xyz.sandboiii.agentcooper.presentation.sessions.SessionsScreen

sealed class Screen(val route: String) {
    data object Sessions : Screen("sessions")
    data class Chat(val sessionId: String, val modelId: String) : Screen("chat/{sessionId}/{modelId}") {
        companion object {
            fun createRoute(sessionId: String, modelId: String) = "chat/$sessionId/$modelId"
        }
    }
    data object ModelSelection : Screen("model_selection")
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
                    navController.navigate("chat/$sessionId/$modelId")
                },
                onNavigateToModelSelection = {
                    navController.navigate(Screen.ModelSelection.route)
                }
            )
        }
        
        composable("chat/{sessionId}/{modelId}") { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            val modelId = backStackEntry.arguments?.getString("modelId") ?: return@composable
            
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
    }
}

