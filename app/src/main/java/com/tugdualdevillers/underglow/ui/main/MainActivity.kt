package com.tugdualdevillers.underglow.ui.main

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.tugdualdevillers.underglow.ui.light.LightActivity
import com.tugdualdevillers.underglow.R
import com.tugdualdevillers.underglow.ui.scan.ScanActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.enterTransition = null

        findViewById<Button>(R.id.buttonScan).setOnClickListener{
            startActivity(Intent(this, ScanActivity::class.java))
        }

        findViewById<Button>(R.id.buttonConnect).setOnClickListener{
            startActivity(Intent(this, LightActivity::class.java))
        }

    }

}