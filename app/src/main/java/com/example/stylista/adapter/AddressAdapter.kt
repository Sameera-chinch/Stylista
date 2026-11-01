package com.example.stylista.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.stylista.R
import com.example.stylista.database.AddressEntity

class AddressAdapter(
    private var addresses: List<AddressEntity>,
    private val isSelectingMode: Boolean,
    private val onSelect: (AddressEntity) -> Unit,
    private val onSetDefault: (AddressEntity) -> Unit,
    private val onEdit: (AddressEntity) -> Unit,
    private val onDelete: (AddressEntity) -> Unit
) : RecyclerView.Adapter<AddressAdapter.AddressViewHolder>() {

    class AddressViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvAddressName)
        val tvType: TextView = itemView.findViewById(R.id.tvAddressType)
        val tvDefault: TextView = itemView.findViewById(R.id.tvDefaultBadge)
        val tvBody: TextView = itemView.findViewById(R.id.tvAddressBody)
        val tvPhone: TextView = itemView.findViewById(R.id.tvAddressPhone)
        val btnSelect: Button = itemView.findViewById(R.id.btnSelectAddress)
        val btnSetDefault: Button = itemView.findViewById(R.id.btnSetDefault)
        val btnEdit: TextView = itemView.findViewById(R.id.btnEditAddress)
        val btnDelete: TextView = itemView.findViewById(R.id.btnDeleteAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddressViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_address_card, parent, false)
        return AddressViewHolder(view)
    }

    override fun onBindViewHolder(holder: AddressViewHolder, position: Int) {
        val address = addresses[position]

        holder.tvName.text = address.name
        holder.tvType.text = address.type
        holder.tvBody.text = address.getFullAddressString()
        holder.tvPhone.text = "Phone: ${address.phone}"

        if (address.isDefault) {
            holder.tvDefault.visibility = View.VISIBLE
            holder.btnSetDefault.visibility = View.GONE
        } else {
            holder.tvDefault.visibility = View.GONE
            holder.btnSetDefault.visibility = View.VISIBLE
            holder.btnSetDefault.setOnClickListener { onSetDefault(address) }
        }

        if (isSelectingMode) {
            holder.btnSelect.visibility = View.VISIBLE
            holder.btnSelect.setOnClickListener { onSelect(address) }
        } else {
            holder.btnSelect.visibility = View.GONE
        }

        holder.btnEdit.setOnClickListener { onEdit(address) }
        holder.btnDelete.setOnClickListener { onDelete(address) }
    }

    override fun getItemCount(): Int = addresses.size

    fun updateList(newAddresses: List<AddressEntity>) {
        addresses = newAddresses
        notifyDataSetChanged()
    }
}
