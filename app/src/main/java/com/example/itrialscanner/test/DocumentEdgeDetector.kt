package com.example.itrialscanner.test

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.ArrayList
import kotlin.math.max
import kotlin.math.min

/**
 * 高级文档边缘检测工具类
 * 专门针对桌面环境中的文档进行了优化
 */
class DocumentEdgeDetector {
    private val TAG = "DocumentEdgeDetector"

    // 边缘圆点标记的半径和颜色
    private val POINT_RADIUS = 20f
    private val POINT_COLOR = Color.RED // 红色点标记顶点

    // 绘制连接线和填充区域的颜色和样式
    private val LINE_COLOR = Color.GREEN // 绿色线连接顶点
    private val LINE_WIDTH = 6f // 线宽
    private val FILL_COLOR = Color.argb(80, 0, 255, 0) // 半透明绿色填充（Alpha=80）

    // 调试模式 - 设置为true可以显示中间处理步骤
    private val DEBUG_MODE = false

    /**
     * 检测图像中的文档边缘 - 增强版算法
     * @param originalBitmap 原始图像
     * @return 带有标记的边缘点的新位图
     */
    fun detectDocumentEdges(originalBitmap: Bitmap): Bitmap {
        try {
            // 创建一个可变的原始位图副本
            val resultBitmap =
                originalBitmap.copy(originalBitmap.config ?: Bitmap.Config.ARGB_8888, true)

            // 将Bitmap转换为OpenCV的Mat
            val originalMat = Mat()
            Utils.bitmapToMat(originalBitmap, originalMat)

            // 保存图像尺寸信息
            val imageWidth = originalMat.width()
            val imageHeight = originalMat.height()

            // ===== 多阶段预处理 =====

            // 1. 尝试提高亮度和对比度以增强文档可见性
            val enhancedMat = Mat()
            originalMat.convertTo(enhancedMat, -1, 1.2, 10.0) // 增加对比度和亮度

            // 2. 转换为灰度图
            val grayMat = Mat()
            Imgproc.cvtColor(enhancedMat, grayMat, Imgproc.COLOR_BGR2GRAY)

            // 3. 应用高斯模糊减少噪声，但保留更多细节
            val blurredMat = Mat()
            Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)

            // 4. 使用Otsu方法进行二值化
            val thresholdMat = Mat()
            Imgproc.threshold(
                blurredMat,
                thresholdMat,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU
            )

            // 5. 形态学操作清理噪点并连接断开的边缘
            val morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            val cleanedMat = Mat()
            Imgproc.morphologyEx(thresholdMat, cleanedMat, Imgproc.MORPH_CLOSE, morphKernel)

            // 6. 应用Canny边缘检测 (调整阈值)
            val edgesMat = Mat()
            Imgproc.Canny(cleanedMat, edgesMat, 50.0, 150.0) // 增加阈值，减少噪声边缘

            // 7. 膨胀边缘，确保连续性
            val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edgesMat, edgesMat, dilateKernel)

            // 输出调试信息
            if (DEBUG_MODE) {
                Log.d(TAG, "原始图像尺寸: ${imageWidth}x${imageHeight}")
                debugSaveMat(edgesMat, "edges")
            }

            // ===== 轮廓检测阶段 =====
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                edgesMat,
                contours,
                hierarchy,
                Imgproc.RETR_LIST, // 改为检测所有轮廓，不仅仅是外部轮廓
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            if (DEBUG_MODE) {
                Log.d(TAG, "找到轮廓数量: ${contours.size}")
            }

            // 根据面积排序轮廓 (从大到小)
            contours.sortByDescending { Imgproc.contourArea(it) }

            // 尝试找到符合文档特征的轮廓
            var documentPoints: Array<Point>? = null
            var bestScore = 0.0

            // 只检查最大的几个轮廓
            val contoursToCheck = min(10, contours.size) // 增加检查的轮廓数量

            val debugMat = if (DEBUG_MODE) {
                val debugMat = Mat.zeros(edgesMat.size(), CvType.CV_8UC3)
                debugMat
            } else {
                null
            }

            for (i in 0 until contoursToCheck) {
                if (i >= contours.size) break

                val contour = contours[i]
                val area = Imgproc.contourArea(contour)

                // 调整最小面积阈值 (降低至图像面积的3%)
                if (area < imageWidth * imageHeight * 0.03) continue

                // 将轮廓近似为多边形
                val contour2f = MatOfPoint2f()
                contour.convertTo(contour2f, CvType.CV_32FC2)

                // 获取轮廓周长
                val perimeter = Imgproc.arcLength(contour2f, true)

                // 使用更广泛的多边形近似精度范围
                val epsilonFactors = mutableListOf<Double>()
                var factor = 0.01
                while (factor <= 0.08) { // 增大上限
                    epsilonFactors.add(factor)
                    factor += 0.005
                }

                for (epsilonFactor in epsilonFactors) {
                    val epsilon = epsilonFactor * perimeter
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(contour2f, approx, epsilon, true)

                    // 寻找近似顶点数为4的轮廓
                    if (approx.total() == 4L) {
                        // 检查是否为凸四边形
                        val approxContour = MatOfPoint()
                        approx.convertTo(approxContour, CvType.CV_32S)

                        if (Imgproc.isContourConvex(approxContour)) {
                            // 计算此轮廓的"文档可能性分数"
                            val points = approx.toArray()
                            val score = calculateDocumentScore(points, imageWidth, imageHeight)

                            if (DEBUG_MODE) {
                                Log.d(
                                    TAG,
                                    "轮廓 #$i: 面积=$area, epsilon=${epsilonFactor}, 分数=$score"
                                )
                                if (debugMat != null) {
                                    Imgproc.drawContours(
                                        debugMat,
                                        listOf(approxContour),
                                        0,
                                        Scalar(0.0, 255.0, 0.0),
                                        2
                                    )
                                }
                            }

                            // 如果分数更高，更新最佳结果
                            if (score > bestScore) {
                                bestScore = score
                                documentPoints = points

                                if (DEBUG_MODE) {
                                    Log.d(TAG, "新最佳轮廓: 分数=$score")
                                }
                            }
                        }
                    }
                }
            }

            // 提前进行简单检测
            var simpleDetectionUsed = false

            // 如果找到了可能的文档
            if (documentPoints != null && bestScore > 0.4) { // 降低阈值，增加检测率
                // 在位图上绘制标记点
                val canvas = Canvas(resultBitmap)

                // 对点进行排序，确保点的顺序一致（左上、右上、右下、左下）
                documentPoints = orderPoints(documentPoints)

                // 创建填充区域的Path
                val fillPath = Path()
                fillPath.moveTo(documentPoints[0].x.toFloat(), documentPoints[0].y.toFloat())
                for (i in 1 until documentPoints.size) {
                    fillPath.lineTo(documentPoints[i].x.toFloat(), documentPoints[i].y.toFloat())
                }
                fillPath.close() // 闭合路径

                // 创建绘制填充区域的画笔
                val fillPaint = Paint()
                fillPaint.color = FILL_COLOR
                fillPaint.style = Paint.Style.FILL
                fillPaint.isAntiAlias = true

                // 绘制填充区域
                canvas.drawPath(fillPath, fillPaint)

                // 创建绘制线条的画笔
                val linePaint = Paint()
                linePaint.color = LINE_COLOR
                linePaint.style = Paint.Style.STROKE
                linePaint.strokeWidth = LINE_WIDTH
                linePaint.isAntiAlias = true

                // 绘制连接线
                canvas.drawPath(fillPath, linePaint)

                // 创建绘制点的画笔
                val pointPaint = Paint()
                pointPaint.color = POINT_COLOR
                pointPaint.style = Paint.Style.FILL
                pointPaint.isAntiAlias = true

                // 画出四个顶点
                for (point in documentPoints) {
                    canvas.drawCircle(
                        point.x.toFloat(),
                        point.y.toFloat(),
                        POINT_RADIUS,
                        pointPaint
                    )
                }

                Log.d(TAG, "文档边缘检测成功: 找到四个顶点，分数=$bestScore")
            } else {
                Log.d(TAG, "未检测到合适的四边形文档，使用简化方法")
                // 使用简化方法估算文档边缘
                resultBitmap.recycle() // 释放之前的位图
                simpleDetectionUsed = true
            }

            // 清理资源
            originalMat.release()
            enhancedMat.release()
            grayMat.release()
            blurredMat.release()
            thresholdMat.release()
            cleanedMat.release()
            edgesMat.release()
            hierarchy.release()
            for (contour in contours) {
                contour.release()
            }

            if (simpleDetectionUsed) {
                return detectDocumentEdgesSimple(originalBitmap)
            }

            return resultBitmap
        } catch (e: Exception) {
            Log.e(TAG, "文档边缘检测失败: ${e.message}", e)
            return detectDocumentEdgesSimple(originalBitmap)
        }
    }

    /**
     * 对四个点进行排序，返回顺序：左上、右上、右下、左下
     */
    private fun orderPoints(points: Array<Point>): Array<Point> {
        if (points.size != 4) return points

        // 按照x+y的和排序，最小的应该是左上角
        val sortedPoints = points.sortedWith(compareBy { it.x + it.y })

        val leftTop = sortedPoints[0]
        val rightBottom = sortedPoints[3]

        // 对剩下的两个点按照y-x的差进行排序
        val middlePoints = sortedPoints.subList(1, 3).sortedWith(compareBy { it.y - it.x })

        val leftBottom = middlePoints[1]
        val rightTop = middlePoints[0]

        return arrayOf(leftTop, rightTop, rightBottom, leftBottom)
    }

    /**
     * 计算四边形的"文档可能性分数"
     * 基于形状、尺寸和位置特征
     */
    private fun calculateDocumentScore(
        points: Array<Point>,
        imageWidth: Int,
        imageHeight: Int
    ): Double {
        if (points.size != 4) return 0.0

        var score = 1.0

        // 1. 矩形度评分 - 越接近矩形越好
        // 使用更灵活的宽高比计算
        val orderedPoints = orderPoints(points)
        val width1 = distance(orderedPoints[0], orderedPoints[1])
        val width2 = distance(orderedPoints[3], orderedPoints[2])
        val height1 = distance(orderedPoints[0], orderedPoints[3])
        val height2 = distance(orderedPoints[1], orderedPoints[2])

        // 计算平均宽高
        val avgWidth = (width1 + width2) / 2
        val avgHeight = (height1 + height2) / 2

        // 计算宽高一致性
        val widthConsistency = 1.0 - min(abs(width1 - width2) / max(width1, width2), 0.3)
        val heightConsistency = 1.0 - min(abs(height1 - height2) / max(height1, height2), 0.3)

        // 计算宽高比
        val aspectRatio = max(avgWidth, avgHeight) / min(avgWidth, avgHeight)

        // 文档通常宽高比在1.0到2.0之间
        val aspectScore = if (aspectRatio >= 1.0 && aspectRatio <= 2.0) {
            1.0
        } else {
            max(0.7, 1.0 - min(abs(aspectRatio - 1.5) * 0.3, 0.5))
        }

        // 更新分数，加权宽高一致性和宽高比
        score *= (aspectScore * 0.6 + widthConsistency * 0.2 + heightConsistency * 0.2)

        // 2. 位置评分 - 文档应该在图像中心附近
        val centerX = points.sumOf { it.x } / 4
        val centerY = points.sumOf { it.y } / 4
        val normalizedCenterX = centerX / imageWidth
        val normalizedCenterY = centerY / imageHeight

        // 中心偏离惩罚，但更宽松
        val centerDistanceFromIdeal = Math.hypot(
            normalizedCenterX - 0.5,
            normalizedCenterY - 0.5
        )
        val positionScore = 1.0 - min(centerDistanceFromIdeal * 1.2, 0.5)
        score *= positionScore

        // 3. 大小评分 - 文档应该占据图像的合理部分
        val area = Imgproc.contourArea(MatOfPoint2f(*points))
        val relativeArea = area / (imageWidth * imageHeight)

        // 文档大小评分，使用更宽松的范围
        val areaScore = if (relativeArea >= 0.1 && relativeArea <= 0.95) {
            1.0
        } else {
            max(0.7, 1.0 - abs(relativeArea - 0.5) * 1.5)
        }
        score *= areaScore

        // 4. 角度评分 - 检查四个角是否接近90度
        val angleScore = calculateAngleScore(points)
        score *= angleScore

        if (DEBUG_MODE) {
            Log.d(
                TAG,
                "文档评分 - 宽高比:$aspectRatio, 宽高比得分:$aspectScore, 位置得分:$positionScore, 面积得分:$areaScore, 角度得分:$angleScore, 总分:$score"
            )
        }

        return score
    }

    /**
     * 计算四边形角度得分 - 检查角度是否接近90度
     */
    private fun calculateAngleScore(points: Array<Point>): Double {
        if (points.size != 4) return 0.0

        // 对点进行排序
        val orderedPoints = orderPoints(points)

        // 计算四个角的角度
        val angles = mutableListOf<Double>()

        for (i in 0 until 4) {
            val p1 = orderedPoints[i]
            val p2 = orderedPoints[(i + 1) % 4]
            val p3 = orderedPoints[(i + 2) % 4]

            val angle = calculateAngle(p1, p2, p3)
            angles.add(angle)
        }

        // 计算角度偏离90度的平均值
        var angleDeviationSum = 0.0
        for (angle in angles) {
            angleDeviationSum += abs(angle - 90.0)
        }

        val avgDeviation = angleDeviationSum / 4.0

        // 转换为0-1分数，偏差越小分数越高
        return max(0.7, 1.0 - min(avgDeviation / 30.0, 0.5))
    }

    /**
     * 计算三点形成的角度
     * p2是角的顶点
     */
    private fun calculateAngle(p1: Point, p2: Point, p3: Point): Double {
        val v1x = p1.x - p2.x
        val v1y = p1.y - p2.y
        val v2x = p3.x - p2.x
        val v2y = p3.y - p2.y

        // 计算两个向量的点积
        val dotProduct = v1x * v2x + v1y * v2y

        // 计算两个向量的模
        val v1Mag = Math.sqrt(v1x * v1x + v1y * v1y)
        val v2Mag = Math.sqrt(v2x * v2x + v2y * v2y)

        // 计算夹角（弧度）
        val cosTheta = dotProduct / (v1Mag * v2Mag)

        // 转换为角度
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(cosTheta, 1.0))))
    }

    private fun distance(p1: Point, p2: Point): Double {
        return Math.hypot(p1.x - p2.x, p1.y - p2.y)
    }

    private fun abs(value: Double): Double {
        return if (value < 0) -value else value
    }

    private fun debugSaveMat(mat: Mat, name: String) {
        if (!DEBUG_MODE) return

        try {
            Log.d(TAG, "保存调试图像: $name")
            // 可以在这里添加代码将Mat保存为图像文件
        } catch (e: Exception) {
            Log.e(TAG, "保存调试图像失败: ${e.message}", e)
        }
    }

    /**
     * 使用简化逻辑检测文档边缘（当高级检测失败时的备选方法）
     * 改进的简化方法，更加智能地查找文档
     */
    fun detectDocumentEdgesSimple(originalBitmap: Bitmap): Bitmap {
        try {
            // 创建一个可变的位图副本
            val resultBitmap =
                originalBitmap.copy(originalBitmap.config ?: Bitmap.Config.ARGB_8888, true)

            // 获取图像尺寸
            val width = originalBitmap.width
            val height = originalBitmap.height

            // 将Bitmap转换为OpenCV的Mat
            val mat = Mat()
            Utils.bitmapToMat(originalBitmap, mat)

            // 增强对比度
            val enhancedMat = Mat()
            mat.convertTo(enhancedMat, -1, 1.5, 20.0)

            // 转换为灰度
            val grayMat = Mat()
            Imgproc.cvtColor(enhancedMat, grayMat, Imgproc.COLOR_BGR2GRAY)

            // 模糊处理
            val blurredMat = Mat()
            Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)

            // 阈值处理 - 尝试Otsu方法
            val binaryMat = Mat()
            Imgproc.threshold(
                blurredMat,
                binaryMat,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU
            )

            // 进行形态学操作
            val morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
            val cleanedMat = Mat()
            Imgproc.morphologyEx(binaryMat, cleanedMat, Imgproc.MORPH_CLOSE, morphKernel)

            // 查找轮廓
            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(
                cleanedMat,
                contours,
                Mat(),
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            // 按面积排序轮廓
            contours.sortByDescending { Imgproc.contourArea(it) }

            // 创建绘图对象
            val canvas = Canvas(resultBitmap)

            // 创建各种画笔
            val pointPaint = Paint().apply {
                color = POINT_COLOR
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = LINE_COLOR
                style = Paint.Style.STROKE
                strokeWidth = LINE_WIDTH
                isAntiAlias = true
            }

            val fillPaint = Paint().apply {
                color = FILL_COLOR
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            // 检查是否有足够大的轮廓
            if (contours.size > 0 && Imgproc.contourArea(contours[0]) > width * height * 0.05) {
                // 将轮廓近似为多边形
                val contour2f = MatOfPoint2f()
                contours[0].convertTo(contour2f, CvType.CV_32FC2)

                val perimeter = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()

                // 尝试不同的epsilon值
                var bestApprox: MatOfPoint2f? = null
                var bestPointCount = 0

                for (epsilonFactor in listOf(0.02, 0.03, 0.05, 0.08)) {
                    val epsilon = epsilonFactor * perimeter
                    Imgproc.approxPolyDP(contour2f, approx, epsilon, true)

                    val pointCount = approx.total().toInt()

                    // 如果找到四边形，立即使用
                    if (pointCount == 4) {
                        val newApprox = MatOfPoint2f()
                        approx.copyTo(newApprox)
                        bestApprox = newApprox
                        break
                    }

                    // 否则，保存点数最接近4的近似值
                    if (bestPointCount == 0 || Math.abs(pointCount - 4) < Math.abs(bestPointCount - 4)) {
                        bestPointCount = pointCount
                        val newApprox = MatOfPoint2f()
                        approx.copyTo(newApprox)
                        bestApprox = newApprox
                    }
                }

                if (bestApprox != null) {
                    val points = bestApprox.toArray()
                    var orderedPoints: Array<Point>

                    if (points.size == 4) {
                        // 直接使用四个点
                        orderedPoints = orderPoints(points)
                        drawDocumentOutline(canvas, orderedPoints, pointPaint, linePaint, fillPaint)
                        Log.d(TAG, "简化方法: 成功找到四边形轮廓")
                    } else if (points.size > 4) {
                        // 如果点太多，尝试找出四个最远的角点
                        orderedPoints = findCornerPoints(points)
                        drawDocumentOutline(canvas, orderedPoints, pointPaint, linePaint, fillPaint)
                        Log.d(TAG, "简化方法: 从${points.size}个点中选择了4个角点")
                    } else {
                        // 如果点太少，使用边界矩形
                        drawBoundingRectPoints(
                            contours[0],
                            canvas,
                            pointPaint,
                            linePaint,
                            fillPaint
                        )
                    }
                } else {
                    drawBoundingRectPoints(contours[0], canvas, pointPaint, linePaint, fillPaint)
                }
            } else {
                // 如果没有找到有效轮廓，使用固定比例估算
                drawFixedRatioPoints(width, height, canvas, pointPaint, linePaint, fillPaint)
            }

            // 清理资源
            mat.release()
            enhancedMat.release()
            grayMat.release()
            blurredMat.release()
            binaryMat.release()
            cleanedMat.release()
            for (contour in contours) {
                contour.release()
            }

            return resultBitmap
        } catch (e: Exception) {
            Log.e(TAG, "简化文档边缘检测失败", e)

            try {
                // 最后的备选方案 - 最简单的固定边缘估算
                val resultBitmap =
                    originalBitmap.copy(originalBitmap.config ?: Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(resultBitmap)

                val pointPaint = Paint().apply {
                    color = POINT_COLOR
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                val linePaint = Paint().apply {
                    color = LINE_COLOR
                    style = Paint.Style.STROKE
                    strokeWidth = LINE_WIDTH
                    isAntiAlias = true
                }

                val fillPaint = Paint().apply {
                    color = FILL_COLOR
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                val width = originalBitmap.width
                val height = originalBitmap.height

                drawFixedRatioPoints(width, height, canvas, pointPaint, linePaint, fillPaint)

                return resultBitmap
            } catch (e2: Exception) {
                Log.e(TAG, "备选方案也失败", e2)
                return originalBitmap
            }
        }
    }

    /**
     * 绘制文档轮廓，包括填充区域、边框线和角点
     */
    private fun drawDocumentOutline(
        canvas: Canvas,
        points: Array<Point>,
        pointPaint: Paint,
        linePaint: Paint,
        fillPaint: Paint
    ) {
        // 创建填充路径
        val path = Path()
        path.moveTo(points[0].x.toFloat(), points[0].y.toFloat())
        for (i in 1 until points.size) {
            path.lineTo(points[i].x.toFloat(), points[i].y.toFloat())
        }
        path.close()

        // 先绘制填充区域
        canvas.drawPath(path, fillPaint)

        // 然后绘制边框线
        canvas.drawPath(path, linePaint)

        // 最后绘制角点
        for (point in points) {
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), POINT_RADIUS, pointPaint)
        }
    }

    /**
     * 从一组点中找出四个角点
     */
    private fun findCornerPoints(points: Array<Point>): Array<Point> {
        if (points.size <= 4) return points

        // 计算点云的中心
        val centerX = points.sumOf { it.x } / points.size
        val centerY = points.sumOf { it.y } / points.size
        val center = Point(centerX, centerY)

        // 按照到中心的距离排序
        val sortedPoints = points.sortedByDescending {
            Math.hypot(it.x - center.x, it.y - center.y)
        }

        // 取前4个点作为角点
        val cornerCandidates = sortedPoints.take(4).toTypedArray()

        // 对这4个点排序
        return orderPoints(cornerCandidates)
    }

    /**
     * 使用轮廓的边界矩形绘制四个角点并填充区域
     */
    private fun drawBoundingRectPoints(
        contour: MatOfPoint,
        canvas: Canvas,
        pointPaint: Paint,
        linePaint: Paint,
        fillPaint: Paint
    ) {
        val rect = Imgproc.boundingRect(contour)

        val topLeft = Point(rect.x.toDouble(), rect.y.toDouble())
        val topRight = Point((rect.x + rect.width).toDouble(), rect.y.toDouble())
        val bottomRight = Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble())
        val bottomLeft = Point(rect.x.toDouble(), (rect.y + rect.height).toDouble())

        val points = arrayOf(topLeft, topRight, bottomRight, bottomLeft)

        // 创建填充路径
        val path = Path()
        path.moveTo(topLeft.x.toFloat(), topLeft.y.toFloat())
        path.lineTo(topRight.x.toFloat(), topRight.y.toFloat())
        path.lineTo(bottomRight.x.toFloat(), bottomRight.y.toFloat())
        path.lineTo(bottomLeft.x.toFloat(), bottomLeft.y.toFloat())
        path.close()

        // 先绘制填充区域
        canvas.drawPath(path, fillPaint)

        // 然后绘制边框线
        canvas.drawPath(path, linePaint)

        // 最后绘制角点
        for (point in points) {
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), POINT_RADIUS, pointPaint)
        }

        Log.d(TAG, "简化方法: 使用边界矩形")
    }

    /**
     * 使用固定比例估算四个角点并填充区域
     */
    private fun drawFixedRatioPoints(
        width: Int,
        height: Int,
        canvas: Canvas,
        pointPaint: Paint,
        linePaint: Paint,
        fillPaint: Paint
    ) {
        val marginX = width * 0.15 // 减小边缘
        val marginY = height * 0.15

        val topLeft = Point(marginX, marginY)
        val topRight = Point(width - marginX, marginY)
        val bottomRight = Point(width - marginX, height - marginY)
        val bottomLeft = Point(marginX, height - marginY)

        // 创建填充路径
        val path = Path()
        path.moveTo(topLeft.x.toFloat(), topLeft.y.toFloat())
        path.lineTo(topRight.x.toFloat(), topRight.y.toFloat())
        path.lineTo(bottomRight.x.toFloat(), bottomRight.y.toFloat())
        path.lineTo(bottomLeft.x.toFloat(), bottomLeft.y.toFloat())
        path.close()

        // 先绘制填充区域
        canvas.drawPath(path, fillPaint)

        // 然后绘制边框线
        canvas.drawPath(path, linePaint)

        // 最后绘制角点
        canvas.drawCircle(topLeft.x.toFloat(), topLeft.y.toFloat(), POINT_RADIUS, pointPaint)
        canvas.drawCircle(topRight.x.toFloat(), topRight.y.toFloat(), POINT_RADIUS, pointPaint)
        canvas.drawCircle(
            bottomRight.x.toFloat(),
            bottomRight.y.toFloat(),
            POINT_RADIUS,
            pointPaint
        )
        canvas.drawCircle(bottomLeft.x.toFloat(), bottomLeft.y.toFloat(), POINT_RADIUS, pointPaint)

        Log.d(TAG, "简化方法: 使用固定比例")
    }
}