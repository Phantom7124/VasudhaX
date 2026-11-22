package com.example.executorchllamademo

import android.graphics.Bitmap
import org.pytorch.executorch.Tensor

/**
 * Turns a Bitmap into the input the classifier was trained on:
 * 224x224, RGB, scaled to 0..1, then ImageNet mean/std normalisation, layout [1, 3, 224, 224].
 */
object ImageTensor {
    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    private const val SIZE = 224

    fun fromBitmap(src: Bitmap): Tensor {
        val bmp = Bitmap.createScaledBitmap(src, SIZE, SIZE, true)
        val n = SIZE * SIZE
        val pixels = IntArray(n)
        bmp.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        val buf = Tensor.allocateFloatBuffer(3 * n)
        for (i in 0 until n) {
            val c = pixels[i]
            val r = ((c shr 16) and 0xff) / 255f
            val g = ((c shr 8) and 0xff) / 255f
            val b = (c and 0xff) / 255f
            buf.put(i, (r - MEAN[0]) / STD[0])
            buf.put(n + i, (g - MEAN[1]) / STD[1])
            buf.put(2 * n + i, (b - MEAN[2]) / STD[2])
        }
        return Tensor.fromBlob(buf, longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong()))
    }
}
