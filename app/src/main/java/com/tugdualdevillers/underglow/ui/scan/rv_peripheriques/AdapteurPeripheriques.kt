package com.tugdual.yunikon.ui.ble_scan.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tugdualdevillers.underglow.R
import com.tugdualdevillers.underglow.ui.scan.rv_peripheriques.DataItemPeripheriques

class AdapteurPeripheriques(private val deviceList: MutableList<DataItemPeripheriques>, private val onClick: ((selectedDevice: String) -> Unit)? = null) : RecyclerView.Adapter<AdapteurPeripheriques.ViewHolder>() {

    // Retourne une « vue » / « layout » pour chaque élément de la liste
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_list_peripheriques, parent, false)
        return ViewHolder(view)
    }

    // Connect la vue ET la données
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.showItem(deviceList[position], onClick)
    }

    // Comment s'affiche ma vue
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun showItem(device: DataItemPeripheriques, onClick: ((selectedDevice: String) -> Unit)? = null) {
            itemView.findViewById<TextView>(R.id.textViewTitle).text = device.title
            itemView.findViewById<TextView>(R.id.textViewSubtitle).text = device.mac
            itemView.findViewById<ImageView>(R.id.imageViewIcon).setImageResource(device.icon)
            itemView.findViewById<Button>(R.id.buttonConnect).setOnClickListener { device.onClick() }
        }
    }


    override fun getItemCount(): Int {
        return deviceList.size
    }

}