package com.example.stylista

import android.app.Application
import com.example.stylista.utils.UserPreferencesHelper
import com.example.stylista.activities.*
import com.example.stylista.adapters.*
import com.example.stylista.chatbot.*
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*
import com.example.stylista.utils.*

class StylistaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val themeMode = UserPreferencesHelper.getThemeMode(this)
        UserPreferencesHelper.applyThemeMode(themeMode)
    }
}
