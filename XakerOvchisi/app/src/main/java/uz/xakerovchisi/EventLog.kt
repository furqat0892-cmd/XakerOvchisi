package uz.xakerovchisi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Event(val time: Long, val type: String, val severity: Int, val text: String)

/** Hodisalar jurnali. severity: 1 = ma'lumot, 2 = ogohlantirish, 3 = xavfli. */
object EventLog {
    private const val PREFS = "events"
    private const val KEY = "list"
    private const val MAX = 300

    @Synchronized
    fun add(ctx: Context, type: String, severity: Int, text: String) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = try {
            JSONArray(p.getString(KEY, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = JSONArray()
        out.put(
            JSONObject()
                .put("t", System.currentTimeMillis())
                .put("y", type)
                .put("s", severity)
                .put("x", text)
        )
        val keep = minOf(old.length(), MAX - 1)
        for (i in 0 until keep) out.put(old.get(i))
        p.edit().putString(KEY, out.toString()).apply()
    }

    @Synchronized
    fun all(ctx: Context): List<Event> {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = try {
            JSONArray(p.getString(KEY, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val list = ArrayList<Event>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(Event(o.optLong("t"), o.optString("y"), o.optInt("s", 1), o.optString("x")))
        }
        return list
    }

    @Synchronized
    fun clear(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
