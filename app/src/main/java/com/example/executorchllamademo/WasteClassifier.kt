package com.example.executorchllamademo

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.json.JSONArray
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import java.io.File
import kotlin.math.exp

/**
 * Loads the waste classifier (.pte) from the app's assets and runs it on a Bitmap.
 * Needs two files in app/src/main/assets:  waste_classifier.pte  and  classes.json
 */
class WasteClassifier(context: Context) {

    data class Result(val label: String, val confidence: Float, val millis: Long)

    private val module: Module
    private val classes: List<String>

    init {
        // Module.load needs a real file path, so copy the model out of assets first.
        val outFile = File(context.filesDir, MODEL_ASSET)
        context.assets.open(MODEL_ASSET).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        module = Module.load(outFile.absolutePath)

        val json = context.assets.open("classes.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        classes = List(arr.length()) { arr.getString(it) }
    }

    fun classify(bitmap: Bitmap): Result {
        val input = ImageTensor.fromBitmap(bitmap)
        val start = SystemClock.elapsedRealtime()
        val logits = module.forward(EValue.from(input))[0].toTensor().dataAsFloatArray
        val millis = SystemClock.elapsedRealtime() - start

        // If the model already outputs probabilities (all between 0 and 1, adding up to 1),
        // use them as they are. Applying softmax a second time would squash every confidence
        // to about 20-30% and the app would say "Not sure" for every photo.
        val total = logits.sum()
        val alreadyProbabilities =
            logits.all { it >= 0f && it <= 1.0001f } && total > 0.98f && total < 1.02f

        val probs = FloatArray(logits.size)
        if (alreadyProbabilities) {
            for (i in logits.indices) probs[i] = logits[i]
        } else {
            val maxLogit = logits.maxOrNull() ?: 0f
            val exps = DoubleArray(logits.size) { exp((logits[it] - maxLogit).toDouble()) }
            val sum = exps.sum()
            for (i in exps.indices) probs[i] = (exps[i] / sum).toFloat()
        }

        var best = 0
        for (i in probs.indices) if (probs[i] > probs[best]) best = i
        val label = if (best < classes.size) classes[best] else "unsure"
        return Result(label, probs[best], millis)
    }

    companion object {
        const val MODEL_ASSET = "waste_classifier.pte"
    }
}