package com.example.stylista.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.stylista.R
import com.example.stylista.data.repository.OrderDetailsModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderAdapter(
    private val context: Context,
    private var orders: List<OrderDetailsModel>,
    private val onTrackClick: (OrderDetailsModel) -> Unit,
    private val onDetailsClick: (OrderDetailsModel) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    fun updateOrders(newOrders: List<OrderDetailsModel>) {
        this.orders = newOrders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_order_card, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]
        holder.bind(order)
    }

    override fun getItemCount(): Int = orders.size

    inner class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvOrderNumber: TextView = itemView.findViewById(R.id.tvOrderCardNumber)
        private val tvOrderDate: TextView = itemView.findViewById(R.id.tvOrderCardDate)
        private val tvOrderStatus: TextView = itemView.findViewById(R.id.tvOrderCardStatus)
        private val ivOrderThumb: ImageView = itemView.findViewById(R.id.ivOrderCardThumb)
        private val tvOrderItemsSummary: TextView = itemView.findViewById(R.id.tvOrderCardItemsSummary)
        private val tvOrderTotal: TextView = itemView.findViewById(R.id.tvOrderCardTotal)
        private val tvOrderPaymentMethod: TextView = itemView.findViewById(R.id.tvOrderCardPaymentMethod)
        private val btnTrack: Button = itemView.findViewById(R.id.btnOrderCardTrack)
        private val btnDetails: Button = itemView.findViewById(R.id.btnOrderCardDetails)

        fun bind(order: OrderDetailsModel) {
            tvOrderNumber.text = order.orderNumber

            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            tvOrderDate.text = sdf.format(Date(order.createdAt))

            // Format Status Badge
            val status = order.status.uppercase(Locale.getDefault())
            tvOrderStatus.text = status
            when (status) {
                "DELIVERED" -> {
                    tvOrderStatus.setTextColor(ContextCompat.getColor(context, R.color.success_green))
                    tvOrderStatus.setBackgroundColor(Color.parseColor("#E8F5E9"))
                }
                "CANCELLED" -> {
                    tvOrderStatus.setTextColor(ContextCompat.getColor(context, R.color.error_red))
                    tvOrderStatus.setBackgroundColor(Color.parseColor("#FFEBEE"))
                }
                "SHIPPED", "OUT_FOR_DELIVERY", "PACKED" -> {
                    tvOrderStatus.setTextColor(ContextCompat.getColor(context, R.color.cat_campus))
                    tvOrderStatus.setBackgroundColor(Color.parseColor("#FFF3E0"))
                }
                else -> { // PLACED, CONFIRMED
                    tvOrderStatus.setTextColor(ContextCompat.getColor(context, R.color.pastel_baby_blue_dark))
                    tvOrderStatus.setBackgroundColor(Color.parseColor("#EBF2FB"))
                }
            }

            // Items summary & thumbnail
            if (order.items.isNotEmpty()) {
                val first = order.items[0]
                val summary = if (order.items.size == 1) {
                    "${first.name} (Size: ${first.size}) × ${first.quantity}"
                } else {
                    "${first.name} (Size: ${first.size}) × ${first.quantity} + ${order.items.size - 1} more items"
                }
                tvOrderItemsSummary.text = summary

                if (first.imageUrl.isNotBlank()) {
                    Glide.with(context)
                        .load(first.imageUrl)
                        .centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.ic_placeholder_product)
                        .error(R.drawable.ic_placeholder_product)
                        .into(ivOrderThumb)
                } else {
                    ivOrderThumb.setImageResource(R.drawable.ic_placeholder_product)
                }
            } else {
                tvOrderItemsSummary.text = "Order selection"
                ivOrderThumb.setImageResource(R.drawable.ic_placeholder_product)
            }

            // Total & Payment
            val formattedTotal = NumberFormat.getNumberInstance(Locale.US).format(order.totalAmount)
            tvOrderTotal.text = "Total: ₹$formattedTotal"
            tvOrderPaymentMethod.text = order.paymentMethod

            // Actions
            btnTrack.setOnClickListener { onTrackClick(order) }
            btnDetails.setOnClickListener { onDetailsClick(order) }
            itemView.setOnClickListener { onDetailsClick(order) }
        }
    }
}
