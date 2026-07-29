package com.stormalert

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StormAlertApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
    }
}
