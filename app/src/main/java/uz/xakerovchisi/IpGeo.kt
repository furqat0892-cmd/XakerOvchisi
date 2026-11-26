package uz.xakerovchisi

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Ulangan IP manzilning taxminiy joylashuvini (mamlakat/shahar/provayder) topadi. */
object IpGeo {

    private val cache = HashMap<String, String?>()

    private fun isPrivate(ip: String): Boolean {
        return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("127.") ||
            ip.startsWith("169.254.") || Regex("^172\\.(1[6-9]|2\\d|3[0-1])\\.").containsMatchIn(ip)
    }

    /** Tarmoq so'rovi yuboradi — fon oqimida chaqiring. Lokal (uy) IP uchun darhol qaytadi. */
    fun lookup(ip: String): String? {
        if (isPrivate(ip)) return "Lokal tarmoq (shu Wi-Fi ichida)"
        synchronized(cache) { if (cache.containsKey(ip)) return cache[ip] }
        var result: String? = null
        try {
            val conn = URL("https://ipapi.co/$ip/json/").openConnection() as HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val o = JSONObject(body)
                if (!o.optBoolean("error", false)) {
                    val city = o.optString("city", "")
                    val country = o.optString("country_name", "")
                    val org = o.optString("org", "")
                    result = listOf(city, country, org).filter { it.isNotBlank() }.joinToString(", ")
                    if (result.isBlank()) result = null
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            // tarmoq yo'q yoki limitga tegdi
        }
        synchronized(cache) { cache[ip] = result }
        return result
    }
}
