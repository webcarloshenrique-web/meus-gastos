package br.com.meusgastos

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Date

/** Abre o calendário do Android para escolher um dia (até hoje, salvo allowFuture). */
fun pickDate(ctx: Context, atual: Long, onPick: (Long) -> Unit, allowFuture: Boolean = false) {
    val c = Calendar.getInstance().apply { timeInMillis = atual }
    DatePickerDialog(
        ctx,
        { _, y, m, d ->
            val n = Calendar.getInstance().apply { timeInMillis = atual; set(y, m, d) }
            onPick(n.timeInMillis)
        },
        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH),
    ).apply { if (!allowFuture) datePicker.maxDate = System.currentTimeMillis() }.show()
}

/** Hoje / Ontem / Outro dia */
@Composable
fun DateChooser(data: Long, onChange: (Long) -> Unit) {
    val ctx = LocalContext.current
    val agora = remember { System.currentTimeMillis() }
    val ontem = agora - Dates.DAY_MS
    val ehHoje = Dates.sameDay(data, agora)
    val ehOntem = Dates.sameDay(data, ontem)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Hoje", ehHoje, Modifier.weight(1f)) { onChange(agora) }
        Chip("Ontem", ehOntem, Modifier.weight(1f)) { onChange(ontem) }
        Chip("Outro dia", !ehHoje && !ehOntem, Modifier.weight(1f)) { pickDate(ctx, data, onChange) }
    }
    Hint(fullDayFmt.format(Date(data)).replaceFirstChar { it.uppercase() })
}

// ------------------------------------------------------------ Adicionar gasto

@Composable
fun AddScreen(db: Db, pad: PaddingValues, inicialFixo: Boolean = false, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var valor by remember { mutableLongStateOf(0L) }
    var desc by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var erro by remember { mutableStateOf("") }
    var fixo by remember { mutableStateOf(inicialFixo) }
    var pay by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }

    // Parcelamento
    var parcelado by remember { mutableStateOf(false) }
    var modoValor by remember { mutableIntStateOf(0) } // 0 = valor da parcela, 1 = valor total
    var totalParcelas by remember { mutableIntStateOf(3) }
    var parcelaAtual by remember { mutableIntStateOf(1) }
    var lancarAnteriores by remember { mutableStateOf(true) }

    val valorParcela = if (parcelado && modoValor == 1 && totalParcelas > 0) valor / totalParcelas else valor
    val sobraDivisao = if (parcelado && modoValor == 1) valor - valorParcela * totalParcelas else 0L

    var catManual by remember { mutableStateOf(false) }
    var maisOpcoes by remember { mutableStateOf(inicialFixo) }
    val sugestoes = remember(desc) { db.suggestions(desc, 6).filter { it.merchant != desc.trim() } }

    fun salvar(continuar: Boolean) {
        if (valor <= 0) {
            erro = "Digite o valor do gasto"
            return
        }
        val nome = desc.trim().ifBlank { cat ?: "Gasto" }
        when {
            fixo -> Recorrentes.criar(db, valor, nome, cat, data, pay, card = card)
            // A data escolhida é a da 1ª parcela: a parcela N cai (N - 1) meses depois.
            parcelado -> Installments.register(
                db, nome, cat,
                java.util.Calendar.getInstance().apply { timeInMillis = data; add(java.util.Calendar.MONTH, parcelaAtual - 1) }.timeInMillis,
                "manual", valorParcela, totalParcelas,
                from = parcelaAtual, withPrevious = lancarAnteriores, firstExtra = sobraDivisao, pay = pay, card = card,
            )
            else -> db.insertExpense(Expense(0, valor, nome, cat, data, "manual", pay = pay, card = card))
        }
        Limits.check(ctx, db, cat)
        Bus.bump()
        Toast.makeText(ctx, "Gasto salvo: ${money(valor)} ✅", Toast.LENGTH_SHORT).show()
        valor = 0; desc = ""; cat = null; catManual = false; parcelado = false; parcelaAtual = 1; fixo = false
        if (!continuar) { pay = ""; card = ""; onSaved() }
    }

    Column(
        Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1) Valor e descrição
        AppCard {
            SectionTitle("💵 Quanto você gastou?")
            if (parcelado) {
                Segmented(listOf("Valor da parcela", "Valor total"), modoValor, { modoValor = it })
            }
            MoneyField(valor, { valor = it; erro = "" }, big = true)
            OutlinedTextField(
                value = desc,
                onValueChange = {
                    desc = it
                    if (!catManual) cat = if (it.trim().length >= 3) Categorizer.categorize(db, it) else null
                },
                label = { Text("Com o quê? (ex.: almoço, padaria)") },
                textStyle = TextStyle(fontSize = 18.sp),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (sugestoes.isNotEmpty()) {
                Hint(if (desc.isBlank()) "Onde você mais gasta (toque para preencher):" else "Do seu histórico:")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sugestoes.forEach { sg ->
                        Chip("${Cats.of(sg.category).emoji} ${sg.merchant}", false) {
                            desc = sg.merchant
                            if (sg.category != null) { cat = sg.category; catManual = true }
                            if (sg.pay.isNotBlank()) pay = sg.pay
                            if (sg.card.isNotBlank()) card = sg.card
                        }
                    }
                }
            }
        }

        // 2) Categoria
        AppCard {
            SectionTitle("🏷️ Categoria")
            if (cat != null && !catManual) Hint("✨ Sugerida pelo nome. Toque em outra para trocar.")
            CategoryGrid(cat) { c -> cat = if (cat == c) null else c; catManual = true }
            if (cat == null) Hint("Sem categoria, o gasto vai para ❓ Pendentes.")
        }

        // 3) Data
        AppCard {
            SectionTitle(
                when {
                    fixo -> "📅 Quando vence (ou venceu) este mês?"
                    parcelado -> "📅 Quando foi a 1ª parcela?"
                    else -> "📅 Quando?"
                }
            )
            DateChooser(data) { data = it }
            if (fixo) {
                val dia = java.util.Calendar.getInstance().apply { timeInMillis = data }.get(java.util.Calendar.DAY_OF_MONTH)
                Hint("🔁 Vai repetir todo dia $dia. Para parar ou mudar o valor: Menu → Gastos fixos.")
            }
        }

        // 4) Mais opções (recolhido para o lançamento ser rápido)
        AppCard {
            Row(
                Modifier.fillMaxWidth().clickable { maisOpcoes = !maisOpcoes },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("⚙️ Mais opções")
                    val resumo = listOfNotNull(
                        if (fixo) "🔁 fixo" else null,
                        if (parcelado) "💳 ${totalParcelas}x" else null,
                        if (pay.isNotBlank()) "${Pagamento.emoji(pay)} $pay" else null,
                        if (card.isNotBlank()) Cartoes.curto(card) else null,
                    )
                    Hint(if (resumo.isEmpty()) "Gasto fixo, parcelado, forma de pagamento e cartão" else resumo.joinToString(" · "))
                }
                Text(if (maisOpcoes) "▲" else "▼", fontSize = 18.sp, color = TextoSuave)
            }
            if (maisOpcoes) {
                Text("Que tipo de gasto?", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextoForte)
                Segmented(listOf("🛒 Compra", "🔁 Fixo (todo mês)"), if (fixo) 1 else 0, { fixo = it == 1; if (fixo) parcelado = false })
                Hint(
                    if (fixo) "Contas que se repetem: aluguel, internet, academia… O app lança sozinho todo mês."
                    else "Compras do dia a dia: mercado, lanche, loja…"
                )
                Text("Como pagou?", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextoForte)
                PayChips(pay) { pay = it }
                if (pay == "Crédito" || pay == "Débito" || card.isNotBlank()) {
                    Text("Qual cartão?", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextoForte)
                    CardChooser(db, card) { card = it }
                }
                if (!fixo) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Compra parcelada?", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextoForte)
                            Hint("Ex.: uma compra em 10x no cartão")
                        }
                        Switch(checked = parcelado, onCheckedChange = { parcelado = it; if (parcelaAtual > totalParcelas) parcelaAtual = 1 })
                    }
                    if (parcelado) {
                        Text("Em quantas parcelas?", fontSize = 15.sp, color = TextoForte)
                        Stepper(totalParcelas, 2..48) {
                            totalParcelas = it
                            if (parcelaAtual > it) parcelaAtual = it
                        }
                        Text("Qual parcela você pagou por último?", fontSize = 15.sp, color = TextoForte)
                        Hint("Compra nova = 1. Se já vinha pagando, escolha a última que pagou (ex.: 3 de 6). A data acima é sempre a da 1ª parcela.")
                        Stepper(parcelaAtual, 1..totalParcelas) { parcelaAtual = it }
                        if (parcelaAtual > 1) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = lancarAnteriores, onCheckedChange = { lancarAnteriores = it })
                                Text("Lançar também as parcelas já pagas (1 a ${parcelaAtual - 1})", fontSize = 14.sp, color = TextoForte)
                            }
                        }
                        if (valor > 0) {
                            val primeira = if (lancarAnteriores) 1 else parcelaAtual
                            fun mes(k: Int): String {
                                val c = java.util.Calendar.getInstance().apply { timeInMillis = data; add(java.util.Calendar.MONTH, k - 1) }
                                return "${Dates.ABBR[c.get(java.util.Calendar.MONTH)]}/${c.get(java.util.Calendar.YEAR) % 100}"
                            }
                            Text(
                                "📌 O app vai lançar as parcelas $primeira a $totalParcelas de ${money(valorParcela)}, " +
                                    "uma por mês, de ${mes(primeira)} até ${mes(totalParcelas)}. " +
                                    "Total da compra: ${money(valorParcela * totalParcelas + sobraDivisao)}.",
                                fontSize = 14.sp, color = TextoForte,
                            )
                        }
                    }
                }
            }
        }

        if (erro.isNotEmpty()) Text(erro, color = Vermelho, fontSize = 16.sp)

        Button(
            onClick = { salvar(false) },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Verde),
        ) { Text(if (valor > 0) "Salvar ${money(valor)}" else "Salvar gasto", fontSize = 19.sp) }
        OutlinedButton(
            onClick = { salvar(true) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("Salvar e lançar outro", fontSize = 16.sp) }
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------ Pendentes

@Composable
fun PendingScreen(db: Db, pad: PaddingValues) {
    val ctx = LocalContext.current
    val tick = Bus.tick.intValue
    // Uma compra parcelada aparece uma vez só (a primeira parcela pendente).
    val lista = remember(tick) {
        db.pending().distinctBy { if (it.planId > 0) "p${it.planId}" else "e${it.id}" }.reversed()
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤔", fontSize = 34.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("O que foi cada gasto?", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                        Hint("Toque na categoria. O app aprende e, da próxima vez, classifica sozinho.")
                    }
                }
            }
        }
        if (lista.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 60.sp)
                    Text("Tudo em dia!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                    Hint("Nenhum gasto esperando categoria.")
                }
            }
        }
        items(lista, key = { it.id }) { e ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatIcon(Cats.pendente, 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.merchant, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                        Hint(
                            dateFmt.format(Date(e.dateMs)) +
                                if (e.instTotal > 1) " · 💳 ${e.instTotal}x (vale para todas as parcelas)" else ""
                        )
                    }
                    Text(money(e.cents), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                }
                CategoryGrid(null) { c ->
                    Categorizer.learn(db, e, c)
                    Limits.check(ctx, db, c)
                    Bus.bump()
                }
            }
        }
    }
}
