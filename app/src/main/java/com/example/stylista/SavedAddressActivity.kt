package com.example.stylista

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stylista.adapter.AddressAdapter
import com.example.stylista.data.repository.AddressRepository
import com.example.stylista.database.AddressEntity
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class SavedAddressActivity : AppCompatActivity() {

    private lateinit var rvAddresses: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var adapter: AddressAdapter
    private lateinit var activeUid: String
    private var isSelectingMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_address)

        activeUid = UserPreferencesHelper.getActiveUserId(this)
        isSelectingMode = intent.getBooleanExtra("is_selecting_mode", false)

        val btnBack = findViewById<TextView>(R.id.btnBack)
        val tvTitle = findViewById<TextView>(R.id.tvHeaderTitle)
        val cardAddNew = findViewById<MaterialCardView>(R.id.cardAddNewAddress)
        rvAddresses = findViewById(R.id.rvAddresses)
        layoutEmpty = findViewById(R.id.layoutEmptyAddresses)

        if (isSelectingMode) {
            tvTitle.text = "Select Delivery Address"
        }

        btnBack.setOnClickListener { finish() }

        cardAddNew.setOnClickListener {
            val intent = Intent(this, AddEditAddressActivity::class.java)
            startActivity(intent)
        }

        adapter = AddressAdapter(
            addresses = emptyList(),
            isSelectingMode = isSelectingMode,
            onSelect = { address ->
                val resultIntent = Intent().apply {
                    putExtra("selected_address_id", address.id)
                    putExtra("selected_address_name", address.name)
                    putExtra("selected_address_details", address.getFullAddressString())
                    putExtra("selected_address_phone", address.phone)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            },
            onSetDefault = { address ->
                lifecycleScope.launch {
                    AddressRepository.setDefaultAddress(this@SavedAddressActivity, address.id, activeUid)
                    UiFeedback.showSuccess(this@SavedAddressActivity, "Default address updated")
                    loadAddresses()
                }
            },
            onEdit = { address ->
                val intent = Intent(this, AddEditAddressActivity::class.java).apply {
                    putExtra("edit_address_id", address.id)
                }
                startActivity(intent)
            },
            onDelete = { address ->
                showDeleteConfirmationDialog(address)
            }
        )

        rvAddresses.layoutManager = LinearLayoutManager(this)
        rvAddresses.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadAddresses()
    }

    private fun loadAddresses() {
        lifecycleScope.launch {
            val addresses = AddressRepository.getAddresses(this@SavedAddressActivity, activeUid)
            if (addresses.isEmpty()) {
                rvAddresses.visibility = View.GONE
                layoutEmpty.visibility = View.VISIBLE
            } else {
                rvAddresses.visibility = View.VISIBLE
                layoutEmpty.visibility = View.GONE
                adapter.updateList(addresses)
            }
        }
    }

    private fun showDeleteConfirmationDialog(address: AddressEntity) {
        AlertDialog.Builder(this)
            .setTitle("Delete Address")
            .setMessage("Are you sure you want to delete this address (${address.name})?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    AddressRepository.deleteAddress(this@SavedAddressActivity, address.id, activeUid)
                    UiFeedback.showInfo(this@SavedAddressActivity, "Address deleted")
                    loadAddresses()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
