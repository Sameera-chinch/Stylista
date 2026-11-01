package com.example.stylista

import android.app.Application
import com.example.stylista.utils.UserPreferencesHelper

class StylistaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val themeMode = UserPreferencesHelper.getThemeMode(this)
        UserPreferencesHelper.applyThemeMode(themeMode)
    }
}
