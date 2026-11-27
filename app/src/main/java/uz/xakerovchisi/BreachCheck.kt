package uz.xakerovchisi

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Email manzil avvalgi ma'lumot sizishlarida (data breach) chiqqan-chiqmaganini tekshiradi. */
object BreachCheck {

    fun check(email: String): String {
        return try {
            val enc = URLEncoder.encode(email, "UTF-8")
            val conn = URL("https://api.xposedornot.com/v1/check-email/$enc").openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            parse(body)
        } catch (e: Exception) {
            "Tekshirib bo'lmadi — internet aloqasini tekshiring va qayta urinib ko'ring."
        }
    }

    private fun parse(body: String): String {
        if (body.isBlank()) return "Natija olinmadi, qayta urinib ko'ring."
        return try {
            val o = JSONObject(body)
            val names = ArrayList<String>()
            val breachesField = o.opt("breaches")
            if (breachesField is JSONArray) {
                for (i in 0 until breachesField.length()) {
                    when (val inner = breachesField.opt(i)) {
                        is JSONArray -> for (j in 0 until inner.length()) names.add(inner.optString(j))
                        is String -> names.add(inner)
                        else -> {}
                    }
                }
            }
            if (names.isNotEmpty()) {
                "TOPILDI: bu email ${names.size} ta ma'lum ma'lumot sizishida uchragan: " +
                    names.joinToString(", ") +
                    ".\nTavsiya: shu xizmatlarda parolni darhol o'zgartiring va imkon bo'lsa 2FA yoqing."
            } else {
                "Hech qanday ma'lum ma'lumot sizishida uchramagan."
            }
        } catch (e: Exception) {
            "Natijani aniqlab bo'lmadi (server javobi tanib bo'lmadi)."
        }
    }
}
