package com.ms8.smartgaragedoor

import android.app.Notification.EXTRA_NOTIFICATION_ID
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.ms8.smartgaragedoor.GarageBroadcastReceiver.Companion.ACTION_CANCEL_AUTO_CLOSE
import com.ms8.smartgaragedoor.GarageWidget.Companion.TAG

class GarageWidgetService : Service() {
    private var hasStarted = false
    private var notificationId = 8888

    private var status: AppState.GarageStatus = AppState.GarageStatus.CLOSED

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel(applicationContext)

        if (hasStarted)
            return START_STICKY

        FirebaseDatabaseFunctions.addGarageWidgetListener(garageWidgetListener)
        FirebaseDatabaseFunctions.addOptionsListener(garageOptionsListener)
        FirebaseDatabaseFunctions.addAutoCloseWarningListener(autoCloseWarningListener)

        return START_STICKY
    }

    private fun getNotificationId(): Int {
        return notificationId
    }

    /** ---------------- Listeners ----------------  **/

    private val autoCloseWarningListener = object : ValueEventListener {
        override fun onCancelled(p0: DatabaseError) {
            Log.e(TAG, "autoCloseWarningListener - cancelled!")
            FirebaseDatabaseFunctions.sendDebugMessage("Auto Close Warning Listener has been cancelled!")
        }

        override fun onDataChange(snapshot: DataSnapshot) {
            @Suppress("UNCHECKED_CAST")
            try {
                val snapshotValues = snapshot.value as Map<String, Any?>
                val timeout = snapshotValues["timeout"] as Long
                val timestamp = snapshotValues["timestamp"] as Long
                AppState.garageData.autoCloseWarning.set(FirebaseDatabaseFunctions.AutoCloseWarning(timeout, timestamp))

                // Don't send a notification if the app is opened or if no warning was actually sent
                if (AppState.appData.appInForeground || AppState.garageData.autoCloseWarning.get()?.timeout ?: 0 == 0.toLong()) {
                    Log.d(TAG, "App is in foreground or warning was set to FALSE")
                    return
                }

                // Send notification with action to cancel auto close
                val context = applicationContext
                if (context == null) {
                    Log.w(TAG, "onDataChange - context was null")
                    FirebaseDatabaseFunctions.sendDebugMessage("Garage Widget Listener tried to parse data change, " +
                            "but context was null!")
                }

                context?.let { c ->
                    val cancelAutoCloseIntent = Intent(c, GarageBroadcastReceiver::class.java).apply {
                        action = ACTION_CANCEL_AUTO_CLOSE
                        putExtra(EXTRA_NOTIFICATION_ID, CHANNEL_ID)
                    }
                    val cancelAutoClosePendingIntent = PendingIntent.getBroadcast(c, 0, cancelAutoCloseIntent, 0)
                    val newIntent = Intent(c, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    val builder = NotificationCompat.Builder(c, CHANNEL_ID)
                        .setSmallIcon(R.drawable.garage_icon)
                        .setContentTitle(getString(R.string.auto_close_warning_title))
                        .setContentText(getString(R.string.auto_close_warning_desc))
                        .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.auto_close_warning_desc)))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(PendingIntent.getBroadcast(c, 0, newIntent, 0))
                        .setAutoCancel(true)
                        .addAction(R.drawable.ic_clear_black_24dp, getString(R.string.cancel_auto_close), cancelAutoClosePendingIntent)
                    with(NotificationManagerCompat.from(c)) {
                        notify(getNotificationId(), builder.build())
                    }
                }

            } catch (e: Exception) {
                FirebaseDatabaseFunctions.sendDebugMessage("Auto Close Warning Listener: $e")
                Log.e(TAG, "autoCloseWarningListener - $e")
            }
        }
    }

    private val garageOptionsListener = object : ValueEventListener {
        override fun onCancelled(p0: DatabaseError) {
            Log.e(TAG, "garageOptionsLister - cancelled")
            FirebaseDatabaseFunctions.sendDebugMessage("Garage Options Listener has been cancelled!")
        }

        override fun onDataChange(snapshot: DataSnapshot) {
            @Suppress("UNCHECKED_CAST")
            try {
                val snapshotValues = snapshot.value as Map<String, Any?>

                Log.d(TAG, "enabled is a ${if (snapshotValues["enabled"] is Boolean) "BOOLEAN" else "ERROR"}" )
                Log.d(TAG, "warningEnabled is a ${if (snapshotValues["warningEnabled"] is Boolean) "BOOLEAN" else "ERROR"}" )

                val newOptions = FirebaseDatabaseFunctions.AutoCloseOptions(
                    snapshotValues["enabled"] as Boolean,
                    snapshotValues["timeout"] as Long,
                    snapshotValues["warningTimeout"] as Long,
                    snapshotValues["warningEnabled"] as Boolean,
                    snapshotValues["uid"] as String,
                    snapshotValues["o_timestamp"] as String)

                AppState.garageData.autoCloseOptions.set(newOptions)
                Log.d(TAG, "garageOptionsListener - Got new autoCloseOptions!")
            } catch (e: Exception) {
                FirebaseDatabaseFunctions.sendDebugMessage("Garage Options Listener encountered an error - $e")
                Log.e(TAG, "garageOptionsListener dataChange - $e")
            }
        }
    }

    private val garageWidgetListener = object : ValueEventListener {
        override fun onCancelled(p0: DatabaseError) {
            Log.e(TAG, "garageWidgetListener - cancelled")
            FirebaseDatabaseFunctions.sendDebugMessage("Garage Widget Listener has been cancelled!")
        }

        @Suppress("UNCHECKED_CAST")
        override fun onDataChange(snapshot: DataSnapshot) {
            try {
                val snapshotValues = snapshot.value as Map<String, Any?>
                val newStatusString = snapshotValues[FirebaseDatabaseFunctions.TYPE] as String

                val context = applicationContext
                if (context == null) {
                    Log.w(TAG, "onDataChange - context was null")
                    FirebaseDatabaseFunctions.sendDebugMessage("Garage Widget Listener tried to parse data change, but context was null!")
                }

                context?.let { c ->
                    val appWidgetManager = AppWidgetManager.getInstance(c)
                    val ids = appWidgetManager.getAppWidgetIds(ComponentName(c, GarageWidget::class.java))
                    val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        putExtra(GarageWidget.EXTRA_NEW_STATUS, newStatusString)
                    }
                    c.sendBroadcast(updateIntent)

                    // Send notification if the door reversed direction on the user
                    val newStatus = AppState.statusFromString(newStatusString)
                    when {
                        status == AppState.GarageStatus.OPENING && newStatus == AppState.GarageStatus.CLOSED ->
                        {
                            val newIntent = Intent(c, MainActivity::class.java)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            val builder = NotificationCompat.Builder(c, CHANNEL_ID)
                                .setSmallIcon(R.drawable.garage_icon)
                                .setContentTitle(getString(R.string.garage_closed_title))
                                .setContentText(getString(R.string.garage_door_closed_message))
                                .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.garage_door_closed_message)))
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setContentIntent(PendingIntent.getActivity(c, 0, newIntent, 0))
                                .setAutoCancel(true)
                            with(NotificationManagerCompat.from(c)) {
                                notify(getNotificationId(), builder.build())
                            }
                        }
                        status == AppState.GarageStatus.CLOSING && newStatus == AppState.GarageStatus.OPEN ->
                        {
                            val newIntent = Intent(c, MainActivity::class.java)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            val builder = NotificationCompat.Builder(c, CHANNEL_ID)
                                .setSmallIcon(R.drawable.garage_icon)
                                .setContentTitle(getString(R.string.garage_closed_title))
                                .setContentText(getString(R.string.garage_door_closed_message))
                                .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.garage_door_closed_message)))
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setContentIntent(PendingIntent.getActivity(c, 0, newIntent, 0))
                                .setAutoCancel(true)
                            with(NotificationManagerCompat.from(c)) {
                                notify(getNotificationId(), builder.build())
                            }
                        }
                    }

                    status = newStatus
                }
            } catch (e : Exception) {
                FirebaseDatabaseFunctions.sendDebugMessage("Garage Widget Listener encountered an error: $e")
                Log.e(TAG, "garageWidgetListener - $e")
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "GARAGE_DOOR_DEFAULT"

        fun createNotificationChannel(context: Context) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = context.getString(R.string.channel_name)
                val descriptionText = context.getString(R.string.channel_description)
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                // Register the channel with the system
                val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}
