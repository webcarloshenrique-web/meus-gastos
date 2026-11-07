package br.com.meusgastos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date

// ============================================================ Entradas (receitas)

private val TIPOS_ENTRADA = listOf("💼 Salário" to "Salário", "⚡ Pix recebido" to "Pix recebido", "🏦 Depósito" to "Depósito", "💵 Outros" to "Outros")

private fun emojiEntrada(kind: String) = when (kind) {
    "Salário" -> "💼"
    "Pix recebido" -> "⚡"
    "Depósito" -> "🏦"
    "Transferência" -> "🔁"
    else -> "💵"
}

@Composable
fun IncomeScreen(db: Db, pad: PaddingValues, month: Int, onMonth: (Int) -> Unit) {
    val ctx = LocalContext.current
    val tick = Bus.tick.intValue
    val range = remember(month) { Dates.range(month) }
    val lista = remember(tick, month) { db.incomes(range.first, range.second) }
    val saldo = remember(tick, month) { Budget.saldo(db, month) }
    var valor by remember { mutableLongStateOf(0L) }
    var desc by remember { mutableStateOf("") }
    var tipo by remember { mutableIntStateOf(0) }
    var data by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var apagar by remember { mutableStateOf<Income?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { MonthStrip(db, month, onMonth) }

        item {
            AppCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("💰 ${Dates.full(month)}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("Entrou", compact(saldo.entrou), Modifier.weight(1f), VerdeOk)
                    StatBox("Saiu", compact(saldo.saiu), Modifier.weight(1f), Vermelho)
                    StatBox("Sobra", compact(saldo.entrou - saldo.saiu), Modifier.weight(1f), if (saldo.entrou - saldo.saiu >= 0) VerdeOk else Vermelho)
                }
                saldo.sobraPrevista?.let { s ->
                    if (saldo.renda > 0) Text(
                        if (s >= 0) "🔮 Mantendo o ritmo, sobram cerca de ${money(s)} no fim do mês."
                        else "⚠️ Mantendo o ritmo, faltam cerca de ${money(-s)} no fim do mês.",
                        fontSize = 15.sp, color = if (s >= 0) TextoForte else Vermelho,
                    )
                }
                Hint("Pix recebidos, depósitos e salário avisados pelo banco entram sozinhos (pode desligar em Ajustes).")
            }
        }

        item {
            AppCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("➕ Registrar entrada")
                MoneyField(valor, { valor = it }, big = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TIPOS_ENTRADA.take(2).forEachIndexed { i, (r, _) -> Chip(r, tipo == i, Modifier.weight(1f)) { tipo = i } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TIPOS_ENTRADA.drop(2).forEachIndexed { j, (r, _) -> Chip(r, tipo == j + 2, Modifier.weight(1f)) { tipo = j + 2 } }
                }
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    placeholder = { Text("Descrição (opcional): ex. salário da empresa") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                DateChooser(data) { data = it }
                Button(
                    onClick = {
                        if (valor <= 0) {
                            Toast.makeText(ctx, "Digite o valor", Toast.LENGTH_SHORT).show()
                        } else {
                            val k = TIPOS_ENTRADA[tipo].second
                            db.insertIncome(Income(0, valor, desc.trim().ifBlank { k }, k, data, "manual"))
                            Bus.bump()
                            Toast.makeText(ctx, "Entrada salva! ✅", Toast.LENGTH_SHORT).show()
                            valor = 0; desc = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Verde),
                ) { Text("Salvar entrada", fontSize = 17.sp) }
            }
        }

        item {
            AppCard(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("📥 Entradas do mês")
                if (lista.isEmpty()) Hint("Nenhuma entrada registrada neste mês.")
                lista.forEach { i ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { apagar = i }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(VerdeOk.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(emojiEntrada(i.kind), fontSize = 20.sp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(i.desc, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextoForte, maxLines = 1)
                            Hint(dateFmt.format(Date(i.dateMs)) + if (i.source != "manual" && i.source != "backup") " · automático" else "")
                        }
                        Text("+ ${money(i.cents)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VerdeOk)
                    }
                }
                if (lista.isNotEmpty()) Hint("Toque em uma entrada para apagar (ex.: um Pix que era só devolução).")
            }
        }
    }

    apagar?.let { i ->
        AlertDialog(
            onDismissRequest = { apagar = null },
            title = { Text("Apagar esta entrada?") },
            text = { Text("${i.desc} — ${money(i.cents)}") },
            confirmButton = {
                TextButton(onClick = { db.deleteIncome(i.id); Bus.bump(); apagar = null }) {
                    Text("Apagar", color = Vermelho, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { apagar = null }) { Text("Cancelar") } },
        )
    }
}

// ============================================================ Exportar e backup

fun shareApp(ctx: Context) {
    val link = "https://github.com/${BuildConfig.REPO}/releases/latest"
    val texto = "Estou usando o Meus Gastos para controlar meus gastos de um jeito simples. " +
        "Baixe aqui (arquivo meus-gastos.apk): $link"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
    ctx.startActivity(Intent.createChooser(send, "Indicar o Meus Gastos"))
}

/** Janela com o resultado de uma importação. */
@Composable
fun ImportResultDialog(r: Planilha.Resultado, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (!r.reconhecido) "⚠️ Planilha não reconhecida" else if (r.gastos + r.entradas > 0) "✅ Importação concluída" else "Nada novo para importar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!r.reconhecido) {
                    Text("Não encontrei a linha de cabeçalho (precisa ter pelo menos as colunas \"Data\" e \"Valor\"). Baixe o modelo em 📥 Importar planilha.")
                } else {
                    Text("🧾 Gastos importados: ${r.gastos}")
                    Text("💰 Entradas importadas: ${r.entradas}")
                    if (r.repetidas > 0) Text("🔁 Já existiam (ignoradas): ${r.repetidas}")
                }
                if (r.erros.isNotEmpty()) {
                    Text("❌ Linhas com problema: ${r.erros.size}", fontWeight = FontWeight.Bold, color = Vermelho)
                    r.erros.take(8).forEach { Hint(it) }
                    if (r.erros.size > 8) Hint("… e mais ${r.erros.size - 8}")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
fun DateButton(rotulo: String, data: Long, modifier: Modifier = Modifier, onPick: (Long) -> Unit) {
    val ctx = LocalContext.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Fundo)
            .border(1.dp, Linha, RoundedCornerShape(14.dp))
            .clickable { pickDate(ctx, data, onPick, allowFuture = true) }
            .padding(12.dp)
    ) {
        Hint(rotulo)
        Text(dayFmt.format(Date(data)) + "/" + java.util.Calendar.getInstance().apply { timeInMillis = data }.get(java.util.Calendar.YEAR), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextoForte)
    }
}

private fun inicioDoDia(ms: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = ms; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
fun BackupScreen(db: Db, pad: PaddingValues, month: Int) {
    val ctx = LocalContext.current
    var periodo by remember { mutableIntStateOf(0) } // 0 = mês, 1 = escolher datas, 2 = tudo
    var de by remember { mutableLongStateOf(Dates.range(month).first) }
    var ate by remember { mutableLongStateOf(minOf(System.currentTimeMillis(), Dates.range(month).second - 1)) }
    var excel by remember { mutableStateOf(true) }
    var arquivo by remember { mutableStateOf<java.io.File?>(null) }
    var resultado by remember { mutableStateOf<Planilha.Resultado?>(null) }

    val salvarXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Planilha.MIME_XLSX)) { uri: Uri? ->
        val f = arquivo
        if (uri != null && f != null) Toast.makeText(ctx, if (Planilha.salvarEm(ctx, f, uri)) "Arquivo salvo ✅" else "Não consegui salvar", Toast.LENGTH_SHORT).show()
    }
    val salvarCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Planilha.MIME_CSV)) { uri: Uri? ->
        val f = arquivo
        if (uri != null && f != null) Toast.makeText(ctx, if (Planilha.salvarEm(ctx, f, uri)) "Arquivo salvo ✅" else "Não consegui salvar", Toast.LENGTH_SHORT).show()
    }
    val restaurar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            resultado = Planilha.importar(ctx, db, uri)
            Cats.load(db)
            Bus.bump()
        }
    }

    fun gerar(): java.io.File? {
        val (ini, fim, texto, nome) = when (periodo) {
            0 -> { val r = Dates.range(month); Quatro(r.first, r.second, Dates.full(month), "gastos_${Dates.year(month)}_${Dates.abbr(month)}") }
            1 -> {
                val a = inicioDoDia(minOf(de, ate))
                val b = inicioDoDia(maxOf(de, ate)) + Dates.DAY_MS
                val fmt = java.text.SimpleDateFormat("dd-MM-yyyy", ptBR)
                Quatro(a, b, "${fullDayFmt.format(Date(a)).substringAfter(", ")} a ${fullDayFmt.format(Date(b - 1)).substringAfter(", ")}",
                    "gastos_${fmt.format(Date(a))}_a_${fmt.format(Date(b - 1))}")
            }
            else -> Quatro(0L, Long.MAX_VALUE, "Todos os registros", "gastos_todos")
        }
        val f = Planilha.exportar(ctx, db, ini, fim, texto, nome, excel)
        if (f == null) Toast.makeText(ctx, "Não há registros nesse período.", Toast.LENGTH_SHORT).show()
        arquivo = f
        return f
    }

    Column(
        Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppCard {
            SectionTitle("📅 Qual período?")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("${Dates.abbr(month)}/${Dates.year(month)}", periodo == 0, Modifier.weight(1f)) { periodo = 0 }
                Chip("De … até", periodo == 1, Modifier.weight(1f)) { periodo = 1 }
                Chip("Tudo", periodo == 2, Modifier.weight(1f)) { periodo = 2 }
            }
            if (periodo == 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateButton("De", de, Modifier.weight(1f)) { de = it }
                    DateButton("Até", ate, Modifier.weight(1f)) { ate = it }
                }
                Hint("Ex.: de 15/10 até 30/11. Toque na data para mudar.")
            }
            if (periodo == 0) Hint("Para outro mês, escolha o mês no Início e volte aqui.")
        }
        AppCard {
            SectionTitle("📄 Formato")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("📗 Excel (.xlsx)", excel, Modifier.weight(1f)) { excel = true }
                Chip("📄 CSV", !excel, Modifier.weight(1f)) { excel = false }
            }
            Hint("A planilha tem uma aba com os lançamentos (gastos e entradas, com data, categoria, estabelecimento, parcela e valor) e outra com o resumo. Ela também serve de backup.")
        }
        Button(
            onClick = { gerar()?.let { Planilha.compartilhar(ctx, it, "Meus Gastos — ${it.nameWithoutExtension}") } },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Verde),
        ) { Text("📤 Compartilhar (WhatsApp, e-mail, Drive…)", fontSize = 16.sp) }
        OutlinedButton(
            onClick = { gerar()?.let { f -> if (excel) salvarXlsx.launch(f.name) else salvarCsv.launch(f.name) } },
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("💾 Salvar no celular", fontSize = 16.sp) }

        AppCard {
            SectionTitle("📥 Restaurar backup")
            Hint("Escolha uma planilha exportada pelo Meus Gastos (Excel ou CSV). Só adiciona o que estiver faltando; nada é apagado.")
            OutlinedButton(onClick = { restaurar.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Escolher arquivo de backup")
            }
        }
        AppCard {
            SectionTitle("💡 Dica")
            Hint("Uma vez por mês, exporte \"Tudo\" e guarde no Google Drive. Se trocar de celular, é só restaurar.")
        }
    }

    resultado?.let { r -> ImportResultDialog(r) { resultado = null } }
}

private data class Quatro(val a: Long, val b: Long, val c: String, val d: String)

// ============================================================ Importar planilha

@Composable
fun ImportScreen(db: Db, pad: PaddingValues) {
    val ctx = LocalContext.current
    var resultado by remember { mutableStateOf<Planilha.Resultado?>(null) }
    var modelo by remember { mutableStateOf<java.io.File?>(null) }
    val salvarModelo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Planilha.MIME_XLSX)) { uri: Uri? ->
        val f = modelo
        if (uri != null && f != null) Toast.makeText(ctx, if (Planilha.salvarEm(ctx, f, uri)) "Modelo salvo ✅" else "Não consegui salvar", Toast.LENGTH_SHORT).show()
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            resultado = Planilha.importar(ctx, db, uri)
            Cats.load(db)
            Bus.bump()
        }
    }
    val colunas = listOf(
        Triple("Tipo", "Gasto ou Entrada (vazio = Gasto)", "Gasto"),
        Triple("Data ✱", "Dia do gasto ou da entrada", "15/11/2026"),
        Triple("Hora", "Opcional", "14:30"),
        Triple("Descrição", "Onde gastou / de onde veio", "Padaria"),
        Triple("Categoria", "Uma categoria do app. Vazio = o app adivinha", "Alimentação"),
        Triple("Valor (R$) ✱", "Parcelado: valor de UMA parcela", "25,90"),
        Triple("Parcela atual", "Só se for parcelado: a última paga", "4"),
        Triple("Total de parcelas", "Só se for parcelado", "10"),
        Triple("Criar próximas parcelas?", "Sim = cria as que faltam nos próximos meses", "Sim"),
    )

    Column(
        Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppCard {
            SectionTitle("📥 Trazer gastos de uma planilha")
            Text("1️⃣  Baixe o modelo abaixo.", fontSize = 15.sp, color = TextoForte)
            Text("2️⃣  Preencha no Excel, Google Planilhas ou WPS (uma linha por gasto ou entrada).", fontSize = 15.sp, color = TextoForte)
            Text("3️⃣  Volte aqui e toque em \"Importar planilha\".", fontSize = 15.sp, color = TextoForte)
            Hint("Também aceita planilhas exportadas pelo próprio app.")
        }
        AppCard {
            SectionTitle("📗 Modelo")
            Hint("Já vem com os cabeçalhos certos, exemplos e uma aba \"Como preencher\".")
            Button(
                onClick = { val f = Planilha.modelo(ctx); modelo = f; Planilha.compartilhar(ctx, f, "Modelo — Meus Gastos") },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Verde),
            ) { Text("📤 Enviar modelo (WhatsApp, e-mail, Drive…)") }
            OutlinedButton(
                onClick = { val f = Planilha.modelo(ctx); modelo = f; salvarModelo.launch(f.name) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("💾 Salvar modelo no celular") }
        }
        AppCard {
            SectionTitle("🧩 Colunas da planilha")
            Hint("A 1ª linha precisa ter estes nomes. ✱ = obrigatória. A ordem das colunas pode mudar.")
            colunas.forEach { (nome, explica, ex) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Fundo).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(nome, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                        Hint(explica)
                    }
                    Text(ex, fontSize = 13.sp, color = Verde, fontWeight = FontWeight.Medium)
                }
            }
            Hint("Valores aceitos: 25,90 · R$ 25,90 · 1.234,56. Datas: 15/11/2026 ou 2026-11-15. Categoria que não existe é criada sozinha.")
        }
        Button(
            onClick = { importar.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Verde),
        ) { Text("📥 Importar planilha (.xlsx ou .csv)", fontSize = 17.sp) }
        Hint("Importar o mesmo arquivo duas vezes não duplica: linhas iguais são ignoradas.")
        Spacer(Modifier.height(16.dp))
    }

    resultado?.let { r -> ImportResultDialog(r) { resultado = null } }
}

// ============================================================ Limites por categoria

@Composable
fun LimitsScreen(db: Db, pad: PaddingValues) {
    val ctx = LocalContext.current
    val tick = Bus.tick.intValue
    var alerta by remember { mutableIntStateOf(Prefs.alertPct(ctx)) }
    var limites by remember(tick) { mutableStateOf(db.limits()) }
    val r = remember { Dates.range(0) }
    val gastos = remember(tick) { db.expenses(r.first, r.second).groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.cents } } }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            AppCard {
                SectionTitle("🔔 Quando avisar?")
                Hint("O app avisa quando você chegar nessa % do limite, e de novo se passar de 100%.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(50, 60, 70, 80, 90).forEach { p ->
                        Chip("$p%", alerta == p, Modifier.weight(1f)) {
                            alerta = p
                            Prefs.setAlertPct(ctx, p)
                            Bus.bump()
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                SectionTitle("🎯 Limite por mês")
                Hint("Deixe vazio para não ter limite. Os valores ficam salvos na hora.")
            }
        }
        items(Cats.list, key = { it.name }) { c ->
            val lim = limites[c.name] ?: 0L
            val g = gastos[c.name] ?: 0L
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatIcon(c, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                        Hint("Gasto neste mês: ${money(g)}" + if (lim > 0) " · ${(g * 100 / lim)}% do limite" else "")
                    }
                }
                MoneyField(lim, { v ->
                    db.setLimit(c.name, v)
                    limites = db.limits()
                    Limits.check(ctx, db, c.name)
                }, label = "Limite do mês")
                if (lim > 0) LimitBar(g, lim, alerta)
            }
        }
    }
}

// ============================================================ Categorias

private val EMOJIS = listOf(
    "🍔", "🛒", "🚗", "💊", "🏠", "🛍️", "🎮", "📚", "👤", "📦", "🐶", "👶", "💇", "🏋️", "✈️", "🎁",
    "☕", "🍺", "⛽", "🚌", "📱", "💡", "💧", "🧾", "🏦", "💳", "🎓", "🏥", "🧹", "🔧", "🎬", "🎵",
    "👗", "👟", "🍕", "🥦", "🐱", "🌱", "⛪", "❤️",
)
private val CORES = listOf(
    0xFFF4A261, 0xFF2A9D8F, 0xFF457B9D, 0xFFE76F51, 0xFF8D6E63, 0xFF9C4DCC,
    0xFFE5487D, 0xFF3F51B5, 0xFF00A5B8, 0xFF78858A, 0xFF43A047, 0xFFFFB300,
)

@Composable
fun CategoriesScreen(db: Db, pad: PaddingValues) {
    var editando by remember { mutableStateOf<CatInfo?>(null) }
    var criando by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            AppCard {
                SectionTitle("🏷️ Suas categorias")
                Hint("Crie, renomeie, troque o ícone e a cor. Fica só no seu celular.")
                Button(
                    onClick = { criando = true }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Verde),
                ) { Text("➕ Nova categoria") }
            }
        }
        items(Cats.list, key = { it.name }) { c ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
                    .clickable { editando = c }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CatIcon(c, 44.dp)
                Spacer(Modifier.width(12.dp))
                Text(c.name, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = TextoForte)
                Text("✏️", fontSize = 18.sp)
            }
        }
    }

    if (criando) CategoryDialog(db, null) { criando = false }
    editando?.let { c -> CategoryDialog(db, c) { editando = null } }
}

@Composable
fun CategoryDialog(db: Db, original: CatInfo?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var nome by remember { mutableStateOf(original?.name ?: "") }
    var emoji by remember { mutableStateOf(original?.emoji ?: "📦") }
    var cor by remember { mutableLongStateOf(original?.color?.toArgb()?.toLong()?.and(0xFFFFFFFFL) ?: CORES[0]) }
    var confirmarApagar by remember { mutableStateOf(false) }
    val podeApagar = original != null && original.name != Db.FALLBACK_CATEGORY

    if (confirmarApagar && original != null) {
        AlertDialog(
            onDismissRequest = { confirmarApagar = false },
            title = { Text("Apagar \"${original.name}\"?") },
            text = { Text("Os gastos dessa categoria vão para \"${Db.FALLBACK_CATEGORY}\". Nenhum gasto é apagado.") },
            confirmButton = {
                TextButton(onClick = {
                    db.deleteCategory(original.name); Cats.load(db); Bus.bump(); onDismiss()
                }) { Text("Apagar", color = Vermelho, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmarApagar = false }) { Text("Cancelar") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Nova categoria" else "Editar categoria") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatIcon(CatInfo(nome, emoji, Color(cor)), 52.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = nome, onValueChange = { nome = it.take(24) },
                        label = { Text("Nome") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Text("Ícone", fontWeight = FontWeight.Medium)
                EMOJIS.chunked(8).forEach { linha ->
                    Row(Modifier.fillMaxWidth()) {
                        linha.forEach { e ->
                            Text(
                                e, fontSize = 22.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (e == emoji) Verde.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable { emoji = e }.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
                Text("Cor", fontWeight = FontWeight.Medium)
                CORES.chunked(6).forEach { linha ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        linha.forEach { c ->
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                                    .border(if (c == cor) 3.dp else 0.dp, TextoForte, CircleShape)
                                    .clickable { cor = c }
                            )
                        }
                    }
                }
                if (podeApagar) {
                    TextButton(onClick = { confirmarApagar = true }) { Text("🗑 Apagar categoria", color = Vermelho) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = nome.trim()
                    val existe = Cats.list.any { it.name.equals(n, ignoreCase = true) && it.name != original?.name }
                    when {
                        n.isEmpty() -> Toast.makeText(ctx, "Digite um nome", Toast.LENGTH_SHORT).show()
                        existe -> Toast.makeText(ctx, "Já existe uma categoria com esse nome", Toast.LENGTH_SHORT).show()
                        original?.name == Db.FALLBACK_CATEGORY && n != Db.FALLBACK_CATEGORY ->
                            Toast.makeText(ctx, "\"${Db.FALLBACK_CATEGORY}\" não pode ser renomeada", Toast.LENGTH_SHORT).show()
                        else -> {
                            val pos = db.categories().firstOrNull { it.name == original?.name }?.pos ?: 0
                            db.saveCategory(Category(n, emoji, cor, pos), original?.name)
                            Cats.load(db)
                            Bus.bump()
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Verde),
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
