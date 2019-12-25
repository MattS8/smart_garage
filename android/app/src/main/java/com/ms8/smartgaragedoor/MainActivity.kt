package com.ms8.smartgaragedoor

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.ms8.smartgaragedoor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding : ActivityMainBinding
    private var garageProgressDrawable : AnimatedVectorDrawableCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.btnOpen.setOnClickListener(this)
        binding.btnClose.setOnClickListener(this)

        garageProgressDrawable = AnimatedVectorDrawableCompat.create(this, R.drawable.av_progress)
        garageProgressDrawable?.registerAnimationCallback(garageProgressViewCallback)
    }

    override fun onResume() {
        super.onResume()
        AppState.garageData.status.addOnPropertyChangedCallback(garageStatusListener)
        updateStatusUI()
    }

    override fun onPause() {
        super.onPause()
        AppState.garageData.status.removeOnPropertyChangedCallback(garageStatusListener)
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btnOpen -> openGarageDoor()
            R.id.btnClose -> closeGarageDoor()
        }
    }

    private fun openGarageDoor() {
        FirebaseDatabaseFunctions.sendGarageAction(FirebaseDatabaseFunctions.ActionType.OPEN)
    }

    private fun closeGarageDoor() {
        FirebaseDatabaseFunctions.sendGarageAction(FirebaseDatabaseFunctions.ActionType.CLOSE)
    }

    private fun updateStatusUI() {
        when (AppState.garageData.status.get()) {
            AppState.GarageStatus.OPEN ->
            {
                binding.tvStatus.text = AppState.GarageStatus.OPEN.name
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.colorOpen))

                binding.progGarageStatus.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            AppState.GarageStatus.CLOSED ->
            {
                binding.tvStatus.text = AppState.GarageStatus.CLOSED.name
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.colorClose))

                binding.progGarageStatus.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            AppState.GarageStatus.CLOSING ->
            {
                binding.tvStatus.text = AppState.GarageStatus.CLOSING.name
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.colorClosing))

                garageProgressDrawable?.setTint(ContextCompat.getColor(this, R.color.colorClosing))
                binding.progGarageStatus.setImageDrawable(garageProgressDrawable)
                garageProgressDrawable?.start()

                binding.progGarageStatus.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(AccelerateInterpolator())
                    .start()
            }
            AppState.GarageStatus.OPENING ->
            {
                binding.tvStatus.text = AppState.GarageStatus.OPENING.name
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.colorOpening))

                garageProgressDrawable?.setTint(ContextCompat.getColor(this, R.color.colorOpening))
                binding.progGarageStatus.setImageDrawable(garageProgressDrawable)
                garageProgressDrawable?.start()

                binding.progGarageStatus.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(AccelerateInterpolator())
                    .start()
            }
            null ->
            {
                Log.e(TAG, "updateStatusUI - status was null. This shouldn't be possible!")
            }
        }
    }

    private val garageStatusListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            updateStatusUI()
        }
    }

    private val garageProgressViewCallback = object : Animatable2Compat.AnimationCallback() {
        override fun onAnimationEnd(drawable: Drawable?) {
            binding.progGarageStatus.post { garageProgressDrawable?.start() }
        }
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
