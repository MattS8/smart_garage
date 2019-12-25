package com.ms8.smartgaragedoor

import android.util.Log
import androidx.databinding.ObservableField
import java.lang.Exception
import java.util.*

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
            else ->
            {
                Log.e(TAG, "statusFromString - not a valid string... ($statusStr)")
                GarageStatus.CLOSED
            }
        }
    }

    val garageData = GarageData()
    val errorData = ErrorData()

    data class GarageData (
        val status : ObservableField<GarageStatus?> = ObservableField()
    )

    data class ErrorData (
        var garageStatusError : ObservableField<Exception?> = ObservableField()
    )

    enum class GarageStatus {CLOSED, CLOSING, OPEN, OPENING}

    const val TAG = "AppState"
}