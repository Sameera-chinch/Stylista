package com.example.stylista.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.stylista.utils.UiFeedback
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

class OrderSuccessActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_success)

        val orderNumber = intent.getStringExtra("order_number") ?: "ORD-${System.currentTimeMillis()}"
        val productName = intent.getStringExtra("product_name") ?: "Fashion Selection"
        val productSize = intent.getStringExtra("product_size") ?: "Standard"
        val paymentMethod = intent.getStringExtra("payment_method") ?: "Cash on Delivery"
        val total = intent.getStringExtra("total") ?: "₹0"
        val estimatedDeliveryTimestamp = intent.getLongExtra("estimated_delivery", System.currentTimeMillis() + 4 * 24 * 60 * 60 * 1000L)

        val tvOrderId = findViewById<TextView>(R.id.tvSuccessOrderId)
        val btnCopy = findViewById<TextView>(R.id.btnCopyOrderId)
        val tvProduct = findViewById<TextView>(R.id.tvOrderProduct)
        val tvDelivery = findViewById<TextView>(R.id.tvEstimatedDelivery)
        val tvDetails = findViewById<TextView>(R.id.tvOrderDetails)
        val btnTrack = findViewById<Button>(R.id.btnTrackOrder)
        val btnViewDetails = findViewById<Button>(R.id.btnViewOrderDetails)
        val btnContinue = findViewById<TextView>(R.id.btnContinueShopping)

        tvOrderId.text = orderNumber
        tvProduct.text = "$productName (Size: $productSize)"

        val dateFormatted = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(estimatedDeliveryTimestamp))
        tvDelivery.text = "📅 Estimated Delivery: $dateFormatted"

        val paymentSummary = if (paymentMethod.contains("Razorpay", ignoreCase = true)) {
            "Paid $total via Razorpay (Test Mode)"
        } else {
            "Pay $total on delivery (Cash on Delivery)"
        }
        tvDetails.text = paymentSummary

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Order ID", orderNumber)
            clipboard.setPrimaryClip(clip)
            UiFeedback.showSuccess(this, "Order ID copied to clipboard")
        }

        btnTrack.setOnClickListener {
            val intent = Intent(this, TrackOrderActivity::class.java).apply {
                putExtra("order_number", orderNumber)
            }
            startActivity(intent)
        }

        btnViewDetails.setOnClickListener {
            val intent = Intent(this, OrderDetailsActivity::class.java).apply {
                putExtra("order_number", orderNumber)
            }
            startActivity(intent)
        }

        btnContinue.setOnClickListener {
            returnToHome()
        }

        onBackPressedDispatcher.addCallback(this) {
            returnToHome()
        }
    }

    private fun returnToHome() {
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
}
