package com.tugdualdevillers.underglow.ui.main

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import androidx.core.content.ContextCompat
import com.tugdualdevillers.underglow.R
import com.tugdualdevillers.underglow.data.LocalPreferences
import com.tugdualdevillers.underglow.ui.scan.ScanActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.enterTransition = null

        findViewById<Button>(R.id.buttonScan).setOnClickListener{
            startActivity(Intent(this, ScanActivity::class.java))
        }



        val localPreferences = LocalPreferences.getInstance(this)
        val buttonReConnect = findViewById<Button>(R.id.buttonConnect)

        val lastAddress = localPreferences.lastConnectedDeviceAddress()

        if(lastAddress!=null){

            buttonReConnect.setTextColor(getColor(R.color.text_on_light_bg))
            buttonReConnect.backgroundTintList = ContextCompat.getColorStateList(this, R.color.bg_light)

            buttonReConnect.text = getString(R.string._main_activity_button_reconnect_active, localPreferences.lastConnectedDeviceName())

            buttonReConnect.setOnClickListener{
                startActivity(Intent(this, ScanActivity::class.java))
                val intent = Intent(this, ScanActivity::class.java)
                intent.putExtra("lastAddress", lastAddress)
                startActivity(intent)
            }
        }

    }

}