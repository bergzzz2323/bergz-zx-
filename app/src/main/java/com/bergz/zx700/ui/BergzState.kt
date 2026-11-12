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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

fun transposeText(v: Int): String = when { v > 0 -> "+$v"; v < 0 -> "−${-v}"; else -> "0" }

data class PresetInfo(val index: Int, val name: String, val category: Int)

/** What the DATA wheel changes on the selected step. */
enum class EditMode(val label: String) { NOTE("NOTE"), CHORD("CHORD"), CHANCE("CHANCE"), RATCHET("RATCH"), TIE("TIE") }

/** Chord shapes for CHORD edit, as scale degrees stacked on the step's root. */
val CHORD_SHAPES = listOf(
    "SINGLE" to listOf(0), "5TH" to listOf(0, 4), "TRIAD" to listOf(0, 2, 4),
    "7TH" to listOf(0, 2, 4, 6), "SUS" to listOf(0, 3, 4), "9TH" to listOf(0, 2, 4, 8),
)
val CHANCES = listOf(100, 75, 50, 25)

/** A moment in HISTORY: the whole pattern and sound, so UNDO can go back to it. */
class Moment(
    val label: String,
    val kind: String,
    val time: String,
    val params: FloatArray,
    val notes: List<List<Int>>,
    val props: List<StepProps>,
    val sceneA: FloatArray,
    val sceneB: FloatArray,
    val morph: Float,
    val presetIndex: Int,
    val presetName: String,
    val key: Int,
    val scaleIndex: Int,
)

/**
 * Your sound tweaks to a patch: both scenes, the slider and the algorithm. Kept
 * in memory while the app is open, so you can switch patches and come back to
 * your version of the sound. The patch's steps always reset to its default
 * pattern on a patch change, and tempo/transpose are yours, not the patch's.
 */
private class PatchSession(
    val sceneA: FloatArray,
    val sceneB: FloatArray,
    val morph: Float,
    val algorithm: Float,
)

/**
 * Everything the panel shows, plus every action a control can trigger.
 * The UI keeps its own copy of the pattern; the engine is told about every
 * change, so both always agree. Knob values are refreshed from the engine
 * each frame so recorded motion visibly turns the knobs.
 */
class BergzState(private val store: BergzStore = MemoryStore()) {
    private val emptyStep = listOf(-1, -1, -1, -1)
    private val plainStep = StepProps()

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
    val notes = mutableStateListOf<List<Int>>().apply { repeat(NativeEngine.MAX_STEPS) { add(emptyStep) } }
    /** Chance / ratchet / tie of every step. */
    val stepProps = mutableStateListOf<StepProps>().apply { repeat(NativeEngine.MAX_STEPS) { add(StepProps()) } }

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
    /**
     * Step memory for this jam: notes a step had when you switched it off, so
     * switching it back on brings them back. Cleared on patch change and app start.
     */
    val ghosts = mutableStateListOf<List<Int>>().apply { repeat(NativeEngine.MAX_STEPS) { add(listOf(-1, -1, -1, -1)) } }

    // ---- v0.7: pages, step editing, KEYS, sync, patterns, history
    /** Which page of 16 steps is on screen (0-3). */
    var page by mutableIntStateOf(0)
        private set
    var editMode by mutableStateOf(EditMode.NOTE)
        private set
    /** KEYS: the step buttons are a keyboard in the current scale, played over the pattern. */
    var keysMode by mutableStateOf(false)
        private set
    var keysOctave by mutableIntStateOf(3)
        private set
    val heldKeys = mutableStateListOf<Int>()
    /** DATA only changes tempo after the BPM on the display is tapped. */
    var tempoArmed by mutableStateOf(false)
        private set
    private var tempoArmedAt = 0L

    var syncOpen by mutableStateOf(false)
    var patternsOpen by mutableStateOf(false)
    var historyOpen by mutableStateOf(false)
    /** bit 0 following external clock, bit 1 clock arriving, bit 2 waiting for the bar. */
    var syncState by mutableIntStateOf(0)
        private set
    var beat by mutableIntStateOf(-1)
        private set
    var clockTargets: List<String> = emptyList()

    val slots = mutableStateListOf<PatternSlot?>()
    var currentSlot by mutableIntStateOf(-1)
        private set
    private var pendingSlot = -1
    private var lastPlayhead = -1

    /** Last 20 moments, this session only; empty every time the app opens. */
    val history = mutableStateListOf<Moment>()
    /** Index in [history] of what you are hearing now. */
    var historyPos by mutableIntStateOf(-1)
        private set
    private var pendingMoment: Pair<String, String>? = null
    private var pendingMomentAt = 0L
    private val touchStart = FloatArray(P.COUNT)
    private var patchLoaded = false
    private val touched = BooleanArray(P.COUNT)
    private val localSetAt = LongArray(P.COUNT)
    private val tapTimes = ArrayDeque<Long>()

    val scale: Scale get() = SCALES[scaleIndex]
    val keyScaleName: String get() = keyAndScaleName(key, scale)
    val fx: NativeEngine.Fx get() = NativeEngine.FX[fxSelected]
    val tempo: Int get() = params[P.TEMPO].roundToInt()
    val patternLength: Int get() = params[P.PAT_LEN].roundToInt().coerceIn(1, NativeEngine.MAX_STEPS)
    val pages: Int get() = (patternLength + NativeEngine.STEPS - 1) / NativeEngine.STEPS
    val rateIndex: Int get() = params[P.RATE].roundToInt().coerceIn(0, NativeEngine.RATES.size - 1)
    val swingPercent: Int get() = (params[P.SWING] * 100).roundToInt()
    val syncMode: Int get() = params[P.SYNC_MODE].roundToInt()
    val syncOffset: Int get() = params[P.SYNC_OFFSET].roundToInt()
    val startQuant: Int get() = params[P.START_Q].roundToInt()
    val clockOut: Boolean get() = params[P.CLOCK_OUT] > 0.5f
    val following: Boolean get() = syncState and 1 != 0
    val clockArriving: Boolean get() = syncState and 2 != 0
    val waitingForBar: Boolean get() = syncState and 4 != 0
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
        for (s in 0 until NativeEngine.MAX_STEPS) ghosts[s] = emptyStep
        patchLoaded = false
        key = 0
        scaleIndex = 0  // C MAJOR
        fxSelected = 0  // DELAY
        playing = false
        recording = false
        page = 0; editMode = EditMode.NOTE; keysMode = false; keysOctave = 3; tempoArmed = false
        // SYNC settings are kept between launches
        store.loadSync()?.let { v ->
            listOf(P.SYNC_MODE, P.SYNC_OFFSET, P.START_Q, P.CLOCK_OUT).forEachIndexed { i, id -> if (i < v.size) setRaw(id, v[i]) }
        }
        slots.clear(); slots.addAll(store.loadSlots())
        currentSlot = -1
        // HISTORY always starts empty
        history.clear(); historyPos = -1; pendingMoment = null
        loadPreset(0)
        message = "PRESS PLAY"
    }

    // ---------------------------------------------------------------- presets
    private fun saveSession() {
        if (!patchLoaded) return
        sessions[presetIndex] = PatchSession(sceneA.copyOf(), sceneB.copyOf(), morph, params[P.ALGORITHM])
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
        // Tempo is the player's: the patch's own tempo is ignored.
        for (k in 0 until P.PRESET_COUNT) if (k != P.TEMPO) { params[k] = p[k]; localSetAt[k] = now }

        val saved = sessions[i]
        if (saved != null) {
            // Back to a patch you already tweaked this session: restore your sound.
            sceneA = saved.sceneA.copyOf()
            sceneB = saved.sceneB.copyOf()
            morph = saved.morph
            applyScenes()
            setRaw(P.ALGORITHM, saved.algorithm)
        } else {
            sceneA = FloatArray(P.MOTION_COUNT) { params[it] }
            sceneB = variation(sceneA)
            morph = 0f
        }
        // Steps always start from the patch's own 16-step pattern, in your key and scale.
        // (Rate and swing are yours and stay as they are.)
        val n = NativeEngine.nativePresetNotes(i)
        val raw = List(NativeEngine.STEPS) { s -> List(NativeEngine.NOTES_PER_STEP) { n[s * NativeEngine.NOTES_PER_STEP + it] } }
        val steps = conformSteps(raw, PRESET_KEY, SCALES[PRESET_SCALE_INDEX], key, scale)
        for (s in 0 until NativeEngine.MAX_STEPS) {
            sendStep(s, if (s < NativeEngine.STEPS) steps[s] else emptyStep)
            sendProps(s, plainStep)
            ghosts[s] = emptyStep
        }
        setRaw(P.PAT_LEN, NativeEngine.STEPS.toFloat())
        page = 0

        patchLoaded = true
        selectedStep = -1
        edited = saved != null
        currentSlot = -1
        message = if (saved != null) "$presetName · YOUR TWEAKS" else "$presetName · ${keyScaleName}"
        moment("PATCH · $presetName", "load")
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
        if (down) touchStart[id] = params[id]
        else if (abs(params[id] - touchStart[id]) > 0.005f) {
            val name = PARAM_NAMES[id]?.friendly ?: "KNOB"
            moment("$name %.2f → %.2f".format(touchStart[id], params[id]), "knob")
        }
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
        syncState = NativeEngine.nativeSyncState()
        beat = NativeEngine.nativeBeat()
        midiActive = now - MidiStatus.lastEventAt < 150
        if (tempoArmed && now - tempoArmedAt > 4000) { tempoArmed = false; message = "TEMPO LOCKED" }
        // A DATA-wheel turn becomes one HISTORY moment once you stop turning.
        pendingMoment?.let { (label, kind) -> if (now - pendingMomentAt > 700) { pendingMoment = null; moment(label, kind) } }
        // A pattern slot picked while playing loads as the loop comes round.
        if (pendingSlot >= 0 && (!playing || (playhead == 0 && lastPlayhead != 0))) {
            val s = pendingSlot; pendingSlot = -1; applySlot(s)
        }
        lastPlayhead = playhead
        for (i in 0 until P.COUNT) {
            if (touched[i] || now - localSetAt[i] < 250) continue
            val v = NativeEngine.nativeParam(i)
            if (abs(v - params[i]) > 1e-4f) params[i] = v
        }
    }

    // ---------------------------------------------------------------- transport
    fun play() {
        if (!playing) {
            NativeEngine.nativePlay(); playing = true; transportSetAt = SystemClock.uptimeMillis()
            val onClock = syncMode == NativeEngine.SYNC_EXT || (syncMode == NativeEngine.SYNC_AUTO && clockArriving)
            message = if (onClock) "WAITING FOR THE ${listOf("BAR", "BEAT", "CLOCK")[startQuant]}" else "PLAYING"
        }
    }

    private var lastPlayTap = 0L

    /**
     * PLAY (PATT). One tap plays straight away (no waiting to see if a second tap
     * is coming). Two quick taps (within 350 ms) also make a new random pattern.
     */
    fun playPressed() {
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayTap < 350L) {
            lastPlayTap = 0L
            randomPattern()
            play()
        } else {
            lastPlayTap = now
            play()
        }
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
        if (following) { message = "TEMPO FROM CLOCK · ${tempo} BPM"; return }
        val old = tempo
        val v = bpm.coerceIn(40, 240).toFloat()
        setRaw(P.TEMPO, v)
        message = "TEMPO ${v.roundToInt()} BPM"
        laterMoment("TEMPO $old → ${v.roundToInt()}", "tempo")
    }

    /** Tap the BPM on the display: DATA changes tempo until you tap a step, tap BPM again, or stop for 4 s. */
    fun toggleTempoArm() {
        if (following) { tempoArmed = false; message = "TEMPO FROM CLOCK · ${tempo} BPM"; return }
        tempoArmed = !tempoArmed
        tempoArmedAt = SystemClock.uptimeMillis()
        if (tempoArmed) selectedStep = -1
        message = if (tempoArmed) "TEMPO · TURN DATA" else "TEMPO LOCKED"
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

    // ---------------------------------------------------------------- transpose
    /** Sequencer transpose in semitones (-12..+12). Starts at 0; kept across patch changes. */
    val transpose: Int get() = params[P.TRANSPOSE].roundToInt()

    fun setTranspose(semitones: Int) {
        val v = semitones.coerceIn(-12, 12)
        if (v.toFloat() == params[P.TRANSPOSE]) return
        setRaw(P.TRANSPOSE, v.toFloat())
        message = "TRANSPOSE ${transposeText(v)}"
    }

    fun nudgeTranspose(by: Int) = setTranspose(transpose + by)

    // ---------------------------------------------------------------- key & scale
    fun openKeyPicker() {
        keyPickerOpen = true
        message = keyScaleName
    }

    fun closeKeyPicker() { keyPickerOpen = false }

    /** Change key and/or scale: the current pattern moves with it, note by note. */
    fun setKeyScale(newKey: Int, newScaleIndex: Int) {
        if (newKey == key && newScaleIndex == scaleIndex) return
        val oldKey = key
        val oldScale = scale
        val converted = conformSteps(notes.toList(), key, scale, newKey, SCALES[newScaleIndex])
        key = newKey
        scaleIndex = newScaleIndex
        for (s in 0 until NativeEngine.MAX_STEPS) if (converted[s] != notes[s]) sendStep(s, converted[s])
        val ghostsMoved = conformSteps(ghosts.toList(), oldKey, oldScale, newKey, SCALES[newScaleIndex])
        for (s in 0 until NativeEngine.MAX_STEPS) ghosts[s] = ghostsMoved[s]
        message = keyScaleName
        moment("KEY · $keyScaleName", "key")
    }

    // ---------------------------------------------------------------- steps
    /** First step of the page on screen. */
    val pageStart: Int get() = page * NativeEngine.STEPS

    /** Where new notes start: the pattern's first note, or the key note around C3. */
    private val homeNote: Int get() {
        val first = notes.firstOrNull { it[0] >= 0 }?.get(0)
        return first ?: (48 + if (key > 6) key - 12 else key)
    }

    /** Tap empty = add note, tap = select, tap selected = clear. [step] is the absolute step (0-63). */
    fun stepPressed(step: Int) {
        if (step >= patternLength) { message = "STEP ${step + 1} IS PAST THE END · LENGTH $patternLength"; return }
        tempoArmed = false
        val current = notes[step]
        when {
            current[0] < 0 && ghosts[step][0] >= 0 -> {
                // switched back on: it carries on with the notes it was left on
                writeStep(step, ghosts[step])
                selectedStep = step
                message = "STEP ${step + 1} ${noteName(ghosts[step][0])} · BACK ON"
            }
            current[0] < 0 -> {
                val n = snapToScale(homeNote, key, scale)
                writeStep(step, listOf(n, -1, -1, -1))
                selectedStep = step
                message = "STEP ${step + 1} ${noteName(n)} · TURN DATA"
                moment("STEP ${step + 1} ${noteName(n)}", "edit")
            }
            selectedStep == step -> {
                ghosts[step] = current  // remember it for this jam
                writeStep(step, emptyStep)
                selectedStep = -1
                message = "STEP ${step + 1} OFF · NOTE REMEMBERED"
                moment("STEP ${step + 1} OFF", "edit")
            }
            else -> {
                selectedStep = step
                message = "STEP ${step + 1} ${noteName(current[0])} · TURN DATA"
            }
        }
    }

    /**
     * DATA wheel. Changes tempo only when the BPM has been tapped. Otherwise it
     * edits the selected step (NOTE / CHORD / CHANCE / RATCH / TIE), and with
     * nothing selected it does nothing, so the tempo can't move by accident.
     */
    fun dataWheel(delta: Int) {
        if (tempoArmed) {
            tempoArmedAt = SystemClock.uptimeMillis()
            setTempo(tempo + delta)
            return
        }
        val s = selectedStep
        if (s < 0 || notes[s][0] < 0) { message = "SELECT A STEP OR TAP BPM"; return }
        val props = stepProps[s]
        when (editMode) {
            EditMode.NOTE -> {
                val oldRoot = notes[s][0]
                val newRoot = moveInScale(oldRoot, delta, key, scale)
                val shiftBy = newRoot - oldRoot
                writeStep(s, notes[s].map { if (it >= 0) (it + shiftBy).coerceIn(24, 100) else -1 })
                message = "STEP ${s + 1} ${noteName(newRoot)} · $keyScaleName"
                laterMoment("STEP ${s + 1} NOTE ${noteName(newRoot)}", "edit")
            }
            EditMode.CHORD -> {
                val i = (chordShapeIndex(s) + delta).coerceIn(0, CHORD_SHAPES.size - 1)
                val root = notes[s][0]
                val shape = CHORD_SHAPES[i].second
                writeStep(s, List(NativeEngine.NOTES_PER_STEP) { v ->
                    if (v < shape.size) moveInScale(root, shape[v], key, scale).coerceIn(24, 100) else -1
                })
                message = "STEP ${s + 1} CHORD ${CHORD_SHAPES[i].first}"
                laterMoment("CHORD STEP ${s + 1} · ${CHORD_SHAPES[i].first}", "edit")
            }
            EditMode.CHANCE -> {
                val i = (CHANCES.indexOf(props.chance).coerceAtLeast(0) + delta).coerceIn(0, CHANCES.size - 1)
                writeProps(s, props.copy(chance = CHANCES[i]))
                message = "STEP ${s + 1} CHANCE ${CHANCES[i]}%"
                laterMoment("STEP ${s + 1} CHANCE ${CHANCES[i]}%", "edit")
            }
            EditMode.RATCHET -> {
                val r = (props.ratchet + delta).coerceIn(1, 4)
                writeProps(s, props.copy(ratchet = r, tie = if (r > 1) 1 else props.tie))
                message = if (r == 1) "STEP ${s + 1} RATCHET OFF" else "STEP ${s + 1} RATCHET x$r"
                laterMoment("STEP ${s + 1} RATCHET x$r", "edit")
            }
            EditMode.TIE -> {
                val t = (props.tie + delta).coerceIn(1, minOf(16, patternLength - s))
                writeProps(s, props.copy(tie = t, ratchet = if (t > 1) 1 else props.ratchet))
                message = if (t == 1) "STEP ${s + 1} NOT TIED" else "STEP ${s + 1} HELD ${t} STEPS"
                laterMoment("STEP ${s + 1} TIE x$t", "edit")
            }
        }
    }

    /** Which CHORD_SHAPES entry a step's notes match (closest by note count). */
    fun chordShapeIndex(step: Int): Int {
        val root = notes[step][0]
        if (root < 0) return 0
        val got = notes[step].filter { it >= 0 }
        CHORD_SHAPES.forEachIndexed { i, (_, shape) ->
            if (shape.size == got.size && shape.map { moveInScale(root, it, key, scale) } == got) return i
        }
        return when (got.size) { 1 -> 0; 2 -> 1; 3 -> 2; else -> 3 }
    }

    fun setEditMode(m: EditMode) {
        editMode = m
        tempoArmed = false
        message = if (selectedStep >= 0) "STEP ${selectedStep + 1} · ${m.label} · TURN DATA" else "${m.label} · SELECT A STEP"
    }

    fun showPage(p: Int) {
        if (p >= pages) { message = "PAGE ${p + 1} · SET LENGTH IN PATT"; return }
        page = p
        message = "PAGE ${p + 1} · STEPS ${p * 16 + 1}-${p * 16 + 16}"
    }

    /** Which steps a tied step before it is holding over (for the tie bars). */
    fun tiedFrom(step: Int): Int {
        for (k in step - 1 downTo maxOf(0, step - 15)) {
            if (notes[k][0] >= 0 && stepProps[k].tie > step - k) return k
        }
        return -1
    }

    private fun writeProps(step: Int, p: StepProps) {
        sendProps(step, p)
        edited = true
    }

    private fun sendProps(step: Int, p: StepProps) {
        stepProps[step] = p
        NativeEngine.nativeSetStepProps(step, p.chance, p.ratchet, p.tie)
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
        moment("MUTATE", "sound")
    }

    private fun mutateMelody() {
        val r = Random(SystemClock.uptimeMillis())
        val home = homeNote
        for (s in 0 until NativeEngine.MAX_STEPS) {
            val cur = notes[s]
            if (cur[0] < 0) continue
            val newRoot = moveInScale(home, r.nextInt(-2, 8), key, scale)
            val shiftBy = newRoot - cur[0]
            writeStep(s, cur.map { if (it >= 0) snapToScale((it + shiftBy).coerceIn(24, 100), key, scale) else -1 })
        }
        message = "NEW MELODY · $keyScaleName"
        moment("NEW MELODY", "shift + mutate")
    }

    // ---------------------------------------------------------------- random pattern
    /**
     * PLAY (PATT): a brand-new random pattern, both which steps play and which
     * notes. It stays musical: notes are always in the chosen key and scale, the
     * line moves mostly by small steps with the odd leap, step 1 always plays,
     * and it keeps the patch's own register (bass stays low, bleeps stay high)
     * and its feel (chord patches get chords, single-note patches get melody).
     */
    fun randomPattern() {
        val r = Random(SystemClock.uptimeMillis())
        val current = notes.filter { it[0] >= 0 }
        val roots = current.map { it[0] }.sorted()
        val center = snapToScale(if (roots.isEmpty()) homeNote else roots[roots.size / 2], key, scale)
        val voices = (current.map { st -> st.count { it >= 0 } }.maxOrNull() ?: 1).coerceIn(1, NativeEngine.NOTES_PER_STEP)
        val chordy = voices > 1

        // Which steps play: chord patches are sparser, melodies busier. 8th-note
        // positions are favoured so it grooves rather than scatters.
        val len = patternLength
        val bars = (len + 15) / 16
        val target = (if (chordy) r.nextInt(3, 7) else r.nextInt(6, 12)) * bars
        val on = BooleanArray(len)
        on[0] = true
        var count = 1
        while (count < target) {
            val s = r.nextInt(1, len)
            if (on[s]) continue
            val weight = when {
                s % 4 == 0 -> 0.9f   // beats
                s % 2 == 0 -> 0.65f  // 8ths
                else -> 0.35f        // 16th offbeats
            }
            if (r.nextFloat() < weight) { on[s] = true; count++ }
        }

        // Which notes: a random walk through the scale around the patch's register.
        var pos = 0  // scale degrees away from the centre
        for (s in 0 until NativeEngine.MAX_STEPS) {
            sendProps(s, plainStep)
            if (s >= len || !on[s]) { writeStep(s, emptyStep); continue }
            if (s > 0) {
                val move = if (r.nextFloat() < 0.18f) r.nextInt(-5, 6) else r.nextInt(-2, 3)
                pos = (pos + move).coerceIn(-5, 7)
            }
            val root = moveInScale(center, pos, key, scale).coerceIn(24, 100)
            val step = MutableList(NativeEngine.NOTES_PER_STEP) { -1 }
            step[0] = root
            if (chordy) {
                // stack scale thirds above the root: triads and 7ths
                for (v in 1 until voices) step[v] = moveInScale(root, 2 * v, key, scale).coerceIn(24, 100)
            }
            writeStep(s, step)
        }
        for (s in 0 until NativeEngine.MAX_STEPS) ghosts[s] = emptyStep
        selectedStep = -1
        message = "NEW PATTERN · $keyScaleName"
        moment("NEW PATTERN", "play (patt)")
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
        for (i in 0 until P.MOTION_COUNT) if (sceneA[i] != sceneB[i]) {
            touched[i] = down
            NativeEngine.nativeTouch(i, down)
        }
        if (down) morphStart = morph
        else if (abs(morph - morphStart) > 0.01f) moment("MORPH ${(morphStart * 100).roundToInt()}% → ${(morph * 100).roundToInt()}%", "morph")
    }
    private var morphStart = 0f

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

    // ---------------------------------------------------------------- HISTORY
    // The last 20 moments (pattern + sound) of this session, newest last. Kept
    // in memory only: closing the app clears it, and every launch starts empty.
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.UK)

    private fun capture(label: String, kind: String) = Moment(
        label, kind, timeFormat.format(Date()),
        FloatArray(P.COUNT) { params[it] }, notes.toList(), stepProps.toList(),
        sceneA.copyOf(), sceneB.copyOf(), morph, presetIndex, presetName, key, scaleIndex,
    )

    /** Record what you hear now as a moment you can go back to. */
    private fun moment(label: String, kind: String) {
        pendingMoment = null
        // A new move after going back drops the moments that were ahead of it.
        while (history.size - 1 > historyPos) history.removeAt(history.size - 1)
        history.add(capture(label, kind))
        while (history.size > HISTORY_SIZE) history.removeAt(0)
        historyPos = history.size - 1
    }

    /** For DATA turns: one moment once the wheel has been still for a moment. */
    private fun laterMoment(label: String, kind: String) {
        pendingMoment = label to kind
        pendingMomentAt = SystemClock.uptimeMillis()
    }

    private fun flushMoment() { pendingMoment?.let { (l, k) -> moment(l, k) } }

    val canUndo: Boolean get() = historyPos > 0 || pendingMoment != null
    val canRedo: Boolean get() = historyPos < history.size - 1

    /** UNDO: back one moment. SHIFT + UNDO: forward again (redo). */
    fun undoPressed() {
        if (consumeShift()) { redo(); return }
        flushMoment()
        if (historyPos <= 0) { message = "NOTHING TO UNDO"; return }
        jumpTo(historyPos - 1)
        message = "UNDO · ${history[historyPos + 1].label}"
    }

    fun redo() {
        if (historyPos >= history.size - 1) { message = "NOTHING TO REDO"; return }
        jumpTo(historyPos + 1)
        message = "REDO · ${history[historyPos].label}"
    }

    /** Go back (or forward) to any moment in HISTORY. */
    fun jumpTo(index: Int) {
        val m = history.getOrNull(index) ?: return
        pendingMoment = null
        historyPos = index
        if (m.presetIndex != presetIndex) { presetIndex = m.presetIndex; presetName = m.presetName }
        key = m.key; scaleIndex = m.scaleIndex
        sceneA = m.sceneA.copyOf(); sceneB = m.sceneB.copyOf(); morph = m.morph
        for (k in 0 until P.COUNT) {
            if (k in SYNC_PARAMS) continue
            if (k == P.TEMPO && following) continue
            if (abs(m.params[k] - params[k]) > 1e-5f) setRaw(k, m.params[k])
        }
        for (st in 0 until NativeEngine.MAX_STEPS) {
            if (m.notes[st] != notes[st]) sendStep(st, m.notes[st])
            if (m.props[st] != stepProps[st]) sendProps(st, m.props[st])
        }
        if (page >= pages) page = 0
        selectedStep = -1
        edited = true
        message = "BACK TO ${m.time} · ${m.label}"
    }

    // ---------------------------------------------------------------- KEYS
    fun toggleKeysMode() {
        keysMode = !keysMode
        selectedStep = -1
        if (!keysMode) releaseAllKeys()
        message = if (keysMode) "KEYS · $keyScaleName · PLAYS OVER THE PATTERN" else "STEPS"
    }

    fun setKeysMode(on: Boolean) { if (on != keysMode) toggleKeysMode() }

    fun shiftOctave(by: Int) {
        keysOctave = (keysOctave + by).coerceIn(1, 6)
        releaseAllKeys()
        message = "KEYS OCTAVE $keysOctave"
    }

    /** The note on KEYS button [i]: two octaves of the current scale from the key note. */
    fun keyNote(i: Int): Int {
        val root = 12 * (keysOctave + 1) + key
        return moveInScale(root, i, key, scale).coerceIn(0, 127)
    }

    fun keyDown(i: Int) {
        val n = keyNote(i)
        if (n in heldKeys) return
        heldKeys.add(n)
        NativeEngine.nativeNoteOn(n, 0.85f)
        // With REC lit while playing, what you play is written into the step under the playhead.
        if (recording && playing && playhead >= 0) {
            val st = playhead
            val cur = notes[st]
            val next = when {
                cur[0] < 0 -> listOf(n, -1, -1, -1)
                n in cur -> cur
                cur.contains(-1) -> cur.toMutableList().also { it[it.indexOf(-1)] = n }
                else -> cur
            }
            if (next != cur) { writeStep(st, next); laterMoment("KEYS → STEP ${st + 1} ${noteName(n)}", "keys") }
        }
        message = "KEYS · ${noteName(n)}"
    }

    fun keyUp(i: Int) {
        val n = keyNote(i)
        heldKeys.remove(n)
        NativeEngine.nativeNoteOff(n)
    }

    private fun releaseAllKeys() {
        heldKeys.toList().forEach { NativeEngine.nativeNoteOff(it) }
        heldKeys.clear()
    }

    // ---------------------------------------------------------------- PATTERNS
    fun openPatterns() { patternsOpen = true; message = "PATTERNS" }
    fun closePatterns() { patternsOpen = false }

    fun setLength(steps: Int) {
        val len = steps.coerceIn(1, NativeEngine.MAX_STEPS)
        setRaw(P.PAT_LEN, len.toFloat())
        if (page >= pages) page = pages - 1
        if (selectedStep >= len) selectedStep = -1
        message = "LENGTH $len STEPS"
        moment("LENGTH $len", "pattern")
    }

    fun setRate(index: Int) {
        setRaw(P.RATE, index.coerceIn(0, NativeEngine.RATES.size - 1).toFloat())
        message = "RATE ${NativeEngine.RATES[rateIndex]}"
        moment("RATE ${NativeEngine.RATES[rateIndex]}", "pattern")
    }

    fun nudgeSwing(by: Int) {
        val v = ((swingPercent + by).coerceIn(50, 75)) / 100f
        setRaw(P.SWING, v)
        message = "SWING $swingPercent%"
        laterMoment("SWING $swingPercent%", "pattern")
    }

    /** Tap a slot: load it (on the next loop if playing). SHIFT + tap: save the current pattern + sound there. */
    fun slotPressed(i: Int) {
        if (consumeShift()) { saveSlot(i); return }
        if (slots.getOrNull(i) == null) { message = "SLOT ${i + 1} EMPTY · SHIFT + TAP TO SAVE"; return }
        if (playing) { pendingSlot = i; message = "SLOT ${i + 1} · LOADS ON THE NEXT LOOP" } else applySlot(i)
    }

    private fun saveSlot(i: Int) {
        val slot = PatternSlot(
            name = presetName, presetIndex = presetIndex, length = patternLength, rate = rateIndex,
            swing = params[P.SWING], key = key, scaleIndex = scaleIndex,
            notes = notes.toList(), props = stepProps.toList(),
            sound = FloatArray(P.PRESET_COUNT) { params[it] }, sceneA = sceneA.copyOf(), sceneB = sceneB.copyOf(), morph = morph,
        )
        while (slots.size < PrefsStore.SLOTS) slots.add(null)
        slots[i] = slot
        store.saveSlot(i, slot)
        currentSlot = i
        message = "SAVED TO SLOT ${i + 1}"
    }

    fun clearSlot(i: Int) {
        if (i !in slots.indices) return
        slots[i] = null
        store.saveSlot(i, null)
        if (currentSlot == i) currentSlot = -1
        message = "SLOT ${i + 1} CLEARED"
    }

    private fun applySlot(i: Int) {
        val sl = slots.getOrNull(i) ?: return
        saveSession()
        presetIndex = sl.presetIndex.coerceIn(0, maxOf(0, library.size - 1))
        presetName = sl.name
        key = sl.key; scaleIndex = sl.scaleIndex
        for (k in 0 until P.PRESET_COUNT) if (k != P.TEMPO && k < sl.sound.size) setRaw(k, sl.sound[k])
        sceneA = sl.sceneA.copyOf(); sceneB = sl.sceneB.copyOf(); morph = sl.morph
        setRaw(P.PAT_LEN, sl.length.toFloat()); setRaw(P.RATE, sl.rate.toFloat()); setRaw(P.SWING, sl.swing)
        for (st in 0 until NativeEngine.MAX_STEPS) {
            sendStep(st, sl.notes.getOrNull(st) ?: emptyStep)
            sendProps(st, sl.props.getOrNull(st) ?: plainStep)
            ghosts[st] = emptyStep
        }
        page = 0; selectedStep = -1; currentSlot = i; patchLoaded = true
        message = "SLOT ${i + 1} · ${sl.name}"
        moment("SLOT ${i + 1} · ${sl.name}", "load")
    }

    // ---------------------------------------------------------------- SYNC
    fun openSync() { syncOpen = true; message = "SYNC" }
    fun closeSync() { syncOpen = false }

    private fun saveSync() {
        store.saveSync(floatArrayOf(params[P.SYNC_MODE], params[P.SYNC_OFFSET], params[P.START_Q], params[P.CLOCK_OUT]))
    }

    fun setSyncMode(mode: Int) {
        setRaw(P.SYNC_MODE, mode.toFloat()); saveSync()
        message = "CLOCK " + listOf("AUTO", "INTERNAL", "EXTERNAL")[mode]
    }

    fun setClockOut(on: Boolean) {
        setRaw(P.CLOCK_OUT, if (on) 1f else 0f); saveSync()
        message = if (on) "CLOCK OUT ON" else "CLOCK OUT OFF"
    }

    fun nudgeOffset(by: Int) {
        setRaw(P.SYNC_OFFSET, (syncOffset + by).coerceIn(-60, 60).toFloat()); saveSync()
        message = "SYNC OFFSET ${if (syncOffset > 0) "+" else ""}$syncOffset ms"
    }

    fun setStartQuant(q: Int) {
        setRaw(P.START_Q, q.toFloat()); saveSync()
        message = "START ON " + listOf("NEXT BAR", "NEXT BEAT", "NEXT TICK")[q]
    }

    /** Text for the SYNC pill in the header. */
    val syncLabel: String get() {
        val src = when {
            waitingForBar -> "WAITING FOR ${if (startQuant == 0) "BAR" else "BEAT"}"
            following -> "EXT %.1f".format(params[P.TEMPO])
            syncMode == NativeEngine.SYNC_EXT -> "EXT · NO CLOCK"
            else -> "INT $tempo"
        }
        return "SYNC · $src" + if (clockOut && !following) " · OUT ON" else ""
    }

    companion object {
        const val HISTORY_SIZE = 20
        val SYNC_PARAMS = setOf(P.SYNC_MODE, P.SYNC_OFFSET, P.START_Q, P.CLOCK_OUT)
    }
}
