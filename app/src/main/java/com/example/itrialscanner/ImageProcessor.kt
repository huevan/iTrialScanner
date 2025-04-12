package com.example.itrialscanner

import android.graphics.*
import android.os.Environment
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap

class ImageProcessor {
    companion object {
        fun processImage(imagePath: String, corners: Array<PointF>?): String? {
            return try {
                val originalBitmap = BitmapFactory.decodeFile(imagePath) ?: return null

                val warpedBitmap = perspectiveTransform(originalBitmap, corners)
                val enhancedBitmap = enhanceImage(warpedBitmap)

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val storageDir = File(File(imagePath).parent)
                val outputFile = File(storageDir, "ENHANCED_$timestamp.jpg")

                FileOutputStream(outputFile).use { fos ->
                    enhancedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                }

                if (originalBitmap != warpedBitmap) originalBitmap.recycle()
                warpedBitmap.recycle()
                enhancedBitmap.recycle()

                outputFile.absolutePath
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }

        private fun perspectiveTransform(input: Bitmap, corners: Array<PointF>?): Bitmap {
            if (corners == null || corners.size != 4) return input

            val inputMat = Mat()
            Utils.bitmapToMat(input, inputMat)

            val src = MatOfPoint2f().apply {
                fromArray(
                    Point(corners[0].x.toDouble(), corners[0].y.toDouble()),
                    Point(corners[1].x.toDouble(), corners[1].y.toDouble()),
                    Point(corners[2].x.toDouble(), corners[2].y.toDouble()),
                    Point(corners[3].x.toDouble(), corners[3].y.toDouble())
                )
            }

            var width = maxOf(distance(corners[0], corners[1]), distance(corners[2], corners[3]))
            var height = maxOf(distance(corners[0], corners[3]), distance(corners[1], corners[2]))

            if (width / height > 1.5 || height / width > 1.5) {
                width = input.width.toDouble()
                height = input.height.toDouble()
            }

            val dst = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(width, 0.0),
                Point(width, height),
                Point(0.0, height)
            )

            val perspectiveTransform = Imgproc.getPerspectiveTransform(src, dst)
            val warpedMat = Mat()
            Imgproc.warpPerspective(inputMat, warpedMat, perspectiveTransform, Size(width, height))

            val warpedBitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warpedMat, warpedBitmap)

            inputMat.release()
            warpedMat.release()
            perspectiveTransform.release()

            return warpedBitmap
        }

        private fun distance(p1: PointF, p2: PointF): Double {
            return sqrt((p2.x - p1.x).toDouble().pow(2) + (p2.y - p1.y).toDouble().pow(2))
        }

        private fun enhanceImage(input: Bitmap): Bitmap {
//            val output = Bitmap.createBitmap(input.width, input.height, input.config)
            val output =
                createBitmap(input.width, input.height, input.config ?: Bitmap.Config.ARGB_8888)
            val inputMat = Mat()
            Utils.bitmapToMat(input, inputMat)

            val grayMat = Mat()
            Imgproc.cvtColor(inputMat, grayMat, Imgproc.COLOR_BGR2GRAY)

            val adaptiveThresholdMat = Mat()
            Imgproc.adaptiveThreshold(
                grayMat, adaptiveThresholdMat, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2.0
            )

            val sharpenedMat = Mat()
            val kernel = Mat(3, 3, CvType.CV_32F).apply {
                put(0, 0, 0.0, -1.0, 0.0, -1.0, 5.0, -1.0, 0.0, -1.0, 0.0)
            }
            Imgproc.filter2D(grayMat, sharpenedMat, -1, kernel)

            val resultMat = Mat()
            Core.addWeighted(grayMat, 0.5, sharpenedMat, 0.5, 0.0, resultMat)

            Utils.matToBitmap(resultMat, output)

            inputMat.release()
            grayMat.release()
            adaptiveThresholdMat.release()
            sharpenedMat.release()
            resultMat.release()
            kernel.release()

            return output
        }
    }

    private object Core {
        fun addWeighted(src1: Mat, alpha: Double, src2: Mat, beta: Double, gamma: Double, dst: Mat) {
            if (src1.type() != src2.type() || src1.rows() != src2.rows() || src1.cols() != src2.cols()) {
                throw IllegalArgumentException("Input images must have same type and size")
            }

            for (i in 0 until src1.rows()) {
                for (j in 0 until src1.cols()) {
                    val src1Px = src1.get(i, j)
                    val src2Px = src2.get(i, j)
                    val dstPx = DoubleArray(src1Px.size)

                    for (c in src1Px.indices) {
                        dstPx[c] = alpha * src1Px[c] + beta * src2Px[c] + gamma
                    }

                    dst.put(i, j, *dstPx)
                }
            }
        }
    }
}