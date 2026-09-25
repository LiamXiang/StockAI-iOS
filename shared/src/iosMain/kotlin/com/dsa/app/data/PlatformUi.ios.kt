package com.dsa.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
actual fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    return remember {
        {
            val picker = UIImagePickerController().apply {
                sourceType = UIImagePickerControllerSourceType.PhotoLibrary
                delegate = object : NSObject(),
                    UIImagePickerControllerDelegateProtocol,
                    UINavigationControllerDelegateProtocol {
                    override fun imagePickerController(
                        picker: UIImagePickerController,
                        didFinishPickingMediaWithInfo: Map<Any?, *>,
                    ) {
                        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                        picker.dismissViewControllerAnimated(true, null)
                        if (image == null) {
                            onResult(null)
                            return
                        }
                        onResult(compressUIImage(image))
                    }

                    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                        picker.dismissViewControllerAnimated(true, null)
                        onResult(null)
                    }
                }
            }
            val root = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (root == null) {
                println("[StockAI] no root VC for image picker")
                onResult(null)
            } else {
                root.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

/** JPEG 0.6 质量压缩（保持原尺寸，供 OCR 使用） */
private fun compressUIImage(image: UIImage): ByteArray? {
    val data: NSData? = UIImageJPEGRepresentation(image, 0.6)
    return data?.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) {
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
    return out
}

actual fun shareText(title: String, text: String) {
    val vc = UIActivityViewController(
        activityItems = listOf("$title\n\n$text"),
        applicationActivities = null,
    )
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController
    root?.presentViewController(vc, animated = true, completion = null)
}

actual fun showToast(message: String) {
    // iOS 无系统 Toast，记日志即可；界面内已有状态文字提示
    println("[StockAI-Toast] $message")
}
