package com.example.itrialscanner

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class PhotosFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private val documentItems = mutableListOf<DocumentItem>()
    private lateinit var adapter: DocumentAdapter

    var onSelectionChangedListener: ((Int) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_photos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.photosRecyclerView)
        emptyView = view.findViewById(R.id.emptyPhotosView)

        setupRecyclerView()
        loadPhotos()
    }

    private fun setupRecyclerView() {
        adapter = DocumentAdapter(requireContext(), documentItems)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)

        adapter.setOnSelectionChangedListener { selectedCount ->
            onSelectionChangedListener?.invoke(selectedCount)

            // 激活主活动中的删除按钮
            (activity as? MainActivity)?.let {
                it.setDeleteButtonEnabled(selectedCount > 0)
            }
        }
    }

    fun loadPhotos() {
        documentItems.clear()

        // 获取应用私有目录（不指定子目录）
        val storageDir = requireContext().getExternalFilesDir(null)
        if (storageDir != null && storageDir.exists()) {
            val files = storageDir.listFiles { file ->
                file.isFile && file.name.endsWith(".jpg")
            }

            files?.forEach { file ->
                // 优先使用处理后的文件，如果存在
                if (!file.name.contains("processed_")) {
                    // 对于原始文件，查找对应的处理后文件
                    val processedFile = File(file.parentFile, "processed_${file.name}")

                    // 使用处理后的文件（如果存在），否则使用原始文件
                    val displayPath = if (processedFile.exists()) processedFile.absolutePath else file.absolutePath

                    documentItems.add(DocumentItem(displayPath, file.name))
                } else if (!file.name.startsWith("processed_")) {
                    // 如果文件名包含"processed"但不是以"processed_"开头，则可能是独立文件
                    documentItems.add(DocumentItem(file.absolutePath, file.name))
                }
            }

            // 按照修改时间倒序排序（最新的在前）
            documentItems.sortByDescending { File(it.path).lastModified() }
        }

        // 调试日志
        android.util.Log.d("PhotosFragment", "找到 ${documentItems.size} 张照片")
        documentItems.forEach {
            android.util.Log.d("PhotosFragment", "照片: ${it.path}")
        }

        adapter.notifyDataSetChanged()

        // 显示或隐藏空视图
        if (documentItems.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    fun getSelectedItems(): List<DocumentItem> {
        return documentItems.filter { it.isSelected }
    }

    fun clearSelections() {
        documentItems.forEach { it.isSelected = false }
        adapter.notifyDataSetChanged()
        onSelectionChangedListener?.invoke(0)
    }

    fun deleteSelectedItems(): Int {
        val selectedItems = documentItems.filter { it.isSelected }
        var deletedCount = 0

        selectedItems.forEach { item ->
            val file = File(item.path)
            if (file.exists() && file.delete()) {
                deletedCount++
            }

            // 如果是处理后的图片，也要删除原始图片
            if (item.path.contains("_processed")) {
                val originalPath = item.path.replace("_processed.jpg", ".jpg")
                val originalFile = File(originalPath)
                if (originalFile.exists()) {
                    originalFile.delete()
                }
            } else {
                // 如果是原始图片，也要删除处理后的图片
                val processedPath = item.path.replace(".jpg", "_processed.jpg")
                val processedFile = File(processedPath)
                if (processedFile.exists()) {
                    processedFile.delete()
                }
            }
        }

        // 刷新列表
        if (deletedCount > 0) {
            loadPhotos()
        }

        return deletedCount
    }
}