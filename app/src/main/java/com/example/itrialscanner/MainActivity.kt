package com.example.itrialscanner

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import android.util.Log

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_SCAN_DOCUMENT = 102
    }

    private lateinit var documentsRecyclerView: RecyclerView
    private lateinit var adapter: DocumentAdapter
    private val documentItems = mutableListOf<DocumentItem>()
    private lateinit var btnCreatePdf: Button
    private lateinit var btnShare: Button
    private lateinit var btnTakePhoto: FloatingActionButton
    private var pdfFile: File? = null
    private lateinit var btnDelete: Button

    // 使用 Activity Result API 替代 requestPermissions
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            checkPermissionsAndStartScanner()
        } else {
            Toast.makeText(this, "需要相机权限才能使用扫描功能", Toast.LENGTH_SHORT).show()
        }
    }

    private var storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            checkPermissionsAndStartScanner()
        } else {
            Toast.makeText(this, "需要存储权限才能保存文档", Toast.LENGTH_SHORT).show()
        }
    }

    // 使用 Activity Result API 替代 startActivityForResult
    private val scanDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra("documentPath")?.let { imagePath ->
                val imageFile = File(imagePath)
                val newItem = DocumentItem(imagePath, imageFile.name)
                documentItems.add(newItem)
                adapter.notifyItemInserted(documentItems.size - 1)
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化视图
        documentsRecyclerView = findViewById(R.id.documentsRecyclerView)
        btnCreatePdf = findViewById(R.id.btnCreatePdf)
        btnShare = findViewById(R.id.btnShare)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)

        btnDelete = findViewById(R.id.btnDelete) // 添加删除按钮初始化

        // 设置 RecyclerView
        adapter = DocumentAdapter(this, documentItems )
        documentsRecyclerView.layoutManager = GridLayoutManager(this, 2)
        documentsRecyclerView.adapter = adapter

        // 设置监听器
        btnTakePhoto.setOnClickListener { checkPermissionsAndStartScanner() }
        btnCreatePdf.setOnClickListener { createPdf() }
        btnShare.setOnClickListener { sharePdf() }
        btnDelete.setOnClickListener { deleteSelectedDocuments() } // 添加删除按钮点击监听器

        // 加载已有文档
        loadExistingDocuments()

        // 监听选择状态变化
        adapter.setOnSelectionChangedListener { selectedCount ->
            btnCreatePdf.isEnabled = (selectedCount.toString().toInt() > 0)
        }
    }

    private fun loadExistingDocuments() {
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (storageDir != null && storageDir.exists()) {
            val files = storageDir.listFiles { _, name -> name.endsWith(".jpg") }
            files?.forEach { file ->
                documentItems.add(DocumentItem(file.absolutePath, file.name))
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun checkPermissionsAndStartScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            startScannerActivity()
        }
    }

    private fun startScannerActivity() {
        try {
            Log.e("startScannerActivity", "启动扫描器。。。")
            val intent = Intent(this, ScannerActivity::class.java)
            scanDocumentLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "启动扫描器失败", e)
            Toast.makeText(this, "启动扫描器失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createPdf() {
        val selectedImagePaths = documentItems.filter { it.isSelected }.map { it.path }
        if (selectedImagePaths.isEmpty()) {
            Toast.makeText(this, "请选择至少一张图片", Toast.LENGTH_SHORT).show()
            return
        }

        // 创建 PDF
        val pdfPath = File(getExternalFilesDir(null), "documents_${System.currentTimeMillis()}.pdf").absolutePath
        Log.d("pdfPath=", pdfPath)
        val success = PdfConverter.createPdf(selectedImagePaths, pdfPath)

        if (success) {
            pdfFile = File(pdfPath)
            btnShare.isEnabled = true
            Toast.makeText(this, "PDF创建成功", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "PDF创建失败", Toast.LENGTH_SHORT).show()
        }
    }

    // 添加删除选中文档的方法
    private fun deleteSelectedDocuments() {
        val selectedItems = documentItems.filter { it.isSelected }

        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "请选择要删除的文档", Toast.LENGTH_SHORT).show()
            return
        }

        // 确认对话框
        AlertDialog.Builder(this)
            .setTitle("删除文档")
            .setMessage("确定要删除选中的 ${selectedItems.size} 个文档吗？")
            .setPositiveButton("删除") { _, _ ->
                // 删除文件
                val filesToDelete = selectedItems.map { File(it.path) }
                var deletedCount = 0

                filesToDelete.forEach { file ->
                    if (file.exists() && file.delete()) {
                        deletedCount++
                    }
                }

                // 更新UI
                val itemsToRemove = ArrayList(selectedItems)
                documentItems.removeAll(itemsToRemove)
                adapter.notifyDataSetChanged()

                Toast.makeText(this, "已删除 $deletedCount 个文档", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun sharePdf() {
        pdfFile?.let { file ->
            if (file.exists()) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    val contentUri = FileProvider.getUriForFile(this@MainActivity, "${packageName}.fileprovider", file)
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    type = "application/pdf"
                }
                startActivity(Intent.createChooser(shareIntent, "分享PDF"))
            } else {
                Toast.makeText(this@MainActivity, "PDF文件不存在", Toast.LENGTH_SHORT).show()
            }
        } ?: Toast.makeText(this, "PDF文件不存在", Toast.LENGTH_SHORT).show()
    }
}
