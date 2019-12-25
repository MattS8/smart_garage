package com.ms8.smartgaragedoor

import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.databinding.DataBindingUtil
import androidx.databinding.Observable
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.ms8.smartgaragedoor.databinding.ActivitySplashBinding
import java.lang.Exception


class SplashScreen : AppCompatActivity(), View.OnClickListener {

    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var auth : FirebaseAuth

    private lateinit var binding : ActivitySplashBinding

    private var progressView : AnimatedVectorDrawableCompat? = null

    private var splashIconRaised = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_splash)

        savedInstanceState?.let { state ->
            splashIconRaised = state.getBoolean(STATE_ICON_RAISED, splashIconRaised)
        }

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        progressView = AnimatedVectorDrawableCompat.create(this, R.drawable.av_progress)
        progressView?.registerAnimationCallback(garageProgressViewCallback)
    }

    override fun onStart() {
        super.onStart()

        updateUI(auth.currentUser)
    }

    override fun onResume() {
        super.onResume()

        if (auth.currentUser != null) {
            if (AppState.garageData.status.get() != null)
                nextActivity()
            else
                fetchGarageStatus()
        }
    }

    override fun onPause() {
        super.onPause()

        if (auth.currentUser != null) {
            AppState.garageData.status.removeOnPropertyChangedCallback(statusListener)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ICON_RAISED, splashIconRaised)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btnSignIn -> signIn()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                firebaseAuthWithGoogle(task!!.getResult(ApiException::class.java)!!)
            } catch (e : Exception) {
                Log.e("SplashScreen", "onActivityResult - ${e.message}")
                updateUI(null)
            }
        }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.id)

        startProgressView()

        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")

                    updateUI(auth.currentUser)

                    fetchGarageStatus()
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)

                    Snackbar.make(binding.root, "Authentication Failed.", Snackbar.LENGTH_SHORT)
                        .show()

                    updateUI(null)

                    binding.progressBar.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
    }

    private fun startProgressView() {
        binding.progressBar.setImageDrawable(progressView)
        progressView?.start()
        binding.progressBar.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(AccelerateInterpolator())
            .start()
    }

    private fun updateUI(user : FirebaseUser?) {
        if (!splashIconRaised) {
            splashIconRaised = true
            binding.imgSplashIcon.animate()
                .translationYBy(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        if (user == null) {
            binding.btnSignIn.apply {
                setOnClickListener {v -> this@SplashScreen.onClick(v)  }
                animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }
        } else {
            binding.btnSignIn.apply {
                setOnClickListener {  }
                animate()
                    .alpha(0f)
                    .setDuration(300)
                    .start()
            }
        }
    }

    private fun fetchGarageStatus() {
        if (binding.progressBar.alpha != 1f)
            startProgressView()

        FirebaseDatabaseFunctions.listenForGarageStatus()
        AppState.garageData.status.addOnPropertyChangedCallback(statusListener)
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    private val statusListener = object : Observable.OnPropertyChangedCallback() {
        override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
            if (AppState.garageData.status.get() != null) {
                AppState.garageData.status.removeOnPropertyChangedCallback(this)
                nextActivity()
            }
        }
    }

    private val garageProgressViewCallback = object : Animatable2Compat.AnimationCallback() {
        override fun onAnimationEnd(drawable: Drawable?) {
            binding.progressBar.post { progressView?.start() }
        }
    }

    private fun nextActivity() {
       startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    companion object {
        const val TAG = "SplashActivity"
        const val STATE_ICON_RAISED = "ICON_LIFTED"

        const val RC_SIGN_IN = 88
    }
}
