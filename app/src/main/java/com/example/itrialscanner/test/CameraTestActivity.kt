package com.example.itrialscanner.test

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.itrialscanner.R
import java.io.File

/**
 * 用于隔离和测试相机功能的活动
 */
class CameraTestActivity : AppCompatActivity() {
    private val TAG = "CameraTestActivity"
    private lateinit var cameraHelper: CameraDebugHelper
    private lateinit var exifHelper: ExifOrientationHelper
    private lateinit var documentDetector: DocumentEdgeDetector
    private lateinit var openCVHelper: OpenCVInitHelper

    // UI控件
    private lateinit var logTextView: TextView
    private lateinit var previewView: PreviewView
    private lateinit var imagePreviewView: ImageView
    private lateinit var imagePreviewContainer: LinearLayout
    private lateinit var cameraControlsContainer: LinearLayout
    private lateinit var fileListRecyclerView: RecyclerView
    private lateinit var fileAdapter: FileListAdapter

    // 当前预览的图片文件
    private var currentPreviewFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_test)

        // 初始化OpenCV
        openCVHelper = OpenCVInitHelper(this)
        if (!openCVHelper.init()) {
            Toast.makeText(this, "OpenCV初始化失败，部分功能可能不可用", Toast.LENGTH_LONG).show()
        }

        // 初始化UI组件
        setupUI()

        // 初始化辅助类
        cameraHelper = CameraDebugHelper(this)
        exifHelper = ExifOrientationHelper()
        documentDetector = DocumentEdgeDetector()

        // 初始化文件列表
        refreshFileList()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun setupUI() {
        // 查找视图
        logTextView = findViewById(R.id.logTextView)
        previewView = findViewById(R.id.cameraPreviewView)
        imagePreviewView = findViewById(R.id.imagePreviewView)
        imagePreviewContainer = findViewById(R.id.imagePreviewContainer)
        cameraControlsContainer = findViewById(R.id.cameraControlsContainer)
        fileListRecyclerView = findViewById(R.id.fileListRecyclerView)

        // 设置文件列表适配器
        fileAdapter = FileListAdapter { file ->
            // 点击文件时显示预览
            showImagePreview(file)
        }

        fileListRecyclerView.layoutManager = LinearLayoutManager(this)
        fileListRecyclerView.adapter = fileAdapter

        // 按钮点击事件
        findViewById<Button>(R.id.btnInitCamera).setOnClickListener {
            initializeCamera()
        }

        findViewById<Button>(R.id.btnTakePhoto).setOnClickListener {
            takePhoto()
        }

        findViewById<Button>(R.id.btnGenerateTestImage).setOnClickListener {
            generateTestImage()
        }

        findViewById<Button>(R.id.btnTestFileIO).setOnClickListener {
            testFileIO()
        }

        // 图片预览控件按钮
        findViewById<Button>(R.id.btnAcceptImage).setOnClickListener {
            acceptImage()
        }

        findViewById<Button>(R.id.btnDiscardImage).setOnClickListener {
            discardImage()
        }

        // 文档边缘检测按钮
        findViewById<Button>(R.id.btnDetectEdges).setOnClickListener {
            detectEdges()
        }

        // 初始隐藏图片预览容器
        imagePreviewContainer.visibility = View.GONE
    }

    private fun detectEdges() {
        currentPreviewFile?.let { file ->
            try {
                val bitmap = exifHelper.loadAndFixOrientation(file)
                if (bitmap != null) {
                    // 使用OpenCV进行文档边缘检测
                    val processedBitmap = documentDetector.detectDocumentEdges(bitmap)

                    // 显示处理后的位图
                    imagePreviewView.setImageBitmap(processedBitmap)
                    appendLog("文档边缘检测完成")
                } else {
                    appendLog("无法加载图片进行边缘检测")
                }
            } catch (e: Exception) {
                appendLog("边缘检测失败: ${e.message}")
                Log.e(TAG, "边缘检测失败", e)
            }
        } ?: run {
            Toast.makeText(this, "没有选中的图片", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeCamera() {
        appendLog("正在初始化相机...")

        // 显示相机预览
        previewView.visibility = View.VISIBLE
        imagePreviewContainer.visibility = View.GONE

        cameraHelper.initializeCamera(
            this,
            previewView,
            onSuccess = {
                appendLog("相机初始化成功")
                Toast.makeText(this, "相机初始化成功", Toast.LENGTH_SHORT).show()

                // 显示相机控制按钮
                cameraControlsContainer.visibility = View.VISIBLE
            },
            onError = { error ->
                appendLog("相机初始化失败: $error")
                Toast.makeText(this, "相机初始化失败: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun takePhoto() {
        appendLog("正在拍照...")

        cameraHelper.takePhoto(
            onSuccess = { file ->
                appendLog("拍照成功: ${file.absolutePath}")
                appendLog("文件大小: ${file.length()} 字节")

                // 显示拍摄的照片预览
                showImagePreview(file)

                // 刷新文件列表
                refreshFileList()
            },
            onError = { error ->
                appendLog("拍照失败: $error")
                Toast.makeText(this, "拍照失败: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun showImagePreview(file: File) {
        try {
            currentPreviewFile = file

            // 使用Exif帮助类加载并修正图片方向
            val correctedBitmap = exifHelper.loadAndFixOrientation(file)

            if (correctedBitmap != null) {
                // 使用OpenCV对图片进行文档边缘检测
                val processedBitmap = documentDetector.detectDocumentEdges(correctedBitmap)

                // 隐藏相机预览，显示图片预览
                previewView.visibility = View.GONE
                imagePreviewView.setImageBitmap(processedBitmap)
                imagePreviewContainer.visibility = View.VISIBLE
                cameraControlsContainer.visibility = View.GONE

                appendLog("显示图片预览并检测文档边缘: ${file.name}")
            } else {
                appendLog("无法加载图片: ${file.absolutePath}")
                Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            appendLog("图片预览失败: ${e.message}")
            Log.e(TAG, "图片预览失败", e)
        }
    }

    private fun acceptImage() {
        // 保存当前图片（实际上已经保存了，这里只是关闭预览）
        Toast.makeText(this, "图片已保存", Toast.LENGTH_SHORT).show()
        imagePreviewContainer.visibility = View.GONE
        refreshFileList()
    }

    private fun discardImage() {
        // 删除当前图片并重新打开相机
        currentPreviewFile?.let { file ->
            if (file.exists()) {
                if (file.delete()) {
                    appendLog("已删除图片: ${file.name}")
                    Toast.makeText(this, "已删除图片", Toast.LENGTH_SHORT).show()
                } else {
                    appendLog("删除图片失败: ${file.name}")
                }
            }

            currentPreviewFile = null
            imagePreviewContainer.visibility = View.GONE
            refreshFileList()

            // 重新初始化相机
            initializeCamera()
        }
    }

    private fun generateTestImage() {
        appendLog("正在生成测试图像...")

        cameraHelper.generateTestImage(
            onSuccess = { file ->
                appendLog("测试图像生成成功: ${file.absolutePath}")
                appendLog("文件大小: ${file.length()} 字节")
                Toast.makeText(this, "测试图像生成成功", Toast.LENGTH_SHORT).show()

                // 刷新文件列表
                refreshFileList()
            },
            onError = { error ->
                appendLog("测试图像生成失败: $error")
                Toast.makeText(this, "测试图像生成失败: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun testFileIO() {
        appendLog("测试文件IO...")

        try {
            // 创建随机名称的文件
            val storageDir = getExternalFilesDir(null) ?: cacheDir
            val fileName = "test_${System.currentTimeMillis()}.txt"
            val file = File(storageDir, fileName)

            // 写入一些文本
            file.writeText("测试数据 ${System.currentTimeMillis()}")

            // 检查文件
            if (file.exists() && file.length() > 0) {
                appendLog("文件IO测试成功: ${file.absolutePath}")
                appendLog("文件内容: ${file.readText()}")

                // 刷新文件列表
                refreshFileList()
            } else {
                appendLog("文件创建成功但无法验证: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            appendLog("文件IO测试失败: ${e.message}")
            Log.e(TAG, "文件IO测试失败", e)
        }
    }

    private fun refreshFileList() {
        // 获取所有图像文件
        val imageFiles = cameraHelper.getAllImageFiles()

        // 更新适配器
        fileAdapter.updateFiles(imageFiles)
    }

    private fun appendLog(message: String) {
        Log.d(TAG, message)
        runOnUiThread {
            // 限制日志区域不要太长
            val currentText = logTextView.text.toString()
            val lines = currentText.split("\n")

            // 保留最后10行
            val newText = if (lines.size > 10) {
                val startIndex = lines.size - 10
                val remainingLines = lines.subList(startIndex, lines.size)
                remainingLines.joinToString("\n")
            } else {
                currentText
            }

            logTextView.text = "$newText\n$message"

            // 自动滚动到底部
            val scrollAmount = logTextView.layout?.getLineTop(logTextView.lineCount) ?: 0
            if (scrollAmount > logTextView.height) {
                logTextView.scrollTo(0, scrollAmount - logTextView.height)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHelper.close()
    }
}