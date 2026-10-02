package com.anitec.platform.livestock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.livestock.infrastructure.AnimalImageUploaderImpl
import com.anitec.platform.livestock.infrastructure.remote.LivestockApi
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Runs the real decode/compress code on a device, with a fake API that captures what would be uploaded. */
@RunWith(AndroidJUnit4::class)
class AnimalImageUploaderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val api = mockk<LivestockApi>()
    private val files = mutableListOf<File>()

    @Before
    fun stubApi() {
        coEvery { api.uploadImage(any()) } returns com.anitec.platform.livestock.infrastructure.remote.UploadedImageDto("/uploads/animals/x.jpg")
    }

    @After
    fun cleanUp() = files.forEach { it.delete() }

    private fun photo(width: Int, height: Int, orientation: Int? = null): String {
        val file = File(context.cacheDir, "test_${System.nanoTime()}.jpg").also(files::add)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(120, 170, 100)) }
            .also { FileOutputStream(file).use { out -> it.compress(Bitmap.CompressFormat.JPEG, 90, out) } }
        if (orientation != null) {
            ExifInterface(file.absolutePath).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
        }
        return file.toUri().toString()
    }

    private fun capturedBytes(part: MultipartBody.Part): ByteArray {
        val buffer = Buffer()
        part.body.writeTo(buffer)
        return buffer.readByteArray()
    }

    @Test
    fun aSmallPhotoIsUploaded() = runTest {
        val part = slot<MultipartBody.Part>()
        coEvery { api.uploadImage(capture(part)) } returns com.anitec.platform.livestock.infrastructure.remote.UploadedImageDto("/uploads/animals/ok.jpg")

        val result = AnimalImageUploaderImpl(context, api).upload(photo(400, 300))

        assertEquals(AppResult.Success("/uploads/animals/ok.jpg"), result)
        val bitmap = BitmapFactory.decodeByteArray(capturedBytes(part.captured), 0, capturedBytes(part.captured).size)
        assertEquals(400, bitmap.width)
        assertEquals(300, bitmap.height)
    }

    @Test
    fun aLargePhotoIsDownscaledBeforeUpload() = runTest {
        val part = slot<MultipartBody.Part>()
        coEvery { api.uploadImage(capture(part)) } returns com.anitec.platform.livestock.infrastructure.remote.UploadedImageDto("/uploads/animals/big.jpg")

        val result = AnimalImageUploaderImpl(context, api).upload(photo(4000, 3000))

        assertTrue(result is AppResult.Success)
        val bytes = capturedBytes(part.captured)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertTrue("longest side was ${maxOf(bitmap.width, bitmap.height)}", maxOf(bitmap.width, bitmap.height) <= 2000)
    }

    @Test
    fun cameraRotationIsApplied() = runTest {
        val part = slot<MultipartBody.Part>()
        coEvery { api.uploadImage(capture(part)) } returns com.anitec.platform.livestock.infrastructure.remote.UploadedImageDto("/uploads/animals/r.jpg")

        // Landscape pixels flagged "rotate 90": the stored picture must come out in portrait.
        AnimalImageUploaderImpl(context, api).upload(photo(600, 400, ExifInterface.ORIENTATION_ROTATE_90))

        val bytes = capturedBytes(part.captured)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(400, bitmap.width)
        assertEquals(600, bitmap.height)
    }

    @Test
    fun aMissingFileFailsWithoutCrashing() = runTest {
        val result = AnimalImageUploaderImpl(context, api).upload(File(context.cacheDir, "does_not_exist.jpg").toUri().toString())
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun sampleSize_halvesWhileTheSideStaysAtLeast1600() {
        assertEquals(1, AnimalImageUploaderImpl.sampleSizeFor(1600, 1200))
        assertEquals(1, AnimalImageUploaderImpl.sampleSizeFor(3000, 2000))
        assertEquals(2, AnimalImageUploaderImpl.sampleSizeFor(3200, 2400))
        assertEquals(4, AnimalImageUploaderImpl.sampleSizeFor(6400, 4800))
    }
}
