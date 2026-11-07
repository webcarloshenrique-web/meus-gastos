package br.com.meusgastos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val range = remember(month) { Dates.range(month) }
    val lista = remember(tick, month) { db.expenses(range.first, range.second) }
    val anterior = remember(tick, month) { val r = Dates.range(month - 1); db.expenses(r.first, r.second) }
    val meses = (month - 4..month).toList()
    val porMes = remember(tick, month) { meses.map { o -> val r = Dates.range(o); db.sumBetween(r.first, r.second) } }
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
                listOf("📊 Por mês", "📅 Por dia", "🍕 Categorias"), modo, { modo = it },
                Modifier.padding(horizontal = 16.dp),
            )
        }

        when (modo) {
            0 -> item {
                val comGasto = porMes.filter { it > 0 }
                val media = if (comGasto.isEmpty()) 0L else comGasto.sum() / comGasto.size
                val iMaior = porMes.indices.maxByOrNull { porMes[it] } ?: 4
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle("Quanto você gastou por mês")
                    Hint("${Dates.abbr(month - 4)} a ${Dates.abbr(month)} · toque em uma barra para escolher o mês")
                    BarChart(
                        values = porMes,
                        labels = meses.map { Dates.abbr(it) },
                        selected = 4,
                        onSelect = { i -> onMonth(month - 4 + i) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBox("Média por mês", money(media), Modifier.weight(1f))
                        StatBox(
                            "Mês mais caro",
                            if (porMes[iMaior] > 0) "${Dates.abbr(meses[iMaior])} · ${compact(porMes[iMaior])}" else "—",
                            Modifier.weight(1f),
                        )
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
                }
            }

            else -> item {
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle("Para onde foi o dinheiro")
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Donut(porCat.map { it.first.color to it.second.toFloat() }, 170.dp, 28.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Hint("Total")
                            Text(compact(total), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                        }
                    }
                    if (porCat.isEmpty()) Hint("Ainda não há gastos neste mês.")
                    porCat.forEach { (cat, v) -> CategoryBarRow(cat, v, total) }
                }
            }
        }

        item {
            AppCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("🔎 Entenda seus gastos")
                insights(lista, anterior, month).forEach { (emoji, texto) ->
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

/** Frases simples explicando o mês: comparação, o que mais pesou, maior gasto, previsão. */
fun insights(lista: List<Expense>, prev: List<Expense>, month: Int): List<Pair<String, String>> {
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

    val pequenos = lista.filter { it.cents <= 3000 }
    if (pequenos.size >= 5) {
        out.add("☕" to "Gastos pequenos (até R$ 30) somaram ${money(pequenos.sumOf { it.cents })} em ${pequenos.size} compras. Eles se acumulam!")
    }

    if (month == 0 && Dates.today() >= 3) {
        val proj = total * Dates.daysIn(0) / Dates.today()
        out.add("🔮" to "Se continuar nesse ritmo, o mês deve fechar em cerca de ${money(proj)}.")
    }
    return out
}
