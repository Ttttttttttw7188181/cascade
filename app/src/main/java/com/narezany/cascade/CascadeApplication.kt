package com.narezany.cascade

import android.app.Application
import com.narezany.cascade.data.CascadeStore
import com.narezany.cascade.engine.XrayEngine
import com.narezany.cascade.shizuku.ShizukuBridge

class CascadeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CascadeStore.init(this)
        runCatching { XrayEngine.init(this) }
        ShizukuBridge.init(this)
    }
}
