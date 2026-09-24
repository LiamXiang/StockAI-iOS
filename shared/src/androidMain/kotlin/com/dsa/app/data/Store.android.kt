package com.dsa.app.data

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

/** Android 全局 Application Context（在 MainActivity 中初始化） */
object AndroidApp {
    lateinit var context: Context
}

actual fun createSettings(): Settings =
    SharedPreferencesSettings(AndroidApp.context.getSharedPreferences("stockai_prefs", Context.MODE_PRIVATE))
