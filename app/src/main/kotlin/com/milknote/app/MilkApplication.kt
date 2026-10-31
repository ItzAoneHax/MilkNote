package com.milknote.app

import android.app.Application
import com.milknote.app.di.ServiceLocator

class MilkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
