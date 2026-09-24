package com.dsa.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.dsa.app.ui.SharedViewModel
import platform.UIKit.UIViewController

/** iOS 入口：SwiftUI 调用此函数获得 Compose 控制器。
 *  注意：不能标注 @Composable —— 带 @Composable 的函数不会被导出到 ObjC 头，
 *  Swift 侧将无法访问 MainViewControllerKt。 */
fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        val vm = remember { SharedViewModel() }
        App(vm)
    }
}
