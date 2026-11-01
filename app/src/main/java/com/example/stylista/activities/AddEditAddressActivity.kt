package com.example.stylista.activities

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stylista.repositories.AddressRepository
import com.example.stylista.database.entities.AddressEntity
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.utils.UiFeedback
import com.example.stylista.utils.UserPreferencesHelper
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.util.UUID
import com.example.stylista.BuildConfig
import com.example.stylista.R
import com.example.stylista.StylistaApp
import com.example.stylista.adapters.*
import com.example.stylista.chatbot.*
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*
import com.example.stylista.utils.*

class AddEditAddressActivity : AppCompatActivity() {

    private lateinit var activeUid: String
    private var editAddressId: String? = null

    private val indianStates = listOf(
        "Andhra Pradesh", "Assam", "Bihar", "Delhi", "Goa", "Gujarat",
        "Haryana", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra",
        "Punjab", "Rajasthan", "Tamil Nadu", "Telangana", "Uttar Pradesh", "West Bengal"
    )

    companion object {
        val PHONE_REGEX = Regex("^[6-9]\\d{9}$")
        val PINCODE_REGEX = Regex("^[1-9][0-9]{5}$")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_address)

        activeUid = UserPreferencesHelper.getActiveUserId(this)
        editAddressId = intent.getStringExtra("edit_address_id")

        val btnBack = findViewById<TextView>(R.id.btnBack)
        val tvFormTitle = findViewById<TextView>(R.id.tvFormTitle)
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        val etPincode = findViewById<TextInputEditText>(R.id.etPincode)
        val etHouseFlat = findViewById<TextInputEditText>(R.id.etHouseFlat)
        val etStreetArea = findViewById<TextInputEditText>(R.id.etStreetArea)
        val etLandmark = findViewById<TextInputEditText>(R.id.etLandmark)
        val etCity = findViewById<TextInputEditText>(R.id.etCity)
        val actvState = findViewById<AutoCompleteTextView>(R.id.actvState)
        val chipGroupType = findViewById<ChipGroup>(R.id.chipGroupType)
        val cbDefault = findViewById<CheckBox>(R.id.cbDefaultAddress)
        val btnSave = findViewById<Button>(R.id.btnSaveAddress)

        // Setup States Dropdown
        val stateAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, indianStates)
        actvState.setAdapter(stateAdapter)
        actvState.setText("Maharashtra", false)

        btnBack.setOnClickListener { finish() }

        // If editing existing address, load data
        if (!editAddressId.isNullOrBlank()) {
            tvFormTitle.text = "Edit Delivery Address"
            lifecycleScope.launch {
                val db = StylistaDatabase.getInstance(this@AddEditAddressActivity)
                val existing = db.addressDao().getAddressById(editAddressId!!, activeUid)
                if (existing != null) {
                    etName.setText(existing.name)
                    etPhone.setText(existing.phone)
                    etPincode.setText(existing.pincode)
                    etHouseFlat.setText(existing.houseFlat)
                    etStreetArea.setText(existing.streetArea)
                    etLandmark.setText(existing.landmark)
                    etCity.setText(existing.city)
                    actvState.setText(existing.state, false)
                    cbDefault.isChecked = existing.isDefault

                    when (existing.type) {
                        "Work" -> findViewById<Chip>(R.id.chipWork)?.isChecked = true
                        "Other" -> findViewById<Chip>(R.id.chipOther)?.isChecked = true
                        else -> findViewById<Chip>(R.id.chipHome)?.isChecked = true
                    }
                }
            }
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val pincode = etPincode.text.toString().trim()
            val houseFlat = etHouseFlat.text.toString().trim()
            val streetArea = etStreetArea.text.toString().trim()
            val landmark = etLandmark.text.toString().trim()
            val city = etCity.text.toString().trim()
            val state = actvState.text.toString().trim()
            val isDefault = cbDefault.isChecked

            val selectedChipId = chipGroupType.checkedChipId
            val addressType = when (selectedChipId) {
                R.id.chipWork -> "Work"
                R.id.chipOther -> "Other"
                else -> "Home"
            }

            // Validation
            when {
                name.isBlank() -> {
                    etName.error = "Name is required"
                    etName.requestFocus()
                }
                phone.isBlank() || !PHONE_REGEX.matches(phone) -> {
                    etPhone.error = "Enter a valid 10-digit mobile number"
                    etPhone.requestFocus()
                }
                pincode.isBlank() || !PINCODE_REGEX.matches(pincode) -> {
                    etPincode.error = "Enter a valid 6-digit Indian PIN code"
                    etPincode.requestFocus()
                }
                houseFlat.isBlank() -> {
                    etHouseFlat.error = "House/Flat number is required"
                    etHouseFlat.requestFocus()
                }
                streetArea.isBlank() -> {
                    etStreetArea.error = "Street/Area is required"
                    etStreetArea.requestFocus()
                }
                city.isBlank() -> {
                    etCity.error = "City is required"
                    etCity.requestFocus()
                }
                state.isBlank() -> {
                    actvState.error = "State is required"
                    actvState.requestFocus()
                }
                else -> {
                    btnSave.isEnabled = false
                    val addressId = editAddressId ?: ("addr_" + UUID.randomUUID().toString().take(12))

                    val address = AddressEntity(
                        id = addressId,
                        userId = activeUid,
                        name = name,
                        phone = phone,
                        pincode = pincode,
                        houseFlat = houseFlat,
                        streetArea = streetArea,
                        landmark = landmark,
                        city = city,
                        state = state,
                        type = addressType,
                        isDefault = isDefault,
                        createdAt = System.currentTimeMillis()
                    )

                    lifecycleScope.launch {
                        val existingAddresses = AddressRepository.getAddresses(this@AddEditAddressActivity, activeUid)
                        val shouldBeDefault = isDefault || existingAddresses.isEmpty()
                        val finalAddress = address.copy(isDefault = shouldBeDefault)

                        AddressRepository.saveAddress(this@AddEditAddressActivity, finalAddress)
                        UiFeedback.showSuccess(this@AddEditAddressActivity, "Address saved successfully")
                        finish()
                    }
                }
            }
        }
    }
}
