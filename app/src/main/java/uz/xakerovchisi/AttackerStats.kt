package uz.xakerovchisi

import android.content.Context
import org.json.JSONObject

/** Har bir IP necha marta tuzoqqa urinib ko'rganini hisoblaydi — takroriy hujumchilarni aniqlash uchun. */
object AttackerStats {
    private const val PREFS = "attacker_stats"
    private const val KEY = "counts"
    const val DANGER_THRESHOLD = 3

    @Synchronized
    fun record(ctx: Context, ip: String): Int {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val obj = try {
            JSONObject(p.getString(KEY, "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        val count = obj.optInt(ip, 0) + 1
        obj.put(ip, count)
        p.edit().putString(KEY, obj.toString()).apply()
        return count
    }

    @Synchronized
    fun all(ctx: Context): List<Pair<String, Int>> {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val obj = try {
            JSONObject(p.getString(KEY, "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        val list = ArrayList<Pair<String, Int>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            list.add(k to obj.optInt(k, 0))
        }
        return list.sortedByDescending { it.second }
    }

    @Synchronized
    fun clear(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
