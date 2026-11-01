package com.example.stylista.utils

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.stylista.R
import com.example.stylista.database.ProductEntity

object ImageLoaderHelper {

    fun loadProductImage(imageView: ImageView, product: ProductEntity) {
        val context = imageView.context ?: return
        val fallbackRes = if (product.imageRes != 0) product.imageRes else R.drawable.ic_placeholder_product

        val imageSource: Any = when {
            product.imageUrl.isNotBlank() && product.imageUrl.startsWith("asset:") -> {
                val path = product.imageUrl.removePrefix("asset:")
                "file:///android_asset/$path"
            }
            product.imageUrl.isNotBlank() -> {
                product.imageUrl
            }
            product.id in 1..32 -> {
                "file:///android_asset/product_images/${product.id}.webp"
            }
            product.imageRes != 0 -> {
                product.imageRes
            }
            else -> {
                fallbackRes
            }
        }

        try {
            Glide.with(context)
                .load(imageSource)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(fallbackRes)
                .error(fallbackRes)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView)
        } catch (e: Exception) {
            imageView.setImageResource(fallbackRes)
        }
    }
}
