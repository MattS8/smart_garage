package com.ms8.smartgaragedoor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.AsyncTask

class GarageBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val asyncTask = Task(pendingResult, intent)
        asyncTask.execute()
    }

    private class Task (private val pendingResult: PendingResult, private val intent: Intent)
        : AsyncTask<String, Int, String>() {
        override fun doInBackground(vararg p0: String?): String {
            when (intent.action) {
                ACTION_CANCEL_AUTO_CLOSE -> FirebaseDatabaseFunctions
                    .sendGarageAction(FirebaseDatabaseFunctions.ActionType.STOP_AUTO_CLOSE)
            }

            return toString()
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            pendingResult.finish()
        }
    }

    companion object {
        const val EXTRA_SMART_GARAGE_INTENTS = "com.ms8.smartgaragedoor.intenTypes"
        const val CANCEL_AUTO_CLOSE = "GARAGE_CANCEL_AUTO_CLOSE"
        const val ACTION_CANCEL_AUTO_CLOSE = "coms.ms8.smartgaragedoor.actions.cancel_auto_close"
    }
}
