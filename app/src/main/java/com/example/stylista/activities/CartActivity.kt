package com.example.stylista.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stylista.database.entities.CartEntity
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.repositories.CartRepository
import com.example.stylista.utils.NavigationHelper
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

class CartActivity : AppCompatActivity() {

    private lateinit var rvCartItems: RecyclerView
    private lateinit var layoutCartContent: View
    private lateinit var layoutEmptyCart: View
    private lateinit var layoutCheckoutBar: View
    private lateinit var tvSubtotal: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvBottomTotal: TextView
    private lateinit var tvCartBadgeCount: TextView
    private lateinit var btnCheckout: Button
    private lateinit var btnEmptyExplore: Button
    private lateinit var btnCartBack: TextView

    // Promo Code Views
    private lateinit var etPromoCode: EditText
    private lateinit var btnApplyPromo: Button
    private lateinit var tvPromoStatus: TextView
    private lateinit var layoutDiscountRow: LinearLayout
    private lateinit var tvDiscountLabel: TextView
    private lateinit var tvDiscountAmount: TextView
    private var tvDeliveryThresholdBanner: TextView? = null
    private var tvDeliveryFee: TextView? = null

    private lateinit var cartAdapter: CartAdapter
    private val cartItemsList = mutableListOf<CartItemUI>()

    private var currentSubtotal: Int = 0
    private var appliedDiscount: Int = 0
    private var appliedPromoCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)

        initViews()
        setupRecyclerView()
        setupListeners()
    }

    private fun initViews() {
        rvCartItems = findViewById(R.id.rvCartItems)
        layoutCartContent = findViewById(R.id.layoutCartContent)
        layoutEmptyCart = findViewById(R.id.layoutEmptyCart)
        layoutCheckoutBar = findViewById(R.id.layoutCheckoutBar)
        tvSubtotal = findViewById(R.id.tvSubtotal)
        tvTotal = findViewById(R.id.tvTotal)
        tvBottomTotal = findViewById(R.id.tvBottomTotal)
        tvCartBadgeCount = findViewById(R.id.tvCartBadgeCount)
        btnCheckout = findViewById(R.id.btnCheckout)
        btnEmptyExplore = findViewById(R.id.btnEmptyExplore)
        btnCartBack = findViewById(R.id.btnCartBack)

        etPromoCode = findViewById(R.id.etPromoCode)
        btnApplyPromo = findViewById(R.id.btnApplyPromo)
        tvPromoStatus = findViewById(R.id.tvPromoStatus)
        layoutDiscountRow = findViewById(R.id.layoutDiscountRow)
        tvDiscountLabel = findViewById(R.id.tvDiscountLabel)
        tvDiscountAmount = findViewById(R.id.tvDiscountAmount)
        tvDeliveryThresholdBanner = findViewById(R.id.tvDeliveryThresholdBanner)
        tvDeliveryFee = findViewById(R.id.tvDeliveryFee)
    }

    private fun setupRecyclerView() {
        cartAdapter = CartAdapter(
            items = cartItemsList,
            onQuantityChanged = { cartEntity, newQty ->
                updateQuantity(cartEntity, newQty)
            },
            onItemRemoved = { cartEntity ->
                removeItem(cartEntity)
            },
            onItemClicked = { product ->
                NavigationHelper.openProductDetails(this, product.id)
            }
        )

        rvCartItems.layoutManager = LinearLayoutManager(this)
        rvCartItems.adapter = cartAdapter
    }

    private fun setupListeners() {
        btnCartBack.setOnClickListener {
            finish()
        }

        btnEmptyExplore.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
            finish()
        }

        btnApplyPromo.setOnClickListener {
            val code = etPromoCode.text.toString().trim().uppercase()
            validateAndApplyPromo(code)
        }

        btnCheckout.setOnClickListener {
            if (cartItemsList.isNotEmpty()) {
                val deliveryFee = com.example.stylista.utils.StoreConfig.calculateDeliveryFee(currentSubtotal)
                val intent = Intent(this, CheckoutActivity::class.java).apply {
                    putExtra("discount_amount", appliedDiscount)
                    putExtra("promo_code", appliedPromoCode ?: "")
                    putExtra("delivery_fee", deliveryFee)
                }
                startActivity(intent)
            } else {
                UiFeedback.showWarning(this, "Your bag is empty")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            com.example.stylista.utils.StoreConfig.syncWithFirestore()
        }
        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser != null && !authUser.isAnonymous) {
            lifecycleScope.launch {
                CartRepository.syncCartOnLogin(this@CartActivity, authUser.uid)
                loadCart()
            }
        } else {
            loadCart()
        }
    }

    private fun validateAndApplyPromo(code: String) {
        if (code.isBlank()) {
            tvPromoStatus.text = "Please enter a promo code"
            tvPromoStatus.setTextColor(ContextCompat.getColor(this, R.color.error_red))
            UiFeedback.showWarning(this, "Please enter a promo code")
            return
        }

        if (currentSubtotal <= 0) {
            UiFeedback.showWarning(this, "Add items to your bag first")
            return
        }

        // Check local known promo codes
        var percent = when (code) {
            "VIP15" -> 15
            "STYLISTA10" -> 10
            "WELCOME20" -> 20
            else -> 0
        }

        if (percent > 0) {
            applyDiscount(code, percent)
        } else {
            // Check Firestore promoCodes collection
            FirebaseFirestore.getInstance().collection("promoCodes").document(code).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val discountLong = document.getLong("discountPercent") ?: 10L
                        applyDiscount(code, discountLong.toInt())
                    } else {
                        tvPromoStatus.text = "Invalid promo code. Try VIP15, STYLISTA10, or WELCOME20"
                        tvPromoStatus.setTextColor(ContextCompat.getColor(this, R.color.error_red))
                        appliedDiscount = 0
                        appliedPromoCode = null
                        recalculateTotals()
                        UiFeedback.showError(this, "Invalid promo code '$code'")
                    }
                }
                .addOnFailureListener { e ->
                    tvPromoStatus.text = "Invalid promo code. Try VIP15 or STYLISTA10"
                    tvPromoStatus.setTextColor(ContextCompat.getColor(this, R.color.error_red))
                    appliedDiscount = 0
                    appliedPromoCode = null
                    recalculateTotals()
                    UiFeedback.showError(this, "Promo code validation failed: ${e.localizedMessage}")
                }
        }
    }

    private fun applyDiscount(code: String, percent: Int) {
        appliedPromoCode = code
        appliedDiscount = (currentSubtotal * percent) / 100
        tvPromoStatus.text = "✓ $code applied! You saved $percent% (₹$appliedDiscount)"
        tvPromoStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green))
        UiFeedback.showSuccess(this, "$code applied: $percent% off!")
        recalculateTotals()
    }

    private fun recalculateTotals() {
        val deliveryFee = com.example.stylista.utils.StoreConfig.calculateDeliveryFee(currentSubtotal)
        val finalTotal = maxOf(0, currentSubtotal + deliveryFee - appliedDiscount)
        val subtotalFormatted = "₹" + NumberFormat.getNumberInstance(Locale.US).format(currentSubtotal)
        val finalFormatted = "₹" + NumberFormat.getNumberInstance(Locale.US).format(finalTotal)

        tvSubtotal.text = subtotalFormatted
        tvTotal.text = finalFormatted
        tvBottomTotal.text = finalFormatted

        tvDeliveryThresholdBanner?.text = com.example.stylista.utils.StoreConfig.getDeliveryBannerMessage(currentSubtotal)

        tvDeliveryFee?.let { feeView ->
            if (deliveryFee == 0) {
                feeView.text = "FREE"
                feeView.setTextColor(ContextCompat.getColor(this, R.color.success_green))
            } else {
                feeView.text = "₹$deliveryFee"
                feeView.setTextColor(ContextCompat.getColor(this, R.color.charcoal))
            }
        }

        if (appliedDiscount > 0) {
            layoutDiscountRow.visibility = View.VISIBLE
            tvDiscountLabel.text = "Discount ($appliedPromoCode)"
            tvDiscountAmount.text = "-₹" + NumberFormat.getNumberInstance(Locale.US).format(appliedDiscount)
        } else {
            layoutDiscountRow.visibility = View.GONE
        }
    }

    private fun loadCart() {
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = UserPreferencesHelper.getActiveUserId(this@CartActivity)
            val db = StylistaDatabase.getInstance(this@CartActivity)
            val cartEntities = db.cartDao().getCartItems(userId)

            val productIds = cartEntities.map { it.productId }.distinct()
            val products = if (productIds.isNotEmpty()) {
                db.productDao().getProductsByIds(productIds)
            } else {
                emptyList()
            }
            val productMap = products.associateBy { it.id }

            val items = mutableListOf<CartItemUI>()
            var subtotal = 0

            for (cart in cartEntities) {
                val product = productMap[cart.productId]
                if (product != null) {
                    items.add(CartItemUI(cart, product))
                    subtotal += product.price * cart.quantity
                }
            }

            withContext(Dispatchers.Main) {
                cartItemsList.clear()
                cartItemsList.addAll(items)
                cartAdapter.notifyDataSetChanged()

                if (items.isEmpty()) {
                    layoutCartContent.visibility = View.GONE
                    layoutCheckoutBar.visibility = View.GONE
                    layoutEmptyCart.visibility = View.VISIBLE
                    tvCartBadgeCount.text = "0 items"
                    currentSubtotal = 0
                    appliedDiscount = 0
                    appliedPromoCode = null
                } else {
                    layoutCartContent.visibility = View.VISIBLE
                    layoutCheckoutBar.visibility = View.VISIBLE
                    layoutEmptyCart.visibility = View.GONE

                    val count = items.sumOf { it.cart.quantity }
                    tvCartBadgeCount.text = if (count == 1) "1 item" else "$count items"

                    currentSubtotal = subtotal
                    if (appliedPromoCode != null) {
                        // Re-validate discount with new subtotal
                        val percent = when (appliedPromoCode) {
                            "VIP15" -> 15
                            "WELCOME20" -> 20
                            else -> 10
                        }
                        appliedDiscount = (currentSubtotal * percent) / 100
                    }
                    recalculateTotals()
                }
            }
        }
    }

    private fun updateQuantity(cartEntity: CartEntity, newQty: Int) {
        lifecycleScope.launch {
            val product = cartItemsList.find { it.cart.productId == cartEntity.productId }?.product
            val result = CartRepository.updateQuantity(this@CartActivity, cartEntity, newQty, product)
            if (result.isFailure) {
                UiFeedback.showError(this@CartActivity, "Failed to update bag: ${result.exceptionOrNull()?.localizedMessage}")
            }
            loadCart()
        }
    }

    private fun removeItem(cartEntity: CartEntity) {
        lifecycleScope.launch {
            val removedItem = cartEntity
            val product = cartItemsList.find { it.cart.productId == cartEntity.productId }?.product
            val result = CartRepository.removeFromCart(this@CartActivity, cartEntity)
            if (result.isSuccess) {
                UiFeedback.showInfo(
                    this@CartActivity,
                    "Item removed from bag",
                    actionText = "Undo"
                ) {
                    if (product != null) {
                        lifecycleScope.launch {
                            CartRepository.addToCart(this@CartActivity, product, removedItem.size, removedItem.quantity)
                            loadCart()
                        }
                    }
                }
            } else {
                UiFeedback.showError(this@CartActivity, "Failed to remove item: ${result.exceptionOrNull()?.localizedMessage}")
            }
            loadCart()
        }
    }
}
