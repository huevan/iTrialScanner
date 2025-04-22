package com.example.itrialscanner

import PdfConverter
import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PersistableBundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.viewpager2.widget.ViewPager2
import com.example.itrialscanner.test.CameraTestActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var toolbar: Toolbar
    private lateinit var viewPager: ViewPager2
    override fun onCreate(
        savedInstanceState: Bundle?,
        persistentState: PersistableBundle?
    ) {
        super.onCreate(savedInstanceState, persistentState)
    }

    private lateinit var tabLayout: TabLayout
    private lateinit var btnCreatePdf: Button
    private lateinit var btnShare: Button
    private lateinit var btnTakePhoto: FloatingActionButton

    private lateinit var photosFragment: PhotosFragment
    private lateinit var pdfsFragment: PdfsFragment

    private var pdfUri: Uri? = null
    private var currentPdfPath: String? = null
    private var deleteMenuItem: MenuItem? = null

    // 使用 Activity Result API 替代 requestPermissions
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            checkPermissionsAndStartScanner()
        } else {
            showToast("需要相机权限才能使用扫描功能")
        }
    }


    // 使用 Activity Result API 替代 startActivityForResult
    private val scanDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d(TAG, "收到扫描结果: ${result.resultCode}")

        if (result.resultCode == RESULT_OK) {
            val documentPath = result.data?.getStringExtra("documentPath")
            Log.d(TAG, "收到文档路径: $documentPath")

            if (documentPath != null && File(documentPath).exists()) {
                // 确认文件存在后再刷新照片列表
                Log.d(TAG, "文件存在，刷新照片列表并切换到照片Tab")
                photosFragment.loadPhotos()
                viewPager.currentItem = 0
            } else {
                Log.e(TAG, "未收到有效的文档路径或文件不存在: $documentPath")
                showToast("未能获取拍摄的照片")
            }
        } else {
            Log.d(TAG, "扫描被取消或失败")
        }
    }
    private val EAST_MODEL_PATH = "frozen_east_text_detection.pb"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initOpenCV()
        initViews()
        setupViewPager()
        setupListeners()

        setupDebugOptions()

//        try {
//            val assetsList = assets.list("")
//            Log.d(TAG, "Assets列表: ${assetsList?.joinToString()}")
//            val modelExists = assetsList?.contains(EAST_MODEL_PATH) ?: false
//            Log.d(TAG, "模型文件在assets中存在: $modelExists")
//        } catch (e: Exception) {
//            Log.e(TAG, "列出assets目录内容失败", e)
//        }
    }

    private fun initOpenCV() {
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "OpenCV初始化失败")
        } else {
            Log.d("OpenCV", "OpenCV初始化成功")
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        btnCreatePdf = findViewById(R.id.btnCreatePdf)
        btnShare = findViewById(R.id.btnShare)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
    }

    private fun setupViewPager() {
        // 创建Fragment
        photosFragment = PhotosFragment()
        pdfsFragment = PdfsFragment()

        // 设置Fragment列表和标题
        val fragments = listOf(photosFragment, pdfsFragment)
        val titles = listOf("照片", "PDF文件")


        // 设置ViewPager适配器
        val pagerAdapter: androidx.viewpager2.adapter.FragmentStateAdapter = ViewPagerAdapter(this, fragments, titles)
        viewPager.adapter = pagerAdapter


        // 连接TabLayout和ViewPager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = titles[position]
        }.attach()

        // 设置Fragment回调
        photosFragment.onSelectionChangedListener = { selectedCount ->
            btnCreatePdf.isEnabled = selectedCount > 0
            updateDeleteButtonState()
        }

        pdfsFragment.onSelectionChangedListener = { selectedCount ->
            btnShare.isEnabled = selectedCount > 0
            updateDeleteButtonState()
        }

        pdfsFragment.onPdfSelectedListener = { pdfItem ->
            currentPdfPath = pdfItem.path
            pdfUri = pdfItem.uri?.toUri()
            btnShare.isEnabled = true
        }

        // 页面切换监听
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateBottomButtonsState(position)
            }
        })
    }

    private fun updateBottomButtonsState(position: Int) {
        when (position) {
            0 -> { // 照片页
                btnCreatePdf.isEnabled = photosFragment.getSelectedItems().isNotEmpty()
                btnShare.isEnabled = false
            }
            1 -> { // PDF页
                btnCreatePdf.isEnabled = false
                btnShare.isEnabled = pdfsFragment.getSelectedItems().isNotEmpty() || currentPdfPath != null
            }
        }
    }

    private fun setupListeners() {
        btnTakePhoto.setOnClickListener { checkPermissionsAndStartScanner() }
        btnCreatePdf.setOnClickListener { createPdf() }
        btnShare.setOnClickListener { sharePdf() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        deleteMenuItem = menu.findItem(R.id.action_delete)
        updateDeleteButtonState()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                deleteSelectedItems()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun setDeleteButtonEnabled(enabled: Boolean) {
        deleteMenuItem?.isEnabled = enabled
        deleteMenuItem?.icon?.alpha = if (enabled) 255 else 130
    }

    private fun updateDeleteButtonState() {
        val hasSelection = when (viewPager.currentItem) {
            0 -> photosFragment.getSelectedItems().isNotEmpty()
            1 -> pdfsFragment.getSelectedItems().isNotEmpty()
            else -> false
        }
        setDeleteButtonEnabled(hasSelection)
    }

    private fun deleteSelectedItems() {
        when (viewPager.currentItem) {
            0 -> {
                val selectedCount = photosFragment.getSelectedItems().size
                if (selectedCount > 0) {
                    AlertDialog.Builder(this)
                        .setTitle("删除照片")
                        .setMessage("确定要删除选中的 $selectedCount 张照片吗？")
                        .setPositiveButton("删除") { _, _ ->
                            val deletedCount = photosFragment.deleteSelectedItems()
                            showToast("已删除 $deletedCount 张照片")
                            updateDeleteButtonState()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            1 -> {
                val selectedCount = pdfsFragment.getSelectedItems().size
                if (selectedCount > 0) {
                    AlertDialog.Builder(this)
                        .setTitle("删除PDF")
                        .setMessage("确定要删除选中的 $selectedCount 个PDF文件吗？")
                        .setPositiveButton("删除") { _, _ ->
                            val deletedCount = pdfsFragment.deleteSelectedItems()
                            showToast("已删除 $deletedCount 个PDF文件")
                            updateDeleteButtonState()
                            btnShare.isEnabled = false
                            currentPdfPath = null
                            pdfUri = null
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
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
            Log.d(TAG, "启动扫描器...")
            val intent = Intent(this, ScannerActivity::class.java)
            scanDocumentLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "启动扫描器失败", e)
            showToast("启动扫描器失败: ${e.message}")
        }
    }

    private fun createPdf() {
        val selectedImagePaths = photosFragment.getSelectedItems().map { it.path }
        if (selectedImagePaths.isEmpty()) {
            showToast("请选择至少一张图片")
            return
        }

        // 创建临时 PDF 在应用私有目录
        val pdfFileName = "documents_${System.currentTimeMillis()}.pdf"
        val pdfPath = File(getExternalFilesDir(null), pdfFileName).absolutePath
        Log.d(TAG, "PDF路径: $pdfPath")

        // 显示处理对话框
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("正在生成PDF...")
            setCancelable(false)
            show()
        }

        // 在后台线程处理
        Thread {
            val success = PdfConverter.createPdf(selectedImagePaths, pdfPath)

            runOnUiThread {
                progressDialog.dismiss()

                if (success) {
                    currentPdfPath = pdfPath

                    // 保存到公共目录并获取新的 URI
                    val publicUri = savePdfToPublicDirectory(pdfPath)
                    if (publicUri != null) {
                        // 保存公共 URI 以供分享
                        pdfUri = publicUri
                        showToast("PDF创建成功并保存到Documents/Scanner目录")
                    } else {
                        showToast("PDF创建成功，但保存到公共目录失败")
                    }

                    // 清除照片选择
                    photosFragment.clearSelections()

                    // 切换到PDF标签页并刷新
                    viewPager.currentItem = 1
                    pdfsFragment.loadPdfs()
                    pdfsFragment.setCurrentPdf(pdfPath, pdfUri?.toString())

                    // 启用分享按钮
                    btnShare.isEnabled = true
                } else {
                    showToast("PDF创建失败")
                }
            }
        }.start()
    }

    private fun sharePdf() {
        // 使用当前选中的PDF或上次创建的PDF
        val pdfToShare = if (viewPager.currentItem == 1 && pdfsFragment.getSelectedItems().isNotEmpty()) {
            val selectedPdf = pdfsFragment.getSelectedItems().first()
            pdfUri = selectedPdf.uri?.toUri()
            File(selectedPdf.path)
        } else if (currentPdfPath != null) {
            File(currentPdfPath!!)
        } else {
            showToast("没有可分享的PDF文件")
            return
        }

        val uri = pdfUri ?: pdfToShare.let { file ->
            if (file.exists()) {
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            } else null
        }

        if (uri != null) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "application/pdf"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享PDF"))
        } else {
            showToast("PDF文件不存在")
        }
    }

    private fun setupDebugOptions() {
        // 仅在DEBUG版本添加长按操作
        btnTakePhoto.setOnLongClickListener {
            // 启动相机测试工具
            val intent = Intent(this, CameraTestActivity::class.java)
            startActivity(intent)
            true
        }
    }

    // 将PDF保存到公共目录
    private fun savePdfToPublicDirectory(sourceFilePath: String): Uri? {
        try {
            val sourceFile = File(sourceFilePath)
            val fileName = sourceFile.name

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore API
                val contentValues = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/Scanner")
                    put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                }

                val contentResolver = applicationContext.contentResolver
                val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                    ?: return null

                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val inputStream = FileInputStream(sourceFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                }

                contentValues.clear()
                contentValues.put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)

                return uri
            } else {
                // Android 9 及以下，使用传统文件系统
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val scannerDir = File(documentsDir, "Scanner")
                if (!scannerDir.exists()) {
                    scannerDir.mkdirs()
                }

                val destinationFile = File(scannerDir, fileName)
                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // 通知媒体扫描器更新
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(destinationFile)
                sendBroadcast(mediaScanIntent)

                return Uri.fromFile(destinationFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存PDF到公共目录失败", e)
            return null
        }
    }

    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}