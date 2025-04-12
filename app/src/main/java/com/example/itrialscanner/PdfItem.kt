package com.example.itrialscanner

data class PdfItem(
    val path: String,
    val name: String,
    val uri: String? = null,
    var isSelected: Boolean = false
)