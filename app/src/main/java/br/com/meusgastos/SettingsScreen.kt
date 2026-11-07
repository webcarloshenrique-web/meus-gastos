package br.com.meusgastos

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

private fun isListenerEnabled(ctx: Context): Boolean =
    Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        ?.contains(ctx.packageName) == true

@Composable
fun SettingsScreen(db: Db, pad: PaddingValues, month: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = Bus.tick.intValue

    // Atualiza o status da permissão sempre que você volta para o app.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_RESUME) refresh += 1 }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val enabled = remember(refresh, tick) { isListenerEnabled(ctx) }

    val apps = remember { Banks.apps(ctx) }
    val nomes = remember(apps) { apps.associate { it.pkg to it.label } }
    var selecionados by remember { mutableStateOf(Prefs.monitored(ctx)) }
    var abrirBancos by remember { mutableStateOf(false) }
    var mostrarTodos by remember { mutableStateOf(false) }
    var abrirTextos by remember { mutableStateOf(false) }
    val logs = remember(tick) { db.logs(30) }
    val visiveis = if (mostrarTodos) apps else apps.filter { it.bank || it.pkg in selecionados }

    var textoTeste by remember { mutableStateOf("Compra de R$ 25,90 APROVADA em IFOOD") }
    var resultado by remember { mutableStateOf<String?>(null) }
    var exportar by remember { mutableStateOf(false) }
    var statusAtualizacao by remember { mutableStateOf("") }

    val restaurar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val n = try { Csv.restore(ctx, db, uri) } catch (_: Exception) { -1 }
            Bus.bump()
            val msg = when {
                n < 0 -> "Esse arquivo não é um backup do Meus Gastos."
                n == 0 -> "Nada novo para restaurar (os gastos já estavam aqui)."
                else -> "✅ $n gastos restaurados!"
            }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1) Permissão
        item {
            AppCard {
                SectionTitle("🔔 Leitura das notificações")
                Text(
                    if (enabled) "✅ ATIVADA — os gastos do banco entram sozinhos." else "⚠️ DESATIVADA",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (enabled) Verde else Color(0xFFB26A00),
                )
                if (!enabled) {
                    Hint(
                        "1. Toque em \"Liberar configurações restritas\", depois no menu ⋮ (canto de cima) " +
                            "e em \"Permitir configurações restritas\". Se não aparecer, pule.\n" +
                            "2. Toque em \"Ativar leitura\" e ligue o Meus Gastos."
                    )
                    OutlinedButton(onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        )
                    }, modifier = Modifier.fillMaxWidth()) { Text("1. Liberar configurações restritas") }
                    Button(
                        onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Verde),
                    ) { Text("2. Ativar leitura") }
                }
            }
        }

        // 2) Bancos
        item {
            AppCard {
                Row(
                    Modifier.fillMaxWidth().clickable { abrirBancos = !abrirBancos },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("🏦 Bancos que o app lê")
                        Hint("${selecionados.size} marcado(s) · toque para ${if (abrirBancos) "fechar" else "escolher"}")
                    }
                    Text(if (abrirBancos) "▲" else "▼", fontSize = 18.sp, color = TextoSuave)
                }
                if (abrirBancos) {
                    visiveis.forEach { a ->
                        val marcado = a.pkg in selecionados
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selecionados = if (marcado) selecionados - a.pkg else selecionados + a.pkg
                                Prefs.setMonitored(ctx, a.pkg, !marcado)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = marcado, onCheckedChange = { on ->
                                selecionados = if (on) selecionados + a.pkg else selecionados - a.pkg
                                Prefs.setMonitored(ctx, a.pkg, on)
                            })
                            Text(a.label, fontSize = 16.sp, color = TextoForte)
                        }
                    }
                    TextButton(onClick = { mostrarTodos = !mostrarTodos }) {
                        Text(if (mostrarTodos) "Mostrar só bancos" else "Meu banco não está aqui — mostrar todos os apps")
                    }
                }
            }
        }

        // 3) Testar o leitor (não grava nada)
        item {
            AppCard {
                SectionTitle("🧪 Testar o leitor")
                Hint("Cole ou digite o texto de uma notificação. É só um teste: nada é salvo e seus gastos não mudam.")
                OutlinedTextField(
                    value = textoTeste, onValueChange = { textoTeste = it; resultado = null },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
                OutlinedButton(onClick = {
                    val p = Parser.parse("", textoTeste)
                    resultado = if (p == null) {
                        "✖ Não reconheci como gasto (pode ser Pix recebido, fatura ou propaganda)."
                    } else {
                        val c = Categorizer.categorize(db, p.merchant)
                        val ci = Cats.of(c)
                        "✔ ${money(p.cents)} · ${p.merchant} · ${ci.emoji} " +
                            if (c == null) "iria para Pendentes" else ci.name
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Testar (não salva nada)") }
                resultado?.let {
                    Text(
                        it, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextoForte,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Fundo).padding(12.dp),
                    )
                }
            }
        }

        // 4) Exportar / backup
        item {
            AppCard {
                SectionTitle("📤 Relatório e backup")
                Hint("O CSV abre no Excel ou no Google Planilhas: data, categoria, estabelecimento, valor e um resumo no final. Ele também serve de backup.")
                Button(
                    onClick = { exportar = true }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Verde),
                ) { Text("📤 Exportar gastos (CSV)") }
                OutlinedButton(
                    onClick = { restaurar.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(),
                ) { Text("📥 Restaurar backup (CSV)") }
                Hint("Restaurar só adiciona o que estiver faltando. Nada é apagado.")
            }
        }

        // 5) Atualização
        item {
            AppCard {
                SectionTitle("🔄 Atualização")
                Text("Versão instalada: ${BuildConfig.VERSION_NAME}", fontSize = 15.sp, color = TextoForte)
                Hint("O app procura versões novas sozinho sempre que abre.")
                OutlinedButton(onClick = {
                    statusAtualizacao = "Procurando…"
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { Updater.check() }
                        statusAtualizacao = when {
                            r == null -> "Não consegui verificar agora. Confira a internet."
                            r.version > BuildConfig.VERSION_CODE -> {
                                UpdateUi.info = r
                                "Nova versão encontrada!"
                            }
                            else -> "✅ Você já está na versão mais recente."
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Procurar atualização agora") }
                if (statusAtualizacao.isNotEmpty()) Text(statusAtualizacao, fontSize = 15.sp, color = TextoForte)
            }
        }

        // 6) Textos recebidos
        item {
            AppCard {
                Row(
                    Modifier.fillMaxWidth().clickable { abrirTextos = !abrirTextos },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("📜 Textos que o app recebeu")
                        Hint("Para ajustar o leitor quando um gasto não for reconhecido")
                    }
                    Text(if (abrirTextos) "▲" else "▼", fontSize = 18.sp, color = TextoSuave)
                }
                if (abrirTextos) {
                    Hint("Itens com ✖ não viraram gasto. Copie e envie (apague nomes e números antes).")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val texto = logs.joinToString("\n\n") { "[${nomes[it.pkg] ?: it.pkg}] ${it.text}" }
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("textos", texto))
                            Toast.makeText(ctx, "Copiado!", Toast.LENGTH_SHORT).show()
                        }) { Text("Copiar tudo") }
                        OutlinedButton(onClick = { db.clearLogs(); Bus.bump() }) { Text("Limpar textos") }
                    }
                    if (logs.isEmpty()) Hint("Nenhum texto recebido ainda.")
                    logs.forEach { l ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Fundo).padding(10.dp)
                        ) {
                            Text(
                                "${if (l.parsed) "✔ virou gasto" else "✖ ignorado"} · ${nomes[l.pkg] ?: l.pkg} · ${dateFmt.format(Date(l.dateMs))}",
                                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextoSuave,
                            )
                            Text(l.text, fontSize = 14.sp, color = TextoForte)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.width(1.dp)) }
    }

    if (exportar) {
        AlertDialog(
            onDismissRequest = { exportar = false },
            title = { Text("Exportar quais gastos?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "📅 ${Dates.full(month)}",
                        "🗓️ Últimos 5 meses (${Dates.abbr(month - 4)} a ${Dates.abbr(month)})",
                        "📚 Todos os gastos",
                    ).forEachIndexed { i, rotulo ->
                        OutlinedButton(onClick = {
                            exportar = false
                            if (!Csv.export(ctx, db, i, month)) {
                                Toast.makeText(ctx, "Não há gastos nesse período.", Toast.LENGTH_SHORT).show()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(rotulo) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { exportar = false }) { Text("Cancelar") } },
        )
    }
}
