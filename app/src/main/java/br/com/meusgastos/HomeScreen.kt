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
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HomeScreen(db: Db, pad: PaddingValues, month: Int, onMonth: (Int) -> Unit, go: (Screen) -> Unit) {
    val tick = Bus.tick.intValue
    val range = remember(month) { Dates.range(month) }
    val lista = remember(tick, month) { db.expenses(range.first, range.second) }
    val prevTotal = remember(tick, month) { val r = Dates.range(month - 1); db.sumBetween(r.first, r.second) }
    val meses = (month - 4..month).toList()
    val last5 = remember(tick, month) { meses.map { o -> val r = Dates.range(o); db.sumBetween(r.first, r.second) } }
    val pendentes = remember(tick) { db.pendingCount() }
    val total = lista.sumOf { it.cents }
    val porCat = lista.groupBy { it.category }
        .map { (c, l) -> Cats.of(c) to l.sumOf { it.cents } }
        .sortedByDescending { it.second }
    val projecao: Long? = if (month == 0 && total > 0 && Dates.today() >= 3) {
        total * Dates.daysIn(0) / Dates.today()
    } else null
    var editando by remember { mutableStateOf<Expense?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { MonthStrip(db, month, onMonth) }

        // Cartão principal (total do mês)
        item {
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Verde, VerdeClaro)))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gasto em ${Dates.full(month)}", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
                    Text(money(total), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (prevTotal > 0) {
                            val diff = total - prevTotal
                            val pct = (abs(diff) * 100.0 / prevTotal).roundToInt()
                            val seta = if (diff > 0) "▲" else if (diff < 0) "▼" else "="
                            Pill("$seta $pct% vs ${Dates.abbr(month - 1)}")
                        }
                        Pill("🧾 ${lista.size} gastos")
                    }
                    if (projecao != null) Pill("🔮 Previsão do mês: ${money(projecao)}")
                }
            }
        }

        // Aviso de pendentes
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
            // Onde foi o dinheiro
            item {
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle("💸 Onde foi seu dinheiro")
                    porCat.take(5).forEach { (cat, v) -> CategoryBarRow(cat, v, total) }
                    TextButton(onClick = { go(Screen.REPORTS) }) { Text("Ver relatório completo ›", fontSize = 15.sp) }
                }
            }
        }

        // Últimos 5 meses
        item {
            AppCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("📊 Últimos 5 meses")
                Hint("Toque em uma barra para ver aquele mês")
                BarChart(
                    values = last5,
                    labels = meses.map { Dates.abbr(it) },
                    selected = 4,
                    onSelect = { i -> onMonth(month - 4 + i) },
                    chartHeight = 110.dp,
                )
            }
        }

        // Últimos gastos
        if (lista.isNotEmpty()) {
            item {
                AppCard(Modifier.padding(horizontal = 16.dp)) {
                    SectionTitle("🕒 Últimos gastos")
                    Hint("Toque em um gasto para corrigir ou apagar")
                    lista.take(8).forEach { e -> ExpenseItem(e) { editando = e } }
                    if (lista.size > 8) {
                        TextButton(onClick = { go(Screen.CALENDAR) }) { Text("Ver todos no calendário ›", fontSize = 15.sp) }
                    }
                }
            }
        }
    }

    editando?.let { e -> EditExpenseDialog(db, e) { editando = null } }
}
