package com.example.stylista

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stylista.database.ProductEntity
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.WishlistEntity
import com.example.stylista.utils.NavigationHelper
import com.example.stylista.utils.ProductRepository
import com.example.stylista.utils.RecommendationEngine
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.example.stylista.utils.WishlistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private lateinit var rvHomeProducts: RecyclerView
    private var allProducts = listOf<ProductEntity>()
    private var wishlistedIds = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        initViews()
        setupTopButtons()
        setupCampusMap()
        requestPinToHomeScreen()
        setupSearch()
        setupOccasions()
        setupBottomNavigation()
        setupChatButton()

        lifecycleScope.launch(Dispatchers.IO) {
            ProductRepository.ensureDatabaseInitialized(applicationContext)
            ProductRepository.syncWithFirestore(applicationContext)
            val uid = UserPreferencesHelper.getActiveUserId(applicationContext)
            com.example.stylista.data.repository.OrderRepository.syncAllOrdersToFirestore(applicationContext, uid)
        }
    }

    override fun onResume() {
        super.onResume()
        loadProductsAndWishlist()
        setupRecommendations()
        updateCartBadge()
    }

    private fun initViews() {
        rvHomeProducts = findViewById(R.id.rvHomeProducts)
        rvHomeProducts.layoutManager = GridLayoutManager(this, 2)
        rvHomeProducts.isNestedScrollingEnabled = false
    }

    private fun showNotificationsBottomSheet() {
        com.example.stylista.utils.NotificationsHelper.show(this)
    }

    private fun setupCampusMap() {
        val cardCampusMap = findViewById<View>(R.id.cardCampusMap)
        cardCampusMap?.setOnClickListener {
            val intent = Intent(this, CampusMapActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupTopButtons() {
        // Use View to safely accommodate FrameLayout, TextView, or any container
        val menuButton = findViewById<View>(R.id.btnMenu)
        val campusMapButton = findViewById<View>(R.id.btnTopCampusMap)
        val wishlistButton = findViewById<View>(R.id.btnTopWishlist)
        val bagButton = findViewById<View>(R.id.btnCart)
        val bannerShopNow = findViewById<View>(R.id.btnBannerShopNow)
        val appIconButton = findViewById<View>(R.id.btnAppIconHome)

        appIconButton?.setOnClickListener {
            findViewById<androidx.core.widget.NestedScrollView>(R.id.homeScrollView)?.smoothScrollTo(0, 0)
        }

        menuButton?.setOnClickListener {
            val intent = Intent(this, CategoriesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }

        campusMapButton?.setOnClickListener {
            showNotificationsBottomSheet()
        }

        wishlistButton?.setOnClickListener {
            NavigationHelper.openWishlist(this)
        }

        bagButton?.setOnClickListener {
            NavigationHelper.openCart(this)
        }

        bannerShopNow?.setOnClickListener {
            startActivity(Intent(this, ProductListingActivity::class.java))
        }
    }

    private fun updateCartBadge() {
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = UserPreferencesHelper.getActiveUserId(this@HomeActivity)
            val db = StylistaDatabase.getInstance(this@HomeActivity)
            val cartCount = db.cartDao().getCartCount(userId)
            withContext(Dispatchers.Main) {
                val badge = findViewById<TextView>(R.id.tvHomeCartBadge)
                if (badge != null) {
                    if (cartCount > 0) {
                        badge.visibility = View.VISIBLE
                        badge.text = if (cartCount > 99) "99+" else cartCount.toString()
                    } else {
                        badge.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setupSearch() {
        val searchBar = findViewById<EditText>(R.id.searchBar)
        searchBar?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                openSearch(searchBar.text.toString())
                true
            } else {
                false
            }
        }
    }

    private fun openSearch(query: String) {
        val intent = Intent(this, SearchActivity::class.java)
        intent.putExtra("search_query", query.trim())
        startActivity(intent)
    }

    private fun setupOccasions() {
        val western = findViewById<View>(R.id.categoryWestern)
        val ethnic = findViewById<View>(R.id.categoryEthnic)
        val sports = findViewById<View>(R.id.categorySports)
        val formal = findViewById<View>(R.id.categoryFormal)
        val accessories = findViewById<View>(R.id.categoryAccessories)
        val maternity = findViewById<View>(R.id.categoryMaternity)

        western?.setOnClickListener { openCategoryFlow("Western") }
        ethnic?.setOnClickListener { openCategoryFlow("Ethnic") }
        sports?.setOnClickListener { openCategoryFlow("Sports") }
        formal?.setOnClickListener { openCategoryFlow("Formal") }
        accessories?.setOnClickListener { openCategoryFlow("Accessories") }
        maternity?.setOnClickListener { openCategoryFlow("Maternity") }

        val seeAllOccasions = findViewById<View>(R.id.tvSeeAllOccasions)
        seeAllOccasions?.setOnClickListener {
            val intent = Intent(this, CategoriesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }

        val seeAllProducts = findViewById<View>(R.id.tvSeeAllProducts)
        seeAllProducts?.setOnClickListener {
            val intent = Intent(this, DiscoverActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }
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

    private fun loadProductsAndWishlist() {
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = UserPreferencesHelper.getActiveUserId(this@HomeActivity)
            val db = StylistaDatabase.getInstance(this@HomeActivity)
            val products = db.productDao().getAllProducts()
            val wishlistItems = db.wishlistDao().getWishlist(userId)
            val wishIds = wishlistItems.map { it.productId }.toSet()

            withContext(Dispatchers.Main) {
                allProducts = products
                wishlistedIds.clear()
                wishlistedIds.addAll(wishIds)

                rvHomeProducts.adapter = ProductGridAdapter(
                    products = allProducts,
                    wishlistedIds = wishlistedIds,
                    onWishlistToggle = { product ->
                        toggleWishlist(product)
                    },
                    onProductClick = { product ->
                        NavigationHelper.openProductDetails(this@HomeActivity, product.id)
                    }
                )
            }
        }
    }

    private fun toggleWishlist(product: ProductEntity) {
        lifecycleScope.launch {
            val result = WishlistRepository.toggleWishlist(this@HomeActivity, product)
            if (result.isSuccess) {
                val isNowWishlisted = result.getOrDefault(false)
                if (isNowWishlisted) {
                    wishlistedIds.add(product.id)
                    UiFeedback.showSuccess(this@HomeActivity, "Added to Wishlist")
                } else {
                    wishlistedIds.remove(product.id)
                    UiFeedback.showInfo(this@HomeActivity, "Removed from Wishlist")
                }
                rvHomeProducts.adapter?.notifyDataSetChanged()
            } else {
                UiFeedback.showError(this@HomeActivity, "Could not update Wishlist: ${result.exceptionOrNull()?.localizedMessage}")
            }
        }
    }

    private fun setupBottomNavigation() {
        NavigationHelper.setupBottomNav(this, R.id.navHome)
    }

    private fun setupChatButton() {
        val btnOpenChat = findViewById<View>(R.id.btnOpenChat)
        btnOpenChat?.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRecommendations() {
        val layoutRecommendedSection = findViewById<View>(R.id.layoutRecommendedSection)
        val rvRecommendedProducts = findViewById<RecyclerView>(R.id.rvRecommendedProducts) ?: return
        val tvPickedForYouSubtitle = findViewById<TextView>(R.id.tvPickedForYouSubtitle)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uid = UserPreferencesHelper.getActiveUserId(this@HomeActivity)
                val styles = UserPreferencesHelper.getSelectedStyles(this@HomeActivity, uid)
                val recommended = RecommendationEngine.getHomeRecommendations(this@HomeActivity)
                withContext(Dispatchers.Main) {
                    if (recommended.isNotEmpty()) {
                        layoutRecommendedSection?.visibility = View.VISIBLE
                        if (styles.isNotEmpty() && tvPickedForYouSubtitle != null) {
                            tvPickedForYouSubtitle.text = "Handpicked for your style: ${styles.joinToString(", ")}"
                        }
                        rvRecommendedProducts.layoutManager = LinearLayoutManager(
                            this@HomeActivity,
                            LinearLayoutManager.HORIZONTAL,
                            false
                        )
                        rvRecommendedProducts.adapter = RecommendedProductAdapter(recommended) { product ->
                            NavigationHelper.openProductDetails(this@HomeActivity, product.id)
                        }
                    } else {
                        layoutRecommendedSection?.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    layoutRecommendedSection?.visibility = View.GONE
                }
            }
        }
    }

    private fun requestPinToHomeScreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val shortcutManager = getSystemService(android.content.pm.ShortcutManager::class.java)
                if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                    val shortcutIntent = Intent(this, SplashActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    }
                    val pinShortcutInfo = android.content.pm.ShortcutInfo.Builder(this, "stylista_shortcut")
                        .setShortLabel("Stylista")
                        .setLongLabel("Stylista Fashion")
                        .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher))
                        .setIntent(shortcutIntent)
                        .build()
                    shortcutManager.requestPinShortcut(pinShortcutInfo, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
