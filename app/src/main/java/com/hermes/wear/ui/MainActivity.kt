package com.hermes.wear.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
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

    // Notification permission launcher (Android 13+ / Wear OS 4+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Notification permission needed for background alerts", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()
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

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleIntent(intent: Intent) {
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
                    // Just dismiss the overlay — do NOT deny.
                    // Approval remains pending server-side but the UI goes away.
                    viewModel.dismissCurrentRequest()
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
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Hermes...")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            voiceInputLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice input not available on this device", Toast.LENGTH_SHORT).show()
        }
    }
}
