package com.dsa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.dsa.app.data.AndroidApp
import com.dsa.app.ui.SharedViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidApp.context = applicationContext
        setContent {
            val vm = remember { SharedViewModel() }
            App(vm)
        }
    }
}
