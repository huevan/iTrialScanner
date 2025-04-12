package com.example.itrialscanner

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import com.github.chrisbanes.photoview.PhotoView

class ImageViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        // 设置ActionBar显示返回按钮
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle("查看图片")
        }

        // 获取传递过来的图片路径
        val imagePath = intent.getStringExtra("imagePath") ?: return
        val imageFile = File(imagePath)

        // 设置标题为图片名称
        supportActionBar?.title = imageFile.name

        // 加载图片到PhotoView (支持缩放)
        val photoView = findViewById<PhotoView>(R.id.photoView)
        photoView.setImageURI(android.net.Uri.fromFile(imageFile))
    }

    // 处理返回按钮点击
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}