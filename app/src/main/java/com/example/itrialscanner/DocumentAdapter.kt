package com.example.itrialscanner
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class DocumentAdapter(
    private val context: Context,
    private val documentItems: MutableList<DocumentItem>
) : RecyclerView.Adapter<DocumentAdapter.DocumentViewHolder>() {

    // 使用函数类型的监听器，简化实现
    private var onSelectionChangedListener: ((Int) -> Unit)? = null

    @NonNull
    override fun onCreateViewHolder(@NonNull parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.document_item, parent, false)
        return DocumentViewHolder(view)
    }

    override fun onBindViewHolder(@NonNull holder: DocumentViewHolder, position: Int) {
        val item = documentItems[position]

        // 加载图片
        Glide.with(context)
            .load(File(item.path))
            .centerCrop()
            .into(holder.documentImage)

        holder.documentName.text = item.name
        holder.documentCheckbox.isChecked = item.isSelected

        holder.documentCheckbox.setOnClickListener {
            item.isSelected = holder.documentCheckbox.isChecked
            notifySelectionChanged()
        }

        holder.itemView.setOnClickListener {
            val newState = !item.isSelected
            item.isSelected = newState
            holder.documentCheckbox.isChecked = newState
            notifySelectionChanged()
        }
    }

    override fun getItemCount(): Int {
        return documentItems.size
    }

    // 通知选择状态改变
    private fun notifySelectionChanged() {
        val selectedCount = documentItems.count { it.isSelected }
        onSelectionChangedListener?.invoke(selectedCount)
    }

    // 设置选择状态改变监听器
    fun setOnSelectionChangedListener(listener: (Int) -> Unit) {
        onSelectionChangedListener = listener
        // 初始化时通知当前选中状态
        notifySelectionChanged()
    }

    // 获取选中的项目
    fun getSelectedItems(): List<DocumentItem> {
        return documentItems.filter { it.isSelected }
    }

    // 移除项目并通知适配器
    fun removeItems(itemsToRemove: List<DocumentItem>) {
        documentItems.removeAll(itemsToRemove)
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val documentImage: ImageView = itemView.findViewById(R.id.documentImage)
        val documentName: TextView = itemView.findViewById(R.id.documentName)
        val documentCheckbox: CheckBox = itemView.findViewById(R.id.documentCheckbox)
    }
}