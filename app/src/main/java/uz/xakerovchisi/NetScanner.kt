package uz.xakerovchisi

import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** O'z Wi-Fi tarmog'ingizdagi (/24) faol qurilmalarni topadi. */
object NetScanner {

    fun localIpv4(): String? {
        try {
            val ifs = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in ifs) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in ni.inetAddresses) {
                    if (a is Inet4Address && a.isSiteLocalAddress) return a.hostAddress
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    fun scan(me: String): List<String> {
        val prefix = me.substringBeforeLast('.')
        val pool = Executors.newFixedThreadPool(48)
        val futures = (1..254).map { n ->
            val ip = "$prefix.$n"
            pool.submit(Callable<String?> { if (ip == me || alive(ip)) ip else null })
        }
        val res = ArrayList<String>()
        for (f in futures) {
            try {
                f.get()?.let { res.add(it) }
            } catch (e: Exception) {
            }
        }
        pool.shutdown()
        return res
    }

    /** /proc/net/arp orqali shu IP uchun MAC manzilni topadi (Android'da ko'pincha o'qiladigan fayl). */
    fun macFor(ip: String): String? {
        try {
            val lines = java.io.File("/proc/net/arp").readLines()
            for (line in lines.drop(1)) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4 && parts[0] == ip) {
                    val mac = parts[3]
                    if (mac != "00:00:00:00:00:00") return mac
                }
            }
        } catch (e: Exception) {
        }
        return null
    }

    private fun alive(ip: String): Boolean {
        try {
            if (InetAddress.getByName(ip).isReachable(250)) return true
        } catch (e: Exception) {
        }
        for (port in intArrayOf(80, 443, 53, 8080, 22)) {
            try {
                Socket().use {
                    it.connect(InetSocketAddress(ip, port), 200)
                    return true
                }
            } catch (e: ConnectException) {
                // "refused" — qurilma bor, port yopiq
                return true
            } catch (e: Exception) {
            }
        }
        return false
    }
}
