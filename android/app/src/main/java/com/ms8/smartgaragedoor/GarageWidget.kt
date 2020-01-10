package com.ms8.smartgaragedoor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.databinding.ObservableField


/**
 * Implementation of App Widget functionality.
 */
class GarageWidget : AppWidgetProvider() {
    private val garageData = GarageData()

    data class GarageData (
    val status : ObservableField<AppState.GarageStatus?> = ObservableField(),
    val previousStatus : ObservableField<AppState.GarageStatus?> = ObservableField()
    )

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate - updating ${appWidgetIds.size} widgets")

        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, garageData.status.get() ?: AppState.GarageStatus.CLOSED)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
        Log.d(TAG, "onEnabled - triggered")

        // Start background service
        Intent(context, GarageWidgetService::class.java).also {intent ->
            context.startService(intent)
        }
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
        Log.d(TAG, "onDisabled - triggered")

//        Intent(context, GarageWidgetService::class.java).also { intent ->
//            context.stopService(intent)
//        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive - ${intent?.action}")
        Log.d(TAG,"onReceive - garageStatus = ${garageData.status.get()}")
        val newStatus = intent?.getStringExtra(EXTRA_NEW_STATUS)
        if (newStatus != null) {
            garageData.status.set(AppState.statusFromString(newStatus))
            Log.d(TAG,"onReceive - now garageStatus = ${garageData.status.get()}")
        }

        when (intent?.action) {
            OnOpenClickAction -> FirebaseDatabaseFunctions.sendGarageAction(FirebaseDatabaseFunctions.ActionType.OPEN)
            OnCloseClickAction  -> {
                FirebaseDatabaseFunctions.sendGarageAction(FirebaseDatabaseFunctions.ActionType.CLOSE)

                @Suppress("NON_EXHAUSTIVE_WHEN")
                when (AppState.garageData.status.get()) {
                    AppState.GarageStatus.OPENING ->
                    {
                        // We know the user previously opened the door, so a close command will keep the garage door in limbo state
                        AppState.garageData.previousStatus.set(AppState.garageData.status.get())
                        AppState.garageData.status.set(AppState.GarageStatus.PAUSED)

                        updateWidgets(context, AppState.GarageStatus.PAUSED.name)
                    }
                    AppState.GarageStatus.PAUSED ->
                    {
                        // A close command while the garage door is PAUSED means we are now closing
                        AppState.garageData.previousStatus.set(AppState.garageData.status.get())
                        AppState.garageData.status.set(AppState.GarageStatus.CLOSING)

                        updateWidgets(context, AppState.GarageStatus.CLOSING.name)
                    }
                }
            }
        }

        super.onReceive(context, intent)
    }

    companion object {
        const val OnOpenClickAction = "onOpenGarageClickAction"
        const val OnCloseClickAction = "onCloseGarageClickAction"

        const val TAG = "SmartGarageWidget"
        const val EXTRA_NEW_STATUS = "EXTRA_NEW_STATUS"
    }
}

internal fun getPendingSelfIntent(context: Context, intentAction: String) : PendingIntent {
    val intent = Intent(context, GarageWidget::class.java).apply {
        action = intentAction
    }

    return PendingIntent.getBroadcast(context, 0, intent, 0)
}

internal fun updateWidgets(context: Context?, newStatusString: String) {
    context?.let { c ->
        val appWidgetManager = AppWidgetManager.getInstance(c)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(c, GarageWidget::class.java))
        val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            putExtra(GarageWidget.EXTRA_NEW_STATUS, newStatusString)
        }
        c.sendBroadcast(updateIntent)
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    currentStatus: AppState.GarageStatus
) {
    val statusText = currentStatus.name
    val statusColor = ContextCompat.getColor(context, AppState.colorFromStatus(currentStatus))

    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.garage_widget)

    // Update garage door status views
    views.setTextViewText(R.id.tvStatus, statusText)
    views.setTextColor(R.id.tvStatus, statusColor)

    // Set button listeners
    views.setOnClickPendingIntent(R.id.btnOpen, getPendingSelfIntent(context, GarageWidget.OnOpenClickAction))
    views.setOnClickPendingIntent(R.id.btnClose, getPendingSelfIntent(context, GarageWidget.OnCloseClickAction))

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}