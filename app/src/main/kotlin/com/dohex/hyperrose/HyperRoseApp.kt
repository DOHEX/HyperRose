package com.dohex.hyperrose

import android.app.Application
import com.dohex.hyperrose.ui.state.DeviceControlStore

class HyperRoseApp : Application() {
    companion object {
        lateinit var instance: HyperRoseApp
            private set

        val deviceControlStore: DeviceControlStore by lazy {
            DeviceControlStore(instance)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
