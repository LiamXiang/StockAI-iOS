package com.dsa.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memcpy
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSLog
import platform.UIKit.CGSizeMake
import platform.UIKit.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UIImagePickerControllerSourceTypePhotoLibrary
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

@Composable
actual fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    return remember {
        {
            val picker = UIImagePickerController().apply {
                sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
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

@OptIn(ExperimentalForeignApi::class)
private fun compressUIImage(image: UIImage): ByteArray? {
    // 缩放到最大边 1024px
    val maxDim = maxOf(image.size.width, image.size.height)
    val scale = if (maxDim > 1024.0) 1024.0 / maxDim else 1.0
    val newW = image.size.width * scale
    val newH = image.size.height * scale
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(newW, newH), false, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, newW, newH))
    val scaled = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    val data: NSData? = scaled?.let { UIImageJPEGRepresentation(it, 0.8) }
    return data?.toByteArray()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    val out = ByteArray(len)
    if (len > 0) {
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
