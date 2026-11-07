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
import java.util.Calendar
import java.util.Locale

val ptBR: Locale = Locale("pt", "BR")

fun money(cents: Long): String = NumberFormat.getCurrencyInstance(ptBR).format(cents / 100.0)

/** Valor curto para gráficos: "R$ 1,2 mil", "R$ 850". */
fun compact(cents: Long, symbol: Boolean = true): String {
    val v = cents / 100.0
    val neg = v < 0
    val a = Math.abs(v)
    val s = when {
        a >= 1_000_000 -> String.format(ptBR, "%.1f mi", a / 1_000_000)
        a >= 1_000 -> String.format(ptBR, "%.1f mil", a / 1_000)
        else -> String.format(ptBR, "%.0f", a)
    }
    val r = if (symbol) "R$ $s" else s
    return if (neg) "−$r" else r
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

/** Preferências do app. */
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
    fun skip(c: Context, version: Int) { sp(c).edit().putInt("skipped_version", version).apply() }

    /** % do limite em que o app começa a avisar (padrão 80%). */
    fun alertPct(c: Context): Int = sp(c).getInt("alert_pct", 80)
    fun setAlertPct(c: Context, v: Int) { sp(c).edit().putInt("alert_pct", v).apply() }

    fun alertLevel(c: Context, key: String): Int = sp(c).getInt(key, 0)
    fun setAlertLevel(c: Context, key: String, v: Int) { sp(c).edit().putInt(key, v).apply() }

    /** Registrar sozinho Pix recebido, depósitos e salário. */
    fun autoIncome(c: Context): Boolean = sp(c).getBoolean("auto_income", true)
    fun setAutoIncome(c: Context, v: Boolean) { sp(c).edit().putBoolean("auto_income", v).apply() }

    fun palette(c: Context): Int = sp(c).getInt("palette", 0)
    fun setPalette(c: Context, v: Int) { sp(c).edit().putInt("palette", v).apply() }
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

/**
 * Compras parceladas: cada parcela vira um gasto no mês em que vence.
 * Ex.: R$ 300 em 3x no dia 10/11 -> R$ 100 em 10/11, 10/12 e 10/01, todas com o mesmo planId.
 */
object Installments {
    /**
     * @param from      número da parcela que cai em [dateMs] (1 para compra nova)
     * @param onlyOne   true = lança só a parcela [from] (quando o banco avisa "parcela 3/10")
     * @param withPrevious true = lança também as parcelas 1..from-1 nos meses anteriores
     * @return os ids criados
     */
    fun register(
        db: Db, merchant: String, category: String?, dateMs: Long, source: String,
        instCents: Long, total: Int, from: Int = 1, onlyOne: Boolean = false,
        withPrevious: Boolean = false, firstExtra: Long = 0,
    ): List<Long> {
        if (total <= 1) {
            return listOf(db.insertExpense(Expense(0, instCents + firstExtra, merchant, category, dateMs, source)))
        }
        val planId = db.nextPlanId()
        val start = if (onlyOne) from else if (withPrevious) 1 else from
        val end = if (onlyOne) from else total
        val ids = ArrayList<Long>()
        for (k in start..end) {
            val c = Calendar.getInstance().apply { timeInMillis = dateMs; add(Calendar.MONTH, k - from) }
            val valor = if (k == 1) instCents + firstExtra else instCents
            ids.add(db.insertExpense(Expense(0, valor, merchant, category, c.timeInMillis, source, planId, k, total)))
        }
        return ids
    }
}

/** Limite por categoria: avisa ao chegar na % escolhida e ao passar de 100%. */
object Limits {
    fun check(ctx: Context, db: Db, category: String?) {
        if (category == null) return
        val lim = db.limits()[category] ?: return
        if (lim <= 0) return
        val r = Dates.range(0)
        val gasto = db.sumCategory(r.first, r.second, category)
        val pct = (gasto * 100 / lim).toInt()
        val nivel = when {
            pct >= 100 -> 2
            pct >= Prefs.alertPct(ctx) -> 1
            else -> 0
        }
        if (nivel == 0) return
        val key = "alerta_${Dates.year(0)}_${Dates.abbr(0)}_$category"
        if (Prefs.alertLevel(ctx, key) >= nivel) return
        Prefs.setAlertLevel(ctx, key, nivel)
        Notifier.limitAlert(ctx, category, gasto, lim, pct, nivel)
    }
}

/** Lógica central: recebe um texto de notificação e transforma em gasto ou entrada. */
object Processor {
    fun process(ctx: Context, pkg: String, title: String, text: String, time: Long = System.currentTimeMillis()) {
        val db = Db.get(ctx)
        val full = listOf(title, text).filter { it.isNotBlank() }.joinToString(" — ")

        val entrada = Parser.parseIncome(title, text)
        if (entrada != null) {
            db.insertLog(pkg, full, time, true)
            if (Prefs.autoIncome(ctx) && !db.hasRecentIncome(entrada.cents, pkg, time - 120_000)) {
                db.insertIncome(Income(0, entrada.cents, entrada.desc, entrada.kind, time, pkg))
            }
            Bus.bump()
            return
        }

        val parsed = Parser.parse(title, text)
        db.insertLog(pkg, full, time, parsed != null)
        if (parsed != null && !db.hasRecent(parsed.cents, pkg, time - 120_000)) {
            val cat = Categorizer.categorize(db, parsed.merchant)
            val ids = Installments.register(
                db, parsed.merchant, cat, time, pkg, parsed.cents, parsed.parcelas,
                from = if (parsed.parcelaAtual > 0) parsed.parcelaAtual else 1,
                onlyOne = parsed.parcelaAtual > 0,
                firstExtra = parsed.firstExtra,
            )
            if (cat == null) Notifier.askCategory(ctx, ids.first(), parsed) else Limits.check(ctx, db, cat)
        }
        Bus.bump()
    }
}

/** Avisos do app. */
object Notifier {
    private const val CHANNEL = "pendentes"
    private const val CHANNEL_LIMITS = "limites"

    private fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context, screen: String, req: Int): PendingIntent {
        val open = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("screen", screen)
        return PendingIntent.getActivity(ctx, req, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun askCategory(ctx: Context, id: Long, p: Parsed) {
        if (!canNotify(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Gastos para classificar", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val parc = if (p.parcelas > 1) " (${p.parcelas}x)" else ""
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("O que foi esse gasto?")
            .setContentText("${money(p.cents)}$parc em ${p.merchant} — toque para escolher")
            .setContentIntent(openApp(ctx, "PENDING", 0))
            .setAutoCancel(true)
            .build()
        nm.notify(id.toInt(), n)
    }

    fun limitAlert(ctx: Context, category: String, gasto: Long, limite: Long, pct: Int, nivel: Int) {
        if (!canNotify(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LIMITS, "Limites por categoria", NotificationManager.IMPORTANCE_HIGH)
        )
        val titulo = if (nivel >= 2) "🚨 Limite estourado: $category" else "⚠️ Atenção com $category"
        val texto = if (nivel >= 2) {
            "Você gastou ${money(gasto)} de ${money(limite)} ($pct%). Passou ${money(gasto - limite)} do limite."
        } else {
            "Você já usou $pct% do limite: ${money(gasto)} de ${money(limite)}. Restam ${money(limite - gasto)}."
        }
        val n = Notification.Builder(ctx, CHANNEL_LIMITS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setContentIntent(openApp(ctx, "LIMITS", 1))
            .setAutoCancel(true)
            .build()
        nm.notify(("lim$category").hashCode(), n)
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
