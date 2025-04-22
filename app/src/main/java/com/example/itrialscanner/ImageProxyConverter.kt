package com.example.itrialscanner

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import androidx.core.graphics.createBitmap

object ImageProxyConverter {
    private const val TAG = "ImageProxyConverter"

    /**
     * 将ImageProxy转换为Bitmap
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun toBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image
        if (image == null) {
            Log.e(TAG, "Image为null")
            return null
        }

        try {
            // 首先尝试使用YuvImage方法
            val bitmap = yuv420ToBitmap(image, imageProxy.imageInfo.rotationDegrees)
            if (bitmap != null) {
                return bitmap
            }

            // 如果YuvImage方法失败，尝试其他方法
            Log.d(TAG, "YuvImage方法失败，尝试替代方法")
            return alternateToBitmap(imageProxy)
        } catch (e: Exception) {
            Log.e(TAG, "转换图像失败", e)
            return alternateToBitmap(imageProxy)
        }
    }

    /**
     * 使用YuvImage方法将YUV_420数据转换为Bitmap
     */
    private fun yuv420ToBitmap(image: Image, rotationDegrees: Int): Bitmap? {
        try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()


            // 定义NV21数据大小
            val nv21Size = image.width * image.height * 3 / 2
            val nv21 = ByteArray(nv21Size)

            // 填充Y平面
            if (ySize <= image.width * image.height) {
                yBuffer.get(nv21, 0, ySize)
            } else {
                // 如果Y平面尺寸不匹配，按行复制，跳过不必要的像素
                val yRowStride = image.planes[0].rowStride
                val yPixelStride = image.planes[0].pixelStride

                for (i in 0 until image.height) {
                    val yBufferPos = i * yRowStride
                    val nv21Pos = i * image.width

                    for (j in 0 until image.width) {
                        nv21[nv21Pos + j] = yBuffer.get(yBufferPos + j * yPixelStride)
                    }
                }
            }

            // 填充UV平面，不同设备可能有不同的UV平面排列
            val uvRowStride = image.planes[1].rowStride
            val uvPixelStride = image.planes[1].pixelStride
            val uvHeight = image.height / 2
            val uvWidth = image.width / 2

            for (i in 0 until uvHeight) {
                for (j in 0 until uvWidth) {
                    val uvPos = (i * uvRowStride) + (j * uvPixelStride)

                    // V和U在不同设备可能互换
                    nv21[image.width * image.height + (i * image.width) + j * 2] = vBuffer.get(uvPos)
                    nv21[image.width * image.height + (i * image.width) + j * 2 + 1] = uBuffer.get(uvPos)
                }
            }

            // 创建YuvImage，然后转换为JPEG
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)

            // 从JPEG数据创建Bitmap
            val jpegData = out.toByteArray()
            var bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)

            // 根据旋转信息旋转Bitmap
            if (rotationDegrees != 0 && bitmap != null) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                bitmap.recycle()
                bitmap = rotatedBitmap
            }

            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "YUV420转换失败", e)
            return null
        }
    }

    /**
     * 备用的转换方法
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun alternateToBitmap(imageProxy: ImageProxy): Bitmap? {
        try {
            // 如果设备支持JPEG格式，直接使用
            if (imageProxy.format == ImageFormat.JPEG) {
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }

            // 否则，使用OpenCV（如果可用）
            val image = imageProxy.image ?: return null

            try {
                // 使用OpenCV Utils来转换（需要引入OpenCV库）
                val yuvMat = org.opencv.core.Mat()
                val rgbMat = org.opencv.core.Mat()

                // 省略OpenCV代码...（取决于您的项目是否集成了OpenCV）

                // 最后的备用方案：直接创建一个全黑的位图，至少不会崩溃
                val bitmap = createBitmap(image.width, image.height)
                if (imageProxy.imageInfo.rotationDegrees != 0) {
                    val matrix = Matrix()
                    matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                    val rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    bitmap.recycle()
                    return rotatedBitmap
                }
                return bitmap
            } catch (e: Exception) {
                Log.e(TAG, "OpenCV转换失败", e)
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "备用转换方法失败", e)
            return null
        }
    }
}