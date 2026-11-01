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

class ProductAdapter(
    private val products: List<ProductEntity>,
    private val onProductClick: (ProductEntity) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val productImage: ImageView = itemView.findViewById(R.id.itemProductImage)
        val productName: TextView = itemView.findViewById(R.id.itemProductName)
        val productSubcategory: TextView = itemView.findViewById(R.id.itemProductSubcategory)
        val productPrice: TextView = itemView.findViewById(R.id.itemProductPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val product = products[position]
        holder.productName.text = product.name
        
        if (product.subcategory.isNotBlank()) {
            holder.productSubcategory.text = "${product.category} • ${product.subcategory}"
            holder.productSubcategory.visibility = View.VISIBLE
        } else {
            holder.productSubcategory.text = product.category
            holder.productSubcategory.visibility = View.VISIBLE
        }

        val formattedPrice = "₹" + NumberFormat.getNumberInstance(Locale.US).format(product.price)
        holder.productPrice.text = formattedPrice

        com.example.stylista.utils.ImageLoaderHelper.loadProductImage(holder.productImage, product)

        holder.itemView.setOnClickListener {
            onProductClick(product)
        }
    }

    override fun getItemCount(): Int = products.size
}
