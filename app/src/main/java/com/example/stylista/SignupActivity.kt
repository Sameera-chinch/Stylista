package com.example.stylista

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stylista.utils.CartRepository
import com.example.stylista.utils.WishlistRepository
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    companion object {
        private const val TAG = "SignupActivity"
        private val PHONE_REGEX = Regex("^[6-9]\\d{9}$")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val name = findViewById<EditText>(R.id.etName)
        val email = findViewById<EditText>(R.id.etEmail)
        val phone = findViewById<EditText>(R.id.etPhone)
        val password = findViewById<EditText>(R.id.etPassword)
        val confirmPassword = findViewById<EditText>(R.id.etConfirmPassword)

        val terms = findViewById<CheckBox>(R.id.cbTerms)
        val signupButton = findViewById<Button>(R.id.btnSignup)

        val backButton = findViewById<TextView>(R.id.btnBack)
        val loginButton = findViewById<TextView>(R.id.tvLogin)

        signupButton.setOnClickListener {

            val nameText = name.text.toString().trim()
            val emailText = email.text.toString().trim()
            val phoneText = phone.text.toString().trim()
            val passwordText = password.text.toString()
            val confirmText = confirmPassword.text.toString()

            when {
                nameText.isEmpty() -> {
                    name.error = "Enter your full name"
                    name.requestFocus()
                }

                emailText.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailText).matches() -> {
                    email.error = "Enter a valid email address"
                    email.requestFocus()
                }

                phoneText.isEmpty() || !PHONE_REGEX.matches(phoneText) -> {
                    phone.error = "Enter a valid 10-digit Indian mobile number"
                    phone.requestFocus()
                }

                passwordText.isEmpty() -> {
                    password.error = "Enter a password"
                    password.requestFocus()
                }

                passwordText.length < 6 -> {
                    password.error = "Password must be at least 6 characters"
                    password.requestFocus()
                }

                confirmText.isEmpty() -> {
                    confirmPassword.error = "Confirm your password"
                    confirmPassword.requestFocus()
                }

                passwordText != confirmText -> {
                    confirmPassword.error = "Passwords do not match"
                    confirmPassword.requestFocus()
                }

                !terms.isChecked -> {
                    UiFeedback.showWarning(
                        this,
                        "Please accept the Terms & Conditions"
                    )
                }

                else -> {

                    signupButton.isEnabled = false

                    // Create Firebase Authentication account
                    auth.createUserWithEmailAndPassword(
                        emailText,
                        passwordText
                    ).addOnCompleteListener { task ->

                        if (task.isSuccessful) {

                            val user = auth.currentUser

                            if (user != null) {

                                // Cache user profile locally
                                UserPreferencesHelper.saveUserProfile(
                                    this,
                                    user.uid,
                                    nameText,
                                    emailText,
                                    phoneText
                                )

                                // Merge guest cart and wishlist in background
                                val guestId = UserPreferencesHelper.getActiveUserId(this@SignupActivity)
                                lifecycleScope.launch {
                                    CartRepository.syncCartOnLogin(this@SignupActivity, user.uid, guestId)
                                    WishlistRepository.syncWishlistOnLogin(this@SignupActivity, user.uid)
                                }

                                // Data to store in Firestore users/{uid}
                                val userData = hashMapOf<String, Any>(
                                    "uid" to user.uid,
                                    "name" to nameText,
                                    "email" to emailText,
                                    "phone" to phoneText,
                                    "createdAt" to System.currentTimeMillis(),
                                    "onboardingCompleted" to false
                                )

                                // Save user in Firestore asynchronously without blocking Auth flow
                                db.collection("users")
                                    .document(user.uid)
                                    .set(userData)
                                    .addOnFailureListener { exception ->
                                        Log.w(TAG, "Firestore profile write warning: ${exception.message}")
                                    }

                                // Save name in Firebase Auth profile
                                val profileUpdates =
                                    UserProfileChangeRequest.Builder()
                                        .setDisplayName(nameText)
                                        .build()

                                user.updateProfile(profileUpdates)
                                    .addOnCompleteListener {
                                        UiFeedback.showSuccess(
                                            this,
                                            "Account created! Let's personalize your style."
                                        )

                                        // New users go to Style Preferences onboarding
                                        val intent = Intent(
                                            this,
                                            StyleActivity::class.java
                                        ).apply {
                                            putExtra("is_new_user_onboarding", true)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        }

                                        startActivity(intent)
                                        finish()
                                    }

                            } else {

                                signupButton.isEnabled = true

                                UiFeedback.showError(
                                    this,
                                    "User account could not be retrieved."
                                )
                            }

                        } else {

                            signupButton.isEnabled = true

                            UiFeedback.showError(
                                this,
                                task.exception?.message
                                    ?: "Account creation failed"
                            )
                        }
                    }
                }
            }
        }

        backButton.setOnClickListener {
            finish()
        }

        loginButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
