package com.example.stylista.utils

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.stylista.R
import com.google.android.material.snackbar.Snackbar
import com.example.stylista.activities.*
import com.example.stylista.adapters.*
import com.example.stylista.chatbot.*
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*

object UiFeedback {

    enum class Type {
        SUCCESS,
        ERROR,
        INFO,
        WARNING
    }

    fun showSuccess(
        activity: Activity,
        message: String,
        actionText: String? = null,
        action: (() -> Unit)? = null
    ) {
        val rootView = activity.findViewById<View>(android.R.id.content) ?: return
        val anchorView = findAnchor(activity)
        show(rootView, message, Type.SUCCESS, actionText, anchorView, action)
    }

    fun showError(
        activity: Activity,
        message: String,
        actionText: String? = null,
        action: (() -> Unit)? = null
    ) {
        val rootView = activity.findViewById<View>(android.R.id.content) ?: return
        val anchorView = findAnchor(activity)
        show(rootView, message, Type.ERROR, actionText, anchorView, action)
    }

    fun showInfo(
        activity: Activity,
        message: String,
        actionText: String? = null,
        action: (() -> Unit)? = null
    ) {
        val rootView = activity.findViewById<View>(android.R.id.content) ?: return
        val anchorView = findAnchor(activity)
        show(rootView, message, Type.INFO, actionText, anchorView, action)
    }

    fun showWarning(
        activity: Activity,
        message: String,
        actionText: String? = null,
        action: (() -> Unit)? = null
    ) {
        val rootView = activity.findViewById<View>(android.R.id.content) ?: return
        val anchorView = findAnchor(activity)
        show(rootView, message, Type.WARNING, actionText, anchorView, action)
    }

    fun showSuccess(
        view: View,
        message: String,
        actionText: String? = null,
        anchorView: View? = null,
        action: (() -> Unit)? = null
    ) {
        show(view, message, Type.SUCCESS, actionText, anchorView, action)
    }

    fun showError(
        view: View,
        message: String,
        actionText: String? = null,
        anchorView: View? = null,
        action: (() -> Unit)? = null
    ) {
        show(view, message, Type.ERROR, actionText, anchorView, action)
    }

    fun showInfo(
        view: View,
        message: String,
        actionText: String? = null,
        anchorView: View? = null,
        action: (() -> Unit)? = null
    ) {
        show(view, message, Type.INFO, actionText, anchorView, action)
    }

    fun show(
        view: View,
        message: String,
        type: Type,
        actionText: String? = null,
        anchorView: View? = null,
        action: (() -> Unit)? = null
    ) {
        val duration = if (type == Type.ERROR) 5000 else 4000
        val snackbar = Snackbar.make(view, message, duration)

        if (anchorView != null && anchorView.visibility == View.VISIBLE) {
            snackbar.anchorView = anchorView
        }

        val snackbarView = snackbar.view
        val context = view.context

        // Deep high-contrast colors readable in both light and dark modes
        val (bgColor, textColor, iconRes) = when (type) {
            Type.SUCCESS -> Triple(
                Color.parseColor("#1B5E20"), // Deep Forest Green
                Color.WHITE,
                R.drawable.ic_check_circle
            )
            Type.ERROR -> Triple(
                Color.parseColor("#B71C1C"), // Crimson Red
                Color.WHITE,
                R.drawable.ic_error_circle
            )
            Type.INFO -> Triple(
                Color.parseColor("#1A237E"), // Deep Midnight Navy
                Color.WHITE,
                R.drawable.ic_info_circle
            )
            Type.WARNING -> Triple(
                Color.parseColor("#E65100"), // Warm Ochre
                Color.WHITE,
                R.drawable.ic_info_circle
            )
        }

        snackbarView.setBackgroundColor(bgColor)

        val density = context.resources.displayMetrics.density
        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView?.apply {
            setTextColor(textColor)
            textSize = 16f
            maxLines = 3
            typeface = Typeface.DEFAULT_BOLD
            val icon = ContextCompat.getDrawable(context, iconRes)?.apply {
                val iconPx = (22 * density).toInt()
                setBounds(0, 0, iconPx, iconPx)
            }
            setCompoundDrawables(icon, null, null, null)
            compoundDrawablePadding = (12 * density).toInt()
        }

        val actionView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)
        actionView?.apply {
            setTextColor(Color.parseColor("#FFF3B0")) // Soft butter yellow accent
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }

        if (actionText != null && action != null) {
            snackbar.setAction(actionText) {
                action()
            }
        }

        snackbar.show()
    }

    private fun findAnchor(activity: Activity): View? {
        val candidates = listOf(
            R.id.layoutCheckoutBar,
            R.id.btnCheckout,
            R.id.btnAddBag,
            R.id.navCategories,
            R.id.navProfile,
            R.id.profileNavProfile
        )
        for (id in candidates) {
            val v = activity.findViewById<View>(id)
            if (v != null && v.visibility == View.VISIBLE) return v
        }
        return null
    }
}
