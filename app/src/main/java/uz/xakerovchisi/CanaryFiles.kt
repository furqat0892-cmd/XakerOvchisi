package uz.xakerovchisi

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.provider.MediaStore
import java.io.File

/**
 * "O'lja" (canary) fayllar: telefon "Yuklab olishlar" (Download) papkasiga jozibador nomli
 * soxta fayllar qo'yiladi. Agar kimdir (ruxsatsiz kirib yoki boshqa ilova orqali) ularni
 * ochsa/o'zgartirsa/o'chirsa — bu telefon fayllari titkilanayotganidan dalolat beradi va
 * ogohlantirish yuboriladi. Hammasi faqat shu qurilma ichida — hech qanday tashqi serverga
 * signal yubormaydi.
 */
object CanaryFiles {
    private val NAMES = listOf("Bank_kartalar.txt", "Parollar_zaxira.txt", "Pasport_nusxa.txt")
    private const val SUBFOLDER = "Muhim_hujjatlar"
    private var observer: FileObserver? = null

    private val CONTENT = "Diqqat: bu — himoya maqsadida qo'yilgan soxta (o'lja) hujjat.\n" +
        "Agar bu faylni ko'rayotgan bo'lsangiz-u, uni o'zingiz qo'ymagan bo'lsangiz — " +
        "telefon fayllariga ruxsatsiz kirilgan bo'lishi mumkin."

    /** O'lja fayllar joylashgan haqiqiy papka (Download/Muhim_hujjatlar) — FileObserver shuni kuzatadi. */
    fun dir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val d = File(downloads, SUBFOLDER)
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** Fayllarni yaratadi (agar hali yo'q bo'lsa). Kuzatuvchi ishga tushishidan OLDIN chaqiriladi. */
    fun setup(ctx: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setupViaMediaStore(ctx)
            } else {
                setupDirect()
            }
        } catch (e: Exception) {
        }
    }

    private fun setupDirect() {
        val d = dir()
        for (name in NAMES) {
            val f = File(d, name)
            if (!f.exists()) {
                try {
                    f.writeText(CONTENT)
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun setupViaMediaStore(ctx: Context) {
        val d = dir()
        // Agar fayllar fizik yo'lda allaqachon bo'lsa, qayta yaratmaymiz (MediaStore'ga ikki marta yozilmasin).
        val missing = NAMES.filter { !File(d, it).exists() }
        if (missing.isEmpty()) return
        val resolver = ctx.applicationContext.contentResolver
        for (name in missing) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: continue
                resolver.openOutputStream(uri)?.use { it.write(CONTENT.toByteArray()) }
            } catch (e: Exception) {
            }
        }
    }

    @Suppress("DEPRECATION")
    fun start(ctx: Context) {
        stop()
        val app = ctx.applicationContext
        val d = dir()
        val mask = FileObserver.ACCESS or FileObserver.OPEN or FileObserver.MODIFY or
            FileObserver.DELETE or FileObserver.MOVED_FROM
        val obs = object : FileObserver(d.absolutePath, mask) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null || path !in NAMES) return
                val label = when (event and ALL_EVENTS) {
                    FileObserver.DELETE -> "o'chirib yuborildi"
                    FileObserver.MOVED_FROM -> "ko'chirildi / olib chiqildi"
                    FileObserver.MODIFY -> "o'zgartirildi"
                    else -> "ochildi"
                }
                EventLog.add(
                    app, "Tuzoq", 3,
                    "⚠️ O'LJA FAYLGA TEGINILDI: \"$path\" fayli $label. Telefon fayllari titkilanayotgan bo'lishi mumkin!"
                )
                Alerts.raise(app, "Tuzoq", 3, "Diqqat! O'lja fayl ($path) $label — telefon titkilanmoqda!")
            }
        }
        obs.startWatching()
        observer = obs
    }

    fun stop() {
        observer?.stopWatching()
        observer = null
    }
}
