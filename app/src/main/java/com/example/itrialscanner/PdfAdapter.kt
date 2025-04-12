package com.example.itrialscanner

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PdfAdapter(
    private val context: Context,
    private val items: List<PdfItem>,
    private val onItemClick: (PdfItem) -> Unit
) : RecyclerView.Adapter<PdfAdapter.ViewHolder>() {

    private var onSelectionChangedListener: ((Int) -> Unit)? = null

    fun setOnSelectionChangedListener(listener: (Int) -> Unit) {
        onSelectionChangedListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_pdf, parent, false)
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

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.pdfName)
        private val checkBox: CheckBox = itemView.findViewById(R.id.pdfCheckbox)

        fun bind(item: PdfItem) {
            nameTextView.text = item.name
            checkBox.isChecked = item.isSelected

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                item.isSelected = isChecked
                notifySelectionChanged()
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}