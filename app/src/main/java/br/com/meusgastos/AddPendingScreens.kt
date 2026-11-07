package br.com.meusgastos

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Date

// ------------------------------------------------------------ Adicionar

@Composable
fun AddScreen(db: Db, pad: PaddingValues, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var valor by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var erro by remember { mutableStateOf("") }
    val agora = remember { System.currentTimeMillis() }
    val ontem = agora - Dates.DAY_MS
    val ehHoje = Dates.sameDay(data, agora)
    val ehOntem = Dates.sameDay(data, ontem)

    Column(
        Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppCard {
            SectionTitle("💵 Quanto você gastou?")
            OutlinedTextField(
                value = valor, onValueChange = { valor = it; erro = "" },
                placeholder = { Text("0,00", fontSize = 28.sp) },
                prefix = { Text("R$ ", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
                textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Text("📝 Com o quê?", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextoForte)
            OutlinedTextField(
                value = desc, onValueChange = { desc = it },
                placeholder = { Text("Ex: almoço, padaria, gasolina") },
                textStyle = TextStyle(fontSize = 18.sp),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }

        AppCard {
            SectionTitle("📅 Quando?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateChip("Hoje", ehHoje, Modifier.weight(1f)) { data = agora }
                DateChip("Ontem", ehOntem, Modifier.weight(1f)) { data = ontem }
                DateChip("Outro dia", !ehHoje && !ehOntem, Modifier.weight(1f)) {
                    val c = Calendar.getInstance().apply { timeInMillis = data }
                    DatePickerDialog(
                        ctx,
                        { _, y, m, d ->
                            val n = Calendar.getInstance().apply { timeInMillis = data; set(y, m, d) }
                            data = n.timeInMillis
                        },
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH),
                    ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                }
            }
            Hint(fullDayFmt.format(Date(data)).replaceFirstChar { it.uppercase() })
        }

        AppCard {
            SectionTitle("🏷️ Categoria")
            CategoryGrid(cat) { c -> cat = if (cat == c) null else c }
            Hint("Se não escolher, o gasto vai para ❓ Pendentes.")
        }

        if (erro.isNotEmpty()) Text(erro, color = Vermelho, fontSize = 16.sp)

        Button(
            onClick = {
                val cents = parseCents(valor)
                if (cents == null || cents <= 0) {
                    erro = "Digite um valor válido, por exemplo 25,90"
                } else {
                    val nome = desc.trim().ifBlank { cat ?: "Gasto" }
                    db.insertExpense(Expense(0, cents, nome, cat, data, "manual"))
                    Bus.bump()
                    Toast.makeText(ctx, "Gasto salvo! ✅", Toast.LENGTH_SHORT).show()
                    valor = ""; desc = ""; cat = null
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

@Composable
fun DateChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        modifier = modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Verde else Fundo)
            .border(1.dp, if (selected) Verde else Linha, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        textAlign = TextAlign.Center,
        color = if (selected) Color.White else TextoForte,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    )
}

// ------------------------------------------------------------ Pendentes

@Composable
fun PendingScreen(db: Db, pad: PaddingValues) {
    val tick = Bus.tick.intValue
    val lista = remember(tick) { db.pending() }

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
                        Hint(dateFmt.format(Date(e.dateMs)))
                    }
                    Text(money(e.cents), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextoForte)
                }
                CategoryGrid(null) { c -> Categorizer.learn(db, e, c); Bus.bump() }
            }
        }
    }
}
