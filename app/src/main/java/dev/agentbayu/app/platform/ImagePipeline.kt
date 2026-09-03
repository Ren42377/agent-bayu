package dev.agentbayu.app.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class PreparedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int
)

class ImagePipeline(private val context: Context) {

    fun prepare(uri: Uri, maxEdge: Int = MAX_EDGE, quality: Int = QUALITY): PreparedImage? {
        val bounds = readBounds(uri) ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val decoded = decode(uri, sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge))
            ?: return null
        val rotated = rotate(decoded, orientationOf(uri))
        val scaled = scale(rotated, maxEdge)
        return try {
            PreparedImage(
                bytes = compress(scaled, quality),
                mimeType = JPEG_MIME_TYPE,
                width = scaled.width,
                height = scaled.height
            )
        } finally {
            recycleUnless(scaled, decoded)
            recycleUnless(rotated, decoded)
            decoded.recycle()
        }
    }

    fun decodeScaled(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    fun fileNameOf(uri: Uri): String? = uri.lastPathSegment?.substringAfterLast('/')

    private fun readBounds(uri: Uri): BitmapFactory.Options? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val opened = open(uri) ?: return null
        opened.use { BitmapFactory.decodeStream(it, null, options) }
        return options
    }

    private fun decode(uri: Uri, sampleSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val opened = open(uri) ?: return null
        return opened.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun orientationOf(uri: Uri): Int {
        val opened = open(uri) ?: return ExifInterface.ORIENTATION_NORMAL
        return opened.use { stream ->
            try {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } catch (error: IOException) {
                ExifInterface.ORIENTATION_NORMAL
            }
        }
    }

    private fun rotate(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }

            else -> return source
        }
        return try {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } catch (error: OutOfMemoryError) {
            Log.e(TAG, "Unable to rotate image", error)
            source
        }
    }

    private fun scale(source: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxEdge) return source
        val ratio = maxEdge.toFloat() / longest.toFloat()
        val width = (source.width * ratio).toInt().coerceAtLeast(1)
        val height = (source.height * ratio).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(source, width, height, true)
        } catch (error: OutOfMemoryError) {
            Log.e(TAG, "Unable to scale image", error)
            source
        }
    }

    private fun compress(source: Bitmap, quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.JPEG, quality, output)
        return output.toByteArray()
    }

    private fun recycleUnless(candidate: Bitmap, keep: Bitmap) {
        if (candidate !== keep && !candidate.isRecycled) candidate.recycle()
    }

    private fun open(uri: Uri): InputStream? = try {
        context.contentResolver.openInputStream(uri)
    } catch (error: IOException) {
        Log.e(TAG, "Unable to open image", error)
        null
    } catch (error: SecurityException) {
        Log.e(TAG, "No access to image", error)
        null
    }

    companion object {
        const val MAX_EDGE = 1568
        const val THUMBNAIL_EDGE = 256
        const val QUALITY = 85
        const val JPEG_MIME_TYPE = "image/jpeg"
        private const val TAG = "ImagePipeline"

        fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
            var sample = 1
            var longest = maxOf(width, height)
            while (longest / 2 >= maxEdge) {
                longest /= 2
                sample *= 2
            }
            return sample
        }
    }
}
