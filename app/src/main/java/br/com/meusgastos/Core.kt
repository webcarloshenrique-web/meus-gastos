package br.com.meusgastos

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableIntStateOf
import java.text.NumberFormat
import java.util.Locale

val ptBR: Locale = Locale("pt", "BR")

fun money(cents: Long): String = NumberFormat.getCurrencyInstance(ptBR).format(cents / 100.0)

/** Valor curto para gráficos: "R$ 1,2 mil", "R$ 850". */
fun compact(cents: Long, symbol: Boolean = true): String {
    val v = cents / 100.0
    val s = when {
        v >= 1_000_000 -> String.format(ptBR, "%.1f mi", v / 1_000_000)
        v >= 1_000 -> String.format(ptBR, "%.1f mil", v / 1_000)
        else -> String.format(ptBR, "%.0f", v)
    }
    return if (symbol) "R$ $s" else s
}

/** "25,90" / "1.234,56" / "25.9" -> centavos */
fun parseCents(s: String): Long? {
    var t = s.trim().replace("R$", "").replace(" ", "")
    if (t.isEmpty()) return null
    if (t.contains(",")) t = t.replace(".", "").replace(",", ".")
    val d = t.toDoubleOrNull() ?: return null
    return Math.round(d * 100)
}

/** Avisa as telas que os dados mudaram. */
object Bus {
    val tick = mutableIntStateOf(0)
    private val handler = Handler(Looper.getMainLooper())
    fun bump() {
        handler.post { tick.intValue = tick.intValue + 1 }
    }
}

/** Preferências: quais apps (bancos) o app deve ler, e versão de atualização adiada. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun hasChosen(c: Context): Boolean = sp(c).contains("monitored")

    fun monitored(c: Context): Set<String> = sp(c).getStringSet("monitored", emptySet())?.toSet() ?: emptySet()

    fun setAll(c: Context, pkgs: Set<String>) {
        sp(c).edit().putStringSet("monitored", HashSet(pkgs)).apply()
    }

    fun setMonitored(c: Context, pkg: String, on: Boolean) {
        val s = monitored(c).toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        setAll(c, s)
    }

    fun skipped(c: Context): Int = sp(c).getInt("skipped_version", -1)

    fun skip(c: Context, version: Int) {
        sp(c).edit().putInt("skipped_version", version).apply()
    }
}

data class AppInfo(val label: String, val pkg: String, val bank: Boolean)

/** Lista os apps instalados e marca os que parecem ser de banco. */
object Banks {
    private val contains = listOf(
        "nubank", "itaú", "itau", "santander", "bradesco", "banco do brasil", "c6", "caixa",
        "picpay", "mercado pago", "neon", "sicredi", "sicoob", "btg", "pagbank", "pagseguro",
        "safra", "banco pan", "agibank", "recargapay", "banco inter", "banrisul", "brb", "will bank",
        "digio", "ourocard", "cartão", "cartao", "banco",
    )
    private val exact = setOf("bb", "nu", "inter", "next", "iti", "original", "xp", "pan", "bs2", "nomad", "wise")

    fun looksBank(label: String): Boolean {
        val l = label.lowercase().trim()
        return l in exact || contains.any { l.contains(it) }
    }

    fun apps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val list = pm.queryIntentActivities(intent, 0).map { r ->
            val label = r.loadLabel(pm).toString()
            AppInfo(label, r.activityInfo.packageName, looksBank(label))
        }
        return list.filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedWith(compareByDescending<AppInfo> { it.bank }.thenBy { it.label.lowercase() })
    }
}

/** Lógica central: recebe um texto de notificação e transforma em gasto. */
object Processor {
    fun process(ctx: Context, pkg: String, title: String, text: String, time: Long = System.currentTimeMillis()) {
        val db = Db.get(ctx)
        val full = listOf(title, text).filter { it.isNotBlank() }.joinToString(" — ")
        val parsed = Parser.parse(title, text)
        db.insertLog(pkg, full, time, parsed != null)
        if (parsed != null && !db.hasRecent(parsed.cents, pkg, time - 120_000)) {
            val cat = Categorizer.categorize(db, parsed.merchant)
            val id = db.insertExpense(Expense(0, parsed.cents, parsed.merchant, cat, time, pkg))
            if (cat == null) Notifier.askCategory(ctx, id, parsed)
        }
        Bus.bump()
    }
}

/** Mostra o aviso "O que foi esse gasto?". */
object Notifier {
    private const val CHANNEL = "pendentes"

    fun askCategory(ctx: Context, id: Long, p: Parsed) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Gastos para classificar", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("screen", "PENDING")
        val pi = PendingIntent.getActivity(
            ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("O que foi esse gasto?")
            .setContentText("${money(p.cents)} em ${p.merchant} — toque para escolher")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(id.toInt(), n)
    }
}

/** Serviço que o Android chama a cada notificação nova. */
class NotifService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName == packageName) return
            if (sbn.packageName !in Prefs.monitored(this)) return
            val n = sbn.notification ?: return
            if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
            val ex = n.extras ?: return
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
            if (title.isBlank() && text.isBlank()) return
            Processor.process(this, sbn.packageName, title, text, sbn.postTime)
        } catch (_: Exception) {
            // Nunca derrubar o serviço por causa de uma notificação estranha.
        }
    }
}
