package com.example.executorchllamademo

import android.content.Context
import org.json.JSONObject

data class WasteInfo(
    val title: String,
    val category: String,
    val steps: List<String>,
    val dont: List<String>,
    val why: String,
    val tip: String,
    val warning: String?
)

/** Reads knowledge_base.json from assets and returns the entry for a class name. */
object KnowledgeBase {
    private var root: JSONObject? = null

    fun get(context: Context, key: String): WasteInfo {
        val data = root ?: JSONObject(
            context.assets.open("knowledge_base.json").bufferedReader().use { it.readText() }
        ).also { root = it }

        val o = data.optJSONObject(key) ?: data.getJSONObject("unsure")

        fun list(name: String): List<String> {
            val a = o.optJSONArray(name) ?: return emptyList()
            return List(a.length()) { a.getString(it) }
        }

        return WasteInfo(
            title = o.optString("title"),
            category = o.optString("category"),
            steps = list("steps"),
            dont = list("dont"),
            why = o.optString("why"),
            tip = o.optString("tip"),
            warning = if (o.has("warning")) o.getString("warning") else null
        )
    }
}
