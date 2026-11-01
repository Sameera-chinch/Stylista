package com.example.stylista.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import com.example.stylista.activities.CartActivity
import com.example.stylista.activities.CategoriesActivity
import com.example.stylista.activities.DiscoverActivity
import com.example.stylista.activities.HomeActivity
import com.example.stylista.activities.ProductActivity
import com.example.stylista.activities.ProfileActivity
import com.example.stylista.R
import com.example.stylista.activities.WishlistActivity
import com.example.stylista.activities.*
import com.example.stylista.adapters.*
import com.example.stylista.chatbot.*
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*

object NavigationHelper {

    fun setupBottomNav(activity: Activity, currentTabId: Int) {
        val navHome = activity.findViewById<View>(R.id.navHome)
            ?: activity.findViewById<View>(R.id.wishlistNavHome)
            ?: activity.findViewById<View>(R.id.profileNavHome)
            ?: activity.findViewById<View>(R.id.discoverNavHome)

        val navCategories = activity.findViewById<View>(R.id.navCategories)
            ?: activity.findViewById<View>(R.id.wishlistNavCategories)
            ?: activity.findViewById<View>(R.id.profileNavCategories)
            ?: activity.findViewById<View>(R.id.discoverNavCategories)

        val navDiscover = activity.findViewById<View>(R.id.navDiscover)
            ?: activity.findViewById<View>(R.id.wishlistNavDiscover)
            ?: activity.findViewById<View>(R.id.profileNavDiscover)
            ?: activity.findViewById<View>(R.id.discoverNavDiscover)

        val navWishlist = activity.findViewById<View>(R.id.navWishlist)
            ?: activity.findViewById<View>(R.id.wishlistNavWishlist)
            ?: activity.findViewById<View>(R.id.profileNavWishlist)
            ?: activity.findViewById<View>(R.id.discoverNavWishlist)

        val navProfile = activity.findViewById<View>(R.id.navProfile)
            ?: activity.findViewById<View>(R.id.wishlistNavProfile)
            ?: activity.findViewById<View>(R.id.profileNavProfile)
            ?: activity.findViewById<View>(R.id.discoverNavProfile)

        navHome?.setOnClickListener {
            if (currentTabId != R.id.navHome) {
                val intent = Intent(activity, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                activity.startActivity(intent)
            }
        }

        navCategories?.setOnClickListener {
            if (currentTabId != R.id.navCategories) {
                val intent = Intent(activity, CategoriesActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                activity.startActivity(intent)
            }
        }

        navDiscover?.setOnClickListener {
            if (currentTabId != R.id.navDiscover) {
                val intent = Intent(activity, DiscoverActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                activity.startActivity(intent)
            }
        }

        navWishlist?.setOnClickListener {
            if (currentTabId != R.id.navWishlist) {
                val intent = Intent(activity, WishlistActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                activity.startActivity(intent)
            }
        }

        navProfile?.setOnClickListener {
            if (currentTabId != R.id.navProfile) {
                val intent = Intent(activity, ProfileActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                activity.startActivity(intent)
            }
        }
    }

    fun openProductDetails(context: Context, productId: Int) {
        val intent = Intent(context, ProductActivity::class.java)
        intent.putExtra("product_id", productId)
        context.startActivity(intent)
    }

    fun openCart(context: Context) {
        val intent = Intent(context, CartActivity::class.java)
        context.startActivity(intent)
    }

    fun openWishlist(context: Context) {
        val intent = Intent(context, WishlistActivity::class.java)
        context.startActivity(intent)
    }
}
