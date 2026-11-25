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
    val warning: String?,
    val journey: String = "",
    val items: List<Pair<String, String>> = emptyList()
)

/**
 * Reads the advice from assets: knowledge_base.json (English) or knowledge_base_hi.json (Hindi),
 * depending on the language chosen in the app.
 */
object KnowledgeBase {
    private var rootEn: JSONObject? = null
    private var rootHi: JSONObject? = null

    private fun read(context: Context, file: String): JSONObject =
        JSONObject(context.assets.open(file).bufferedReader().use { it.readText() })

    private fun root(context: Context): JSONObject {
        if (Lang.hindi) {
            val cached = rootHi
            if (cached != null) return cached
            try {
                return read(context, "knowledge_base_hi.json").also { rootHi = it }
            } catch (e: Exception) {
                // no Hindi file: fall back to English
            }
        }
        return rootEn ?: read(context, "knowledge_base.json").also { rootEn = it }
    }

    fun get(context: Context, key: String): WasteInfo {
        val data = root(context)
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
            warning = if (o.has("warning")) o.getString("warning") else null,
            journey = o.optString("journey"),
            items = o.optJSONArray("items")?.let { arr ->
                List(arr.length()) { i ->
                    val x = arr.getJSONObject(i)
                    Pair(x.optString("name"), x.optString("tip"))
                }
            } ?: emptyList()
        )
    }
}