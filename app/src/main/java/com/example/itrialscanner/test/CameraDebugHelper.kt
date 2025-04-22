package com.example.itrialscanner.test

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 相机调试和测试工具，用于隔离和解决相机问题
 */
class CameraDebugHelper(private val context: Context) {
    private val TAG = "CameraDebugHelper"
    private var executor: ExecutorService? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null

    init {
        executor = Executors.newSingleThreadExecutor()
    }

    /**
     * 初始化相机，显示预览
     */
    fun initializeCamera(
        owner: LifecycleOwner,
        previewView: PreviewView,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()

                    // 清理先前的绑定
                    cameraProvider?.unbindAll()

                    // 选择后置相机
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()

                    // 创建预览用例
                    preview = Preview.Builder().build()
                    preview?.setSurfaceProvider(previewView.surfaceProvider)

                    // 设置图像捕获用例
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetRotation(previewView.display.rotation)
                        .build()

                    // 绑定用例到相机生命周期
                    cameraProvider?.bindToLifecycle(
                        owner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )

                    runOnMainThread {
                        onSuccess()
                        Log.d(TAG, "相机初始化成功")
                    }
                } catch (e: Exception) {
                    runOnMainThread {
                        onError("相机初始化失败: ${e.message}")
                        Log.e(TAG, "相机初始化失败", e)
                    }
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            runOnMainThread {
                onError("相机Provider获取失败: ${e.message}")
                Log.e(TAG, "相机Provider获取失败", e)
            }
        }
    }

    /**
     * 拍照功能并保存图像
     */
    fun takePhoto(onSuccess: (File) -> Unit, onError: (String) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            runOnMainThread {
                onError("相机未初始化")
                Log.e(TAG, "相机未初始化")
            }
            return
        }

        // 创建新图像文件
        val photoFile = createImageFile()

        // 创建输出选项
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // 拍照
        capture.takePicture(
            outputOptions,
            executor!!,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "图像保存成功: ${photoFile.absolutePath}, 大小: ${photoFile.length()}")

                    if (photoFile.exists() && photoFile.length() > 0) {
                        runOnMainThread {
                            onSuccess(photoFile)
                        }
                    } else {
                        runOnMainThread {
                            onError("图像文件创建失败或为空")
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "图像捕获失败", exception)
                    runOnMainThread {
                        onError("图像捕获失败: ${exception.message}")
                    }
                }
            }
        )
    }

    /**
     * 生成测试图像（不使用相机）
     */
    fun generateTestImage(onSuccess: (File) -> Unit, onError: (String) -> Unit) {
        try {
            // 创建一个纯色位图
            val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)

            // 保存到文件
            val file = createImageFile()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
            }

            bitmap.recycle()

            // 验证文件是否正确创建
            if (file.exists() && file.length() > 0) {
                runOnMainThread {
                    onSuccess(file)
                }
            } else {
                runOnMainThread {
                    onError("测试图像创建失败")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "生成测试图像失败", e)
            runOnMainThread {
                onError("生成测试图像失败: ${e.message}")
            }
        }
    }

    /**
     * 创建图像文件
     */
    fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_DEBUG_"
        val storageDir = context.getExternalFilesDir(null) ?: context.cacheDir

        // 确保目录存在
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        Log.d(TAG, "创建图像文件于: ${storageDir.absolutePath}")

        // 创建临时文件
        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
    }

    /**
     * 获取所有测试图像文件
     */
    fun getAllImageFiles(): List<File> {
        val storageDir = context.getExternalFilesDir(null) ?: context.cacheDir
        return storageDir.listFiles { file ->
            file.isFile && file.name.startsWith("JPEG_") && file.extension == "jpg"
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * 在主线程上运行
     */
    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    /**
     * 关闭和清理资源
     */
    fun close() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        preview = null
        executor?.shutdown()
        executor = null
        imageCapture = null
    }
}