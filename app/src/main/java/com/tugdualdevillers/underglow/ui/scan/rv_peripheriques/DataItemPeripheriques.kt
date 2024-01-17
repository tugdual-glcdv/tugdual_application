package com.tugdualdevillers.underglow.ui.scan.rv_peripheriques

import android.bluetooth.BluetoothDevice

//data class DataItemPeripheriques(val title: String, var subtitle: String, val icon: Int, val onClick: (() -> Unit))


data class DataItemPeripheriques(val title: String?, var mac: String?, var device: BluetoothDevice?, val icon: Int, val onClick: (() -> Unit)) {
    override fun equals(other: Any?): Boolean {
        // On compare les MAC, pour ne pas ajouté deux fois le même device dans la liste.
        return other is DataItemPeripheriques && other.mac == this.mac
    }
}