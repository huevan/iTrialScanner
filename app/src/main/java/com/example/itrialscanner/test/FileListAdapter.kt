package com.example.itrialscanner.test

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.itrialscanner.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件列表适配器，用于显示相机测试产生的图像文件
 */
class FileListAdapter(private val onItemClick: (File) -> Unit) :
    RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {

    private var files: List<File> = emptyList()

    // 更新文件列表
    fun updateFiles(newFiles: List<File>) {
        files = newFiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.bind(file)
    }

    override fun getItemCount(): Int = files.size

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileNameTextView: TextView = itemView.findViewById(R.id.fileNameTextView)
        private val fileSizeTextView: TextView = itemView.findViewById(R.id.fileSizeTextView)
        private val fileDateTextView: TextView = itemView.findViewById(R.id.fileDateTextView)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(files[position])
                }
            }
        }

        fun bind(file: File) {
            fileNameTextView.text = file.name

            // 显示文件大小
            val fileSize = when {
                file.length() > 1024 * 1024 -> "${file.length() / (1024 * 1024)} MB"
                file.length() > 1024 -> "${file.length() / 1024} KB"
                else -> "${file.length()} B"
            }
            fileSizeTextView.text = fileSize

            // 显示文件日期
            val date = Date(file.lastModified())
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            fileDateTextView.text = formatter.format(date)
        }
    }
}