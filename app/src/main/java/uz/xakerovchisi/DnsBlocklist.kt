package uz.xakerovchisi

import android.content.Context

/**
 * Bloklanadigan domenlar ro'yxati. Bu — kichik, foydalanuvchi o'zi to'ldiradigan ro'yxat
 * (keng qamrovli tahdid-razvedka bazasi emas). Foydalanuvchi "Hisob" yoki "Tuzoq" bo'limidan
 * o'zi shubhali domenlarni qo'shishi mumkin.
 */
object DnsBlocklist {
    private const val PREFS = "dns_blocklist"
    private const val KEY = "domains"

    fun all(ctx: Context): Set<String> {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getStringSet(KEY, emptySet()) ?: emptySet()
    }

    fun add(ctx: Context, domain: String) {
        val d = domain.trim().trimEnd('.').lowercase()
        if (d.isEmpty()) return
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = HashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
        set.add(d)
        p.edit().putStringSet(KEY, set).apply()
    }

    fun remove(ctx: Context, domain: String) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = HashSet(p.getStringSet(KEY, emptySet()) ?: emptySet())
        set.remove(domain)
        p.edit().putStringSet(KEY, set).apply()
    }

    fun isBlocked(ctx: Context, domain: String): Boolean {
        val d = domain.trimEnd('.').lowercase()
        val list = all(ctx)
        return list.any { d == it || d.endsWith(".$it") }
    }
}
