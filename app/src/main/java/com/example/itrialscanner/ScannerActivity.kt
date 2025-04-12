package com.example.itrialscanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.opencv.android.OpenCVLoader

class ScannerActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "ScannerActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
    }

    private lateinit var previewView: PreviewView
    private lateinit var documentFrame: DocumentFrameView
    private lateinit var btnCapture: Button

    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService? = null

    // 存储文档角点
    private var documentCorners: Array<PointF>? = null

    // 图像分析
    private var imageAnalysis: ImageAnalysis? = null
    private var imageAnalyzerExecutor: ExecutorService? = null

    // 最后一帧的分辨率
    private var lastFrameWidth = 0
    private var lastFrameHeight = 0

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        // 初始化OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "初始化OpenCV失败")
            Toast.makeText(this, "初始化OpenCV失败", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        previewView = findViewById(R.id.previewView)
        documentFrame = findViewById(R.id.documentFrame)
        btnCapture = findViewById(R.id.btnCapture)

        cameraExecutor = Executors.newSingleThreadExecutor()
        imageAnalyzerExecutor = Executors.newSingleThreadExecutor()

        // 检查权限
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)
        }

        // 设置拍照按钮点击事件
        btnCapture.setOnClickListener {
            takePhoto()
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 设置预览
            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // 设置图像捕获
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // 设置图像分析
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // 设置图像分析器
            imageAnalysis?.setAnalyzer(imageAnalyzerExecutor!!) { imageProxy ->
                try {
                    // 获取图像
                    val bitmap = ImageProxyConverter.toBitmap(imageProxy)

                    if (bitmap != null) {
                        // 记录图像分辨率
                        lastFrameWidth = bitmap.width
                        lastFrameHeight = bitmap.height

                        // 检测文档边缘
                        val corners = DocumentDetector.detectDocumentCorners(bitmap)

                        if (corners != null) {
                            // 更新文档角点
                            documentCorners = corners

                            // 转换角点坐标以适应预览视图
                            val displayCorners = convertCornersToViewCoordinates(corners)

                            // 更新UI（必须在主线程）
                            runOnUiThread {
                                documentFrame.setPoints(displayCorners)
                            }
                        } else {
                            // 如果没有检测到边缘，清除显示
                            runOnUiThread {
                                documentFrame.setPoints(null)
                            }
                        }

                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "分析图像失败", e)
                } finally {
                    // 处理完成后关闭ImageProxy
                    imageProxy.close()
                }
            }

            try {
                // 解绑之前的用例
                cameraProvider.unbindAll()

                // 绑定用例到相机
                camera = cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, imageCapture, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "用例绑定失败", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // 将图像坐标转换为视图坐标
    private fun convertCornersToViewCoordinates(corners: Array<PointF>): Array<PointF> {
        if (lastFrameWidth == 0 || lastFrameHeight == 0) return corners

        val viewWidth = previewView.width.toFloat()
        val viewHeight = previewView.height.toFloat()

        // 计算预览视图的比例
        val previewRatio = viewWidth / viewHeight

        // 计算图像的比例
        val imageRatio = lastFrameWidth.toFloat() / lastFrameHeight.toFloat()

        // 根据旋转方向调整宽高
        val rotatedWidth: Float
        val rotatedHeight: Float

        if (imageCapture?.targetRotation == Surface.ROTATION_0 ||
            imageCapture?.targetRotation == Surface.ROTATION_180) {
            rotatedWidth = lastFrameWidth.toFloat()
            rotatedHeight = lastFrameHeight.toFloat()
        } else {
            rotatedWidth = lastFrameHeight.toFloat()
            rotatedHeight = lastFrameWidth.toFloat()
        }

        // 计算缩放比例和偏移量
        var scale: Float
        var xOffset = 0f
        var yOffset = 0f

        if (previewRatio > imageRatio) {
            // 预览比例宽大于高，图像两侧会裁剪
            scale = viewHeight / rotatedHeight
            xOffset = (viewWidth - rotatedWidth * scale) / 2
        } else {
            // 预览比例高大于宽，图像上下会裁剪
            scale = viewWidth / rotatedWidth
            yOffset = (viewHeight - rotatedHeight * scale) / 2
        }

        // 转换每个角点
        return Array(corners.size) { i ->
            val viewX = corners[i].x * scale + xOffset
            val viewY = corners[i].y * scale + yOffset
            PointF(viewX, viewY)
        }
    }

    // 将视图坐标转换为图像坐标
    private fun convertViewCornersToImageCoordinates(viewCorners: Array<PointF>, imageWidth: Int, imageHeight: Int): Array<PointF> {
        val viewWidth = previewView.width.toFloat()
        val viewHeight = previewView.height.toFloat()

        // 计算预览视图的比例
        val previewRatio = viewWidth / viewHeight

        // 计算图像的比例
        val imageRatio = imageWidth.toFloat() / imageHeight.toFloat()

        // 计算缩放比例和偏移量
        var scale: Float
        var xOffset = 0f
        var yOffset = 0f

        if (previewRatio > imageRatio) {
            // 预览比例宽大于高，图像两侧会裁剪
            scale = viewHeight / imageHeight
            xOffset = (viewWidth - imageWidth * scale) / 2
        } else {
            // 预览比例高大于宽，图像上下会裁剪
            scale = viewWidth / imageWidth
            yOffset = (viewHeight - imageHeight * scale) / 2
        }

        // 转换每个角点
        return Array(viewCorners.size) { i ->
            val imageX = (viewCorners[i].x - xOffset) / scale
            val imageY = (viewCorners[i].y - yOffset) / scale

            // 保证坐标在图像范围内
            val clampedX = imageX.coerceIn(0f, imageWidth.toFloat())
            val clampedY = imageY.coerceIn(0f, imageHeight.toFloat())

            PointF(clampedX, clampedY)
        }
    }

    private fun takePhoto() {
        // 检查是否已初始化捕获
        val imageCapture = imageCapture ?: return

        // 创建图像文件
        val photoFile = createImageFile()

        // 创建输出选项
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // 拍照
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processImage(photoFile)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "拍照失败: ${exc.message}", exc)
                    Toast.makeText(baseContext, "拍照失败: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun processImage(photoFile: File) {
        try {
            // 加载原始图像
            val originalBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            if (originalBitmap == null) {
                Log.e(TAG, "无法加载图像")
                return
            }

            // 如果检测到了文档边缘
            if (documentCorners != null) {
                // 将预览坐标转换回图像坐标
                val imageCorners = convertViewCornersToImageCoordinates(documentCorners!!, originalBitmap.width, originalBitmap.height)

                // 裁剪文档
                val warpedBitmap = DocumentDetector.warpDocument(originalBitmap, imageCorners)

                // 保存处理后的图像
                val processedFile = File(photoFile.parent, "processed_${photoFile.name}")
                FileOutputStream(processedFile).use { out ->
                    warpedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                // 回传结果
                val resultIntent = Intent()
                resultIntent.putExtra("documentPath", processedFile.absolutePath)
                setResult(RESULT_OK, resultIntent)

                // 回收资源
                warpedBitmap.recycle()
                originalBitmap.recycle()

                finish()
            } else {
                // 如果没有检测到文档边缘，直接使用原始图像
                val resultIntent = Intent()
                resultIntent.putExtra("documentPath", photoFile.absolutePath)
                setResult(RESULT_OK, resultIntent)

                originalBitmap.recycle()

                finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理图像失败", e)
            Toast.makeText(this, "处理图像失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "需要相机权限", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
        imageAnalyzerExecutor?.shutdown()
    }
}