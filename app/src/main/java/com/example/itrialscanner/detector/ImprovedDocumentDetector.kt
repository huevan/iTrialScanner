package com.example.itrialscanner.detector

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 基于OpenCV的改进文档检测器
 */
object ImprovedDocumentDetector {
    private val TAG = "ImprovedDocumentDetector"

    // 边缘检测参数
    private var cannyThreshold1 = 30.0
    private var cannyThreshold2 = 150.0

    // 面积比例限制
    private const val MIN_DOCUMENT_AREA_RATIO = 0.15
    private const val MAX_DOCUMENT_AREA_RATIO = 0.98

    // 调试模式
    var isDebugMode = false
    private var debugMat: Mat? = null

    /**
     * 文档检测核心方法
     */
    @SuppressLint("LongLogTag")
    fun detectDocumentCorners(bitmap: Bitmap): Array<PointF> {
        val srcMat = Mat()
        Utils.bitmapToMat(bitmap, srcMat)
        val originalWidth = srcMat.cols()
        val originalHeight = srcMat.rows()
        val totalArea = originalWidth * originalHeight

        // 转为灰度
        val grayMat = Mat()
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_BGR2GRAY)

        // 高斯模糊减少噪声
        val blurredMat = Mat()
        Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)

        // 保存原始灰度图用于后续处理
        val originalGray = blurredMat.clone()

        // 使用Canny边缘检测
        val edgeMat = Mat()
        Imgproc.Canny(blurredMat, edgeMat, cannyThreshold1, cannyThreshold2)

        // 膨胀操作，连接断开的边缘
        val dilatedMat = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edgeMat, dilatedMat, kernel)

        // 使用两个方法查找和评估轮廓
        val result = findBestContour(dilatedMat, originalGray, totalArea)

        // 如果没找到合适的轮廓，尝试另一种方法
        if (result == null) {
            // 使用自适应阈值处理
            val thresholdMat = Mat()
            Imgproc.adaptiveThreshold(
                originalGray, thresholdMat, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 11, 2.0
            )

            // 形态学操作优化二值图
            val morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            val morphedMat = Mat()
            Imgproc.morphologyEx(thresholdMat, morphedMat, Imgproc.MORPH_CLOSE, morphKernel)

            val secondResult = findBestContour(morphedMat, originalGray, totalArea)

            if (secondResult != null) {
                if (isDebugMode) {
                    debugMat = morphedMat.clone()
                }
                return secondResult
            }
        } else {
            if (isDebugMode) {
                debugMat = dilatedMat.clone()
            }
            return result
        }

        // 如果两种方法都失败，返回默认边框
        Log.d(TAG, "无法检测到文档边框，使用默认值")
        return createDefaultCorners(originalWidth, originalHeight)
    }

    /**
     * 查找最佳文档轮廓
     */
    private fun findBestContour(binaryMat: Mat, originalGray: Mat, totalArea: Int): Array<PointF>? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()

        // 查找轮廓
        Imgproc.findContours(
            binaryMat, contours, hierarchy,
            Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE
        )

        // 如果没有找到轮廓，返回null
        if (contours.isEmpty()) return null

        // 按面积排序
        contours.sortByDescending { Imgproc.contourArea(it) }

        val minDocArea = totalArea * MIN_DOCUMENT_AREA_RATIO
        val maxDocArea = totalArea * MAX_DOCUMENT_AREA_RATIO

        // 最佳候选轮廓
        var bestContour: MatOfPoint? = null
        var bestScore = Double.MAX_VALUE
        var bestCorners: Array<PointF>? = null

        // 评估每个轮廓
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)

            // 面积检查
            if (area < minDocArea || area > maxDocArea) continue

            // 将轮廓转换为多边形近似
            val contourFloat = MatOfPoint2f(*contour.toArray())
            val perimeter = Imgproc.arcLength(contourFloat, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contourFloat, approx, 0.02 * perimeter, true)

            // 寻找四边形
            if (approx.total().toInt() == 4) {
                // 检查是否凸多边形
                if (!isConvexQuadrilateral(approx)) continue

                // 计算多边形评分（基于角度、边长比例等）
                val corners = approx.toArray()
                val score = evaluateQuadrilateral(corners, originalGray)

                if (score < bestScore) {
                    bestScore = score
                    bestContour = contour

                    // 转换为有序的PointF数组
                    val orderedCorners = orderPoints(corners)
                    bestCorners = Array(4) { i ->
                        PointF(orderedCorners[i].x.toFloat(), orderedCorners[i].y.toFloat())
                    }
                }
            }
        }

        // 如果找到了最佳四边形，返回它
        if (bestCorners != null) {
            return bestCorners
        }

        // 如果没找到合适的四边形，但有大轮廓，使用最大轮廓的外接矩形
        if (contours.isNotEmpty()) {
            val largestContour = contours[0]
            val area = Imgproc.contourArea(largestContour)

            if (area >= minDocArea) {
                // 尝试再次多边形近似，使用更宽松的参数
                val contourFloat = MatOfPoint2f(*largestContour.toArray())
                val perimeter = Imgproc.arcLength(contourFloat, true)
                val approx = MatOfPoint2f()

                // 尝试多个近似参数
                val approximationFactors = doubleArrayOf(0.02, 0.03, 0.04, 0.05)

                for (factor in approximationFactors) {
                    Imgproc.approxPolyDP(contourFloat, approx, factor * perimeter, true)

                    // 如果近似为四边形
                    if (approx.total().toInt() == 4 && isConvexQuadrilateral(approx)) {
                        val corners = approx.toArray()
                        val orderedCorners = orderPoints(corners)
                        return Array(4) { i ->
                            PointF(orderedCorners[i].x.toFloat(), orderedCorners[i].y.toFloat())
                        }
                    }
                }

                // 如果无法获得四边形，使用最小面积外接矩形
                val rect = Imgproc.minAreaRect(contourFloat)
                val rectPoints = Array(4) { Point() }
                rect.points(rectPoints)

                val orderedRectPoints = orderPoints(rectPoints)
                return Array(4) { i ->
                    PointF(orderedRectPoints[i].x.toFloat(), orderedRectPoints[i].y.toFloat())
                }
            }
        }

        return null
    }

    /**
     * 检查是否为凸四边形
     */
    private fun isConvexQuadrilateral(points: MatOfPoint2f): Boolean {
        val cornerMat = MatOfPoint(*points.toArray().map { Point(it.x, it.y) }.toTypedArray())
        return Imgproc.isContourConvex(cornerMat)
    }

    /**
     * 评估四边形质量
     * 返回分数（越低越好）
     */
    private fun evaluateQuadrilateral(corners: Array<Point>, grayMat: Mat): Double {
        if (corners.size != 4) return Double.MAX_VALUE

        // 排序角点
        val orderedCorners = orderPoints(corners)

        // 计算各边长度
        val edgeLengths = Array(4) { i ->
            val next = (i + 1) % 4
            sqrt(
                (orderedCorners[next].x - orderedCorners[i].x).pow(2) +
                        (orderedCorners[next].y - orderedCorners[i].y).pow(2)
            )
        }

        // 计算对边长度比
        val edgeRatio1 = max(edgeLengths[0], edgeLengths[2]) / max(0.001, min(edgeLengths[0], edgeLengths[2]))
        val edgeRatio2 = max(edgeLengths[1], edgeLengths[3]) / max(0.001, min(edgeLengths[1], edgeLengths[3]))

        // 计算对角线比例
        val diagonal1 = sqrt(
            (orderedCorners[2].x - orderedCorners[0].x).pow(2) +
                    (orderedCorners[2].y - orderedCorners[0].y).pow(2)
        )
        val diagonal2 = sqrt(
            (orderedCorners[3].x - orderedCorners[1].x).pow(2) +
                    (orderedCorners[3].y - orderedCorners[1].y).pow(2)
        )
        val diagonalRatio = max(diagonal1, diagonal2) / max(0.001, min(diagonal1, diagonal2))

        // 计算角点梯度强度（边缘清晰度）
        var cornerGradientSum = 0.0
        val kernelSize = 7
        val halfKernel = kernelSize / 2

        for (corner in orderedCorners) {
            val x = corner.x.toInt()
            val y = corner.y.toInt()

            // 确保点在图像内
            if (x >= halfKernel && y >= halfKernel &&
                x < grayMat.cols() - halfKernel && y < grayMat.rows() - halfKernel) {

                val roi = grayMat.submat(
                    y - halfKernel, y + halfKernel,
                    x - halfKernel, x + halfKernel
                )

                val gradX = Mat()
                val gradY = Mat()
                Imgproc.Sobel(roi, gradX, CvType.CV_16S, 1, 0)
                Imgproc.Sobel(roi, gradY, CvType.CV_16S, 0, 1)

                val absGradX = Mat()
                val absGradY = Mat()
                Core.convertScaleAbs(gradX, absGradX)
                Core.convertScaleAbs(gradY, absGradY)

                val gradMat = Mat()
                Core.addWeighted(absGradX, 0.5, absGradY, 0.5, 0.0, gradMat)

                val sum = Core.sumElems(gradMat)
                cornerGradientSum += sum.`val`[0]

                // 释放临时Mat
                gradX.release()
                gradY.release()
                absGradX.release()
                absGradY.release()
                gradMat.release()
            }
        }

        // 综合评分（加权组合）
        val edgeScore = (edgeRatio1 + edgeRatio2) * 0.5
        val diagonalScore = diagonalRatio
        val gradientScore = 1.0 / max(0.001, cornerGradientSum)

        // 最终得分（越低越好）
        return edgeScore * 2.0 + diagonalScore * 1.5 + gradientScore * 100.0
    }

    /**
     * 将四个点按照左上、右上、右下、左下的顺序排列
     */
    private fun orderPoints(points: Array<Point>): Array<Point> {
        if (points.size != 4) return points

        // 结果数组
        val result = Array(4) { Point() }

        // 质心坐标
        val center = Point(
            points.sumOf { it.x } / 4.0,
            points.sumOf { it.y } / 4.0
        )

        // 基于角度排序
        val pointsWithAngles = points.map { point ->
            val angle = Math.atan2(point.y - center.y, point.x - center.x)
            Pair(point, angle)
        }.sortedBy { it.second }

        // 按角度重新排列点
        val sortedPoints = pointsWithAngles.map { it.first }.toTypedArray()

        // 明确识别四个角点
        // 寻找左上角（到原点距离最小）
        var minDist = Double.MAX_VALUE
        var topLeftIdx = 0

        for (i in 0 until 4) {
            val dist = points[i].x * points[i].x + points[i].y * points[i].y
            if (dist < minDist) {
                minDist = dist
                topLeftIdx = i
            }
        }

        // 重新排序为左上、右上、右下、左下
        val topLeft = sortedPoints[topLeftIdx]
        val topRight = sortedPoints[(topLeftIdx + 1) % 4]
        val bottomRight = sortedPoints[(topLeftIdx + 2) % 4]
        val bottomLeft = sortedPoints[(topLeftIdx + 3) % 4]

        // 验证顺序是否正确
        // 如果不是，则使用更可靠的坐标比较法
        if (!(topLeft.x < bottomRight.x && topLeft.y < bottomRight.y &&
                    topRight.x > topLeft.x && bottomLeft.y > topLeft.y)) {

            // 使用坐标和排序
            val sumPoints = points.map { it.x + it.y }
            val diffPoints = points.map { it.x - it.y }

            // 左上角: 和最小
            val topLeftIndex = sumPoints.withIndex().minByOrNull { it.value }?.index ?: 0
            result[0] = points[topLeftIndex]

            // 右下角: 和最大
            val bottomRightIndex = sumPoints.withIndex().maxByOrNull { it.value }?.index ?: 2
            result[2] = points[bottomRightIndex]

            // 右上角: 差最大
            val topRightIndex = diffPoints.withIndex().maxByOrNull { it.value }?.index ?: 1
            result[1] = points[topRightIndex]

            // 左下角: 差最小
            val bottomLeftIndex = diffPoints.withIndex().minByOrNull { it.value }?.index ?: 3
            result[3] = points[bottomLeftIndex]

            return result
        }

        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    /**
     * 创建默认角点（占据图像大部分区域）
     */
    private fun createDefaultCorners(width: Int, height: Int): Array<PointF> {
        val margin = min(width, height) * 0.1f

        return arrayOf(
            PointF(margin, margin),
            PointF(width - margin, margin),
            PointF(width - margin, height - margin),
            PointF(margin, height - margin)
        )
    }

    /**
     * 获取调试图像
     */
    fun getDebugImage(): Bitmap? {
        if (!isDebugMode || debugMat == null) return null

        val debugBitmap = Bitmap.createBitmap(
            debugMat!!.cols(),
            debugMat!!.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(debugMat, debugBitmap)
        return debugBitmap
    }

    /**
     * 调整参数
     */
    @SuppressLint("LongLogTag")
    fun adjustParameters(lowerThreshold: Double, upperThreshold: Double) {
        cannyThreshold1 = lowerThreshold
        cannyThreshold2 = upperThreshold
        Log.d(TAG, "调整边缘检测参数: $cannyThreshold1, $cannyThreshold2")
    }

    /**
     * 释放资源
     */
    fun release() {
        debugMat?.release()
        debugMat = null
    }

    // 扩展函数：Double的平方
    private fun Double.pow(n: Int): Double {
        var result = 1.0
        repeat(n) { result *= this }
        return result
    }
}