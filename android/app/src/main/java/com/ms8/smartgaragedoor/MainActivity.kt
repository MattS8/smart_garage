package com.ms8.smartgaragedoor

import android.content.Context
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
import com.google.firebase.iid.FirebaseInstanceId
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.ms8.smartgaragedoor.FirebaseMessageService.Companion.NOTIFICATION_TYPE
import com.ms8.smartgaragedoor.FirebaseMessageService.Companion.TYPE_AUTO_CLOSE_WARNING
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

    private fun setupOptions(themeStr: String?) {
        optionsBinding  = DrawerAutoCloseOptionsBinding.inflate(layoutInflater)
        val options = AppState.garageData.autoCloseOptions.get() ?: FirebaseDatabaseFunctions.AutoCloseOptions()
        optionsBinding.swAutoClose.apply {
            isChecked = options.enabled
            setOnCheckedChangeListener(this@MainActivity)
        }
        optionsBinding.swWarnBeforeClosing.apply {
            isChecked = options.warningEnabled
            setOnCheckedChangeListener(this@MainActivity)
        }
        optionsBinding.sbCloseAfter.apply {
            progress = getProgress(options.timeout)
            setOnSeekBarChangeListener(this@MainActivity)
        }
        optionsBinding.sbWarningAfter.apply {
            progress = getProgress(options.warningTimeout)
            setOnSeekBarChangeListener(this@MainActivity)
        }
        optionsBinding.swTheme.apply {
            text = if (themeStr == THEME_DARK) getString(R.string.dark_theme ) else getString(R.string.light_theme)
            isChecked = themeStr == THEME_DARK
            setOnCheckedChangeListener(this@MainActivity)
        }

        val autoCloseEnabled = options.enabled
        val warningEnabled = options.warningEnabled

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

        FirebaseInstanceId.getInstance().instanceId.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(GarageWidget.TAG, "getInstanceId failed: ${task.exception}")
                return@addOnCompleteListener
            }

            val token = task.result?.token ?: return@addOnCompleteListener

            FirebaseDatabaseFunctions.addTokenToGarage(token)
        }

        // Check if started from FCM Notification
        intent.extras?.getString(NOTIFICATION_TYPE)?.let {
            handleFCMNotification(it)
        }
    }

    private fun handleFCMNotification(type: String) {
        when (type) {
            TYPE_AUTO_CLOSE_WARNING -> showAutoCloseWarningFlashbar()
            else -> Log.e(TAG, "Unknown FCM notification type")
        }
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

        // Listen for changes to AppState
        AppState.garageData.status.addOnPropertyChangedCallback(garageStatusListener)
        AppState.errorData.garageStatusError.addOnPropertyChangedCallback(garageStatusErrorListener)
        AppState.garageData.autoCloseOptions.addOnPropertyChangedCallback(optionsChangedListener)
        AppState.garageData.autoCloseWarning.addOnPropertyChangedCallback(autoCloseWarningListener)

        updateStatusUI()
        updateOptionsUI()

        // Ensure backend listeners are running
        FirebaseDatabaseFunctions.listenForGarageStatus()
        FirebaseDatabaseFunctions.listenForOptionChanges()
    }

    override fun onPause() {
        super.onPause()
        AppState.appData.appInForeground = false

        // Remove to prevent leaks
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
        val options = AppState.garageData.autoCloseOptions.get() ?: return
        val sendToFirebase = options.warningEnabled != isChecked

        options.warningEnabled = isChecked
        AppState.garageData.autoCloseOptions.set(options)

        // Enable/Disable warning views
        optionsBinding.sbWarningAfter.isEnabled = options.warningEnabled && options.enabled
        optionsBinding.tvWarningAfter.isEnabled = options.warningEnabled && options.enabled
        optionsBinding.tvWarningAfterValue.isEnabled = options.warningEnabled && options.enabled

        if (sendToFirebase)
            FirebaseDatabaseFunctions.sendAutoCloseOption(options)
    }

    private fun toggleAutoClose(isChecked: Boolean) {
        val options = AppState.garageData.autoCloseOptions.get() ?: return
        val sendToFirebase = options.enabled != isChecked
        options.enabled = isChecked
        AppState.garageData.autoCloseOptions.set(options)

        // Enable/Disable auto close views
        optionsBinding.sbCloseAfter.isEnabled = options.enabled
        optionsBinding.tvCloseAfter.isEnabled = options.enabled
        optionsBinding.tvCloseAfterValue.isEnabled = options.enabled
        optionsBinding.tvWarningAfterValue.isEnabled = options.enabled && options.warningEnabled
        optionsBinding.tvWarningAfter.isEnabled = options.enabled && options.warningEnabled
        optionsBinding.sbWarningAfter.isEnabled = options.enabled && options.warningEnabled
        optionsBinding.swWarnBeforeClosing.isEnabled = options.enabled

        // Send firebase update
        if (sendToFirebase)
            FirebaseDatabaseFunctions.sendAutoCloseOption(options)
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

    private fun updateOptionsUI() {
        val options = AppState.garageData.autoCloseOptions.get() ?: return
        if (optionsBinding.swAutoClose.isChecked != options.enabled) {
            Log.d(TAG, "optionsChangedListener - autoCloseEnabled has changed: ${options.enabled}")
            optionsBinding.swAutoClose.isChecked = options.enabled
        }

        if (optionsBinding.swWarnBeforeClosing.isChecked != options.warningEnabled) {
            Log.d(TAG, "optionsChangedListener - warningEnabled has changed: ${options.warningEnabled}")
            optionsBinding.swWarnBeforeClosing.isChecked = options.warningEnabled
        }

        if (optionsBinding.sbWarningAfter.progress != getProgress(options.warningTimeout)) {
            Log.d(TAG, "optionsChangedListener - warningTimeout has changed: ${options.warningTimeout}")
            optionsBinding.sbWarningAfter.progress = getProgress(options.warningTimeout)
        }

        if (getTimeout(optionsBinding.sbCloseAfter.progress) != options.timeout) {
            optionsBinding.sbCloseAfter.progress = getProgress((options.timeout))
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

    private val autoCloseWarningListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            Log.d(TAG, "AutoCloseWarningListener - triggered!")

            if (AppState.garageData.autoCloseWarning.get() == null)
                return

            AppState.garageData.autoCloseWarning.set(null)

            if (flashbar != null) {
                Log.w(TAG, "AutoCloseWarningListener - Flashbar is already showing!")
                return
            }

            showAutoCloseWarningFlashbar()
        }
    }

    private val garageProgressViewCallback = object : Animatable2Compat.AnimationCallback() {
        override fun onAnimationEnd(drawable: Drawable?) {
            binding.progGarageStatus.post { garageProgressDrawable?.start() }
        }
    }
    private val optionsChangedListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            updateOptionsUI()
        }
    }

    private fun showAutoCloseWarningFlashbar() {
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
        val options = AppState.garageData.autoCloseOptions.get() ?: return
        // Get the new timeout (in milliseconds)
        val warningTimeout: Long = getWarningTimeout(progress)

        options.warningTimeout = warningTimeout

        // Update text view
        optionsBinding.tvWarningAfterValue.text = getTimeFromProgress(progress)


        FirebaseDatabaseFunctions.sendAutoCloseOption(options)
    }

    private fun updateCloseAfter() {
        val progress = optionsBinding.sbCloseAfter.progress
        val options = AppState.garageData.autoCloseOptions.get() ?: return
        // Get the new timeout (in milliseconds)
        val timeout: Long = getTimeout(progress)

        options.timeout = timeout

        // Update text view
        optionsBinding.tvCloseAfterValue.text = getTimeFromProgress(progress+1)

        FirebaseDatabaseFunctions.sendAutoCloseOption(options)
    }

    private fun getWarningTimeout(progress: Int) : Long  = (15 * progress * 60 * 1000).toLong()
    private fun getTimeout(progress: Int): Long = (15 * (progress + 1) * 60 * 1000).toLong()

    private fun getProgress(timeout: Long): Int = (timeout / (15 * 60 * 1000)).toInt()

}
