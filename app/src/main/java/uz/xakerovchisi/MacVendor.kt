package uz.xakerovchisi

import java.net.HttpURLConnection
import java.net.URL

/** MAC manzil prefiksi (OUI) orqali ishlab chiqaruvchini topadi. */
object MacVendor {

    private val cache = HashMap<String, String?>()

    // Eng ko'p uchraydigan brendlarning OUI prefikslari (internetsiz ham ishlashi uchun).
    private val offline = mapOf(
        "3C5AB4" to "Xiaomi", "F8A45F" to "Xiaomi", "64CC2E" to "Xiaomi", "9C99A0" to "Xiaomi",
        "286C07" to "Xiaomi", "9C28EF" to "Xiaomi", "34CE00" to "Xiaomi", "F02F74" to "Xiaomi",
        "A4C138" to "Samsung", "001632" to "Samsung", "88326E" to "Samsung", "5C0A5B" to "Samsung",
        "CC07AB" to "Samsung", "BCE63F" to "Samsung", "3C5A37" to "Samsung",
        "F4F1E1" to "Huawei", "48435A" to "Huawei", "00E0FC" to "Huawei", "D8490B" to "Huawei",
        "C83870" to "Huawei", "202BC1" to "Huawei",
        "A4C361" to "Apple", "F0989D" to "Apple", "001451" to "Apple", "3C0754" to "Apple",
        "D0817A" to "Apple", "F82793" to "Apple", "6845071" to "Apple",
        "001A11" to "Google", "F4F5D8" to "Google", "3C28A6" to "Google",
        "B827EB" to "Raspberry Pi", "DCA632" to "Raspberry Pi", "E45F01" to "Raspberry Pi",
        "00242C" to "ASUS", "08606E" to "ASUS", "D017C2" to "ASUS", "2C56DC" to "ASUS",
        "C44BD1" to "TP-Link", "5C628B" to "TP-Link", "EC086B" to "TP-Link", "F4F26D" to "TP-Link",
        "000D88" to "D-Link", "1C7EE5" to "D-Link", "B0C554" to "D-Link",
        "E89F80" to "Hisense", "D8EB97" to "Oppo", "4C5499" to "Oppo", "C0EEFB" to "vivo",
        "8CF5A3" to "vivo", "F4A475" to "vivo", "8871B4" to "Nokia", "D45D64" to "Sony"
    )

    /** Natija darhol kerak bo'lganda — faqat offline jadvaldan (tarmoqqa chiqmaydi). */
    fun quick(mac: String?): String? {
        if (mac == null) return null
        val prefix = mac.replace(":", "").replace("-", "").uppercase().take(6)
        return offline[prefix]
    }

    /** Onlayn tekshiradi (api.macvendors.com), topilmasa offline jadvalga qaytadi. Tarmoq kerak, shuning uchun fon oqimida chaqiring. */
    fun lookup(mac: String?): String? {
        if (mac == null) return null
        synchronized(cache) {
            if (cache.containsKey(mac)) return cache[mac]
        }
        var result = quick(mac)
        try {
            val conn = URL("https://api.macvendors.com/$mac").openConnection() as HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.requestMethod = "GET"
            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().readText().trim()
                if (body.isNotEmpty()) result = body
            }
            conn.disconnect()
        } catch (e: Exception) {
            // tarmoq yo'q yoki limitga tegdi — offline natija bilan qolamiz
        }
        synchronized(cache) { cache[mac] = result }
        return result
    }
}
