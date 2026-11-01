package com.example.stylista.models

import com.example.stylista.database.entities.ProductEntity
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: Sender,
    val text: String,
    val recommendedProductIds: List<Int> = emptyList(),
    var recommendedProducts: List<ProductEntity> = emptyList(),
    val imageUriString: String? = null,
    val isError: Boolean = false,
    val isOfflineFallback: Boolean = false,
    val canRetry: Boolean = false,
    val failedUserQuery: String? = null,
    val tierLabel: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Sender {
        USER,
        BOT
    }
}
