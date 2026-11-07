package br.com.meusgastos

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Na primeira vez, já marca os apps que parecem ser de banco.
        if (!Prefs.hasChosen(this)) {
            Prefs.setAll(this, Banks.apps(this).filter { it.bank }.map { it.pkg }.toSet())
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val startTab = intent.getIntExtra("tab", 0)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1B7F5C), secondary = Color(0xFF1B7F5C))) {
                App(startTab)
            }
        }
    }
}

private val dateFmt = SimpleDateFormat("dd/MM HH:mm", ptBR)
private val monthFmt = SimpleDateFormat("MMMM 'de' yyyy", ptBR)

/** Início e fim (em ms) do mês atual + offset. */
private fun monthRange(offset: Int): Pair<Long, Long> {
    val c = Calendar.getInstance()
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    c.add(Calendar.MONTH, offset)
    val start = c.timeInMillis
    c.add(Calendar.MONTH, 1)
    return start to c.timeInMillis
}

private fun isListenerEnabled(ctx: Context): Boolean =
    Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        ?.contains(ctx.packageName) == true

@Composable
fun App(startTab: Int) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var tab by remember { mutableIntStateOf(startTab) }
    val tick = Bus.tick.intValue
    val pendingCount = remember(tick) { db.pendingCount() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Text("🏠", fontSize = 22.sp) }, label = { Text("Início") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = {
                        BadgedBox(badge = { if (pendingCount > 0) Badge { Text("$pendingCount") } }) {
                            Text("❓", fontSize = 22.sp)
                        }
                    },
                    label = { Text("Pendentes") }
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Text("➕", fontSize = 22.sp) }, label = { Text("Adicionar") }
                )
                NavigationBarItem(
                    selected = tab == 3, onClick = { tab = 3 },
                    icon = { Text("⚙️", fontSize = 22.sp) }, label = { Text("Ajustes") }
                )
            }
        }
    ) { pad ->
        when (tab) {
            0 -> HomeScreen(db, pad)
            1 -> PendingScreen(db, pad)
            2 -> AddScreen(db, pad) { tab = 0 }
            else -> SettingsScreen(db, pad)
        }
    }
}

// ------------------------------------------------------------ Início

@Composable
fun HomeScreen(db: Db, pad: PaddingValues) {
    var offset by remember { mutableIntStateOf(0) }
    val tick = Bus.tick.intValue
    val range = remember(offset) { monthRange(offset) }
    val lista = remember(tick, offset) { db.expenses(range.first, range.second) }
    val total = lista.sumOf { it.cents }
    val porCategoria = lista.groupBy { it.category ?: "Pendente" }
        .map { (cat, itens) -> cat to itens.sumOf { it.cents } }
        .sortedByDescending { it.second }

    LazyColumn(
        modifier = Modifier.padding(pad).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { offset -= 1 }) { Text("◀", fontSize = 22.sp) }
                Text(
                    monthFmt.format(Date(range.first)).replaceFirstChar { it.uppercase() },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { if (offset < 0) offset += 1 }, enabled = offset < 0) {
                    Text("▶", fontSize = 22.sp)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total gasto no mês", fontSize = 16.sp)
                    Text(money(total), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (lista.isEmpty()) {
            item {
                Text(
                    "Nenhum gasto neste mês ainda.\n\nToque em ➕ Adicionar para lançar um gasto, " +
                        "ou deixe o app ler as notificações do seu banco (veja em ⚙️ Ajustes).",
                    fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp)
                )
            }
        } else {
            item { Text("Por categoria", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            items(porCategoria) { (cat, valor) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(cat, fontSize = 17.sp)
                    Text(money(valor), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
            }
            item { Text("Últimos gastos", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
            items(lista, key = { it.id }) { e -> ExpenseRow(e) { db.delete(e.id); Bus.bump() } }
        }
    }
}

@Composable
fun ExpenseRow(e: Expense, onDelete: () -> Unit) {
    var confirmar by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.merchant, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text("${e.category ?: "❓ Pendente"} · ${dateFmt.format(Date(e.dateMs))}", fontSize = 14.sp)
            }
            Text(money(e.cents), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { confirmar = true }) { Text("🗑") }
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Apagar este gasto?") },
            text = { Text("${e.merchant} — ${money(e.cents)}") },
            confirmButton = { TextButton(onClick = { confirmar = false; onDelete() }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } }
        )
    }
}

// ------------------------------------------------------------ Pendentes

@Composable
fun CategoryButtons(selected: String?, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Categorizer.categories.chunked(2).forEach { par ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                par.forEach { c ->
                    if (c == selected) {
                        Button(onClick = { onPick(c) }, modifier = Modifier.weight(1f)) {
                            Text(c, fontSize = 15.sp, maxLines = 1)
                        }
                    } else {
                        OutlinedButton(onClick = { onPick(c) }, modifier = Modifier.weight(1f)) {
                            Text(c, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                }
                if (par.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PendingScreen(db: Db, pad: PaddingValues) {
    val tick = Bus.tick.intValue
    val lista = remember(tick) { db.pending() }
    LazyColumn(
        modifier = Modifier.padding(pad).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text("O que foi cada gasto?", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Toque na categoria. O app aprende e, da próxima vez, já classifica sozinho.", fontSize = 15.sp)
        }
        if (lista.isEmpty()) {
            item { Text("Tudo certo! Nenhum gasto esperando. 🎉", fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp)) }
        }
        items(lista, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(e.merchant, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${money(e.cents)} · ${dateFmt.format(Date(e.dateMs))}", fontSize = 16.sp)
                    CategoryButtons(null) { c -> Categorizer.learn(db, e, c); Bus.bump() }
                }
            }
        }
    }
}

// ------------------------------------------------------------ Adicionar

@Composable
fun AddScreen(db: Db, pad: PaddingValues, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    var valor by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    var erro by remember { mutableStateOf("") }

    Column(
        Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Novo gasto", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = valor, onValueChange = { valor = it; erro = "" },
            label = { Text("Quanto gastou? (ex: 25,90)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true, textStyle = TextStyle(fontSize = 22.sp),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = desc, onValueChange = { desc = it },
            label = { Text("Com o quê? (ex: almoço, mercado)") },
            singleLine = true, textStyle = TextStyle(fontSize = 18.sp),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Categoria", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        CategoryButtons(cat) { c -> cat = if (cat == c) null else c }
        if (erro.isNotEmpty()) Text(erro, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
        Button(
            onClick = {
                val cents = parseCents(valor)
                if (cents == null || cents <= 0) {
                    erro = "Digite um valor válido, por exemplo 25,90"
                } else {
                    val nome = desc.trim().ifBlank { cat ?: "Gasto" }
                    db.insertExpense(Expense(0, cents, nome, cat, System.currentTimeMillis(), "manual"))
                    Bus.bump()
                    Toast.makeText(ctx, "Gasto salvo!", Toast.LENGTH_SHORT).show()
                    valor = ""; desc = ""; cat = null
                    onSaved()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Salvar", fontSize = 20.sp) }
        Text("Se não escolher categoria, o gasto vai para ❓ Pendentes.", fontSize = 14.sp)
    }
}

// ------------------------------------------------------------ Ajustes

@Composable
fun SettingsScreen(db: Db, pad: PaddingValues) {
    val ctx = LocalContext.current
    val tick = Bus.tick.intValue
    var refresh by remember { mutableIntStateOf(0) }
    val enabled = remember(refresh, tick) { isListenerEnabled(ctx) }
    val apps = remember { Banks.apps(ctx) }
    val nomes = remember(apps) { apps.associate { it.pkg to it.label } }
    var selecionados by remember { mutableStateOf(Prefs.monitored(ctx)) }
    var mostrarTodos by remember { mutableStateOf(false) }
    val logs = remember(tick) { db.logs(30) }
    val visiveis = if (mostrarTodos) apps else apps.filter { it.bank || it.pkg in selecionados }

    LazyColumn(
        modifier = Modifier.padding(pad).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item { Text("Ajustes", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

        // 1) Permissão
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (enabled) "✅ Leitura das notificações: ATIVADA" else "⚠️ Leitura das notificações: DESATIVADA",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    if (!enabled) {
                        Text(
                            "Para o app ler os gastos do banco:\n" +
                                "1. Toque em \"Liberar configurações restritas\", depois no menu ⋮ (canto de cima) " +
                                "e em \"Permitir configurações restritas\". (Se não aparecer, pule este passo.)\n" +
                                "2. Volte e toque em \"Ativar leitura\" e ligue o Meus Gastos.",
                            fontSize = 15.sp
                        )
                        OutlinedButton(onClick = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                            )
                        }, modifier = Modifier.fillMaxWidth()) { Text("1. Liberar configurações restritas") }
                        Button(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }, modifier = Modifier.fillMaxWidth()) { Text("2. Ativar leitura") }
                        TextButton(onClick = { refresh += 1 }) { Text("Já fiz, verificar de novo") }
                    }
                }
            }
        }

        // 2) Quais apps ler
        item {
            Text("Quais apps de banco ler", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text("Marque os bancos e cartões que você usa.", fontSize = 15.sp)
        }
        items(visiveis, key = { it.pkg }) { a ->
            val marcado = a.pkg in selecionados
            Row(
                Modifier.fillMaxWidth().clickable {
                    selecionados = if (marcado) selecionados - a.pkg else selecionados + a.pkg
                    Prefs.setMonitored(ctx, a.pkg, !marcado)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = marcado, onCheckedChange = { on ->
                    selecionados = if (on) selecionados + a.pkg else selecionados - a.pkg
                    Prefs.setMonitored(ctx, a.pkg, on)
                })
                Text(a.label, fontSize = 17.sp)
            }
        }
        item {
            TextButton(onClick = { mostrarTodos = !mostrarTodos }) {
                Text(if (mostrarTodos) "Mostrar só bancos" else "Meu banco não está na lista — mostrar todos os apps")
            }
        }

        // 3) Testes e textos recebidos
        item {
            Text("Testar o app", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    Processor.process(ctx, "teste", "Compra aprovada", "Compra de R$ 25,90 APROVADA em IFOOD")
                    Toast.makeText(ctx, "Teste lançado em Alimentação", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f)) { Text("Compra conhecida") }
                OutlinedButton(onClick = {
                    Processor.process(ctx, "teste", "Compra aprovada", "Compra de R$ 89,00 APROVADA em LOJA DO ZE")
                    Toast.makeText(ctx, "Teste foi para Pendentes", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.weight(1f)) { Text("Compra desconhecida") }
            }
            Text("Dica: depois de testar, apague os testes na tela Início (🗑).", fontSize = 14.sp)
        }
        item {
            Text("Textos que o app recebeu", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Mostra as últimas notificações dos bancos marcados. Se um gasto não for reconhecido (✖), " +
                    "copie e envie para ajustar o leitor. Antes de enviar, apague nomes e números.",
                fontSize = 14.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val texto = logs.joinToString("\n\n") { "[${nomes[it.pkg] ?: it.pkg}] ${it.text}" }
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("textos", texto))
                    Toast.makeText(ctx, "Copiado!", Toast.LENGTH_SHORT).show()
                }) { Text("Copiar tudo") }
                OutlinedButton(onClick = { db.clearLogs(); Bus.bump() }) { Text("Limpar") }
            }
        }
        if (logs.isEmpty()) {
            item { Text("Nenhum texto recebido ainda.", fontSize = 15.sp) }
        }
        items(logs, key = { "log" + it.id }) { l ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "${if (l.parsed) "✔ virou gasto" else "✖ ignorado"} · ${nomes[l.pkg] ?: l.pkg} · ${dateFmt.format(Date(l.dateMs))}",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium
                    )
                    Text(l.text, fontSize = 14.sp)
                }
            }
        }
    }
}
