package com.bergz.zx700.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One step's extra settings: chance (100/75/50/25 %), ratchet (1-4 hits), tie (held over 1-16 steps). */
data class StepProps(val chance: Int = 100, val ratchet: Int = 1, val tie: Int = 1)

/**
 * A saved pattern slot: the steps plus the sound they were made with, so
 * loading a slot brings back exactly what you heard.
 */
data class PatternSlot(
    val name: String,
    val presetIndex: Int,
    val length: Int,
    val rate: Int,
    val swing: Float,
    val key: Int,
    val scaleIndex: Int,
    val notes: List<List<Int>>,
    val props: List<StepProps>,
    val sound: FloatArray,      // params below PRESET_COUNT
    val sceneA: FloatArray,
    val sceneB: FloatArray,
    val morph: Float,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name); put("preset", presetIndex); put("len", length); put("rate", rate)
        put("swing", swing.toDouble()); put("key", key); put("scale", scaleIndex); put("morph", morph.toDouble())
        put("notes", JSONArray().apply { notes.forEach { st -> put(JSONArray(st)) } })
        put("props", JSONArray().apply { props.forEach { put(JSONArray(listOf(it.chance, it.ratchet, it.tie))) } })
        put("sound", JSONArray(sound.map { it.toDouble() }))
        put("a", JSONArray(sceneA.map { it.toDouble() }))
        put("b", JSONArray(sceneB.map { it.toDouble() }))
    }

    companion object {
        private fun floats(a: JSONArray) = FloatArray(a.length()) { a.getDouble(it).toFloat() }
        fun fromJson(o: JSONObject): PatternSlot? = runCatching {
            val n = o.getJSONArray("notes")
            val p = o.getJSONArray("props")
            PatternSlot(
                name = o.getString("name"), presetIndex = o.getInt("preset"), length = o.getInt("len"),
                rate = o.getInt("rate"), swing = o.getDouble("swing").toFloat(), key = o.getInt("key"),
                scaleIndex = o.getInt("scale"),
                notes = List(n.length()) { i -> n.getJSONArray(i).let { a -> List(a.length()) { a.getInt(it) } } },
                props = List(p.length()) { i -> p.getJSONArray(i).let { StepProps(it.getInt(0), it.getInt(1), it.getInt(2)) } },
                sound = floats(o.getJSONArray("sound")), sceneA = floats(o.getJSONArray("a")),
                sceneB = floats(o.getJSONArray("b")), morph = o.getDouble("morph").toFloat(),
            )
        }.getOrNull()
    }
}

/** What Bergz keeps between launches: the 16 pattern slots and the SYNC settings. */
interface BergzStore {
    fun loadSlots(): List<PatternSlot?>
    fun saveSlot(index: Int, slot: PatternSlot?)
    fun loadSync(): FloatArray?          // mode, offset, start, clock out
    fun saveSync(values: FloatArray)
}

class PrefsStore(context: Context) : BergzStore {
    private val prefs = context.getSharedPreferences("bergz", Context.MODE_PRIVATE)

    override fun loadSlots(): List<PatternSlot?> = List(SLOTS) { i ->
        prefs.getString("slot$i", null)?.let { s -> runCatching { PatternSlot.fromJson(JSONObject(s)) }.getOrNull() }
    }

    override fun saveSlot(index: Int, slot: PatternSlot?) {
        prefs.edit().apply { if (slot == null) remove("slot$index") else putString("slot$index", slot.toJson().toString()) }.apply()
    }

    override fun loadSync(): FloatArray? {
        val s = prefs.getString("sync", null) ?: return null
        return runCatching { JSONArray(s).let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } } }.getOrNull()
    }

    override fun saveSync(values: FloatArray) {
        prefs.edit().putString("sync", JSONArray(values.map { it.toDouble() }).toString()).apply()
    }

    companion object { const val SLOTS = 16 }
}

/** In-memory store for previews and tests. */
class MemoryStore : BergzStore {
    private val slots = MutableList<PatternSlot?>(PrefsStore.SLOTS) { null }
    private var sync: FloatArray? = null
    override fun loadSlots() = slots.toList()
    override fun saveSlot(index: Int, slot: PatternSlot?) { slots[index] = slot }
    override fun loadSync() = sync
    override fun saveSync(values: FloatArray) { sync = values }
}
