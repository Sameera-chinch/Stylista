package com.example.stylista

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.stylista.utils.NavigationHelper

class CategoriesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)

        // Back button
        findViewById<TextView>(R.id.btnCategoriesBack)?.setOnClickListener {
            finish()
        }

        // Search trigger
        findViewById<View>(R.id.categorySearch)?.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        // Category Cards
        findViewById<View>(R.id.categoryWesternLarge)?.setOnClickListener {
            openCategoryFlow("Western")
        }

        findViewById<View>(R.id.categoryEthnicLarge)?.setOnClickListener {
            openCategoryFlow("Ethnic")
        }

        findViewById<View>(R.id.categorySportsLarge)?.setOnClickListener {
            openCategoryFlow("Sports")
        }

        findViewById<View>(R.id.categoryMaternityLarge)?.setOnClickListener {
            openCategoryFlow("Maternity")
        }

        findViewById<View>(R.id.categoryFormalLarge)?.setOnClickListener {
            openCategoryFlow("Formal")
        }

        findViewById<View>(R.id.categoryAccessoriesLarge)?.setOnClickListener {
            openCategoryFlow("Accessories")
        }

        findViewById<View>(R.id.categoryFootwearLarge)?.setOnClickListener {
            val intent = Intent(this, ProductListingActivity::class.java).apply {
                putExtra("category", "Sports")
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.categoryBagsLarge)?.setOnClickListener {
            val intent = Intent(this, ProductListingActivity::class.java).apply {
                putExtra("category", "Accessories")
                putExtra("subcategory", "Bags")
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.categoryBeautyLarge)?.setOnClickListener {
            val intent = Intent(this, ProductListingActivity::class.java).apply {
                putExtra("category", "Accessories")
                putExtra("subcategory", "Hair Accessories")
            }
            startActivity(intent)
        }

        NavigationHelper.setupBottomNav(this, R.id.navCategories)
    }

    private fun openCategoryFlow(category: String) {
        if (category.equals("Accessories", ignoreCase = true)) {
            val intent = Intent(this, SubcategoriesActivity::class.java)
            intent.putExtra("category", category)
            startActivity(intent)
        } else {
            val intent = Intent(this, ProductListingActivity::class.java)
            intent.putExtra("category", category)
            startActivity(intent)
        }
    }
}
