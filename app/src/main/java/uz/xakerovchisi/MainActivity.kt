package uz.xakerovchisi

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.journeyapps.barcodescanner.IntentIntegrator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val BG = Color.parseColor("#0D1117")
    private val CARD = Color.parseColor("#161B22")
    private val GRAY = Color.parseColor("#30363D")
    private val GREEN = Color.parseColor("#2EA043")
    private val RED = Color.parseColor("#F85149")
    private val AMBER = Color.parseColor("#D29922")
    private val TEXT = Color.parseColor("#E6EDF3")
    private val MUTED = Color.parseColor("#8B949E")

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: android.widget.FrameLayout
    private lateinit var statusLine: TextView
    private lateinit var tabButtons: List<Button>
    private var current = 0

    // so'nggi natijalar (bo'limlar almashganda yo'qolmasligi uchun)
    private var scanning = false
    private var lastScan: List<String>? = null
    private var lastMe: String? = null
    private var auditing = false
    private var lastApps: List<AppRisk>? = null
    private var analyzing = false
    private var lastLog: List<Finding>? = null
    private var vtBusy = false
    private var vtStage = ""
    private var vtFileName: String? = null
    private var vtResult: VtOutcome.Found? = null
    private var vtError: String? = null
    private var vtNotFound = false
    private var vtTooLarge = false
    private var vtPendingUri: Uri? = null
    private var lkBusy = false
    private var lkStage = ""
    private var lkTarget: String? = null
    private var lkResult: VtOutcome.Found? = null
    private var lkError: String? = null

    private val refresher = Runnable { if (current == 0) show(0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = CARD

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(BG)

        val title = tv("XAKER OVCHISI", 18f, GREEN, true)
        title.setPadding(dp(16), dp(14), dp(16), dp(0))
        root.addView(title)

        statusLine = tv("", 12f, MUTED)
        statusLine.setPadding(dp(16), dp(2), dp(16), dp(8))
        root.addView(statusLine)

        content = android.widget.FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(CARD)
        val names = listOf(
            "🕸 Tuzoq", "📶 Tarmoq", "📱 Ilovalar",
            "🛡 APK", "🔗 Havola", "📄 Log", "📋 Jurnal"
        )
        tabButtons = names.mapIndexed { i, n ->
            val b = Button(this)
            b.text = n
            b.isAllCaps = false
            b.textSize = 12f
            b.setBackgroundColor(Color.TRANSPARENT)
            b.setTextColor(MUTED)
            b.setPadding(dp(16), 0, dp(16), 0)
            b.minWidth = 0
            b.minimumWidth = 0
            b.setOnClickListener { show(i) }
            b
        }
        tabButtons.forEach { bar.addView(it, LinearLayout.LayoutParams(-2, dp(52))) }
        val barScroll = HorizontalScrollView(this)
        barScroll.isHorizontalScrollBarEnabled = false
        barScroll.addView(bar)
        root.addView(barScroll)

        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Alerts.ensureChannels(this)
        show(0)
        handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (current == 0 || current == 6) show(current)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleShareIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri == null) return
        current = 3
        processApk(uri)
    }

    // ---------------------------------------------------------------- yordamchilar

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun tv(s: String, size: Float = 14f, color: Int = TEXT, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(null, Typeface.BOLD)
        return t
    }

    private fun box(color: Int, radius: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radius).toFloat()
        return g
    }

    private fun card(vararg views: View): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = box(CARD, 12)
        c.setPadding(dp(14), dp(12), dp(14), dp(12))
        for (v in views) c.addView(v)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, 0, 0, dp(10))
        c.layoutParams = lp
        return c
    }

    private fun btn(label: String, color: Int = GREEN, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.background = box(color, 10)
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(-1, dp(48))
        lp.setMargins(0, 0, 0, dp(10))
        b.layoutParams = lp
        return b
    }

    private fun page(views: List<View>): ScrollView {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), dp(8), dp(14), dp(14))
        for (v in views) col.addView(v)
        val sv = ScrollView(this)
        sv.addView(col)
        return sv
    }

    private fun fmt(t: Long): String = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date(t))

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun copy(s: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("hisobot", s))
        toast("Nusxalandi")
    }

    private fun eventRow(e: Event): View {
        val c = when (e.severity) {
            3 -> RED
            2 -> AMBER
            else -> MUTED
        }
        val stripe = View(this)
        stripe.setBackgroundColor(c)
        val body = card(tv("${fmt(e.time)} · ${e.type}", 12f, c, true), tv(e.text, 13f, TEXT))
        body.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val rowLp = LinearLayout.LayoutParams(-1, -2)
        rowLp.setMargins(0, 0, 0, dp(10))
        row.layoutParams = rowLp
        row.addView(stripe, LinearLayout.LayoutParams(dp(4), -1))
        row.addView(body)
        return row
    }

    private fun show(i: Int) {
        current = i
        handler.removeCallbacks(refresher)
        tabButtons.forEachIndexed { k, b -> b.setTextColor(if (k == i) GREEN else MUTED) }
        content.removeAllViews()
        content.addView(
            when (i) {
                0 -> tabTrap()
                1 -> tabNet()
                2 -> tabApps()
                3 -> tabApk()
                4 -> tabHavola()
                5 -> tabLog()
                else -> tabJournal()
            }
        )
        updateStatusLine()
        if (i == 0) handler.postDelayed(refresher, 3000)
    }

    private fun updateStatusLine() {
        val on = HoneypotService.running
        val events = EventLog.all(this)
        val last = events.firstOrNull()
        val lastText = if (last != null) "oxirgi hodisa: ${fmt(last.time)}" else "hodisalar yo'q"
        statusLine.text = (if (on) "🟢 Tuzoq yoqilgan" else "⚪ Tuzoq o'chiq") +
            "  ·  $lastText  ·  jami ${events.size}"
    }

    // ---------------------------------------------------------------- 1. Tuzoq

    private fun tabTrap(): View {
        val on = HoneypotService.running
        val items = ArrayList<View>()
        items.add(
            card(
                tv(if (on) "Tuzoq YOQILGAN" else "Tuzoq o'chiq", 20f, if (on) GREEN else MUTED, true),
                tv(
                    "Telefon Wi-Fi tarmog'ida soxta xizmatlarni ko'rsatadi: SSH (2222, 8022), Telnet (2323), " +
                        "HTTP (8080, 8888), ADB (5555). Kimdir ularga ulanishga urinsa, IP manzili jurnalga " +
                        "yoziladi va ogohlantirish keladi.",
                    13f, MUTED
                )
            )
        )
        items.add(btn(if (on) "Tuzoqni o'chirish" else "Tuzoqni yoqish", if (on) RED else GREEN) { toggleTrap() })
        items.add(
            card(
                tv(
                    "Eslatma: mobil internetda tashqaridan ulanib bo'lmaydi. Tuzoq faqat bitta Wi-Fi " +
                        "tarmog'idagi qurilmalarni ushlaydi.",
                    12f, MUTED
                )
            )
        )
        if (!isIgnoringBatteryOptimizations()) {
            items.add(
                card(
                    tv(
                        "Ba'zi telefonlarda (ayniqsa Xiaomi/Redmi) tizim fon xizmatlarini o'chirib qo'yadi va " +
                            "tuzoq to'xtab qoladi. Bunga yo'l qo'ymaslik uchun batareya cheklovidan chiqaring.",
                        13f, AMBER
                    )
                )
            )
            items.add(btn("Batareya cheklovidan chiqarish", AMBER) { requestIgnoreBattery() })
        }
        items.add(tv("Oxirgi urinishlar", 16f, TEXT, true))
        val ev = EventLog.all(this).filter { it.type == "Tuzoq" && it.severity >= 2 }.take(8)
        if (ev.isEmpty()) items.add(tv("Hozircha urinish yo'q.", 13f, MUTED))
        for (e in ev) items.add(eventRow(e))
        return page(items)
    }

    private fun toggleTrap() {
        val i = Intent(this, HoneypotService::class.java)
        val turningOn = !HoneypotService.running
        if (turningOn) startForegroundService(i) else stopService(i)
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("trap_enabled", turningOn).apply()
        handler.postDelayed({ if (current == 0) show(0) }, 700)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBattery() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            toast("Sozlamalarni ochib bo'lmadi")
        }
    }

    // ---------------------------------------------------------------- 2. Tarmoq

    private fun trusted(): Set<String> =
        getSharedPreferences("settings", MODE_PRIVATE).getStringSet("trusted", emptySet()) ?: emptySet()

    private fun tabNet(): View {
        val items = ArrayList<View>()
        items.add(btn(if (scanning) "Skanerlanmoqda..." else "Tarmoqni skanerlash") { startScan() })
        val found = lastScan
        if (found == null) {
            items.add(
                card(
                    tv(
                        "Wi-Fi'ga ulangan holda skanerlang. Dastur tarmoqdagi barcha faol qurilmalarni topadi. " +
                            "Keyin hozirgi qurilmalarni ishonchli deb belgilang: keyingi skanerlashda yangi " +
                            "qurilma chiqsa, ogohlantirish keladi.",
                        13f, MUTED
                    )
                )
            )
        } else {
            val base = trusted()
            items.add(tv("Topilgan qurilmalar: ${found.size}", 16f, TEXT, true))
            for (ip in found) {
                val tag = when {
                    ip == lastMe -> "Siz (bu telefon)"
                    base.isNotEmpty() && ip !in base -> "YANGI qurilma"
                    ip.endsWith(".1") || ip.endsWith(".254") -> "Router (ehtimol)"
                    else -> ""
                }
                val isNew = tag == "YANGI qurilma"
                items.add(
                    card(
                        tv(ip, 15f, if (isNew) RED else TEXT, true),
                        tv(if (tag.isEmpty()) "Faol qurilma" else tag, 12f, if (isNew) RED else MUTED)
                    )
                )
            }
            items.add(btn("Hozirgilarni ishonchli deb belgilash", GRAY) {
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putStringSet("trusted", found.toSet()).apply()
                toast("Saqlandi: ${found.size} ta qurilma")
                show(1)
            })
            items.add(
                card(
                    tv(
                        "Izoh: router IP manzillarni vaqti-vaqti bilan o'zgartirishi mumkin. Shuning uchun " +
                            "\"yangi qurilma\" ogohlantirishini tekshirib ko'ring, har biri xavf emas.",
                        12f, MUTED
                    )
                )
            )
        }
        return page(items)
    }

    private fun startScan() {
        if (scanning) return
        val me = NetScanner.localIpv4()
        if (me == null) {
            toast("Avval Wi-Fi'ga ulaning")
            return
        }
        scanning = true
        show(1)
        thread {
            val found = NetScanner.scan(me)
            val base = trusted()
            if (base.isNotEmpty()) {
                for (ip in found) {
                    if (ip != me && ip !in base) {
                        Alerts.raise(this, "Tarmoq", 2, "Tarmoqda yangi qurilma: $ip")
                    }
                }
            }
            runOnUiThread {
                lastScan = found
                lastMe = me
                scanning = false
                if (current == 1) show(1)
            }
        }
    }

    // ---------------------------------------------------------------- 3. Ilovalar

    private fun tabApps(): View {
        val items = ArrayList<View>()
        items.add(btn(if (auditing) "Tekshirilmoqda..." else "Ilovalarni tekshirish") { startAudit() })
        val r = lastApps
        if (r == null) {
            items.add(
                card(
                    tv(
                        "O'rnatilgan ilovalar ruxsatlari, o'rnatilgan manbasi va xavfli sozlamalari bo'yicha " +
                            "tekshiriladi. Eng shubhali ilovalar yuqorida chiqadi. Ilovani bosib, uning " +
                            "sozlamalariga o'tasiz.",
                        13f, MUTED
                    )
                )
            )
        } else if (r.isEmpty()) {
            items.add(card(tv("Shubhali ilova topilmadi.", 15f, GREEN, true)))
        } else {
            items.add(tv("Shubhali ilovalar: ${r.size}", 16f, TEXT, true))
            for (a in r) items.add(appRow(a))
        }
        return page(items)
    }

    private fun appRow(a: AppRisk): View {
        val col = when {
            a.score >= 6 -> RED
            a.score >= 3 -> AMBER
            else -> MUTED
        }
        val c = card(
            tv("${a.label}  (xavf: ${a.score})", 15f, col, true),
            tv(a.reasons.joinToString("\n• ", "• "), 13f, TEXT),
            tv(a.pkg, 11f, MUTED)
        )
        c.setOnClickListener {
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))
                )
            } catch (e: Exception) {
                toast("Sozlamalarni ochib bo'lmadi")
            }
        }
        return c
    }

    private fun startAudit() {
        if (auditing) return
        auditing = true
        show(2)
        thread {
            val res = try {
                AppAuditor.scan(this)
            } catch (e: Exception) {
                emptyList()
            }
            val high = res.count { it.score >= 6 }
            if (high > 0) {
                Alerts.raise(this, "Ilova auditi", 2, "$high ta yuqori xavfli ilova topildi")
            } else {
                EventLog.add(this, "Ilova auditi", 1, "${res.size} ta shubhali ilova")
            }
            runOnUiThread {
                lastApps = res
                auditing = false
                if (current == 2) show(2)
            }
        }
    }

    // ---------------------------------------------------------------- APK tekshirish (VirusTotal)

    private fun apiKey(): String =
        (getSharedPreferences("settings", MODE_PRIVATE).getString("vt_key", "") ?: "").trim()

    private fun tabApk(): View {
        val items = ArrayList<View>()
        val key = apiKey()
        items.add(
            btn(
                if (key.isEmpty()) "VirusTotal API kalitini sozlash" else "API kalit sozlangan (o'zgartirish)",
                GRAY
            ) { vtKeyDialog() }
        )
        if (key.isEmpty()) {
            items.add(
                card(
                    tv(
                        "APK yoki boshqa shubhali faylni zararli dasturlar bazasi (VirusTotal) orqali " +
                            "tekshirish uchun avval bepul API kalit kerak. Yuqoridagi tugmani bosing.",
                        13f, MUTED
                    )
                )
            )
            return page(items)
        }
        items.add(
            card(
                tv(
                    "Telefoningizga kelgan APK yoki boshqa shubhali faylni tanlang, yoki boshqa ilovadan " +
                        "(Telegram, fayl menejeri) to'g'ridan-to'g'ri \"Ulashish\" orqali shu ilovaga yuboring.",
                    13f, MUTED
                )
            )
        )
        items.add(btn(if (vtBusy) "Kuting..." else "Faylni tanlash", GREEN) { pickApk() })
        if (vtBusy) items.add(card(tv(vtStage, 14f, AMBER, true)))
        val name = vtFileName
        if (name != null && !vtBusy) {
            val r = vtResult
            val err = vtError
            when {
                r != null -> items.add(vtResultCard(name, r))
                err != null -> items.add(card(tv(name, 14f, TEXT, true), tv(err, 13f, RED)))
                vtNotFound -> {
                    items.add(
                        card(
                            tv(name, 14f, TEXT, true),
                            tv(
                                "VirusTotal bazasida hali yo'q — bu fayl hali tekshirilmagan bo'lishi mumkin.",
                                13f, AMBER
                            )
                        )
                    )
                    if (vtTooLarge) {
                        items.add(
                            card(tv("Fayl 32 MB dan katta, to'g'ridan-to'g'ri yuklab tekshirib bo'lmaydi.", 12f, MUTED))
                        )
                    } else {
                        items.add(btn("Yuklab tekshirish (1-2 daqiqa)", AMBER) { uploadAndPoll() })
                    }
                }
            }
        }
        items.add(
            card(
                tv(
                    "Eslatma: bu ilovaga ishonmagan fayllarni O'RNATMANG, faqat tekshirish uchun tanlang. " +
                        "Zararli deb topilgan faylni darhol o'chiring.",
                    12f, MUTED
                )
            )
        )
        return page(items)
    }

    private fun vtResultCard(name: String, r: VtOutcome.Found): View {
        val total = r.malicious + r.suspicious + r.harmless + r.undetected
        val danger = r.malicious > 0
        val warn = !danger && r.suspicious > 0
        val color = if (danger) RED else if (warn) AMBER else GREEN
        val verdict = when {
            danger -> "XAVFLI: ${r.malicious}/$total dastur zararli deb topdi"
            warn -> "Shubhali: ${r.suspicious}/$total dastur shubhali deb topdi"
            else -> "Zararli topilmadi ($total tekshiruvdan)"
        }
        val views = ArrayList<View>()
        views.add(tv(name, 14f, TEXT, true))
        views.add(tv(verdict, 15f, color, true))
        if (r.names.isNotEmpty()) views.add(tv("Ma'lum nomlari: " + r.names.joinToString(", "), 12f, MUTED))
        return card(*views.toTypedArray())
    }

    private fun vtKeyDialog() {
        val p = getSharedPreferences("settings", MODE_PRIVATE)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(10), dp(20), 0)
        val hint = TextView(this)
        hint.text = "virustotal.com saytida bepul ro'yxatdan o'ting, so'ng Profil > API Key bo'limidan " +
            "kalitni nusxalab shu yerga joylashtiring."
        hint.textSize = 12f
        val key = EditText(this)
        key.hint = "VirusTotal API kalit"
        key.setSingleLine()
        key.setText(p.getString("vt_key", ""))
        col.addView(hint)
        col.addView(key)
        AlertDialog.Builder(this)
            .setTitle("VirusTotal API kalit")
            .setView(col)
            .setPositiveButton("Saqlash") { _, _ ->
                p.edit().putString("vt_key", key.text.toString().trim()).apply()
                show(3)
            }
            .setNegativeButton("Bekor", null)
            .show()
    }

    private fun pickApk() {
        if (vtBusy) return
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        startActivityForResult(i, 78)
    }

    private fun processApk(uri: Uri) {
        vtBusy = true
        vtStage = "Fayl o'qilmoqda..."
        vtResult = null
        vtError = null
        vtNotFound = false
        vtTooLarge = false
        vtFileName = VirusTotal.fileName(contentResolver, uri)
        vtPendingUri = uri
        show(3)
        val key = apiKey()
        if (key.isEmpty()) {
            vtBusy = false
            vtError = "Avval API kalitni sozlang"
            show(3)
            return
        }
        thread {
            val size = VirusTotal.fileSize(contentResolver, uri)
            val hash = VirusTotal.sha256(contentResolver, uri)
            if (hash == null) {
                runOnUiThread {
                    vtBusy = false
                    vtError = "Faylni o'qib bo'lmadi"
                    if (current == 3) show(3)
                }
                return@thread
            }
            runOnUiThread {
                vtStage = "VirusTotal'dan so'ralmoqda..."
                if (current == 3) show(3)
            }
            val outcome = VirusTotal.lookup(key, hash)
            runOnUiThread {
                vtBusy = false
                when (outcome) {
                    is VtOutcome.Found -> {
                        vtResult = outcome
                        val danger = outcome.malicious > 0
                        EventLog.add(
                            this, "APK tekshiruvi", if (danger) 3 else 1,
                            "$vtFileName: ${outcome.malicious} zararli deb topdi"
                        )
                        if (danger) {
                            Alerts.raise(
                                this, "APK tekshiruvi", 3,
                                "$vtFileName ZARARLI deb topildi (${outcome.malicious} antivirus)"
                            )
                        }
                    }
                    VtOutcome.NotFound -> {
                        vtNotFound = true
                        vtTooLarge = size > 32L * 1024 * 1024
                    }
                    is VtOutcome.Error -> vtError = outcome.message
                }
                if (current == 3) show(3)
            }
        }
    }

    private fun uploadAndPoll() {
        val uri = vtPendingUri ?: return
        val key = apiKey()
        val name = vtFileName ?: "fayl"
        vtBusy = true
        vtNotFound = false
        vtStage = "Yuklanmoqda..."
        show(3)
        thread {
            val id = VirusTotal.upload(key, contentResolver, uri, name)
            if (id == null) {
                runOnUiThread {
                    vtBusy = false
                    vtError = "Yuklab bo'lmadi"
                    if (current == 3) show(3)
                }
                return@thread
            }
            val outcome = VirusTotal.pollAnalysis(key, id) { sec ->
                runOnUiThread {
                    vtStage = "Tekshirilmoqda... (${sec}s)"
                    if (current == 3) show(3)
                }
            }
            runOnUiThread {
                vtBusy = false
                when (outcome) {
                    is VtOutcome.Found -> {
                        vtResult = outcome
                        val danger = outcome.malicious > 0
                        EventLog.add(
                            this, "APK tekshiruvi", if (danger) 3 else 1,
                            "$name: ${outcome.malicious} zararli deb topdi"
                        )
                        if (danger) {
                            Alerts.raise(
                                this, "APK tekshiruvi", 3,
                                "$name ZARARLI deb topildi (${outcome.malicious} antivirus)"
                            )
                        }
                    }
                    is VtOutcome.Error -> vtError = outcome.message
                    VtOutcome.NotFound -> vtNotFound = true
                }
                if (current == 3) show(3)
            }
        }
    }

    // ---------------------------------------------------------------- Havola/QR tekshiruvi

    private fun tabHavola(): View {
        val items = ArrayList<View>()
        val key = apiKey()
        items.add(
            card(
                tv(
                    "SMS, Telegram yoki email orqali kelgan shubhali havolani tekshiring — fishing (soxta " +
                        "login sahifasi) yoki zararli sayt ekanini aniqlaydi. QR kod orqali kelgan havolani ham " +
                        "ochmasdan oldin shu yerda tekshirishingiz mumkin.",
                    13f, MUTED
                )
            )
        )
        if (key.isEmpty()) {
            items.add(card(tv("Avval APK bo'limida VirusTotal API kalitini sozlang.", 13f, AMBER)))
            return page(items)
        }
        val input = EditText(this)
        input.hint = "https://... havolani shu yerga joylang"
        input.setSingleLine()
        input.setTextColor(TEXT)
        input.setHintTextColor(MUTED)
        items.add(input)
        items.add(btn(if (lkBusy) "Kuting..." else "Havolani tekshirish", GREEN) {
            checkUrl(input.text.toString())
        })
        items.add(btn("QR kodni skanerlash", AMBER) { pickQr() })
        if (lkBusy) items.add(card(tv(lkStage, 14f, AMBER, true)))
        val target = lkTarget
        if (target != null && !lkBusy) {
            val r = lkResult
            val err = lkError
            when {
                r != null -> items.add(linkResultCard(target, r))
                err != null -> items.add(card(tv(target, 13f, TEXT, true), tv(err, 13f, RED)))
            }
        }
        items.add(
            card(
                tv(
                    "Eslatma: natija 100% kafolat emas, lekin ko'pchilik fishing va firibgar saytlarni " +
                        "aniqlaydi. Shubhali havolaga hech qachon parol, karta yoki kod kiritmang.",
                    12f, MUTED
                )
            )
        )
        return page(items)
    }

    private fun linkResultCard(url: String, r: VtOutcome.Found): View {
        val total = r.malicious + r.suspicious + r.harmless + r.undetected
        val danger = r.malicious > 0
        val warn = !danger && r.suspicious > 0
        val color = if (danger) RED else if (warn) AMBER else GREEN
        val verdict = when {
            danger -> "XAVFLI HAVOLA: ${r.malicious}/$total antivirus zararli deb topdi"
            warn -> "Shubhali: ${r.suspicious}/$total dastur shubhali deb topdi"
            else -> "Xavfsiz ko'rinadi ($total tekshiruvdan)"
        }
        return card(tv(url, 13f, TEXT, true), tv(verdict, 15f, color, true))
    }

    private fun pickQr() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 2)
            return
        }
        IntentIntegrator(this)
            .setOrientationLocked(false)
            .setBeepEnabled(true)
            .setPrompt("QR kodni kameraga tuting")
            .initiateScan()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pickQr()
        }
    }

    private fun finishLinkCheck(outcome: VtOutcome, url: String) {
        lkBusy = false
        when (outcome) {
            is VtOutcome.Found -> {
                lkResult = outcome
                val danger = outcome.malicious > 0
                EventLog.add(
                    this, "Havola tekshiruvi", if (danger) 3 else 1,
                    "$url: ${outcome.malicious} xavfli deb topdi"
                )
                if (danger) {
                    Alerts.raise(this, "Havola tekshiruvi", 3, "XAVFLI HAVOLA: $url (${outcome.malicious} antivirus)")
                }
            }
            is VtOutcome.Error -> lkError = outcome.message
            VtOutcome.NotFound -> lkError = "Natija topilmadi"
        }
        if (current == 4) show(4)
    }

    private fun checkUrl(urlRaw: String) {
        var url = urlRaw.trim()
        if (url.isEmpty()) return
        if (!url.contains("://")) url = "http://$url"
        lkBusy = true
        lkStage = "VirusTotal'dan so'ralmoqda..."
        lkTarget = url
        lkResult = null
        lkError = null
        show(4)
        val key = apiKey()
        if (key.isEmpty()) {
            lkBusy = false
            lkError = "Avval VirusTotal API kalitni sozlang (APK bo'limida)"
            show(4)
            return
        }
        thread {
            val first = VirusTotal.lookupUrl(key, url)
            if (first is VtOutcome.NotFound) {
                runOnUiThread {
                    lkStage = "Yuborilmoqda..."
                    if (current == 4) show(4)
                }
                val id = VirusTotal.submitUrl(key, url)
                if (id == null) {
                    runOnUiThread { finishLinkCheck(VtOutcome.Error("Yuborib bo'lmadi"), url) }
                    return@thread
                }
                val second = VirusTotal.pollAnalysis(key, id) { sec ->
                    runOnUiThread {
                        lkStage = "Tekshirilmoqda... (${sec}s)"
                        if (current == 4) show(4)
                    }
                }
                runOnUiThread { finishLinkCheck(second, url) }
            } else {
                runOnUiThread { finishLinkCheck(first, url) }
            }
        }
    }

    // ---------------------------------------------------------------- 5. Log tahlili

    private fun tabLog(): View {
        val items = ArrayList<View>()
        items.add(
            card(
                tv(
                    "Server yoki sayt log faylini (auth.log, access.log, error.log) tanlang. Dastur parol " +
                        "topishga urinishlar, SQL injection, katalog bo'ylab kirish va skanerlash izlarini " +
                        "qidiradi va har bir IP uchun xulosa beradi.",
                    13f, MUTED
                )
            )
        )
        items.add(btn(if (analyzing) "Tahlil qilinmoqda..." else "Log faylni tanlash") { pickLog() })
        val r = lastLog
        if (r != null) {
            if (r.isEmpty()) {
                items.add(card(tv("Shubhali IP topilmadi.", 15f, GREEN, true)))
            } else {
                items.add(tv("Shubhali IP manzillar: ${r.size}", 16f, TEXT, true))
                items.add(btn("Hisobotni nusxalash", GRAY) { copy(report(r)) })
                for (f in r.take(40)) {
                    items.add(
                        card(
                            tv("${f.ip}  (xavf: ${f.score})", 15f, if (f.score >= 20) RED else AMBER, true),
                            tv(f.reasons.joinToString("\n• ", "• "), 13f, TEXT)
                        )
                    )
                }
                items.add(
                    card(
                        tv(
                            "Tavsiya: tasdiqlangan hujumchi IP'larni server firewall'ida (masalan, fail2ban yoki " +
                                "ufw) bloklang.",
                            12f, MUTED
                        )
                    )
                )
            }
        }
        return page(items)
    }

    private fun report(r: List<Finding>): String {
        val sb = StringBuilder("Xaker Ovchisi: log tahlili\n\n")
        for (f in r) {
            sb.append(f.ip).append("  (xavf: ").append(f.score).append(")\n")
            for (x in f.reasons) sb.append(" - ").append(x).append('\n')
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun pickLog() {
        if (analyzing) return
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        startActivityForResult(i, 77)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val qr = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (qr != null) {
            qr.contents?.let { checkUrl(it) }
            return
        }
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            77 -> processLog(uri)
            78 -> processApk(uri)
        }
    }

    private fun processLog(uri: Uri) {
        analyzing = true
        show(5)
        thread {
            var res: List<Finding>? = null
            try {
                val stream = contentResolver.openInputStream(uri)
                if (stream != null) {
                    res = stream.bufferedReader().use { LogAnalyzer.analyze(it.lineSequence()) }
                }
            } catch (e: Exception) {
                res = null
            }
            val result = res
            runOnUiThread {
                analyzing = false
                if (result == null) {
                    toast("Faylni o'qib bo'lmadi")
                } else {
                    lastLog = result
                    EventLog.add(this, "Log tahlili", 1, "${result.size} ta shubhali IP topildi")
                }
                if (current == 5) show(5)
            }
        }
    }

    // ---------------------------------------------------------------- 6. Jurnal

    private fun tabJournal(): View {
        val items = ArrayList<View>()
        val tg = (getSharedPreferences("settings", MODE_PRIVATE).getString("tg_token", "") ?: "").isNotBlank()
        items.add(btn(if (tg) "Telegram ulangan (o'zgartirish)" else "Telegram ogohlantirishni sozlash", GRAY) {
            telegramDialog()
        })
        items.add(btn("Hisobotni PDF qilib saqlash/ulashish", GRAY) { exportReport() })
        items.add(btn("Jurnalni tozalash", GRAY) {
            EventLog.clear(this)
            show(6)
        })
        val ev = EventLog.all(this)
        if (ev.isEmpty()) items.add(tv("Jurnal bo'sh.", 13f, MUTED))
        for (e in ev.take(100)) items.add(eventRow(e))
        return page(items)
    }

    private fun exportReport() {
        toast("Hisobot tayyorlanmoqda...")
        thread {
            try {
                val events = EventLog.all(this)
                val file = ReportExporter.buildPdf(this, events)
                runOnUiThread { ReportExporter.shareFile(this, file) }
            } catch (e: Exception) {
                runOnUiThread { toast("Hisobot yaratib bo'lmadi: ${e.message}") }
            }
        }
    }

    private fun telegramDialog() {
        val p = getSharedPreferences("settings", MODE_PRIVATE)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(10), dp(20), 0)
        val hint = TextView(this)
        hint.text = "Bot tokenini @BotFather'dan, chat ID raqamingizni @userinfobot'dan oling. " +
            "Botga avval /start yuboring."
        hint.textSize = 12f
        val token = EditText(this)
        token.hint = "Bot token"
        token.setSingleLine()
        token.setText(p.getString("tg_token", ""))
        val chat = EditText(this)
        chat.hint = "Chat ID"
        chat.setSingleLine()
        chat.setText(p.getString("tg_chat", ""))
        col.addView(hint)
        col.addView(token)
        col.addView(chat)
        AlertDialog.Builder(this)
            .setTitle("Telegram ogohlantirish")
            .setView(col)
            .setPositiveButton("Saqlash va test") { _, _ ->
                p.edit()
                    .putString("tg_token", token.text.toString().trim())
                    .putString("tg_chat", chat.text.toString().trim())
                    .apply()
                Alerts.sendTelegram(this, "Xaker Ovchisi ulandi ✅") { ok ->
                    runOnUiThread {
                        toast(if (ok) "Telegram'ga yuborildi" else "Yuborib bo'lmadi, token va chat ID'ni tekshiring")
                    }
                }
                show(6)
            }
            .setNegativeButton("Bekor", null)
            .show()
    }
}
