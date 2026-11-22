package com.example.executorchllamademo

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/** One detected item: where it is (in camera-image pixels) and what the classifier says. */
data class DetBox(
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val category: String,      // Biodegradable, Recyclable, E-Waste, Hazardous or "Unsure"
    val confidence: Float
)

data class DetFrame(val width: Int, val height: Int, val boxes: List<DetBox>)

/** Box colours: different for every category so they are easy to tell apart. */
fun boxColor(category: String): Color = when (category) {
    "Biodegradable" -> Color(0xFF43A047)  // green
    "Recyclable" -> Color(0xFF1E88E5)     // blue
    "E-Waste" -> Color(0xFFFFB300)        // amber
    "Hazardous" -> Color(0xFFE53935)      // red
    else -> Color(0xFF9E9E9E)             // grey
}

/**
 * Step 1: ML Kit finds WHERE the objects are (it works fully on the phone, no internet).
 * Step 2: every box is cropped and given to our own ExecuTorch classifier, which says WHAT it is.
 */
class LiveAnalyzer(
    private val classify: (Bitmap) -> Pair<String, Float>?,
    private val onResult: (DetFrame) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
    )
    private var last = 0L
    private var lastSeen = 0L

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - last < 300) { image.close(); return }   // about 3 updates per second
        last = now
        try {
            var raw = image.toBitmap()
            // use only the part of the picture that is visible on screen
            val cr = image.cropRect
            if (cr.width() > 0 && cr.height() > 0 && (cr.width() != raw.width || cr.height() != raw.height)) {
                raw = Bitmap.createBitmap(raw, cr.left, cr.top,
                    minOf(cr.width(), raw.width - cr.left), minOf(cr.height(), raw.height - cr.top))
            }
            val rot = image.imageInfo.rotationDegrees
            val bmp = if (rot == 0) raw else Bitmap.createBitmap(
                raw, 0, 0, raw.width, raw.height,
                Matrix().apply { postRotate(rot.toFloat()) }, true
            )
            val objects = Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0)))
            val boxes = ArrayList<DetBox>()
            for (o in objects) {
                val r = o.boundingBox
                val pad = 0.08f * maxOf(r.width(), r.height())
                val l = maxOf(0f, r.left - pad).toInt()
                val t = maxOf(0f, r.top - pad).toInt()
                val rr = minOf(bmp.width.toFloat(), r.right + pad).toInt()
                val bb = minOf(bmp.height.toFloat(), r.bottom + pad).toInt()
                if (rr - l < 48 || bb - t < 48) continue
                val crop = Bitmap.createBitmap(bmp, l, t, rr - l, bb - t)
                val res = classify(crop)
                val cat = if (res != null && res.second >= 0.3f) res.first else "Unsure"
                boxes.add(DetBox(l.toFloat(), t.toFloat(), rr.toFloat(), bb.toFloat(), cat, res?.second ?: 0f))
            }
            // ML Kit often misses an object for a frame or two. Keep the old boxes for 2 seconds
            // so they do not blink, and only clear them when nothing has been seen for that long.
            if (boxes.isNotEmpty()) {
                lastSeen = now
                onResult(DetFrame(bmp.width, bmp.height, boxes))
            } else if (now - lastSeen > 2000) {
                onResult(DetFrame(bmp.width, bmp.height, emptyList()))
            }
        } catch (e: Exception) {
            // skip this frame
        } finally {
            image.close()
        }
    }
}

/** Draws the coloured squares and the category label on top of the camera preview. */
@Composable
fun DetectionOverlay(frame: DetFrame, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier = modifier) {
        if (frame.width <= 1 || frame.boxes.isEmpty()) return@Canvas
        // The preview fills the screen and crops the sides (FILL_CENTER), so use the same maths.
        val scale = maxOf(size.width / frame.width, size.height / frame.height)
        val dx = (size.width - frame.width * scale) / 2f
        val dy = (size.height - frame.height * scale) / 2f

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 13.dp.toPx()
            isFakeBoldText = true
            color = android.graphics.Color.WHITE
        }
        for (b in frame.boxes) {
            val c = boxColor(b.category)
            val x = b.left * scale + dx
            val y = b.top * scale + dy
            val w = (b.right - b.left) * scale
            val h = (b.bottom - b.top) * scale
            drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(10.dp.toPx()), style = Stroke(3.dp.toPx()))

            val label = if (b.category == "Unsure") "Not sure"
            else "${b.category}  ${(b.confidence * 100).toInt()}%"
            val tw = paint.measureText(label)
            val padH = 8.dp.toPx()
            val lh = 22.dp.toPx()
            val ly = if (y - lh < 0f) y else y - lh
            drawRoundRect(c, Offset(x, ly), Size(tw + 2 * padH, lh), CornerRadius(6.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText(label, x + padH, ly + lh * 0.7f, paint)
        }
    }
}