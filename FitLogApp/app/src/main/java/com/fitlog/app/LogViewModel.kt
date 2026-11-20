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

/**
 * A set saved from this phone today, shown in the Today list.
 * [inSheet] is true only after the script confirmed it wrote the row; [setNo]/[row] come from the
 * script's reply (null with an older script, or while the set is waiting on the phone).
 * [id] links a queued (offline) set to its entry here so it can be updated once it syncs.
 * [code] (sheet exercise code) and [date] (ISO) identify the row for edits; empty on old entries.
 */
data class SavedSet(
    val id: Long,
    val exercise: String,
    val person: String,
    val weight: String,
    val reps: Int,
    val inSheet: Boolean,
    val setNo: Int? = null,
    val row: Int? = null,
    val code: String = "",
    val date: String = "",
) {
    /** Synced sets need their set number and code to be found in the sheet; waiting sets can always be edited. */
    val editable: Boolean get() = !inSheet || (setNo != null && code.isNotEmpty() && date.isNotEmpty())

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("exercise", exercise).put("person", person)
        .put("weight", weight).put("reps", reps).put("inSheet", inSheet)
        .put("setNo", setNo ?: 0).put("row", row ?: 0)
        .put("code", code).put("date", date)

    companion object {
        fun fromJson(o: JSONObject) = SavedSet(
            id = o.optLong("id"),
            exercise = o.optString("exercise"),
            person = o.optString("person"),
            weight = o.optString("weight"),
            reps = o.optInt("reps"),
            inSheet = o.optBoolean("inSheet"),
            setNo = o.optInt("setNo").takeIf { it > 0 },
            row = o.optInt("row").takeIf { it > 0 },
            code = o.optString("code"),
            date = o.optString("date"),
        )
    }
}

/** What the script reports it wrote: the set number and sheet row. */
private fun SavedSet.confirmed(reply: JSONObject) = copy(
    inSheet = true,
    setNo = reply.optJSONArray("setNos")?.optInt(0)?.takeIf { it > 0 },
    row = reply.optInt("row").takeIf { it > 0 },
)

class LogViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    var scriptUrl by mutableStateOf(prefs.scriptUrl); private set
    var secret by mutableStateOf(prefs.secret); private set

    var person by mutableStateOf(prefs.person); private set
    var date by mutableStateOf(LocalDate.now())
    var exercise by mutableStateOf("")
    /** The one set being entered. SetNo is assigned by the sheet script. */
    var current by mutableStateOf(SetEntry())

    var exercises by mutableStateOf(prefs.exercises); private set
    var people by mutableStateOf(prefs.people); private set
    var lastSession by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false); private set
    var pendingCount by mutableIntStateOf(prefs.pending.length()); private set
    /** Sets saved from this phone today, newest first. Kept across app restarts; resets each day. */
    val today = mutableStateListOf<SavedSet>().apply { addAll(loadToday()) }

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
    private var lastJob: Job? = null

    init {
        if (scriptUrl.isNotBlank()) {
            viewModelScope.launch {
                refreshMeta()
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
                    mergeMeta(r)
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

    fun changePerson(p: String) {
        person = p; prefs.person = p.trim()
    }

    fun save() {
        val who = person.trim()
        val ex = exerciseCode()
        if (who.isEmpty()) return msg("Enter who is lifting (Person)")
        if (ex.isEmpty()) return msg("Enter an exercise name")

        val reps = current.reps.toIntOrNull()
        val weight = if (current.weight.isBlank()) 0.0 else current.weight.replace(',', '.').toDoubleOrNull()
        if (reps == null || reps <= 0) return msg("Enter the number of reps")
        if (weight == null || weight < 0) return msg("Enter a valid weight")
        val arr = JSONArray().put(JSONObject().put("weight", weight).put("reps", reps))

        val id = System.currentTimeMillis()
        val payload = JSONObject()
            .put("action", "log")
            .put("clientId", id)
            .put("person", who)
            .put("date", date.toString())
            .put("exercise", ex)
            .put("sets", arr)

        val saved = SavedSet(
            id, prettyExercise(ex), who, fmt(weight), reps, inSheet = false,
            code = ex, date = date.toString()
        )

        if (scriptUrl.isBlank()) {
            enqueue(payload); remember(who, ex); resetForm()
            addToday(saved)
            return msg("Saved on phone — add the script URL in Settings to sync")
        }

        saving = true
        viewModelScope.launch {
            try {
                val r = call(payload)
                if (r.optBoolean("ok")) {
                    remember(who, ex); resetForm()
                    // Only now do we know the row is in the sheet; show what the script reports it wrote.
                    addToday(saved.confirmed(r))
                    syncPendingInternal(verbose = false)
                } else {
                    msg("Not saved — sheet said: ${r.optString("error")}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                enqueue(payload); remember(who, ex); resetForm()
                addToday(saved)
            } catch (e: Exception) {
                msg("Not saved — ${e.message ?: "something went wrong"}")
            } finally {
                saving = false
            }
        }
    }

    /**
     * Changes the weight/reps of a set from the Today list. Returns true when done.
     * Waiting sets are changed on the phone; synced sets are changed in the sheet.
     */
    suspend fun editSet(s: SavedSet, weightText: String, repsText: String): Boolean {
        val reps = repsText.toIntOrNull()
        val weight = if (weightText.isBlank()) 0.0 else weightText.replace(',', '.').toDoubleOrNull()
        if (reps == null || reps <= 0) { msg("Enter the number of reps"); return false }
        if (weight == null || weight < 0) { msg("Enter a valid weight"); return false }

        if (!s.inSheet) {
            val q = prefs.pending
            for (i in 0 until q.length()) {
                val p = q.getJSONObject(i)
                if (p.optLong("clientId") == s.id) {
                    p.put("sets", JSONArray().put(JSONObject().put("weight", weight).put("reps", reps)))
                }
            }
            prefs.pending = q
            updateToday(s.id) { it.copy(weight = fmt(weight), reps = reps) }
            msg("Changed (still waiting to sync)")
            return true
        }

        val r = sheetEdit(s, JSONObject().put("action", "update").put("weight", weight).put("reps", reps)) ?: return false
        updateToday(s.id) { it.copy(weight = fmt(weight), reps = reps, row = r.optInt("row").takeIf { n -> n > 0 } ?: it.row) }
        msg("Updated in sheet")
        return true
    }

    /** Removes a set from the Today list and from the sheet (or from the phone's queue). */
    suspend fun deleteSet(s: SavedSet): Boolean {
        if (!s.inSheet) {
            val q = prefs.pending
            val kept = JSONArray()
            for (i in 0 until q.length()) q.getJSONObject(i).let { if (it.optLong("clientId") != s.id) kept.put(it) }
            prefs.pending = kept
            pendingCount = kept.length()
            removeToday(s.id)
            msg("Deleted")
            return true
        }

        val r = sheetEdit(s, JSONObject().put("action", "delete")) ?: return false
        val deletedRow = r.optInt("deletedRow")
        removeToday(s.id)
        // Mirror the sheet: later sets of the same person/exercise/day move down one number,
        // and rows below the deleted one move up.
        for (i in today.indices) {
            val o = today[i]
            var n = o
            if (o.inSheet && o.person.equals(s.person, true) && o.code == s.code && o.date == s.date &&
                o.setNo != null && s.setNo != null && o.setNo > s.setNo) n = n.copy(setNo = o.setNo - 1)
            if (deletedRow > 0 && o.row != null && o.row > deletedRow) n = n.copy(row = o.row - 1)
            if (n != o) today[i] = n
        }
        persistToday()
        msg("Deleted from sheet")
        return true
    }

    /** Sends an update/delete for a synced set, identified by person + date + exercise + SetNo. */
    private suspend fun sheetEdit(s: SavedSet, body: JSONObject): JSONObject? {
        if (!s.editable) { msg("This set can't be edited from the app — change it in the sheet"); return null }
        body.put("person", s.person).put("exercise", s.code).put("date", s.date).put("setNo", s.setNo)
        return try {
            val r = call(body)
            if (r.optBoolean("ok")) r else { msg("Not changed — sheet said: ${r.optString("error")}"); null }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            msg("No connection — try again when you're online"); null
        } catch (e: Exception) {
            msg("Not changed — ${e.message ?: "something went wrong"}"); null
        }
    }

    fun syncPending() {
        viewModelScope.launch { syncPendingInternal(verbose = true) }
    }

    /** The sheet code for what's typed/selected in the Exercise field ("Leg Press" -> "leg_press"). */
    fun exerciseCode(): String = toExerciseCode(exercise, exercises)

    /** Shows what this person did last time for the selected exercise. */
    fun loadLast() {
        val ex = exerciseCode()
        val who = person.trim()
        lastJob?.cancel()
        if (ex.isEmpty() || who.isEmpty() || scriptUrl.isBlank()) { lastSession = null; return }
        lastJob = viewModelScope.launch {
            try {
                val r = call(JSONObject().put("action", "last").put("person", who).put("exercise", ex))
                val arr = r.optJSONArray("sets")
                lastSession = if (r.optBoolean("ok") && arr != null && arr.length() > 0) {
                    val list = (0 until arr.length()).joinToString(", ") {
                        val s = arr.getJSONObject(it)
                        "${fmt(s.optDouble("weight"))}×${s.optInt("reps")}"
                    }
                    "$who last time (${r.optString("date")}): $list"
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

    private suspend fun refreshMeta() {
        try {
            val r = call(JSONObject().put("action", "exercises"))
            if (r.optBoolean("ok")) mergeMeta(r)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // offline: keep the cached lists
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
                if (r.optBoolean("ok")) {
                    sent++
                    // Turn this set's Today entry green with what the sheet wrote.
                    val id = p.optLong("clientId")
                    updateToday(id) { it.confirmed(r) }
                } else { remaining.put(p); lastError = r.optString("error") }
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

    private fun mergeMeta(r: JSONObject) {
        exercises = merge(exercises, r.optJSONArray("exercises")?.toList().orEmpty())
        prefs.exercises = exercises
        people = merge(people, r.optJSONArray("people")?.toList().orEmpty())
        prefs.people = people
        if (person.isBlank() && people.size == 1) changePerson(people[0])
    }

    private fun remember(who: String, ex: String) {
        exercises = merge(exercises, listOf(ex)); prefs.exercises = exercises
        people = merge(people, listOf(who)); prefs.people = people
    }

    private fun merge(a: List<String>, b: List<String>): List<String> =
        (a + b).map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

    private fun JSONArray.toList(): List<String> = (0 until length()).map { getString(it) }

    /**
     * Keeps person, date and exercise (partners usually alternate sets on the same machine)
     * and clears only weight and reps. SetNo keeps counting up in the sheet.
     */
    private fun resetForm() {
        current = SetEntry()
        loadLast()
    }

    // ---- Today list ----

    private fun loadToday(): List<SavedSet> {
        if (prefs.todayDate != LocalDate.now().toString()) return emptyList()
        val arr = prefs.today
        return (0 until arr.length()).map { SavedSet.fromJson(arr.getJSONObject(it)) }
    }

    private fun addToday(s: SavedSet) {
        if (prefs.todayDate != LocalDate.now().toString()) today.clear()   // new day, fresh list
        today.add(0, s)
        persistToday()
    }

    private fun updateToday(id: Long, change: (SavedSet) -> SavedSet) {
        val i = today.indexOfFirst { it.id == id }
        if (i >= 0) { today[i] = change(today[i]); persistToday() }
    }

    private fun removeToday(id: Long) {
        today.removeAll { it.id == id }
        persistToday()
    }

    private fun persistToday() {
        prefs.today = JSONArray().apply { today.forEach { put(it.toJson()) } }
        prefs.todayDate = LocalDate.now().toString()
    }

    private fun msg(text: String) {
        messageChannel.trySend(text)
    }

    private fun fmt(d: Double): String =
        if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
}
