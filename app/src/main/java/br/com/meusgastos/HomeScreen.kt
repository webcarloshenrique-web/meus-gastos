package br.com.meusgastos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HomeScreen(db: Db, pad: PaddingValues, month: Int, onMonth: (Int) -> Unit, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val tick = Bus.tick.intValue
    val range = remember(month) { Dates.range(month) }
    val reais = remember(tick, month) { db.expenses(range.first, range.second) }
    val previstos = remember(tick, month) { Recorrentes.previstos(db, month) }
    val aVencer = previstos.sumOf { it.cents }
    // Mês atual e futuros: conta tudo o que é do mês, pago ou a vencer (contabilidade mais certa)
    val lista = (reais + previstos).sortedByDescending { it.dateMs }
    val listaVisivel = (reais + previstos).sortedByDescending { it.dateMs }
    val prevTotal = remember(tick, month) { val r = Dates.range(month - 1); db.sumBetween(r.first, r.second) }
    // Janela do gráfico: sempre deixa um mês à frente para avançar
    val meses = (month - 3..month + 1).toList()
    val last5 = remember(tick, month) {
        meses.map { o -> val r = Dates.range(o); db.sumBetween(r.first, r.second) + if (o >= 0) Recorrentes.totalPrevisto(db, o) else 0L }
    }
    val pendentes = remember(tick) { db.pendingCount() }
    val salarioPendente = remember(tick) { db.payersPending().firstOrNull() }
    val saldo = remember(tick, month) { Budget.saldo(db, month) }
    val limites = remember(tick) { db.limits() }
    val alertPct = remember(tick) { Prefs.alertPct(ctx) }
    val proximas = remember(tick, month) {
        (1..6).map { k ->
            val o = month + k
            val r = Dates.range(o)
            Triple(o, db.sumInstallments(r.first, r.second), Recorrentes.totalPrevisto(db, o))
        }.filter { it.second + it.third > 0 }
    }
    val fixos = remember(tick) { db.recurring().filter { it.active } }
    val fixosLancados = remember(tick, month) { db.sumFixed(range.first, range.second) }
    val total = lista.sumOf { it.cents }
    val porCatMap = lista.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.cents } }
    val porCat = porCatMap.map { (c, v) -> Cats.of(c) to v }.sortedByDescending { it.second }
    var editando by remember { mutableStateOf<Expense?>(null) }
    var painel by remember { mutableStateOf<String?>(null) }
    var detalheMes by remember(month) { mutableStateOf<Int?>(null) }
    val historico = remember(month) { mutableStateListOf<Int>() }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { MonthStrip(db, month, onMonth) }

        // Cartão principal
        item {
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Verde, VerdeClaro)))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            month > 0 -> "Previsto para ${Dates.full(month)}"
                            month == 0 && aVencer > 0 -> "Gastos de ${Dates.full(month)} (pagos + a vencer)"
                            else -> "Gasto em ${Dates.full(month)}"
                        },
                        color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp,
                    )
                    Text(money(total), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    if (month <= 0 && saldo.renda > 0) {
                        val usado = (total.toFloat() / saldo.renda).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))) {
                            Box(Modifier.fillMaxWidth(usado).height(8.dp).clip(RoundedCornerShape(50)).background(Color.White))
                        }
                        Text(
                            "${(total * 100 / saldo.renda)}% da entrada" + (if (saldo.estimada) " estimada" else "") + " de ${money(saldo.renda)}",
                            color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (prevTotal > 0 && month <= 0) {
                            val diff = total - prevTotal
                            val pct = (abs(diff) * 100.0 / prevTotal).roundToInt()
                            val seta = if (diff > 0) "▲" else if (diff < 0) "▼" else "="
                            Pill("$seta $pct% vs ${Dates.abbr(month - 1)}")
                        }
                        if (month > 0) {
                            val parc = reais.filter { it.instTotal > 1 }.sumOf { it.cents }
                            if (parc > 0) Pill("💳 parcelas ${compact(parc)}")
                            if (aVencer > 0) Pill("🔁 fixos ${compact(aVencer)}")
                        } else {
                            Pill("🧾 ${reais.size} gastos")
                        }
                    }
                    if (month == 0 && aVencer > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("✅ já pago ${compact(total - aVencer)}")
                            Pill("⏳ a vencer ${compact(aVencer)}")
                        }
                    }
                    saldo.projecaoGasto?.let { Pill("🔮 Previsão de gastos: ${money(it)}") }
                }
            }
        }

        // Entradas e sobra
        if (month <= 0) {
            item {
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    if (saldo.renda > 0) {
                        SectionTitle("💰 Quanto ainda tenho")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatBox(if (saldo.estimada) "Entrada (estim.)" else "Entrou", compact(saldo.renda), Modifier.weight(1f), VerdeOk)
                            StatBox(if (saldo.aVencer > 0) "Gastos (+a vencer)" else "Saiu", compact(saldo.saiu), Modifier.weight(1f), Vermelho)
                            StatBox("Disponível", compact(saldo.sobra), Modifier.weight(1f), if (saldo.sobra >= 0) VerdeOk else Vermelho)
                        }
                        if (saldo.aVencer > 0) {
                            Hint("Já desconta ${money(saldo.aVencer)} de contas fixas que ainda vão vencer neste mês.")
                        }
                        saldo.sobraPrevista?.let { prev ->
                            Text(
                                if (prev >= 0) "🔮 No ritmo atual, o mês deve fechar sobrando cerca de ${money(prev)}."
                                else "⚠️ No ritmo atual, o mês deve fechar faltando cerca de ${money(-prev)}.",
                                fontSize = 15.sp, color = if (prev >= 0) TextoForte else Vermelho,
                            )
                        }
                        saldo.porDia?.let { d ->
                            if (d > 0) Text("📅 Para não ficar no vermelho, gaste até ${money(d)} por dia até o fim do mês.", fontSize = 15.sp, color = TextoForte)
                        }
                        if (saldo.estimada) Hint("Entrada estimada pela média dos últimos meses. Registre o que entrou este mês em 💰 Entradas.")
                        TextButton(onClick = { go(Screen.INCOME) }) { Text("Ver entradas ›", fontSize = 15.sp) }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("💰", fontSize = 30.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Quanto sobra no mês?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                                Hint("Registre seu salário ou o que recebe. Pix recebidos entram sozinhos.")
                            }
                        }
                        Button(
                            onClick = { go(Screen.INCOME) }, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Verde),
                        ) { Text("Registrar entrada") }
                    }
                }
            }
        }

        // Confirmar salário
        if (salarioPendente != null) {
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFE6F4EA)).clickable { go(Screen.INCOME) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("💼", fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Isso é o seu salário?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                        Hint("Entraram ${money(salarioPendente.lastCents)} de ${salarioPendente.name.ifBlank { "uma empresa" }}. Toque para confirmar.")
                    }
                    Text("›", fontSize = 28.sp, color = TextoSuave)
                }
            }
        }

        // Pendentes
        if (pendentes > 0) {
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFFFFF3D6)).clickable { go(Screen.PENDING) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("❓", fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (pendentes == 1) "1 gasto esperando categoria" else "$pendentes gastos esperando categoria",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextoForte,
                        )
                        Hint("Toque para classificar")
                    }
                    Text("›", fontSize = 28.sp, color = TextoSuave)
                }
            }
        }

        // Atalhos em ícones (tocar abre o painel)
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val prox = proximas.firstOrNull()
                IconTile(
                    "📆", "Próximos meses",
                    if (prox != null) "${Dates.abbr(prox.first)} ${compact(prox.second + prox.third)}" else "—",
                    Modifier.weight(1f), Laranja,
                ) { painel = "proximos" }
                val estourados = limites.count { (c, lim) -> (porCatMap[c] ?: 0L) * 100 / lim >= alertPct }
                IconTile(
                    "🎯", "Limites",
                    when {
                        limites.isEmpty() -> "Definir"
                        estourados > 0 -> "⚠️ $estourados perto"
                        else -> "${limites.size} ok"
                    },
                    Modifier.weight(1f), if (estourados > 0) Vermelho else Verde,
                ) { painel = "limites" }
                IconTile("📅", "Calendário", Dates.abbr(month), Modifier.weight(1f)) { go(Screen.CALENDAR) }
            }
        }

        // Gastos fixos
        if (fixos.isNotEmpty() && month <= 0) {
            item {
                AppCard(Modifier.padding(horizontal = 16.dp).clickable { go(Screen.FIXED) }) {
                    SectionTitle("🔁 Gastos fixos")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBox("Por mês", compact(fixos.sumOf { it.cents }), Modifier.weight(1f))
                        StatBox("Já lançados", compact(fixosLancados), Modifier.weight(1f))
                        StatBox("A vencer", compact(aVencer), Modifier.weight(1f), Laranja)
                    }
                    if (month == 0) {
                        val proximos = fixos.filter { it.day > Dates.today() }.take(3)
                        if (proximos.isNotEmpty()) {
                            Hint("Próximos: " + proximos.joinToString(" · ") { "${it.merchant} dia ${it.day} (${compact(it.cents)})" })
                        }
                    }
                }
            }
        }

        if (lista.isEmpty()) {
            item {
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🪙", fontSize = 52.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Nenhum gasto neste mês", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Os gastos do banco entram sozinhos. Você também pode lançar à mão.",
                            fontSize = 15.sp, color = TextoSuave, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { go(Screen.ADD) },
                            colors = ButtonDefaults.buttonColors(containerColor = Verde),
                        ) { Text("➕ Adicionar gasto") }
                    }
                }
            }
        } else {
            item {
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle("💸 Onde foi seu dinheiro")
                    porCat.take(5).forEach { (cat, v) -> CategoryBarRow(cat, v, total) }
                    TextButton(onClick = { go(Screen.REPORTS) }) { Text("Ver relatório completo ›", fontSize = 15.sp) }
                }
            }
        }

        item {
            val aberto = detalheMes
            AppCard(
                Modifier.padding(horizontal = 16.dp)
                    .then(if (aberto != null) Modifier.border(2.dp, Verde, RoundedCornerShape(20.dp)) else Modifier)
            ) {
                SectionTitle("📊 Gastos por mês")
                Hint(if (aberto == null) "Toque em uma barra para ver o detalhe do mês" else "Toque em outra barra para comparar")
                BarChart(
                    values = last5,
                    labels = meses.map { Dates.abbr(it) },
                    selected = meses.indexOf(aberto ?: month),
                    onSelect = { i ->
                        val o = meses[i]
                        if (aberto != null && aberto != o) historico.add(aberto)
                        detalheMes = o
                    },
                    chartHeight = if (aberto != null) 170.dp else 110.dp,
                )
                if (aberto != null) {
                    DetalheBox { MonthDetail(db, aberto) { editando = it } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { detalheMes = historico.removeLastOrNull() },
                            modifier = Modifier.weight(1f),
                        ) { Text("◀ Voltar") }
                        OutlinedButton(onClick = { onMonth(aberto); detalheMes = null; historico.clear() }, modifier = Modifier.weight(1f)) {
                            Text("Abrir mês")
                        }
                        Button(
                            onClick = { detalheMes = null; historico.clear() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Verde),
                        ) { Text("✕ Fechar") }
                    }
                }
            }
        }

    }

    editando?.let { e -> EditExpenseDialog(db, e) { editando = null } }

    when (painel) {
        "proximos" -> AlertDialog(
            onDismissRequest = { painel = null },
            title = { Text("📆 Próximos meses") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hint("O que já está comprometido: parcelas + gastos fixos previstos. Toque em um mês para abrir.")
                    if (proximas.isEmpty()) Hint("Nada marcado para os próximos meses.")
                    proximas.forEach { (o, parc, fix) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Fundo)
                                .clickable { onMonth(o); painel = null }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(Dates.full(o), fontWeight = FontWeight.Bold, color = TextoForte)
                                Hint("💳 parcelas ${compact(parc)} · 🔁 fixos ${compact(fix)}")
                            }
                            Text(compact(parc + fix), fontWeight = FontWeight.Bold, color = Laranja, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { painel = null }) { Text("Fechar") } },
        )
        "limites" -> AlertDialog(
            onDismissRequest = { painel = null },
            title = { Text("🎯 Limites do mês") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (limites.isEmpty()) Hint("Você ainda não definiu limites. O app avisa quando você chegar perto do limite de uma categoria.")
                    limites.map { (c, lim) -> Triple(c, porCatMap[c] ?: 0L, lim) }
                        .sortedByDescending { it.second * 100 / it.third }
                        .forEach { (c, g, lim) ->
                            val ci = Cats.of(c)
                            val pct = (g * 100 / lim).toInt()
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${ci.emoji} ${ci.name}", Modifier.weight(1f), fontSize = 15.sp, color = TextoForte)
                                    Text(
                                        "${compact(g)} de ${compact(lim)} · $pct%", fontSize = 13.sp,
                                        color = if (pct >= 100) Vermelho else if (pct >= alertPct) Laranja else TextoSuave,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                LimitBar(g, lim, alertPct)
                            }
                        }
                }
            },
            confirmButton = {
                Button(
                    onClick = { painel = null; go(Screen.LIMITS) },
                    colors = ButtonDefaults.buttonColors(containerColor = Verde),
                ) { Text(if (limites.isEmpty()) "Definir limites" else "Editar limites") }
            },
            dismissButton = { TextButton(onClick = { painel = null }) { Text("Fechar") } },
        )
    }
}
