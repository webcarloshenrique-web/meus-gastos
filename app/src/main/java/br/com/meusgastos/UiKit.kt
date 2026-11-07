package br.com.meusgastos

import android.content.Context
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Paletas de cor

data class Paleta(val nome: String, val emoji: String, val primary: Color, val light: Color)

object Tema {
    val opcoes = listOf(
        Paleta("Verde Dinheiro", "💵", Color(0xFF1B7F5C), Color(0xFF2FA37A)),
        Paleta("Azul Banco", "🏦", Color(0xFF1F4E8C), Color(0xFF3A78C2)),
        Paleta("Ouro", "🪙", Color(0xFF8A6300), Color(0xFFC08A1A)),
        Paleta("Petróleo", "📈", Color(0xFF0E5C66), Color(0xFF1C8A96)),
        Paleta("Grafite", "💼", Color(0xFF2F3E46), Color(0xFF52646E)),
        Paleta("Bordô", "🍷", Color(0xFF7A1F3D), Color(0xFFA8385E)),
    )
    var atual by mutableStateOf(opcoes[0])

    fun load(ctx: Context) {
        atual = opcoes.getOrElse(Prefs.palette(ctx)) { opcoes[0] }
    }

    fun set(ctx: Context, i: Int) {
        Prefs.setPalette(ctx, i)
        atual = opcoes.getOrElse(i) { opcoes[0] }
    }
}

/** Cor principal (segue a paleta escolhida em Ajustes). */
val Verde: Color get() = Tema.atual.primary
val VerdeClaro: Color get() = Tema.atual.light
val Fundo = Color(0xFFF3F6F4)
val TextoForte = Color(0xFF17231F)
val TextoSuave = Color(0xFF5F6F68)
val Linha = Color(0xFFE1E8E4)
val Vermelho = Color(0xFFC62828)
val Laranja = Color(0xFFE08A00)
val VerdeOk = Color(0xFF2E9E5B)

const val MIN_MONTH = -23
const val MAX_MONTH = 6

val dateFmt = SimpleDateFormat("dd/MM HH:mm", ptBR)
val dayFmt = SimpleDateFormat("dd/MM", ptBR)
val fullDayFmt = SimpleDateFormat("EEEE, dd/MM/yyyy", ptBR)

// ---------------------------------------------------------------- Categorias (ícone e cor)

data class CatInfo(val name: String, val emoji: String, val color: Color)

object Cats {
    val pendente = CatInfo("Pendente", "❓", Color(0xFFB0B9B5))
    var list by mutableStateOf<List<CatInfo>>(emptyList())

    fun load(db: Db) {
        list = db.categories().map { CatInfo(it.name, it.emoji, Color(it.color)) }
    }

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
fun StatBox(label: String, value: String, modifier: Modifier = Modifier, color: Color = TextoForte) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Fundo).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, fontSize = 12.sp, color = TextoSuave, maxLines = 1)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
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
                fontSize = 13.sp,
                maxLines = 1,
                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                color = if (sel) Verde else TextoSuave,
            )
        }
    }
}

/** Botão de escolha (chip) simples. */
@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Verde else Fundo)
            .border(1.dp, if (selected) Verde else Linha, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        textAlign = TextAlign.Center,
        color = if (selected) Color.White else TextoForte,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        maxLines = 1,
    )
}

/** Contador com − e + (ex.: número de parcelas). */
@Composable
fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { if (value > range.first) onChange(value - 1) }) { Text("−", fontSize = 20.sp) }
        Text(
            "$value", Modifier.width(56.dp), textAlign = TextAlign.Center,
            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextoForte,
        )
        OutlinedButton(onClick = { if (value < range.last) onChange(value + 1) }) { Text("+", fontSize = 20.sp) }
    }
}

/** Barra de progresso de limite: verde, laranja (perto) e vermelho (passou). */
@Composable
fun LimitBar(gasto: Long, limite: Long, alertPct: Int) {
    val frac = if (limite > 0) gasto.toFloat() / limite else 0f
    val anim by animateFloatAsState(targetValue = frac.coerceAtMost(1f), label = "limite")
    val cor = when {
        frac >= 1f -> Vermelho
        frac * 100 >= alertPct -> Laranja
        else -> VerdeOk
    }
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Linha)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(anim).clip(RoundedCornerShape(50)).background(cor))
    }
}

// ---------------------------------------------------------------- Campo de valor (digita só números)

/**
 * Campo de dinheiro sem vírgula: os números entram da direita para a esquerda.
 * 3 -> 0,03 · 33 -> 0,33 · 333 -> 3,33 · 3333 -> 33,33
 */
@Composable
fun MoneyField(
    cents: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    big: Boolean = false,
) {
    val text = if (cents == 0L) "" else String.format(ptBR, "%,.2f", cents / 100.0)
    val size = if (big) 28.sp else 18.sp
    val rotulo: (@Composable () -> Unit)? = if (label == null) null else ({ Text(label) })
    OutlinedTextField(
        value = TextFieldValue(text, selection = TextRange(text.length)),
        onValueChange = { v ->
            val d = v.text.filter { it.isDigit() }
            if (d.length <= 11) onChange(d.toLongOrNull() ?: 0L)
        },
        label = rotulo,
        placeholder = { Text("0,00", fontSize = size) },
        prefix = { Text("R$ ", fontSize = size, fontWeight = FontWeight.Bold) },
        textStyle = TextStyle(fontSize = size, fontWeight = FontWeight.Bold),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------- Faixa de meses (SET OUT NOV DEZ JAN)

@Composable
fun MonthStrip(db: Db, selected: Int, onSelect: (Int) -> Unit) {
    val tick = Bus.tick.intValue
    val offsets = remember { (MIN_MONTH..MAX_MONTH).toList() }
    val totais = remember(tick) { offsets.map { o -> val r = Dates.range(o); db.sumBetween(r.first, r.second) } }
    val idx = selected - MIN_MONTH
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (idx - 2).coerceAtLeast(0))
    LaunchedEffect(selected) { state.animateScrollToItem((idx - 2).coerceAtLeast(0)) }

    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(offsets.size) { i ->
            val o = offsets[i]
            val sel = o == selected
            val futuro = o > 0
            Column(
                Modifier.width(62.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (sel) Verde else if (futuro) Color(0xFFF7F9F8) else Color.White)
                    .border(1.dp, if (sel) Verde else Linha, RoundedCornerShape(16.dp))
                    .clickable { onSelect(o) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    Dates.abbr(o), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (sel) Color.White else if (futuro) TextoSuave else TextoForte,
                )
                Text(
                    if (o == 0) "hoje" else "${Dates.year(o)}", fontSize = 10.sp,
                    color = if (sel) Color.White.copy(alpha = 0.8f) else TextoSuave,
                )
                Text(
                    if (totais[i] > 0) (if (futuro) "⏳" else "") + compact(totais[i], false) else "—",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (sel) Color.White else if (futuro) Laranja else Verde,
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
            val alvo = v.coerceAtLeast(0L).toFloat() / max
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
            val parc = if (e.instTotal > 1) " · 💳 ${e.instNum}/${e.instTotal}" else ""
            Text("${cat.name} · ${dateFmt.format(Date(e.dateMs))}$parc", fontSize = 13.sp, color = TextoSuave, maxLines = 1)
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

/** Janela para corrigir um gasto: nome, valor e categoria — ou apagar (uma parcela ou a compra toda). */
@Composable
fun EditExpenseDialog(db: Db, e: Expense, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var nome by remember(e.id) { mutableStateOf(e.merchant) }
    var valor by remember(e.id) { mutableLongStateOf(e.cents) }
    var cat by remember(e.id) { mutableStateOf(e.category) }
    var confirmarApagar by remember(e.id) { mutableStateOf(false) }
    var erro by remember(e.id) { mutableStateOf("") }
    val parcelado = e.planId > 0 && e.instTotal > 1

    if (confirmarApagar) {
        AlertDialog(
            onDismissRequest = { confirmarApagar = false },
            title = { Text("Apagar este gasto?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${e.merchant} — ${money(e.cents)}")
                    if (parcelado) Text("É a parcela ${e.instNum} de ${e.instTotal}. O que você quer apagar?")
                    Hint("Essa ação não pode ser desfeita.")
                    if (parcelado) {
                        OutlinedButton(onClick = {
                            db.delete(e.id); Bus.bump(); confirmarApagar = false; onDismiss()
                        }, modifier = Modifier.fillMaxWidth()) { Text("Só esta parcela") }
                        Button(
                            onClick = { db.deletePlan(e.planId); Bus.bump(); confirmarApagar = false; onDismiss() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Vermelho),
                        ) { Text("Todas as ${e.instTotal} parcelas") }
                    }
                }
            },
            confirmButton = {
                if (!parcelado) {
                    TextButton(onClick = {
                        db.delete(e.id); Bus.bump(); confirmarApagar = false; onDismiss()
                    }) { Text("Apagar", color = Vermelho, fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = { TextButton(onClick = { confirmarApagar = false }) { Text("Cancelar") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (parcelado) "Editar parcela ${e.instNum}/${e.instTotal}" else "Editar gasto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nome, onValueChange = { nome = it },
                    label = { Text("Estabelecimento") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(valor, { valor = it; erro = "" }, label = "Valor")
                Text("Categoria", fontWeight = FontWeight.Medium)
                if (parcelado) Hint("A categoria vale para todas as parcelas desta compra.")
                CategoryGrid(cat) { cat = it }
                if (erro.isNotEmpty()) Text(erro, color = Vermelho)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (valor <= 0) {
                        erro = "Digite o valor"
                    } else {
                        val upd = e.copy(cents = valor, merchant = nome.trim().ifBlank { e.merchant }, category = cat)
                        db.update(upd)
                        val nova = cat
                        if (nova != null && nova != e.category) Categorizer.learn(db, upd, nova)
                        Limits.check(ctx, db, nova)
                        Bus.bump()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Verde),
            ) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmarApagar = true }) { Text("🗑 Apagar", color = Vermelho) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
