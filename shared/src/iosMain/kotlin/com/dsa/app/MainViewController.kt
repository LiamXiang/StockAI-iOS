package com.dsa.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.dsa.app.ui.SharedViewModel
import platform.UIKit.UIViewController

/** iOS 入口：SwiftUI 调用此函数获得 Compose 控制器 */
@Composable
fun MainViewController(): UIViewController {
    val vm = remember { SharedViewModel() }
    return ComposeUIViewController { App(vm) }
}
