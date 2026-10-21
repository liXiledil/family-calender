package app.familycalendar.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Turns a picked or shared photo, screenshot or PDF into something Claude can read, keeping uploads small. */
object AttachmentLoader {
    private const val MAX_EDGE = 1800
    private const val MAX_PDF_BYTES = 20 * 1024 * 1024

    suspend fun load(context: Context, uri: Uri): Result<Attachment> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val type = resolver.getType(uri).orEmpty()
            if (type == "application/pdf") {
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't open that file.")
                check(bytes.size <= MAX_PDF_BYTES) { "That PDF is over 20 MB." }
                Attachment(Base64.encodeToString(bytes, Base64.NO_WRAP), "application/pdf")
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                check(bounds.outWidth > 0) { "Couldn't read that image." }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) sample *= 2
                val bitmap = resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("Couldn't read that image.")
                val scale = MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
                val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
                Attachment(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), "image/jpeg")
            }
        }
    }
}
