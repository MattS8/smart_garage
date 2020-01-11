package com.ms8.smartgaragedoor

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

        return START_STICKY
    }

    private fun getNotificationId(): Int {
        return notificationId
    }

    private val garageOptionsListener = object : ValueEventListener {
        override fun onCancelled(p0: DatabaseError) {
            Log.e(GarageWidget.TAG, "garageOptionsLister - cancelled")
            FirebaseDatabaseFunctions.sendDebugMessage("Garage Options Listener has been cancelled!")
        }

        override fun onDataChange(snapshot: DataSnapshot) {
            @Suppress("UNCHECKED_CAST")
            try {
                val snapshotValues = snapshot.value as Map<String, Any?>

                Log.d(GarageWidget.TAG, "enabled is a ${if (snapshotValues["enabled"] is Boolean) "BOOLEAN" else "ERROR"}" )
                Log.d(GarageWidget.TAG, "warningEnabled is a ${if (snapshotValues["warningEnabled"] is Boolean) "BOOLEAN" else "ERROR"}" )

                val newOptions = FirebaseDatabaseFunctions.AutoCloseOptions(
                    snapshotValues["enabled"] as Boolean,
                    snapshotValues["timeout"] as Long,
                    snapshotValues["warningTimeout"] as Long,
                    snapshotValues["warningEnabled"] as Boolean,
                    snapshotValues["uid"] as String,
                    snapshotValues["o_timestamp"] as String)

                AppState.garageData.autoCloseOptions.set(newOptions)
                Log.d(GarageWidget.TAG, "garageOptionsListener - Got new autoCloseOptions!")
            } catch (e: Exception) {
                FirebaseDatabaseFunctions.sendDebugMessage("Garage Options Listener encountered an error - $e")
                Log.e(GarageWidget.TAG, "garageOptionsListener dataChange - $e")
            }
        }

    }

    private val garageWidgetListener = object : ValueEventListener {
        override fun onCancelled(p0: DatabaseError) {
            Log.e(GarageWidget.TAG, "garageWidgetListener - cancelled")
            FirebaseDatabaseFunctions.sendDebugMessage("Garage Widget Listener has been cancelled!")
        }

        @Suppress("UNCHECKED_CAST")
        override fun onDataChange(snapshot: DataSnapshot) {
            try {
                val snapshotValues = snapshot.value as Map<String, Any?>
                val newStatusString = snapshotValues[FirebaseDatabaseFunctions.TYPE] as String

                val context = applicationContext
                if (context == null) {
                    Log.w(GarageWidget.TAG, "onDataChange - context was null")
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
                Log.e(GarageWidget.TAG, "garageWidgetListener - $e")
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
