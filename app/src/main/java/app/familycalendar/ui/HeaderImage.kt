package app.familycalendar.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

private const val MAX_HEIGHT = 256
private const val MAX_WIDTH = 1024

private fun file(context: Context) = File(context.filesDir, "header.png")

/** Copies a picture the user chose into the app, shrunk to a header-sized PNG. Returns false if it can't be read. */
fun saveHeaderImage(context: Context, uri: Uri): Boolean = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
    var sample = 1
    while (bounds.outHeight / (sample * 2) >= MAX_HEIGHT && bounds.outWidth / (sample * 2) >= MAX_WIDTH / 2) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return false
    val scale = minOf(MAX_HEIGHT.toFloat() / decoded.height, MAX_WIDTH.toFloat() / decoded.width, 1f)
    val out = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true) else decoded
    file(context).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    true
}.getOrDefault(false)

fun deleteHeaderImage(context: Context) {
    file(context).delete()
}

fun loadHeaderImage(context: Context): ImageBitmap? =
    file(context).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
