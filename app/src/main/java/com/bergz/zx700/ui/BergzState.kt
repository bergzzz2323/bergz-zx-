package com.bergz.zx700.ui

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bergz.zx700.audio.NativeEngine
import com.bergz.zx700.audio.NativeEngine.P
import com.bergz.zx700.midi.MidiStatus
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

data class PresetInfo(val index: Int, val name: String, val category: Int)

/**
 * What a patch looked like when you left it: both scenes, the slider, its steps
 * (with the key/scale they were in), algorithm and tempo. Kept in memory only,
 * so every patch is back to its default when the app is closed and reopened.
 */
private class PatchSession(
    val sceneA: FloatArray,
    val sceneB: FloatArray,
    val morph: Float,
    val notes: List<List<Int>>,
    val key: Int,
    val scaleIndex: Int,
    val algorithm: Float,
    val tempo: Float,
)

/**
 * Everything the panel shows, plus every action a control can trigger.
 * The UI keeps its own copy of the pattern; the engine is told about every
 * change, so both always agree. Knob values are refreshed from the engine
 * each frame so recorded motion visibly turns the knobs.
 */
class BergzState {
    private val emptyStep = listOf(-1, -1, -1, -1)

    /** The patch library, read once from the engine's tables. */
    val library: List<PresetInfo> by lazy {
        (0 until NativeEngine.nativePresetCount()).map {
            PresetInfo(it, NativeEngine.nativePresetName(it), NativeEngine.nativePresetCategory(it))
        }
    }
    val categories: List<String> by lazy {
        (0 until NativeEngine.nativeCategoryCount()).map { NativeEngine.nativeCategoryName(it) }
    }

    val params = mutableStateListOf<Float>().apply { repeat(P.COUNT) { add(0f) } }
    val notes = mutableStateListOf<List<Int>>().apply { repeat(NativeEngine.STEPS) { add(emptyStep) } }

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
    var shift by mutableStateOf(false)
    var selectedStep by mutableIntStateOf(-1)
        private set
    var key by mutableIntStateOf(0)
        private set
    var scaleIndex by mutableIntStateOf(0)
        private set
    var message by mutableStateOf("PRESS PLAY")
        private set
    var morph by mutableFloatStateOf(0f)
        private set

    /** Which effect the FX knobs are showing (0 delay, 1 reverb, 2 phaser). */
    var fxSelected by mutableIntStateOf(0)
        private set
    var fxSettingsOpen by mutableStateOf(false)
    var keyPickerOpen by mutableStateOf(false)
    var browserOpen by mutableStateOf(false)
        private set
    var browserCategory by mutableIntStateOf(0)

    var externalSync by mutableStateOf(false)
        private set
    var midiActive by mutableStateOf(false)
        private set
    var outputName by mutableStateOf("PHONE")
    var midiSources: List<String> = emptyList()
    private var transportSetAt = 0L

    private var sceneA = FloatArray(P.MOTION_COUNT)
    private var sceneB = FloatArray(P.MOTION_COUNT)
    private val sessions = HashMap<Int, PatchSession>()
    private var patchLoaded = false
    private val touched = BooleanArray(P.COUNT)
    private val localSetAt = LongArray(P.COUNT)
    private val tapTimes = ArrayDeque<Long>()

    val scale: Scale get() = SCALES[scaleIndex]
    val keyScaleName: String get() = keyAndScaleName(key, scale)
    val fx: NativeEngine.Fx get() = NativeEngine.FX[fxSelected]
    val tempo: Int get() = params[P.TEMPO].roundToInt()
    fun fxOn(index: Int): Boolean = params[NativeEngine.FX[index].on] > 0.5f

    // ---------------------------------------------------------------- start-up
    /**
     * Called when the app opens: every setting back to default (delay on, reverb
     * and phaser off, C Major), session memory empty, first patch loaded.
     */
    fun startFresh() {
        NativeEngine.nativeStopSequencer()
        NativeEngine.nativeSetRecord(false)
        NativeEngine.nativeResetDefaults()
        val now = SystemClock.uptimeMillis()
        for (k in 0 until P.COUNT) { params[k] = NativeEngine.nativeDefaultParam(k); localSetAt[k] = now }
        sessions.clear()
        patchLoaded = false
        key = 0
        scaleIndex = 0  // C MAJOR
        fxSelected = 0  // DELAY
        playing = false
        recording = false
        loadPreset(0)
        message = "PRESS PLAY"
    }

    // ---------------------------------------------------------------- presets
    private fun saveSession() {
        if (!patchLoaded) return
        sessions[presetIndex] = PatchSession(
            sceneA.copyOf(), sceneB.copyOf(), morph, notes.toList(), key, scaleIndex,
            params[P.ALGORITHM], params[P.TEMPO],
        )
    }

    fun loadPreset(index: Int) {
        val count = NativeEngine.nativePresetCount()
        if (count == 0) return
        val i = ((index % count) + count) % count
        saveSession()

        presetIndex = i
        presetName = NativeEngine.nativePresetName(i)
        NativeEngine.nativeLoadPreset(i)

        val p = NativeEngine.nativePresetParams(i)
        val now = SystemClock.uptimeMillis()
        for (k in 0 until P.PRESET_COUNT) { params[k] = p[k]; localSetAt[k] = now }

        val saved = sessions[i]
        val steps: List<List<Int>>
        if (saved != null) {
            // Back to a patch you already tweaked this session: restore it.
            sceneA = saved.sceneA.copyOf()
            sceneB = saved.sceneB.copyOf()
            morph = saved.morph
            applyScenes()
            setRaw(P.ALGORITHM, saved.algorithm)
            setRaw(P.TEMPO, saved.tempo)
            steps = conformSteps(saved.notes, saved.key, SCALES[saved.scaleIndex], key, scale)
        } else {
            sceneA = FloatArray(P.MOTION_COUNT) { params[it] }
            sceneB = variation(sceneA)
            morph = 0f
            val n = NativeEngine.nativePresetNotes(i)
            val raw = List(NativeEngine.STEPS) { s -> List(NativeEngine.NOTES_PER_STEP) { n[s * NativeEngine.NOTES_PER_STEP + it] } }
            steps = conformSteps(raw, PRESET_KEY, SCALES[PRESET_SCALE_INDEX], key, scale)
        }
        for (s in 0 until NativeEngine.STEPS) sendStep(s, steps[s])

        patchLoaded = true
        selectedStep = -1
        edited = saved != null
        message = if (saved != null) "$presetName · YOUR TWEAKS" else "$presetName · ${keyScaleName}"
    }

    /** Category and position, e.g. "BASS 2/5". */
    val presetLabel: String get() {
        val info = library.getOrNull(presetIndex) ?: return ""
        val inCat = library.filter { it.category == info.category }
        return "${categories.getOrElse(info.category) { "" }} ${inCat.indexOf(info) + 1}/${inCat.size}"
    }

    /** PATCH opens the browser; SHIFT + PATCH jumps straight to the next patch. */
    fun patchPressed() {
        if (consumeShift()) { loadPreset(presetIndex + 1); return }
        browserCategory = library.getOrNull(presetIndex)?.category ?: 0
        browserOpen = true
        message = "PICK A SOUND"
    }

    fun closeBrowser() { browserOpen = false }

    // ---------------------------------------------------------------- knobs
    /** A knob turned by hand. Also updates scene A or B (see [editScene]). */
    fun setParam(id: Int, value: Float) {
        val old = params[id]
        setRaw(id, value)
        if (id < P.MOTION_COUNT) editScene(id, value, old)
        edited = true
        PARAM_NAMES[id]?.let { message = "${it.friendly} · ${it.technical} ${(value * 100).roundToInt()}" }
    }

    /** Set a parameter on screen and in the engine, without touching the scenes. */
    private fun setRaw(id: Int, value: Float) {
        params[id] = value
        localSetAt[id] = SystemClock.uptimeMillis()
        NativeEngine.nativeSetParam(id, value)
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
        // External MIDI Start/Stop can change the transport too.
        if (now - transportSetAt > 250) {
            val p = NativeEngine.nativeIsPlaying()
            if (p != playing) {
                playing = p
                if (!p && recording) { recording = false; NativeEngine.nativeSetRecord(false) }
            }
        }
        externalSync = NativeEngine.nativeExternalSync()
        midiActive = now - MidiStatus.lastEventAt < 150
        for (i in 0 until P.COUNT) {
            if (touched[i] || now - localSetAt[i] < 250) continue
            val v = NativeEngine.nativeParam(i)
            if (abs(v - params[i]) > 1e-4f) params[i] = v
        }
    }

    // ---------------------------------------------------------------- transport
    fun play() {
        if (!playing) { NativeEngine.nativePlay(); playing = true; transportSetAt = SystemClock.uptimeMillis(); message = "PLAYING" }
    }

    fun stop() {
        if (recording) toggleRecord()
        NativeEngine.nativeStopSequencer()
        playing = false
        transportSetAt = SystemClock.uptimeMillis()
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
        setRaw(P.TEMPO, v)
        message = "TEMPO ${v.roundToInt()} BPM"
    }

    // ---------------------------------------------------------------- buttons
    /** ALGO cycles the FM algorithm; SHIFT + ALGO opens KEY & SCALE. */
    fun algoOrKey() {
        if (consumeShift()) { openKeyPicker(); return }
        val a = (params[P.ALGORITHM].roundToInt() + 1) % NativeEngine.ALGORITHMS
        setRaw(P.ALGORITHM, a.toFloat())
        edited = true
        message = "ALGORITHM ${a + 1}"
    }

    fun toggleShift() { shift = !shift }
    private fun consumeShift(): Boolean { val s = shift; shift = false; return s }

    // ---------------------------------------------------------------- effects
    /** Tap the FX name: show the next effect's knobs. Effects that are on keep playing. */
    fun cycleFx() {
        fxSelected = (fxSelected + 1) % NativeEngine.FX.size
        message = "${fx.name} · ${if (fxOn(fxSelected)) "ON" else "OFF"}"
    }

    /** The light next to the FX name: switch the shown effect on or off. */
    fun toggleFx(index: Int = fxSelected) {
        val e = NativeEngine.FX[index]
        val on = !fxOn(index)
        setRaw(e.on, if (on) 1f else 0f)
        val stack = NativeEngine.FX.indices.filter { fxOn(it) }.joinToString(" + ") { NativeEngine.FX[it].name }
        message = if (stack.isEmpty()) "ALL FX OFF" else stack
    }

    fun openFxSettings() {
        fxSettingsOpen = true
        message = "${fx.name} SETTINGS"
    }

    fun closeFxSettings() { fxSettingsOpen = false }

    fun setDelayTime(index: Int) {
        setRaw(P.DLY_TIME, index / 3f)
        message = "DELAY TIME ${NativeEngine.DELAY_TIMES[index]}"
    }

    val delayTimeIndex: Int get() = (params[P.DLY_TIME] * 3f).roundToInt().coerceIn(0, 3)

    fun togglePingPong() {
        val v = if (params[P.DLY_PING] > 0.5f) 0f else 1f
        setRaw(P.DLY_PING, v)
        message = if (v > 0.5f) "PING-PONG ON" else "PING-PONG OFF"
    }

    // ---------------------------------------------------------------- key & scale
    fun openKeyPicker() {
        keyPickerOpen = true
        message = keyScaleName
    }

    fun closeKeyPicker() { keyPickerOpen = false }

    /** Change key and/or scale: the current pattern moves with it, note by note. */
    fun setKeyScale(newKey: Int, newScaleIndex: Int) {
        if (newKey == key && newScaleIndex == scaleIndex) return
        val converted = conformSteps(notes.toList(), key, scale, newKey, SCALES[newScaleIndex])
        key = newKey
        scaleIndex = newScaleIndex
        for (s in 0 until NativeEngine.STEPS) if (converted[s] != notes[s]) sendStep(s, converted[s])
        message = keyScaleName
    }

    // ---------------------------------------------------------------- steps
    /** Where new notes start: the pattern's first note, or the key note around C3. */
    private val homeNote: Int get() {
        val first = notes.firstOrNull { it[0] >= 0 }?.get(0)
        return first ?: (48 + if (key > 6) key - 12 else key)
    }

    /** Tap empty = add note, tap = select, tap selected = clear. */
    fun stepPressed(step: Int) {
        val current = notes[step]
        when {
            current[0] < 0 -> {
                val n = snapToScale(homeNote, key, scale)
                writeStep(step, listOf(n, -1, -1, -1))
                selectedStep = step
                message = "STEP ${step + 1} ${noteName(n)} · TURN DATA"
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
        if (s >= 0 && notes[s][0] >= 0) {
            val oldRoot = notes[s][0]
            val newRoot = moveInScale(oldRoot, delta, key, scale)
            val shiftBy = newRoot - oldRoot
            writeStep(s, notes[s].map { if (it >= 0) (it + shiftBy).coerceIn(24, 100) else -1 })
            message = "STEP ${s + 1} ${noteName(newRoot)} · $keyScaleName"
        } else {
            setTempo(tempo + delta)
        }
    }

    private fun writeStep(step: Int, value: List<Int>) {
        sendStep(step, value)
        edited = true
    }

    private fun sendStep(step: Int, value: List<Int>) {
        notes[step] = value
        for (slot in 0 until NativeEngine.NOTES_PER_STEP) NativeEngine.nativeSetStepNote(step, slot, value[slot])
    }

    // ---------------------------------------------------------------- mutate
    /** New sound from the current one, kept within musical limits. SHIFT: new melody instead. */
    fun mutate() {
        if (consumeShift()) { mutateMelody(); return }
        val r = Random(SystemClock.uptimeMillis())
        val limits = mapOf(
            P.FM_DEPTH to (0.1f to 0.95f), P.HARMONIC to (0f to 1f), P.FEEDBACK to (0f to 0.6f),
            P.CUTOFF to (0.25f to 0.95f), P.RESO to (0f to 0.75f), P.ENV_AMT to (0f to 0.9f),
            P.DECAY to (0.1f to 0.9f), P.SUSTAIN to (0f to 0.9f), P.LFO_RATE to (0f to 0.8f),
            P.LFO_DEPTH to (0f to 0.5f), fx.mix to (0.1f to 0.7f), fx.param to (0.1f to 0.9f),
        )
        for ((id, range) in limits) {
            val v = (params[id] + r.nextFloat() * 0.5f - 0.25f).coerceIn(range.first, range.second)
            setParam(id, v)
        }
        if (r.nextFloat() < 0.3f) setRaw(P.ALGORITHM, r.nextInt(NativeEngine.ALGORITHMS).toFloat())
        message = "MUTATED · TAP AGAIN FOR MORE"
    }

    private fun mutateMelody() {
        val r = Random(SystemClock.uptimeMillis())
        val home = homeNote
        for (s in 0 until NativeEngine.STEPS) {
            val cur = notes[s]
            if (cur[0] < 0) continue
            val newRoot = moveInScale(home, r.nextInt(-2, 8), key, scale)
            val shiftBy = newRoot - cur[0]
            writeStep(s, cur.map { if (it >= 0) snapToScale((it + shiftBy).coerceIn(24, 100), key, scale) else -1 })
        }
        message = "NEW MELODY · $keyScaleName"
    }

    // ---------------------------------------------------------------- scenes
    private fun variation(a: FloatArray): FloatArray {
        val b = a.copyOf()
        fun bump(id: Int, by: Float) { b[id] = (b[id] + by).coerceIn(0f, 1f) }
        bump(P.CUTOFF, 0.3f); bump(P.FM_DEPTH, -0.25f); bump(P.HARMONIC, 0.2f)
        bump(P.DLY_MIX, 0.2f); bump(P.REV_MIX, 0.25f); bump(P.PH_MIX, 0.2f)
        bump(P.LFO_DEPTH, 0.3f); bump(P.RESO, 0.15f)
        return b
    }

    /**
     * A knob edit lands in the scene the slider is on: fully left edits A,
     * fully right edits B. In between, both scenes move together so the sound
     * you hear is exactly what you set.
     */
    private fun editScene(id: Int, value: Float, old: Float) {
        when {
            morph <= 0.001f -> sceneA[id] = value
            morph >= 0.999f -> sceneB[id] = value
            else -> {
                val d = value - old
                sceneA[id] = (sceneA[id] + d).coerceIn(0f, 1f)
                sceneB[id] = (sceneB[id] + d).coerceIn(0f, 1f)
            }
        }
    }

    fun scenePressed(isB: Boolean) {
        if (consumeShift()) {
            val snap = FloatArray(P.MOTION_COUNT) { params[it] }
            if (isB) sceneB = snap else sceneA = snap
            edited = true
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
        applyScenes()
        message = "MORPH ${(x * 100).roundToInt()}%"
    }

    private fun applyScenes() {
        for (i in 0 until P.MOTION_COUNT) {
            val v = sceneA[i] + (sceneB[i] - sceneA[i]) * morph
            if (abs(v - params[i]) > 1e-4f) setRaw(i, v)
        }
    }

    fun motionNames(): String {
        val names = (0 until P.MOTION_COUNT).filter { motionMask and (1 shl it) != 0 }
            .mapNotNull { PARAM_NAMES[it]?.friendly }
        return if (names.isEmpty()) "NO MOTION YET" else names.joinToString(" · ")
    }
}
