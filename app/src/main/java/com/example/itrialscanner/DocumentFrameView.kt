package com.example.itrialscanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View

class DocumentFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint: Paint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val path = Path()
    private val corners = Array(4) { PointF(0f, 0f) }

    fun setCorners(newCorners: Array<PointF>?) {
        if (newCorners != null && newCorners.size == 4) {
            for (i in 0..3) {
                corners[i].set(newCorners[i].x, newCorners[i].y)
            }
            updatePath()
            invalidate()
        }
    }

    private fun updatePath() {
        path.reset()
        path.moveTo(corners[0].x, corners[0].y)
        path.lineTo(corners[1].x, corners[1].y)
        path.lineTo(corners[2].x, corners[2].y)
        path.lineTo(corners[3].x, corners[3].y)
        path.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制动态边框
        canvas.drawPath(path, paint)
        // 添加固定参考边框 (红色)
//        val referencePaint = Paint().apply {
//            color = Color.RED
//            style = Paint.Style.STROKE
//            strokeWidth = 8f
//        }
//        val referenceRect = android.graphics.RectF(100f, 100f, width - 100f, height - 100f)
//        canvas.drawRect(referenceRect, referencePaint)
    }
}