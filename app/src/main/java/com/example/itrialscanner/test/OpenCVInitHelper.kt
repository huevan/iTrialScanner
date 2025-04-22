package com.example.itrialscanner.test

import android.content.Context
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * OpenCV初始化帮助类
 * 用于在应用启动时正确初始化OpenCV库
 */
class OpenCVInitHelper(private val context: Context) {
    private val TAG = "OpenCVInitHelper"

    /**
     * 初始化OpenCV
     * 使用静态方法初始化，不依赖于OpenCV Manager
     */
    fun init(): Boolean {
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV初始化失败")
            return false
        } else {
            Log.d(TAG, "OpenCV初始化成功")
            return true
        }
    }
}