package com.example.stylista

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.stylista.data.repository.OrderDetailsModel
import com.example.stylista.data.repository.OrderRepository
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackOrderActivity : AppCompatActivity() {

    private lateinit var tvOrderNumberTop: TextView
    private lateinit var tvCurrentStatusTitle: TextView
    private lateinit var tvEstimatedDate: TextView
    private lateinit var cardCancelled: MaterialCardView
    private lateinit var tvCancelledReason: TextView
    private lateinit var tvAddressSummary: TextView

    // Step 1: Placed
    private lateinit var ivDotPlaced: ImageView
    private lateinit var linePlacedToConfirmed: View
    private lateinit var tvTitlePlaced: TextView
    private lateinit var tvDatePlaced: TextView

    // Step 2: Confirmed
    private lateinit var ivDotConfirmed: ImageView
    private lateinit var lineConfirmedToPacked: View
    private lateinit var tvTitleConfirmed: TextView
    private lateinit var tvDateConfirmed: TextView

    // Step 3: Packed
    private lateinit var ivDotPacked: ImageView
    private lateinit var linePackedToShipped: View
    private lateinit var tvTitlePacked: TextView
    private lateinit var tvDatePacked: TextView

    // Step 4: Shipped
    private lateinit var ivDotShipped: ImageView
    private lateinit var lineShippedToOut: View
    private lateinit var tvTitleShipped: TextView
    private lateinit var tvDateShipped: TextView

    // Step 5: Out for Delivery
    private lateinit var ivDotOut: ImageView
    private lateinit var lineOutToDelivered: View
    private lateinit var tvTitleOut: TextView
    private lateinit var tvDateOut: TextView

    // Step 6: Delivered
    private lateinit var ivDotDelivered: ImageView
    private lateinit var tvTitleDelivered: TextView
    private lateinit var tvDateDelivered: TextView

    private var orderNumber: String = ""
    private var firestoreListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "TrackOrderActivity"
        private val STATUS_ORDER = listOf(
            "PLACED",
            "CONFIRMED",
            "PACKED",
            "SHIPPED",
            "OUT_FOR_DELIVERY",
            "DELIVERED"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_order)

        orderNumber = intent.getStringExtra("order_number") ?: ""

        initViews()
        loadInitialData()
        attachRealtimeListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
        firestoreListener = null
    }

    private fun initViews() {
        findViewById<ImageView>(R.id.btnBackTrackOrder).setOnClickListener { finish() }

        tvOrderNumberTop = findViewById(R.id.tvTrackOrderNumberTop)
        tvCurrentStatusTitle = findViewById(R.id.tvTrackCurrentStatusTitle)
        tvEstimatedDate = findViewById(R.id.tvTrackEstimatedDate)
        cardCancelled = findViewById(R.id.cardOrderCancelled)
        tvCancelledReason = findViewById(R.id.tvCancelledReason)
        tvAddressSummary = findViewById(R.id.tvTrackAddressSummary)

        tvOrderNumberTop.text = orderNumber

        // Steps
        ivDotPlaced = findViewById(R.id.ivStepDotPlaced)
        linePlacedToConfirmed = findViewById(R.id.linePlacedToConfirmed)
        tvTitlePlaced = findViewById(R.id.tvStepTitlePlaced)
        tvDatePlaced = findViewById(R.id.tvStepDatePlaced)

        ivDotConfirmed = findViewById(R.id.ivStepDotConfirmed)
        lineConfirmedToPacked = findViewById(R.id.lineConfirmedToPacked)
        tvTitleConfirmed = findViewById(R.id.tvStepTitleConfirmed)
        tvDateConfirmed = findViewById(R.id.tvStepDateConfirmed)

        ivDotPacked = findViewById(R.id.ivStepDotPacked)
        linePackedToShipped = findViewById(R.id.linePackedToShipped)
        tvTitlePacked = findViewById(R.id.tvStepTitlePacked)
        tvDatePacked = findViewById(R.id.tvStepDatePacked)

        ivDotShipped = findViewById(R.id.ivStepDotShipped)
        lineShippedToOut = findViewById(R.id.lineShippedToOut)
        tvTitleShipped = findViewById(R.id.tvStepTitleShipped)
        tvDateShipped = findViewById(R.id.tvStepDateShipped)

        ivDotOut = findViewById(R.id.ivStepDotOut)
        lineOutToDelivered = findViewById(R.id.lineOutToDelivered)
        tvTitleOut = findViewById(R.id.tvStepTitleOut)
        tvDateOut = findViewById(R.id.tvStepDateOut)

        ivDotDelivered = findViewById(R.id.ivStepDotDelivered)
        tvTitleDelivered = findViewById(R.id.tvStepTitleDelivered)
        tvDateDelivered = findViewById(R.id.tvStepDateDelivered)
    }

    private fun loadInitialData() {
        if (orderNumber.isBlank()) return
        val userId = UserPreferencesHelper.getActiveUserId(this)

        lifecycleScope.launch {
            val order = OrderRepository.getOrderDetails(this@TrackOrderActivity, userId, orderNumber)
            if (order != null) {
                renderOrderState(order)
            }
        }
    }

    private fun attachRealtimeListener() {
        if (orderNumber.isBlank()) return
        val userId = UserPreferencesHelper.getActiveUserId(this)

        try {
            val docRef = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("orders")
                .document(orderNumber)

            firestoreListener = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Snapshot error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    handleSnapshotUpdate(snapshot, userId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach snapshot listener: ${e.message}")
        }
    }

    private fun handleSnapshotUpdate(doc: DocumentSnapshot, userId: String) {
        val parsed = OrderRepository.parseFirestoreDoc(doc.data ?: emptyMap(), userId)
        if (parsed != null) {
            renderOrderState(parsed)

            // Update Room locally in background
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    StylistaDatabase.getInstance(this@TrackOrderActivity)
                        .orderDao()
                        .updateOrderStatus(parsed.orderNumber, parsed.status)
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating local order status: ${e.message}")
                }
            }
        }
    }

    private fun renderOrderState(order: OrderDetailsModel) {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        tvEstimatedDate.text = "📅 Estimated Delivery: " + sdf.format(Date(order.estimatedDeliveryDate))

        // Address summary
        order.deliveryAddress?.let { addr ->
            tvAddressSummary.text = "${addr.name} (${addr.type})\n${addr.houseFlat}, ${addr.streetArea}, ${addr.city} - ${addr.pincode}\nPhone: +91 ${addr.phone}"
        }

        val statusUpper = order.status.uppercase(Locale.getDefault())

        if (statusUpper == "CANCELLED") {
            cardCancelled.visibility = View.VISIBLE
            val cancelEvent = order.statusHistory.lastOrNull { it.status.equals("CANCELLED", ignoreCase = true) }
            val reason = cancelEvent?.note ?: "Cancelled by customer"
            tvCancelledReason.text = "Reason: $reason"
            tvCurrentStatusTitle.text = "Order Cancelled"
            tvCurrentStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.error_red))
            renderTimelineProgress(-1)
            return
        }

        cardCancelled.visibility = View.GONE
        tvCurrentStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.charcoal))

        val currentIdx = STATUS_ORDER.indexOf(statusUpper)
        val activeIndex = if (currentIdx >= 0) currentIdx else 0

        tvCurrentStatusTitle.text = when (statusUpper) {
            "CONFIRMED" -> "Order Confirmed"
            "PACKED" -> "Packed & Ready to Ship"
            "SHIPPED" -> "Shipped & In Transit"
            "OUT_FOR_DELIVERY" -> "Out for Delivery"
            "DELIVERED" -> "Order Delivered"
            else -> "Order Placed"
        }

        renderTimelineProgress(activeIndex)
    }

    private fun renderTimelineProgress(activeStepIndex: Int) {
        val colorActive = ContextCompat.getColor(this, R.color.success_green)
        val colorInactive = ContextCompat.getColor(this, R.color.neutral_gray)
        val colorDivider = ContextCompat.getColor(this, R.color.neutral_divider)
        val colorLineActive = ContextCompat.getColor(this, R.color.success_green)

        val steps = listOf(
            StepViews(ivDotPlaced, linePlacedToConfirmed, tvTitlePlaced, tvDatePlaced),
            StepViews(ivDotConfirmed, lineConfirmedToPacked, tvTitleConfirmed, tvDateConfirmed),
            StepViews(ivDotPacked, linePackedToShipped, tvTitlePacked, tvDatePacked),
            StepViews(ivDotShipped, lineShippedToOut, tvTitleShipped, tvDateShipped),
            StepViews(ivDotOut, lineOutToDelivered, tvTitleOut, tvDateOut),
            StepViews(ivDotDelivered, null, tvTitleDelivered, tvDateDelivered)
        )

        for (i in steps.indices) {
            val step = steps[i]
            if (activeStepIndex >= 0 && i <= activeStepIndex) {
                // Completed or Current step
                step.dot.setColorFilter(colorActive)
                step.title.setTextColor(ContextCompat.getColor(this, R.color.charcoal))
                step.title.paint.isFakeBoldText = true

                step.line?.setBackgroundColor(if (i < activeStepIndex) colorLineActive else colorDivider)
            } else {
                // Future step
                step.dot.setColorFilter(colorInactive)
                step.title.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                step.title.paint.isFakeBoldText = false
                step.line?.setBackgroundColor(colorDivider)
            }
        }
    }

    private data class StepViews(
        val dot: ImageView,
        val line: View?,
        val title: TextView,
        val date: TextView
    )
}
