package com.tugdualdevillers.underglow.ui.light

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tugdualdevillers.underglow.R
import com.tugdualdevillers.underglow.data.BluetoothLEManager


class LightActivity : AppCompatActivity() {
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_light)


        val deviceToConnect = BluetoothLEManager.currentDevice()
        enableListenBleNotify()


        val imageViewNeon = findViewById<ImageView>(R.id.imageViewNeon)
        val textDeviceConnected = findViewById<TextView>(R.id.textViewConnectedTo)
        val textNbSwitch = findViewById<TextView>(R.id.textViewNbAllumages)

        imageViewNeon.visibility = View.INVISIBLE
        BluetoothLEManager.setListener(object : BluetoothLEManager.ChangeListener {
            override fun onCurrentDeviceChange() {
                if(BluetoothLEManager.currentDevice() == null){
                    BluetoothLEManager.currentBluetoothGatt?.disconnect()
                    BluetoothLEManager.setListener(null)
                    BluetoothLEManager.setIsLedOn(false)
                    BluetoothLEManager.setNbSwitch(0)
                    finish()
                }
            }
            override fun onIsLedOnChange() {
                imageViewNeon.visibility = if (BluetoothLEManager.isLedOn()) View.VISIBLE else View.INVISIBLE
            }
            override fun onNbSwitchChange() {
                textNbSwitch.text = getString(R.string._light_activity_text_nb_switch, BluetoothLEManager.nbSwitch())
            }
        })


        // initialisation des bouttons
        findViewById<Button>(R.id.buttonOnOff).setOnClickListener{
            toggleLed()
        }
        findViewById<Button>(R.id.buttonClignoter).setOnClickListener{
            sendAnimation()
        }
        findViewById<Button>(R.id.buttonDisconnect).setOnClickListener{
            disconnectFromCurrentDevice()
        }

        // initialisation des textes
        if (deviceToConnect != null) {
            textDeviceConnected.text = getString(R.string._light_activity_text_connected_to, deviceToConnect.name)
        }
        textNbSwitch.text = getString(R.string._light_activity_text_nb_switch, BluetoothLEManager.nbSwitch())
    }


    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()

        if (hasPermission()
            && locationServiceEnabled()
            && packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
            && BluetoothLEManager.currentDevice() != null) {

        }else {
            BluetoothLEManager.currentBluetoothGatt?.disconnect()
            BluetoothLEManager.setListener(null)
            BluetoothLEManager.setIsLedOn(false)
            BluetoothLEManager.setNbSwitch(0)
            finish()
        }
    }

    private fun locationServiceEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // This is new method provided in API 28
            val lm = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.isLocationEnabled
        } else {
            // This is Deprecated in API 28
            val mode = Settings.Secure.getInt(this.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF)
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }


    /**
     * Permet de vérifier si l'application possede la permission « Localisation ». OBLIGATOIRE pour scanner en BLE
     * Sur Android 11, il faut la permission « BLUETOOTH_CONNECT » et « BLUETOOTH_SCAN »
     * Sur Android 10 et inférieur, il faut la permission « ACCESS_FINE_LOCATION » qui permet de scanner en BLE
     */
    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        }
    }



    /**
     * Récupération de « service » BLE (via UUID) qui nous permettra d'envoyer / recevoir des commandes
     */
    private fun getMainDeviceService(): BluetoothGattService? {
        return BluetoothLEManager.currentBluetoothGatt?.let { bleGatt ->
            val service = bleGatt.getService(BluetoothLEManager.DEVICE_UUID)
            service?.let {
                return it
            } ?: run {
                Toast.makeText(this, getString(R.string._light_activity_toast_uuid_not_found), Toast.LENGTH_SHORT).show()
                return null;
            }
        } ?: run {
            Toast.makeText(this, getString(R.string._light_activity_toast_not_connected), Toast.LENGTH_SHORT).show()
            return null
        }
    }

    /**
     * On change l'état de la LED (via l'UUID de toggle)
     */
    @SuppressLint("MissingPermission")
    private fun toggleLed() {
        getMainDeviceService()?.let { service ->
            val toggleLed = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_TOGGLE_LED_UUID)
            toggleLed.setValue("1")
            BluetoothLEManager.currentBluetoothGatt?.writeCharacteristic(toggleLed)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableListenBleNotify() {
        getMainDeviceService()?.let { service ->
            //Toast.makeText(this, "getString(R.string.enable_ble_notifications)", Toast.LENGTH_SHORT).show()
            // Indique que le GATT Client va écouter les notifications sur le charactérisque
            val notificationStatus = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_NOTIFY_STATE)
            val notificationLedCount = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_GET_COUNT)
            val wifiScan = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_GET_WIFI_SCAN)

            BluetoothLEManager.currentBluetoothGatt?.setCharacteristicNotification(notificationStatus, true)
            BluetoothLEManager.currentBluetoothGatt?.setCharacteristicNotification(notificationLedCount, true)
            BluetoothLEManager.currentBluetoothGatt?.setCharacteristicNotification(wifiScan, true)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendAnimation() {
        getMainDeviceService()?.let { service ->
            val toggleLed = service.getCharacteristic(BluetoothLEManager.CHARACTERISTIC_TOGGLE_LED_UUID)
            toggleLed.setValue("101010101111011111000010101010")
            BluetoothLEManager.currentBluetoothGatt?.writeCharacteristic(toggleLed)
        }
    }







    /**
     * On demande la déconnexion du device
     */
    @SuppressLint("MissingPermission")
    private fun disconnectFromCurrentDevice() {
        BluetoothLEManager.currentBluetoothGatt?.disconnect()
        BluetoothLEManager.setCurrentDevice(null)
        BluetoothLEManager.setListener(null)
        BluetoothLEManager.setIsLedOn(false)
        BluetoothLEManager.setNbSwitch(0)
        finish()
    }

    override fun onDestroy(){
        super.onDestroy()
        disconnectFromCurrentDevice()
        finish()
    }
}