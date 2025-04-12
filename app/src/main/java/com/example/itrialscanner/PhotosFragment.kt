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

        // 获取拍摄的照片
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (storageDir != null && storageDir.exists()) {
            val files = storageDir.listFiles { _, name ->
                name.endsWith(".jpg") && !name.contains("_processed")
            }

            files?.forEach { file ->
                // 查找对应的处理后文件
                val processedFileName = file.name.replace(".jpg", "_processed.jpg")
                val processedFile = File(storageDir, processedFileName)

                // 优先使用处理后的文件，如果存在的话
                val displayPath = if (processedFile.exists()) processedFile.absolutePath else file.absolutePath

                documentItems.add(DocumentItem(displayPath, file.name))
            }
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