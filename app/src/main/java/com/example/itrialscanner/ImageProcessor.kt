package com.example.itrialscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import org.opencv.core.Point
import kotlin.math.max
import kotlin.math.min

object ImageProcessor {
    fun processImage(imagePath: String, documentCorners: Array<PointF>): String? {
        try {
            Log.d("ImageProcessor", "开始处理图像: $imagePath")

            // 加载原始图像
            val originalBitmap = BitmapFactory.decodeFile(imagePath)
            if (originalBitmap == null) {
                Log.e("ImageProcessor", "无法加载原始图像")
                return null
            }

            // 获取图像尺寸
            val imageWidth = originalBitmap.width
            val imageHeight = originalBitmap.height
            Log.d("ImageProcessor", "原始图像尺寸: ${imageWidth}x${imageHeight}")

            // 创建预处理后的图像副本
            val processedImagePath = imagePath.replace(".jpg", "_processed.jpg")

            // 确保我们有足够的四个角点，否则直接返回原图副本
            if (documentCorners.size < 4) {
                Log.d("ImageProcessor", "角点数量不足，返回原图")
                FileOutputStream(processedImagePath).use { out ->
                    originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                originalBitmap.recycle()
                return processedImagePath
            }

            try {
                // 转换为OpenCV格式
                val originalMat = Mat()
                Utils.bitmapToMat(originalBitmap, originalMat)

                // 定义输出尺寸 - 使用固定大小进行测试
                val outputWidth = 1000
                val outputHeight = 1500

                // 源点和目标点
                val srcPoints = MatOfPoint2f()
                val dstPoints = MatOfPoint2f()

                // 使用图像的固定比例作为源点 (简化测试)
                srcPoints.fromArray(
                    Point(imageWidth * 0.2, imageHeight * 0.2),  // 左上
                    Point(imageWidth * 0.8, imageHeight * 0.2),  // 右上
                    Point(imageWidth * 0.8, imageHeight * 0.8),  // 右下
                    Point(imageWidth * 0.2, imageHeight * 0.8)   // 左下
                )

                // 目标点为输出图像的四个角
                dstPoints.fromArray(
                    Point(0.0, 0.0),                         // 左上
                    Point(outputWidth.toDouble(), 0.0),                 // 右上
                    Point(outputWidth.toDouble(), outputHeight.toDouble()), // 右下
                    Point(0.0, outputHeight.toDouble())                 // 左下
                )

                // 计算透视变换矩阵
                val perspectiveTransform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

                // 应用透视变换
                val warpedMat = Mat()
                Imgproc.warpPerspective(
                    originalMat,
                    warpedMat,
                    perspectiveTransform,
                    Size(outputWidth.toDouble(), outputHeight.toDouble())
                )

                // 转换回Bitmap
                val resultBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(warpedMat, resultBitmap)

                // 保存处理后的图像
                FileOutputStream(processedImagePath).use { out ->
                    resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                // 释放资源
                originalMat.release()
                warpedMat.release()
                resultBitmap.recycle()

                Log.d("ImageProcessor", "图像处理成功: $processedImagePath")
            } catch (e: Exception) {
                Log.e("ImageProcessor", "OpenCV处理失败", e)
                // 出错时返回原图
                FileOutputStream(processedImagePath).use { out ->
                    originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }

            originalBitmap.recycle()
            return processedImagePath

        } catch (e: Exception) {
            Log.e("ImageProcessor", "处理图像失败", e)
            return null
        }
    }
}