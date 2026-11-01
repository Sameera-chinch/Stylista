package com.example.stylista.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.stylista.repositories.AddressRepository
import com.example.stylista.repositories.OrderRepository
import com.example.stylista.database.entities.AddressEntity
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.utils.StoreConfig
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import com.example.stylista.BuildConfig
import com.example.stylista.R
import com.example.stylista.StylistaApp
import com.example.stylista.adapters.*
import com.example.stylista.chatbot.*
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*
import com.example.stylista.utils.*

class CheckoutActivity : AppCompatActivity() {

    private lateinit var tvAddressName: TextView
    private lateinit var tvAddress: TextView
    private lateinit var btnChangeAddress: TextView
    private lateinit var tvCheckoutDeliveryBanner: TextView
    private lateinit var tvCheckoutSubtotal: TextView
    private lateinit var tvCheckoutDelivery: TextView
    private lateinit var tvCheckoutTotal: TextView
    private lateinit var layoutCheckoutDiscountRow: LinearLayout
    private lateinit var tvCheckoutDiscountLabel: TextView
    private lateinit var tvCheckoutDiscount: TextView
    private lateinit var btnPlaceOrder: Button

    // Payment Cards
    private lateinit var paymentCardCod: LinearLayout
    private lateinit var tvPaymentCodSelected: TextView
    private lateinit var cardPaymentOnline: MaterialCardView
    private lateinit var paymentCardOnlineLayout: LinearLayout
    private lateinit var tvPaymentOnlineSelected: TextView

    private var selectedAddress: AddressEntity? = null
    private var discountAmount: Int = 0
    private var promoCode: String? = null
    private var currentSubtotal: Int = 0
    private var currentDeliveryFee: Int = 0
    private var selectedPaymentMethod: String = "Cash on Delivery"

    private val addressSelectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val addressId = result.data?.getStringExtra("selected_address_id") ?: ""
            if (addressId.isNotBlank()) {
                loadAddressById(addressId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        discountAmount = intent.getIntExtra("discount_amount", 0)
        promoCode = intent.getStringExtra("promo_code")

        initViews()
        setupListeners()
        loadInitialData()
    }

    override fun onResume() {
        super.onResume()
        if (selectedAddress == null) {
            loadDefaultAddress()
        }
        recalculateTotals()
    }

    private fun initViews() {
        val btnBack = findViewById<TextView>(R.id.btnCheckoutBack)
        btnBack.setOnClickListener { finish() }

        tvAddressName = findViewById(R.id.tvAddressName)
        tvAddress = findViewById(R.id.tvAddress)
        btnChangeAddress = findViewById(R.id.btnChangeAddress)
        tvCheckoutDeliveryBanner = findViewById(R.id.tvCheckoutDeliveryBanner)
        tvCheckoutSubtotal = findViewById(R.id.tvCheckoutSubtotal)
        tvCheckoutDelivery = findViewById(R.id.tvCheckoutDelivery)
        tvCheckoutTotal = findViewById(R.id.tvCheckoutTotal)
        layoutCheckoutDiscountRow = findViewById(R.id.layoutCheckoutDiscountRow)
        tvCheckoutDiscountLabel = findViewById(R.id.tvCheckoutDiscountLabel)
        tvCheckoutDiscount = findViewById(R.id.tvCheckoutDiscount)
        btnPlaceOrder = findViewById(R.id.btnPlaceOrder)

        paymentCardCod = findViewById(R.id.paymentCard)
        tvPaymentCodSelected = findViewById(R.id.tvPaymentSelected)
        cardPaymentOnline = findViewById(R.id.cardPaymentOnline)
        paymentCardOnlineLayout = findViewById(R.id.paymentCardOnlineLayout)
        tvPaymentOnlineSelected = findViewById(R.id.tvPaymentOnlineSelected)

        // Select COD by default
        selectCodPayment()
    }

    private fun setupListeners() {
        btnChangeAddress.setOnClickListener {
            val intent = Intent(this, SavedAddressActivity::class.java).apply {
                putExtra("is_selecting_mode", true)
            }
            addressSelectionLauncher.launch(intent)
        }

        paymentCardCod.setOnClickListener {
            selectCodPayment()
        }

        paymentCardOnlineLayout.setOnClickListener {
            selectOnlinePayment()
        }

        btnPlaceOrder.setOnClickListener {
            placeOrder()
        }
    }

    private fun selectCodPayment() {
        selectedPaymentMethod = "Cash on Delivery"
        tvPaymentCodSelected.visibility = View.VISIBLE
        tvPaymentOnlineSelected.visibility = View.INVISIBLE
    }

    private fun selectOnlinePayment() {
        selectedPaymentMethod = "Razorpay (Test Mode)"
        tvPaymentCodSelected.visibility = View.INVISIBLE
        tvPaymentOnlineSelected.visibility = View.VISIBLE
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            StoreConfig.syncWithFirestore()
            loadDefaultAddress()
            recalculateTotals()
        }
    }

    private fun loadDefaultAddress() {
        lifecycleScope.launch {
            val uid = UserPreferencesHelper.getActiveUserId(this@CheckoutActivity)
            val defaultAddr = AddressRepository.getDefaultAddress(this@CheckoutActivity, uid)
            withContext(Dispatchers.Main) {
                if (defaultAddr != null) {
                    selectedAddress = defaultAddr
                    tvAddressName.text = defaultAddr.name
                    tvAddress.text = "${defaultAddr.getFullAddressString()}\nPhone: ${defaultAddr.phone}"
                } else {
                    selectedAddress = null
                    tvAddressName.text = "No address selected"
                    tvAddress.text = "Tap 'Change' to add or select a delivery address"
                }
            }
        }
    }

    private fun loadAddressById(addressId: String) {
        lifecycleScope.launch {
            val uid = UserPreferencesHelper.getActiveUserId(this@CheckoutActivity)
            val addr = AddressRepository.getAddressById(this@CheckoutActivity, addressId, uid)
            withContext(Dispatchers.Main) {
                if (addr != null) {
                    selectedAddress = addr
                    tvAddressName.text = addr.name
                    tvAddress.text = "${addr.getFullAddressString()}\nPhone: ${addr.phone}"
                }
            }
        }
    }

    private fun recalculateTotals() {
        lifecycleScope.launch(Dispatchers.IO) {
            val uid = UserPreferencesHelper.getActiveUserId(this@CheckoutActivity)
            val db = StylistaDatabase.getInstance(this@CheckoutActivity)
            val cartItems = db.cartDao().getCartItems(uid)

            val productIds = cartItems.map { it.productId }.distinct()
            val products = if (productIds.isNotEmpty()) db.productDao().getProductsByIds(productIds) else emptyList()
            val productMap = products.associateBy { it.id }

            var subtotal = 0
            for (item in cartItems) {
                val p = productMap[item.productId]
                if (p != null) {
                    subtotal += p.price * item.quantity
                }
            }

            val deliveryFee = StoreConfig.calculateDeliveryFee(subtotal)
            val finalTotal = maxOf(0, subtotal + deliveryFee - discountAmount)

            withContext(Dispatchers.Main) {
                currentSubtotal = subtotal
                currentDeliveryFee = deliveryFee

                tvCheckoutDeliveryBanner.text = StoreConfig.getDeliveryBannerMessage(subtotal)
                tvCheckoutSubtotal.text = formatPrice(subtotal)

                if (deliveryFee == 0) {
                    tvCheckoutDelivery.text = "FREE"
                    tvCheckoutDelivery.setTextColor(ContextCompat.getColor(this@CheckoutActivity, R.color.success_green))
                } else {
                    tvCheckoutDelivery.text = "₹$deliveryFee"
                    tvCheckoutDelivery.setTextColor(ContextCompat.getColor(this@CheckoutActivity, R.color.charcoal))
                }

                if (discountAmount > 0) {
                    layoutCheckoutDiscountRow.visibility = View.VISIBLE
                    tvCheckoutDiscountLabel.text = if (!promoCode.isNullOrBlank()) "Coupon ($promoCode)" else "Coupon Discount"
                    tvCheckoutDiscount.text = "-${formatPrice(discountAmount)}"
                } else {
                    layoutCheckoutDiscountRow.visibility = View.GONE
                }

                tvCheckoutTotal.text = formatPrice(finalTotal)
            }
        }
    }

    private fun placeOrder() {
        val address = selectedAddress
        if (address == null) {
            UiFeedback.showWarning(this, "Please select or add a delivery address")
            val intent = Intent(this, SavedAddressActivity::class.java).apply {
                putExtra("is_selecting_mode", true)
            }
            addressSelectionLauncher.launch(intent)
            return
        }

        btnPlaceOrder.isEnabled = false
        btnPlaceOrder.text = "Placing Order..."

        lifecycleScope.launch(Dispatchers.IO) {
            val uid = UserPreferencesHelper.getActiveUserId(this@CheckoutActivity)
            val db = StylistaDatabase.getInstance(this@CheckoutActivity)
            val cartItems = db.cartDao().getCartItems(uid)

            if (cartItems.isEmpty()) {
                withContext(Dispatchers.Main) {
                    btnPlaceOrder.isEnabled = true
                    btnPlaceOrder.text = "Place Order"
                    UiFeedback.showWarning(this@CheckoutActivity, "Your bag is empty")
                }
                return@launch
            }

            val result = OrderRepository.createOrder(
                context = this@CheckoutActivity,
                userId = uid,
                cartItems = cartItems,
                address = address,
                paymentMethod = selectedPaymentMethod,
                discountAmount = discountAmount
            )

            withContext(Dispatchers.Main) {
                btnPlaceOrder.isEnabled = true
                btnPlaceOrder.text = "Place Order"

                if (result.isSuccess) {
                    val orderNumber = result.getOrNull() ?: ""
                    UiFeedback.showSuccess(this@CheckoutActivity, "Order placed successfully!")

                    val firstProduct = db.productDao().getProductById(cartItems.first().productId)
                    val productNameSummary = if (cartItems.size > 1) {
                        "${firstProduct?.name ?: "Product"} + ${cartItems.size - 1} more"
                    } else {
                        firstProduct?.name ?: "Stylista Product"
                    }
                    val productSizeSummary = cartItems.first().size
                    val totalFormatted = formatPrice(maxOf(0, currentSubtotal + currentDeliveryFee - discountAmount))

                    val intent = Intent(this@CheckoutActivity, OrderSuccessActivity::class.java).apply {
                        putExtra("order_number", orderNumber)
                        putExtra("product_name", productNameSummary)
                        putExtra("product_size", productSizeSummary)
                        putExtra("payment_method", selectedPaymentMethod)
                        putExtra("total", totalFormatted)
                        putExtra("estimated_delivery", System.currentTimeMillis() + 4 * 24 * 60 * 60 * 1000L)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    UiFeedback.showError(
                        this@CheckoutActivity,
                        "Could not place order: ${result.exceptionOrNull()?.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun formatPrice(amount: Int): String {
        return "₹" + NumberFormat.getNumberInstance(Locale.US).format(amount)
    }
}
