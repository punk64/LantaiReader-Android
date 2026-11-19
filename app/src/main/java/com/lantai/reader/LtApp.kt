package com.lantai.reader

import android.app.Application
import com.lantai.reader.common.Crash

class LtApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Crash.install(this)
    }

    companion object {
        lateinit var instance: LtApp
            private set
    }
}
