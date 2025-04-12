package com.example.itrialscanner

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object DocumentDetector {
    private const val TAG = "DocumentDetector"

    // 检测文档边缘 - 改进版
    fun detectDocumentCorners(bitmap: Bitmap): Array<PointF>? {
        try {
            Log.d(TAG, "开始检测文档边缘, 图像大小: ${bitmap.width}x${bitmap.height}")

            // 将Bitmap转换为Mat
            val srcMat = Mat()
            Utils.bitmapToMat(bitmap, srcMat)

            // 转换为灰度图
            val grayMat = Mat()
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)

            // 先尝试检测原始尺寸的图像
            var corners = detectCornersWithMultipleMethods(srcMat, grayMat)

            // 如果原始尺寸检测失败，尝试缩小图像
            if (corners == null) {
                Log.d(TAG, "原始尺寸检测失败，尝试缩小图像")
                val downscaledMat = Mat()
                Imgproc.resize(srcMat, downscaledMat, Size(srcMat.cols() * 0.5, srcMat.rows() * 0.5))

                val downscaledGray = Mat()
                Imgproc.cvtColor(downscaledMat, downscaledGray, Imgproc.COLOR_BGR2GRAY)

                val downscaledCorners = detectCornersWithMultipleMethods(downscaledMat, downscaledGray)

                if (downscaledCorners != null) {
                    // 将找到的角点坐标调整回原始尺寸
                    corners = Array(4) { i ->
                        PointF(
                            downscaledCorners[i].x * 2f,
                            downscaledCorners[i].y * 2f
                        )
                    }
                }

                downscaledMat.release()
                downscaledGray.release()
            }

            // 释放资源
            srcMat.release()
            grayMat.release()

            return corners
        } catch (e: Exception) {
            Log.e(TAG, "检测文档边缘失败", e)
            return null
        }
    }

    // 使用多种方法检测角点
    private fun detectCornersWithMultipleMethods(srcMat: Mat, grayMat: Mat): Array<PointF>? {
        // 保存原始图像尺寸
        val originalWidth = srcMat.cols()
        val originalHeight = srcMat.rows()

        // 方法1: 尝试使用边缘检测和轮廓发现
        val corners1 = detectCornersUsingEdges(grayMat)

        // 方法2: 尝试自适应阈值和形态学操作
        val corners2 = detectCornersUsingAdaptiveThreshold(grayMat)

        // 方法3: 尝试颜色分割
        val corners3 = detectCornersUsingColorSegmentation(srcMat)

        // 检查结果并优先选择最佳结果
        val result = when {
            // 如果方法3找到了角点，且面积最大，优先使用方法3
            corners3 != null && (corners1 == null || calculateArea(corners3) > calculateArea(corners1)) &&
                    (corners2 == null || calculateArea(corners3) > calculateArea(corners2)) -> {
                Log.d(TAG, "使用颜色分割方法找到角点")
                corners3
            }
            // 如果方法2找到了角点，且面积大于方法1，使用方法2
            corners2 != null && (corners1 == null || calculateArea(corners2) > calculateArea(corners1)) -> {
                Log.d(TAG, "使用自适应阈值方法找到角点")
                corners2
            }
            // 默认使用方法1的结果
            corners1 != null -> {
                Log.d(TAG, "使用边缘检测方法找到角点")
                corners1
            }
            // 所有方法都失败，返回默认角点（图像四个角）
            else -> {
                Log.d(TAG, "所有检测方法失败，使用默认角点")
                Array(4) { i ->
                    when (i) {
                        0 -> PointF(0f, 0f)  // 左上
                        1 -> PointF(originalWidth.toFloat(), 0f)  // 右上
                        2 -> PointF(originalWidth.toFloat(), originalHeight.toFloat())  // 右下
                        3 -> PointF(0f, originalHeight.toFloat())  // 左下
                        else -> PointF(0f, 0f)
                    }
                }
            }
        }

        return result
    }

    // 方法1: 边缘检测和轮廓发现
    private fun detectCornersUsingEdges(grayMat: Mat): Array<PointF>? {
        // 保存原始图像尺寸
        val originalWidth = grayMat.cols()
        val originalHeight = grayMat.rows()

        // 估计可能的纸张占比（假设纸张至少占据图像的20%面积）
        val minArea = originalWidth * originalHeight * 0.2

        try {
            // 保存灰度图的副本，避免修改原始图像
            val workingMat = grayMat.clone()

            // 高斯模糊降噪
            Imgproc.GaussianBlur(workingMat, workingMat, Size(5.0, 5.0), 0.0)

            // 边缘检测
            val cannyMat = Mat()
            Imgproc.Canny(workingMat, cannyMat, 50.0, 200.0)

            // 膨胀操作以连接边缘
            val dilatedMat = Mat()
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.dilate(cannyMat, dilatedMat, kernel)

            // 寻找轮廓
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(dilatedMat, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            // 按面积从大到小排序轮廓
            contours.sortByDescending { Imgproc.contourArea(it) }

            // 遍历轮廓寻找最佳四边形
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)

                // 忽略太小的轮廓
                if (area < minArea) continue

                // 近似轮廓为多边形
                val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)

                // 如果近似后是四边形
                if (approx.total() == 4L) {
                    // 检查是否是凸四边形
                    val points = approx.toArray()
                    if (isConvexQuadrilateral(points)) {
                        // 对点进行排序（左上，右上，右下，左下）
                        val sortedPoints = sortPoints(points)

                        // 释放资源
                        workingMat.release()
                        cannyMat.release()
                        dilatedMat.release()
                        hierarchy.release()

                        // 将OpenCV点转换为Android PointF
                        return Array(4) { i ->
                            PointF(sortedPoints[i].x.toFloat(), sortedPoints[i].y.toFloat())
                        }
                    }
                }
            }

            // 如果没有找到合适的四边形，尝试使用最大轮廓的外接矩形
            if (contours.isNotEmpty()) {
                val largestContour = contours[0]
                val area = Imgproc.contourArea(largestContour)

                if (area >= minArea) {
                    val rect = Imgproc.minAreaRect(MatOfPoint2f(*largestContour.toArray()))
                    val points = Array(4) { Point() }
                    rect.points(points)

                    // 对点进行排序
                    val sortedPoints = sortPoints(points)

                    // 释放资源
                    workingMat.release()
                    cannyMat.release()
                    dilatedMat.release()
                    hierarchy.release()

                    // 将OpenCV点转换为Android PointF
                    return Array(4) { i ->
                        PointF(sortedPoints[i].x.toFloat(), sortedPoints[i].y.toFloat())
                    }
                }
            }

            // 释放资源
            workingMat.release()
            cannyMat.release()
            dilatedMat.release()
            hierarchy.release()

            return null
        } catch (e: Exception) {
            Log.e(TAG, "边缘检测方法失败", e)
            return null
        }
    }

    // 方法2: 使用自适应阈值和形态学操作
    private fun detectCornersUsingAdaptiveThreshold(grayMat: Mat): Array<PointF>? {
        // 保存原始图像尺寸
        val originalWidth = grayMat.cols()
        val originalHeight = grayMat.rows()

        // 估计可能的纸张占比
        val minArea = originalWidth * originalHeight * 0.2

        try {
            // 保存灰度图的副本
            val workingMat = grayMat.clone()

            // 应用高斯模糊
            Imgproc.GaussianBlur(workingMat, workingMat, Size(5.0, 5.0), 0.0)

            // 自适应阈值
            val binaryMat = Mat()
            Imgproc.adaptiveThreshold(
                workingMat,
                binaryMat,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                11,
                2.0
            )

            // 形态学闭操作，连接边缘
            val morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
            Imgproc.morphologyEx(binaryMat, binaryMat, Imgproc.MORPH_CLOSE, morphKernel)

            // 寻找轮廓
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(binaryMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            // 按面积从大到小排序轮廓
            contours.sortByDescending { Imgproc.contourArea(it) }

            // 遍历轮廓寻找最佳四边形
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)

                // 忽略太小的轮廓
                if (area < minArea) continue

                // 近似轮廓为多边形
                val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)

                // 检查多边形的顶点数
                val vertexCount = approx.total().toInt()

                // 如果是四边形，或者顶点数在4-6之间（可能是由于噪声导致多边形不规则）
                if (vertexCount >= 4 && vertexCount <= 6) {
                    // 如果不是恰好4个顶点，则使用最小外接矩形
                    val points = if (vertexCount == 4) {
                        approx.toArray()
                    } else {
                        val rect = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
                        val rectPoints = Array(4) { Point() }
                        rect.points(rectPoints)
                        rectPoints
                    }

                    // 对点进行排序
                    val sortedPoints = sortPoints(points)

                    // 释放资源
                    workingMat.release()
                    binaryMat.release()
                    hierarchy.release()

                    // 将OpenCV点转换为Android PointF
                    return Array(4) { i ->
                        PointF(sortedPoints[i].x.toFloat(), sortedPoints[i].y.toFloat())
                    }
                }
            }

            // 释放资源
            workingMat.release()
            binaryMat.release()
            hierarchy.release()

            return null
        } catch (e: Exception) {
            Log.e(TAG, "自适应阈值方法失败", e)
            return null
        }
    }

    // 方法3: 使用颜色分割
    private fun detectCornersUsingColorSegmentation(srcMat: Mat): Array<PointF>? {
        // 保存原始图像尺寸
        val originalWidth = srcMat.cols()
        val originalHeight = srcMat.rows()

        // 估计可能的纸张占比
        val minArea = originalWidth * originalHeight * 0.2

        try {
            // 转换为HSV色彩空间
            val hsvMat = Mat()
            Imgproc.cvtColor(srcMat, hsvMat, Imgproc.COLOR_BGR2HSV)

            // 创建掩码，提取可能的白色/明亮区域
            val lowerWhite = Scalar(0.0, 0.0, 200.0)
            val upperWhite = Scalar(180.0, 30.0, 255.0)

            val mask = Mat()
            Core.inRange(hsvMat, lowerWhite, upperWhite, mask)

            // 形态学操作，增强纸张区域
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(11.0, 11.0))
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)

            // 寻找轮廓
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            // 按面积从大到小排序轮廓
            contours.sortByDescending { Imgproc.contourArea(it) }

            // 遍历轮廓寻找最佳四边形
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)

                // 忽略太小的轮廓
                if (area < minArea) continue

                // 使用最小外接矩形
                val rect = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
                val points = Array(4) { Point() }
                rect.points(points)

                // 对点进行排序
                val sortedPoints = sortPoints(points)

                // 释放资源
                hsvMat.release()
                mask.release()
                hierarchy.release()

                // 将OpenCV点转换为Android PointF
                return Array(4) { i ->
                    PointF(sortedPoints[i].x.toFloat(), sortedPoints[i].y.toFloat())
                }
            }

            // 释放资源
            hsvMat.release()
            mask.release()
            hierarchy.release()

            return null
        } catch (e: Exception) {
            Log.e(TAG, "颜色分割方法失败", e)
            return null
        }
    }

    // 计算四边形面积
    private fun calculateArea(corners: Array<PointF>): Float {
        if (corners.size != 4) return 0f

        // 使用叉积计算四边形面积
        val x1 = corners[0].x
        val y1 = corners[0].y
        val x2 = corners[1].x
        val y2 = corners[1].y
        val x3 = corners[2].x
        val y3 = corners[2].y
        val x4 = corners[3].x
        val y4 = corners[3].y

        return 0.5f * abs((x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y4 - x4 * y3) + (x4 * y1 - x1 * y4))
    }

    // 判断是否是凸四边形
    private fun isConvexQuadrilateral(points: Array<Point>): Boolean {
        if (points.size != 4) return false

        // 创建一个点的列表
        val pointsList = ArrayList<Point>()
        for (point in points) {
            pointsList.add(point)
        }

        // 创建MatOfPoint
        val contour = MatOfPoint()
        contour.fromList(pointsList)

        // 检查是否为凸形
        return Imgproc.isContourConvex(contour)
    }

    // 对点进行排序（左上，右上，右下，左下）
    private fun sortPoints(points: Array<Point>): Array<Point> {
        val result = arrayOfNulls<Point>(4)
        val sumPoints = DoubleArray(points.size)
        val diffPoints = DoubleArray(points.size)

        // 计算每个点的x+y和x-y值
        for (i in points.indices) {
            sumPoints[i] = points[i].x + points[i].y
            diffPoints[i] = points[i].x - points[i].y
        }

        // 找到最小和最大值的索引
        var minSumIndex = 0
        var maxSumIndex = 0
        var minDiffIndex = 0
        var maxDiffIndex = 0

        for (i in 1 until points.size) {
            // 找最小x+y值的索引 (左上角)
            if (sumPoints[i] < sumPoints[minSumIndex]) {
                minSumIndex = i
            }

            // 找最大x+y值的索引 (右下角)
            if (sumPoints[i] > sumPoints[maxSumIndex]) {
                maxSumIndex = i
            }

            // 找最小x-y值的索引 (左下角)
            if (diffPoints[i] < diffPoints[minDiffIndex]) {
                minDiffIndex = i
            }

            // 找最大x-y值的索引 (右上角)
            if (diffPoints[i] > diffPoints[maxDiffIndex]) {
                maxDiffIndex = i
            }
        }

        // 左上角点 = 最小的x+y值
        result[0] = points[minSumIndex]

        // 右下角点 = 最大的x+y值
        result[2] = points[maxSumIndex]

        // 右上角点 = 最大的x-y值
        result[1] = points[maxDiffIndex]

        // 左下角点 = 最小的x-y值
        result[3] = points[minDiffIndex]

        return result.requireNoNulls()
    }

    // 将原始图像裁剪为文档区域
    fun warpDocument(bitmap: Bitmap, corners: Array<PointF>): Bitmap {
        try {
            if (corners.size != 4) {
                throw IllegalArgumentException("需要4个角点")
            }

            // 将Bitmap转换为Mat
            val srcMat = Mat()
            Utils.bitmapToMat(bitmap, srcMat)

            // 计算目标文档的宽度和高度
            val widthBottom = Math.sqrt(
                Math.pow(corners[3].x - corners[2].x.toDouble(), 2.0) +
                        Math.pow(corners[3].y - corners[2].y.toDouble(), 2.0)
            )
            val widthTop = Math.sqrt(
                Math.pow(corners[1].x - corners[0].x.toDouble(), 2.0) +
                        Math.pow(corners[1].y - corners[0].y.toDouble(), 2.0)
            )
            val width = max(widthBottom, widthTop)

            val heightRight = Math.sqrt(
                Math.pow(corners[1].x - corners[2].x.toDouble(), 2.0) +
                        Math.pow(corners[1].y - corners[2].y.toDouble(), 2.0)
            )
            val heightLeft = Math.sqrt(
                Math.pow(corners[0].x - corners[3].x.toDouble(), 2.0) +
                        Math.pow(corners[0].y - corners[3].y.toDouble(), 2.0)
            )
            val height = max(heightRight, heightLeft)

            // 创建源点和目标点
            val srcPoints = MatOfPoint2f()
            val dstPoints = MatOfPoint2f()

            // 源点 - 文档角点
            srcPoints.fromArray(
                Point(corners[0].x.toDouble(), corners[0].y.toDouble()),
                Point(corners[1].x.toDouble(), corners[1].y.toDouble()),
                Point(corners[2].x.toDouble(), corners[2].y.toDouble()),
                Point(corners[3].x.toDouble(), corners[3].y.toDouble())
            )

            // 目标点 - 输出图像的四个角点
            dstPoints.fromArray(
                Point(0.0, 0.0),
                Point(width, 0.0),
                Point(width, height),
                Point(0.0, height)
            )

            // 计算透视变换矩阵
            val perspectiveTransform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

            // 应用透视变换
            val warpedMat = Mat()
            Imgproc.warpPerspective(srcMat, warpedMat, perspectiveTransform, Size(width, height))

            // 转换回Bitmap
            val resultBitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warpedMat, resultBitmap)

            // 释放资源
            srcMat.release()
            warpedMat.release()
            srcPoints.release()
            dstPoints.release()
            perspectiveTransform.release()

            return resultBitmap
        } catch (e: Exception) {
            Log.e(TAG, "裁剪文档失败", e)
            return bitmap
        }
    }
}