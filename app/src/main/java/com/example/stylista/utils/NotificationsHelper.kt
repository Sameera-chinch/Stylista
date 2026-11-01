package com.example.stylista.utils

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.stylista.activities.CampusMapActivity
import com.example.stylista.activities.OrderDetailsActivity
import com.example.stylista.R
import com.example.stylista.activities.TrackOrderActivity
import com.example.stylista.repositories.OrderRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.stylista.activities.*
import com.example.stylista.adapters.*
import com.example.stylista.chatbot.*
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*

object NotificationsHelper {

    fun show(activity: AppCompatActivity) {
        val dialog = BottomSheetDialog(activity)
        val sheetView = activity.layoutInflater.inflate(R.layout.bottom_sheet_notifications, null)
        dialog.setContentView(sheetView)

        val btnClose = sheetView.findViewById<TextView>(R.id.btnCloseNotifications)
        val pbLoading = sheetView.findViewById<ProgressBar>(R.id.pbNotificationsLoading)
        val container = sheetView.findViewById<LinearLayout>(R.id.containerDynamicNotifications)
        val layoutEmpty = sheetView.findViewById<View>(R.id.layoutNoNotifications)

        btnClose.setOnClickListener { dialog.dismiss() }

        val userId = UserPreferencesHelper.getActiveUserId(activity)
        pbLoading.visibility = View.VISIBLE

        activity.lifecycleScope.launch {
            try {
                container.removeAllViews()
                val inflater = LayoutInflater.from(activity)

                // 1. Fetch real orders
                val orders = OrderRepository.getOrders(activity, userId)

                // Render active or recent orders
                for (order in orders.take(3)) {
                    val card = inflater.inflate(R.layout.item_notification_card, container, false)
                    val ivIcon = card.findViewById<ImageView>(R.id.ivNotificationIcon)
                    val tvTitle = card.findViewById<TextView>(R.id.tvNotificationTitle)
                    val tvSub = card.findViewById<TextView>(R.id.tvNotificationSubtitle)
                    val tvAction = card.findViewById<TextView>(R.id.tvNotificationAction)

                    val statusUpper = order.status.uppercase(Locale.getDefault())
                    val estDateStr = SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date(order.estimatedDeliveryDate))

                    when (statusUpper) {
                        "CANCELLED" -> {
                            ivIcon.setImageResource(R.drawable.ic_close)
                            ivIcon.setColorFilter(ContextCompat.getColor(activity, R.color.error_red))
                            tvTitle.text = "Order Cancelled: ${order.orderNumber}"
                            tvSub.text = "Your cancellation has been confirmed. Zero charges applied."
                            tvAction.text = "Details ›"
                            card.setOnClickListener {
                                dialog.dismiss()
                                activity.startActivity(Intent(activity, OrderDetailsActivity::class.java).apply {
                                    putExtra("order_number", order.orderNumber)
                                })
                            }
                        }
                        "DELIVERED" -> {
                            ivIcon.setImageResource(R.drawable.ic_check_circle)
                            ivIcon.setColorFilter(ContextCompat.getColor(activity, R.color.success_green))
                            tvTitle.text = "Order Delivered: ${order.orderNumber}"
                            tvSub.text = "Delivered to your address. Eligible for 7-day hassle-free exchange."
                            tvAction.text = "View ›"
                            card.setOnClickListener {
                                dialog.dismiss()
                                activity.startActivity(Intent(activity, OrderDetailsActivity::class.java).apply {
                                    putExtra("order_number", order.orderNumber)
                                })
                            }
                        }
                        else -> { // PLACED, CONFIRMED, PACKED, SHIPPED, OUT_FOR_DELIVERY
                            ivIcon.setImageResource(R.drawable.ic_local_shipping)
                            ivIcon.setColorFilter(ContextCompat.getColor(activity, R.color.pastel_baby_blue_dark))
                            tvTitle.text = "Order Status: $statusUpper"
                            tvSub.text = "Order #${order.orderNumber}. Expected delivery by $estDateStr."
                            tvAction.text = "Track Live ›"
                            card.setOnClickListener {
                                dialog.dismiss()
                                activity.startActivity(Intent(activity, TrackOrderActivity::class.java).apply {
                                    putExtra("order_number", order.orderNumber)
                                })
                            }
                        }
                    }
                    container.addView(card)
                }

                // 2. Active Promo Code Notification from StoreConfig
                val promoCard = inflater.inflate(R.layout.item_notification_card, container, false)
                val ivPromoIcon = promoCard.findViewById<ImageView>(R.id.ivNotificationIcon)
                val tvPromoTitle = promoCard.findViewById<TextView>(R.id.tvNotificationTitle)
                val tvPromoSub = promoCard.findViewById<TextView>(R.id.tvNotificationSubtitle)
                val tvPromoAction = promoCard.findViewById<TextView>(R.id.tvNotificationAction)

                ivPromoIcon.setImageResource(R.drawable.ic_star)
                ivPromoIcon.setColorFilter(ContextCompat.getColor(activity, R.color.badge_gold))
                tvPromoTitle.text = "Active Coupon: WELCOME50"
                tvPromoSub.text = "Get ₹50 flat discount on your checkout. Free shipping on orders above ₹${StoreConfig.freeDeliveryAbove}."
                tvPromoAction.text = "Active"
                container.addView(promoCard)

                // 3. Campus Boutique & Pickup Counter notification
                val mapCard = inflater.inflate(R.layout.item_notification_card, container, false)
                val ivMapIcon = mapCard.findViewById<ImageView>(R.id.ivNotificationIcon)
                val tvMapTitle = mapCard.findViewById<TextView>(R.id.tvNotificationTitle)
                val tvMapSub = mapCard.findViewById<TextView>(R.id.tvNotificationSubtitle)
                val tvMapAction = mapCard.findViewById<TextView>(R.id.tvNotificationAction)

                ivMapIcon.setImageResource(R.drawable.ic_location_on)
                ivMapIcon.setColorFilter(ContextCompat.getColor(activity, R.color.pastel_baby_blue_dark))
                tvMapTitle.text = "Campus Boutiques & Pickup"
                tvMapSub.text = "Locate Stylista boutique counters and pickup desks across campus."
                tvMapAction.text = "Map ›"
                mapCard.setOnClickListener {
                    dialog.dismiss()
                    activity.startActivity(Intent(activity, CampusMapActivity::class.java))
                }
                container.addView(mapCard)

                layoutEmpty.visibility = if (container.childCount == 0) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                layoutEmpty.visibility = View.VISIBLE
            } finally {
                pbLoading.visibility = View.GONE
            }
        }

        dialog.show()
    }
}
