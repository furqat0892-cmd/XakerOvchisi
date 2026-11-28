package uz.xakerovchisi

/**
 * Domen nomiga qarab, u "shubhali" ko'rinishini taxmin qiladi (heuristika — 100% aniq emas,
 * lekin ko'p uchraydigan fishing/zararli domen naqshlariga asoslangan).
 */
object DomainHeuristic {

    private val BRANDS = listOf(
        "google", "facebook", "instagram", "whatsapp", "telegram", "paypal", "amazon",
        "microsoft", "apple", "netflix", "bank", "visa", "mastercard", "gmail", "youtube"
    )

    private val SUSPICIOUS_TLDS = setOf(
        "xyz", "top", "click", "work", "gq", "tk", "ml", "cf", "ga", "loan", "win", "zip", "country"
    )

    private val SUSPICIOUS_WORDS = setOf("secure", "login", "verify", "update", "account", "confirm", "signin")

    fun isSuspicious(domain: String): Boolean {
        val d = domain.lowercase().trimEnd('.')
        if (d.isEmpty()) return false

        val tld = d.substringAfterLast('.', "")
        if (tld in SUSPICIOUS_TLDS) return true

        // Brend nomini o'z ichiga oladi, lekin uning HAQIQIY domeni emas (masalan google-secure-login.xyz)
        for (b in BRANDS) {
            val isRealDomain = d == "$b.com" || d.endsWith(".$b.com") || d.endsWith(".$b.net")
            if (!isRealDomain && d.contains(b)) {
                val hasSuspiciousWord = SUSPICIOUS_WORDS.any { d.contains(it) }
                val hasHyphen = d.contains("-")
                if (hasSuspiciousWord || hasHyphen) return true
            }
        }

        // Asosiy qism (subdomensiz) juda uzun va ko'p raqamli — tasodifiy generatsiya qilingan nomga o'xshaydi
        val mainLabel = d.substringBeforeLast('.').substringAfterLast('.')
        if (mainLabel.length >= 10) {
            val digits = mainLabel.count { it.isDigit() }
            if (digits.toDouble() / mainLabel.length > 0.3) return true
        }

        return false
    }
}
