package com.ms8.smartgaragedoor

import android.util.ArrayMap
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

object FirebaseDatabaseFunctions {
    private const val TAG = "FirebaseDBFunctions"

    // Listeners
    private var garageStatusListener : GarageStatusListener? = null
    private var garageOptionsListener : GarageOptionsListener? = null

    /**
     * Changes the action endpoint for the garage to the
     * specified action type. The Arduino will  read this
     * action and act accordingly.
     *
     * @param actionType The type of action to send to the garage
     */
    fun sendGarageAction(actionType : ActionType) {
        val database = FirebaseDatabase.getInstance()
        Log.d(TAG, "sendGarageAction - sending action ${actionType.name} ($database)")
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.reference
            .child(GARAGES)
            .child(HOME_GARAGE)
            .child(CONTROLLER)
            .child(ACTION)
            .setValue(
                GarageAction(actionType.name,
                    uid,
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Calendar.getInstance().time))
            )
    }

    /**
     * Sends a debug message to firebase for remote
     * logging. The message is stored under debug/uid
     * with the identifier being a timestamp in
     * the format yyyy-MM-dd HH:mm:ss.
     *
     * @param message The debug message to store on firebase
     */
    fun sendDebugMessage(message: String) {
        val database = FirebaseDatabase.getInstance()
        Log.d(TAG, "sendDebugMessage - sending debug message $message ($database)")
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.reference
            .child(GARAGES)
            .child(HOME_GARAGE)
            .child(DEBUG)
            .child(uid)
            .child(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Calendar.getInstance().time))
            .setValue(DebugMessage(message))
    }

    /**
     * Changes the auto_close_options endpoint for the garage to
     * reflect the new option changes. The Arduino will read in
     * the new option and update it's 'auto close' functionality
     * accordingly.
     */
    fun sendAutoCloseOption(options: AutoCloseOptions) {
        val database = FirebaseDatabase.getInstance()
        Log.d(TAG, "sendAutoCloseOption - updating options to ${options.enabled}, ${options.timeout}, ${options.warningTimeout}")
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.reference
            .child(GARAGES)
            .child(HOME_GARAGE)
            .child(CONTROLLER)
            .child(AUTO_CLOSE_OPTIONS)
            .setValue(
                AutoCloseOptions(
                    options.enabled,
                    options.timeout,
                    options.warningTimeout,
                    options.warningEnabled,
                    uid,
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Calendar.getInstance().time))
            )
    }

    /**
     * Registers listener for garage status. The listener
     * will update the AppState upon change and first time
     * calling this function.
     */
    fun listenForGarageStatus() {
        val database = FirebaseDatabase.getInstance()

        if (garageStatusListener == null) {
            garageStatusListener = GarageStatusListener()
            garageStatusListener?.let {
                database.reference
                    .child(GARAGES)
                    .child(HOME_GARAGE)
                    .child(STATUS)
                    .addValueEventListener(it)
            }
        }
    }

    fun listenForOptionChanges() {
        val database = FirebaseDatabase.getInstance()

        if (garageOptionsListener == null) {
            garageOptionsListener = GarageOptionsListener()
            garageOptionsListener?.let {
                database.reference
                    .child(GARAGES)
                    .child(HOME_GARAGE)
                    .child(CONTROLLER)
                    .child(AUTO_CLOSE_OPTIONS)
                    .addValueEventListener(it)
            }
        }
    }

    fun addTokenToGarage(token: String) {
        val database = FirebaseDatabase.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.reference
            .child(GARAGES)
            .child(HOME_GARAGE)
            .child(DEVICE_TOKENS)
            .child(ALL_TOKENS)
            .child(token)
            .setValue(uid)
    }

    fun addStatusToken(token: String) {
        val database = FirebaseDatabase.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.reference
            .child(GARAGES)
            .child(HOME_GARAGE)
            .child(DEVICE_TOKENS)
            .child(STATUS_UPDATE)
            .child(token)
            .setValue(uid)
    }

    fun removeStatusToken(token: String) {
        val database = FirebaseDatabase.getInstance()

        database.reference
            .child(GARAGES)
            .child(HOME_GARAGE)
            .child(DEVICE_TOKENS)
            .child(STATUS_UPDATE)
            .child(token)
            .removeValue()
    }

    private class GarageOptionsListener : ValueEventListener {
        @Suppress("PropertyName")
        val TAG = "GarageOptionsListener"
        override fun onCancelled(p0: DatabaseError) {
            Log.w(TAG, "cancelled")
            garageStatusListener = null
            sendDebugMessage("$TAG - cancelled!")
        }

        override fun onDataChange(snapshot: DataSnapshot) {
            @Suppress("UNCHECKED_CAST")
            try {
                val snapshotValues = snapshot.value as Map<String, Any?>

                val newOptions = AutoCloseOptions(
                    snapshotValues["enabled"] as Boolean,
                    snapshotValues["timeout"] as Long,
                    snapshotValues["warningTimeout"] as Long,
                    snapshotValues["warningEnabled"] as Boolean,
                    snapshotValues["uid"] as String,
                    snapshotValues["o_timestamp"] as String)

                AppState.garageData.autoCloseOptions.set(newOptions)
            } catch (e: Exception) {
                sendDebugMessage("$TAG - encountered an error: $e")
                Log.e(TAG, "$TAG - dataChange: $e")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private class GarageStatusListener : ValueEventListener {
        @Suppress("PropertyName")
        val TAG = "GarageStatusListener"
        override fun onCancelled(p0: DatabaseError) {
            Log.w(TAG, "cancelled")
            garageStatusListener = null
            sendDebugMessage("$TAG - cancelled!")
        }

        override fun onDataChange(snapshot: DataSnapshot) {
            try {
                val snapshotValues = snapshot.value as Map<String, Any?>
                val newStatus = snapshotValues[TYPE] as String
                AppState.garageData.previousStatus.set(AppState.garageData.status.get())
                AppState.garageData.status.set(AppState.statusFromString(newStatus))
            } catch (e : Exception) {
                AppState.errorData.garageStatusError.set(e)
                Log.e(TAG, "$e")
                sendDebugMessage("GarageStatusListener - An error occurred: $e")
            }
        }
    }

    private data class DebugMessage(val message: String)

    data class GarageAction (
        val type : String,
        val uid : String,
        val a_timestamp : String
    )

    data class AutoCloseOptions (
        var enabled : Boolean = false,
        var timeout : Long = 0,
        var warningTimeout : Long = 0,
        var warningEnabled: Boolean = false,
        var uid : String = "",
        var o_timestamp : String = ""
    )

    data class AutoCloseWarning(
        val timeout: Long,
        val timestamp: Long
    )

    enum class ActionType {OPEN, CLOSE, STOP_AUTO_CLOSE}

    private const val DEBUG = "debug"
    private const val CONTROLLER = "controller"
    private const val HOME_GARAGE = "home_garage"
    private const val GARAGES = "garages"
    private const val STATUS = "status"
    private const val ACTION = "action"
    private const val AUTO_CLOSE_OPTIONS = "auto_close_options"
    const val AUTO_CLOSE_WARNING = "auto_close_warning"
    const val STATUS_UPDATE = "status_update"
    private const val DEVICE_TOKENS = "device_tokens"
    private const val ALL_TOKENS = "all_tokens"
    const val TYPE = "type"
}