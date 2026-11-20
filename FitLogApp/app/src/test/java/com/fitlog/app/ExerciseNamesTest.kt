package com.fitlog.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseNamesTest {

    /**
     * The LiftData Exercise dropdown items. Items marked "cut off" were truncated in the
     * screenshot of the validation rule, so their endings here are a best guess.
     */
    private val sheet = listOf(
        "hip_adduction", "hip_abduction", "leg_press", "leg_curl", "leg_extension",
        "chest_press", "fly(machine)", "fly(cable)",
        "shoulder_press(dumbbell)",      // cut off
        "shoulder_press(machine)",       // cut off
        "lateral_raise(machine)",        // cut off
        "lateral_raise(dumbbell)",       // cut off
        "single_arm_tricep_pushdown",    // cut off
        "lat_pulldown", "row", "lat_pullover", "face_pull",
        "biceps_curl(dumbbell)",         // cut off
        "biceps_curl(barbell)", "hammer_curl", "abdominal",
        "triceps_pulldown", "triceps_pushdown", "pullups", "bench_press",
        "incline_dumbbell_press",        // cut off
    )

    @Test
    fun displayNamesOnlyChangeFormatting() {
        val expected = mapOf(
            "hip_adduction" to "Hip Adduction",
            "leg_press" to "Leg Press",
            "chest_press" to "Chest Press",
            "fly(machine)" to "Fly (Machine)",
            "fly(cable)" to "Fly (Cable)",
            "shoulder_press(dumbbell)" to "Shoulder Press (Dumbbell)",
            "lateral_raise(machine)" to "Lateral Raise (Machine)",
            "single_arm_tricep_pushdown" to "Single Arm Tricep Pushdown",
            "row" to "Row",
            "biceps_curl(barbell)" to "Biceps Curl (Barbell)",
            "biceps_curl(dumbbell)" to "Biceps Curl (Dumbbell)",
            "abdominal" to "Abdominal",
            "pullups" to "Pullups",
            "bench_press" to "Bench Press",
            "incline_dumbbell_press" to "Incline Dumbbell Press",
        )
        expected.forEach { (code, pretty) -> assertEquals(code, pretty, prettyExercise(code)) }
    }

    @Test
    fun everyDisplayNameMapsBackToItsExactCode() {
        sheet.forEach { code ->
            assertEquals(code, toExerciseCode(prettyExercise(code), sheet))
            assertEquals(code, toExerciseCode(prettyExercise(code).lowercase(), sheet))
        }
    }

    @Test
    fun displayNamesAreUnique() {
        assertEquals(sheet.size, sheet.map(::prettyExercise).toSet().size)
    }

    @Test
    fun newExercisesFollowTheSheetStyle() {
        assertEquals("fly(cable)", toExerciseCode("Fly (Cable)", emptyList()))
        assertEquals("cable_crunch", toExerciseCode("Cable Crunch", emptyList()))
        assertEquals("shoulder_press(machine)", toExerciseCode("shoulder press ( machine )", emptyList()))
    }
}
