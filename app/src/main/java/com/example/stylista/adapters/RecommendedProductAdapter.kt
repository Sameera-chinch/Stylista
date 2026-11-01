package com.example.stylista.adapters

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

class RecommendedProductAdapter(
    private var products: List<ProductEntity>,
    private val onProductClick: (ProductEntity) -> Unit
) : RecyclerView.Adapter<RecommendedProductAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val productImage: ImageView = itemView.findViewById(R.id.recProductImage)
        val productName: TextView = itemView.findViewById(R.id.recProductName)
        val productCategory: TextView = itemView.findViewById(R.id.recProductCategory)
        val productPrice: TextView = itemView.findViewById(R.id.recProductPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommended_product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val product = products[position]
        holder.productName.text = product.name
        holder.productCategory.text = if (product.subcategory.isNotBlank()) {
            "${product.category} • ${product.subcategory}"
        } else {
            product.category
        }

        val formattedPrice = "₹" + NumberFormat.getNumberInstance(Locale.US).format(product.price)
        holder.productPrice.text = formattedPrice

        com.example.stylista.utils.ImageLoaderHelper.loadProductImage(holder.productImage, product)

        holder.itemView.setOnClickListener {
            onProductClick(product)
        }
    }

    override fun getItemCount(): Int = products.size

    fun updateData(newProducts: List<ProductEntity>) {
        products = newProducts
        notifyDataSetChanged()
    }
}
