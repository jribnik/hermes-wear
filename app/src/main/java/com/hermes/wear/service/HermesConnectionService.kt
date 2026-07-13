package com.hermes.wear.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hermes.wear.HermesWearApp
import com.hermes.wear.data.model.*
import com.hermes.wear.data.repository.HermesRepository
import com.hermes.wear.data.repository.PreferenceHelper
import kotlinx.coroutines.*

/**
 * Foreground service that owns the WebSocket connection to the Hermes Gateway.
 *
 * Architecture: this service is the sole consumer of HermesApiClient.observeMessages().
 * It relays payloads to a HermesRepository SharedFlow for UI consumption, and independently
 * posts Android notifications for background delivery.
 */
class HermesConnectionService : LifecycleService() {

    private lateinit var preferenceHelper: PreferenceHelper
    private val apiClient = HermesWearApp.instance.apiClient
    private val repository = HermesRepository(apiClient)

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "hermes_connection"
        const val CHANNEL_NAME = "Hermes Connection"
        const val NOTIFICATION_ID = 1001
        const val MESSAGE_CHANNEL_ID = "hermes_messages"
        const val APPROVAL_CHANNEL_ID = "hermes_approvals"
        const val ACTION_START = "com.hermes.wear.action.START_CONNECTION"
        const val ACTION_STOP = "com.hermes.wear.action.STOP_CONNECTION"

        fun start(context: Context) {
            val intent = Intent(context, HermesConnectionService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HermesConnectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferenceHelper = PreferenceHelper(this)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startConnection()
            ACTION_STOP -> stopConnection()
        }
        return START_STICKY
    }

    private fun startConnection() {
        val notification = buildServiceNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HermesWear:ConnectionWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L)
        }

        // Start relaying WebSocket messages to the repository for UI
        repository.startObserving()

        lifecycleScope.launch {
            apiClient.connectWebSocket(
                onOpen = {
                    updateNotification("Connected to Hermes")
                },
                onClosed = { code, reason ->
                    updateNotification("Connection closed")
                    stopSelf()
                },
                onFailure = { error ->
                    updateNotification("Connection error: ${error.message}")
                    // Disconnect (soft — keeps channel alive) and try long-poll
                    apiClient.disconnect()
                    wakeLock?.let { if (it.isHeld) it.release() }
                    repository.startLongPollingFallback()
                    // Retry WebSocket after delay
                    lifecycleScope.launch {
                        delay(10000)
                        startConnection()
                    }
                }
            )

            // Process incoming messages — sole consumer of the channel
            val messageChannel = apiClient.observeMessages()
            for (payload in messageChannel) {
                when (payload.type) {
                    PayloadType.MESSAGE -> {
                        payload.message?.let { msg ->
                            if (msg.sender == Sender.HERMES) {
                                showMessageNotification(msg)
                            }
                        }
                    }
                    PayloadType.APPROVAL -> {
                        payload.approval?.let { approval ->
                            showApprovalNotification(approval)
                        }
                    }
                    PayloadType.HEARTBEAT -> {
                        updateNotification("Connected to Hermes")
                    }
                    PayloadType.STATUS -> { /* handled via repository */ }
                }
            }
        }
    }

    private fun stopConnection() {
        apiClient.disconnect()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val connectionChannel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows when Hermes is connected"; setShowBadge(false) }

            val messageChannel = NotificationChannel(
                MESSAGE_CHANNEL_ID, "Hermes Messages", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "New messages from Hermes"; enableVibration(true) }

            val approvalChannel = NotificationChannel(
                APPROVAL_CHANNEL_ID, "Hermes Approvals", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Approval requests from Hermes"; enableVibration(true); setBypassDnd(true) }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(connectionChannel, messageChannel, approvalChannel))
        }
    }

    private fun buildServiceNotification(): Notification {
        val openIntent = Intent(this, com.hermes.wear.ui.MainActivity::class.java).also {
            it.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hermes Wear")
            .setContentText("Connecting...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hermes Wear")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showMessageNotification(message: HermesMessage) {
        if (!preferenceHelper.enableNotifications) return
        val intent = Intent(this, com.hermes.wear.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, message.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setContentTitle("Hermes")
            .setContentText(message.text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(message.id.hashCode(), notification)
        if (preferenceHelper.vibrateOnMessage) {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    private fun showApprovalNotification(approval: ApprovalRequest) {
        val intent = Intent(this, com.hermes.wear.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, approval.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, APPROVAL_CHANNEL_ID)
            .setContentTitle("\u26A0\uFE0F Approval Required")
            .setContentText(approval.command)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Command: ${approval.command}\n\n${approval.description}"))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setTimeoutAfter(approval.timeoutSeconds * 1000L)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(approval.id.hashCode(), notification)
        if (preferenceHelper.vibrateOnApproval) {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(
                    longArrayOf(0, 400, 200, 400),
                    intArrayOf(0, 255, 0, 255),
                    -1
                ))
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        stopConnection()
        repository.stop()
        super.onDestroy()
    }
}
