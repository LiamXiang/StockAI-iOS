package com.dsa.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.dsa.app.ui.SharedViewModel
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.UIKit.UIViewController

/** iOS 入口：SwiftUI 调用此函数获得 Compose 控制器。
 *  注意：不能标注 @Composable —— 带 @Composable 的函数不会被导出到 ObjC 头，
 *  Swift 侧将无法访问 MainViewControllerKt。 */
@OptIn(ExperimentalNativeApi::class)
fun MainViewController(): UIViewController {
    // 未捕获异常钩子：记录到系统日志，便于定位闪退（显式 NSString 桥接，避免 NSLog format 崩溃）
    setUnhandledExceptionHook { e ->
        val msg = e.message ?: e.toString()
        val ns = NSString.create(string = "[StockAI-Crash] $msg")
        NSLog("%@", ns)
    }
    NSLog("[StockAI-MARK] enter MainViewController")
    return try {
        NSLog("[StockAI-MARK] creating ComposeUIViewController")
        ComposeUIViewController {
            NSLog("[StockAI-MARK] compose content starts")
            val vm = remember { SharedViewModel() }
            NSLog("[StockAI-MARK] viewmodel created")
            LaunchedEffect(Unit) {
                NSLog("[StockAI-MARK] first frame launched, app is rendering")
            }
            App(vm)
        }.also {
            NSLog("[StockAI-MARK] controller created, returning to Swift")
        }
    } catch (e: Throwable) {
        NSLog("[StockAI-InitError] %@", e.toString())
        throw e
    }
}
