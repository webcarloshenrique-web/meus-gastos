package br.com.meusgastos

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
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

    // Parcelamento
    var parcelado by remember { mutableStateOf(false) }
    var modoValor by remember { mutableIntStateOf(0) } // 0 = valor da parcela, 1 = valor total
    var totalParcelas by remember { mutableIntStateOf(3) }
    var parcelaAtual by remember { mutableIntStateOf(1) }
    var lancarAnteriores by remember { mutableStateOf(false) }

    val valorParcela = if (parcelado && modoValor == 1 && totalParcelas > 0) valor / totalParcelas else valor
    val sobraDivisao = if (parcelado && modoValor == 1) valor - valorParcela * totalParcelas else 0L

    Column(
        Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppCard {
            SectionTitle("💵 Quanto você gastou?")
            if (parcelado) {
                Segmented(listOf("Valor da parcela", "Valor total"), modoValor, { modoValor = it })
            }
            MoneyField(valor, { valor = it; erro = "" }, big = true)
            Hint("Digite só os números: 2590 vira R$ 25,90")
            Text("📝 Com o quê?", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextoForte)
            OutlinedTextField(
                value = desc, onValueChange = { desc = it },
                placeholder = { Text("Ex: almoço, padaria, geladeira") },
                textStyle = TextStyle(fontSize = 18.sp),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }

        // Tipo de gasto
        AppCard {
            SectionTitle("🔖 Que tipo de gasto?")
            Segmented(listOf("🛒 Compra", "🔁 Fixo (todo mês)"), if (fixo) 1 else 0, { fixo = it == 1; if (fixo) parcelado = false })
            Hint(
                if (fixo) "Contas que se repetem todo mês: aluguel, internet, academia, escola… O app lança sozinho no mesmo dia, todo mês."
                else "Compras do dia a dia: mercado, lanche, loja…"
            )
        }

        AppCard {
            SectionTitle("💳 Como pagou?")
            PayChips(pay) { pay = it }
        }

        // Parcelado
        if (!fixo) AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("💳 Compra parcelada?")
                    Hint("Ex.: uma compra em 10x no cartão")
                }
                Switch(checked = parcelado, onCheckedChange = { parcelado = it; if (parcelaAtual > totalParcelas) parcelaAtual = 1 })
            }
            if (parcelado) {
                Text("Em quantas parcelas?", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextoForte)
                Stepper(totalParcelas, 2..48) {
                    totalParcelas = it
                    if (parcelaAtual > it) parcelaAtual = it
                }
                Text("Qual parcela você pagou por último?", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextoForte)
                Hint("Compra nova = 1. Se já vinha pagando, escolha a última que pagou (ex.: 4 de 10).")
                Stepper(parcelaAtual, 1..totalParcelas) { parcelaAtual = it }
                if (parcelaAtual > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = lancarAnteriores, onCheckedChange = { lancarAnteriores = it })
                        Text(
                            "Lançar também as parcelas 1 a ${parcelaAtual - 1} nos meses passados",
                            fontSize = 14.sp, color = TextoForte,
                        )
                    }
                }
                if (valor > 0) {
                    val restantes = totalParcelas - parcelaAtual
                    Text(
                        "📌 O app vai lançar a parcela $parcelaAtual/$totalParcelas de ${money(valorParcela + if (parcelaAtual == 1) sobraDivisao else 0L)} " +
                            "na data abaixo" +
                            (if (restantes > 0) " e mais $restantes parcela(s) de ${money(valorParcela)}, uma por mês." else ".") +
                            " Total da compra: ${money(valorParcela * totalParcelas + sobraDivisao)}.",
                        fontSize = 14.sp, color = TextoForte,
                    )
                }
            }
        }

        AppCard {
            SectionTitle(
                when {
                    fixo -> "📅 Quando vence (ou venceu) este mês?"
                    parcelado -> "📅 Quando pagou essa parcela?"
                    else -> "📅 Quando?"
                }
            )
            DateChooser(data) { data = it }
            if (fixo) {
                val dia = java.util.Calendar.getInstance().apply { timeInMillis = data }.get(java.util.Calendar.DAY_OF_MONTH)
                Hint("🔁 Vai repetir todo dia $dia. Para parar ou mudar o valor: Menu → Gastos fixos.")
            }
        }

        AppCard {
            SectionTitle("🏷️ Categoria")
            CategoryGrid(cat) { c -> cat = if (cat == c) null else c }
            Hint("Se não escolher, o gasto vai para ❓ Pendentes.")
        }

        if (erro.isNotEmpty()) Text(erro, color = Vermelho, fontSize = 16.sp)

        Button(
            onClick = {
                if (valor <= 0) {
                    erro = "Digite o valor do gasto"
                } else {
                    val nome = desc.trim().ifBlank { cat ?: "Gasto" }
                    when {
                        fixo -> Recorrentes.criar(db, valor, nome, cat, data, pay)
                        parcelado -> Installments.register(
                            db, nome, cat, data, "manual", valorParcela, totalParcelas,
                            from = parcelaAtual, withPrevious = lancarAnteriores, firstExtra = sobraDivisao, pay = pay,
                        )
                        else -> db.insertExpense(Expense(0, valor, nome, cat, data, "manual", pay = pay))
                    }
                    Limits.check(ctx, db, cat)
                    Bus.bump()
                    Toast.makeText(ctx, "Gasto salvo! ✅", Toast.LENGTH_SHORT).show()
                    valor = 0; desc = ""; cat = null; parcelado = false; parcelaAtual = 1; pay = ""; fixo = false
                    onSaved()
                }
            },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Verde),
        ) { Text("Salvar gasto", fontSize = 19.sp) }
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
