package io.embodit.trafficsignals.platform

import android.content.Context
import io.embodit.trafficsignals.domain.Answer
import io.embodit.trafficsignals.domain.Mistake
import io.embodit.trafficsignals.domain.Question
import org.json.JSONArray
import org.json.JSONObject

/** Local persistence (design `persistence`): language, sound, officer size, speed, best scores, mistakes (≤ 200), lessons seen. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("traffic_signals", Context.MODE_PRIVATE)

    var zh: Boolean
        get() = prefs.getBoolean("zh", true) // design: Chinese first (driving test content), EN one tap away
        set(v) = prefs.edit().putBoolean("zh", v).apply()

    fun hasLang(): Boolean = prefs.contains("zh")

    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) = prefs.edit().putBoolean("sound", v).apply()

    var big: Boolean
        get() = prefs.getBoolean("big", false)
        set(v) = prefs.edit().putBoolean("big", v).apply()

    var slow: Boolean
        get() = prefs.getBoolean("slow", false)
        set(v) = prefs.edit().putBoolean("slow", v).apply()

    var best: Map<Int, Int>
        get() {
            val o = JSONObject(prefs.getString("best", "{}") ?: "{}")
            return o.keys().asSequence().associate { it.toInt() to o.getInt(it) }
        }
        set(v) {
            val o = JSONObject()
            v.forEach { (k, s) -> o.put(k.toString(), s) }
            prefs.edit().putString("best", o.toString()).apply()
        }

    var learned: Set<Int>
        get() = (prefs.getString("learned", "") ?: "").split(',').mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = prefs.edit().putString("learned", v.sorted().joinToString(",")).apply()

    var mistakes: List<Mistake>
        get() {
            val a = JSONArray(prefs.getString("mistakes", "[]") ?: "[]")
            return (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val q = Question.parse(o.getString("q")) ?: return@mapNotNull null
                Mistake(q, o.optString("a").takeIf { it.isNotEmpty() }?.let { Answer.byId(it) }, o.optLong("t"))
            }
        }
        set(v) {
            val a = JSONArray()
            v.takeLast(MAX_MISTAKES).forEach { m -> a.put(JSONObject().put("q", m.question.key).put("a", m.yours?.id ?: "").put("t", m.at)) }
            prefs.edit().putString("mistakes", a.toString()).apply()
        }

    fun resetProgress() {
        prefs.edit().remove("best").remove("learned").remove("mistakes").apply()
    }

    companion object {
        const val MAX_MISTAKES = 200
    }
}
