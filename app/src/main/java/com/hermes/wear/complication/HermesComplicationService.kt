package com.hermes.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequestPager
import com.hermes.wear.data.repository.PreferenceHelper
import com.hermes.wear.ui.MainActivity

/**
 * Complication data provider that shows Hermes connection status
 * and serves as a quick-launch button for the app.
 *
 * Supported complication types:
 * - SHORT_TEXT: Shows "Hermes" or brief status
 * - ICON: Shows the Hermes icon
 * - RANGED_VALUE: Shows if there are pending messages/approvals
 */
class HermesComplicationService : ComplicationDataSourceService() {

    override fun onComplicationActivated(
        complicationInstanceId: Int,
        type: ComplicationType
    ) {
        // Complication became active
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        // Complication deactivated
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val prefs = PreferenceHelper(this)

        when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                val text = ComplicationData.Builder(ComplicationType.SHORT_TEXT)
                    .setShortText(PlainComplicationText.Builder("Hermes").build())
                    .setContentDescription(PlainComplicationText.Builder("Open Hermes Wear").build())
                    .setTapAction(createTapAction())
                    .build()
                listener.onComplicationData(text)
            }

            ComplicationType.ICON -> {
                val icon = ComplicationData.Builder(ComplicationType.ICON)
                    .setIcon(MonochromaticImage.Builder(
                        android.graphics.drawable.Icon.createWithResource(
                            this,
                            android.R.drawable.ic_dialog_info
                        )
                    ).build())
                    .setContentDescription(PlainComplicationText.Builder("Hermes").build())
                    .setTapAction(createTapAction())
                    .build()
                listener.onComplicationData(icon)
            }

            ComplicationType.RANGED_VALUE -> {
                val value = ComplicationData.Builder(ComplicationType.RANGED_VALUE)
                    .setValue(if (prefs.autoConnect) 1f else 0f)
                    .setMinValue(0f)
                    .setMaxValue(1f)
                    .setShortText(PlainComplicationText.Builder(
                        if (prefs.autoConnect) "ON" else "OFF"
                    ).build())
                    .setContentDescription(PlainComplicationText.Builder(
                        "Hermes connection status"
                    ).build())
                    .setTapAction(createTapAction())
                    .build()
                listener.onComplicationData(value)
            }

            else -> {
                listener.onComplicationData(
                    NoDataComplicationData.Builder(ComplicationType.NO_DATA).build()
                )
            }
        }
    }

    private fun createTapAction(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
