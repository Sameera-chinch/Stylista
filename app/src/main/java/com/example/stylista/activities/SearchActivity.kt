package com.example.stylista.activities

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
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

class SearchActivity : AppCompatActivity() {

    private lateinit var rvSearchResults: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnClearSearch: TextView
    private lateinit var btnSearchBack: TextView
    private lateinit var tvSearchResultCount: TextView
    private lateinit var layoutNoResults: View

    private var currentResults = listOf<ProductEntity>()
    private var wishlistedIds = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        initViews()
        setupListeners()

        val initialQuery = intent.getStringExtra("search_query")
        if (!initialQuery.isNullOrBlank()) {
            searchInput.setText(initialQuery)
            performSearch(initialQuery)
        } else {
            // Show all products by default to browse
            performSearch("")
        }
    }

    private fun initViews() {
        rvSearchResults = findViewById(R.id.rvSearchResults)
        searchInput = findViewById(R.id.searchInput)
        btnSearch = findViewById(R.id.btnSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        btnSearchBack = findViewById(R.id.btnSearchBack)
        tvSearchResultCount = findViewById(R.id.tvSearchResultCount)
        layoutNoResults = findViewById(R.id.layoutNoResults)

        rvSearchResults.layoutManager = GridLayoutManager(this, 2)
    }

    private fun setupListeners() {
        btnSearchBack.setOnClickListener {
            finish()
        }

        btnSearch.setOnClickListener {
            performSearch(searchInput.text.toString())
        }

        btnClearSearch.setOnClickListener {
            searchInput.setText("")
            performSearch("")
        }

        searchInput.doAfterTextChanged { text ->
            btnClearSearch.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchInput.text.toString())
                true
            } else {
                false
            }
        }
    }

    private fun performSearch(query: String) {
        val trimmed = query.trim()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = StylistaDatabase.getInstance(this@SearchActivity)
            val results = if (trimmed.isEmpty()) {
                db.productDao().getAllProducts()
            } else {
                db.productDao().searchProducts(trimmed)
            }

            val userId = UserPreferencesHelper.getActiveUserId(this@SearchActivity)
            val wishItems = db.wishlistDao().getWishlist(userId)
            val wishIds = wishItems.map { it.productId }.toSet()

            withContext(Dispatchers.Main) {
                currentResults = results
                wishlistedIds.clear()
                wishlistedIds.addAll(wishIds)

                if (results.isEmpty()) {
                    rvSearchResults.visibility = View.GONE
                    layoutNoResults.visibility = View.VISIBLE
                    tvSearchResultCount.text = "No results for \"$trimmed\""
                } else {
                    layoutNoResults.visibility = View.GONE
                    rvSearchResults.visibility = View.VISIBLE

                    tvSearchResultCount.text = if (trimmed.isEmpty()) {
                        "All Collection Styles (${results.size})"
                    } else {
                        "Found ${results.size} styles for \"$trimmed\""
                    }

                    rvSearchResults.adapter = ProductGridAdapter(
                        products = results,
                        wishlistedIds = wishlistedIds,
                        onWishlistToggle = { product ->
                            toggleWishlist(product)
                        },
                        onProductClick = { product ->
                            NavigationHelper.openProductDetails(this@SearchActivity, product.id)
                        }
                    )
                }
            }
        }
    }

    private fun toggleWishlist(product: ProductEntity) {
        lifecycleScope.launch {
            val result = WishlistRepository.toggleWishlist(this@SearchActivity, product)
            if (result.isSuccess) {
                val isNowWishlisted = result.getOrDefault(false)
                if (isNowWishlisted) {
                    wishlistedIds.add(product.id)
                    UiFeedback.showSuccess(this@SearchActivity, "Added to Wishlist")
                } else {
                    wishlistedIds.remove(product.id)
                    UiFeedback.showInfo(this@SearchActivity, "Removed from Wishlist")
                }
                rvSearchResults.adapter?.notifyDataSetChanged()
            } else {
                UiFeedback.showError(this@SearchActivity, "Could not update Wishlist: ${result.exceptionOrNull()?.localizedMessage}")
            }
        }
    }
}
