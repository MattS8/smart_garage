package com.ms8.smartgaragedoor

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

object FirebaseDatabaseFunctions {
    private const val TAG = "FirebaseDBFunctions"

    // Listeners
    private var garageStatusListener : GarageStatusListener? = null

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
            .child("home_garage")
            .child("controller")
            .child(ACTION)
            .setValue(
                GarageAction(actionType.name,
                    uid,
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Calendar.getInstance().time))
            )
    }

    /**
     * Changes the auto_close_options endpoint for the garage to
     * reflect the new option changes. The Arduino will read in
     * the new option and update it's 'auto close' functionality
     * accordingly.
     *
     * @param enabled Whether auto close functionality is enabled
     * @param timeout The time (in milliseconds) to wait before
     *  automatically shutting the garage door
     *  @param warningTimeout The time (in milliseconds) to wait
     *  before automatically shutting the garage door
     */
    fun sendAutoCloseOption(enabled: Boolean, timeout: Long, warningTimeout: Long) {
        val database = FirebaseDatabase.getInstance()
        Log.d(TAG, "sendAutoCloseOption - updating options to $enabled, $timeout, $warningTimeout")
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.reference
            .child(GARAGES)
            .child("home_garage")
            .child("controller")
            .child(AUTO_CLOSE_OPTIONS)
            .setValue(
                AutoCloseOptions(enabled,
                    timeout,
                    warningTimeout,
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
                    .child("home_garage")
                    .child(STATUS)
                    .addValueEventListener(it)
            }
        }
    }

    fun addGarageWidgetListener(eventListener: ValueEventListener?) {
        val database = FirebaseDatabase.getInstance()

        eventListener?.let { listener ->
            database.reference
                .child(GARAGES)
                .child("home_garage")
                .child(STATUS)
                .addValueEventListener(listener)
        }
    }

    fun removeGarageWidgetListener(eventListener: ValueEventListener?) {
        val database = FirebaseDatabase.getInstance()

        eventListener?.let { listener ->
            database.reference
                .child(GARAGES)
                .child("home_garage")
                .child(STATUS)
                .removeEventListener(listener)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private class GarageStatusListener : ValueEventListener {
        val TAG = "GarageListener"
        override fun onCancelled(p0: DatabaseError) {
            Log.w(TAG, "cancelled")
            garageStatusListener = null
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
            }
        }

    }

    data class GarageAction (
        val type : String,
        val uid : String,
        val a_timestamp : String
    )

    data class AutoCloseOptions (
        val enabled : Boolean,
        val timeout : Long,
        val warningTimeout : Long,
        val uid : String,
        val o_timestamp : String
    )

    enum class ActionType {OPEN, CLOSE}

    private const val GARAGES = "garages"
    private const val STATUS = "status"
    private const val ACTION = "action"
    private const val AUTO_CLOSE_OPTIONS = "auto_close_options"
    const val TYPE = "type"

    private const val ACTION_OPEN = "OPEN"
    private const val ACTION_CLOSE = "CLOSE"
}