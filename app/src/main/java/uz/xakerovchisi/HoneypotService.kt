package uz.xakerovchisi

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** Soxta portlarni tinglaydi; ulanishga uringanlarning IP manzilini qayd etadi. */
class HoneypotService : Service() {

    companion object {
        @Volatile
        var running = false
    }

    private val services = linkedMapOf(
        2222 to "SSH",
        2323 to "Telnet",
        8022 to "SSH",
        8080 to "HTTP",
        8888 to "HTTP",
        5555 to "ADB"
    )

    private val banners = mapOf(
        2222 to "SSH-2.0-OpenSSH_8.9p1 Ubuntu-3\r\n",
        8022 to "SSH-2.0-OpenSSH_7.4\r\n",
        2323 to "login: "
    )

    private val sockets = ArrayList<ServerSocket>()
    private val lastSeen = HashMap<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, n)
        }
        if (!running) {
            running = true
            services.keys.forEach { listen(it) }
            CanaryFiles.setup(this)
            CanaryFiles.start(this)
            EventLog.add(this, "Tuzoq", 1, "Tuzoq yoqildi")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        CanaryFiles.stop()
        synchronized(sockets) {
            sockets.forEach {
                try {
                    it.close()
                } catch (e: Exception) {
                }
            }
            sockets.clear()
        }
        EventLog.add(this, "Tuzoq", 1, "Tuzoq o'chirildi")
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        Alerts.ensureChannels(this)
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, Alerts.CH_SERVICE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Tuzoq faol")
            .setContentText("Soxta portlar tinglanmoqda")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun listen(port: Int) {
        thread(isDaemon = true) {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(port))
                synchronized(sockets) { sockets.add(ss) }
                while (running) {
                    val c = ss.accept()
                    thread(isDaemon = true) { handle(c, port) }
                }
            } catch (e: Exception) {
                if (running) {
                    EventLog.add(this, "Tuzoq", 1, "Port $port ochilmadi (band bo'lishi mumkin)")
                }
            }
        }
    }

    private fun handle(c: Socket, port: Int) {
        try {
            val ip = c.inetAddress?.hostAddress ?: "?"
            c.soTimeout = 3000
            val banner = banners[port]
            if (banner != null) {
                try {
                    val o = c.getOutputStream()
                    o.write(banner.toByteArray())
                    o.flush()
                } catch (e: Exception) {
                }
            }
            val buf = ByteArray(256)
            val n = try {
                c.getInputStream().read(buf)
            } catch (e: Exception) {
                -1
            }
            val first = if (n > 0) {
                String(buf, 0, n, Charsets.ISO_8859_1).filter { it.code in 32..126 }.take(60)
            } else ""
            if (ip == "127.0.0.1") return
            val count = AttackerStats.record(this, ip)
            val now = System.currentTimeMillis()
            val skip = synchronized(lastSeen) {
                val l = lastSeen[ip] ?: 0L
                if (now - l < 30000) {
                    true
                } else {
                    lastSeen[ip] = now
                    false
                }
            }
            if (skip) return
            val geo = IpGeo.lookup(ip)
            var msg = "Port $port (${services[port]}) ga ulanish urinishi: $ip"
            if (geo != null) msg += "\nJoylashuv: $geo"
            if (first.isNotBlank()) msg += "\nYuborgan ma'lumot: $first"
            msg += if (count >= AttackerStats.DANGER_THRESHOLD) {
                "\n⚠️ XAVFLI IP — jami $count marta urindi!"
            } else {
                "\n(jami $count marta urindi)"
            }
            Alerts.raise(this, "Tuzoq", 3, msg)
        } catch (e: Exception) {
        } finally {
            try {
                c.close()
            } catch (e: Exception) {
            }
        }
    }
}
