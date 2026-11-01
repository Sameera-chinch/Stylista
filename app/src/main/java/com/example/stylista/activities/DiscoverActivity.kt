package com.example.stylista.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.stylista.utils.NavigationHelper
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

class DiscoverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover)

        val onlineProductsButton = findViewById<Button>(R.id.btnOnlineProducts)
        onlineProductsButton?.setOnClickListener {
            startActivity(Intent(this, NetworkActivity::class.java))
        }

        val searchButton = findViewById<TextView>(R.id.discoverSearch)
        searchButton?.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        // ================= PRODUCTS =================
        val product1 = findViewById<View>(R.id.discoverProduct1)
        val product2 = findViewById<View>(R.id.discoverProduct2)

        product1?.setOnClickListener {
            NavigationHelper.openProductDetails(this, 22) // Floral Midi Dress
        }

        product2?.setOnClickListener {
            NavigationHelper.openProductDetails(this, 25) // Linen Co-ord Set
        }

        // ================= BOTTOM NAV =================
        NavigationHelper.setupBottomNav(this, R.id.navDiscover)
    }
}
