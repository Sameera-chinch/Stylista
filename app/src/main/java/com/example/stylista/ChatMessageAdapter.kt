package com.example.stylista

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stylista.database.ProductEntity

class ChatMessageAdapter(
    private val messages: MutableList<ChatMessage>,
    private val onProductClick: (ProductEntity) -> Unit,
    private val onRetryClick: ((ChatMessage) -> Unit)? = null
) : RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutUserMessage: LinearLayout = itemView.findViewById(R.id.layoutUserMessage)
        val tvUserText: TextView = itemView.findViewById(R.id.tvUserText)
        val ivUserAttachment: ImageView = itemView.findViewById(R.id.ivUserAttachment)

        val layoutBotMessage: LinearLayout = itemView.findViewById(R.id.layoutBotMessage)
        val layoutBotBubbleContainer: LinearLayout = itemView.findViewById(R.id.layoutBotBubbleContainer)
        val tvBotBadge: TextView = itemView.findViewById(R.id.tvBotBadge)
        val tvBotText: TextView = itemView.findViewById(R.id.tvBotText)
        val btnRetryQuery: TextView = itemView.findViewById(R.id.btnRetryQuery)
        val rvBotProducts: RecyclerView = itemView.findViewById(R.id.rvBotProducts)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        if (message.sender == ChatMessage.Sender.USER) {
            holder.layoutUserMessage.visibility = View.VISIBLE
            holder.layoutBotMessage.visibility = View.GONE
            holder.tvUserText.text = message.text

            if (message.imageUriString != null) {
                holder.ivUserAttachment.visibility = View.VISIBLE
                try {
                    holder.ivUserAttachment.setImageURI(Uri.parse(message.imageUriString))
                } catch (e: Exception) {
                    holder.ivUserAttachment.visibility = View.GONE
                }
            } else {
                holder.ivUserAttachment.visibility = View.GONE
            }
        } else {
            holder.layoutUserMessage.visibility = View.GONE
            holder.layoutBotMessage.visibility = View.VISIBLE
            holder.tvBotText.text = message.text

            // Badge text showing active tier
            val tier = message.tierLabel ?: if (message.isOfflineFallback) "Offline stylist" else "AI"
            holder.tvBotBadge.text = "Stylista Stylist · $tier"
            if (message.isError) {
                holder.tvBotBadge.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.error_red))
            } else {
                holder.tvBotBadge.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.pastel_baby_blue_dark))
            }

            // Retry Button
            if (message.canRetry) {
                holder.btnRetryQuery.visibility = View.VISIBLE
                holder.btnRetryQuery.setOnClickListener {
                    onRetryClick?.invoke(message)
                }
            } else {
                holder.btnRetryQuery.visibility = View.GONE
            }

            // Product recommendations carousel
            if (message.recommendedProducts.isNotEmpty()) {
                holder.rvBotProducts.visibility = View.VISIBLE
                holder.rvBotProducts.layoutManager = LinearLayoutManager(
                    holder.itemView.context,
                    LinearLayoutManager.HORIZONTAL,
                    false
                )
                holder.rvBotProducts.adapter = RecommendedProductAdapter(message.recommendedProducts) { product ->
                    onProductClick(product)
                }
            } else {
                holder.rvBotProducts.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clearMessages() {
        messages.clear()
        notifyDataSetChanged()
    }
}
