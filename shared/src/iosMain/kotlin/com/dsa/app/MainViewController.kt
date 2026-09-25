package com.dsa.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.dsa.app.ui.SharedViewModel
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLog
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs

/** iOS 入口：SwiftUI 调用此函数获得 Compose 控制器。
 *  注意：不能标注 @Composable —— 带 @Composable 的函数不会被导出到 ObjC 头，
 *  Swift 侧将无法访问 MainViewControllerKt。 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
fun MainViewController(): UIViewController {
    // 未捕获异常钩子：写文件兜底（console 不可见时仍可读）+ println
    // （terminate 路径里 NSLog 的 ObjC 桥接不安全，会二次崩溃，故不用 NSLog）
    setUnhandledExceptionHook { e ->
        val msg = e.message ?: e.toString()
        println("[StockAI-Crash] $msg")
        try {
            val docs = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory, NSUserDomainMask, true
            ).firstOrNull() as? String
            if (docs != null) {
                val f = fopen("$docs/stockai_crash.log", "a")
                if (f != null) {
                    fputs("[StockAI-Crash] $msg\n", f)
                    fclose(f)
                }
            }
        } catch (e2: Throwable) {
            // 忽略写文件失败
        }
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
