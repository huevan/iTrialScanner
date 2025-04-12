package com.example.itrialscanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class PdfsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private val pdfItems = mutableListOf<PdfItem>()
    private lateinit var adapter: PdfAdapter

    var onSelectionChangedListener: ((Int) -> Unit)? = null
    var onPdfSelectedListener: ((PdfItem) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pdfs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.pdfsRecyclerView)
        emptyView = view.findViewById(R.id.emptyPdfsView)

        setupRecyclerView()
        loadPdfs()
    }

    private fun setupRecyclerView() {
        adapter = PdfAdapter(requireContext(), pdfItems) { pdfItem ->
            // 点击打开PDF
            openPdf(pdfItem)
            onPdfSelectedListener?.invoke(pdfItem)
        }

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter.setOnSelectionChangedListener { selectedCount ->
            onSelectionChangedListener?.invoke(selectedCount)

            // 激活主活动中的删除按钮
            (activity as? MainActivity)?.let {
                it.setDeleteButtonEnabled(selectedCount > 0)
            }
        }
    }

    private fun openPdf(pdfItem: PdfItem) {
        try {
            val file = File(pdfItem.path)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(intent)
                } else {
                    (activity as? MainActivity)?.showToast("没有可以打开PDF的应用")
                }
            }
        } catch (e: Exception) {
            (activity as? MainActivity)?.showToast("打开PDF失败: ${e.message}")
        }
    }

    fun loadPdfs() {
        pdfItems.clear()

        // 获取应用内PDF文件
        val internalDir = requireContext().getExternalFilesDir(null)
        if (internalDir != null && internalDir.exists()) {
            val files = internalDir.listFiles { _, name -> name.endsWith(".pdf") }

            files?.forEach { file ->
                pdfItems.add(PdfItem(file.absolutePath, file.name))
            }
        }

        adapter.notifyDataSetChanged()

        // 显示或隐藏空视图
        if (pdfItems.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    fun getSelectedItems(): List<PdfItem> {
        return pdfItems.filter { it.isSelected }
    }

    fun clearSelections() {
        pdfItems.forEach { it.isSelected = false }
        adapter.notifyDataSetChanged()
        onSelectionChangedListener?.invoke(0)
    }

    fun setCurrentPdf(pdfPath: String, uri: String?) {
        // 为新创建的PDF设置状态
        pdfItems.forEach { it.isSelected = false }

        val existingItem = pdfItems.find { it.path == pdfPath }
        if (existingItem != null) {
            existingItem.isSelected = true
        } else {
            val file = File(pdfPath)
            if (file.exists()) {
                val newItem = PdfItem(pdfPath, file.name, uri)
                pdfItems.add(0, newItem) // 添加到列表开头
                newItem.isSelected = true
            }
        }

        adapter.notifyDataSetChanged()
        onSelectionChangedListener?.invoke(pdfItems.count { it.isSelected })
    }

    fun deleteSelectedItems(): Int {
        val selectedItems = pdfItems.filter { it.isSelected }
        var deletedCount = 0

        selectedItems.forEach { item ->
            val file = File(item.path)
            if (file.exists() && file.delete()) {
                deletedCount++
            }
        }

        // 刷新列表
        if (deletedCount > 0) {
            loadPdfs()
        }

        return deletedCount
    }
}