package com.example.itrialscanner

import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    // 进度对话框，用于显示处理状态
    private var progressDialog: ProgressDialog? = null

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

    // 将图片保存到公共相册
    private suspend fun saveImageToGallery(sourceFilePath: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourceFilePath)
            val fileName = sourceFile.name
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Scanner")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val contentResolver = contentResolver
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext null

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val inputStream = FileInputStream(sourceFile)
                inputStream.copyTo(outputStream)
                inputStream.close()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }

            return@withContext uri
        } catch (e: Exception) {
            Log.e("ScannerActivity", "保存图片到相册失败", e)
            return@withContext null
        }
    }

    private fun captureImage() {
        if (!::imageCapture.isInitialized) {
            Toast.makeText(this, "相机未初始化", Toast.LENGTH_SHORT).show()
            return
        }

        // 显示进度对话框
        showProgressDialog("正在处理图像...")

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "DOCUMENT_$timestamp.jpg"

        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val outputFile = File(storageDir, fileName)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // 在后台线程处理图像
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            // 1. 处理并增强图像 - 这是最耗时的部分
                            val enhancedImagePath = ImageProcessor.processImage(outputFile.absolutePath, documentCorners)

                            // 2. 如果处理成功，保存到公共相册
                            var publicUri: Uri? = null
                            if (enhancedImagePath != null) {
                                publicUri = saveImageToGallery(enhancedImagePath)
                                Log.d("ScannerActivity", "图像已保存到公共相册: $publicUri")
                            }

                            // 3. 返回主线程更新UI并结束活动
                            withContext(Dispatchers.Main) {
                                hideProgressDialog()

                                if (enhancedImagePath != null) {
                                    val resultIntent = Intent()
                                    resultIntent.putExtra("documentPath", enhancedImagePath)
                                    if (publicUri != null) {
                                        resultIntent.putExtra("publicUri", publicUri.toString())
                                    }
                                    setResult(RESULT_OK, resultIntent)
                                    finish()
                                } else {
                                    Toast.makeText(
                                        this@ScannerActivity,
                                        "图像处理失败",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ScannerActivity", "处理图像时出错", e)
                            withContext(Dispatchers.Main) {
                                hideProgressDialog()
                                Toast.makeText(
                                    this@ScannerActivity,
                                    "处理图像时出错: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    hideProgressDialog()
                    Toast.makeText(
                        this@ScannerActivity,
                        "拍照失败: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    // 显示进度对话框
    private fun showProgressDialog(message: String) {
        hideProgressDialog() // 确保没有已存在的对话框
        progressDialog = ProgressDialog(this).apply {
            setMessage(message)
            setCancelable(false)
            show()
        }
    }

    // 隐藏进度对话框
    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        hideProgressDialog()
    }
}