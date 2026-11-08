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
        withPrevious: Boolean = false, firstExtra: Long = 0, pay: String = "", card: String = "",
    ): List<Long> {
        if (total <= 1) {
            return listOf(db.insertExpense(Expense(0, instCents + firstExtra, merchant, category, dateMs, source, pay = pay, card = card)))
        }
        val planId = db.nextPlanId()
        val start = if (onlyOne) from else if (withPrevious) 1 else from
        val end = if (onlyOne) from else total
        val ids = ArrayList<Long>()
        for (k in start..end) {
            val c = Calendar.getInstance().apply { timeInMillis = dateMs; add(Calendar.MONTH, k - from) }
            val valor = if (k == 1) instCents + firstExtra else instCents
            ids.add(db.insertExpense(Expense(0, valor, merchant, category, c.timeInMillis, source, planId, k, total, pay = pay, card = card)))
        }
        return ids
    }
}

/** Cartões: "Banco do Brasil •2332" = nome do app do banco + final do cartão. */
object Cartoes {
    fun nome(ctx: Context, pkg: String, final4: String): String {
        if (final4.isBlank()) return ""
        val banco = try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            ""
        }
        return if (banco.isBlank()) "•$final4" else "$banco •$final4"
    }

    /** "Banco do Brasil •2332" -> "•2332" (versão curta para listas). */
    fun curto(card: String): String = if (card.contains('•')) "•" + card.substringAfterLast('•') else card
}

/**
 * Salário: na primeira vez que entra dinheiro de uma fonte com "cara" de salário (CNPJ, empresa,
 * palavra salário ou valor alto), o app pergunta se é o seu salário e qual o líquido normal.
 * Depois disso, o que vier dessa fonte até o líquido é Salário; o que passar vira Extra.
 */
object Salario {
    private val empresa = Regex("""(?i)\b(ltda|s\.?\s?a\.?|eireli|me|epp|empresa|folha|pagamento|salario|salário|servicos|serviços|comercio|comércio|industria|indústria)\b""")

    fun pareceSalario(e: ParsedIncome): Boolean =
        e.kind == "Salário" || e.cnpj.isNotBlank() || empresa.containsMatchIn(e.payerName) || e.cents >= 50_000

    /** Grava a entrada já separando salário e extra quando a fonte for conhecida. */
    fun registrar(ctx: Context, db: Db, e: ParsedIncome, time: Long, pkg: String) {
        val key = e.payerKey
        val p = if (key.isNotBlank()) db.payer(key) else null
        when {
            p != null && p.status == 1 -> {
                lancarSalario(db, p, e.cents, time, pkg)
                db.savePayer(p.copy(lastCents = e.cents))
            }
            p == null && key.isNotBlank() && pareceSalario(e) -> {
                db.insertIncome(Income(0, e.cents, e.desc, e.kind, time, pkg, key))
                db.savePayer(Payer(key, e.payerName.ifBlank { e.cnpj }, e.cnpj, -1, 0, e.cents))
                Notifier.askSalary(ctx, e)
            }
            else -> {
                db.insertIncome(Income(0, e.cents, e.desc, e.kind, time, pkg, key))
                if (p != null) db.savePayer(p.copy(lastCents = e.cents))
            }
        }
    }

    private fun lancarSalario(db: Db, p: Payer, cents: Long, time: Long, source: String) {
        val nome = p.name.ifBlank { "empresa" }
        val comp = Recorrentes.ym(time) + p.refOffset
        if (p.baseCents in 1 until cents) {
            db.insertIncome(Income(0, p.baseCents, "Salário de $nome", "Salário", time, source, p.key, comp))
            db.insertIncome(Income(0, cents - p.baseCents, "Extra de $nome", "Extra", time, source, p.key, comp))
        } else {
            db.insertIncome(Income(0, cents, "Salário de $nome", "Salário", time, source, p.key, comp))
        }
    }

    /** Você confirmou: é salário, com este líquido. Ajusta também as entradas já recebidas dessa fonte. */
    fun confirmar(db: Db, p: Payer, base: Long, refOffset: Int = p.refOffset) {
        val novo = p.copy(status = 1, baseCents = base, refOffset = refOffset)
        db.savePayer(novo)
        val nome = p.name.ifBlank { "empresa" }
        db.incomesByPayer(p.key).forEach { i ->
            val comp = Recorrentes.ym(i.dateMs) + refOffset
            when {
                i.kind == "Extra" -> db.updateIncome(i.copy(compYm = comp))
                base in 1 until i.cents -> {
                    db.updateIncome(i.copy(cents = base, kind = "Salário", desc = "Salário de $nome", compYm = comp))
                    db.insertIncome(Income(0, i.cents - base, "Extra de $nome", "Extra", i.dateMs, i.source, p.key, comp))
                }
                else -> db.updateIncome(i.copy(kind = "Salário", desc = "Salário de $nome", compYm = comp))
            }
        }
    }

    fun negar(db: Db, p: Payer) = db.savePayer(p.copy(status = 0))

    /** Volta uma fonte para "não é salário" (as entradas já lançadas continuam como estão). */
    fun remover(db: Db, p: Payer) = db.savePayer(p.copy(status = 0, baseCents = 0))
}

/** Formas de pagamento. */
object Pagamento {
    val opcoes = listOf("Crédito" to "💳", "Débito" to "🏧", "Pix" to "⚡", "Dinheiro" to "💵", "Boleto" to "🧾")
    fun emoji(p: String): String = opcoes.firstOrNull { it.first == p }?.second ?: ""

    /** Aceita "credito", "cartão de crédito", "pix"... */
    fun normalizar(s: String): String {
        val x = Categorizer.norm(s)
        return when {
            x.isBlank() -> ""
            x.contains("cred") -> "Crédito"
            x.contains("deb") -> "Débito"
            x.contains("pix") -> "Pix"
            x.contains("dinheiro") || x.contains("especie") -> "Dinheiro"
            x.contains("boleto") -> "Boleto"
            else -> ""
        }
    }
}

/**
 * Gastos fixos: lança sozinho, todo mês, no dia do vencimento.
 * Se você apagar o lançamento de um mês, ele não volta (o app lembra o último mês lançado).
 */
object Recorrentes {
    fun ym(ms: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
    }

    /** Data de vencimento no mês [ymv] (ajusta dia 31 em meses menores). */
    fun dataNoMes(ymv: Int, dia: Int): Long {
        val c = Calendar.getInstance()
        c.set(ymv / 12, ymv % 12, 1, 9, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.set(Calendar.DAY_OF_MONTH, dia.coerceIn(1, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
        return c.timeInMillis
    }

    private fun rangeYm(ymv: Int): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.set(ymv / 12, ymv % 12, 1, 0, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        val a = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        return a to c.timeInMillis
    }

    /** Cria um gasto fixo e já lança o gasto da data informada. */
    fun criar(db: Db, cents: Long, merchant: String, category: String?, dateMs: Long, pay: String, source: String = "manual", card: String = ""): Long {
        val dia = Calendar.getInstance().apply { timeInMillis = dateMs }.get(Calendar.DAY_OF_MONTH)
        val id = db.insertRecurring(Recurring(0, cents, merchant, category, dia, pay, true, dateMs, ym(dateMs), source))
        db.insertExpense(Expense(0, cents, merchant, category, dateMs, source, pay = pay, fixedId = id, card = card))
        return id
    }

    /** Lança os gastos fixos que já venceram e ainda não foram lançados. Retorna quantos lançou. */
    fun gerar(ctx: Context, db: Db): Int {
        val agora = System.currentTimeMillis()
        val atual = ym(agora)
        var n = 0
        for (r in db.recurring()) {
            if (!r.active) continue
            var ultimo = r.lastYm
            for (m in maxOf(ym(r.startMs), r.lastYm + 1)..atual) {
                val data = dataNoMes(m, r.day)
                if (data > agora) break
                val (a, b) = rangeYm(m)
                if (!db.hasFixedIn(r.id, a, b)) {
                    db.insertExpense(Expense(0, r.cents, r.merchant, r.category, data, "fixo", pay = r.pay, fixedId = r.id))
                    n++
                }
                ultimo = m
            }
            if (ultimo != r.lastYm) db.updateRecurring(r.copy(lastYm = ultimo))
        }
        if (n > 0) {
            Limits.checkAll(ctx, db)
            Bus.bump()
        }
        return n
    }

    /**
     * Um pagamento chegou pelo banco. Se for o valor de um gasto fixo deste mês, liga os dois
     * (não conta duas vezes). Retorna true se tratou.
     */
    fun casarPagamento(db: Db, cents: Long, time: Long, pkg: String, pay: String, card: String = ""): Boolean {
        val (a, b) = rangeYm(ym(time))
        db.findAutoFixed(cents, a, b)?.let { e ->
            db.update(e.copy(source = pkg, pay = if (e.pay.isBlank()) pay else e.pay, card = if (e.card.isBlank()) card else e.card))
            return true
        }
        val r = db.recurring().firstOrNull { it.active && it.cents == cents && it.lastYm < ym(time) } ?: return false
        db.insertExpense(Expense(0, cents, r.merchant, r.category, time, pkg, pay = if (r.pay.isBlank()) pay else r.pay, fixedId = r.id, card = card))
        db.updateRecurring(r.copy(lastYm = ym(time)))
        return true
    }

    /**
     * Fixos que ainda vão vencer no mês [offset] (0 = atual). Não são gravados: aparecem como "⏳ previsto"
     * e viram gasto de verdade no dia do vencimento.
     */
    fun previstos(db: Db, offset: Int): List<Expense> {
        val (a, b) = Dates.range(offset)
        val ymv = ym(a)
        val agora = System.currentTimeMillis()
        return db.recurring().filter { it.active && ymv > it.lastYm && ymv >= ym(it.startMs) }.mapNotNull { r ->
            val data = dataNoMes(ymv, r.day)
            if (data <= agora || data < a || data >= b) null
            else Expense(-(r.id * 1000 + (ymv % 1000)), r.cents, r.merchant, r.category, data, PREVISTO, pay = r.pay, fixedId = r.id)
        }
    }

    fun totalPrevisto(db: Db, offset: Int): Long = previstos(db, offset).sumOf { it.cents }

    const val PREVISTO = "previsto"

    /** Quanto os fixos ativos somam por mês. */
    fun totalMensal(db: Db): Long = db.recurring().filter { it.active }.sumOf { it.cents }
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

/** (ver Limits.check) */
fun Limits.checkAll(ctx: Context, db: Db) {
    db.limits().keys.forEach { check(ctx, db, it) }
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
                Salario.registrar(ctx, db, entrada, time, pkg)
            }
            Bus.bump()
            return
        }

        val parsed = Parser.parse(title, text)
        db.insertLog(pkg, full, time, parsed != null)
        val cartao = if (parsed != null) Cartoes.nome(ctx, pkg, parsed.final4) else ""
        if (parsed != null && parsed.parcelas <= 1 && Recorrentes.casarPagamento(db, parsed.cents, time, pkg, parsed.pay, cartao)) {
            Bus.bump()
            return
        }
        if (parsed != null && !db.hasRecent(parsed.cents, pkg, time - 120_000)) {
            val cat = Categorizer.categorize(db, parsed.merchant)
            val ids = Installments.register(
                db, parsed.merchant, cat, time, pkg, parsed.cents, parsed.parcelas,
                from = if (parsed.parcelaAtual > 0) parsed.parcelaAtual else 1,
                onlyOne = parsed.parcelaAtual > 0,
                firstExtra = parsed.firstExtra,
                pay = parsed.pay,
                card = cartao,
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

    fun askSalary(ctx: Context, e: ParsedIncome) {
        if (!canNotify(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel("entradas", "Entradas e salário", NotificationManager.IMPORTANCE_DEFAULT))
        val quem = e.payerName.ifBlank { "CNPJ ${e.cnpj}" }
        val n = Notification.Builder(ctx, "entradas")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("💼 Isso é o seu salário?")
            .setContentText("Entraram ${money(e.cents)} de $quem. Toque para confirmar.")
            .setContentIntent(openApp(ctx, "INCOME", 2))
            .setAutoCancel(true)
            .build()
        nm.notify(("sal" + e.payerKey).hashCode(), n)
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
