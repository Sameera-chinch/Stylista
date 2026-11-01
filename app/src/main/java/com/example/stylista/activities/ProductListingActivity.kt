package com.example.stylista.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stylista.database.entities.ProductEntity
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.entities.WishlistEntity
import com.example.stylista.utils.NavigationHelper
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.example.stylista.repositories.WishlistRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

class ProductListingActivity : AppCompatActivity() {

    private var categoryName: String = "Western"
    private var activeSubcategory: String = ""

    private lateinit var rvProducts: RecyclerView
    private lateinit var tvNoProducts: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var layoutSubcategoryPills: LinearLayout

    private lateinit var btnFilter: TextView
    private lateinit var btnSort: TextView
    private lateinit var btnFilterPrice: TextView
    private lateinit var btnFilterSize: TextView

    private var allCategoryProducts = listOf<ProductEntity>()
    private var wishlistedIds = mutableSetOf<Int>()

    // Filter states
    private var activeMinPrice: Int = 0
    private var activeMaxPrice: Int = Int.MAX_VALUE
    private var activeSizes: MutableSet<String> = mutableSetOf()
    private var activeInStockOnly: Boolean = false
    private var activeSortMode: Int = SORT_FEATURED

    companion object {
        const val SORT_FEATURED = 0
        const val SORT_PRICE_LOW_HIGH = 1
        const val SORT_PRICE_HIGH_LOW = 2
        const val SORT_RATING = 3
        const val SORT_NEWEST = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_listing)

        categoryName = intent.getStringExtra("category") ?: "Western"
        activeSubcategory = intent.getStringExtra("subcategory") ?: ""

        val backButton = findViewById<TextView>(R.id.btnListingBack)
        tvTitle = findViewById(R.id.tvListingTitle)
        tvSubtitle = findViewById(R.id.tvListingSubtitle)
        rvProducts = findViewById(R.id.rvProducts)
        tvNoProducts = findViewById(R.id.tvNoProducts)
        layoutSubcategoryPills = findViewById(R.id.layoutSubcategoryPills)

        btnFilter = findViewById(R.id.btnFilter)
        btnSort = findViewById(R.id.btnSort)
        btnFilterPrice = findViewById(R.id.btnFilterPrice)
        btnFilterSize = findViewById(R.id.btnFilterSize)

        rvProducts.layoutManager = GridLayoutManager(this, 2)

        updateHeaderTitles()

        backButton?.setOnClickListener { finish() }

        findViewById<View>(R.id.btnListingSearch)?.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        findViewById<View>(R.id.btnListingCart)?.setOnClickListener {
            NavigationHelper.openCart(this)
        }

        btnFilter.setOnClickListener { showFilterBottomSheet() }
        btnFilterPrice.setOnClickListener { showFilterBottomSheet() }
        btnFilterSize.setOnClickListener { showFilterBottomSheet() }
        btnSort.setOnClickListener { showSortBottomSheet() }

        NavigationHelper.setupBottomNav(this, R.id.navCategories)

        loadProducts()
    }

    private fun updateHeaderTitles() {
        if (activeSubcategory.isNotBlank()) {
            tvTitle.text = activeSubcategory.uppercase()
            tvSubtitle.text = "$categoryName • $activeSubcategory"
        } else {
            tvTitle.text = categoryName.uppercase()
            tvSubtitle.text = "All $categoryName Styles"
        }
    }

    private fun loadProducts() {
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = UserPreferencesHelper.getActiveUserId(this@ProductListingActivity)
            val db = StylistaDatabase.getInstance(this@ProductListingActivity)

            val products = if (categoryName.equals("All", ignoreCase = true)) {
                db.productDao().getAllProducts()
            } else {
                db.productDao().getProductsByCategory(categoryName)
            }

            val dynamicSubcats = if (categoryName.equals("All", ignoreCase = true)) {
                emptyList()
            } else {
                db.productDao().getSubcategoriesByCategory(categoryName)
            }

            val wishlistItems = db.wishlistDao().getWishlist(userId)
            val wishIds = wishlistItems.map { it.productId }.toSet()

            withContext(Dispatchers.Main) {
                allCategoryProducts = products
                wishlistedIds.clear()
                wishlistedIds.addAll(wishIds)

                setupDynamicSubcategoryPills(dynamicSubcats)
                applyFiltersAndSort()
            }
        }
    }

    private fun setupDynamicSubcategoryPills(subcategories: List<String>) {
        layoutSubcategoryPills.removeAllViews()

        val allCategoriesPill = createPillView("All", activeSubcategory.isBlank()) {
            activeSubcategory = ""
            updateHeaderTitles()
            setupDynamicSubcategoryPills(subcategories)
            applyFiltersAndSort()
        }
        layoutSubcategoryPills.addView(allCategoriesPill)

        val subcatList = if (subcategories.isNotEmpty()) {
            subcategories
        } else {
            // Default fallback if not yet categorized
            listOf("Dresses", "Tops", "Jeans", "Co-ords", "Skirts")
        }

        for (subcat in subcatList) {
            val isSelected = activeSubcategory.equals(subcat, ignoreCase = true)
            val pill = createPillView(subcat, isSelected) {
                activeSubcategory = if (isSelected) "" else subcat
                updateHeaderTitles()
                setupDynamicSubcategoryPills(subcategories)
                applyFiltersAndSort()
            }
            layoutSubcategoryPills.addView(pill)
        }
    }

    private fun createPillView(title: String, isSelected: Boolean, onClick: () -> Unit): TextView {
        val pill = TextView(this)
        pill.text = title
        pill.textSize = 12f
        pill.gravity = android.view.Gravity.CENTER
        pill.setPadding(14.dp(), 6.dp(), 14.dp(), 6.dp())

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            32.dp()
        )
        params.marginEnd = 8.dp()
        pill.layoutParams = params

        if (isSelected) {
            pill.setBackgroundResource(R.drawable.bg_filter_pill_selected)
            pill.setTextColor(ContextCompat.getColor(this, R.color.charcoal))
            pill.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            pill.setBackgroundResource(R.drawable.bg_filter_pill)
            pill.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            pill.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        pill.setOnClickListener { onClick() }
        return pill
    }

    private fun applyFiltersAndSort() {
        var list = allCategoryProducts

        // Subcategory filter
        if (activeSubcategory.isNotBlank()) {
            list = list.filter {
                it.subcategory.equals(activeSubcategory, ignoreCase = true) ||
                        it.name.contains(activeSubcategory, ignoreCase = true)
            }
        }

        // Price range
        list = list.filter { it.price in activeMinPrice..activeMaxPrice }

        // Size filter
        if (activeSizes.isNotEmpty()) {
            list = list.filter { product ->
                activeSizes.any { size -> product.isSizeInStock(size) || product.isUniversalSize() }
            }
        }

        // In stock only
        if (activeInStockOnly) {
            list = list.filter { it.inStock }
        }

        // Sorting
        list = when (activeSortMode) {
            SORT_PRICE_LOW_HIGH -> list.sortedBy { it.price }
            SORT_PRICE_HIGH_LOW -> list.sortedByDescending { it.price }
            SORT_RATING -> list.sortedByDescending { it.rating }
            SORT_NEWEST -> list.sortedByDescending { it.createdAt.coerceAtLeast(it.id.toLong()) }
            else -> list.sortedByDescending { if (it.isFeatured) 1 else 0 }
        }

        // Update button indicators
        val hasActiveFilter = activeMinPrice > 0 || activeMaxPrice < Int.MAX_VALUE || activeSizes.isNotEmpty() || activeInStockOnly
        if (hasActiveFilter) {
            btnFilter.setBackgroundResource(R.drawable.bg_filter_pill_selected)
            btnFilter.text = "🎛 Filter (•)"
        } else {
            btnFilter.setBackgroundResource(R.drawable.bg_filter_pill)
            btnFilter.text = "🎛 Filter"
        }

        btnSort.text = when (activeSortMode) {
            SORT_PRICE_LOW_HIGH -> "⇅ Price ↑"
            SORT_PRICE_HIGH_LOW -> "⇅ Price ↓"
            SORT_RATING -> "⇅ Rating"
            SORT_NEWEST -> "⇅ Newest"
            else -> "⇅ Sort"
        }

        updateGridDisplay(list)
    }

    private fun updateGridDisplay(products: List<ProductEntity>) {
        if (products.isNotEmpty()) {
            tvNoProducts.visibility = View.GONE
            rvProducts.visibility = View.VISIBLE
            tvSubtitle.text = "Showing ${products.size} style${if (products.size > 1) "s" else ""}"

            rvProducts.adapter = ProductGridAdapter(
                products = products,
                wishlistedIds = wishlistedIds,
                onWishlistToggle = { product ->
                    toggleWishlist(product)
                },
                onProductClick = { product ->
                    NavigationHelper.openProductDetails(this@ProductListingActivity, product.id)
                }
            )
        } else {
            rvProducts.visibility = View.GONE
            tvNoProducts.visibility = View.VISIBLE
            tvNoProducts.text = "No styles match your filter criteria"
        }
    }

    private fun showFilterBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_filter, null)
        dialog.setContentView(view)

        val chipGroupPrice = view.findViewById<ChipGroup>(R.id.chipGroupPrice)
        val chipGroupSizes = view.findViewById<ChipGroup>(R.id.chipGroupSizes)
        val switchInStock = view.findViewById<MaterialSwitch>(R.id.switchInStockOnly)
        val btnApply = view.findViewById<Button>(R.id.btnApplyFilters)
        val btnReset = view.findViewById<TextView>(R.id.btnResetFilters)

        // Restore price selection
        when {
            activeMinPrice == 0 && activeMaxPrice == 1000 -> chipGroupPrice.check(R.id.chipPriceUnder1000)
            activeMinPrice == 1000 && activeMaxPrice == 2000 -> chipGroupPrice.check(R.id.chipPrice1000To2000)
            activeMinPrice == 2000 && activeMaxPrice == Int.MAX_VALUE -> chipGroupPrice.check(R.id.chipPriceAbove2000)
            else -> chipGroupPrice.check(R.id.chipPriceAll)
        }

        // Restore sizes
        if (activeSizes.contains("XS")) view.findViewById<Chip>(R.id.chipSizeXS)?.isChecked = true
        if (activeSizes.contains("S")) view.findViewById<Chip>(R.id.chipSizeS)?.isChecked = true
        if (activeSizes.contains("M")) view.findViewById<Chip>(R.id.chipSizeM)?.isChecked = true
        if (activeSizes.contains("L")) view.findViewById<Chip>(R.id.chipSizeL)?.isChecked = true
        if (activeSizes.contains("XL")) view.findViewById<Chip>(R.id.chipSizeXL)?.isChecked = true
        if (activeSizes.contains("One Size") || activeSizes.contains("Free Size")) view.findViewById<Chip>(R.id.chipSizeFree)?.isChecked = true

        switchInStock.isChecked = activeInStockOnly

        btnReset.setOnClickListener {
            activeMinPrice = 0
            activeMaxPrice = Int.MAX_VALUE
            activeSizes.clear()
            activeInStockOnly = false
            applyFiltersAndSort()
            dialog.dismiss()
        }

        btnApply.setOnClickListener {
            // Read price
            when (chipGroupPrice.checkedChipId) {
                R.id.chipPriceUnder1000 -> {
                    activeMinPrice = 0
                    activeMaxPrice = 1000
                }
                R.id.chipPrice1000To2000 -> {
                    activeMinPrice = 1000
                    activeMaxPrice = 2000
                }
                R.id.chipPriceAbove2000 -> {
                    activeMinPrice = 2000
                    activeMaxPrice = Int.MAX_VALUE
                }
                else -> {
                    activeMinPrice = 0
                    activeMaxPrice = Int.MAX_VALUE
                }
            }

            // Read sizes
            activeSizes.clear()
            val chipXS = view.findViewById<Chip>(R.id.chipSizeXS)
            val chipS = view.findViewById<Chip>(R.id.chipSizeS)
            val chipM = view.findViewById<Chip>(R.id.chipSizeM)
            val chipL = view.findViewById<Chip>(R.id.chipSizeL)
            val chipXL = view.findViewById<Chip>(R.id.chipSizeXL)
            val chipFree = view.findViewById<Chip>(R.id.chipSizeFree)

            if (chipXS?.isChecked == true) activeSizes.add("XS")
            if (chipS?.isChecked == true) activeSizes.add("S")
            if (chipM?.isChecked == true) activeSizes.add("M")
            if (chipL?.isChecked == true) activeSizes.add("L")
            if (chipXL?.isChecked == true) activeSizes.add("XL")
            if (chipFree?.isChecked == true) {
                activeSizes.add("One Size")
                activeSizes.add("Free Size")
            }

            activeInStockOnly = switchInStock.isChecked

            applyFiltersAndSort()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSortBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_sort, null)
        dialog.setContentView(view)

        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupSort)
        when (activeSortMode) {
            SORT_PRICE_LOW_HIGH -> radioGroup.check(R.id.radioSortPriceLowHigh)
            SORT_PRICE_HIGH_LOW -> radioGroup.check(R.id.radioSortPriceHighLow)
            SORT_NEWEST -> radioGroup.check(R.id.radioSortNewest)
            else -> radioGroup.check(R.id.radioSortFeatured)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            activeSortMode = when (checkedId) {
                R.id.radioSortPriceLowHigh -> SORT_PRICE_LOW_HIGH
                R.id.radioSortPriceHighLow -> SORT_PRICE_HIGH_LOW
                R.id.radioSortNewest -> SORT_NEWEST
                else -> SORT_FEATURED
            }
            applyFiltersAndSort()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun toggleWishlist(product: ProductEntity) {
        lifecycleScope.launch {
            val result = WishlistRepository.toggleWishlist(this@ProductListingActivity, product)
            if (result.isSuccess) {
                val isNowWishlisted = result.getOrDefault(false)
                if (isNowWishlisted) {
                    wishlistedIds.add(product.id)
                    UiFeedback.showSuccess(this@ProductListingActivity, "Added to Wishlist")
                } else {
                    wishlistedIds.remove(product.id)
                    UiFeedback.showInfo(this@ProductListingActivity, "Removed from Wishlist")
                }
                rvProducts.adapter?.notifyDataSetChanged()
            } else {
                UiFeedback.showError(this@ProductListingActivity, "Could not update Wishlist: ${result.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
