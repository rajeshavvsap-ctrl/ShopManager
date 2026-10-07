package com.shopmanager.app

import android.app.Application
import android.content.Context
import com.shopmanager.app.data.AppDatabase
import com.shopmanager.app.data.ShopRepository
import com.shopmanager.app.util.Settings

class ShopApp : Application() {
    val repository: ShopRepository by lazy { ShopRepository(AppDatabase.build(this)) }
    val settings: Settings by lazy { Settings(this) }
}

val Context.shopApp: ShopApp get() = applicationContext as ShopApp
