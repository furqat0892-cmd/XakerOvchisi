package uz.xakerovchisi

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * Faqat DNS so'rovlarini kuzatuvchi yengil "VPN". Telefonning BARCHA trafigini emas, faqat
 * DNS (domen) so'rovlarini ushlab turadi (bu uchun faqat soxta DNS-serverga yo'naltirilgan
 * yo'nalish qo'shiladi, umumiy 0.0.0.0/0 yo'nalish YO'Q — shuning uchun boshqa trafik
 * (veb-sahifalar, ilovalar) odatdagidek, to'g'ridan-to'g'ri ishlashda davom etadi).
 * Har bir so'ralgan domen bloklash ro'yxati bilan solishtiriladi; mos kelsa — rad etiladi
 * (NXDOMAIN) va ogohlantirish beriladi; aks holda haqiqiy DNS serverga (8.8.8.8) yuborilib,
 * javob qaytariladi — foydalanuvchi uchun hech narsa o'zgarmaydi.
 */
class DnsVpnService : VpnService() {

    companion object {
        @Volatile var running = false
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val DNS_ADDRESS = "10.111.222.2"
        private const val UPSTREAM_DNS = "8.8.8.8"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val seenDomains = java.util.Collections.synchronizedSet(HashSet<String>())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        try {
            val builder = Builder()
            builder.setSession("Xaker Ovchisi DNS kuzatuvi")
            builder.addAddress(VPN_ADDRESS, 32)
            builder.addDnsServer(DNS_ADDRESS)
            builder.addRoute(DNS_ADDRESS, 32)
            val iface = builder.establish()
            if (iface == null) {
                EventLog.add(this, "Tarmoq", 1, "DNS kuzatuvini yoqib bo'lmadi (ruxsat berilmagan)")
                return START_NOT_STICKY
            }
            vpnInterface = iface
            running = true
            EventLog.add(this, "Tarmoq", 1, "DNS kuzatuvi yoqildi")
            thread(isDaemon = true) { loop(iface) }
        } catch (e: Exception) {
            EventLog.add(this, "Tarmoq", 1, "DNS kuzatuvini yoqib bo'lmadi: ${e.message}")
            running = false
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
        }
        vpnInterface = null
        EventLog.add(this, "Tarmoq", 1, "DNS kuzatuvi o'chirildi")
        super.onDestroy()
    }

    private fun loop(iface: ParcelFileDescriptor) {
        val input = FileInputStream(iface.fileDescriptor)
        val output = FileOutputStream(iface.fileDescriptor)
        val buf = ByteArray(32767)
        while (running) {
            val len = try {
                input.read(buf)
            } catch (e: Exception) {
                -1
            }
            if (len <= 0) continue
            try {
                handlePacket(buf, len, output)
            } catch (e: Exception) {
            }
        }
    }

    private fun handlePacket(buf: ByteArray, len: Int, output: FileOutputStream) {
        if (len < 20) return
        val verIhl = buf[0].toInt() and 0xFF
        if (verIhl shr 4 != 4) return // faqat IPv4
        val ihl = (verIhl and 0x0F) * 4
        if (len < ihl + 8) return
        val protocol = buf[9].toInt() and 0xFF
        if (protocol != 17) return // faqat UDP
        val udpOffset = ihl
        val dstPort = ((buf[udpOffset + 2].toInt() and 0xFF) shl 8) or (buf[udpOffset + 3].toInt() and 0xFF)
        if (dstPort != 53) return
        val dnsOffset = udpOffset + 8
        if (dnsOffset >= len) return
        val dnsBytes = buf.copyOfRange(dnsOffset, len)

        val domain = parseDnsQuestionName(dnsBytes)
        if (!domain.isNullOrEmpty()) {
            val isNew = seenDomains.add(domain)
            if (isNew) {
                val suspicious = DomainHeuristic.isSuspicious(domain)
                val alreadyBlocked = DnsBlocklist.isBlocked(applicationContext, domain)
                if (suspicious && !alreadyBlocked && DnsBlocklist.autoBlockEnabled(applicationContext)) {
                    DnsBlocklist.add(applicationContext, domain)
                    EventLog.add(applicationContext, "Tarmoq", 3, "⛔ Shubhali domen avtomatik bloklandi: $domain")
                    Alerts.raise(applicationContext, "Tarmoq", 3, "Shubhali domen avtomatik bloklandi: $domain")
                } else if (suspicious) {
                    EventLog.add(applicationContext, "Tarmoq", 3, "⚠️ Shubhali ko'rinadigan domenga so'rov: $domain")
                    Alerts.raise(
                        applicationContext, "Tarmoq", 2,
                        "Shubhali domenga so'rov aniqlandi: $domain — \"Havola\" bo'limida tekshiring"
                    )
                } else {
                    EventLog.add(applicationContext, "Tarmoq", 1, "Yangi domenga so'rov: $domain")
                }
            }

            if (DnsBlocklist.isBlocked(applicationContext, domain)) {
                EventLog.add(applicationContext, "Tarmoq", 3, "⚠️ Bloklangan domenga urinish: $domain")
                val nx = buildDnsNxDomain(dnsBytes)
                output.write(buildIpUdpReply(buf, ihl, udpOffset, nx))
                return
            }
        }

        try {
            DatagramSocket().use { socket ->
                protect(socket)
                socket.soTimeout = 4000
                socket.send(DatagramPacket(dnsBytes, dnsBytes.size, InetAddress.getByName(UPSTREAM_DNS), 53))
                val respBuf = ByteArray(1500)
                val respPacket = DatagramPacket(respBuf, respBuf.size)
                socket.receive(respPacket)
                val respBytes = respBuf.copyOfRange(0, respPacket.length)
                output.write(buildIpUdpReply(buf, ihl, udpOffset, respBytes))
            }
        } catch (e: Exception) {
            // javob kelmasa, shunchaki o'tkazib yuboramiz (so'rovchi qayta urinadi)
        }
    }

    private fun parseDnsQuestionName(dns: ByteArray): String? {
        if (dns.size < 13) return null
        var pos = 12
        val sb = StringBuilder()
        while (pos < dns.size) {
            val lenByte = dns[pos].toInt() and 0xFF
            if (lenByte == 0) {
                pos++
                break
            }
            if (lenByte and 0xC0 == 0xC0) break // kompressiya ko'rsatkichi — e'tiborsiz qoldiramiz
            if (pos + 1 + lenByte > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(dns, pos + 1, lenByte, Charsets.US_ASCII))
            pos += 1 + lenByte
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    private fun buildDnsNxDomain(query: ByteArray): ByteArray {
        val resp = query.copyOf()
        if (resp.size < 4) return resp
        resp[2] = (resp[2].toInt() or 0x80).toByte() // QR=1 (javob)
        resp[3] = ((resp[3].toInt() and 0xF0) or 0x03).toByte() // RCODE=3 (NXDOMAIN)
        return resp
    }

    /** Asl IP/UDP sarlavhasidan foydalanib, teskari yo'nalishda (javob) paket quradi. */
    private fun buildIpUdpReply(original: ByteArray, ihl: Int, udpOffset: Int, dnsPayload: ByteArray): ByteArray {
        val udpLen = 8 + dnsPayload.size
        val totalLen = ihl + udpLen
        val packet = ByteArray(totalLen)
        System.arraycopy(original, 0, packet, 0, ihl)
        for (i in 0 until 4) {
            val tmp = packet[12 + i]
            packet[12 + i] = packet[16 + i]
            packet[16 + i] = tmp
        }
        packet[2] = ((totalLen shr 8) and 0xFF).toByte()
        packet[3] = (totalLen and 0xFF).toByte()
        packet[10] = 0
        packet[11] = 0
        val ipChecksum = checksum(packet, 0, ihl)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        val clientPort = ((original[udpOffset].toInt() and 0xFF) shl 8) or
            (original[udpOffset + 1].toInt() and 0xFF)
        packet[ihl] = 0
        packet[ihl + 1] = 53
        packet[ihl + 2] = ((clientPort shr 8) and 0xFF).toByte()
        packet[ihl + 3] = (clientPort and 0xFF).toByte()
        packet[ihl + 4] = ((udpLen shr 8) and 0xFF).toByte()
        packet[ihl + 5] = (udpLen and 0xFF).toByte()
        packet[ihl + 6] = 0
        packet[ihl + 7] = 0
        System.arraycopy(dnsPayload, 0, packet, ihl + 8, dnsPayload.size)
        return packet
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (length % 2 == 1) {
            sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }
}
