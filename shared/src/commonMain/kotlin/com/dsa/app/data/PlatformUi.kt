package com.dsa.app.data

import androidx.compose.runtime.Composable

/** 从系统相册选择一张图片（OCR 导入用）。
 *  返回压缩后的 JPEG 字节；用户取消或读取失败返回 null。
 *  返回的 lambda 用于触发选择器。 */
@Composable
expect fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit

/** 分享文本（报告导出用）：Android 走系统分享，iOS 走 UIActivityViewController */
expect fun shareText(title: String, text: String)

/** 轻量提示（保存/删除成功等）：Android 走 Toast，iOS 走控制台日志 + 主界面状态提示 */
expect fun showToast(message: String)

/** Base64 编码（OCR 图片传给 AI 服务用，跨平台） */
@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
fun base64Encode(bytes: ByteArray): String = kotlin.io.encoding.Base64.encode(bytes)
