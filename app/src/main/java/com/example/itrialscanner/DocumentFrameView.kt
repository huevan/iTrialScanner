package com.example.itrialscanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View

/**
 * 文档边框视图 - 用于在相机预览上显示检测到的文档边框
 */
class DocumentFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 文档角点
    private var corners: Array<PointF>? = null

    // 点的画笔
    private val pointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 线的画笔
    private val linePaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    /**
     * 更新角点并重绘
     */
    fun updateCorners(newCorners: Array<PointF>) {
        corners = newCorners
        invalidate() // 触发重绘
    }

    /**
     * 绘制角点和边框
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val corners = this.corners ?: return

        // 绘制四个角点
        for (point in corners) {
            canvas.drawCircle(point.x, point.y, 15f, pointPaint)
        }

        // 绘制连接线
        val path = Path()
        path.moveTo(corners[0].x, corners[0].y)
        for (i in 1 until corners.size) {
            path.lineTo(corners[i].x, corners[i].y)
        }
        path.close() // 连接回起点

        canvas.drawPath(path, linePaint)
    }

    /**
     * 清除角点
     */
    fun clearCorners() {
        corners = null
        invalidate()
    }
}