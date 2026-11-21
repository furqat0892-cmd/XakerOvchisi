package uz.xakerovchisi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Telefon qayta yoqilganda, agar tuzoq avval yoqilgan bo'lsa, uni qayta ishga tushiradi. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val on = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("trap_enabled", false)
        if (!on) return
        val svc = Intent(context, HoneypotService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
