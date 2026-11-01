package com.example.stylista.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

object UserPreferencesHelper {

    private const val PREF_NAME = "stylista_user_prefs"
    private const val KEY_GUEST_USER_ID = "pref_guest_user_id"
    private const val KEY_THEME_MODE = "pref_theme_mode"

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getActiveUserId(context: Context): String {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null && !firebaseUser.uid.isNullOrBlank()) {
            return firebaseUser.uid
        }
        val prefs = getPrefs(context)
        var guestId = prefs.getString(KEY_GUEST_USER_ID, null)
        if (guestId.isNullOrBlank()) {
            guestId = "guest_" + UUID.randomUUID().toString().take(12)
            prefs.edit().putString(KEY_GUEST_USER_ID, guestId).apply()
        }
        return guestId
    }

    fun isGuestUser(): Boolean {
        return FirebaseAuth.getInstance().currentUser == null
    }

    // --- Per-User Style Preferences (Scoped by UID) ---

    fun saveSelectedStyles(context: Context, uid: String, categories: Set<String>) {
        if (uid.isBlank()) return
        getPrefs(context).edit()
            .putStringSet("pref_styles_$uid", HashSet(categories))
            .apply()
    }

    // Backward compatibility helper
    fun saveSelectedStyles(context: Context, styles: Set<String>) {
        saveSelectedStyles(context, getActiveUserId(context), styles)
    }

    fun getSelectedStyles(context: Context, uid: String): Set<String> {
        if (uid.isBlank()) return emptySet()
        return getPrefs(context).getStringSet("pref_styles_$uid", emptySet()) ?: emptySet()
    }

    // Backward compatibility helper
    fun getSelectedStyles(context: Context): Set<String> {
        return getSelectedStyles(context, getActiveUserId(context))
    }

    fun saveSelectedSizes(context: Context, uid: String, sizes: Set<String>) {
        if (uid.isBlank()) return
        getPrefs(context).edit()
            .putStringSet("pref_sizes_$uid", HashSet(sizes))
            .apply()
    }

    fun getSelectedSizes(context: Context, uid: String): Set<String> {
        if (uid.isBlank()) return emptySet()
        return getPrefs(context).getStringSet("pref_sizes_$uid", emptySet()) ?: emptySet()
    }

    fun saveBudgetRange(context: Context, uid: String, minPrice: Int, maxPrice: Int) {
        if (uid.isBlank()) return
        getPrefs(context).edit()
            .putInt("pref_budget_min_$uid", minPrice)
            .putInt("pref_budget_max_$uid", maxPrice)
            .apply()
    }

    fun getBudgetRange(context: Context, uid: String): Pair<Int, Int> {
        if (uid.isBlank()) return Pair(499, 3299)
        val prefs = getPrefs(context)
        val minPrice = prefs.getInt("pref_budget_min_$uid", 499)
        val maxPrice = prefs.getInt("pref_budget_max_$uid", 3299)
        return Pair(minPrice, maxPrice)
    }

    fun saveOnboardingCompleted(context: Context, uid: String, completed: Boolean) {
        if (uid.isBlank()) return
        getPrefs(context).edit()
            .putBoolean("pref_onboarding_completed_$uid", completed)
            .apply()
    }

    fun isOnboardingCompleted(context: Context, uid: String): Boolean {
        if (uid.isBlank()) return false
        return getPrefs(context).getBoolean("pref_onboarding_completed_$uid", false)
    }

    // --- Per-User Profile Cache (Name, Email, Phone) ---

    fun saveUserProfile(context: Context, uid: String, name: String, email: String, phone: String) {
        if (uid.isBlank()) return
        getPrefs(context).edit()
            .putString("pref_profile_name_$uid", name)
            .putString("pref_profile_email_$uid", email)
            .putString("pref_profile_phone_$uid", phone)
            .apply()
    }

    fun getUserProfile(context: Context, uid: String): Triple<String, String, String>? {
        if (uid.isBlank()) return null
        val prefs = getPrefs(context)
        val name = prefs.getString("pref_profile_name_$uid", null) ?: return null
        val email = prefs.getString("pref_profile_email_$uid", "") ?: ""
        val phone = prefs.getString("pref_profile_phone_$uid", "") ?: ""
        return Triple(name, email, phone)
    }

    // --- User Session & Product Tracking ---

    fun recordProductView(context: Context, productId: Int) {
        if (productId <= 0) return
        val uid = getActiveUserId(context)
        val currentSet = getViewedProductIds(context, uid).toMutableSet()
        currentSet.add(productId.toString())
        getPrefs(context).edit()
            .putStringSet("pref_viewed_products_$uid", currentSet)
            .apply()
    }

    fun getViewedProductIds(context: Context, uid: String = getActiveUserId(context)): Set<String> {
        return getPrefs(context).getStringSet("pref_viewed_products_$uid", emptySet()) ?: emptySet()
    }

    fun clearUserData(context: Context) {
        val uid = getActiveUserId(context)
        getPrefs(context).edit()
            .remove("pref_styles_$uid")
            .remove("pref_sizes_$uid")
            .remove("pref_budget_min_$uid")
            .remove("pref_budget_max_$uid")
            .remove("pref_onboarding_completed_$uid")
            .remove("pref_profile_name_$uid")
            .remove("pref_profile_email_$uid")
            .remove("pref_profile_phone_$uid")
            .remove("pref_viewed_products_$uid")
            .remove(KEY_GUEST_USER_ID)
            .apply()
    }

    // --- Theme Mode ---

    fun setThemeMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
        applyThemeMode(mode)
    }

    fun getThemeMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_THEME_MODE, THEME_SYSTEM)
    }

    fun applyThemeMode(mode: Int) {
        when (mode) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
