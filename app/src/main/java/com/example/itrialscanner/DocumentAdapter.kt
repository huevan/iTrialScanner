package com.example.itrialscanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.io.File

class DocumentAdapter(
    private val context: Context,
    private val items: List<DocumentItem>
) : RecyclerView.Adapter<DocumentAdapter.ViewHolder>() {

    private var onSelectionChangedListener: ((Int) -> Unit)? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val imageCache = mutableMapOf<String, Bitmap>()

    fun setOnSelectionChangedListener(listener: (Int) -> Unit) {
        onSelectionChangedListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_document, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    private fun countSelectedItems(): Int {
        return items.count { it.isSelected }
    }

    private fun notifySelectionChanged() {
        onSelectionChangedListener?.invoke(countSelectedItems())
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        coroutineScope.cancel()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.documentImage)
        private val nameTextView: TextView = itemView.findViewById(R.id.documentName)
        private val checkBox: CheckBox = itemView.findViewById(R.id.documentCheckbox)
        private val documentInfo: LinearLayout = itemView.findViewById(R.id.documentInfo)

        fun bind(item: DocumentItem) {
            nameTextView.text = item.name
            checkBox.isChecked = item.isSelected

            // 加载缩略图
            loadThumbnail(item.path)

            // 设置点击图片打开查看器
            imageView.setOnClickListener {
                val intent = Intent(context, ImageViewerActivity::class.java)
                intent.putExtra("imagePath", item.path)
                context.startActivity(intent)
            }

            // 设置点击文件名才选中图片
            documentInfo.setOnClickListener {
                item.isSelected = !item.isSelected
                checkBox.isChecked = item.isSelected
                notifySelectionChanged()
            }

            // 复选框点击事件
            checkBox.setOnClickListener {
                item.isSelected = checkBox.isChecked
                notifySelectionChanged()
            }
        }

        private fun loadThumbnail(imagePath: String) {
            // 首先检查缓存
            if (imageCache.containsKey(imagePath)) {
                imageView.setImageBitmap(imageCache[imagePath])
                return
            }

            // 显示加载指示器或默认图片
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)

            coroutineScope.launch {
                try {
                    // 后台线程加载图片
                    val bitmap = withContext(Dispatchers.IO) {
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 8  // 缩小图像以提高加载速度
                        }
                        val file = File(imagePath)
                        if (file.exists()) {
                            BitmapFactory.decodeFile(imagePath, options)
                        } else {
                            Log.e("DocumentAdapter", "文件不存在: $imagePath")
                            null
                        }
                    }

                    // 在主线程更新UI
                    bitmap?.let {
                        imageCache[imagePath] = it
                        imageView.setImageBitmap(it)
                    } ?: run {
                        // 加载失败，显示错误图标
                        imageView.setImageResource(android.R.drawable.ic_dialog_alert)
                    }
                } catch (e: Exception) {
                    Log.e("DocumentAdapter", "加载缩略图失败: ${e.message}", e)
                    imageView.setImageResource(android.R.drawable.ic_dialog_alert)
                }
            }
        }
    }
}