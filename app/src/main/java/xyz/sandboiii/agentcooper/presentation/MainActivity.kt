package xyz.sandboiii.agentcooper.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import xyz.sandboiii.agentcooper.presentation.navigation.NavGraph
import xyz.sandboiii.agentcooper.presentation.navigation.Screen
import xyz.sandboiii.agentcooper.presentation.theme.AgentCooperTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgentCooperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    // Handle navigation from notification
                    LaunchedEffect(Unit) {
                        val sessionId = intent.getStringExtra("session_id")
                        val modelId = intent.getStringExtra("model_id")
                        val navigateToChat = intent.getBooleanExtra("navigate_to_chat", false)
                        
                        if (navigateToChat && sessionId != null && modelId != null) {
                            navController.navigate(Screen.Chat.createRoute(sessionId, modelId)) {
                                popUpTo(Screen.Sessions.route) { inclusive = false }
                            }
                        }
                    }
                    
                    NavGraph(
                        navController = navController,
                        startDestination = Screen.Sessions.route
                    )
                }
            }
        }
    }
}

