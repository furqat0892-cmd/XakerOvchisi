package uz.xakerovchisi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Hodisani jurnalga yozadi, bildirishnoma ko'rsatadi va Telegram'ga yuboradi. */
object Alerts {
    const val CH_ALERT = "alerts"
    const val CH_SERVICE = "service"
    private val nid = AtomicInteger(100)

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Xavf ogohlantirishlari", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Tuzoq xizmati", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun raise(ctx: Context, type: String, severity: Int, text: String) {
        val app = ctx.applicationContext
        EventLog.add(app, type, severity, text)
        if (severity < 2) return
        try {
            ensureChannels(app)
            val pi = PendingIntent.getActivity(
                app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val n = Notification.Builder(app, CH_ALERT)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Xaker Ovchisi: $type")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            app.getSystemService(NotificationManager::class.java).notify(nid.incrementAndGet(), n)
        } catch (e: Exception) {
            // bildirishnoma ruxsati yo'q bo'lishi mumkin
        }
        sendTelegram(app, "⚠️ $type\n$text", null)
    }

    /** onDone fon oqimida chaqiriladi. */
    fun sendTelegram(ctx: Context, msg: String, onDone: ((Boolean) -> Unit)?) {
        val p = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val token = (p.getString("tg_token", "") ?: "").trim()
        val chat = (p.getString("tg_chat", "") ?: "").trim()
        if (token.isEmpty() || chat.isEmpty()) {
            onDone?.invoke(false)
            return
        }
        thread {
            var ok = false
            try {
                val c = URL("https://api.telegram.org/bot$token/sendMessage").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val body = "chat_id=" + URLEncoder.encode(chat, "UTF-8") +
                    "&text=" + URLEncoder.encode(msg, "UTF-8")
                c.outputStream.use { it.write(body.toByteArray()) }
                ok = c.responseCode == 200
                c.disconnect()
            } catch (e: Exception) {
                ok = false
            }
            onDone?.invoke(ok)
        }
    }
}
