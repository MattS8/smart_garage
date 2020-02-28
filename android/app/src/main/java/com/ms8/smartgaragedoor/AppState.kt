package com.ms8.smartgaragedoor

import android.util.Log
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField

object AppState {
    fun statusFromString(statusStr: String): GarageStatus {
        return when (statusStr) {
            GarageStatus.CLOSED.name ->
            {
                GarageStatus.CLOSED
            }
            GarageStatus.CLOSING.name ->
            {
                GarageStatus.CLOSING
            }
            GarageStatus.OPEN.name ->
            {
                GarageStatus.OPEN
            }
            GarageStatus.OPENING.name ->
            {
                GarageStatus.OPENING
            }
            GarageStatus.PAUSED.name ->
            {
                GarageStatus.PAUSED
            }
            else ->
            {
                Log.e(TAG, "statusFromString - not a valid string... ($statusStr)")
                GarageStatus.CLOSED
            }
        }
    }

    fun colorFromStatus(garageStatus: GarageStatus): Int {
        return when (garageStatus) {
            GarageStatus.CLOSED -> R.color.colorClose
            GarageStatus.CLOSING -> R.color.colorClosing
            GarageStatus.OPEN -> R.color.colorOpen
            GarageStatus.OPENING -> R.color.colorOpening
            GarageStatus.PAUSED -> R.color.colorPaused
        }
    }

    val garageData = GarageData()
    val errorData = ErrorData()
    val appData = AppData()

    data class GarageData (
        val status : ObservableField<GarageStatus?> = ObservableField(),
        val previousStatus : ObservableField<GarageStatus?> = ObservableField(),
        val autoCloseOptions : ObservableField<FirebaseDatabaseFunctions.AutoCloseOptions> = ObservableField(),
        val autoCloseWarning: ObservableField<FirebaseDatabaseFunctions.AutoCloseWarning?> = ObservableField()
    )

    data class AppData (
        var appInForeground: Boolean = false
    )

    data class ErrorData (
        var garageStatusError : ObservableField<Exception?> = ObservableField()
    )

    enum class GarageStatus {CLOSED, CLOSING, OPEN, OPENING, PAUSED}

    const val TAG = "AppState"
}