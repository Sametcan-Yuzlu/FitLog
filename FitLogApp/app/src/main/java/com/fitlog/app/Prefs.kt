package com.fitlog.app

import android.content.Context
import org.json.JSONArray

/** Small on-device store: connection settings, known exercises, and workouts waiting to sync. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fitlog", Context.MODE_PRIVATE)

    var scriptUrl: String
        get() = sp.getString("url", "").orEmpty()
        set(v) = sp.edit().putString("url", v).apply()

    var secret: String
        get() = sp.getString("secret", "").orEmpty()
        set(v) = sp.edit().putString("secret", v).apply()

    var person: String
        get() = sp.getString("person", "").orEmpty()
        set(v) = sp.edit().putString("person", v).apply()

    var exercises: List<String>
        get() = sp.getString("exercises", "").orEmpty().split("\n").filter { it.isNotBlank() }
        set(v) = sp.edit().putString("exercises", v.joinToString("\n")).apply()

    var people: List<String>
        get() = sp.getString("people", "").orEmpty().split("\n").filter { it.isNotBlank() }
        set(v) = sp.edit().putString("people", v.joinToString("\n")).apply()

    /** Today's saved sets (for the Today list) and the date they belong to. */
    var today: JSONArray
        get() = JSONArray(sp.getString("today", "[]"))
        set(v) = sp.edit().putString("today", v.toString()).apply()

    var todayDate: String
        get() = sp.getString("todayDate", "").orEmpty()
        set(v) = sp.edit().putString("todayDate", v).apply()

    var pending: JSONArray
        get() = JSONArray(sp.getString("pending", "[]"))
        set(v) = sp.edit().putString("pending", v.toString()).apply()
}
