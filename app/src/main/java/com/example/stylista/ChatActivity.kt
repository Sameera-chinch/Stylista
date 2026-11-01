package com.example.stylista

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.utils.ChatbotGroundingHelper
import com.example.stylista.utils.ChatbotTierManager
import com.example.stylista.utils.LocalFashionStylist
import com.example.stylista.utils.NavigationHelper
import com.example.stylista.utils.UiFeedback
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.Part
import com.google.firebase.ai.type.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var rvChatMessages: RecyclerView
    private lateinit var etChatMessage: EditText
    private lateinit var btnSendMessage: TextView
    private lateinit var btnVoiceInput: TextView
    private lateinit var btnAttachImage: TextView
    private lateinit var layoutTypingIndicator: LinearLayout
    private lateinit var layoutImagePreview: LinearLayout
    private lateinit var ivSelectedImagePreview: ImageView
    private lateinit var btnRemoveImage: TextView

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatMessageAdapter

    private var selectedImageUri: Uri? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var generativeModel: GenerativeModel? = null

    // Duplicate submission guard
    private var isGenerating: Boolean = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            layoutImagePreview.visibility = View.VISIBLE
            ivSelectedImagePreview.setImageURI(uri)
        }
    }

    private val requestRecordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startVoiceListening()
        } else {
            UiFeedback.showWarning(this, getString(R.string.chat_permission_mic_rationale))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initViews()
        setupRecyclerView()
        setupListeners()
        initGeminiModel()

        // Welcome greeting
        val welcomeMsg = ChatMessage(
            sender = ChatMessage.Sender.BOT,
            text = getString(R.string.chat_welcome_message)
        )
        adapter.addMessage(welcomeMsg)
    }

    private fun initViews() {
        rvChatMessages = findViewById(R.id.rvChatMessages)
        etChatMessage = findViewById(R.id.etChatMessage)
        btnSendMessage = findViewById(R.id.btnSendMessage)
        btnVoiceInput = findViewById(R.id.btnVoiceInput)
        btnAttachImage = findViewById(R.id.btnAttachImage)
        layoutTypingIndicator = findViewById(R.id.layoutTypingIndicator)
        layoutImagePreview = findViewById(R.id.layoutImagePreview)
        ivSelectedImagePreview = findViewById(R.id.ivSelectedImagePreview)
        btnRemoveImage = findViewById(R.id.btnRemoveImage)

        findViewById<TextView>(R.id.btnChatBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnClearChat).setOnClickListener {
            adapter.clearMessages()
            val welcomeMsg = ChatMessage(
                sender = ChatMessage.Sender.BOT,
                text = getString(R.string.chat_welcome_message)
            )
            adapter.addMessage(welcomeMsg)
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter(
            messages = messages,
            onProductClick = { product ->
                NavigationHelper.openProductDetails(this, product.id)
            },
            onRetryClick = { failedMsg ->
                val query = failedMsg.failedUserQuery
                if (!query.isNullOrBlank()) {
                    etChatMessage.setText(query)
                    sendCurrentMessage()
                }
            }
        )
        rvChatMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvChatMessages.adapter = adapter
    }

    private fun setupListeners() {
        btnSendMessage.setOnClickListener { sendCurrentMessage() }

        etChatMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentMessage()
                true
            } else {
                false
            }
        }

        btnAttachImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnRemoveImage.setOnClickListener {
            selectedImageUri = null
            layoutImagePreview.visibility = View.GONE
        }

        btnVoiceInput.setOnClickListener {
            checkMicPermissionAndListen()
        }

        // Quick prompt chips (Mockup Screen 6)
        findViewById<TextView>(R.id.promptWedding)?.setOnClickListener {
            etChatMessage.setText("I need a dress for a wedding")
            sendCurrentMessage()
        }
        findViewById<TextView>(R.id.promptEthnic)?.setOnClickListener {
            etChatMessage.setText("Show me ethnic wear under ₹2000")
            sendCurrentMessage()
        }
        findViewById<TextView>(R.id.promptSports)?.setOnClickListener {
            etChatMessage.setText("Sportswear for women")
            sendCurrentMessage()
        }
        findViewById<TextView>(R.id.promptBrunch)?.setOnClickListener {
            etChatMessage.setText("Chic outfit for brunch")
            sendCurrentMessage()
        }
    }

    private fun initGeminiModel() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val systemPrompt = ChatbotGroundingHelper.buildSystemInstruction(this@ChatActivity)
                val config = GenerationConfig.builder()
                    .setResponseMimeType("application/json")
                    .setTemperature(0.7f)
                    .build()

                val sysContent = Content(role = "system", parts = listOf(TextPart(systemPrompt)))

                generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                    "gemini-2.0-flash",
                    config,
                    emptyList(),
                    emptyList(),
                    null,
                    sysContent
                )
            } catch (e: Exception) {
                Log.w("StylistaChat", "Cloud GenerativeModel init deferred: ${e.message}")
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun sendCurrentMessage() {
        if (isGenerating) return

        val text = etChatMessage.text.toString().trim()
        val imageUri = selectedImageUri

        if (text.isEmpty() && imageUri == null) return

        etChatMessage.setText("")
        selectedImageUri = null
        layoutImagePreview.visibility = View.GONE

        val userMsg = ChatMessage(
            sender = ChatMessage.Sender.USER,
            text = if (text.isNotEmpty()) text else "What do you think of this outfit?",
            imageUriString = imageUri?.toString()
        )
        adapter.addMessage(userMsg)
        rvChatMessages.smoothScrollToPosition(messages.size - 1)

        layoutTypingIndicator.visibility = View.VISIBLE
        btnSendMessage.isEnabled = false
        isGenerating = true

        val queryText = userMsg.text

        lifecycleScope.launch(Dispatchers.IO) {
            val response = ChatbotTierManager.processQuery(queryText, this@ChatActivity)

            val db = StylistaDatabase.getInstance(this@ChatActivity)
            val matchingProducts = if (response.recommendedProductIds.isNotEmpty()) {
                try {
                    db.productDao().getProductsByIds(response.recommendedProductIds)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                val botMsg = ChatMessage(
                    sender = ChatMessage.Sender.BOT,
                    text = response.replyText,
                    recommendedProductIds = response.recommendedProductIds,
                    recommendedProducts = matchingProducts,
                    tierLabel = response.tierLabel,
                    isOfflineFallback = response.isFallback,
                    canRetry = response.isFallback,
                    failedUserQuery = queryText
                )
                adapter.addMessage(botMsg)
                rvChatMessages.smoothScrollToPosition(messages.size - 1)

                layoutTypingIndicator.visibility = View.GONE
                btnSendMessage.isEnabled = true
                isGenerating = false
            }
        }
    }

    private fun buildRecentHistoryPrompt(currentInput: String): String {
        val recentHistory = messages.takeLast(6)
        if (recentHistory.isEmpty()) return currentInput

        val sb = StringBuilder()
        sb.append("Recent conversation history:\n")
        for (msg in recentHistory) {
            val role = if (msg.sender == ChatMessage.Sender.USER) "User" else "Stylista"
            sb.append("$role: ${msg.text}\n")
        }
        sb.append("\nCurrent user query: $currentInput")
        return sb.toString()
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
            }
            BitmapFactory.decodeStream(inputStream, null, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // =====================================================
    // VOICE INPUT (SpeechRecognizer)
    // =====================================================

    private fun checkMicPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceListening()
        } else {
            requestRecordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            UiFeedback.showWarning(this, "Voice recognition not available on this device")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        UiFeedback.showInfo(this@ChatActivity, "Listening for fashion query...")
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        UiFeedback.showWarning(this@ChatActivity, "Voice input canceled or not recognized")
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognized = matches[0]
                            etChatMessage.setText(recognized)
                            sendCurrentMessage()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Stylista anything about fashion or outfits...")
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            UiFeedback.showError(this, "Could not start voice recognition: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
