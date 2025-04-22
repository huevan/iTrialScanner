package com.example.itrialscanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import com.example.itrialscanner.detector.ImprovedDocumentDetector
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: Button
    private lateinit var documentFrame: DocumentFrameView

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    // 文档角点
    private var documentCorners: Array<PointF>? = null

    // 标记当前状态
    private var isPreviewMode = true
    private var capturedBitmap: Bitmap? = null


    // 添加: ML Kit Document Scanner 扫描结果处理
    private val scannerLauncher = registerForActivityResult(
        StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)

            // 处理扫描结果
            scanningResult?.let { scanResult ->
                // 获取所有页面
                val pages = scanResult.pages

                if (pages.isNullOrEmpty()) {
                    // 获取第一页图像
                    val imageUri = pages?.get(0)?.imageUri

                    // 在这里可以处理图像，例如显示或保存
                    Toast.makeText(this, "文档扫描成功: ${pages?.size} 页", Toast.LENGTH_SHORT).show()

                    // 你可以在这里添加显示扫描结果的代码
                }

                // 获取PDF(如果请求了)
                scanResult.pdf?.let { pdf ->
                    val pdfUri = pdf.uri
                    // 处理PDF...
                }
            }
        } else {
            // 回到相机预览
            isPreviewMode = true
            previewView.visibility = View.VISIBLE
            btnCapture.text = "拍照"
            Toast.makeText(this, "扫描取消或失败", Toast.LENGTH_SHORT).show()
        }
    }


    companion object {
        private const val TAG = "ScannerActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        // 初始化OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "无法加载OpenCV")
            Toast.makeText(this, "OpenCV加载失败", Toast.LENGTH_SHORT).show()
            finish()
            return
        } else {
            Log.d(TAG, "OpenCV加载成功")
        }

        // 初始化视图
        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnCapture)
        documentFrame = findViewById(R.id.documentFrame)

        // 检查权限
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // 设置拍照按钮
        btnCapture.setOnClickListener {
            if (isPreviewMode) {
                // 拍照
//                takePhoto()
                startDocumentScanner()
            } else {
                // 回到预览模式
                isPreviewMode = true
                previewView.visibility = View.VISIBLE
                btnCapture.text = "拍照"
                capturedBitmap = null
                startCamera() // 重启相机预览
            }
        }

        // 创建相机执行器
        cameraExecutor = Executors.newSingleThreadExecutor()

        // 调整文档检测器参数
        ImprovedDocumentDetector.adjustParameters(25.0, 120.0)

        // 启用调试模式可以查看边缘检测过程
        ImprovedDocumentDetector.isDebugMode = true
    }

    // 添加: 启动ML Kit文档扫描器
    private fun startDocumentScanner() {
        // 配置扫描选项
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)  // 允许从相册导入
            .setPageLimit(5)  // 最多扫描5页
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)  // 完整模式
            .build()

        // 获取扫描客户端
        val scanner = GmsDocumentScanning.getClient(options)

        // 启动扫描
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "无法启动扫描: ${e.message}", Toast.LENGTH_SHORT).show()

                // 如果ML Kit扫描器失败，使用原有的拍照功能作为备选
                takePhoto()
            }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // 绑定相机用例
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 创建预览用例
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // 创建图像捕获用例
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()

            // 创建图像分析用例
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, DocumentAnalyzer { corners ->
                        // 更新文档边框
                        runOnUiThread {
                            documentCorners = corners
                            documentFrame.updateCorners(corners)
                        }
                    })
                }

            // 选择后置相机
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // 解绑之前的所有用例
                cameraProvider.unbindAll()

                // 绑定用例到相机
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "用例绑定失败", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // 获取图像捕获引用
        val imageCapture = imageCapture ?: return

        // 创建临时文件
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // 处理捕获的图像
                    processImage(image)
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "拍照失败: ${exception.message}", exception)
                    Toast.makeText(baseContext, "拍照失败", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun processImage(imageProxy: ImageProxy) {
        // 从预览视图创建Bitmap (简化处理)
        val bitmap = previewView.drawToBitmap()

        // 使用当前检测到的角点
        val corners = documentCorners ?: return

        // 在bitmap上绘制角点和边框
        val resultBitmap = drawDocumentBoundary(bitmap, corners)
        capturedBitmap = resultBitmap

        // 更新UI显示结果
        runOnUiThread {
            isPreviewMode = false
            btnCapture.text = "重新拍照"

            // 在这里你可以处理捕获的图像
            // 例如：保存、显示或发送给其他Activity
            Toast.makeText(this, "文档捕获成功", Toast.LENGTH_SHORT).show()
        }
    }

    // 在图像上绘制文档边界
    private fun drawDocumentBoundary(bitmap: Bitmap, corners: Array<PointF>): Bitmap {
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        // 创建画笔
        val pointPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // 绘制四个角点
        for (point in corners) {
            canvas.drawCircle(point.x, point.y, 15f, pointPaint)
        }

        // 绘制连接线
        val linePaint = Paint().apply {
            color = Color.BLUE
            style = Paint.Style.STROKE
            strokeWidth = 5f
            isAntiAlias = true
        }

        val path = Path()
        path.moveTo(corners[0].x, corners[0].y)
        for (i in 1 until corners.size) {
            path.lineTo(corners[i].x, corners[i].y)
        }
        path.close() // 连接回起点

        canvas.drawPath(path, linePaint)

        return resultBitmap
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "权限未授予", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        ImprovedDocumentDetector.release()
    }
}

/**
 * 文档分析器
 */
private class DocumentAnalyzer(private val cornersListener: (Array<PointF>) -> Unit) : ImageAnalysis.Analyzer {

    private var lastProcessTimestamp = 0L
    private val PROCESS_INTERVAL = 300L // 适当降低到300ms，提高响应速度

    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()

        // 限制处理频率
        if (currentTime - lastProcessTimestamp < PROCESS_INTERVAL) {
            imageProxy.close()
            return
        }

        lastProcessTimestamp = currentTime

        // 创建临时Bitmap
        val bitmap = imageProxyToBitmap(imageProxy)

        // 使用文档检测器
        val corners = ImprovedDocumentDetector.detectDocumentCorners(bitmap)

        // 通知监听器
        cornersListener(corners)

        // 关闭ImageProxy
        imageProxy.close()
    }

    // 从ImageProxy创建Bitmap
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        // 注意: 这是一个简化实现，实际项目中需要考虑旋转等问题
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21, android.graphics.ImageFormat.NV21,
            imageProxy.width, imageProxy.height, null
        )

        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
            100, out
        )
        val imageBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}