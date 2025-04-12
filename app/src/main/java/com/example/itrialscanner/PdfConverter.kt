import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object PdfConverter {
    /**
     * 创建PDF文件，保持图片原始方向
     * @param imagePaths 图片路径列表
     * @param outputPath 输出PDF路径
     * @return 是否创建成功
     */
    fun createPdf(imagePaths: List<String>, outputPath: String): Boolean {
        try {
            val pdfDocument = PdfDocument()

            for ((index, imagePath) in imagePaths.withIndex()) {
                // 获取图片尺寸而不加载完整图片
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(imagePath, options)

                // 读取EXIF信息获取图片方向
                val orientation = try {
                    val exif = ExifInterface(imagePath)
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } catch (e: IOException) {
                    Log.e("PdfConverter", "无法读取EXIF: $imagePath", e)
                    ExifInterface.ORIENTATION_NORMAL
                }

                // 确定正确的宽高
                var width = options.outWidth
                var height = options.outHeight

                // 根据方向确定最终PDF页面尺寸
                val rotationDegrees = getRotationDegrees(orientation)
                if (rotationDegrees == 90 || rotationDegrees == 270) {
                    // 交换宽高
                    val temp = width
                    width = height
                    height = temp
                }

                // 创建PDF页面
                val pageInfo = PdfDocument.PageInfo.Builder(width, height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)

                // 加载并旋转bitmap
                val bitmap = loadAndRotateBitmap(imagePath, orientation)
                if (bitmap != null) {
                    // 将bitmap绘制到页面上
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)
                    bitmap.recycle() // 释放资源
                } else {
                    // 如果加载失败，完成空白页面
                    pdfDocument.finishPage(page)
                    Log.e("PdfConverter", "无法加载图片: $imagePath")
                }
            }

            // 写入PDF文件
            FileOutputStream(outputPath).use { fos ->
                pdfDocument.writeTo(fos)
            }

            pdfDocument.close()
            return File(outputPath).exists()
        } catch (e: Exception) {
            Log.e("PdfConverter", "创建PDF失败", e)
            return false
        }
    }

    /**
     * 根据EXIF方向值获取旋转角度
     */
    private fun getRotationDegrees(orientation: Int): Int {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    /**
     * 加载并根据EXIF信息旋转位图
     */
    private fun loadAndRotateBitmap(imagePath: String, orientation: Int): Bitmap? {
        try {
            // 为了避免OOM，先计算合适的采样率
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)

            // 限制最大尺寸为2048像素
            val maxDimension = 2048
            val sampleSize = calculateInSampleSize(options, maxDimension, maxDimension)

            // 加载适当缩小的bitmap
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            val bitmap = BitmapFactory.decodeFile(imagePath, options) ?: return null

            // 旋转bitmap以纠正方向
            val rotationDegrees = getRotationDegrees(orientation)
            return if (rotationDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle() // 回收原始bitmap
                }
                rotatedBitmap
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e("PdfConverter", "处理图片失败", e)
            return null
        }
    }

    /**
     * 计算合适的采样率以避免OOM错误
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}