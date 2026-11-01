package com.example.stylista.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.stylista.database.entities.ProductEntity
import java.text.NumberFormat
import java.util.Locale
import com.example.stylista.R
import com.example.stylista.activities.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*
import com.example.stylista.utils.*

class ProductGridAdapter(
    private val products: List<ProductEntity>,
    private val wishlistedIds: Set<Int> = emptySet(),
    private val onWishlistToggle: ((ProductEntity) -> Unit)? = null,
    private val onProductClick: (ProductEntity) -> Unit
) : RecyclerView.Adapter<ProductGridAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val productImage: ImageView = itemView.findViewById(R.id.gridProductImage)
        val btnWishlist: TextView = itemView.findViewById(R.id.btnGridWishlist)
        val tvCategory: TextView = itemView.findViewById(R.id.tvGridCategory)
        val tvName: TextView = itemView.findViewById(R.id.tvGridName)
        val tvPrice: TextView = itemView.findViewById(R.id.tvGridPrice)
        val tvSizes: TextView = itemView.findViewById(R.id.tvGridSizes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product_grid_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val product = products[position]

        holder.tvName.text = product.name
        holder.tvCategory.text = if (product.subcategory.isNotBlank()) {
            "${product.category} • ${product.subcategory}".uppercase()
        } else {
            product.category.uppercase()
        }

        val formattedPrice = "₹" + NumberFormat.getNumberInstance(Locale.US).format(product.price)
        holder.tvPrice.text = formattedPrice

        val sizeList = product.getSizeList()
        holder.tvSizes.text = when {
            product.isUniversalSize() -> "One Size"
            sizeList.size > 2 -> "${sizeList.first()} - ${sizeList.last()}"
            sizeList.isNotEmpty() -> sizeList.joinToString(", ")
            else -> "Standard"
        }

        com.example.stylista.utils.ImageLoaderHelper.loadProductImage(holder.productImage, product)

        val isWishlisted = wishlistedIds.contains(product.id)
        val iconRes = if (isWishlisted) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
        val tint = if (isWishlisted)
            holder.itemView.context.getColor(R.color.error_red)
        else
            holder.itemView.context.getColor(R.color.charcoal)
        holder.btnWishlist.text = ""
        holder.btnWishlist.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        holder.btnWishlist.compoundDrawableTintList = ColorStateList.valueOf(tint)

        holder.btnWishlist.setOnClickListener {
            onWishlistToggle?.invoke(product)
        }

        holder.itemView.setOnClickListener {
            onProductClick(product)
        }
    }

    override fun getItemCount(): Int = products.size
}
