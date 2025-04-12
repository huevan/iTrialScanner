package com.example.itrialscanner

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object ImageProxyConverter {

    /**
     * 将ImageProxy转换为Bitmap
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun toBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null

        // 使用YuvImage方法转换为JPEG然后解码为Bitmap
        val bytes = imageToJpegByteArray(image) ?: return null

        // 从JPEG数据创建Bitmap
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // 根据旋转信息旋转Bitmap
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        return bitmap
    }

    /**
     * 将Image转换为JPEG字节数组
     */
    private fun imageToJpegByteArray(image: Image): ByteArray? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            // U和V是交错的
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)

            out.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}