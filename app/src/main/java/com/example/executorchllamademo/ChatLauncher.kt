package com.example.executorchllamademo

import android.content.Context
import android.content.Intent

/**
 * Opens the Llama chat screen with the model already configured, so the user never
 * has to go through the demo's settings screens.
 *
 * The model and tokenizer must be on the phone in /data/local/tmp/llama (as you already did with adb).
 */
object ChatLauncher {

    const val MODEL_PATH = "/data/local/tmp/llama/llama3_2_1b_v110.pte"
    const val TOKENIZER_PATH = "/data/local/tmp/llama/tokenizer.model"
    const val EXTRA_SCAN_CONTEXT = "scan_context"

    const val SYSTEM_PROMPT =
        "You are a waste-management assistant for households in India. " +
        "Answer only questions about waste, recycling and disposal, in at most 3 short sentences. " +
        "Use the item information you are given. " +
        "If you are not sure, say so and suggest asking the local waste collector. " +
        "Never give instructions for handling dangerous chemicals beyond the item information."

    fun open(context: Context, scanContext: String) {
        val settings = ModuleSettings(
            modelFilePath = MODEL_PATH,
            tokenizerFilePath = TOKENIZER_PATH,
            dataPath = "",
            systemPrompt = SYSTEM_PROMPT,
            userPrompt = PromptFormat.getUserPromptTemplate(ModelType.LLAMA_3),
            modelType = ModelType.LLAMA_3,
            backendType = BackendType.XNNPACK,
            isLoadModel = true
        )
        DemoSharedPreferences(context).saveModuleSettings(settings)

        val intent = Intent(context, MainActivity::class.java)
        intent.putExtra(EXTRA_SCAN_CONTEXT, scanContext)
        context.startActivity(intent)
    }
}

/** Turns the category card into a short text that is given to Llama together with the question. */
fun WasteInfo.toContext(): String {
    val sb = StringBuilder()
    sb.append("Category: ").append(category).append(". ")
    if (steps.isNotEmpty()) sb.append("What to do: ").append(steps.joinToString(" ")).append(" ")
    if (dont.isNotEmpty()) sb.append("Avoid: ").append(dont.joinToString(" ")).append(" ")
    if (why.isNotEmpty()) sb.append("Why: ").append(why).append(" ")
    if (tip.isNotEmpty()) sb.append("Tip: ").append(tip)
    return sb.toString().trim()
}
