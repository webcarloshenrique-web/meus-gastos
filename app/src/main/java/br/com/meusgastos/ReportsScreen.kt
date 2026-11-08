package br.com.meusgastos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReportsScreen(db: Db, pad: PaddingValues, month: Int, onMonth: (Int) -> Unit) {
    val tick = Bus.tick.intValue
    var modo by rememberSaveable { mutableIntStateOf(0) }
    var abrirAnalise by rememberSaveable { mutableStateOf(false) }
    val range = remember(month) { Dates.range(month) }
    // Meses futuros: inclui os gastos fixos previstos (⏳), junto com as parcelas.
    val lista = remember(tick, month) {
        db.expenses(range.first, range.second) + if (month > 0) Recorrentes.previstos(db, month) else emptyList()
    }
    val anterior = remember(tick, month) { val r = Dates.range(month - 1); db.expenses(r.first, r.second) }
    // Janela do gráfico: 3 meses antes e 1 depois, para sempre poder avançar
    val meses = (month - 3..month + 1).toList()
    var editando by remember { mutableStateOf<Expense?>(null) }
    var catSel by remember(month) { mutableStateOf<String?>(null) }
    var paySel by remember(month) { mutableStateOf<String?>(null) }
    val listaOrdenada = lista.sortedByDescending { it.dateMs }
    val porMes = remember(tick, month) {
        meses.map { o -> val r = Dates.range(o); db.sumBetween(r.first, r.second) + if (o > 0) Recorrentes.totalPrevisto(db, o) else 0L }
    }
    val entradasMes = remember(tick, month) { meses.map { o -> val r = Dates.range(o); db.sumIncome(r.first, r.second) } }
    val saldo = remember(tick, month) { Budget.saldo(db, month) }
    val dim = Dates.daysIn(month)
    val porDia = remember(lista, dim) {
        val arr = LongArray(dim)
        lista.forEach { val d = Dates.dayOfMonth(it.dateMs); if (d in 1..dim) arr[d - 1] += it.cents }
        arr.toList()
    }
    val total = lista.sumOf { it.cents }
    val porCat = lista.groupBy { it.category }
        .map { (c, l) -> Cats.of(c) to l.sumOf { it.cents } }
        .sortedByDescending { it.second }
    var diaSel by remember(month) { mutableIntStateOf(if (month == 0) Dates.today() - 1 else -1) }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { MonthStrip(db, month, onMonth) }
        item {
            Segmented(
                listOf("📊 Mês", "📅 Dia", "🍕 Categ.", "💰 Sobra"), modo, { modo = it },
                Modifier.padding(horizontal = 16.dp),
            )
        }

        when (modo) {
            0 -> item {
                val comGasto = porMes.filter { it > 0 }
                val media = if (comGasto.isEmpty()) 0L else comGasto.sum() / comGasto.size
                val iMaior = porMes.indices.maxByOrNull { porMes[it] } ?: 3
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle("Quanto você gastou por mês")
                    Hint("Toque em uma barra: o gráfico anda e mostra o detalhe do mês")
                    BarChart(
                        values = porMes,
                        labels = meses.map { Dates.abbr(it) },
                        selected = 3,
                        onSelect = { i -> onMonth(meses[i]) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox("Média por mês", money(media), Modifier.weight(1f))
                        StatBox(
                            "Mês mais caro",
                            if (porMes[iMaior] > 0) "${Dates.abbr(meses[iMaior])} · ${compact(porMes[iMaior])}" else "—",
                            Modifier.weight(1f),
                        )
                    }
                    DetalheBox {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🧾 Gastos de ${Dates.full(month)}", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                                Text(money(total), fontWeight = FontWeight.Bold, color = Verde)
                            }
                            ExpenseListDetail(listaOrdenada, 10) { editando = it }
                        }
                    }
                }
            }

            1 -> item {
                val diasPassados = if (month == 0) Dates.today() else dim
                val media = if (diasPassados > 0) total / diasPassados else 0L
                val iMaior = porDia.indices.maxByOrNull { porDia[it] } ?: 0
                val semGasto = porDia.take(diasPassados).count { it == 0L }
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle("Gastos por dia em ${Dates.name(month)}")
                    Hint("Toque em uma barra para ver o dia")
                    BarChart(
                        values = porDia,
                        labels = (1..dim).map { if (it == 1 || it % 5 == 0) "$it" else "" },
                        selected = diaSel,
                        onSelect = { diaSel = it },
                        chartHeight = 140.dp,
                        showValues = false,
                        gap = 2.dp,
                    )
                    if (diaSel in 0 until dim) {
                        val n = lista.count { Dates.dayOfMonth(it.dateMs) == diaSel + 1 }
                        Text(
                            "Dia ${diaSel + 1}: ${money(porDia[diaSel])}" + if (n > 0) " em $n gasto(s)" else "",
                            fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextoForte,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox("Média por dia", money(media), Modifier.weight(1f))
                        StatBox(
                            "Dia mais caro",
                            if (porDia.isNotEmpty() && porDia[iMaior] > 0) "Dia ${iMaior + 1}" else "—",
                            Modifier.weight(1f),
                        )
                        StatBox("Dias sem gasto", "$semGasto", Modifier.weight(1f))
                    }
                    if (diaSel in 0 until dim) {
                        DetalheBox {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("🧾 Gastos do dia ${diaSel + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                                ExpenseListDetail(listaOrdenada.filter { Dates.dayOfMonth(it.dateMs) == diaSel + 1 }, 10) { editando = it }
                            }
                        }
                    }
                }
            }

            2 -> item {
              Column {
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle("Para onde foi o dinheiro")
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Donut(porCat.map { it.first.color to it.second.toFloat() }, 170.dp, 28.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Hint("Total")
                            Text(compact(total), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                        }
                    }
                    if (porCat.isEmpty()) Hint("Ainda não há gastos neste mês.") else Hint("Toque em uma categoria para ver os gastos dela")
                    porCat.forEach { (cat, v) ->
                        CategoryBarRow(cat, v, total, catSel == cat.name) { catSel = if (catSel == cat.name) null else cat.name }
                        if (catSel == cat.name) {
                            DetalheBox { Column { ExpenseListDetail(listaOrdenada.filter { Cats.of(it.category).name == cat.name }, 10) { editando = it } } }
                        }
                    }
                }
                if (lista.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    AppCard(Modifier.padding(horizontal = 16.dp)) {
                        SectionTitle("🔁 Fixos × 🛒 compras")
                        val fixo = lista.filter { it.fixedId > 0 }.sumOf { it.cents }
                        val parc = lista.filter { it.instTotal > 1 }.sumOf { it.cents }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox("Fixos", compact(fixo), Modifier.weight(1f))
                            StatBox("Parcelas", compact(parc), Modifier.weight(1f))
                            StatBox("Compras", compact(total - fixo - parc), Modifier.weight(1f))
                        }
                        SectionTitle("💳 Como você pagou")
                        Hint("Toque em uma forma de pagamento para ver o demonstrativo")
                        lista.groupBy { it.pay }
                            .map { (p, l) -> p to l.sumOf { it.cents } }
                            .sortedByDescending { it.second }
                            .forEach { (p, v) ->
                                val nome = if (p.isBlank()) "Não informado" else p
                                val emoji = if (p.isBlank()) "❔" else Pagamento.emoji(p)
                                CategoryBarRow(CatInfo(nome, emoji, if (p.isBlank()) Linha else Verde), v, total, paySel == nome) {
                                    paySel = if (paySel == nome) null else nome
                                }
                                if (paySel == nome) {
                                    DetalheBox {
                                        Column {
                                            ExpenseListDetail(listaOrdenada.filter { (if (it.pay.isBlank()) "Não informado" else it.pay) == nome }, 10) { editando = it }
                                        }
                                    }
                                }
                            }
                    }
                }
              }
            }

            else -> item {
                val maxV = (porMes + entradasMes).maxOrNull()?.coerceAtLeast(1L) ?: 1L
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle("Entradas × gastos")
                    Hint("🟢 entrou   🔴 saiu   · sobra de cada mês")
                    meses.forEachIndexed { i, o ->
                        val e = entradasMes[i]
                        val g = porMes[i]
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onMonth(o) }) {
                            Text(
                                Dates.abbr(o), Modifier.width(44.dp), fontSize = 14.sp,
                                fontWeight = if (o == month) FontWeight.Bold else FontWeight.Normal, color = TextoForte,
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(Modifier.fillMaxWidth().height(10.dp)) {
                                    Box(
                                        Modifier.fillMaxHeight().fillMaxWidth((e.toFloat() / maxV).coerceIn(0.01f, 1f))
                                            .clip(RoundedCornerShape(50)).background(VerdeOk)
                                    )
                                }
                                Box(Modifier.fillMaxWidth().height(10.dp)) {
                                    Box(
                                        Modifier.fillMaxHeight().fillMaxWidth((g.toFloat() / maxV).coerceIn(0.01f, 1f))
                                            .clip(RoundedCornerShape(50)).background(Vermelho)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (e > 0) compact(e - g) else "—",
                                Modifier.width(76.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = if (e - g >= 0) VerdeOk else Vermelho,
                            )
                        }
                    }
                    if (entradasMes.all { it == 0L }) Hint("Registre suas entradas em 💰 Entradas para ver quanto sobra.")
                }
            }
        }

        // Análise e previsão (recolhida)
        item {
            AppCard(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { abrirAnalise = !abrirAnalise },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("🔎 Entenda seus gastos")
                        Hint(if (abrirAnalise) "Toque para fechar" else "Toque para ver a análise e a previsão do mês")
                    }
                    Text(if (abrirAnalise) "▲" else "▼", fontSize = 18.sp, color = TextoSuave)
                }
                AnimatedVisibility(visible = abrirAnalise) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        insights(lista, anterior, month, saldo).forEach { (emoji, texto) ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(emoji, fontSize = 20.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(texto, fontSize = 15.sp, color = TextoForte, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
    editando?.let { e -> EditExpenseDialog(db, e) { editando = null } }
}


/** Frases simples explicando o mês: comparação, o que mais pesou, maior gasto, sobra e previsão. */
fun insights(lista: List<Expense>, prev: List<Expense>, month: Int, saldo: Saldo): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    val antAbbr = Dates.abbr(month - 1)
    if (lista.isEmpty()) {
        out.add("ℹ️" to "Ainda não há gastos em ${Dates.name(month)}.")
        return out
    }
    val total = lista.sumOf { it.cents }
    val prevTotal = prev.sumOf { it.cents }
    val cur = lista.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.cents } }

    if (prevTotal > 0) {
        val diff = total - prevTotal
        val pct = (abs(diff) * 100.0 / prevTotal).roundToInt()
        when {
            diff > 0 -> out.add("📈" to "Você gastou ${money(diff)} a mais que em $antAbbr (+$pct%).")
            diff < 0 -> out.add("📉" to "Você gastou ${money(-diff)} a menos que em $antAbbr (−$pct%). Muito bem!")
            else -> out.add("➖" to "Você gastou o mesmo valor que em $antAbbr.")
        }
        val ant = prev.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.cents } }
        val deltas = (cur.keys + ant.keys).map { k -> k to ((cur[k] ?: 0L) - (ant[k] ?: 0L)) }
        deltas.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.let { (k, d) ->
            val c = Cats.of(k)
            out.add(c.emoji to "O que mais aumentou: ${c.name}, +${money(d)} em relação a $antAbbr.")
        }
        deltas.minByOrNull { it.second }?.takeIf { it.second < 0 }?.let { (k, d) ->
            val c = Cats.of(k)
            out.add("💚" to "Você economizou em ${c.name}: ${money(-d)} a menos que em $antAbbr.")
        }
    } else {
        out.add("ℹ️" to "Não há gastos em $antAbbr para comparar.")
    }

    cur.maxByOrNull { it.value }?.let { (k, v) ->
        val c = Cats.of(k)
        val pct = (v * 100.0 / total).roundToInt()
        out.add("🎯" to "${c.name} é a maior parte dos seus gastos: $pct% do mês (${money(v)}).")
    }

    lista.maxByOrNull { it.cents }?.let {
        out.add("🏷️" to "Maior gasto: ${money(it.cents)} em ${it.merchant} (${dayFmt.format(Date(it.dateMs))}).")
    }

    lista.groupBy { Categorizer.norm(it.merchant) }.values.maxByOrNull { it.size }?.takeIf { it.size >= 2 }?.let { l ->
        out.add("🔁" to "Onde você mais comprou: ${l.first().merchant} (${l.size} vezes, ${money(l.sumOf { it.cents })}).")
    }

    val fixos = lista.filter { it.fixedId > 0 }.sumOf { it.cents }
    if (fixos > 0) {
        out.add("🔁" to "Gastos fixos (contas do mês) somam ${money(fixos)}: ${(fixos * 100.0 / total).roundToInt()}% do que você gastou.")
    }
    val credito = lista.filter { it.pay == "Crédito" }.sumOf { it.cents }
    if (credito > 0) {
        out.add("💳" to "No cartão de crédito foram ${money(credito)} (${(credito * 100.0 / total).roundToInt()}%). Lembre que isso vem na fatura.")
    }
    val parcelas = lista.filter { it.instTotal > 1 }.sumOf { it.cents }
    if (parcelas > 0) {
        out.add("💳" to "Parcelas de compras antigas ocupam ${money(parcelas)} deste mês (${(parcelas * 100.0 / total).roundToInt()}%).")
    }

    val pequenos = lista.filter { it.cents <= 3000 }
    if (pequenos.size >= 5) {
        out.add("☕" to "Gastos pequenos (até R$ 30) somaram ${money(pequenos.sumOf { it.cents })} em ${pequenos.size} compras. Eles se acumulam!")
    }

    if (saldo.renda > 0) {
        val pct = (total * 100.0 / saldo.renda).roundToInt()
        out.add("💰" to "Você já gastou $pct% do que entrou" + (if (saldo.estimada) " (entrada estimada)." else "."))
    }

    saldo.projecaoGasto?.let { proj ->
        out.add("🔮" to "Se continuar nesse ritmo, o mês deve fechar com cerca de ${money(proj)} em gastos.")
        saldo.sobraPrevista?.let { s ->
            if (saldo.renda > 0) {
                out.add(
                    (if (s >= 0) "✅" else "⚠️") to
                        (if (s >= 0) "Previsão de sobra no fim do mês: ${money(s)}." else "Previsão: faltar ${money(-s)} no fim do mês. Vale segurar os gastos.")
                )
            }
        }
    }
    saldo.porDia?.let { d ->
        if (d > 0) out.add("📅" to "Para fechar no azul, gaste até ${money(d)} por dia até o fim do mês.")
    }
    return out
}
