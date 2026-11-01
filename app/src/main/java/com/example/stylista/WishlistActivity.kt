package com.example.stylista

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stylista.database.CartEntity
import com.example.stylista.database.ProductEntity
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.WishlistEntity
import com.example.stylista.utils.CartRepository
import com.example.stylista.utils.ImageLoaderHelper
import com.example.stylista.utils.NavigationHelper
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.example.stylista.utils.WishlistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WishlistActivity : AppCompatActivity() {

    private lateinit var wishlistContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wishlist)

        wishlistContainer = findViewById(R.id.wishlistContainer)

        setupBackButton()
        setupBottomNavigation()

        loadWishlist()
    }

    // =====================================================
    // BACK
    // =====================================================
    private fun setupBackButton() {
        findViewById<TextView>(R.id.btnWishlistBack).setOnClickListener {
            finish()
        }
    }

    // =====================================================
    // LOAD WISHLIST FROM ROOM DATABASE
    // =====================================================
    private fun loadWishlist() {
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = UserPreferencesHelper.getActiveUserId(this@WishlistActivity)
            val db = StylistaDatabase.getInstance(this@WishlistActivity)
            val wishlistEntities = db.wishlistDao().getWishlist(userId)

            val wishlistProductsUI = mutableListOf<WishlistProductUI>()

            val productIds = wishlistEntities.map { it.productId }.distinct()
            val products = if (productIds.isNotEmpty()) db.productDao().getProductsByIds(productIds) else emptyList()
            val productMap = products.associateBy { it.id }

            for (item in wishlistEntities) {
                val product = productMap[item.productId]
                if (product != null) {
                    wishlistProductsUI.add(
                        WishlistProductUI(
                            wishlistEntity = item,
                            productEntity = product
                        )
                    )
                }
            }

            withContext(Dispatchers.Main) {
                wishlistContainer.removeAllViews()

                if (wishlistProductsUI.isEmpty()) {
                    showEmptyWishlist()
                } else {
                    wishlistProductsUI.forEach { itemUI ->
                        addWishlistProduct(itemUI)
                    }
                }
            }
        }
    }

    // =====================================================
    // PRODUCT UI MODEL
    // =====================================================
    private data class WishlistProductUI(
        val wishlistEntity: WishlistEntity,
        val productEntity: ProductEntity
    )

    // =====================================================
    // ADD PRODUCT
    // =====================================================
    private fun addWishlistProduct(itemUI: WishlistProductUI) {
        val wishlistEntity = itemUI.wishlistEntity
        val product = itemUI.productEntity
        val formattedPrice = "₹${product.price}"

        val cardView = LayoutInflater.from(this).inflate(
            R.layout.item_wishlist_card,
            wishlistContainer,
            false
        )

        val ivImage = cardView.findViewById<ImageView>(R.id.ivWishlistImage)
        val tvCategory = cardView.findViewById<TextView>(R.id.tvWishlistCategory)
        val tvName = cardView.findViewById<TextView>(R.id.tvWishlistName)
        val tvPrice = cardView.findViewById<TextView>(R.id.tvWishlistPrice)
        val btnHeart = cardView.findViewById<TextView>(R.id.btnWishlistHeart)
        val btnAddBag = cardView.findViewById<TextView>(R.id.btnWishlistAddBag)

        ImageLoaderHelper.loadProductImage(ivImage, product)
        tvCategory.text = product.category.uppercase()
        tvName.text = product.name
        tvPrice.text = formattedPrice

        // Remove from wishlist
        btnHeart.setOnClickListener {
            lifecycleScope.launch {
                val result = WishlistRepository.toggleWishlist(this@WishlistActivity, product)
                if (result.isSuccess) {
                    UiFeedback.showInfo(
                        this@WishlistActivity,
                        "Removed from Wishlist",
                        actionText = "Undo"
                    ) {
                        lifecycleScope.launch {
                            WishlistRepository.toggleWishlist(this@WishlistActivity, product)
                            loadWishlist()
                        }
                    }
                    loadWishlist()
                } else {
                    UiFeedback.showError(this@WishlistActivity, "Failed to update wishlist: ${result.exceptionOrNull()?.localizedMessage}")
                }
            }
        }

        // Add to Bag directly from wishlist
        btnAddBag.setOnClickListener {
            val targetSize = product.getInStockList().firstOrNull() ?: "Free Size"
            lifecycleScope.launch {
                val result = CartRepository.addToCart(this@WishlistActivity, product, targetSize, 1)
                if (result.isSuccess) {
                    UiFeedback.showSuccess(
                        this@WishlistActivity,
                        "Added ${product.name} to Bag!",
                        actionText = "View Bag"
                    ) {
                        NavigationHelper.openCart(this@WishlistActivity)
                    }
                } else {
                    UiFeedback.showError(this@WishlistActivity, "Failed to add to Bag: ${result.exceptionOrNull()?.localizedMessage}")
                }
            }
        }

        // Product click
        cardView.setOnClickListener {
            openProduct(product)
        }

        wishlistContainer.addView(cardView)
    }

    // =====================================================
    // EMPTY STATE
    // =====================================================
    private fun showEmptyWishlist() {
        val emptyCard = LayoutInflater.from(this).inflate(
            R.layout.item_wishlist_card,
            wishlistContainer,
            false
        ) as com.google.android.material.card.MaterialCardView

        emptyCard.removeAllViews()
        val emptyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(24.dp(), 40.dp(), 24.dp(), 40.dp())
        }

        val emptyIcon = TextView(this).apply {
            text = "♡"
            textSize = 48f
            setTextColor(Color.parseColor("#A2C2E8"))
            gravity = android.view.Gravity.CENTER
        }

        val emptyTitle = TextView(this).apply {
            text = "Your Wishlist is Empty"
            textSize = 18f
            setTextColor(Color.parseColor("#212529"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 12.dp(), 0, 4.dp())
        }

        val emptySubtitle = TextView(this).apply {
            text = "Explore the new collections and tap the heart icon to save styles you love."
            textSize = 13f
            setTextColor(Color.parseColor("#6C757D"))
            gravity = android.view.Gravity.CENTER
        }

        val exploreButton = android.widget.Button(this).apply {
            text = "Explore Fashion"
            setBackgroundResource(R.drawable.button_butter_yellow)
            backgroundTintList = null
            setTextColor(Color.parseColor("#212529"))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(200.dp(), 48.dp()).apply {
                topMargin = 20.dp()
            }
            setOnClickListener {
                startActivity(Intent(this@WishlistActivity, ProductListingActivity::class.java))
            }
        }

        emptyLayout.addView(emptyIcon)
        emptyLayout.addView(emptyTitle)
        emptyLayout.addView(emptySubtitle)
        emptyLayout.addView(exploreButton)
        emptyCard.addView(emptyLayout)

        wishlistContainer.addView(emptyCard)
    }

    // =====================================================
    // OPEN PRODUCT
    // =====================================================
    private fun openProduct(product: ProductEntity) {
        val intent = Intent(this, ProductActivity::class.java).apply {
            putExtra("product_id", product.id)
            putExtra("product_name", product.name)
            putExtra("product_price", "₹${product.price}")
            putExtra("product_image", product.imageRes)
        }
        startActivity(intent)
    }

    // =====================================================
    // BOTTOM NAVIGATION
    // =====================================================
    private fun setupBottomNavigation() {
        NavigationHelper.setupBottomNav(this, R.id.navWishlist)
    }

    override fun onResume() {
        super.onResume()
        if (::wishlistContainer.isInitialized) {
            loadWishlist()
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
