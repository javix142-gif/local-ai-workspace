package com.localai.workspace.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Bounds-first image preparation for multimodal runtimes. Large source bitmaps are never retained. */
class ImagePreprocessor(private val context: Context, private val outputDirectory: File? = null) {
    suspend fun prepare(uri: Uri, maxDimension: Int = 2048): File = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "The selected image could not be opened" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a valid image" }
        val sample = calculateSample(bounds.outWidth, bounds.outHeight, maxDimension)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "The selected image could not be opened" }
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IllegalArgumentException("The selected image could not be decoded")
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                ExifInterface(requireNotNull(input)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        val bitmap = try {
            if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } catch (failure: Throwable) { decoded.recycle(); throw failure }
        if (bitmap !== decoded) decoded.recycle()
        val directory = (outputDirectory ?: File(context.filesDir, "attachments")).apply { mkdirs() }
        val output = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            FileOutputStream(output).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) { "The image could not be prepared" }
                stream.fd.sync()
            }
            output
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        } finally {
            bitmap.recycle()
        }
    }

    private fun calculateSample(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var largest = maxOf(width, height)
        while (largest / sample > maxDimension) sample *= 2
        return sample
    }
}
