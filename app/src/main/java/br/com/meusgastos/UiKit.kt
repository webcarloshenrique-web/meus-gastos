package br.com.meusgastos

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Cores

val Verde = Color(0xFF1B7F5C)
val VerdeClaro = Color(0xFF2FA37A)
val Fundo = Color(0xFFF3F6F4)
val TextoForte = Color(0xFF17231F)
val TextoSuave = Color(0xFF5F6F68)
val Linha = Color(0xFFE1E8E4)
val Vermelho = Color(0xFFC62828)

const val MIN_MONTH = -23

val dateFmt = SimpleDateFormat("dd/MM HH:mm", ptBR)
val dayFmt = SimpleDateFormat("dd/MM", ptBR)
val fullDayFmt = SimpleDateFormat("EEEE, dd/MM/yyyy", ptBR)

// ---------------------------------------------------------------- Categorias (ícone e cor)

data class CatInfo(val name: String, val emoji: String, val color: Color)

object Cats {
    val pendente = CatInfo("Pendente", "❓", Color(0xFFB0B9B5))
    val list = listOf(
        CatInfo("Alimentação", "🍔", Color(0xFFF4A261)),
        CatInfo("Mercado", "🛒", Color(0xFF2A9D8F)),
        CatInfo("Transporte", "🚗", Color(0xFF457B9D)),
        CatInfo("Saúde", "💊", Color(0xFFE76F51)),
        CatInfo("Casa e contas", "🏠", Color(0xFF8D6E63)),
        CatInfo("Compras", "🛍️", Color(0xFF9C4DCC)),
        CatInfo("Lazer", "🎮", Color(0xFFE5487D)),
        CatInfo("Educação", "📚", Color(0xFF3F51B5)),
        CatInfo("Pessoas (Pix)", "👤", Color(0xFF00A5B8)),
        CatInfo("Outros", "📦", Color(0xFF78858A)),
    )

    fun of(name: String?): CatInfo =
        if (name == null) pendente else list.firstOrNull { it.name == name } ?: CatInfo(name, "📦", Color(0xFF78858A))
}

// ---------------------------------------------------------------- Blocos básicos

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextoForte)
}

@Composable
fun Hint(text: String) {
    Text(text, fontSize = 13.sp, color = TextoSuave)
}

@Composable
fun Pill(text: String, bg: Color = Color.White.copy(alpha = 0.2f), fg: Color = Color.White) {
    Text(
        text,
        color = fg,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun CatIcon(cat: CatInfo, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(cat.color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(cat.emoji, fontSize = (size.value * 0.46f).sp)
    }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Fundo).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, fontSize = 12.sp, color = TextoSuave, maxLines = 1)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextoForte, maxLines = 1)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color(0xFFE4EBE7)).padding(4.dp)
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Text(
                o,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (sel) Color.White else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                maxLines = 1,
                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                color = if (sel) Verde else TextoSuave,
            )
        }
    }
}

// ---------------------------------------------------------------- Faixa de meses (JUL AGO SET OUT NOV)

@Composable
fun MonthStrip(db: Db, selected: Int, onSelect: (Int) -> Unit) {
    val tick = Bus.tick.intValue
    val offsets = remember { (MIN_MONTH..0).toList() }
    val totais = remember(tick) { offsets.map { o -> val r = Dates.range(o); db.sumBetween(r.first, r.second) } }
    val idx = selected - MIN_MONTH
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (idx - 3).coerceAtLeast(0))
    LaunchedEffect(selected) { state.animateScrollToItem((idx - 3).coerceAtLeast(0)) }

    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(offsets.size) { i ->
            val o = offsets[i]
            val sel = o == selected
            Column(
                Modifier.width(62.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (sel) Verde else Color.White)
                    .border(1.dp, if (sel) Verde else Linha, RoundedCornerShape(16.dp))
                    .clickable { onSelect(o) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Dates.abbr(o), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (sel) Color.White else TextoForte)
                Text("${Dates.year(o)}", fontSize = 10.sp, color = if (sel) Color.White.copy(alpha = 0.8f) else TextoSuave)
                Text(
                    if (totais[i] > 0) compact(totais[i], false) else "—",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (sel) Color.White else Verde,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Gráficos

@Composable
fun BarChart(
    values: List<Long>,
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    chartHeight: Dp = 150.dp,
    showValues: Boolean = true,
    gap: Dp = 8.dp,
) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        Modifier.fillMaxWidth().height(chartHeight + 40.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { i, v ->
            val alvo = v.toFloat() / max
            val frac by animateFloatAsState(targetValue = alvo, label = "barra")
            val sel = i == selected
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (showValues) {
                    Text(
                        if (v > 0) compact(v, false) else "",
                        fontSize = 10.sp,
                        maxLines = 1,
                        color = if (sel) Verde else TextoSuave,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                Box(
                    Modifier.fillMaxWidth()
                        .height((chartHeight.value * frac).coerceAtLeast(3f).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (sel) Verde else Verde.copy(alpha = 0.3f))
                )
                Text(
                    labels.getOrElse(i) { "" },
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = if (sel) TextoForte else TextoSuave,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun Donut(slices: List<Pair<Color, Float>>, diameter: Dp = 150.dp, thickness: Dp = 24.dp) {
    Canvas(Modifier.size(diameter)) {
        val total = slices.sumOf { it.second.toDouble() }.toFloat()
        val stroke = thickness.toPx()
        val d = this.size.minDimension - stroke
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        if (total <= 0f) {
            drawArc(
                color = Color(0xFFE6ECE9), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = Size(d, d), style = Stroke(width = stroke),
            )
        } else {
            var start = -90f
            slices.forEach { (c, v) ->
                val sweep = 360f * v / total
                drawArc(
                    color = c, startAngle = start, sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f), useCenter = false,
                    topLeft = topLeft, size = Size(d, d), style = Stroke(width = stroke),
                )
                start += sweep
            }
        }
    }
}

@Composable
fun CategoryBarRow(cat: CatInfo, value: Long, total: Long) {
    val frac = if (total > 0) value.toFloat() / total else 0f
    val anim by animateFloatAsState(targetValue = frac, label = "categoria")
    Row(verticalAlignment = Alignment.CenterVertically) {
        CatIcon(cat, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cat.name, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = TextoForte,
                )
                Text(money(value), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextoForte)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(Linha)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(anim).clip(RoundedCornerShape(50)).background(cat.color))
                }
                Text(
                    "${(frac * 100).roundToInt()}%", Modifier.width(46.dp),
                    textAlign = TextAlign.End, fontSize = 13.sp, color = TextoSuave,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Itens e seletores

@Composable
fun ExpenseItem(e: Expense, onClick: () -> Unit) {
    val cat = Cats.of(e.category)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatIcon(cat, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.merchant, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextoForte,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("${cat.name} · ${dateFmt.format(Date(e.dateMs))}", fontSize = 13.sp, color = TextoSuave, maxLines = 1)
        }
        Text(money(e.cents), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextoForte)
    }
}

@Composable
fun CategoryGrid(selected: String?, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Cats.list.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val sel = c.name == selected
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (sel) c.color.copy(alpha = 0.22f) else Fundo)
                            .border(if (sel) 2.dp else 1.dp, if (sel) c.color else Linha, RoundedCornerShape(14.dp))
                            .clickable { onPick(c.name) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(c.emoji, fontSize = 24.sp)
                        Text(
                            c.name, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2,
                            lineHeight = 14.sp, color = TextoForte,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Janela para corrigir um gasto: nome, valor e categoria — ou apagar. */
@Composable
fun EditExpenseDialog(db: Db, e: Expense, onDismiss: () -> Unit) {
    var nome by remember(e.id) { mutableStateOf(e.merchant) }
    var valor by remember(e.id) { mutableStateOf(String.format(ptBR, "%.2f", e.cents / 100.0)) }
    var cat by remember(e.id) { mutableStateOf(e.category) }
    var confirmarApagar by remember(e.id) { mutableStateOf(false) }
    var erro by remember(e.id) { mutableStateOf("") }

    if (confirmarApagar) {
        AlertDialog(
            onDismissRequest = { confirmarApagar = false },
            title = { Text("Apagar este gasto?") },
            text = { Text("${e.merchant} — ${money(e.cents)}\n\nEssa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    db.delete(e.id)
                    Bus.bump()
                    confirmarApagar = false
                    onDismiss()
                }) { Text("Apagar", color = Vermelho, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmarApagar = false }) { Text("Cancelar") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar gasto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it },
                    label = { Text("Estabelecimento") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = valor, onValueChange = { valor = it; erro = "" },
                    label = { Text("Valor (R$)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text("Categoria", fontWeight = FontWeight.Medium)
                CategoryGrid(cat) { cat = it }
                if (erro.isNotEmpty()) Text(erro, color = Vermelho)
            }
        },
        confirmButton = {
            Button(onClick = {
                val c = parseCents(valor)
                if (c == null || c <= 0) {
                    erro = "Digite um valor válido, por exemplo 25,90"
                } else {
                    val upd = e.copy(cents = c, merchant = nome.trim().ifBlank { e.merchant }, category = cat)
                    db.update(upd)
                    val nova = cat
                    if (nova != null && nova != e.category) Categorizer.learn(db, upd, nova)
                    Bus.bump()
                    onDismiss()
                }
            }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmarApagar = true }) { Text("🗑 Apagar", color = Vermelho) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
