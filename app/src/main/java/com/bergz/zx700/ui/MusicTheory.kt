package com.bergz.zx700.ui

/** A scale as semitone offsets from its key note. */
data class Scale(val name: String, val intervals: List<Int>)

val KEY_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** Every scale the panel offers. C MAJOR (index 0, key 0) is the start-up default. */
val SCALES = listOf(
    Scale("MAJOR", listOf(0, 2, 4, 5, 7, 9, 11)),
    Scale("MINOR", listOf(0, 2, 3, 5, 7, 8, 10)),
    Scale("HARM MINOR", listOf(0, 2, 3, 5, 7, 8, 11)),
    Scale("MEL MINOR", listOf(0, 2, 3, 5, 7, 9, 11)),
    Scale("DORIAN", listOf(0, 2, 3, 5, 7, 9, 10)),
    Scale("PHRYGIAN", listOf(0, 1, 3, 5, 7, 8, 10)),
    Scale("LYDIAN", listOf(0, 2, 4, 6, 7, 9, 11)),
    Scale("MIXOLYDIAN", listOf(0, 2, 4, 5, 7, 9, 10)),
    Scale("LOCRIAN", listOf(0, 1, 3, 5, 6, 8, 10)),
    Scale("MAJ PENTA", listOf(0, 2, 4, 7, 9)),
    Scale("MIN PENTA", listOf(0, 3, 5, 7, 10)),
    Scale("BLUES", listOf(0, 3, 5, 6, 7, 10)),
    Scale("WHOLE TONE", listOf(0, 2, 4, 6, 8, 10)),
    Scale("HUNGARIAN", listOf(0, 2, 3, 6, 7, 8, 11)),
    Scale("DBL HARMONIC", listOf(0, 1, 4, 5, 7, 8, 11)),
    Scale("JAPANESE", listOf(0, 1, 5, 7, 8)),
    Scale("CHROMATIC", (0..11).toList()),
)

/** Patch patterns are written in C natural minor; they are conformed from this on load. */
const val PRESET_KEY = 0
val PRESET_SCALE_INDEX = SCALES.indexOfFirst { it.name == "MINOR" }

private const val LOWEST = 24
private const val HIGHEST = 100

private fun pitchClass(n: Int, key: Int) = ((n - key) % 12 + 12) % 12

fun inScale(n: Int, key: Int, scale: Scale) = scale.intervals.contains(pitchClass(n, key))

/** Nearest note in the key and scale (ties go down). */
fun snapToScale(n: Int, key: Int, scale: Scale): Int {
    for (d in 0..6) {
        if (inScale(n - d, key, scale)) return n - d
        if (inScale(n + d, key, scale)) return n + d
    }
    return n
}

/** Move a note up or down by whole scale steps. */
fun moveInScale(n: Int, degrees: Int, key: Int, scale: Scale): Int {
    var x = snapToScale(n, key, scale)
    repeat(kotlin.math.abs(degrees)) {
        do { x += if (degrees > 0) 1 else -1 } while (!inScale(x, key, scale) && x in LOWEST..HIGHEST)
    }
    return x.coerceIn(LOWEST, HIGHEST)
}

/**
 * Re-fit a note from one key and scale to another, keeping its role: the 3rd of
 * the old scale becomes the 3rd of the new one, so a C minor 7 chord turns into
 * C major 7 in C Major, or A minor 7 in A Minor. Scales with a different number
 * of notes fall back to the nearest note. The key moves by at most a tritone so
 * patterns stay in the same register.
 */
fun conformNote(n: Int, fromKey: Int, from: Scale, toKey: Int, to: Scale): Int {
    if (n < 0) return n
    var shift = ((toKey - fromKey) % 12 + 12) % 12
    if (shift > 6) shift -= 12
    val rel = n - fromKey
    val degree = from.intervals.indexOf(pitchClass(n, fromKey))
    val out = if (from.intervals.size == to.intervals.size && degree >= 0) {
        fromKey + shift + 12 * Math.floorDiv(rel, 12) + to.intervals[degree]
    } else {
        snapToScale(n + shift, toKey, to)
    }
    return out.coerceIn(LOWEST, HIGHEST)
}

fun conformSteps(steps: List<List<Int>>, fromKey: Int, from: Scale, toKey: Int, to: Scale): List<List<Int>> =
    steps.map { step -> step.map { conformNote(it, fromKey, from, toKey, to) } }

fun keyAndScaleName(key: Int, scale: Scale) = "${KEY_NAMES[key]} ${scale.name}"
