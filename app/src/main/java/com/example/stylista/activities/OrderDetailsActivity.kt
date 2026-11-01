package com.example.stylista.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.stylista.repositories.OrderDetailsModel
import com.example.stylista.repositories.OrderItemModel
import com.example.stylista.repositories.OrderRepository
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.stylista.BuildConfig
import com.example.stylista.R
import com.example.stylista.StylistaApp
import com.example.stylista.adapters.*
import com.example.stylista.chatbot.*
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*
import com.example.stylista.utils.*

class OrderDetailsActivity : AppCompatActivity() {

    private lateinit var pbLoading: ProgressBar
    private lateinit var scrollContent: View
    private lateinit var tvOrderNumber: TextView
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvOrderDate: TextView
    private lateinit var tvEstimatedDelivery: TextView
    private lateinit var containerItems: LinearLayout

    private lateinit var tvAddressType: TextView
    private lateinit var tvRecipientName: TextView
    private lateinit var tvFullAddress: TextView
    private lateinit var tvRecipientPhone: TextView

    private lateinit var tvPaymentMethod: TextView
    private lateinit var tvPaymentStatus: TextView
    private lateinit var tvPaymentNote: TextView

    private lateinit var tvSubtotal: TextView
    private lateinit var tvDeliveryFee: TextView
    private lateinit var rowDiscount: View
    private lateinit var tvDiscount: TextView
    private lateinit var tvTotal: TextView

    private lateinit var btnTrackOrder: Button
    private lateinit var btnCancelOrder: Button
    private lateinit var btnExchangeItem: Button

    private var currentOrder: OrderDetailsModel? = null
    private var orderNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_details)

        orderNumber = intent.getStringExtra("order_number") ?: ""

        initViews()
        loadOrderDetails()
    }

    private fun initViews() {
        findViewById<ImageView>(R.id.btnBackOrderDetails).setOnClickListener { finish() }

        val btnCopy = findViewById<ImageView>(R.id.btnCopyOrderIdDetails)
        btnCopy.visibility = View.VISIBLE
        btnCopy.setOnClickListener {
            if (orderNumber.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Order Number", orderNumber)
                clipboard.setPrimaryClip(clip)
                UiFeedback.showSuccess(this, "Order ID copied to clipboard")
            }
        }

        pbLoading = findViewById(R.id.pbOrderDetailsLoading)
        scrollContent = findViewById(R.id.scrollOrderDetails)

        tvOrderNumber = findViewById(R.id.tvDetailsOrderNumber)
        tvStatusBadge = findViewById(R.id.tvDetailsStatusBadge)
        tvOrderDate = findViewById(R.id.tvDetailsOrderDate)
        tvEstimatedDelivery = findViewById(R.id.tvDetailsEstimatedDelivery)
        containerItems = findViewById(R.id.containerOrderItems)

        tvAddressType = findViewById(R.id.tvDetailsAddressType)
        tvRecipientName = findViewById(R.id.tvDetailsRecipientName)
        tvFullAddress = findViewById(R.id.tvDetailsFullAddress)
        tvRecipientPhone = findViewById(R.id.tvDetailsRecipientPhone)

        tvPaymentMethod = findViewById(R.id.tvDetailsPaymentMethod)
        tvPaymentStatus = findViewById(R.id.tvDetailsPaymentStatus)
        tvPaymentNote = findViewById(R.id.tvDetailsPaymentNote)

        tvSubtotal = findViewById(R.id.tvDetailsSubtotal)
        tvDeliveryFee = findViewById(R.id.tvDetailsDeliveryFee)
        rowDiscount = findViewById(R.id.rowDetailsDiscount)
        tvDiscount = findViewById(R.id.tvDetailsDiscount)
        tvTotal = findViewById(R.id.tvDetailsTotal)

        btnTrackOrder = findViewById(R.id.btnDetailsTrackOrder)
        btnCancelOrder = findViewById(R.id.btnDetailsCancelOrder)
        btnExchangeItem = findViewById(R.id.btnDetailsExchangeItem)

        btnTrackOrder.setOnClickListener {
            val intent = Intent(this, TrackOrderActivity::class.java).apply {
                putExtra("order_number", orderNumber)
            }
            startActivity(intent)
        }

        btnCancelOrder.setOnClickListener {
            showCancelOrderBottomSheet()
        }

        btnExchangeItem.setOnClickListener {
            showExchangeBottomSheet()
        }
    }

    private fun loadOrderDetails() {
        if (orderNumber.isBlank()) {
            UiFeedback.showError(this, "Invalid order number")
            finish()
            return
        }

        val userId = UserPreferencesHelper.getActiveUserId(this)
        pbLoading.visibility = View.VISIBLE
        scrollContent.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val order = OrderRepository.getOrderDetails(this@OrderDetailsActivity, userId, orderNumber)
                if (order != null) {
                    currentOrder = order
                    bindOrderData(order)
                    scrollContent.visibility = View.VISIBLE
                } else {
                    UiFeedback.showError(this@OrderDetailsActivity, "Could not load order details")
                }
            } catch (e: Exception) {
                UiFeedback.showError(this@OrderDetailsActivity, "Error: ${e.message}")
            } finally {
                pbLoading.visibility = View.GONE
            }
        }
    }

    private fun bindOrderData(order: OrderDetailsModel) {
        tvOrderNumber.text = order.orderNumber

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        tvOrderDate.text = "Placed on " + sdf.format(Date(order.createdAt))

        val estSdf = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        tvEstimatedDelivery.text = "📅 Estimated Delivery: " + estSdf.format(Date(order.estimatedDeliveryDate))

        // Status badge formatting
        val status = order.status.uppercase(Locale.getDefault())
        tvStatusBadge.text = status
        when (status) {
            "DELIVERED" -> {
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.success_green))
                tvStatusBadge.setBackgroundColor(Color.parseColor("#E8F5E9"))
            }
            "CANCELLED" -> {
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.error_red))
                tvStatusBadge.setBackgroundColor(Color.parseColor("#FFEBEE"))
            }
            "SHIPPED", "OUT_FOR_DELIVERY", "PACKED" -> {
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.cat_campus))
                tvStatusBadge.setBackgroundColor(Color.parseColor("#FFF3E0"))
            }
            else -> {
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.pastel_baby_blue_dark))
                tvStatusBadge.setBackgroundColor(Color.parseColor("#EBF2FB"))
            }
        }

        // Inflate items
        containerItems.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (item in order.items) {
            val itemView = inflater.inflate(R.layout.item_order_detail_product, containerItems, false)
            val ivThumb = itemView.findViewById<ImageView>(R.id.ivDetailProductThumb)
            val tvName = itemView.findViewById<TextView>(R.id.tvDetailProductName)
            val tvSize = itemView.findViewById<TextView>(R.id.tvDetailProductSize)
            val tvPriceQty = itemView.findViewById<TextView>(R.id.tvDetailProductPriceQty)

            tvName.text = item.name
            tvSize.text = "Size: ${item.size}"
            val priceStr = NumberFormat.getNumberInstance(Locale.US).format(item.price)
            val itemTotal = NumberFormat.getNumberInstance(Locale.US).format(item.price * item.quantity)
            tvPriceQty.text = "₹$priceStr × ${item.quantity} = ₹$itemTotal"

            if (item.imageUrl.isNotBlank()) {
                Glide.with(this)
                    .load(item.imageUrl)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_placeholder_product)
                    .error(R.drawable.ic_placeholder_product)
                    .into(ivThumb)
            }
            containerItems.addView(itemView)
        }

        // Delivery Address
        order.deliveryAddress?.let { addr ->
            tvAddressType.text = addr.type.uppercase(Locale.getDefault())
            tvRecipientName.text = addr.name
            val fullAddr = StringBuilder()
            if (addr.houseFlat.isNotBlank()) fullAddr.append(addr.houseFlat).append(", ")
            if (addr.streetArea.isNotBlank()) fullAddr.append(addr.streetArea).append("\n")
            if (addr.landmark.isNotBlank()) fullAddr.append("Near ").append(addr.landmark).append(", ")
            fullAddr.append(addr.city).append(", ").append(addr.state).append(" - ").append(addr.pincode)
            tvFullAddress.text = fullAddr.toString()
            tvRecipientPhone.text = "Phone: +91 ${addr.phone}"
        }

        // Payment Info
        tvPaymentMethod.text = order.paymentMethod
        tvPaymentStatus.text = order.paymentStatus
        if (order.paymentStatus.equals("PAID", ignoreCase = true)) {
            tvPaymentStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green))
            tvPaymentNote.text = if (order.razorpayPaymentId.isNotBlank()) {
                "Paid via Razorpay (Test ID: ${order.razorpayPaymentId})"
            } else {
                "Payment received successfully."
            }
        } else {
            tvPaymentStatus.setTextColor(ContextCompat.getColor(this, R.color.cat_campus))
            tvPaymentNote.text = "Pay with cash or UPI directly to the courier upon delivery."
        }

        // Price breakdown
        val fmt = NumberFormat.getNumberInstance(Locale.US)
        tvSubtotal.text = "₹" + fmt.format(order.subtotal)
        if (order.deliveryFee == 0) {
            tvDeliveryFee.text = "FREE"
            tvDeliveryFee.setTextColor(ContextCompat.getColor(this, R.color.success_green))
        } else {
            tvDeliveryFee.text = "₹" + fmt.format(order.deliveryFee)
            tvDeliveryFee.setTextColor(ContextCompat.getColor(this, R.color.charcoal))
        }

        if (order.discountAmount > 0) {
            rowDiscount.visibility = View.VISIBLE
            tvDiscount.text = "- ₹" + fmt.format(order.discountAmount)
        } else {
            rowDiscount.visibility = View.GONE
        }
        tvTotal.text = "₹" + fmt.format(order.totalAmount)

        // Action Buttons Eligibility
        setupActionButtons(order)
    }

    private fun setupActionButtons(order: OrderDetailsModel) {
        val canCancel = com.example.stylista.utils.OrderPolicyHelper.canCancel(order.status)
        btnCancelOrder.visibility = if (canCancel) View.VISIBLE else View.GONE

        val canExchange = com.example.stylista.utils.OrderPolicyHelper.canExchange(order.status, order.createdAt)
        btnExchangeItem.visibility = if (canExchange) View.VISIBLE else View.GONE
    }

    private fun showCancelOrderBottomSheet() {
        val order = currentOrder ?: return
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_cancel_order, null)
        dialog.setContentView(sheetView)

        val rgReasons = sheetView.findViewById<RadioGroup>(R.id.rgCancelReasons)
        val etNotes = sheetView.findViewById<EditText>(R.id.etCancelNotes)
        val rbOther = sheetView.findViewById<RadioButton>(R.id.rbReasonOther)
        val btnDismiss = sheetView.findViewById<Button>(R.id.btnCancelDismiss)
        val btnConfirm = sheetView.findViewById<Button>(R.id.btnConfirmCancel)

        rgReasons.setOnCheckedChangeListener { _, checkedId ->
            etNotes.visibility = if (checkedId == rbOther.id) View.VISIBLE else View.GONE
        }

        btnDismiss.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val selectedReason = when (rgReasons.checkedRadioButtonId) {
                R.id.rbReasonPrice -> "Found a better price elsewhere"
                R.id.rbReasonDelivery -> "Estimated delivery time is too long"
                R.id.rbReasonOther -> {
                    val custom = etNotes.text.toString().trim()
                    if (custom.isNotBlank()) custom else "Other reason"
                }
                else -> "Ordered by mistake"
            }

            dialog.dismiss()
            performOrderCancellation(order.orderNumber, selectedReason)
        }

        dialog.show()
    }

    private fun performOrderCancellation(orderNumber: String, reason: String) {
        val userId = UserPreferencesHelper.getActiveUserId(this)
        pbLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = OrderRepository.cancelOrder(this@OrderDetailsActivity, userId, orderNumber, reason)
            pbLoading.visibility = View.GONE
            if (result.isSuccess) {
                UiFeedback.showSuccess(this@OrderDetailsActivity, "Order cancelled successfully")
                loadOrderDetails()
            } else {
                UiFeedback.showError(this@OrderDetailsActivity, "Failed to cancel order: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun showExchangeBottomSheet() {
        val order = currentOrder ?: return
        if (order.items.isEmpty()) return

        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_exchange_order, null)
        dialog.setContentView(sheetView)

        val tvProduct = sheetView.findViewById<TextView>(R.id.tvExchangeCurrentProduct)
        val tvCurrentSize = sheetView.findViewById<TextView>(R.id.tvExchangeCurrentSize)
        val chipGroupSizes = sheetView.findViewById<ChipGroup>(R.id.chipGroupNewSizes)
        val rgReasons = sheetView.findViewById<RadioGroup>(R.id.rgExchangeReasons)
        val btnDismiss = sheetView.findViewById<Button>(R.id.btnExchangeDismiss)
        val btnSubmit = sheetView.findViewById<Button>(R.id.btnSubmitExchange)

        // Use the first item by default
        val itemToExchange: OrderItemModel = order.items.first()
        tvProduct.text = "Item: ${itemToExchange.name}"
        tvCurrentSize.text = "Current Size: ${itemToExchange.size}"

        btnDismiss.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val checkedChipId = chipGroupSizes.checkedChipId
            if (checkedChipId == View.NO_ID) {
                UiFeedback.showWarning(this, "Please select a replacement size")
                return@setOnClickListener
            }

            val newSize = sheetView.findViewById<Chip>(checkedChipId).text.toString()
            if (newSize.equals(itemToExchange.size, ignoreCase = true)) {
                UiFeedback.showWarning(this, "Please select a different size from your current size (${itemToExchange.size})")
                return@setOnClickListener
            }

            val reason = when (rgReasons.checkedRadioButtonId) {
                R.id.rbExchLarge -> "Current size is too large"
                R.id.rbExchDefective -> "Item has a defect or damage"
                R.id.rbExchMismatch -> "Color or fabric not as expected"
                else -> "Current size is too small"
            }

            dialog.dismiss()
            submitExchangeRequest(itemToExchange, newSize, reason)
        }

        dialog.show()
    }

    private fun submitExchangeRequest(item: OrderItemModel, newSize: String, reason: String) {
        val order = currentOrder ?: return
        val userId = UserPreferencesHelper.getActiveUserId(this)
        pbLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = OrderRepository.requestExchange(
                context = this@OrderDetailsActivity,
                userId = userId,
                orderNumber = order.orderNumber,
                productId = item.productId,
                productName = item.name,
                oldSize = item.size,
                newSize = newSize,
                reason = reason
            )
            pbLoading.visibility = View.GONE
            if (result.isSuccess) {
                AlertDialog.Builder(this@OrderDetailsActivity)
                    .setTitle("Exchange Request Submitted")
                    .setMessage("Your request to exchange ${item.name} from size ${item.size} to size $newSize has been logged.\n\nOur courier will deliver the new size and collect the current piece simultaneously.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                UiFeedback.showError(this@OrderDetailsActivity, "Exchange submission failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}
