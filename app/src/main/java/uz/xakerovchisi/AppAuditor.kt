package uz.xakerovchisi

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

data class AppRisk(val label: String, val pkg: String, val score: Int, val reasons: List<String>)

/** O'rnatilgan ilovalarni xavfli sozlamalar va ruxsatlar bo'yicha tekshiradi. */
object AppAuditor {

    private val trustedStores = setOf(
        "com.android.vending",
        "com.xiaomi.mipicks",
        "com.sec.android.app.samsungapps",
        "com.huawei.appmarket",
        "com.heytap.market",
        "com.amazon.venezia"
    )

    private fun enabledPkgs(ctx: Context, key: String): Set<String> {
        val s: String? = try {
            Settings.Secure.getString(ctx.contentResolver, key)
        } catch (e: Exception) {
            null
        }
        if (s == null) return emptySet()
        return s.split(':').filter { it.contains('/') }.map { it.substringBefore('/') }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun installer(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) {
            pm.getInstallSourceInfo(pkg).installingPackageName
        } else {
            pm.getInstallerPackageName(pkg)
        }
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    fun scan(ctx: Context): List<AppRisk> {
        val pm = ctx.packageManager
        val a11y = enabledPkgs(ctx, "enabled_accessibility_services")
        val listeners = enabledPkgs(ctx, "enabled_notification_listeners")
        val admins: Set<String> = try {
            val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }

        val out = ArrayList<AppRisk>()
        for (pi in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            val ai = pi.applicationInfo ?: continue
            if ((ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue
            val pkg = pi.packageName
            if (pkg == ctx.packageName) continue

            val asked = HashSet<String>()
            val granted = HashSet<String>()
            val perms = pi.requestedPermissions
            val flags = pi.requestedPermissionsFlags
            if (perms != null) {
                for (i in perms.indices) {
                    asked.add(perms[i])
                    val f = if (flags != null && i < flags.size) flags[i] else 0
                    if ((f and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) granted.add(perms[i])
                }
            }

            val reasons = ArrayList<String>()
            var score = 0
            if (pkg in a11y) {
                score += 4
                reasons.add("Maxsus imkoniyatlar xizmati yoqilgan (ekranni o'qiy va boshqara oladi)")
            }
            if (pkg in listeners) {
                score += 3
                reasons.add("Bildirishnomalarni o'qiy oladi (SMS kodlarini ko'rishi mumkin)")
            }
            if (pkg in admins) {
                score += 3
                reasons.add("Qurilma administratori (o'chirish qiyin bo'ladi)")
            }
            if ("android.permission.READ_SMS" in granted || "android.permission.RECEIVE_SMS" in granted) {
                score += 3
                reasons.add("SMS o'qish ruxsati berilgan")
            }
            if ("android.permission.SYSTEM_ALERT_WINDOW" in asked) {
                score += 1
                reasons.add("Boshqa ilovalar ustidan oyna chizish ruxsatini so'raydi")
            }
            if ("android.permission.REQUEST_INSTALL_PACKAGES" in asked) {
                score += 1
                reasons.add("Boshqa APK'larni o'rnata oladi")
            }
            if ("android.permission.CAMERA" in granted &&
                "android.permission.RECORD_AUDIO" in granted &&
                "android.permission.ACCESS_FINE_LOCATION" in granted
            ) {
                score += 1
                reasons.add("Kamera, mikrofon va aniq joylashuv ruxsatlari berilgan")
            }
            if (pm.getLaunchIntentForPackage(pkg) == null) {
                score += 2
                reasons.add("Ekranda ikonkasi yo'q (yashirin ilova)")
            }
            if (score > 0) {
                val inst = installer(pm, pkg)
                if (inst == null || inst !in trustedStores) {
                    score += 1
                    reasons.add("Rasmiy do'kondan tashqari o'rnatilgan")
                }
            }
            if (score >= 2) {
                out.add(AppRisk(pm.getApplicationLabel(ai).toString(), pkg, score, reasons))
            }
        }
        return out.sortedByDescending { it.score }
    }
}
