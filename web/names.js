// Exercise names — same rules as the Android app (ExerciseNames.kt).
// The sheet stores codes like "leg_press" or "biceps_curl(barbell)"; the app shows
// "Leg Press" / "Biceps Curl (Barbell)" and always sends the exact code back.

/** "fly(machine)" -> "Fly (Machine)": underscores to spaces, space before "(", capitalise words. */
export function prettyExercise(code) {
  const spaced = String(code).trim()
    .replace(/_/g, ' ')
    .replace(/\s*\(\s*/g, ' (')
    .replace(/\s*\)/g, ')')
    .replace(/\s+/g, ' ')
    .trim();
  return spaced.replace(/(^|[ (])([a-z])/g, (_, pre, ch) => pre + ch.toUpperCase());
}

/** The sheet's style: "Fly (Cable)" -> "fly(cable)", "Leg Press" -> "leg_press". */
export function normExercise(s) {
  return String(s).trim().toLowerCase()
    .replace(/\s*\(\s*/g, '(')
    .replace(/\s*\)/g, ')')
    .replace(/[\s-]+/g, '_');
}

/** What's in the Exercise field -> the sheet code. Known exercises keep their exact code. */
export function toExerciseCode(text, known) {
  const t = String(text).trim();
  if (!t) return '';
  const lower = t.toLowerCase();
  const hit = known.find(k => k.toLowerCase() === lower || prettyExercise(k).toLowerCase() === lower);
  return hit !== undefined ? hit : normExercise(t);
}
