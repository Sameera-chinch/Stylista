package com.example.stylista

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.utils.NavigationHelper
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var cardGuestPrompt: MaterialCardView
    private lateinit var cardUserProfile: MaterialCardView
    private lateinit var profileInitial: TextView
    private lateinit var profileName: TextView
    private lateinit var profileEmail: TextView

    companion object {
        private val PHONE_REGEX = Regex("^[6-9]\\d{9}$")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        cardGuestPrompt = findViewById(R.id.cardGuestPrompt)
        cardUserProfile = findViewById(R.id.cardUserProfile)
        profileInitial = findViewById(R.id.profileInitial)
        profileName = findViewById(R.id.profileName)
        profileEmail = findViewById(R.id.profileEmail)

        val btnGuestLogin = findViewById<Button>(R.id.btnGuestLogin)
        val btnGuestSignup = findViewById<Button>(R.id.btnGuestSignup)

        btnGuestLogin?.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnGuestSignup?.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        // Setup UI for User vs Guest
        refreshProfileHeader()

        // ================= BOTTOM NAVIGATION =================
        NavigationHelper.setupBottomNav(this, R.id.navProfile)

        // ================= THEME SELECTOR =================
        val themeOptions = arrayOf("System Default", "Light Mode", "Dark Mode")
        val tvCurrentTheme = findViewById<TextView>(R.id.tvCurrentTheme)
        val updateThemeLabel = {
            val mode = UserPreferencesHelper.getThemeMode(this)
            tvCurrentTheme?.text = when (mode) {
                UserPreferencesHelper.THEME_LIGHT -> "Light"
                UserPreferencesHelper.THEME_DARK -> "Dark"
                else -> "System"
            }
        }
        updateThemeLabel()

        val openThemeDialog = View.OnClickListener {
            val mode = UserPreferencesHelper.getThemeMode(this)
            AlertDialog.Builder(this)
                .setTitle("Select Theme")
                .setSingleChoiceItems(themeOptions, mode) { dialog, which ->
                    UserPreferencesHelper.setThemeMode(this, which)
                    updateThemeLabel()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        findViewById<View>(R.id.profileTheme)?.setOnClickListener(openThemeDialog)
        findViewById<View>(R.id.btnProfileSettings)?.setOnClickListener(openThemeDialog)

        findViewById<View>(R.id.btnProfileNotifications)?.setOnClickListener {
            com.example.stylista.utils.NotificationsHelper.show(this)
        }

        // ================= PROFILE OPTIONS =================
        val orders = findViewById<View>(R.id.profileOrders)
        val btnViewAllOrders = findViewById<View>(R.id.btnViewAllOrders)
        val myWishlist = findViewById<View>(R.id.profileWishlist)
        val myBag = findViewById<View>(R.id.profileBag)
        val campusMap = findViewById<View>(R.id.profileCampusMap)
        val logout = findViewById<View>(R.id.profileLogout)

        // Order Status click handlers -> MyOrdersActivity
        val openOrders = { filter: String ->
            val intent = Intent(this, MyOrdersActivity::class.java).apply {
                putExtra("initial_filter", filter)
            }
            startActivity(intent)
        }
        orders?.setOnClickListener { openOrders("ALL") }
        btnViewAllOrders?.setOnClickListener { openOrders("ALL") }
        findViewById<View>(R.id.orderStatusToPay)?.setOnClickListener { openOrders("ACTIVE") }
        findViewById<View>(R.id.orderStatusProcessing)?.setOnClickListener { openOrders("ACTIVE") }
        findViewById<View>(R.id.orderStatusShipped)?.setOnClickListener { openOrders("ACTIVE") }
        findViewById<View>(R.id.orderStatusDelivered)?.setOnClickListener { openOrders("DELIVERED") }

        // My Details -> Edit Profile Dialog
        findViewById<View>(R.id.profileDetails)?.setOnClickListener {
            if (UserPreferencesHelper.isGuestUser()) {
                UiFeedback.showWarning(this, "Please log in to edit your profile")
            } else {
                showEditProfileDialog()
            }
        }

        // Style Preferences
        findViewById<View>(R.id.profileStylePreferences)?.setOnClickListener {
            val intent = Intent(this, StyleActivity::class.java).apply {
                putExtra("is_new_user_onboarding", false)
            }
            startActivity(intent)
        }

        // Saved Addresses
        findViewById<View>(R.id.profileAddresses)?.setOnClickListener {
            if (UserPreferencesHelper.isGuestUser()) {
                UiFeedback.showWarning(this, "Please log in to manage saved addresses")
            } else {
                val intent = Intent(this, SavedAddressActivity::class.java).apply {
                    putExtra("is_selecting_mode", false)
                }
                startActivity(intent)
            }
        }

        // Help & Support
        findViewById<View>(R.id.profileHelp)?.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        // My Wishlist
        myWishlist?.setOnClickListener {
            val intent = Intent(this, WishlistActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        }

        // My Bag
        myBag?.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }

        // Campus Map & Navigation
        campusMap?.setOnClickListener {
            startActivity(Intent(this, CampusMapActivity::class.java))
        }

        // Logout
        logout?.setOnClickListener {
            auth.signOut()
            UserPreferencesHelper.clearUserData(this)
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshProfileHeader()
    }

    private fun refreshProfileHeader() {
        val user = auth.currentUser
        if (user != null) {
            cardGuestPrompt.visibility = View.GONE
            cardUserProfile.visibility = View.VISIBLE

            val cachedProfile = UserPreferencesHelper.getUserProfile(this, user.uid)
            val displayName = cachedProfile?.first ?: user.displayName ?: "Stylista Member"
            val displayEmail = user.email ?: ""

            profileName.text = "Hello, $displayName"
            profileEmail.text = displayEmail
            profileInitial.text = displayName.firstOrNull()?.uppercase() ?: "S"

            // Background fetch from Firestore if cached phone is empty
            if (cachedProfile?.third.isNullOrBlank()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val doc = db.collection("users").document(user.uid).get().await()
                        val name = doc.getString("name") ?: displayName
                        val phone = doc.getString("phone") ?: ""
                        UserPreferencesHelper.saveUserProfile(this@ProfileActivity, user.uid, name, displayEmail, phone)
                        withContext(Dispatchers.Main) {
                            profileName.text = "Hello, $name"
                            profileInitial.text = name.firstOrNull()?.uppercase() ?: "S"
                        }
                    } catch (e: Exception) {
                        // Keep cached/auth values
                    }
                }
            }
        } else {
            cardGuestPrompt.visibility = View.VISIBLE
            cardUserProfile.visibility = View.GONE
        }
    }

    private fun showEditProfileDialog() {
        val user = auth.currentUser ?: return
        val cached = UserPreferencesHelper.getUserProfile(this, user.uid)
        val currentName = cached?.first ?: user.displayName ?: ""
        val currentPhone = cached?.third ?: ""
        val email = user.email ?: ""

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_address, null)
        val etName = dialogView.findViewById<EditText>(R.id.etAddressName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etAddressPhone)
        val tilStreet = dialogView.findViewById<View>(R.id.etAddressStreet)?.parent?.parent as? View
        val tilCity = dialogView.findViewById<View>(R.id.etAddressCity)?.parent?.parent as? View
        val tilPincode = dialogView.findViewById<View>(R.id.etAddressPincode)?.parent?.parent as? View

        // Hide address-only fields in profile editor dialog
        tilStreet?.visibility = View.GONE
        tilCity?.visibility = View.GONE
        tilPincode?.visibility = View.GONE

        etName.setText(currentName)
        etPhone.setText(currentPhone)

        AlertDialog.Builder(this)
            .setTitle("Edit Profile")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString().trim()
                val newPhone = etPhone.text.toString().trim()

                if (newName.isBlank()) {
                    UiFeedback.showWarning(this, "Name cannot be empty")
                    return@setPositiveButton
                }

                if (newPhone.isNotBlank() && !PHONE_REGEX.matches(newPhone)) {
                    UiFeedback.showWarning(this, "Please enter a valid 10-digit mobile number")
                    return@setPositiveButton
                }

                // Update local cache
                UserPreferencesHelper.saveUserProfile(this, user.uid, newName, email, newPhone)

                // Update Firebase Auth display name
                val profileUpdate = UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build()
                user.updateProfile(profileUpdate)

                // Update Firestore users/{uid}
                db.collection("users").document(user.uid).update(
                    "name", newName,
                    "phone", newPhone
                ).addOnFailureListener {
                    // Non-blocking
                }

                UiFeedback.showSuccess(this, "Profile updated!")
                refreshProfileHeader()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOrderHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val userId = UserPreferencesHelper.getActiveUserId(this@ProfileActivity)
            val db = StylistaDatabase.getInstance(this@ProfileActivity)
            val allOrders = db.orderDao().getAllOrders(userId)

            withContext(Dispatchers.Main) {
                if (allOrders.isEmpty()) {
                    AlertDialog.Builder(this@ProfileActivity)
                        .setTitle("My Orders")
                        .setMessage("No past orders found. Start shopping to place your first order!")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    val orderSummary = StringBuilder()
                    for ((index, order) in allOrders.withIndex()) {
                        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            .format(Date(order.timestamp))
                        val formattedTotal = NumberFormat.getNumberInstance(Locale.US).format(order.totalAmount)

                        orderSummary.append("Order #${order.id}\n")
                        orderSummary.append("Date: $dateStr\n")
                        orderSummary.append("Total: ₹$formattedTotal (${order.paymentMethod})\n")
                        if (index < allOrders.size - 1) {
                            orderSummary.append("─────────────────────────\n\n")
                        }
                    }

                    AlertDialog.Builder(this@ProfileActivity)
                        .setTitle("My Orders (${allOrders.size})")
                        .setMessage(orderSummary.toString())
                        .setPositiveButton("Close", null)
                        .show()
                }
            }
        }
    }
}
