package com.example.itrialscanner

data class DocumentItem(
    val path: String,
    val name: String,
    var isSelected: Boolean = false
)

data class PdfItem(
    val path: String,
    val name: String,
    val uri: String? = null,
    var isSelected: Boolean = false
)