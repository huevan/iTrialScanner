package com.example.itrialscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object ImageProcessor {
    private const val TAG = "ImageProcessor"

    fun processImage(imagePath: String, documentCorners: Array<PointF>?): String? {
        try {
            Log.d(TAG, "开始处理图像: $imagePath")

            // 加载原始图像
            val originalBitmap = BitmapFactory.decodeFile(imagePath)
            if (originalBitmap == null) {
                Log.e(TAG, "无法加载原始图像")
                return null
            }

            // 创建预处理后的图像副本
            val processedImagePath = imagePath.replace(".jpg", "_processed.jpg")

            // 确保我们有足够的四个角点，否则尝试检测文档边缘
            var corners = documentCorners
            if (corners == null || corners.size < 4) {
                Log.d(TAG, "尝试自动检测文档边缘")
                corners = DocumentDetector.detectDocumentCorners(originalBitmap)
            }

            // 如果检测到了文档边缘
            if (corners != null && corners.size == 4) {
                Log.d(TAG, "使用检测到的边缘裁剪文档")

                // 裁剪文档
                val warpedBitmap = DocumentDetector.warpDocument(originalBitmap, corners)

                // 保存处理后的图像
                FileOutputStream(processedImagePath).use { out ->
                    warpedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                // 回收资源
                warpedBitmap.recycle()
                originalBitmap.recycle()

                return processedImagePath
            } else {
                Log.d(TAG, "未检测到文档边缘，保存原始图像")
                // 如果没有检测到文档边缘，保存原始图像
                FileOutputStream(processedImagePath).use { out ->
                    originalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                originalBitmap.recycle()
                return processedImagePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理图像失败", e)
            return null
        }
    }
}