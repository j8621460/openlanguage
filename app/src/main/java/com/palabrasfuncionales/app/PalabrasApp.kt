package com.palabrasfuncionales.app

import android.app.Application
import android.os.StrictMode

class PalabrasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Debug builds flag disk/network work on the main thread and cleartext traffic.
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyLog().build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().detectCleartextNetwork().detectLeakedClosableObjects().penaltyLog().build()
            )
        }
    }
}
