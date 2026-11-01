package com.example.stylista.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.stylista.database.entities.CartEntity
import com.example.stylista.database.entities.ProductEntity
import java.text.NumberFormat
import java.util.Locale
import com.example.stylista.R
import com.example.stylista.activities.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*
import com.example.stylista.utils.*

data class CartItemUI(
    val cart: CartEntity,
    val product: ProductEntity
)

class CartAdapter(
    private var items: List<CartItemUI>,
    private val onQuantityChanged: (CartEntity, Int) -> Unit,
    private val onItemRemoved: (CartEntity) -> Unit,
    private val onItemClicked: (ProductEntity) -> Unit
) : RecyclerView.Adapter<CartAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivImage: ImageView = itemView.findViewById(R.id.ivCartItemImage)
        val tvCategory: TextView = itemView.findViewById(R.id.tvCartItemCategory)
        val tvName: TextView = itemView.findViewById(R.id.tvCartItemName)
        val tvSize: TextView = itemView.findViewById(R.id.tvCartItemSize)
        val tvPrice: TextView = itemView.findViewById(R.id.tvCartItemPrice)
        val btnRemove: TextView = itemView.findViewById(R.id.btnRemoveCartItem)
        val btnMinus: TextView = itemView.findViewById(R.id.btnQtyMinus)
        val tvQty: TextView = itemView.findViewById(R.id.tvCartItemQty)
        val btnPlus: TextView = itemView.findViewById(R.id.btnQtyPlus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cart_product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val product = item.product
        val cart = item.cart

        holder.tvName.text = product.name
        holder.tvCategory.text = if (product.subcategory.isNotBlank()) {
            "${product.category} • ${product.subcategory}".uppercase()
        } else {
            product.category.uppercase()
        }

        holder.tvSize.text = "Size: ${cart.size}"

        val totalItemPrice = product.price * cart.quantity
        val formattedPrice = "₹" + NumberFormat.getNumberInstance(Locale.US).format(totalItemPrice)
        val unitPrice = NumberFormat.getNumberInstance(Locale.US).format(product.price)
        holder.tvPrice.text = if (cart.quantity > 1) {
            "$formattedPrice (₹$unitPrice each)"
        } else {
            formattedPrice
        }

        holder.tvQty.text = cart.quantity.toString()

        com.example.stylista.utils.ImageLoaderHelper.loadProductImage(holder.ivImage, product)

        holder.btnMinus.setOnClickListener {
            if (cart.quantity > 1) {
                onQuantityChanged(cart, cart.quantity - 1)
            } else {
                onItemRemoved(cart)
            }
        }

        holder.btnPlus.setOnClickListener {
            onQuantityChanged(cart, cart.quantity + 1)
        }

        holder.btnRemove.setOnClickListener {
            onItemRemoved(cart)
        }

        holder.itemView.setOnClickListener {
            onItemClicked(product)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<CartItemUI>) {
        items = newItems
        notifyDataSetChanged()
    }
}
