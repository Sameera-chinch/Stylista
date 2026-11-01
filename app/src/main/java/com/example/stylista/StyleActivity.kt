package com.example.stylista

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.RangeSlider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StyleActivity : AppCompatActivity() {

    private val selectedCategories = mutableSetOf<String>()
    private val selectedSizes = mutableSetOf<String>()
    private var budgetMin = 499
    private var budgetMax = 3299

    private var isNewUserOnboarding = false
    private lateinit var activeUid: String

    private val availableSizes = listOf("XS", "S", "M", "L", "XL", "Free Size")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_style)

        activeUid = UserPreferencesHelper.getActiveUserId(this)
        isNewUserOnboarding = intent.getBooleanExtra("is_new_user_onboarding", false)

        val backButton = findViewById<TextView>(R.id.btnBack)
        val skipTop = findViewById<TextView>(R.id.btnSkipTop)
        val skipBottom = findViewById<Button>(R.id.btnSkip)
        val continueButton = findViewById<Button>(R.id.btnContinue)

        val cardWestern = findViewById<MaterialCardView>(R.id.cardWestern)
        val cardEthnic = findViewById<MaterialCardView>(R.id.cardEthnic)
        val cardSports = findViewById<MaterialCardView>(R.id.cardSports)
        val cardMaternity = findViewById<MaterialCardView>(R.id.cardMaternity)
        val cardFormal = findViewById<MaterialCardView>(R.id.cardFormal)
        val cardAccessories = findViewById<MaterialCardView>(R.id.cardAccessories)

        val chipGroupSizes = findViewById<ChipGroup>(R.id.chipGroupSizes)
        val sliderBudget = findViewById<RangeSlider>(R.id.sliderBudget)
        val tvBudgetValue = findViewById<TextView>(R.id.tvBudgetValue)

        // Load existing preferences if editing
        val existingStyles = UserPreferencesHelper.getSelectedStyles(this, activeUid)
        selectedCategories.addAll(existingStyles)

        val existingSizes = UserPreferencesHelper.getSelectedSizes(this, activeUid)
        selectedSizes.addAll(existingSizes)

        val existingBudget = UserPreferencesHelper.getBudgetRange(this, activeUid)
        budgetMin = existingBudget.first
        budgetMax = existingBudget.second

        // Category Cards Setup
        setupCategoryCard(cardWestern, "Western")
        setupCategoryCard(cardEthnic, "Ethnic")
        setupCategoryCard(cardSports, "Sports")
        setupCategoryCard(cardMaternity, "Maternity")
        setupCategoryCard(cardFormal, "Formal")
        setupCategoryCard(cardAccessories, "Accessories")

        // Populate Sizes ChipGroup
        for (size in availableSizes) {
            val chip = Chip(this).apply {
                text = size
                isCheckable = true
                isChecked = selectedSizes.contains(size)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedSizes.add(size)
                    } else {
                        selectedSizes.remove(size)
                    }
                }
            }
            chipGroupSizes.addView(chip)
        }

        // Budget Slider
        val safeMin = budgetMin.toFloat().coerceIn(499f, 3299f)
        val safeMax = budgetMax.toFloat().coerceIn(safeMin, 3299f)
        sliderBudget.values = listOf(safeMin, safeMax)
        tvBudgetValue.text = "₹${safeMin.toInt()} – ₹${safeMax.toInt()}"

        sliderBudget.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            if (values.size >= 2) {
                budgetMin = values[0].toInt()
                budgetMax = values[1].toInt()
                tvBudgetValue.text = "₹$budgetMin – ₹$budgetMax"
            }
        }

        // Navigation & Actions
        backButton.setOnClickListener {
            handleBackOrSkip()
        }

        skipTop.setOnClickListener {
            skipOnboarding()
        }

        skipBottom.setOnClickListener {
            skipOnboarding()
        }

        continueButton.setOnClickListener {
            savePreferencesAndProceed()
        }

        // System back navigation
        onBackPressedDispatcher.addCallback(this) {
            handleBackOrSkip()
        }
    }

    private fun setupCategoryCard(card: MaterialCardView, category: String) {
        updateCardVisual(card, selectedCategories.contains(category))
        card.setOnClickListener {
            if (selectedCategories.contains(category)) {
                selectedCategories.remove(category)
                updateCardVisual(card, false)
            } else {
                selectedCategories.add(category)
                updateCardVisual(card, true)
            }
        }
    }

    private fun updateCardVisual(card: MaterialCardView, isSelected: Boolean) {
        if (isSelected) {
            card.strokeWidth = 4
            card.strokeColor = ContextCompat.getColor(this, R.color.pastel_baby_blue_dark)
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.pastel_baby_blue_light))
        } else {
            card.strokeWidth = 2
            card.strokeColor = ContextCompat.getColor(this, R.color.card_border)
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_background))
        }
    }

    private fun savePreferencesAndProceed() {
        // Cache locally scoped to active user
        UserPreferencesHelper.saveSelectedStyles(this, activeUid, selectedCategories)
        UserPreferencesHelper.saveSelectedSizes(this, activeUid, selectedSizes)
        UserPreferencesHelper.saveBudgetRange(this, activeUid, budgetMin, budgetMax)
        UserPreferencesHelper.saveOnboardingCompleted(this, activeUid, true)

        // Save to Firestore users/{uid}
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && !currentUser.isAnonymous) {
            val stylePreferences = hashMapOf<String, Any>(
                "categories" to selectedCategories.toList(),
                "sizes" to selectedSizes.toList(),
                "budgetMin" to budgetMin,
                "budgetMax" to budgetMax,
                "updatedAt" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.uid)
                .update(
                    "stylePreferences", stylePreferences,
                    "onboardingCompleted", true
                )
                .addOnFailureListener {
                    // Non-blocking
                }
        }

        UiFeedback.showSuccess(this, "Style preferences saved!")

        if (isNewUserOnboarding) {
            val intent = Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
        finish()
    }

    private fun skipOnboarding() {
        UserPreferencesHelper.saveOnboardingCompleted(this, activeUid, false)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null && !currentUser.isAnonymous) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.uid)
                .update("onboardingCompleted", false)
        }

        if (isNewUserOnboarding) {
            val intent = Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
        finish()
    }

    private fun handleBackOrSkip() {
        if (isNewUserOnboarding) {
            // New user back goes to Home, never back into Signup
            val intent = Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        } else {
            finish()
        }
    }
}
