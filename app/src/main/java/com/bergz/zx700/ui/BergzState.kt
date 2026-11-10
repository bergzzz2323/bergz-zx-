package com.bergz.zx700.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.bergz.zx700.audio.NativeEngine
import com.bergz.zx700.audio.NativeEngine.P
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Everything the panel shows, plus every action a control can trigger.
 * The UI keeps its own copy of the pattern; the engine is told about every
 * change, so both always agree. Knob values are refreshed from the engine
 * each frame so recorded motion visibly turns the knobs.
 */
class BergzState {
    private val emptyStep = listOf(-1, -1, -1, -1)

    val params = mutableStateListOf<Float>().apply { repeat(P.COUNT) { add(0f) } }
    val notes = mutableStateListOf<List<Int>>().apply { repeat(NativeEngine.STEPS) { add(emptyStep) } }
    val drums: List<SnapshotStateList<Boolean>> = List(NativeEngine.DRUM_LANES) {
        mutableStateListOf<Boolean>().apply { repeat(NativeEngine.STEPS) { add(false) } }
    }

    var audioOk by mutableStateOf(true)
    var playing by mutableStateOf(false)
        private set
    var recording by mutableStateOf(false)
        private set
    var playhead by mutableIntStateOf(-1)
        private set
    var motionMask by mutableIntStateOf(0)
        private set
    var presetName by mutableStateOf("")
        private set
    var presetIndex by mutableIntStateOf(0)
        private set
    var edited by mutableStateOf(false)
        private set
    var track by mutableIntStateOf(0)       // 0 synth, 1..4 drum lanes
        private set
    var shift by mutableStateOf(false)
    var selectedStep by mutableIntStateOf(-1)
        private set
    var scaleIndex by mutableIntStateOf(0)
        private set
    var message by mutableStateOf("PRESS PLAY")
        private set
    var morph by mutableFloatStateOf(0f)
        private set

    private var sceneA = FloatArray(P.MOTION_COUNT)
    private var sceneB = FloatArray(P.MOTION_COUNT)
    private val touched = BooleanArray(P.COUNT)
    private val localSetAt = LongArray(P.COUNT)
    private val tapTimes = ArrayDeque<Long>()
    private var root = 48

    val scale: Scale get() = SCALES[scaleIndex]
    val fxType: Int get() = params[P.FX_TYPE].roundToInt().coerceIn(0, 2)
    val tempo: Int get() = params[P.TEMPO].roundToInt()

    // ---------------------------------------------------------------- presets
    fun loadPreset(index: Int) {
        val count = NativeEngine.nativePresetCount()
        if (count == 0) return
        val i = ((index % count) + count) % count
        presetIndex = i
        presetName = NativeEngine.nativePresetName(i)
        NativeEngine.nativeLoadPreset(i)

        val p = NativeEngine.nativePresetParams(i)
        val now = SystemClock.uptimeMillis()
        for (k in 0 until P.COUNT) { params[k] = p[k]; localSetAt[k] = now }
        val n = NativeEngine.nativePresetNotes(i)
        for (s in 0 until NativeEngine.STEPS)
            notes[s] = List(NativeEngine.NOTES_PER_STEP) { n[s * NativeEngine.NOTES_PER_STEP + it] }
        val d = NativeEngine.nativePresetDrums(i)
        for (l in 0 until NativeEngine.DRUM_LANES)
            for (s in 0 until NativeEngine.STEPS) drums[l][s] = d[l * NativeEngine.STEPS + s] != 0
        root = notes.firstOrNull { it[0] >= 0 }?.get(0) ?: 48

        sceneA = FloatArray(P.MOTION_COUNT) { params[it] }
        sceneB = variation(sceneA)
        morph = 0f
        selectedStep = -1
        edited = false
        message = "PATCH ${i + 1}/$count"
    }

    fun nextPatch() { if (consumeShift()) loadPreset(presetIndex - 1) else loadPreset(presetIndex + 1) }

    // ---------------------------------------------------------------- knobs
    fun setParam(id: Int, value: Float) {
        params[id] = value
        localSetAt[id] = SystemClock.uptimeMillis()
        NativeEngine.nativeSetParam(id, value)
        edited = true
        PARAM_NAMES[id]?.let { message = "${it.friendly} · ${it.technical} ${(value * 100).roundToInt()}" }
    }

    fun touch(id: Int, down: Boolean) {
        touched[id] = down
        NativeEngine.nativeTouch(id, down)
    }

    /** Called once per frame: playhead, motion LEDs and knobs follow the engine. */
    fun poll() {
        playhead = NativeEngine.nativePlayhead()
        motionMask = NativeEngine.nativeMotionMask()
        val now = SystemClock.uptimeMillis()
        for (i in 0 until P.COUNT) {
            if (touched[i] || now - localSetAt[i] < 250) continue
            val v = NativeEngine.nativeParam(i)
            if (abs(v - params[i]) > 1e-4f) params[i] = v
        }
    }

    // ---------------------------------------------------------------- transport
    fun play() {
        if (!playing) { NativeEngine.nativePlay(); playing = true; message = "PLAYING" }
    }

    fun stop() {
        if (recording) toggleRecord()
        NativeEngine.nativeStopSequencer()
        playing = false
        message = "STOPPED"
    }

    fun toggleRecord() {
        recording = !recording
        NativeEngine.nativeSetRecord(recording)
        if (recording) { play(); message = "REC · TURN ANY KNOB" } else message = "MOTION SAVED"
    }

    fun clearMotion() {
        NativeEngine.nativeClearMotion(-1)
        message = "MOTION CLEARED"
    }

    fun tapTempo() {
        val now = SystemClock.uptimeMillis()
        if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2000) tapTimes.clear()
        tapTimes.addLast(now)
        while (tapTimes.size > 4) tapTimes.removeFirst()
        if (tapTimes.size >= 2) {
            val avg = (tapTimes.last() - tapTimes.first()).toFloat() / (tapTimes.size - 1)
            setTempo((60000f / avg).roundToInt())
        } else message = "TAP AGAIN"
    }

    private fun setTempo(bpm: Int) {
        val v = bpm.coerceIn(40, 240).toFloat()
        params[P.TEMPO] = v
        localSetAt[P.TEMPO] = SystemClock.uptimeMillis()
        NativeEngine.nativeSetParam(P.TEMPO, v)
        message = "TEMPO ${v.roundToInt()} BPM"
    }

    // ---------------------------------------------------------------- buttons
    fun algoOrScale() {
        if (consumeShift()) {
            scaleIndex = (scaleIndex + 1) % SCALES.size
            message = "SCALE C ${scale.name}"
        } else {
            val a = (params[P.ALGORITHM].roundToInt() + 1) % NativeEngine.ALGORITHMS
            params[P.ALGORITHM] = a.toFloat()
            localSetAt[P.ALGORITHM] = SystemClock.uptimeMillis()
            NativeEngine.nativeSetParam(P.ALGORITHM, a.toFloat())
            edited = true
            message = "ALGORITHM ${a + 1}"
        }
    }

    fun cycleFx() {
        val t = (fxType + 1) % NativeEngine.FX_NAMES.size
        params[P.FX_TYPE] = t.toFloat()
        localSetAt[P.FX_TYPE] = SystemClock.uptimeMillis()
        NativeEngine.nativeSetParam(P.FX_TYPE, t.toFloat())
        edited = true
        message = "FX ${NativeEngine.FX_NAMES[t]}"
    }

    fun cycleTrack() {
        track = (track + 1) % TRACK_NAMES.size
        selectedStep = -1
        message = "EDIT ${TRACK_NAMES[track]}"
    }

    fun toggleShift() { shift = !shift }
    private fun consumeShift(): Boolean { val s = shift; shift = false; return s }

    // ---------------------------------------------------------------- steps
    /** Synth track: tap empty = add note, tap = select, tap selected = clear. Drum tracks: toggle. */
    fun stepPressed(step: Int) {
        if (track > 0) {
            val lane = track - 1
            val on = !drums[lane][step]
            drums[lane][step] = on
            NativeEngine.nativeSetDrum(lane, step, on)
            message = "${TRACK_NAMES[track]} STEP ${step + 1} ${if (on) "ON" else "OFF"}"
            return
        }
        val current = notes[step]
        when {
            current[0] < 0 -> {
                writeStep(step, listOf(snap(root), -1, -1, -1))
                selectedStep = step
                message = "STEP ${step + 1} ${noteName(snap(root))} · TURN DATA"
            }
            selectedStep == step -> {
                writeStep(step, emptyStep)
                selectedStep = -1
                message = "STEP ${step + 1} CLEARED"
            }
            else -> {
                selectedStep = step
                message = "STEP ${step + 1} ${noteName(current[0])} · TURN DATA"
            }
        }
    }

    /** Data wheel: moves the selected step through the scale, otherwise sets tempo. */
    fun dataWheel(delta: Int) {
        val s = selectedStep
        if (track == 0 && s >= 0 && notes[s][0] >= 0) {
            val oldRoot = notes[s][0]
            val newRoot = moveInScale(oldRoot, delta)
            val shiftBy = newRoot - oldRoot
            writeStep(s, notes[s].map { if (it >= 0) (it + shiftBy).coerceIn(24, 100) else -1 })
            message = "STEP ${s + 1} ${noteName(newRoot)} (${scale.name})"
        } else {
            setTempo(tempo + delta)
        }
    }

    private fun writeStep(step: Int, value: List<Int>) {
        notes[step] = value
        for (slot in 0 until NativeEngine.NOTES_PER_STEP) NativeEngine.nativeSetStepNote(step, slot, value[slot])
        edited = true
    }

    // ---------------------------------------------------------------- scale lock
    private fun inScale(n: Int) = scale.intervals.contains(((n % 12) + 12) % 12)

    private fun snap(n: Int): Int {
        for (d in 0..6) {
            if (inScale(n - d)) return n - d
            if (inScale(n + d)) return n + d
        }
        return n
    }

    private fun moveInScale(n: Int, degrees: Int): Int {
        var x = snap(n)
        repeat(abs(degrees)) {
            do { x += if (degrees > 0) 1 else -1 } while (!inScale(x))
        }
        return x.coerceIn(24, 100)
    }

    // ---------------------------------------------------------------- mutate
    /** New sound from the current one, kept within musical limits. SHIFT: new melody instead. */
    fun mutate() {
        if (consumeShift()) { mutateMelody(); return }
        val r = Random(SystemClock.uptimeMillis())
        val limits = mapOf(
            P.FM_DEPTH to (0.1f to 0.95f), P.HARMONIC to (0f to 1f), P.FEEDBACK to (0f to 0.6f),
            P.CUTOFF to (0.25f to 0.95f), P.RESO to (0f to 0.75f), P.ENV_AMT to (0f to 0.9f),
            P.FX_MIX to (0.1f to 0.7f), P.FX_PARAM to (0.1f to 0.9f), P.DECAY to (0.1f to 0.9f),
            P.SUSTAIN to (0f to 0.9f), P.LFO_RATE to (0f to 0.8f), P.LFO_DEPTH to (0f to 0.5f),
        )
        for ((id, range) in limits) {
            val v = (params[id] + r.nextFloat() * 0.5f - 0.25f).coerceIn(range.first, range.second)
            setParam(id, v)
        }
        if (r.nextFloat() < 0.3f) {
            val a = r.nextInt(NativeEngine.ALGORITHMS).toFloat()
            params[P.ALGORITHM] = a
            NativeEngine.nativeSetParam(P.ALGORITHM, a)
        }
        sceneA = FloatArray(P.MOTION_COUNT) { params[it] }
        sceneB = variation(sceneA)
        morph = 0f
        message = "MUTATED · TAP AGAIN FOR MORE"
    }

    private fun mutateMelody() {
        val r = Random(SystemClock.uptimeMillis())
        for (s in 0 until NativeEngine.STEPS) {
            val cur = notes[s]
            if (cur[0] < 0) continue
            val newRoot = moveInScale(root, r.nextInt(-2, 8))
            val shiftBy = newRoot - cur[0]
            writeStep(s, cur.map { if (it >= 0) (it + shiftBy).coerceIn(24, 100) else -1 })
        }
        message = "NEW MELODY IN C ${scale.name}"
    }

    // ---------------------------------------------------------------- scenes
    private fun variation(a: FloatArray): FloatArray {
        val b = a.copyOf()
        fun bump(id: Int, by: Float) { b[id] = (b[id] + by).coerceIn(0f, 1f) }
        bump(P.CUTOFF, 0.3f); bump(P.FM_DEPTH, -0.25f); bump(P.HARMONIC, 0.2f)
        bump(P.FX_MIX, 0.25f); bump(P.LFO_DEPTH, 0.3f); bump(P.RESO, 0.15f)
        return b
    }

    fun scenePressed(isB: Boolean) {
        if (consumeShift()) {
            val snap = FloatArray(P.MOTION_COUNT) { params[it] }
            if (isB) sceneB = snap else sceneA = snap
            message = "SCENE ${if (isB) "B" else "A"} STORED"
        } else {
            morphTouch(true); morphTo(if (isB) 1f else 0f); morphTouch(false)
            message = "SCENE ${if (isB) "B" else "A"}"
        }
    }

    /** Finger on the crossfader counts as touching every knob it moves, so it records too. */
    fun morphTouch(down: Boolean) {
        for (i in 0 until P.MOTION_COUNT) if (sceneA[i] != sceneB[i]) touch(i, down)
    }

    fun morphTo(x: Float) {
        morph = x
        for (i in 0 until P.MOTION_COUNT) {
            val v = sceneA[i] + (sceneB[i] - sceneA[i]) * x
            if (abs(v - params[i]) > 1e-4f) {
                params[i] = v
                localSetAt[i] = SystemClock.uptimeMillis()
                NativeEngine.nativeSetParam(i, v)
            }
        }
        message = "MORPH ${(x * 100).roundToInt()}%"
    }

    fun motionNames(): String {
        val names = (0 until P.MOTION_COUNT).filter { motionMask and (1 shl it) != 0 }
            .mapNotNull { PARAM_NAMES[it]?.friendly }
        return if (names.isEmpty()) "NO MOTION YET" else names.joinToString(" · ")
    }
}
