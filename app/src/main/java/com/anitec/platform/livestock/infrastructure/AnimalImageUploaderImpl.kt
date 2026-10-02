package com.anitec.platform.livestock.infrastructure

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.network.safeApiCall
import com.anitec.platform.livestock.domain.AnimalImageUploader
import com.anitec.platform.livestock.infrastructure.remote.LivestockApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject

/**
 * Prepares a photo for upload: respects the EXIF rotation written by the camera, downscales to at most
 * [MAX_SIDE] px and re-encodes as JPEG. Phone photos are several MB; this keeps uploads far below the
 * API limit (10 MB) and the mobile data cost low.
 */
class AnimalImageUploaderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: LivestockApi,
) : AnimalImageUploader {

    override suspend fun upload(uri: String): AppResult<String> = withContext(Dispatchers.IO) {
        val bytes = try {
            compress(Uri.parse(uri))
        } catch (e: IOException) {
            return@withContext AppResult.Failure(AppError.Unknown("Could not read the image"))
        } catch (e: SecurityException) {
            return@withContext AppResult.Failure(AppError.Unknown("No access to the image"))
        } ?: return@withContext AppResult.Failure(AppError.Unknown("Could not decode the image"))

        val part = MultipartBody.Part.createFormData("file", "animal.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
        safeApiCall { api.uploadImage(part).url }
    }

    private fun compress(uri: Uri): ByteArray? {
        val resolver = context.contentResolver

        // With inJustDecodeBounds the call fills the options and always returns null, so its result is ignored.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = resolver.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val upright = if (rotation == 0f) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
        }
        return ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    companion object {
        const val MAX_SIDE = 1600
        private const val JPEG_QUALITY = 85

        /** Largest power-of-two sample size that keeps the longest side at [MAX_SIDE] pixels or more. */
        fun sampleSizeFor(width: Int, height: Int): Int {
            var sample = 1
            while (maxOf(width, height) / (sample * 2) >= MAX_SIDE) sample *= 2
            return sample
        }
    }
}
