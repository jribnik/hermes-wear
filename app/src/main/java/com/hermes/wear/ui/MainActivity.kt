package com.hermes.wear.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.hermes.wear.ui.screens.ApprovalScreen
import com.hermes.wear.ui.screens.ConversationScreen
import com.hermes.wear.ui.screens.SettingsScreen
import com.hermes.wear.ui.theme.WearHermesColors
import kotlinx.coroutines.launch
import androidx.wear.compose.material.MaterialTheme

/**
 * Main entry point for the Hermes Wear app.
 * Handles navigation between conversation, approval, and settings screens.
 * Integrates voice input via Android's Speech Recognizer.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: HermesViewModel by viewModels()

    // Voice input launcher
    private val voiceInputLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.sendMessage(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Also allow starting from system (e.g., for voice "Hey Google, talk to Hermes")
        handleIntent(intent)

        setContent {
            MaterialTheme(colors = WearHermesColors) {
                HermesWearApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        // Handle pre-filled text from complication or notification taps
        val messageText = intent.getStringExtra("EXTRA_MESSAGE_TEXT")
        if (!messageText.isNullOrBlank()) {
            lifecycleScope.launch {
                viewModel.sendMessage(messageText)
            }
            intent.removeExtra("EXTRA_MESSAGE_TEXT")
        }
    }

    @Composable
    private fun HermesWearApp() {
        val navController = rememberSwipeDismissableNavController()
        val currentApproval by viewModel.currentApproval.collectAsState()

        // If there's a pending approval, show the approval screen
        val approval = currentApproval
        if (approval != null) {
            ApprovalScreen(
                approval = approval,
                onApprove = {
                    viewModel.approveCurrentRequest()
                },
                onDeny = {
                    viewModel.denyCurrentRequest()
                },
                onDismiss = {
                    viewModel.denyCurrentRequest()
                }
            )
            return
        }

        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "conversation"
        ) {
            composable("conversation") {
                ConversationScreen(
                    viewModel = viewModel,
                    onStartVoiceInput = { launchVoiceInput() },
                    onOpenSettings = {
                        navController.navigate("settings")
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    private fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Hermes...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        voiceInputLauncher.launch(intent)
    }
}
