package com.dsa.app.data

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.ByteArrayOutputStream
import kotlin.math.max

@Composable
actual fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        try {
            val resolver = AndroidApp.context.contentResolver
            val bitmap = BitmapFactory.decodeStream(resolver.openInputStream(uri))
            val bytes = if (bitmap != null) compressBitmap(bitmap) else null
            onResult(bytes)
        } catch (e: Exception) {
            onResult(null)
        }
    }
    return remember { { launcher.launch("image/*") } }
}

/** 压缩到最大边 1024px、JPEG 80，供 OCR 使用 */
private fun compressBitmap(bitmap: Bitmap): ByteArray? {
    val maxDim = max(bitmap.width, bitmap.height)
    val scale = if (maxDim > 1024) 1024f / maxDim else 1f
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt(),
            (bitmap.height * scale).toInt(),
            true,
        )
    } else bitmap
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
    if (scaled !== bitmap) scaled.recycle()
    bitmap.recycle()
    return baos.toByteArray()
}

actual fun shareText(title: String, text: String) {
    val ctx = AndroidApp.context
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n\n$text")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(intent, "分享"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "没有可用的分享应用", Toast.LENGTH_SHORT).show()
    }
}

actual fun showToast(message: String) {
    Toast.makeText(AndroidApp.context, message, Toast.LENGTH_SHORT).show()
}
