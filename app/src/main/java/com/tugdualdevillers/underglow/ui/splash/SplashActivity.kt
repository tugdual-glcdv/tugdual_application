package com.tugdualdevillers.underglow.ui.splash

import android.app.ActivityOptions
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import com.tugdualdevillers.underglow.R
import com.tugdualdevillers.underglow.ui.main.MainActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        window.exitTransition = null

        val logoView = findViewById<View>(R.id.imageViewLogoU)

        Handler(Looper.getMainLooper()).postDelayed({

            val intent = Intent(this, MainActivity::class.java)
            val options = ActivityOptions.makeSceneTransitionAnimation(this, logoView, "SplashTransitionName")

            startActivity( intent, options.toBundle() )
            //finish()
            finishAfterTransition()

        }, 1000)
    }
}