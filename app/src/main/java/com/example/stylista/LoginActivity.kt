package com.example.stylista

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stylista.utils.CartRepository
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.example.stylista.utils.WishlistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        // Firebase Authentication
        auth = FirebaseAuth.getInstance()

        val email = findViewById<EditText>(R.id.etEmail)
        val password = findViewById<EditText>(R.id.etPassword)

        val loginButton = findViewById<Button>(R.id.btnLogin)
        val signup = findViewById<TextView>(R.id.tvSignup)
        val backButton = findViewById<TextView>(R.id.btnBack)

        // LOGIN BUTTON
        loginButton.setOnClickListener {

            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString()

            if (emailText.isEmpty()) {
                email.error = "Enter your email"
                return@setOnClickListener
            }

            if (passwordText.isEmpty()) {
                password.error = "Enter your password"
                return@setOnClickListener
            }

            // Disable button while Firebase checks login
            loginButton.isEnabled = false

            // Firebase Login
            auth.signInWithEmailAndPassword(
                emailText,
                passwordText
            ).addOnCompleteListener { task ->

                if (task.isSuccessful) {

                    val authUser = task.result?.user
                    val guestId = UserPreferencesHelper.getActiveUserId(this)
                    if (authUser != null) {
                        lifecycleScope.launch {
                            try {
                                val userDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document(authUser.uid)
                                    .get()
                                    .await()
                                val name = userDoc.getString("name") ?: authUser.displayName ?: "Member"
                                val phone = userDoc.getString("phone") ?: ""
                                UserPreferencesHelper.saveUserProfile(this@LoginActivity, authUser.uid, name, authUser.email ?: "", phone)
                            } catch (e: Exception) {
                                UserPreferencesHelper.saveUserProfile(
                                    this@LoginActivity,
                                    authUser.uid,
                                    authUser.displayName ?: "Member",
                                    authUser.email ?: "",
                                    ""
                                )
                            }
                            CartRepository.syncCartOnLogin(this@LoginActivity, authUser.uid, guestId)
                            WishlistRepository.syncWishlistOnLogin(this@LoginActivity, authUser.uid)
                            withContext(Dispatchers.Main) {
                                val intent = Intent(
                                    this@LoginActivity,
                                    HomeActivity::class.java
                                ).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                startActivity(intent)
                                finish()
                            }
                        }
                    } else {
                        val intent = Intent(
                            this,
                            HomeActivity::class.java
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }

                } else {

                    loginButton.isEnabled = true
                    val exception = task.exception
                    val errorMessage = when (exception) {
                        is FirebaseAuthInvalidUserException ->
                            "No account found with this email. Please sign up first."
                        is FirebaseAuthInvalidCredentialsException ->
                            "Incorrect password or invalid email format."
                        is FirebaseNetworkException ->
                            "Network connection unavailable. Please check your internet connection."
                        else ->
                            exception?.localizedMessage ?: "Login failed. Please verify your credentials."
                    }

                    UiFeedback.showError(
                        this,
                        errorMessage
                    )
                }
            }
        }

        // SIGN UP
        signup.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
            finish()
        }

        // BACK
        backButton.setOnClickListener {
            finish()
        }
    }
}
