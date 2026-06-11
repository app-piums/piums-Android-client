package com.piums.cliente.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream

/**
 * Convierte cualquier imagen del picker (incluye HEIC) a JPEG redimensionado.
 * El backend solo acepta JPG/PNG/WebP (valida magic bytes) y máximo 5MB,
 * así que subir los bytes crudos del picker falla con fotos grandes o HEIC.
 */
object ImageUtils {

    fun compressedJpeg(context: Context, uri: Uri, maxPx: Int = 2000, quality: Int = 80): ByteArray {
        val bitmap = decode(context, uri, maxPx)
        val scale = minOf(maxPx.toFloat() / bitmap.width, maxPx.toFloat() / bitmap.height, 1f)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }

    private fun decode(context: Context, uri: Uri, maxPx: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder decodifica HEIC y aplica la rotación EXIF automáticamente
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(maxPx.toFloat() / w, maxPx.toFloat() / h, 1f)
                if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
            }
        }
        // API 26-27: BitmapFactory con inSampleSize
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx || bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("No se pudo leer la imagen")
    }
}
