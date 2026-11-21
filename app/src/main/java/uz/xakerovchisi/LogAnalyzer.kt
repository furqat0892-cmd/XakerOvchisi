package uz.xakerovchisi

data class Finding(val ip: String, val score: Int, val reasons: List<String>)

/** Server loglarini (auth.log, access.log va h.k.) tahlil qilib, shubhali IP'larni topadi. */
object LogAnalyzer {

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val ipRe = Regex("\\b(\\d{1,3}(?:\\.\\d{1,3}){3})\\b")
    private val statusRe = Regex("\"\\s(\\d{3})\\s")
    private val failRe = r(
        "failed password|invalid user|authentication failure|failed login|login failed|too many authentication"
    )

    private val attacks = listOf(
        "SQL injection" to r("union\\s+select|union%20select|' or '?1'?='?1|or 1=1|sleep\\(|information_schema|benchmark\\(|%27%20or"),
        "Katalog bo'ylab kirish" to r("\\.\\./|%2e%2e|/etc/passwd|/etc/shadow|boot\\.ini"),
        "XSS" to r("<script|%3cscript|onerror=|javascript:"),
        "Skaner / zaif joy qidirish" to r("wp-login|xmlrpc\\.php|phpmyadmin|/\\.env|/\\.git|wp-admin|cgi-bin|shell\\.php|eval-stdin|nikto|sqlmap|masscan|nmap|zgrab"),
        "Buyruq kiritish" to r(";\\s*(cat|wget|curl|bash|sh)\\s|\\|\\s*(sh|bash)\\b")
    )

    private class Acc {
        var fail = 0
        var notFound = 0
        var denied = 0
        val att = LinkedHashMap<String, Int>()
    }

    private fun validIp(ip: String): Boolean =
        ip.split('.').all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }

    fun analyze(lines: Sequence<String>): List<Finding> {
        val map = HashMap<String, Acc>()
        for (line in lines.take(300_000)) {
            val ip = ipRe.find(line)?.groupValues?.get(1) ?: continue
            if (!validIp(ip)) continue
            val a = map.getOrPut(ip) { Acc() }
            if (failRe.containsMatchIn(line)) a.fail++
            when (statusRe.find(line)?.groupValues?.get(1)) {
                "404" -> a.notFound++
                "401", "403" -> a.denied++
            }
            for ((name, re) in attacks) {
                if (re.containsMatchIn(line)) a.att[name] = (a.att[name] ?: 0) + 1
            }
        }

        val out = ArrayList<Finding>()
        for ((ip, a) in map) {
            val reasons = ArrayList<String>()
            var score = 0
            if (a.fail >= 5) {
                reasons.add("${a.fail} marta noto'g'ri parol / kirish urinishi")
                score += a.fail
            }
            if (a.denied >= 10) {
                reasons.add("${a.denied} ta 401/403 (ruxsat rad etilgan)")
                score += a.denied / 2
            }
            if (a.notFound >= 20) {
                reasons.add("${a.notFound} ta 404 (skanerlash ehtimoli)")
                score += a.notFound / 4
            }
            for ((name, c) in a.att) {
                reasons.add("$name izlari: $c marta")
                score += c * 5
            }
            if (reasons.isNotEmpty()) out.add(Finding(ip, score, reasons))
        }
        return out.sortedByDescending { it.score }
    }
}
