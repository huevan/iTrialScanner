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

    /**
     * 处理图像 - 性能优化版本
     * @param imagePath 原始图像路径
     * @param documentCorners 文档角点
     * @return 处理后的图像路径，失败返回null
     */
    fun processImage(imagePath: String, documentCorners: Array<PointF>): String? {
        try {
            // 降低加载图像的分辨率以提高性能
            val options = BitmapFactory.Options().apply {
                inSampleSize = 2  // 缩小到原始尺寸的1/2，可以根据需要调整
            }

            // 加载图像
            val originalBitmap = BitmapFactory.decodeFile(imagePath, options) ?: return null

            // 测量处理耗时
            val startTime = System.currentTimeMillis()

            // 转换为OpenCV格式
            val originalMat = Mat()
            Utils.bitmapToMat(originalBitmap, originalMat)

            // 获取图像尺寸
            val imageWidth = originalBitmap.width
            val imageHeight = originalBitmap.height

            // 将视图坐标转换到图像坐标
            val imagePoints = documentCorners.map { cornerPoint ->
                val scaledX = (cornerPoint.x / 100.0) * imageWidth
                val scaledY = (cornerPoint.y / 100.0) * imageHeight
                Point(scaledX, scaledY)
            }

            // 确保有4个角点，否则使用矩形作为默认值
            val srcPoints = if (imagePoints.size == 4) {
                MatOfPoint2f().apply {
                    fromList(imagePoints)
                }
            } else {
                // 使用默认矩形
                val defaultPoints = listOf(
                    Point(imageWidth * 0.2, imageHeight * 0.2),
                    Point(imageWidth * 0.8, imageHeight * 0.2),
                    Point(imageWidth * 0.8, imageHeight * 0.8),
                    Point(imageWidth * 0.2, imageHeight * 0.8)
                )
                MatOfPoint2f().apply {
                    fromList(defaultPoints)
                }
            }

            // 确定输出图像的尺寸
            // 使用限制最大尺寸的方式来避免内存问题
            val maxOutputSize = 1800.0  // 最大尺寸，可以根据设备性能调整
            val width = max(
                distance(imagePoints[0], imagePoints[1]),
                distance(imagePoints[2], imagePoints[3])
            )
            val height = max(
                distance(imagePoints[0], imagePoints[3]),
                distance(imagePoints[1], imagePoints[2])
            )

            // 计算比例以限制最大尺寸
            val scale = min(1.0, maxOutputSize / max(width, height))
            val outputWidth = (width * scale).toInt()
            val outputHeight = (height * scale).toInt()

            // 目标点矩阵
            val dstPoints = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(outputWidth.toDouble(), 0.0),
                Point(outputWidth.toDouble(), outputHeight.toDouble()),
                Point(0.0, outputHeight.toDouble())
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

            // 图像增强 - 高对比度黑白效果，适合文档
            val enhancedMat = enhanceDocument(warpedMat)

            // 转换回Bitmap
            val resultBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(enhancedMat, resultBitmap)

            // 释放OpenCV资源
            originalMat.release()
            warpedMat.release()
            enhancedMat.release()

            // 保存处理后的图像
            val processedImagePath = imagePath.replace(".jpg", "_processed.jpg")
            FileOutputStream(processedImagePath).use { out ->
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // 回收位图
            originalBitmap.recycle()
            resultBitmap.recycle()

            // 记录处理时间
            val processingTime = System.currentTimeMillis() - startTime
            Log.d("ImageProcessor", "图像处理耗时: $processingTime ms")

            return processedImagePath
        } catch (e: Exception) {
            Log.e("ImageProcessor", "处理图像失败", e)
            return null
        }
    }

    /**
     * 增强文档图像
     */
    private fun enhanceDocument(src: Mat): Mat {
        val result = Mat()

        // 转换为灰度
        val grayMat = Mat()
        Imgproc.cvtColor(src, grayMat, Imgproc.COLOR_BGR2GRAY)

        // 自适应阈值处理，增强对比度
        Imgproc.adaptiveThreshold(
            grayMat,
            result,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            11,
            2.0
        )

        // 释放临时资源
        grayMat.release()

        return result
    }

    /**
     * 计算两点之间的距离
     */
    private fun distance(p1: Point, p2: Point): Double {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return Math.sqrt(dx * dx + dy * dy)
    }
}