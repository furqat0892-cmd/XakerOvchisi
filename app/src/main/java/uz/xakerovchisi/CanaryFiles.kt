package uz.xakerovchisi

import android.content.Context
import android.os.FileObserver
import java.io.File

/**
 * "O'lja" (canary) fayllar: telefon xotirasiga jozibador nomli soxta fayllar qo'yiladi.
 * Agar kimdir (ruxsatsiz kirib yoki boshqa ilova orqali) ularni ochsa/o'zgartirsa/o'chirsa —
 * bu telefon fayllari titkilanayotganidan dalolat beradi va ogohlantirish yuboriladi.
 * Hammasi faqat shu qurilma ichida — hech qanday tashqi serverga signal yubormaydi.
 */
object CanaryFiles {
    private val NAMES = listOf("Bank_kartalar.txt", "Parollar_zaxira.txt", "Pasport_nusxa.txt")
    private var observer: FileObserver? = null

    fun dir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null), "Muhim_hujjatlar")
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** Fayllarni yaratadi (agar hali yo'q bo'lsa). Kuzatuvchi ishga tushishidan OLDIN chaqiriladi. */
    fun setup(ctx: Context) {
        val d = dir(ctx)
        for (name in NAMES) {
            val f = File(d, name)
            if (!f.exists()) {
                try {
                    f.writeText(
                        "Diqqat: bu — himoya maqsadida qo'yilgan soxta (o'lja) hujjat.\n" +
                            "Agar bu faylni ko'rayotgan bo'lsangiz-u, uni o'zingiz qo'ymagan bo'lsangiz — " +
                            "telefon fayllariga ruxsatsiz kirilgan bo'lishi mumkin."
                    )
                } catch (e: Exception) {
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun start(ctx: Context) {
        stop()
        val app = ctx.applicationContext
        val d = dir(app)
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
