package com.example

import android.app.Application
import com.example.logging.CrashReporter
import com.example.logging.OperationalLogCatcher

class VianshieldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OperationalLogCatcher.initialize(this)
        CrashReporter.install(this)
    }
}
