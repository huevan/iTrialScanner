package com.example.itrialscanner

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Size
import android.widget.Button
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.util.Log
import androidx.camera.core.AspectRatio

class ScannerActivity : AppCompatActivity() {

    companion object {
        init {
            OpenCVLoader.initDebug()
        }
    }

    private lateinit var previewView: PreviewView
    private lateinit var documentFrame: DocumentFrameView
    private lateinit var btnCapture: Button

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture
    private val documentCorners = Array(4) { PointF() }

    private fun convertToViewCoordinates(point: PointF, imageWidth: Int, imageHeight: Int): PointF {
        val previewWidth = previewView.width.toFloat()
        val previewHeight = previewView.height.toFloat()

        // 根据预览视图的缩放模式进行调整
        val scaleX = previewWidth / imageWidth
        val scaleY = previewHeight / imageHeight

        return PointF(point.x * scaleX, point.y * scaleY)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        if (OpenCVLoader.initDebug()) {
            Log.d("ScannerActivity", "OpenCV 初始化成功")
        } else {
            Log.e("ScannerActivity", "OpenCV 初始化失败")
            Toast.makeText(this, "OpenCV 初始化失败", Toast.LENGTH_SHORT).show()
            finish() // 失败时安全退出
        }

        previewView = findViewById(R.id.previewView)
        documentFrame = findViewById(R.id.documentFrame)
        btnCapture = findViewById(R.id.btnCapture)

        btnCapture.setOnClickListener { captureImage() }

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }

    override fun onResume() {
        super.onResume()
        startCamera()
    }

    override fun onPause() {
        super.onPause()
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            Log.e("ScannerActivity", "相机解绑失败", e)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                // 创建预览用例
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                // 图像捕获用例
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()


                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3) // 更通用的设置
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor, DocumentEdgeAnalyzer())
//                imageAnalysis.setAnalyzer(cameraExecutor, SimpleDocumentAnalyzer())

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                // 先解绑所有用例
                cameraProvider.unbindAll()

                // 将用例绑定到摄像头
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
                // 添加日志确认相机已启动
                Log.d("ScannerActivity", "相机已启动")

            } catch (e: ExecutionException) {
                Toast.makeText(this, "启动相机失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: InterruptedException) {
                Toast.makeText(this, "启动相机失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class DocumentEdgeAnalyzer : ImageAnalysis.Analyzer {
        private var noEdgeFrameCount = 0

        @Override
        override fun analyze(image: ImageProxy) {
            try {
                // 在后台线程中处理图像数据
                val bitmap = image.toBitmap() // 使用 ImageProxy 转换为 bitmap

                if (bitmap != null) {
                    // 图像处理代码...
                    val mat = Mat()
                    Utils.bitmapToMat(bitmap, mat)

                    // 使用预处理方法来增强图像
                    val processedMat = preprocess(mat)

                    val edges = Mat()
                    Imgproc.Canny(processedMat, edges, 50.0, 150.0) // 降低Canny阈值

                    val contours = ArrayList<MatOfPoint>()
                    val hierarchy = Mat()
                    Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

                    Log.d("DocumentAnalyzer", "检测到轮廓数: ${contours.size}")

                    val documentContour = findLargestContour(contours)
                    if (documentContour != null) {
                        Log.d("DocumentAnalyzer", "找到文档轮廓")
                        val contour2f = MatOfPoint2f(*documentContour.toArray())
                        val epsilon = 0.02 * Imgproc.arcLength(contour2f, true)
                        val approxCurve = MatOfPoint2f()
                        Imgproc.approxPolyDP(contour2f, approxCurve, epsilon, true)

                        if (approxCurve.total() == 4L) {
                            val points = approxCurve.toArray()

                            // 转换为PointF数组，便于进行坐标转换
                            val tempCorners = Array(4) { PointF() }
                            for (i in 0 until 4) {
                                tempCorners[i] = PointF(points[i].x.toFloat(), points[i].y.toFloat())
                            }

                            // 转换坐标到视图坐标系
                            val convertedCorners = Array(4) { PointF() }
                            for (i in 0 until 4) {
                                convertedCorners[i] = convertToViewCoordinates(tempCorners[i], image.width, image.height)
                            }

                            Log.d("DocumentAnalyzer", "转换后坐标: [${convertedCorners.joinToString { "(${it.x}, ${it.y})" }}]")

                            runOnUiThread {
                                for (i in 0 until 4) {
                                    documentCorners[i].set(convertedCorners[i].x, convertedCorners[i].y)
                                }
                                documentFrame.setCorners(documentCorners)
                                noEdgeFrameCount = 0 // 重置计数器
                                Log.d("DocumentAnalyzer", "UI已更新")
                            }
                        } else {
                            Log.d("DocumentAnalyzer", "找到的轮廓不是四边形: ${approxCurve.total()} 个顶点")
                            handleNoEdgeDetected(image)
                        }
                    } else {
                        Log.d("DocumentAnalyzer", "未找到合适的文档轮廓")
                        handleNoEdgeDetected(image)
                    }

                    // 释放资源
                    mat.release()
                    processedMat.release()
                    edges.release()
                    hierarchy.release()
                }
            } catch (e: Exception) {
                Log.e("DocumentAnalyzer", "分析过程出错: ${e.message}", e)
            } finally {
                image.close()
            }
        }

        // 处理未检测到边缘的情况
        private fun handleNoEdgeDetected(image: ImageProxy) {
            noEdgeFrameCount++
            if (noEdgeFrameCount > 30) { // 连续30帧未找到边缘
                showTemporaryBorder(image.width, image.height)
                noEdgeFrameCount = 0
            }
        }

        // 辅助方法：将 ImageProxy 转换为 Bitmap
        private fun ImageProxy.toBitmap(): Bitmap? {
            try {
                val yBuffer = planes[0].buffer
                val uBuffer = planes[1].buffer
                val vBuffer = planes[2].buffer

                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val nv21 = ByteArray(ySize + uSize + vSize)

                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, width, height, null)
                val out = java.io.ByteArrayOutputStream()
                yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
                val imageBytes = out.toByteArray()

                return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } catch (e: Exception) {
                Log.e("ImageConversion", "转换图像失败: ${e.message}", e)
                return null
            }
        }

        // 预处理图像以增强边缘检测
        private fun preprocess(mat: Mat): Mat {
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY)

            // 使用高斯模糊减少噪点
            Imgproc.GaussianBlur(gray, gray, org.opencv.core.Size(5.0, 5.0), 0.0)

            // 使用自适应阈值处理增强边缘
            val binary = Mat()
            Imgproc.adaptiveThreshold(
                gray, binary, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, 11, 2.0
            )

            // 形态学操作闭合边缘
            val kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                org.opencv.core.Size(5.0, 5.0)
            )
            val processed = Mat()
            Imgproc.morphologyEx(binary, processed, Imgproc.MORPH_CLOSE, kernel)

            gray.release()
            binary.release()

            return processed
        }

        // 显示临时边框
        private fun showTemporaryBorder(width: Int, height: Int) {
            val tempCorners = Array(4) { PointF() }
            tempCorners[0] = PointF(width * 0.2f, height * 0.2f) // 左上
            tempCorners[1] = PointF(width * 0.8f, height * 0.2f) // 右上
            tempCorners[2] = PointF(width * 0.8f, height * 0.8f) // 右下
            tempCorners[3] = PointF(width * 0.2f, height * 0.8f) // 左下

            val convertedCorners = Array(4) { PointF() }
            for (i in 0 until 4) {
                convertedCorners[i] = convertToViewCoordinates(tempCorners[i], width, height)
            }

            runOnUiThread {
                for (i in 0 until 4) {
                    documentCorners[i].set(convertedCorners[i].x, convertedCorners[i].y)
                }
                documentFrame.setCorners(documentCorners)
                Log.d("DocumentAnalyzer", "显示临时边框")
            }
        }

        // 坐标转换方法
        private fun convertToViewCoordinates(point: PointF, imageWidth: Int, imageHeight: Int): PointF {
            val previewWidth = previewView.width.toFloat()
            val previewHeight = previewView.height.toFloat()

            // 根据预览视图的缩放模式进行调整
            val scaleX = previewWidth / imageWidth
            val scaleY = previewHeight / imageHeight

            return PointF(point.x * scaleX, point.y * scaleY)
        }

        // 修改findLargestContour方法，放宽条件
        private fun findLargestContour(contours: List<MatOfPoint>): MatOfPoint? {
            var maxArea = 0.0
            var largestContour: MatOfPoint? = null

            // 记录找到的四边形数量
            var quadCount = 0

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                // 降低面积阈值
                if (area > 1000.0) { // 原来是 5000.0
                    val contour2f = MatOfPoint2f(*contour.toArray())
                    val perimeter = Imgproc.arcLength(contour2f, true)
                    val approxCurve = MatOfPoint2f()
                    // 放宽近似精度
                    Imgproc.approxPolyDP(contour2f, approxCurve, 0.05 * perimeter, true) // 原来是 0.02

                    if (approxCurve.total() == 4L) {
                        quadCount++
                        if (area > maxArea) {
                            maxArea = area
                            largestContour = contour
                        }
                    }
                }
            }

            // 添加日志
            Log.d("DocumentAnalyzer", "找到 $quadCount 个四边形轮廓，最大面积: $maxArea")

            return largestContour
        }
    }


    private inner class SimpleDocumentAnalyzer : ImageAnalysis.Analyzer {
        private var frameCounter = 0

        override fun analyze(image: ImageProxy) {
            frameCounter++
            if (frameCounter % 30 == 0) { // 每30帧更新一次
                val width = image.width.toFloat()
                val height = image.height.toFloat()

                // 创建一个简单的矩形，模拟文档边缘
                val simpleCorners = Array(4) { PointF() }
                simpleCorners[0] = PointF(width * 0.2f, height * 0.2f) // 左上
                simpleCorners[1] = PointF(width * 0.8f, height * 0.2f) // 右上
                simpleCorners[2] = PointF(width * 0.8f, height * 0.8f) // 右下
                simpleCorners[3] = PointF(width * 0.2f, height * 0.8f) // 左下

                Log.d("SimpleAnalyzer", "创建简单边框: 帧=$frameCounter, 尺寸=${width}x${height}")
                val convertedCorners = Array(4) { PointF() }
                for (i in 0 until 4) {
                    convertedCorners[i] = convertToViewCoordinates(simpleCorners[i], image.width, image.height)
                }


                runOnUiThread {
                    for (i in 0 until 4) {
                        documentCorners[i].set(simpleCorners[i].x, simpleCorners[i].y)
                    }
                    documentFrame.setCorners(documentCorners)
                    Log.d("SimpleAnalyzer", "边框已更新到UI")
                }
            }
            image.close()
        }
    }


    private fun captureImage() {
        if (!::imageCapture.isInitialized) {
            Toast.makeText(this, "相机未初始化", Toast.LENGTH_SHORT).show()
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "DOCUMENT_$timestamp.jpg"

        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val outputFile = File(storageDir, fileName)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved( outputFileResults: ImageCapture.OutputFileResults) {
                    processAndEnhanceImage(outputFile.absolutePath)
                }

                override fun onError( exception: ImageCaptureException) {
                    Toast.makeText(this@ScannerActivity, "拍照失败: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun processAndEnhanceImage(imagePath: String) {
        val enhancedImagePath = ImageProcessor.processImage(imagePath, documentCorners)
        if (enhancedImagePath != null) {
            val resultIntent = Intent()
            resultIntent.putExtra("documentPath", enhancedImagePath)
            setResult(RESULT_OK, resultIntent)
            finish()
        } else {
            Toast.makeText(this, "图像处理失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}