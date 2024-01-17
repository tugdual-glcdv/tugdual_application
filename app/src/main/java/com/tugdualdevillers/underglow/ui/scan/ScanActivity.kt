package com.tugdualdevillers.underglow.ui.scan

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tugdual.yunikon.ui.ble_scan.adapter.AdapteurPeripheriques
import com.tugdualdevillers.underglow.R
import com.tugdualdevillers.underglow.data.BluetoothLEManager
import com.tugdualdevillers.underglow.data.LocalPreferences
import com.tugdualdevillers.underglow.ui.light.LightActivity
import com.tugdualdevillers.underglow.ui.scan.rv_peripheriques.DataItemPeripheriques

class ScanActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        findViewById<Button>(R.id.buttonReScan).setOnClickListener{
            if(verifyPermissions()){
                setupBLE()
            }
        }

        // Vérifier si l'intent contient la clé "lastAddress"
        if (intent.hasExtra("lastAddress")) {
            // Si la clé est présente, récupérez la valeur
            val receivedAdresse = intent.getStringExtra("lastAddress")

            setupRecycler()
            setupBLE()

            val lastDevice: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(receivedAdresse)
            BluetoothLEManager.setCurrentDevice(lastDevice)
            connectToCurrentDevice()
        } else {
            // Si la clé n'est pas présente, cela signifie qu'il n'y a pas de paramètre

            if (!hasPermission()){
                askForPermission()
            }
            setupRecycler()
            setupBLE()
        }
    }

    override fun onResume() {
        super.onResume()

        bleDevicesFoundList.clear()
        findViewById<RecyclerView>(R.id.recyclerViewPeripheriques).adapter?.notifyDataSetChanged()
        findViewById<TextView>(R.id.textViewNoDeviceDetected).visibility = View.VISIBLE

        if(verifyPermissions()){
            // Lancer suite => Activation BLE + Lancer Scan
            setupBLE()
        }
    }

    private fun verifyPermissions(): Boolean {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            // Test si le téléphone est compatible BLE, si c'est pas le cas, on finish() l'activity
            Toast.makeText(this, getString(R.string._permission_toast_not_compatible_BLE), Toast.LENGTH_SHORT).show()
            finish()
        } else if (hasPermission() && locationServiceEnabled()) {
            // tout est ok, on a bien les permissions
            return true
        } else if(!hasPermission()) {
            // On demande la permission
            askForPermission()
        } else {
            // On demande d'activer la localisation
            // Idéalement on demande avec un activité.
            // À vous de me proposer mieux (Une activité, une dialog, etc)
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        return false
    }

    // Gestion du Bluetooth
    // L'Adapter permettant de se connecter
    private var bluetoothAdapter: BluetoothAdapter? = null

    // La connexion actuellement établie
    //private var currentBluetoothGatt: BluetoothGatt? = null

    // « Interface système nous permettant de scanner »
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    // Parametrage du scan BLE
    private val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

    // On ne retourne que les « Devices » proposant le bon UUID
    private var scanFilters: List<ScanFilter> = arrayListOf(
        ScanFilter.Builder().setServiceUuid(ParcelUuid(BluetoothLEManager.DEVICE_UUID)).build()
    )

    // Variable de fonctionnement
    private var mScanning = false
    private val handler = Handler(Looper.getMainLooper())

    // DataSource de notre adapter.
    private val bleDevicesFoundList = arrayListOf<DataItemPeripheriques>() //mutableListOf

    private val PERMISSION_REQUEST_LOCATION = 99

    /**
     * Gère l'action après la demande de permission.
     * 2 cas possibles :
     * - Réussite 🎉.
     * - Échec (refus utilisateur).
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && locationServiceEnabled()) {
                // Permission OK & service de localisation actif => Nous pouvons lancer l'initialisation du BLE.
                // En appelant la méthode setupBLE(), La méthode setupBLE() va initialiser le BluetoothAdapter et lancera le scan.
            } else if (!locationServiceEnabled()) {
                // Inviter à activer la localisation
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } else {
                // Permission KO => Gérer le cas d'erreur (permission refusé)
                MaterialAlertDialogBuilder(this)
                    .setTitle(resources.getString(R.string._permission_required_dialog_title))
                    .setMessage(resources.getString(R.string._permission_required_dialog_message))
                    .setNegativeButton(resources.getString(R.string._permission_required_dialog_negative)) { dialog, which ->
                        finish()
                    }
                    .setPositiveButton(resources.getString(R.string._permission_required_dialog_positive)) { dialog, which ->
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        })
                    }
                    .show()
            }
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
     * Demande de la permission (ou des permissions) à l'utilisateur.
     * Sur Android 11, il faut la permission « BLUETOOTH_CONNECT » et « BLUETOOTH_SCAN »
     * Sur Android 10 et inférieur, il faut la permission « ACCESS_FINE_LOCATION » qui permet de scanner en BLE
     */
    private fun askForPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_LOCATION)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), PERMISSION_REQUEST_LOCATION)
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
     * La méthode « registerForActivityResult » permet de gérer le résultat d'une activité.
     * Ce code est appelé à chaque fois que l'utilisateur répond à la demande d'activation du Bluetooth (visible ou non)
     * Si l'utilisateur accepte et donc que le BLE devient disponible, on lance le scan.
     * Si l'utilisateur refuse, on affiche un message d'erreur (Toast).
     */
    val registerForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            // Le Bluetooth est activé, on lance le scan
            scanLeDevice()
        } else {
            // Bluetooth non activé, vous DEVEZ gérer ce cas autrement qu'avec un Toast.
            Toast.makeText(this, "Bluetooth non activé", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Récupération de l'adapter Bluetooth & vérification si celui-ci est actif.
     * Si il n'est pas actif, on demande à l'utilisateur de l'activer. Dans ce cas, au résultat le code présent dans « registerForResult » sera appelé.
     * Si il est déjà actif, on lance le scan.
     */
    @SuppressLint("MissingPermission")
    private fun setupBLE() {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager?)?.let { bluetoothManager ->
            bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter != null && !bluetoothManager.adapter.isEnabled) {
                registerForResult.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                scanLeDevice()
            }
        }
    }

    // Le scan va durer 10 secondes seulement, sauf si vous passez une autre valeur comme paramètre.
    @SuppressLint("MissingPermission")
    private fun scanLeDevice(scanPeriod: Long = 10000) {

        if (!mScanning) {
            bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

            // On vide la liste qui contient les devices actuellement trouvés
            bleDevicesFoundList.clear()
            // Indique à l'adapter que nous avons vidé la liste
            findViewById<RecyclerView>(R.id.recyclerViewPeripheriques).adapter?.notifyDataSetChanged()

            findViewById<TextView>(R.id.textViewNoDeviceDetected).visibility = View.VISIBLE

            // Évite de scanner en double
            mScanning = true

            // On lance une tache qui durera « scanPeriod » à savoir donc de base 10 secondes
            handler.postDelayed({
                mScanning = false
                bluetoothLeScanner?.stopScan(leScanCallback)
                Toast.makeText(this, getString(R.string._scan_activity_toast_scan_ended), Toast.LENGTH_SHORT).show()
            }, scanPeriod)

            // On lance le scan
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, leScanCallback)
        }
    }

    // Callback appelé à chaque périphérique trouvé.
    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val device = DataItemPeripheriques(result.device.name, result.device.address, result.device, R.drawable.bluetooth) {
                //Toast.makeText(this@ScanActivity, "connection to : ${result.device.address}", Toast.LENGTH_SHORT).show()
                BluetoothLEManager.setCurrentDevice(result.device)
                connectToCurrentDevice()

                /*val intentC = Intent(this@ScanActivity, LightActivity::class.java).apply {
                    putExtra("DEVICE_TO_CONNECT_ID", result.device)
                }
                startActivity(intentC)*/
            }


            if (!device.title.isNullOrBlank() && !bleDevicesFoundList.contains(device)) {
                bleDevicesFoundList.add(device)
                findViewById<TextView>(R.id.textViewNoDeviceDetected).visibility = View.INVISIBLE

                //Toast.makeText(this@ScanActivity, "${device.title} ajouté", Toast.LENGTH_SHORT).show()

                // Indique à l'adapter que nous avons ajouté un élément, il va donc se mettre à jour
                findViewById<RecyclerView>(R.id.recyclerViewPeripheriques).adapter?.notifyItemInserted(bleDevicesFoundList.size - 1)
            }
        }
    }

    /*
     * Méthode qui initialise le recycler view.
     */
    private fun setupRecycler() {
        val rvDevice = findViewById<RecyclerView>(R.id.recyclerViewPeripheriques) // Récupération du RecyclerView présent dans le layout
        rvDevice.layoutManager = LinearLayoutManager(this) // Définition du LayoutManager, Comment vont être affichés les éléments, ici en liste
        rvDevice.adapter = AdapteurPeripheriques(bleDevicesFoundList)
    }


    @SuppressLint("MissingPermission")
    private fun connectToCurrentDevice() {
        BluetoothLEManager.currentDevice()?.let { device ->
            Toast.makeText(this, getString(R.string._scan_activity_toast_connecting, device), Toast.LENGTH_SHORT).show()

            BluetoothLEManager.currentBluetoothGatt = device.connectGatt(
                this,
                false,
                BluetoothLEManager.GattCallback(
                    onConnect = {
                        // On indique à l'utilisateur que nous sommes correctement connecté
                        runOnUiThread {
                            // Nous sommes connecté au device, on active les notifications pour être notifié si la LED change d'état.
                            // On sauvegarde dans les « LocalPréférence » de l'application le nom du dernier préphérique
                            // sur lequel nous nous sommes connecté
                            val localPreferences = LocalPreferences.getInstance(this)
                            localPreferences.lastConnectedDeviceName(device.name)
                            localPreferences.lastConnectedDeviceAddress(device.address)

                            val intentC = Intent(this@ScanActivity, LightActivity::class.java)
                            startActivity(intentC)
                        }
                    },
                    onNotify = { runOnUiThread {
                        when (it.uuid) {
                            BluetoothLEManager.CHARACTERISTIC_NOTIFY_STATE -> {
                                handleToggleLedNotificationUpdate(it)
                            }
                            BluetoothLEManager.CHARACTERISTIC_GET_COUNT -> {
                                handleCountLedChangeNotificationUpdate(it)
                            }
                            BluetoothLEManager.CHARACTERISTIC_GET_WIFI_SCAN -> {
                                handleOnNotifyNotificationReceived(it)
                            }
                        }
                    } },
                    onDisconnect = { runOnUiThread {
                        disconnectFromCurrentDevice()
                        Toast.makeText(this@ScanActivity, getString(R.string._light_activity_toast_disconnected), Toast.LENGTH_SHORT).show()
                    } })
            )
        }
    }

    private fun handleToggleLedNotificationUpdate(characteristic: BluetoothGattCharacteristic) {
        BluetoothLEManager.setIsLedOn(characteristic.getStringValue(0).equals("1", ignoreCase = true))
    }

    private fun handleCountLedChangeNotificationUpdate(characteristic: BluetoothGattCharacteristic) {
        BluetoothLEManager.setNbSwitch(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0))
    }

    private fun handleOnNotifyNotificationReceived(characteristic: BluetoothGattCharacteristic) {
        // Vous pouvez ici récupérer la liste des réseaux WiFi disponibles et les afficher dans une liste.
        // Vous pouvez utiliser un RecyclerView pour afficher la liste des réseaux WiFi disponibles.
    }

    /**
     * On demande la déconnexion du device
     */
    @SuppressLint("MissingPermission")
    private fun disconnectFromCurrentDevice() {
        BluetoothLEManager.currentBluetoothGatt?.disconnect()
        BluetoothLEManager.setCurrentDevice(null)
    }

}