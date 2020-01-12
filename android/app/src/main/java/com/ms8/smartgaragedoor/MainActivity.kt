package com.ms8.smartgaragedoor

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
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
    private lateinit var optionsBinding: DrawerAutoCloseOptionsBinding
    private var garageProgressDrawable : AnimatedVectorDrawableCompat? = null
    private var flashbar: Flashbar? = null
    private var themeStr: String = ""


    private fun getBackgroundColor(): Int {
        return if (themeStr == THEME_DARK)
            ContextCompat.getColor(this, R.color.colorBackgroundDark)
        else
            ContextCompat.getColor(this, R.color.colorBackground)
    }

    private fun setupOptions(themeStr: String?, sharedPrefs: SharedPreferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE)) {
        optionsBinding  = DrawerAutoCloseOptionsBinding.inflate(layoutInflater)
        optionsBinding.swAutoClose.apply {
            isChecked = sharedPrefs.getBoolean(PREFS_AUTO_CLOSE_ENABLED, false)
            setOnCheckedChangeListener(this@MainActivity)
        }
        optionsBinding.swWarnBeforeClosing.apply {
            isChecked = sharedPrefs.getBoolean(PREFS_AUTO_CLOSE_WARN_ENABLED, false)
            setOnCheckedChangeListener(this@MainActivity)
        }
        optionsBinding.sbCloseAfter.apply {
            progress = sharedPrefs.getInt(PREFS_AUTO_CLOSE_AFTER, 0)
            setOnSeekBarChangeListener(this@MainActivity)
        }
        optionsBinding.sbWarningAfter.apply {
            progress = sharedPrefs.getInt(PREFS_AUTO_CLOSE_WARN_AFTER, 0)
            setOnSeekBarChangeListener(this@MainActivity)
        }
        optionsBinding.swTheme.apply {
            text = if (themeStr == THEME_DARK) getString(R.string.dark_theme ) else getString(R.string.light_theme)
            isChecked = themeStr == THEME_DARK
            setOnCheckedChangeListener(this@MainActivity)
        }

        val autoCloseEnabled = sharedPrefs.getBoolean(PREFS_AUTO_CLOSE_ENABLED, false)
        val warningEnabled = sharedPrefs.getBoolean(PREFS_AUTO_CLOSE_WARN_ENABLED, false)

        optionsBinding.sbWarningAfter.isEnabled = autoCloseEnabled && warningEnabled
        optionsBinding.swWarnBeforeClosing.isEnabled = autoCloseEnabled
        optionsBinding.sbCloseAfter.isEnabled = autoCloseEnabled
        optionsBinding.tvCloseAfter.isEnabled = autoCloseEnabled
        optionsBinding.tvCloseAfterValue.isEnabled = autoCloseEnabled
        optionsBinding.tvWarningAfter.isEnabled = autoCloseEnabled && warningEnabled
        optionsBinding.tvWarningAfterValue.isEnabled = autoCloseEnabled && warningEnabled

        optionsBinding.tvWarningAfterValue.text = getTimeFromProgress(optionsBinding.sbWarningAfter.progress)
        optionsBinding.tvCloseAfterValue.text = getTimeFromProgress(optionsBinding.sbCloseAfter.progress)

        optionsBinding.spaceStatusBar.minimumHeight = resources
            .getDimensionPixelSize(resources.getIdentifier("status_bar_height", "dimen", "android"))

        optionsBinding.autoCloseOptionsRoot
            .setBackgroundColor(getBackgroundColor())

        // Set up Options drawer
        drawer = DrawerBuilder()
            .withActivity(this)
            .withDrawerGravity(Gravity.END)
            .withFullscreen(true)
            .withCloseOnClick(false)
            .withTranslucentStatusBar(false)
            .withCustomView(optionsBinding.autoCloseOptionsRoot)
            .build()
        drawer.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN)
        drawer.closeDrawer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set up theme
        val sharedPrefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        themeStr = sharedPrefs.getString(PREFS_THEME, THEME_LIGHT) ?: THEME_LIGHT
        setTheme(if (themeStr == THEME_DARK) R.style.AppTheme_Dark else R.style.AppTheme)

        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.btnOpen.setOnClickListener(this)
        binding.btnClose.setOnClickListener(this)
        binding.btnOptions.setOnClickListener(this)

        garageProgressDrawable = AnimatedVectorDrawableCompat.create(this, R.drawable.av_progress)
        garageProgressDrawable?.registerAnimationCallback(garageProgressViewCallback)

        setupOptions(themeStr)
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
        AppState.appData.appInForeground = true

        AppState.garageData.status.addOnPropertyChangedCallback(garageStatusListener)
        AppState.errorData.garageStatusError.addOnPropertyChangedCallback(garageStatusErrorListener)
        AppState.garageData.autoCloseOptions.addOnPropertyChangedCallback(optionsChangedListener)
        AppState.garageData.autoCloseWarning.addOnPropertyChangedCallback(autoCloseWarningListener)
        updateStatusUI()

        // Start background service
        Intent(this, GarageWidgetService::class.java).also { intent ->
            startService(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        AppState.appData.appInForeground = false

        AppState.garageData.status.removeOnPropertyChangedCallback(garageStatusListener)
        AppState.errorData.garageStatusError.removeOnPropertyChangedCallback(garageStatusErrorListener)
        AppState.garageData.autoCloseOptions.removeOnPropertyChangedCallback(optionsChangedListener)
        AppState.garageData.autoCloseWarning.removeOnPropertyChangedCallback(autoCloseWarningListener)
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
        val sendFirebaseUpdate = prefs.getBoolean(PREFS_AUTO_CLOSE_WARN_ENABLED, false) == AppState.garageData.autoCloseOptions.get()?.warningEnabled

        prefs.edit().putBoolean(PREFS_AUTO_CLOSE_WARN_ENABLED, isChecked).apply()

        val enabled = prefs.getBoolean(PREFS_AUTO_CLOSE_ENABLED, false)
        val warningTimeout = getWarningTimeout(prefs.getInt(PREFS_AUTO_CLOSE_WARN_AFTER, 0))
        val timeout = getTimeout(prefs.getInt(PREFS_AUTO_CLOSE_AFTER, 0))

        // Enable/Disable warning views
        optionsBinding.sbWarningAfter.isEnabled = isChecked && enabled
        optionsBinding.tvWarningAfter.isEnabled = isChecked && enabled
        optionsBinding.tvWarningAfterValue.isEnabled = isChecked && enabled

        if (sendFirebaseUpdate)
            FirebaseDatabaseFunctions.sendAutoCloseOption(enabled, timeout, isChecked, warningTimeout )
    }

    private fun toggleAutoClose(isChecked: Boolean) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sendFirebaseUpdate = prefs.getBoolean(PREFS_AUTO_CLOSE_ENABLED, false) == AppState.garageData.autoCloseOptions.get()?.enabled

        prefs.edit().putBoolean(PREFS_AUTO_CLOSE_ENABLED, isChecked).apply()

        val warningTimeout = getWarningTimeout(prefs.getInt(PREFS_AUTO_CLOSE_WARN_AFTER, 0))
        val warningEnabled = prefs.getBoolean(PREFS_AUTO_CLOSE_WARN_ENABLED, false)
        val timeout = getTimeout(prefs.getInt(PREFS_AUTO_CLOSE_AFTER, 0))

        // Enable/Disable auto close views

        optionsBinding.sbCloseAfter.isEnabled = isChecked
        optionsBinding.tvCloseAfter.isEnabled = isChecked
        optionsBinding.tvCloseAfterValue.isEnabled = isChecked
        optionsBinding.tvWarningAfterValue.isEnabled = isChecked && warningEnabled
        optionsBinding.tvWarningAfter.isEnabled = isChecked && warningEnabled
        optionsBinding.sbWarningAfter.isEnabled = isChecked && warningEnabled
        optionsBinding.swWarnBeforeClosing.isEnabled = isChecked

        // Send firebase update
        if (sendFirebaseUpdate)
            FirebaseDatabaseFunctions.sendAutoCloseOption(isChecked, timeout, warningEnabled, warningTimeout)
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
    private val optionsChangedListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            val sharedPrefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val options = AppState.garageData.autoCloseOptions.get() ?: return

            if (sharedPrefs.getBoolean(PREFS_AUTO_CLOSE_ENABLED, false) != options.enabled) {
                Log.d(TAG, "optionsChangedListener - autoCloseEnabled has changed: ${options.enabled}")
                optionsBinding.swAutoClose.isChecked = options.enabled
            }

            if (sharedPrefs.getBoolean(PREFS_AUTO_CLOSE_WARN_AFTER, false) != options.warningEnabled) {
                Log.d(TAG, "optionsChangedListener - warningEnabled has changed: ${options.warningEnabled}")
                optionsBinding.swWarnBeforeClosing.isChecked = options.warningEnabled
            }

            if (sharedPrefs.getInt(PREFS_AUTO_CLOSE_WARN_AFTER, 0) != getProgress(options.warningTimeout)) {
                Log.d(TAG, "optionsChangedListener - warningTimeout has changed: ${options.warningTimeout}")
                optionsBinding.sbWarningAfter.progress = getProgress(options.warningTimeout)
            }

            if (sharedPrefs.getInt(PREFS_AUTO_CLOSE_AFTER, 0) != getProgress(options.timeout)) {
                Log.d(TAG, "optionsChangedListener - timeout has changed: ${options.timeout}")
                optionsBinding.sbCloseAfter.progress = getProgress((options.timeout))
            }
        }
    }

    private val autoCloseWarningListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            Log.d(TAG, "AutoCloseWarningListener - triggered!")

            if (AppState.garageData.autoCloseWarning.get()?.timeout ?: 0 == 0.toLong()){
                Log.w(TAG, "AutoCloseWarningListener - AutoCloseWarningSent was false!")
                return
            }

            if (flashbar != null) {
                Log.w(TAG, "AutoCloseWarningListener - Flashbar is already showing!")
                return
            }

            flashbar = Flashbar.Builder(this@MainActivity)
                .icon(R.drawable.ic_warning_yellow_24dp)
                .iconColorFilter(R.color.warningYellow)
                .showIcon()
                .title(R.string.auto_close_warning_title)
                .message(R.string.auto_close_warning_desc)
                .positiveActionText(R.string.cancel_auto_close)
                .positiveActionTapListener(object : Flashbar.OnActionTapListener {
                    override fun onActionTapped(bar: Flashbar) {
                        Log.d(TAG, "Flashbar - Sending auto close from flashbar")
                        FirebaseDatabaseFunctions.sendGarageAction(FirebaseDatabaseFunctions.ActionType.STOP_AUTO_CLOSE)
                        bar.dismiss()
                    }
                })
                .negativeActionText(R.string.dismiss)
                .negativeActionTapListener(object : Flashbar.OnActionTapListener {
                    override fun onActionTapped(bar: Flashbar) { bar.dismiss() }
                })
                .barDismissListener(flashbarDismissListener)
                .backgroundColor(getBackgroundColor())
                .titleAppearance(R.style.TextAppearance_MaterialComponents_Headline5)
                .messageAppearance(R.style.TextAppearance_MaterialComponents_Body2)
                .positiveActionTextAppearance(R.style.AppTheme_TextAppearance_Flashbar_Warning_Positive)
                .negativeActionTextAppearance(R.style.TextAppearance_MaterialComponents_Body1)
                .build()
            flashbar?.show()
        }
    }

    private val flashbarDismissListener = object : Flashbar.OnBarDismissListener {
        override fun onDismissProgress(bar: Flashbar, progress: Float) {}

        override fun onDismissed(bar: Flashbar, event: Flashbar.DismissEvent) {
            flashbar = null
        }

        override fun onDismissing(bar: Flashbar, isSwiped: Boolean) { }
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
            R.id.sbCloseAfter -> optionsBinding.tvCloseAfterValue.text = getTimeFromProgress(progress + 1)
            R.id.sbWarningAfter -> optionsBinding.tvWarningAfterValue.text = getTimeFromProgress(progress)
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
        val progress = optionsBinding.sbWarningAfter.progress
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(PREFS_AUTO_CLOSE_WARN_AFTER, progress).apply()

        // Update text view
        optionsBinding.tvWarningAfterValue.text = getTimeFromProgress(progress)

        // Get the new timeout (in milliseconds)
        val warningTimeout: Long = getWarningTimeout(progress)

        val enabled = prefs.getBoolean(PREFS_AUTO_CLOSE_ENABLED, false)
        val timeout = getTimeout(prefs.getInt(PREFS_AUTO_CLOSE_AFTER, 0))
        val warningEnabled = prefs.getBoolean(PREFS_AUTO_CLOSE_WARN_ENABLED, false)

        FirebaseDatabaseFunctions.sendAutoCloseOption(enabled, timeout, warningEnabled, warningTimeout)
    }

    private fun updateCloseAfter() {
        val progress = optionsBinding.sbCloseAfter.progress
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(PREFS_AUTO_CLOSE_AFTER, progress).apply()

        // Update text view
        optionsBinding.tvCloseAfterValue.text = getTimeFromProgress(progress+1)

        // Get the new timeout (in milliseconds)
        val timeout: Long = getTimeout(progress)

        val enabled = prefs.getBoolean(PREFS_AUTO_CLOSE_ENABLED, false)
        val warningTimeout = getWarningTimeout(prefs.getInt(PREFS_AUTO_CLOSE_WARN_AFTER, 0))
        val warningEnabled = prefs.getBoolean(PREFS_AUTO_CLOSE_WARN_ENABLED, false)

        FirebaseDatabaseFunctions.sendAutoCloseOption(enabled, timeout, warningEnabled, warningTimeout)
    }

    private fun getWarningTimeout(progress: Int) : Long  = (15 * progress * 60 * 1000).toLong()
    private fun getTimeout(progress: Int): Long = (15 * (progress + 1) * 60 * 1000).toLong()

    private fun getProgress(timeout: Long): Int = (timeout / (15 * 60 * 1000)).toInt()

}
