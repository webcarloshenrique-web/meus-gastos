package br.com.meusgastos

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lista de gastos com "Mostrar todos". Toque abre a edição (ou avisa, se for um fixo previsto). */
@Composable
fun ExpenseListDetail(lista: List<Expense>, max: Int = 10, onEdit: (Expense) -> Unit) {
    val ctx = LocalContext.current
    var todos by remember(lista) { mutableStateOf(false) }
    if (lista.isEmpty()) Hint("Nenhum gasto aqui.")
    (if (todos) lista else lista.take(max)).forEach { e ->
        ExpenseItem(e) {
            if (e.source == Recorrentes.PREVISTO) {
                Toast.makeText(ctx, "Gasto fixo previsto. Para mudar, abra Menu → Gastos fixos.", Toast.LENGTH_SHORT).show()
            } else {
                onEdit(e)
            }
        }
    }
    if (!todos && lista.size > max) {
        TextButton(onClick = { todos = true }) { Text("Mostrar todos (${lista.size})", fontSize = 15.sp) }
    }
}

/** Gastos de um mês (inclui fixos previstos ⏳ no mês atual e nos próximos). */
fun gastosDoMes(db: Db, o: Int): List<Expense> {
    val r = Dates.range(o)
    return (db.expenses(r.first, r.second) + if (o >= 0) Recorrentes.previstos(db, o) else emptyList())
        .sortedByDescending { it.dateMs }
}

/** Detalhamento de um mês: total, principais categorias e a lista de gastos. */
@Composable
fun MonthDetail(db: Db, o: Int, onEdit: (Expense) -> Unit) {
    val tick = Bus.tick.intValue
    val lista = remember(tick, o) { gastosDoMes(db, o) }
    val total = lista.sumOf { it.cents }
    val previsto = lista.filter { it.source == Recorrentes.PREVISTO }.sumOf { it.cents }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Dates.full(o), Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextoForte)
            Text(money(total), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Verde)
        }
        if (previsto > 0) Hint("Inclui ⏳ ${money(previsto)} em gastos fixos que ainda vão vencer.")
        lista.groupBy { it.category }
            .map { (c, l) -> Cats.of(c) to l.sumOf { it.cents } }
            .sortedByDescending { it.second }
            .take(3)
            .forEach { (cat, v) -> CategoryBarRow(cat, v, total) }
        Text("🧾 Gastos", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextoForte)
        ExpenseListDetail(lista, 8, onEdit)
    }
}

/** Atalho em forma de ícone (ex.: Próximos meses, Limites). */
@Composable
fun IconTile(emoji: String, label: String, value: String, modifier: Modifier = Modifier, destaque: Color = Verde, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.White)
            .border(1.dp, Linha, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(destaque.copy(alpha = 0.12f)).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 24.sp) }
        Text(label, fontSize = 12.sp, color = TextoSuave, textAlign = TextAlign.Center, maxLines = 1)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = destaque, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Bloco de detalhe destacado (com borda colorida). */
@Composable
fun DetalheBox(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Verde.copy(alpha = 0.05f))
            .border(1.5.dp, Verde.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) { content() }
}
