package uz.xakerovchisi

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class VtOutcome {
    data class Found(
        val malicious: Int,
        val suspicious: Int,
        val harmless: Int,
        val undetected: Int,
        val names: List<String>
    ) : VtOutcome()

    object NotFound : VtOutcome()
    data class Error(val message: String) : VtOutcome()
}

/** VirusTotal API v3 bilan ishlash: hash bo'yicha qidirish, kerak bo'lsa faylni yuklab tekshirish. */
object VirusTotal {

    fun fileName(cr: ContentResolver, uri: Uri): String {
        var name = "fayl"
        try {
            cr.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
            }
        } catch (e: Exception) {
        }
        return name
    }

    fun fileSize(cr: ContentResolver, uri: Uri): Long {
        var size = -1L
        try {
            cr.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.SIZE)
                if (i >= 0 && c.moveToFirst()) size = c.getLong(i)
            }
        } catch (e: Exception) {
        }
        return size
    }

    fun sha256(cr: ContentResolver, uri: Uri): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val stream = cr.openInputStream(uri) ?: return null
            stream.use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun lookup(apiKey: String, hash: String): VtOutcome {
        return try {
            val c = URL("https://www.virustotal.com/api/v3/files/$hash").openConnection() as HttpURLConnection
            c.requestMethod = "GET"
            c.setRequestProperty("x-apikey", apiKey)
            c.connectTimeout = 10000
            c.readTimeout = 15000
            val code = c.responseCode
            when (code) {
                200 -> {
                    val body = c.inputStream.bufferedReader().use { it.readText() }
                    c.disconnect()
                    parseReport(body)
                }
                404 -> {
                    c.disconnect()
                    VtOutcome.NotFound
                }
                401, 403 -> {
                    c.disconnect()
                    VtOutcome.Error("API kalit noto'g'ri")
                }
                else -> {
                    c.disconnect()
                    VtOutcome.Error("Server xatosi ($code)")
                }
            }
        } catch (e: Exception) {
            VtOutcome.Error("Internetga ulanib bo'lmadi")
        }
    }

    private fun parseReport(body: String): VtOutcome {
        val attrs = JSONObject(body).optJSONObject("data")?.optJSONObject("attributes")
            ?: return VtOutcome.Error("Javobni o'qib bo'lmadi")
        val stats = attrs.optJSONObject("last_analysis_stats")
        val malicious = stats?.optInt("malicious", 0) ?: 0
        val suspicious = stats?.optInt("suspicious", 0) ?: 0
        val harmless = stats?.optInt("harmless", 0) ?: 0
        val undetected = stats?.optInt("undetected", 0) ?: 0
        val namesArr = attrs.optJSONArray("names")
        val names = ArrayList<String>()
        if (namesArr != null) {
            for (i in 0 until minOf(namesArr.length(), 5)) names.add(namesArr.optString(i))
        }
        return VtOutcome.Found(malicious, suspicious, harmless, undetected, names)
    }

    fun upload(apiKey: String, cr: ContentResolver, uri: Uri, fileName: String): String? {
        return try {
            val boundary = "----XakerOvchisi${System.currentTimeMillis()}"
            val c = URL("https://www.virustotal.com/api/v3/files").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("x-apikey", apiKey)
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            c.connectTimeout = 15000
            c.readTimeout = 120000
            val out = c.outputStream
            out.write("--$boundary\r\n".toByteArray())
            out.write(
                "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n".toByteArray()
            )
            out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
            cr.openInputStream(uri)?.use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
            out.flush()
            val code = c.responseCode
            if (code !in 200..299) {
                c.disconnect()
                return null
            }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            JSONObject(body).optJSONObject("data")?.optString("id")
        } catch (e: Exception) {
            null
        }
    }

    /** Tahlil tugaguncha so'raydi (taxminan 2 daqiqagacha). onTick: o'tgan soniyalar. */
    fun pollAnalysis(apiKey: String, id: String, onTick: (Int) -> Unit): VtOutcome {
        repeat(24) { i ->
            onTick(i * 5)
            try {
                val c = URL("https://www.virustotal.com/api/v3/analyses/$id").openConnection() as HttpURLConnection
                c.requestMethod = "GET"
                c.setRequestProperty("x-apikey", apiKey)
                c.connectTimeout = 10000
                c.readTimeout = 15000
                val code = c.responseCode
                if (code == 200) {
                    val body = c.inputStream.bufferedReader().use { it.readText() }
                    c.disconnect()
                    val attrs = JSONObject(body).optJSONObject("data")?.optJSONObject("attributes")
                    if (attrs?.optString("status") == "completed") {
                        val stats = attrs.optJSONObject("stats")
                        return VtOutcome.Found(
                            stats?.optInt("malicious", 0) ?: 0,
                            stats?.optInt("suspicious", 0) ?: 0,
                            stats?.optInt("harmless", 0) ?: 0,
                            stats?.optInt("undetected", 0) ?: 0,
                            emptyList()
                        )
                    }
                } else {
                    c.disconnect()
                }
            } catch (e: Exception) {
            }
            Thread.sleep(5000)
        }
        return VtOutcome.Error("Tahlil juda uzoq davom etmoqda, keyinroq qayta urinib ko'ring")
    }
}
