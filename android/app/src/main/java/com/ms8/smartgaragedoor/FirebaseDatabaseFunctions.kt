package com.ms8.smartgaragedoor

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

object FirebaseDatabaseFunctions {
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
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        database.reference
            .child(GARAGES)
            .child("home_garage")
            .child(ACTION)
            .setValue(
                GarageAction(actionType.name,
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
                AppState.garageData.status.set(AppState.statusFromString(newStatus))
            } catch (e : Exception) { Log.e(TAG, "$e") }
        }

    }

    data class GarageAction (
        val type : String,
        val uid : String,
        val timestamp : String
    )

    enum class ActionType {OPEN, CLOSE}

    private const val GARAGES = "garages"
    private const val STATUS = "status"
    private const val ACTION = "action"
    private const val TYPE = "type"

    private const val ACTION_OPEN = "OPEN"
    private const val ACTION_CLOSE = "CLOSE"
}