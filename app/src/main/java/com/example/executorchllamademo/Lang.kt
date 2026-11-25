package com.example.executorchllamademo

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The app language: English or Hindi. Saved on the phone, and the screens update at once. */
object Lang {
    var hindi by mutableStateOf(false)
        private set

    private const val PREFS = "waste_prefs"

    fun init(ctx: Context) {
        hindi = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("hindi", false)
    }

    fun set(ctx: Context, on: Boolean) {
        hindi = on
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("hindi", on).apply()
    }
}

/** Picks the English or the Hindi text. */
fun tr(en: String, hi: String): String = if (Lang.hindi) hi else en

/** Category name in the current language. The category itself is always kept in English inside the app. */
fun catLabel(category: String): String = when (category) {
    "Biodegradable" -> tr("Biodegradable", "जैव-अपघटनीय")
    "Recyclable" -> tr("Recyclable", "पुनर्चक्रण योग्य")
    "E-Waste" -> tr("E-Waste", "ई-कचरा")
    "Hazardous" -> tr("Hazardous", "खतरनाक")
    else -> tr("Not sure", "पक्का नहीं")
}
