package com.ms8.smartgaragedoor

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.CompoundButton
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.drawerlayout.widget.DrawerLayout
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.andrognito.flashbar.Flashbar
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.ms8.smartgaragedoor.databinding.ActivityMainBinding
import com.ms8.smartgaragedoor.databinding.DrawerAutoCloseOptionsBinding

class MainActivity : AppCompatActivity(), View.OnClickListener, CompoundButton.OnCheckedChangeListener,
    SeekBar.OnSeekBarChangeListener {

    private lateinit var binding : ActivityMainBinding
    private lateinit var drawer : Drawer
    private lateinit var autoCloseOptionsBinding: DrawerAutoCloseOptionsBinding
    private var garageProgressDrawable : AnimatedVectorDrawableCompat? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        // Set up theme
        val sharedPrefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val themeStr = sharedPrefs.getString(PREFS_THEME, THEME_LIGHT)
        setTheme(if (themeStr == THEME_DARK) R.style.AppTheme_Dark else R.style.AppTheme)

        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.btnOpen.setOnClickListener(this)
        binding.btnClose.setOnClickListener(this)
        binding.btnOptions.setOnClickListener(this)

        garageProgressDrawable = AnimatedVectorDrawableCompat.create(this, R.drawable.av_progress)
        garageProgressDrawable?.registerAnimationCallback(garageProgressViewCallback)

        autoCloseOptionsBinding  = DrawerAutoCloseOptionsBinding.inflate(layoutInflater)
        autoCloseOptionsBinding.swAutoClose.apply {
            isChecked = sharedPrefs.getBoolean(PREFS_AUTO_CLOSE_ENABLED, false)
            setOnCheckedChangeListener(this@MainActivity)
        }
        autoCloseOptionsBinding.swWarnBeforeClosing.apply {
            isChecked = sharedPrefs.getBoolean(PREFS_AUTO_CLOSE_WARN_ENABLED, false)
            setOnCheckedChangeListener(this@MainActivity)
        }
        autoCloseOptionsBinding.sbCloseAfter.apply {
            progress = sharedPrefs.getInt(PREFS_AUTO_CLOSE_AFTER, 0)
            setOnSeekBarChangeListener(this@MainActivity)
        }
        autoCloseOptionsBinding.sbWarningAfter.apply {
            progress = sharedPrefs.getInt(PREFS_AUTO_CLOSE_WARN_AFTER, 0)
            setOnSeekBarChangeListener(this@MainActivity)
        }
        autoCloseOptionsBinding.swTheme.apply {
            text = if (themeStr == THEME_DARK) getString(R.string.dark_theme ) else getString(R.string.light_theme)
            isChecked = themeStr == THEME_DARK
            setOnCheckedChangeListener(this@MainActivity)
        }

        val autoCloseEnabled = sharedPrefs.getBoolean(PREFS_AUTO_CLOSE_ENABLED, false)
        val warningEnabled = sharedPrefs.getBoolean(PREFS_AUTO_CLOSE_WARN_ENABLED, false)

        autoCloseOptionsBinding.sbWarningAfter.isEnabled = autoCloseEnabled && warningEnabled
        autoCloseOptionsBinding.swWarnBeforeClosing.isEnabled = autoCloseEnabled
        autoCloseOptionsBinding.sbCloseAfter.isEnabled = autoCloseEnabled
        autoCloseOptionsBinding.tvCloseAfter.isEnabled = autoCloseEnabled
        autoCloseOptionsBinding.tvCloseAfterValue.isEnabled = autoCloseEnabled
        autoCloseOptionsBinding.tvWarningAfter.isEnabled = autoCloseEnabled && warningEnabled
        autoCloseOptionsBinding.tvWarningAfterValue.isEnabled = autoCloseEnabled && warningEnabled

        autoCloseOptionsBinding.tvWarningAfterValue.text = getTimeFromProgress(autoCloseOptionsBinding.sbWarningAfter.progress)
        autoCloseOptionsBinding.tvCloseAfterValue.text = getTimeFromProgress(autoCloseOptionsBinding.sbCloseAfter.progress)

        autoCloseOptionsBinding.spaceStatusBar.minimumHeight = resources
            .getDimensionPixelSize(resources.getIdentifier("status_bar_height", "dimen", "android"))

        autoCloseOptionsBinding.autoCloseOptionsRoot
            .setBackgroundColor(
                if (themeStr == THEME_DARK)
                    ContextCompat.getColor(this, R.color.colorBackgroundDark)
                else
                    ContextCompat.getColor(this, R.color.colorBackground)
            )

        // Set up Options drawer
        drawer = DrawerBuilder()
            .withActivity(this)
            .withDrawerGravity(Gravity.END)
            .withFullscreen(true)
            .withCloseOnClick(false)
            .withTranslucentStatusBar(false)
            .withCustomView(autoCloseOptionsBinding.autoCloseOptionsRoot)
            .build()
        drawer.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN)
    }

    private fun getTimeFromProgress(progress: Int): String {
        return "${progress * 15} min"
    }


    override fun onBackPressed() {
        if (drawer.isDrawerOpen) {
            drawer.closeDrawer()
        } else {
            super.onBackPressed()
            moveTaskToBack(true)
        }
    }
    override fun onResume() {
        super.onResume()
        AppState.garageData.status.addOnPropertyChangedCallback(garageStatusListener)
        AppState.errorData.garageStatusError.addOnPropertyChangedCallback(garageStatusErrorListener)
        updateStatusUI()

        // Start background service
        Intent(this, GarageWidgetService::class.java).also { intent ->
            startService(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        AppState.garageData.status.removeOnPropertyChangedCallback(garageStatusListener)
        AppState.errorData.garageStatusError.removeOnPropertyChangedCallback(garageStatusErrorListener)
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btnOpen -> openGarageDoor()
            R.id.btnClose -> closeGarageDoor()
            R.id.btnOptions -> drawer.openDrawer()
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        when (buttonView.id) {
            R.id.swTheme -> toggleTheme(isChecked)
            R.id.swAutoClose -> toggleAutoClose(isChecked)
            R.id.swWarnBeforeClosing -> toggleWarnBeforeClosing(isChecked)
        }
    }

    private fun toggleWarnBeforeClosing(isChecked: Boolean) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREFS_AUTO_CLOSE_WARN_ENABLED, isChecked).apply()

        val enabled = prefs.getBoolean(PREFS_AUTO_CLOSE_ENABLED, false)
        val warningTimeout = getWarningTimeout(prefs.getInt(PREFS_AUTO_CLOSE_WARN_AFTER, 0))
        val timeout = getTimeout(prefs.getInt(PREFS_AUTO_CLOSE_AFTER, 0))

        // Enable/Disable warning views
        autoCloseOptionsBinding.sbWarningAfter.isEnabled = isChecked && enabled
        autoCloseOptionsBinding.tvWarningAfter.isEnabled = isChecked && enabled
        autoCloseOptionsBinding.tvWarningAfterValue.isEnabled = isChecked && enabled

        FirebaseDatabaseFunctions.sendAutoCloseOption(enabled, timeout, if (isChecked) warningTimeout else 0)
    }

    private fun toggleAutoClose(isChecked: Boolean) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREFS_AUTO_CLOSE_ENABLED, isChecked).apply()

        val warningTimeout = getWarningTimeout(prefs.getInt(PREFS_AUTO_CLOSE_WARN_AFTER, 0))
        val warningEnabled = prefs.getBoolean(PREFS_AUTO_CLOSE_WARN_ENABLED, false)
        val timeout = getTimeout(prefs.getInt(PREFS_AUTO_CLOSE_AFTER, 0))

        // Enable/Disable auto close views

        autoCloseOptionsBinding.sbCloseAfter.isEnabled = isChecked
        autoCloseOptionsBinding.tvCloseAfter.isEnabled = isChecked
        autoCloseOptionsBinding.tvCloseAfterValue.isEnabled = isChecked
        autoCloseOptionsBinding.tvWarningAfterValue.isEnabled = isChecked && warningEnabled
        autoCloseOptionsBinding.tvWarningAfter.isEnabled = isChecked && warningEnabled
        autoCloseOptionsBinding.sbWarningAfter.isEnabled = isChecked && warningEnabled
        autoCloseOptionsBinding.swWarnBeforeClosing.isEnabled = isChecked

        // Send firebase update
        FirebaseDatabaseFunctions.sendAutoCloseOption(isChecked, timeout, if (warningEnabled) warningTimeout else 0)
    }

    private fun toggleTheme(isChecked: Boolean) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREFS_THEME, if (isChecked) THEME_DARK else THEME_LIGHT)
            .apply()
        recreate()
    }

    private fun openGarageDoor() {
        FirebaseDatabaseFunctions.sendGarageAction(FirebaseDatabaseFunctions.ActionType.OPEN)
    }

    private fun closeGarageDoor() {
        FirebaseDatabaseFunctions.sendGarageAction(FirebaseDatabaseFunctions.ActionType.CLOSE)

        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (AppState.garageData.status.get()) {
            AppState.GarageStatus.OPENING ->
            {
                // We know the user previously opened the door, so a close command will keep the garage door in limbo state
                AppState.garageData.previousStatus.set(AppState.garageData.status.get())
                AppState.garageData.status.set(AppState.GarageStatus.PAUSED)
            }
           AppState.GarageStatus.PAUSED ->
            {
                // A close command while the garage door is PAUSED means we are now closing
                AppState.garageData.previousStatus.set(AppState.garageData.status.get())
                AppState.garageData.status.set(AppState.GarageStatus.CLOSING)
            }
        }
    }

    private fun updateStatusUI() {
        val status = AppState.garageData.status.get() ?: return
        val statusColor = ContextCompat.getColor(this, AppState.colorFromStatus(status))

        // Set status view properties
        binding.tvStatus.apply {
            text = status.name
            setTextColor(statusColor)
        }

        // Show/hide progress view
        val showProgressView = status == AppState.GarageStatus.OPENING || status == AppState.GarageStatus.CLOSING
        binding.progGarageStatus.apply {
            if (showProgressView) {
                garageProgressDrawable?.setTint(statusColor)
                setImageDrawable(garageProgressDrawable)
                garageProgressDrawable?.start()
            }

            animate()
                .alpha(if (showProgressView) 1f else 0f)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private val garageStatusErrorListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            AppState.errorData.garageStatusError.get()?.let { exception ->
                val errorString = getString(R.string.error_occurred_message) + " " + exception.message
                Flashbar.Builder(this@MainActivity)
                    .icon(R.drawable.ic_error_red_24dp)
                    .title(R.string.error)
                    .message(errorString)
                    .show()
                AppState.errorData.garageStatusError.set(null)
            }
        }
    }

    private val garageStatusListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            val previousStatus = AppState.garageData.previousStatus.get()
            val currentStatus = AppState.garageData.status.get()
            when {
                previousStatus == AppState.GarageStatus.OPENING && currentStatus == AppState.GarageStatus.CLOSED ->
                {
                    Flashbar.Builder(this@MainActivity)
                        .icon(R.drawable.ic_error_white_24dp)
                        .title(R.string.garage_closed_title)
                        .message(R.string.garage_door_closed_message)
                        .dismissOnTapOutside()
                        .show()
                }
                previousStatus == AppState.GarageStatus.CLOSING && currentStatus == AppState.GarageStatus.OPEN ->
                {
                    Flashbar.Builder(this@MainActivity)
                        .icon(R.drawable.ic_error_white_24dp)
                        .title(R.string.garage_open_title)
                        .message(R.string.garage_door_reopened_message)
                        .dismissOnTapOutside()
                        .show()
                }
            }
            AppState.garageData.previousStatus.set(currentStatus)
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
        const val PREFS = "com.ms8.smartgaragedoor.PREFS"
        const val PREFS_THEME = "PREFS_THEME"
        const val PREFS_AUTO_CLOSE_ENABLED = "PREFS_AUTO_CLOSE_ENABLED"
        const val PREFS_AUTO_CLOSE_WARN_ENABLED = "PREFS_AUTO_CLOSE_WARN_ENABLED"
        const val PREFS_AUTO_CLOSE_AFTER = "PREFS_AUTO_CLOSE_AFTER"
        const val PREFS_AUTO_CLOSE_WARN_AFTER = "PREFS_AUTO_CLOSE_WARN_AFTER"
        const val THEME_DARK = "THEME_DARK"
        const val THEME_LIGHT = "THEME_LIGHT"
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        when (seekBar.id) {
            R.id.sbCloseAfter -> autoCloseOptionsBinding.tvCloseAfterValue.text = getTimeFromProgress(progress + 1)
            R.id.sbWarningAfter -> autoCloseOptionsBinding.tvWarningAfterValue.text = getTimeFromProgress(progress)
        }
    }

    override fun onStartTrackingTouch(p0: SeekBar?) {}

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        when (seekBar.id) {
            R.id.sbCloseAfter -> updateCloseAfter()
            R.id.sbWarningAfter -> updateWarningAfter()
        }
    }

    private fun updateWarningAfter() {
        val progress = autoCloseOptionsBinding.sbWarningAfter.progress
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(PREFS_AUTO_CLOSE_WARN_AFTER, progress).apply()

        // Update text view
        autoCloseOptionsBinding.tvWarningAfterValue.text = getTimeFromProgress(progress)

        // Get the new timeout (in milliseconds)
        val warningTimeout: Long = getWarningTimeout(progress)

        val enabled = prefs.getBoolean(PREFS_AUTO_CLOSE_ENABLED, false)
        val timeout = getTimeout(prefs.getInt(PREFS_AUTO_CLOSE_AFTER, 0))
        val warningEnabled = prefs.getBoolean(PREFS_AUTO_CLOSE_WARN_ENABLED, false)

        FirebaseDatabaseFunctions.sendAutoCloseOption(enabled, timeout, if (warningEnabled) warningTimeout else 0)
    }

    private fun updateCloseAfter() {
        val progress = autoCloseOptionsBinding.sbCloseAfter.progress
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(PREFS_AUTO_CLOSE_AFTER, progress).apply()

        // Update text view
        autoCloseOptionsBinding.tvCloseAfterValue.text = getTimeFromProgress(progress+1)

        // Get the new timeout (in milliseconds)
        val timeout: Long = getTimeout(progress)

        val enabled = prefs.getBoolean(PREFS_AUTO_CLOSE_ENABLED, false)
        val warningTimeout = getWarningTimeout(prefs.getInt(PREFS_AUTO_CLOSE_WARN_AFTER, 0))
        val warningEnabled = prefs.getBoolean(PREFS_AUTO_CLOSE_WARN_ENABLED, false)

        FirebaseDatabaseFunctions.sendAutoCloseOption(enabled, timeout, if (warningEnabled) warningTimeout else 0)
    }

    private fun getWarningTimeout(progress: Int) : Long  = (15 * progress * 60 * 1000).toLong()
    private fun getTimeout(progress: Int): Long = (15 * (progress + 1) * 60 * 1000).toLong()


}
