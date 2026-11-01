package com.example.stylista

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.utils.NavigationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubcategoriesActivity : AppCompatActivity() {

    private var categoryName = "Accessories"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subcategories)

        categoryName = intent.getStringExtra("category") ?: "Accessories"

        val titleTv = findViewById<TextView>(R.id.tvSubcategoriesTitle)
        val subtitleTv = findViewById<TextView>(R.id.tvSubcategoriesSubtitle)
        val backButton = findViewById<TextView>(R.id.btnSubcategoriesBack)

        titleTv.text = categoryName.uppercase()
        if (categoryName.equals("Accessories", ignoreCase = true)) {
            subtitleTv.text = "Complete your look with the perfect accessories"
        } else {
            subtitleTv.text = "Explore top $categoryName collections"
        }

        backButton.setOnClickListener {
            finish()
        }

        // View all products in category action
        findViewById<View>(R.id.cardViewAllCategoryProducts)?.setOnClickListener {
            val intent = Intent(this, ProductListingActivity::class.java).apply {
                putExtra("category", categoryName)
                putExtra("subcategory", "")
            }
            startActivity(intent)
        }

        findViewById<TextView>(R.id.tvViewAllActionTitle)?.text = "View All $categoryName Styles"

        NavigationHelper.setupBottomNav(this, R.id.navCategories)

        loadDynamicSubcategories()
    }

    private fun loadDynamicSubcategories() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = StylistaDatabase.getInstance(this@SubcategoriesActivity)
            val subcategories = db.productDao().getSubcategoriesByCategory(categoryName)
            val allCategoryProducts = db.productDao().getProductsByCategory(categoryName)

            withContext(Dispatchers.Main) {
                if (categoryName.equals("Accessories", ignoreCase = true)) {
                    setupStaticAccessoriesCards()
                } else {
                    renderDynamicSubcategoryCards(subcategories, allCategoryProducts)
                }
            }
        }
    }

    private fun setupStaticAccessoriesCards() {
        findViewById<View>(R.id.cardBags)?.setOnClickListener { openSubcategoryProducts("Bags") }
        findViewById<View>(R.id.cardJewelry)?.setOnClickListener { openSubcategoryProducts("Jewelry") }
        findViewById<View>(R.id.cardSunglasses)?.setOnClickListener { openSubcategoryProducts("Sunglasses") }
        findViewById<View>(R.id.cardWatches)?.setOnClickListener { openSubcategoryProducts("Watches") }
        findViewById<View>(R.id.cardBelts)?.setOnClickListener { openSubcategoryProducts("Belts") }
        findViewById<View>(R.id.cardHairAccessories)?.setOnClickListener { openSubcategoryProducts("Hair Accessories") }
    }

    private fun renderDynamicSubcategoryCards(
        subcategories: List<String>,
        products: List<com.example.stylista.database.ProductEntity>
    ) {
        val container = findViewById<LinearLayout>(R.id.containerSubcategoriesCards) ?: return
        container.removeAllViews()

        if (subcategories.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No subcategories found for $categoryName"
                setTextColor(ContextCompat.getColor(this@SubcategoriesActivity, R.color.text_secondary))
                textSize = 14f
                setPadding(16, 32, 16, 32)
                gravity = Gravity.CENTER
            }
            container.addView(emptyTv)
            return
        }

        for (subcategory in subcategories) {
            val count = products.count { it.subcategory.equals(subcategory, ignoreCase = true) }
            val card = createDynamicSubcategoryCard(subcategory, count)
            container.addView(card)
        }
    }

    private fun createDynamicSubcategoryCard(subcategory: String, count: Int): View {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12)
            }
            layoutParams = params
            radius = dpToPx(16).toFloat()
            cardElevation = dpToPx(1).toFloat()
            strokeWidth = dpToPx(1)
            strokeColor = ContextCompat.getColor(this@SubcategoriesActivity, R.color.card_border)
            setCardBackgroundColor(ContextCompat.getColor(this@SubcategoriesActivity, R.color.white))
            isClickable = true
            isFocusable = true
        }

        val innerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        val titleTv = TextView(this).apply {
            text = subcategory
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@SubcategoriesActivity, R.color.charcoal))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val countTv = TextView(this).apply {
            text = if (count == 1) "1 item available" else "$count items available"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@SubcategoriesActivity, R.color.text_secondary))
        }

        textLayout.addView(titleTv)
        textLayout.addView(countTv)

        val chevron = TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(ContextCompat.getColor(this@SubcategoriesActivity, R.color.charcoal))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        innerLayout.addView(textLayout)
        innerLayout.addView(chevron)
        card.addView(innerLayout)

        card.setOnClickListener {
            openSubcategoryProducts(subcategory)
        }

        return card
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun openSubcategoryProducts(subcategory: String) {
        val intent = Intent(this, ProductListingActivity::class.java).apply {
            putExtra("category", categoryName)
            putExtra("subcategory", subcategory)
        }
        startActivity(intent)
    }
}
