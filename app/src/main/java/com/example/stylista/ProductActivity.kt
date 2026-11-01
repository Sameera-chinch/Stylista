package com.example.stylista

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.stylista.database.CartEntity
import com.example.stylista.database.ProductEntity
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.WishlistEntity
import com.example.stylista.utils.CartRepository
import com.example.stylista.utils.NavigationHelper
import com.example.stylista.utils.RecommendationEngine
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.example.stylista.utils.WishlistRepository
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class ProductActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var currentProduct: ProductEntity? = null
    private var selectedSize: String? = null
    private var isWishlisted: Boolean = false

    // Views
    private lateinit var ivProductImage: ImageView
    private lateinit var tvProductName: TextView
    private lateinit var tvProductPrice: TextView
    private lateinit var tvCategoryTag: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvSelectedSizeIndicator: TextView
    private lateinit var tvColorTitle: TextView
    private lateinit var chipGroupSizes: ChipGroup
    private lateinit var btnTopBack: TextView
    private lateinit var btnTopWishlist: TextView
    private lateinit var btnBottomWishlist: Button
    private lateinit var btnAddBag: Button
    private lateinit var rvSimilarProducts: androidx.recyclerview.widget.RecyclerView
    private lateinit var layoutSimilarProducts: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initViews()
        setupListeners()

        val productId = intent.getIntExtra("product_id", -1)
        val productNameExtra = intent.getStringExtra("product_name")

        loadProductData(productId, productNameExtra)
    }

    private fun initViews() {
        ivProductImage = findViewById(R.id.productImage)
        tvProductName = findViewById(R.id.productName)
        tvProductPrice = findViewById(R.id.productPrice)
        tvCategoryTag = findViewById(R.id.tvProductCategoryTag)
        tvDescription = findViewById(R.id.tvProductDescription)
        tvSelectedSizeIndicator = findViewById(R.id.tvSelectedSizeIndicator)
        tvColorTitle = findViewById(R.id.tvColorTitle)
        chipGroupSizes = findViewById(R.id.chipGroupSizes)
        btnTopBack = findViewById(R.id.btnProductBack)
        btnTopWishlist = findViewById(R.id.btnWishlist)
        btnBottomWishlist = findViewById(R.id.btnAddWishlist)
        btnAddBag = findViewById(R.id.btnAddBag)
        rvSimilarProducts = findViewById(R.id.rvSimilarProducts)
        layoutSimilarProducts = findViewById(R.id.layoutSimilarProducts)
    }

    private fun setupListeners() {
        btnTopBack.setOnClickListener {
            finish()
        }

        btnTopWishlist.setOnClickListener {
            toggleWishlist()
        }

        btnBottomWishlist.setOnClickListener {
            toggleWishlist()
        }

        btnAddBag.setOnClickListener {
            addToBag()
        }
    }

    private fun loadProductData(productId: Int, productNameFallback: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val roomDb = StylistaDatabase.getInstance(this@ProductActivity)
            val product = if (productId != -1) {
                roomDb.productDao().getProductById(productId)
            } else if (!productNameFallback.isNullOrBlank()) {
                roomDb.productDao().getProductByName(productNameFallback)
            } else {
                roomDb.productDao().getProductById(1)
            }

            if (product != null) {
                currentProduct = product
                val userId = UserPreferencesHelper.getActiveUserId(this@ProductActivity)
                isWishlisted = roomDb.wishlistDao().isProductWishlisted(userId, product.id)
                UserPreferencesHelper.recordProductView(this@ProductActivity, product.id)

                withContext(Dispatchers.Main) {
                    bindProductDetails(product)
                    setupSizes(product)
                    setupColors(product)
                    updateWishlistUI()
                    loadSimilarProducts(product)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProductActivity, "Product details unavailable", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun bindProductDetails(product: ProductEntity) {
        tvProductName.text = product.name
        val formattedPrice = "₹" + NumberFormat.getNumberInstance(Locale.US).format(product.price)
        tvProductPrice.text = formattedPrice

        val categoryText = if (product.subcategory.isNotBlank()) {
            "${product.category} • ${product.subcategory}".uppercase()
        } else {
            product.category.uppercase()
        }
        tvCategoryTag.text = categoryText

        if (product.description.isNotBlank()) {
            tvDescription.text = product.description
        }

        com.example.stylista.utils.ImageLoaderHelper.loadProductImage(ivProductImage, product)
    }

    private fun setupSizes(product: ProductEntity) {
        chipGroupSizes.removeAllViews()
        val allSizes = product.getSizeList()
        val inStockSizes = product.getInStockList()

        if (allSizes.isEmpty()) {
            selectedSize = "Free Size"
            tvSelectedSizeIndicator.text = "One Size fits all"
            return
        }

        if (product.isUniversalSize()) {
            selectedSize = allSizes.first()
            val chip = createSizeChip(allSizes.first(), isInStock = true)
            chip.isChecked = true
            chipGroupSizes.addView(chip)
            tvSelectedSizeIndicator.text = "One Size fits all (In Stock)"
            return
        }

        for (size in allSizes) {
            val inStock = inStockSizes.any { it.equals(size, ignoreCase = true) }
            val chip = createSizeChip(size, inStock)
            chipGroupSizes.addView(chip)
        }

        chipGroupSizes.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) {
                selectedSize = null
                tvSelectedSizeIndicator.text = "Please select a size"
                tvSelectedSizeIndicator.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            } else {
                val chip = findViewById<Chip>(checkedIds.first())
                val chosenSize = chip?.tag as? String ?: chip?.text?.toString() ?: ""
                selectedSize = chosenSize
                tvSelectedSizeIndicator.text = "Size: $chosenSize (In Stock)"
                tvSelectedSizeIndicator.setTextColor(ContextCompat.getColor(this, R.color.charcoal))
            }
        }
    }

    private fun createSizeChip(size: String, isInStock: Boolean): Chip {
        val chip = Chip(this)
        chip.tag = size
        chip.isCheckable = isInStock
        chip.isClickable = isInStock
        chip.isEnabled = isInStock

        if (isInStock) {
            chip.text = size
            chip.chipBackgroundColor = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    ContextCompat.getColor(this, R.color.butter_yellow),
                    ContextCompat.getColor(this, R.color.white)
                )
            )
            chip.chipStrokeColor = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    ContextCompat.getColor(this, R.color.charcoal),
                    ContextCompat.getColor(this, R.color.card_border)
                )
            )
            chip.chipStrokeWidth = 3f
            chip.setTextColor(ContextCompat.getColor(this, R.color.charcoal))
        } else {
            chip.text = "$size (Out of stock)"
            chip.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.neutral_light))
            chip.chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.neutral_divider))
            chip.chipStrokeWidth = 2f
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
        }

        chip.textSize = 13f
        chip.shapeAppearanceModel = chip.shapeAppearanceModel.toBuilder().setAllCornerSizes(16f).build()
        return chip
    }

    private fun addToBag() {
        val product = currentProduct ?: return

        if (selectedSize.isNullOrBlank()) {
            tvSelectedSizeIndicator.setTextColor(ContextCompat.getColor(this, R.color.error_red))
            UiFeedback.showWarning(this, "Please select an available size")
            return
        }

        val size = selectedSize!!

        lifecycleScope.launch {
            val result = CartRepository.addToCart(this@ProductActivity, product, size, 1)
            if (result.isSuccess) {
                if (UserPreferencesHelper.isGuestUser() && CartRepository.shouldShowGuestTip(this@ProductActivity)) {
                    UiFeedback.showInfo(this@ProductActivity, "Sign in to save your bag to your account")
                } else {
                    UiFeedback.showSuccess(
                        this@ProductActivity,
                        "${product.name} (Size $size) added to bag!",
                        actionText = "View Bag"
                    ) {
                        NavigationHelper.openCart(this@ProductActivity)
                    }
                }
            } else {
                UiFeedback.showError(
                    this@ProductActivity,
                    "Failed to save item: ${result.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    private fun toggleWishlist() {
        val product = currentProduct ?: return

        lifecycleScope.launch {
            val result = WishlistRepository.toggleWishlist(this@ProductActivity, product)
            if (result.isSuccess) {
                isWishlisted = result.getOrDefault(false)
                updateWishlistUI()
                if (isWishlisted) {
                    UiFeedback.showSuccess(this@ProductActivity, "Added to Wishlist")
                } else {
                    UiFeedback.showInfo(this@ProductActivity, "Removed from Wishlist")
                }
            } else {
                UiFeedback.showError(
                    this@ProductActivity,
                    "Could not update Wishlist: ${result.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    private fun updateWishlistUI() {
        val iconRes = if (isWishlisted) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
        val tintColor = if (isWishlisted) {
            ContextCompat.getColor(this, R.color.error_red)
        } else {
            ContextCompat.getColor(this, R.color.charcoal)
        }

        btnTopWishlist.text = ""
        btnTopWishlist.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        btnTopWishlist.compoundDrawableTintList = ColorStateList.valueOf(tintColor)

        btnBottomWishlist.text = ""
        btnBottomWishlist.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        btnBottomWishlist.compoundDrawableTintList = ColorStateList.valueOf(tintColor)
    }

    private fun setupColors(product: ProductEntity) {
        val blue = findViewById<View>(R.id.colorBlue)
        val pink = findViewById<View>(R.id.colorPink)
        val green = findViewById<View>(R.id.colorGreen)
        val cream = findViewById<View>(R.id.colorCream)

        when {
            product.subcategory.equals("Jewelry", ignoreCase = true) -> {
                tvColorTitle.text = "Finish: 18K Gold Plated"
                blue?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#E6CA65"))
                pink?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#D8D8D8"))
                green?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#B76E79"))
                cream?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#FFFDD0"))

                blue?.setOnClickListener { tvColorTitle.text = "Finish: 18K Gold Plated" }
                pink?.setOnClickListener { tvColorTitle.text = "Finish: Sterling Silver" }
                green?.setOnClickListener { tvColorTitle.text = "Finish: Rose Gold" }
                cream?.setOnClickListener { tvColorTitle.text = "Finish: Natural Pearl" }
            }
            product.subcategory.equals("Watches", ignoreCase = true) -> {
                tvColorTitle.text = "Finish: Rose Gold Mesh"
                blue?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#B76E79"))
                pink?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#212529"))
                green?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#C0C0C0"))
                cream?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#8B5A2B"))

                blue?.setOnClickListener { tvColorTitle.text = "Finish: Rose Gold Mesh" }
                pink?.setOnClickListener { tvColorTitle.text = "Finish: Matte Black" }
                green?.setOnClickListener { tvColorTitle.text = "Finish: Classic Steel" }
                cream?.setOnClickListener { tvColorTitle.text = "Finish: Vintage Tan" }
            }
            product.subcategory.equals("Sunglasses", ignoreCase = true) -> {
                tvColorTitle.text = "Frame: Classic Black"
                blue?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#1A1A1A"))
                pink?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#5A3825"))
                green?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#C5A059"))
                cream?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#4A6B82"))

                blue?.setOnClickListener { tvColorTitle.text = "Frame: Classic Black" }
                pink?.setOnClickListener { tvColorTitle.text = "Frame: Havana Tortoise" }
                green?.setOnClickListener { tvColorTitle.text = "Frame: Champagne Gold" }
                cream?.setOnClickListener { tvColorTitle.text = "Frame: Polarized Slate" }
            }
            product.subcategory.equals("Bags", ignoreCase = true) -> {
                tvColorTitle.text = "Color: Cognac Brown"
                blue?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#8B4513"))
                pink?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#1C1C1C"))
                green?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#FFFDD0"))
                cream?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#A2C2E8"))

                blue?.setOnClickListener { tvColorTitle.text = "Color: Cognac Brown" }
                pink?.setOnClickListener { tvColorTitle.text = "Color: Noir Black" }
                green?.setOnClickListener { tvColorTitle.text = "Color: Butter Cream" }
                cream?.setOnClickListener { tvColorTitle.text = "Color: Pastel Blue" }
            }
            else -> {
                tvColorTitle.text = "Color: Pastel Baby Blue"
                blue?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#A2C2E8"))
                pink?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#F8BBD0"))
                green?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#C8E6C9"))
                cream?.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#FFF3B0"))

                blue?.setOnClickListener { tvColorTitle.text = "Color: Pastel Baby Blue" }
                pink?.setOnClickListener { tvColorTitle.text = "Color: Soft Rose Pink" }
                green?.setOnClickListener { tvColorTitle.text = "Color: Sage Green" }
                cream?.setOnClickListener { tvColorTitle.text = "Color: Butter Cream" }
            }
        }
    }

    private fun loadSimilarProducts(product: ProductEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val similar = RecommendationEngine.getSimilarProducts(this@ProductActivity, product, limit = 6)
            withContext(Dispatchers.Main) {
                if (similar.isNotEmpty()) {
                    layoutSimilarProducts.visibility = View.VISIBLE
                    rvSimilarProducts.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                        this@ProductActivity,
                        androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                        false
                    )
                    rvSimilarProducts.adapter = RecommendedProductAdapter(similar) { clicked ->
                        NavigationHelper.openProductDetails(this@ProductActivity, clicked.id)
                    }
                } else {
                    layoutSimilarProducts.visibility = View.GONE
                }
            }
        }
    }
}
