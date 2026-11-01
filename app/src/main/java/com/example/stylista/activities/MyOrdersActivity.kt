package com.example.stylista.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stylista.adapters.OrderAdapter
import com.example.stylista.repositories.OrderDetailsModel
import com.example.stylista.repositories.OrderRepository
import com.example.stylista.utils.UserPreferencesHelper
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
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

class MyOrdersActivity : AppCompatActivity() {

    private lateinit var rvOrders: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var layoutEmpty: View
    private lateinit var tvCount: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageView
    private lateinit var chipGroup: ChipGroup

    private lateinit var adapter: OrderAdapter
    private var allOrders: List<OrderDetailsModel> = emptyList()
    private var currentFilter: String = "ALL"
    private var currentQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_orders)

        initViews()
        setupListeners()

        val initialFilter = intent.getStringExtra("initial_filter") ?: "ALL"
        applyInitialFilter(initialFilter)

        loadOrders()
    }

    override fun onResume() {
        super.onResume()
        loadOrders()
    }

    private fun initViews() {
        findViewById<ImageView>(R.id.btnBackOrders).setOnClickListener { finish() }
        rvOrders = findViewById(R.id.rvOrders)
        pbLoading = findViewById(R.id.pbOrdersLoading)
        layoutEmpty = findViewById(R.id.layoutEmptyOrders)
        tvCount = findViewById(R.id.tvOrdersCount)
        etSearch = findViewById(R.id.etSearchOrders)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        chipGroup = findViewById(R.id.chipGroupOrders)

        rvOrders.layoutManager = LinearLayoutManager(this)
        adapter = OrderAdapter(
            context = this,
            orders = emptyList(),
            onTrackClick = { order ->
                val intent = Intent(this, TrackOrderActivity::class.java).apply {
                    putExtra("order_number", order.orderNumber)
                }
                startActivity(intent)
            },
            onDetailsClick = { order ->
                val intent = Intent(this, OrderDetailsActivity::class.java).apply {
                    putExtra("order_number", order.orderNumber)
                }
                startActivity(intent)
            }
        )
        rvOrders.adapter = adapter

        findViewById<Button>(R.id.btnEmptyStartShopping).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
    }

    private fun setupListeners() {
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            currentFilter = when (checkedIds.first()) {
                R.id.chipFilterActive -> "ACTIVE"
                R.id.chipFilterDelivered -> "DELIVERED"
                R.id.chipFilterCancelled -> "CANCELLED"
                else -> "ALL"
            }
            filterAndDisplay()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (currentQuery.isNotEmpty()) View.VISIBLE else View.GONE
                filterAndDisplay()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearch.setText("")
        }
    }

    private fun applyInitialFilter(filter: String) {
        when (filter.uppercase(Locale.getDefault())) {
            "ACTIVE", "PROCESSING", "SHIPPED", "TOPAY" -> {
                findViewById<Chip>(R.id.chipFilterActive)?.isChecked = true
                currentFilter = "ACTIVE"
            }
            "DELIVERED" -> {
                findViewById<Chip>(R.id.chipFilterDelivered)?.isChecked = true
                currentFilter = "DELIVERED"
            }
            "CANCELLED" -> {
                findViewById<Chip>(R.id.chipFilterCancelled)?.isChecked = true
                currentFilter = "CANCELLED"
            }
            else -> {
                findViewById<Chip>(R.id.chipFilterAll)?.isChecked = true
                currentFilter = "ALL"
            }
        }
    }

    private fun loadOrders() {
        val userId = UserPreferencesHelper.getActiveUserId(this)
        pbLoading.visibility = if (allOrders.isEmpty()) View.VISIBLE else View.GONE

        lifecycleScope.launch {
            try {
                val orders = OrderRepository.getOrders(this@MyOrdersActivity, userId)
                allOrders = orders
                filterAndDisplay()
            } catch (e: Exception) {
                // If query fails, fall back to whatever is loaded
            } finally {
                pbLoading.visibility = View.GONE
            }
        }
    }

    private fun filterAndDisplay() {
        val activeStatuses = setOf("PLACED", "CONFIRMED", "PACKED", "SHIPPED", "OUT_FOR_DELIVERY")

        val filtered = allOrders.filter { order ->
            val statusMatches = when (currentFilter) {
                "ACTIVE" -> activeStatuses.contains(order.status.uppercase(Locale.getDefault()))
                "DELIVERED" -> order.status.equals("DELIVERED", ignoreCase = true)
                "CANCELLED" -> order.status.equals("CANCELLED", ignoreCase = true)
                else -> true
            }

            val queryMatches = if (currentQuery.isBlank()) {
                true
            } else {
                val q = currentQuery.lowercase(Locale.getDefault())
                order.orderNumber.lowercase(Locale.getDefault()).contains(q) ||
                        order.items.any { it.name.lowercase(Locale.getDefault()).contains(q) }
            }

            statusMatches && queryMatches
        }

        adapter.updateOrders(filtered)
        tvCount.text = "${filtered.size} ${if (filtered.size == 1) "order" else "orders"}"

        if (filtered.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvOrders.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvOrders.visibility = View.VISIBLE
        }
    }
}
