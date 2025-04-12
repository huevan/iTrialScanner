package com.example.itrialscanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View

class DocumentFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 文档的四个角点
    private var points: Array<PointF>? = null

    // 绘制边框的画笔
    private val framePaint = Paint().apply {
        color = Color.BLUE
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // 绘制角点的画笔
    private val cornerPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 4f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 角点圆圈半径
    private val cornerRadius = 20f

    // 更新角点位置
    fun setPoints(newPoints: Array<PointF>?) {
        points = newPoints
        invalidate() // 重绘视图
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val points = this.points ?: return

        // 如果有4个点，绘制完整四边形
        if (points.size == 4) {
            // 绘制边框线
            for (i in points.indices) {
                val next = (i + 1) % points.size
                canvas.drawLine(
                    points[i].x, points[i].y,
                    points[next].x, points[next].y,
                    framePaint
                )
            }

            // 绘制角点圆圈
            for (point in points) {
                canvas.drawCircle(point.x, point.y, cornerRadius, cornerPaint)
            }
        }
    }
}