package com.fitlog.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate

data class SetEntry(val weight: String = "", val reps: String = "")

class LogViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    var scriptUrl by mutableStateOf(prefs.scriptUrl); private set
    var secret by mutableStateOf(prefs.secret); private set
    var unit by mutableStateOf(prefs.unit); private set

    var date by mutableStateOf(LocalDate.now().toString())
    var exercise by mutableStateOf("")
    var notes by mutableStateOf("")
    val sets = mutableStateListOf(SetEntry())

    var exercises by mutableStateOf(prefs.exercises); private set
    var lastSession by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false); private set
    var pendingCount by mutableIntStateOf(prefs.pending.length()); private set

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private var lastJob: Job? = null

    init {
        if (scriptUrl.isNotBlank()) {
            viewModelScope.launch {
                refreshExercises()
                syncPendingInternal(verbose = false)
            }
        }
    }

    fun saveSettings(url: String, newSecret: String) {
        scriptUrl = url; prefs.scriptUrl = url
        secret = newSecret; prefs.secret = newSecret
        viewModelScope.launch {
            try {
                val r = call(JSONObject().put("action", "exercises"))
                if (r.optBoolean("ok")) {
                    mergeExercises(r.optJSONArray("exercises"))
                    msg("Connected to Google Sheets ✓")
                    syncPendingInternal(verbose = false)
                } else {
                    msg("Sheet said: ${r.optString("error")}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                msg("Couldn't connect: ${e.message}")
            }
        }
    }

    fun changeUnit(u: String) {
        unit = u; prefs.unit = u
    }

    fun addSet() {
        sets.add(sets.lastOrNull() ?: SetEntry())
    }

    fun removeSet(index: Int) {
        if (sets.size > 1) sets.removeAt(index) else sets[0] = SetEntry()
    }

    fun save() {
        val ex = exercise.trim()
        if (ex.isEmpty()) return msg("Enter an exercise name")

        val arr = JSONArray()
        sets.forEachIndexed { i, s ->
            if (s.weight.isBlank() && s.reps.isBlank()) return@forEachIndexed
            val reps = s.reps.toIntOrNull()
            val weight = if (s.weight.isBlank()) 0.0 else s.weight.replace(',', '.').toDoubleOrNull()
            if (reps == null || reps <= 0 || weight == null || weight < 0) {
                return msg("Check set ${i + 1}: enter a weight and a number of reps")
            }
            arr.put(JSONObject().put("weight", weight).put("reps", reps))
        }
        if (arr.length() == 0) return msg("Add at least one set")

        val payload = JSONObject()
            .put("action", "log")
            .put("date", date)
            .put("exercise", ex)
            .put("unit", unit)
            .put("notes", notes.trim())
            .put("sets", arr)

        if (scriptUrl.isBlank()) {
            enqueue(payload); rememberExercise(ex); resetForm()
            return msg("Saved on phone — add the script URL in Settings to sync")
        }

        saving = true
        viewModelScope.launch {
            try {
                val r = call(payload)
                if (r.optBoolean("ok")) {
                    rememberExercise(ex); resetForm()
                    msg("Logged ${arr.length()} set(s) of $ex")
                    syncPendingInternal(verbose = false)
                } else {
                    msg("Sheet said: ${r.optString("error")}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                enqueue(payload); rememberExercise(ex); resetForm()
                msg("No connection — saved on phone, will sync later")
            } catch (e: Exception) {
                msg(e.message ?: "Something went wrong")
            } finally {
                saving = false
            }
        }
    }

    fun syncPending() {
        viewModelScope.launch { syncPendingInternal(verbose = true) }
    }

    /** Shows what you did last time for the exercise currently typed in. */
    fun loadLast() {
        val ex = exercise.trim()
        lastJob?.cancel()
        if (ex.isEmpty() || scriptUrl.isBlank()) { lastSession = null; return }
        lastJob = viewModelScope.launch {
            try {
                val r = call(JSONObject().put("action", "last").put("exercise", ex))
                val arr = r.optJSONArray("sets")
                lastSession = if (r.optBoolean("ok") && arr != null && arr.length() > 0) {
                    val list = (0 until arr.length()).joinToString(", ") {
                        val s = arr.getJSONObject(it)
                        "${fmt(s.optDouble("weight"))}×${s.optInt("reps")}"
                    }
                    "Last time (${r.optString("date")}): $list ${r.optString("unit")}"
                } else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastSession = null
            }
        }
    }

    // ---- internals ----

    private suspend fun call(body: JSONObject): JSONObject {
        val withSecret = JSONObject(body.toString()).put("secret", secret)
        return SheetsClient.post(scriptUrl, withSecret)
    }

    private suspend fun refreshExercises() {
        try {
            val r = call(JSONObject().put("action", "exercises"))
            if (r.optBoolean("ok")) mergeExercises(r.optJSONArray("exercises"))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // offline: keep the cached list
        }
    }

    private suspend fun syncPendingInternal(verbose: Boolean) {
        val queue = prefs.pending
        if (queue.length() == 0) {
            if (verbose) msg("Nothing to sync")
            return
        }
        if (scriptUrl.isBlank()) {
            if (verbose) msg("Add the script URL in Settings first")
            return
        }
        val remaining = JSONArray()
        var sent = 0
        var lastError: String? = null
        for (i in 0 until queue.length()) {
            val p = queue.getJSONObject(i)
            try {
                val r = call(p)
                if (r.optBoolean("ok")) sent++ else { remaining.put(p); lastError = r.optString("error") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                remaining.put(p); lastError = e.message
            }
        }
        prefs.pending = remaining
        pendingCount = remaining.length()
        if (verbose || sent > 0) {
            msg(
                when {
                    remaining.length() == 0 -> "Synced $sent saved workout(s)"
                    else -> "Synced $sent, ${remaining.length()} still waiting${lastError?.let { " ($it)" } ?: ""}"
                }
            )
        }
    }

    private fun enqueue(payload: JSONObject) {
        val q = prefs.pending
        q.put(payload)
        prefs.pending = q
        pendingCount = q.length()
    }

    private fun mergeExercises(arr: JSONArray?) {
        if (arr == null) return
        val merged = (exercises + (0 until arr.length()).map { arr.getString(it) })
            .map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        exercises = merged; prefs.exercises = merged
    }

    private fun rememberExercise(name: String) {
        if (exercises.none { it.equals(name, ignoreCase = true) }) {
            val merged = (exercises + name).sortedBy { it.lowercase() }
            exercises = merged; prefs.exercises = merged
        }
    }

    private fun resetForm() {
        exercise = ""
        notes = ""
        lastSession = null
        sets.clear()
        sets.add(SetEntry())
    }

    private fun msg(text: String) {
        messageChannel.trySend(text)
    }

    private fun fmt(d: Double): String =
        if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
}
