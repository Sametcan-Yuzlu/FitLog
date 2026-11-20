package com.fitlog.app

/*
 * The sheet stores exercises as codes like "leg_press" or "biceps_curl(barbell)".
 * The app shows them as "Leg Press" / "Biceps Curl (Barbell)" and sends the exact code back.
 * Display only changes formatting — words are never added, removed, renamed or reordered.
 */

/**
 * "leg_press" -> "Leg Press", "fly(machine)" -> "Fly (Machine)",
 * "biceps_curl(barbell)" -> "Biceps Curl (Barbell)".
 */
fun prettyExercise(code: String): String {
    val spaced = code.trim()
        .replace('_', ' ')
        .replace(Regex("\\s*\\(\\s*"), " (")   // one space before "(", none after
        .replace(Regex("\\s*\\)"), ")")
        .replace(Regex("\\s+"), " ")
        .trim()
    // Capitalise the first letter of each word, including the word right after "(".
    return spaced.replace(Regex("(^|[ (])([a-z])")) { m -> m.groupValues[1] + m.groupValues[2].uppercase() }
}

/** The sheet's style: "Fly (Cable)" -> "fly(cable)", "Leg Press" -> "leg_press". */
fun normExercise(s: String): String =
    s.trim().lowercase()
        .replace(Regex("\\s*\\(\\s*"), "(")
        .replace(Regex("\\s*\\)"), ")")
        .replace(Regex("[\\s\\-]+"), "_")

/**
 * Converts what's in the Exercise field back to the sheet's code. Known exercises are matched by
 * their code or their display name, so the original code is always kept exactly; anything new is
 * written in the same style as the sheet.
 */
fun toExerciseCode(text: String, known: List<String>): String {
    val t = text.trim()
    if (t.isEmpty()) return ""
    known.firstOrNull { it.equals(t, ignoreCase = true) || prettyExercise(it).equals(t, ignoreCase = true) }
        ?.let { return it }
    return normExercise(t)
}
