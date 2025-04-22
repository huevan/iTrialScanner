package com.example.itrialscanner.test

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * 用于处理图像Exif方向信息的工具类
 */
class ExifOrientationHelper {
    private val TAG = "ExifOrientationHelper"

    /**
     * 加载图像并根据Exif方向数据进行旋转校正
     */
    fun loadAndFixOrientation(imageFile: File): Bitmap? {
        try {
            // 首先获取图像的Exif方向信息
            val exifOrientation = getExifOrientation(imageFile)

            // 加载图像
            val options = BitmapFactory.Options()
            options.inSampleSize = 1  // 可根据需要修改采样率
            val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)

            if (originalBitmap == null) {
                Log.e(TAG, "无法解码图像: ${imageFile.absolutePath}")
                return null
            }

            // 如果没有旋转，则直接返回原始位图
            if (exifOrientation == ExifInterface.ORIENTATION_NORMAL || exifOrientation == ExifInterface.ORIENTATION_UNDEFINED) {
                return originalBitmap
            }

            // 旋转图像
            val matrix = Matrix()
            when (exifOrientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.preScale(-1f, 1f)
                    matrix.postRotate(90f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.preScale(-1f, 1f)
                    matrix.postRotate(270f)
                }
            }

            // 创建旋转后的位图
            val rotatedBitmap = Bitmap.createBitmap(
                originalBitmap, 0, 0,
                originalBitmap.width, originalBitmap.height,
                matrix, true
            )

            // 如果生成了新的位图，释放原始位图
            if (rotatedBitmap != originalBitmap) {
                originalBitmap.recycle()
            }

            return rotatedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "修正图像方向时出错", e)
            return null
        }
    }

    /**
     * 获取图像的Exif方向数据
     */
    private fun getExifOrientation(imageFile: File): Int {
        try {
            val exif = ExifInterface(imageFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )

            Log.d(TAG, "图像 ${imageFile.name} 的Exif方向: $orientation")
            return orientation
        } catch (e: IOException) {
            Log.e(TAG, "读取Exif数据失败", e)
            return ExifInterface.ORIENTATION_UNDEFINED
        }
    }
}