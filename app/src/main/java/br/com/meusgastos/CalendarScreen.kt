package br.com.meusgastos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CalendarScreen(db: Db, pad: PaddingValues, month: Int, onMonth: (Int) -> Unit) {
    val tick = Bus.tick.intValue
    val range = remember(month) { Dates.range(month) }
    val lista = remember(tick, month) { db.expenses(range.first, range.second) + Recorrentes.previstos(db, month) }
    var irParaFixos by remember { mutableStateOf(false) }
    val dim = Dates.daysIn(month)
    val primeiro = Dates.firstWeekday(month)
    val porDia = remember(lista, dim) {
        val arr = LongArray(dim)
        lista.forEach { val d = Dates.dayOfMonth(it.dateMs); if (d in 1..dim) arr[d - 1] += it.cents }
        arr
    }
    val max = (porDia.maxOrNull() ?: 0L).coerceAtLeast(1L)
    var diaSel by remember(month) { mutableIntStateOf(if (month == 0) Dates.today() else 0) }
    var editando by remember { mutableStateOf<Expense?>(null) }
    val celulas = List(primeiro) { 0 } + (1..dim).toList()

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { MonthStrip(db, month, onMonth) }

        item {
            AppCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("📅 ${Dates.full(month)}")
                Hint("Quanto mais escuro o quadrado, mais você gastou no dia.")
                Row(Modifier.fillMaxWidth()) {
                    Dates.WEEK.forEach {
                        Text(
                            it, Modifier.weight(1f), textAlign = TextAlign.Center,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextoSuave,
                        )
                    }
                }
                celulas.chunked(7).forEach { semana ->
                    Row(Modifier.fillMaxWidth()) {
                        semana.forEach { d ->
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp)) {
                                if (d > 0) {
                                    val v = porDia[d - 1]
                                    val forca = if (v == 0L) 0f else 0.25f + 0.75f * (v.toFloat() / max)
                                    val fundo = if (v == 0L) Color(0xFFEEF2F0) else Verde.copy(alpha = forca)
                                    val borda = if (d == diaSel) Modifier.border(2.dp, TextoForte, RoundedCornerShape(8.dp)) else Modifier
                                    Box(
                                        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(fundo)
                                            .then(borda).clickable { diaSel = d },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "$d", fontSize = 13.sp,
                                            fontWeight = if (d == diaSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (forca > 0.55f) Color.White else TextoForte,
                                        )
                                    }
                                }
                            }
                        }
                        repeat(7 - semana.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Hint("Menos")
                    Spacer(Modifier.width(6.dp))
                    listOf(0f, 0.3f, 0.55f, 0.8f, 1f).forEach { a ->
                        Box(
                            Modifier.padding(2.dp).size(16.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (a == 0f) Color(0xFFEEF2F0) else Verde.copy(alpha = a))
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Hint("Mais")
                }
            }
        }

        item {
            AppCard(Modifier.padding(horizontal = 16.dp)) {
                if (diaSel in 1..dim) {
                    val doDia = lista.filter { Dates.dayOfMonth(it.dateMs) == diaSel }
                    SectionTitle("Dia $diaSel · ${money(porDia[diaSel - 1])}")
                    if (doDia.isEmpty()) Hint("Nenhum gasto neste dia.")
                    doDia.forEach { e -> ExpenseItem(e) { if (e.source == Recorrentes.PREVISTO) irParaFixos = true else editando = e } }
                } else {
                    Hint("Toque em um dia para ver os gastos.")
                }
            }
        }
    }

    editando?.let { e -> EditExpenseDialog(db, e) { editando = null } }
    if (irParaFixos) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { irParaFixos = false },
            title = { Text("⏳ Gasto fixo previsto") },
            text = { Text("Ele será lançado sozinho no dia do vencimento. Para mudar valor ou dia, abra Menu → Gastos fixos.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { irParaFixos = false }) { Text("OK") } },
        )
    }
}
