package com.example.itrialscanner

data class DocumentItem(
    val path: String,
    val name: String,
    var isSelected: Boolean = false
)