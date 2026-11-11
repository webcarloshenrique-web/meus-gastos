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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
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
fun SettingsScreen(db: Db, pad: PaddingValues, go: (Screen) -> Unit) {
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
    var autoEntrada by remember { mutableStateOf(Prefs.autoIncome(ctx)) }
    var avisarLido by remember { mutableStateOf(Prefs.confirmRead(ctx)) }
    var statusAtualizacao by remember { mutableStateOf("") }

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
                    val ent = Parser.parseIncome("", textoTeste)
                    val p = if (ent == null) Parser.parse("", textoTeste) else null
                    resultado = when {
                        ent != null -> "💰 Entrada: ${money(ent.cents)} · ${ent.desc}" +
                            (if (ent.cnpj.isNotBlank()) " · CNPJ ${ent.cnpj}" else "") +
                            (if (Salario.pareceSalario(ent)) " · o app vai perguntar se é salário" else "")
                        p == null -> "✖ Não reconheci (pode ser fatura, propaganda ou estorno)."
                        else -> {
                            val c = Categorizer.categorize(db, p.merchant)
                            val ci = Cats.of(c)
                            val parc = when {
                                p.parcelaAtual > 0 -> " · parcela ${p.parcelaAtual}/${p.parcelas}"
                                p.parcelas > 1 -> " · ${p.parcelas}x de ${money(p.cents)} (total ${money(p.cents * p.parcelas + p.firstExtra)})"
                                else -> ""
                            }
                            val pg = (if (p.pay.isNotBlank()) " · ${Pagamento.emoji(p.pay)} ${p.pay}" else "") +
                                (if (p.final4.isNotBlank()) " · cartão final ${p.final4}" else "")
                            "✔ Gasto: ${money(p.cents + p.firstExtra)} · ${p.merchant}$parc$pg · ${ci.emoji} " +
                                if (c == null) "iria para Pendentes" else ci.name
                        }
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

        // 4) Aparência, entradas e atalhos
        item {
            AppCard {
                SectionTitle("🎨 Cores do app")
                Tema.opcoes.chunked(3).forEachIndexed { linha, grupo ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        grupo.forEachIndexed { j, p ->
                            val idx = linha * 3 + j
                            val sel = Tema.atual == p
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                    .background(if (sel) p.primary.copy(alpha = 0.12f) else Fundo)
                                    .border(if (sel) 2.dp else 1.dp, if (sel) p.primary else Linha, RoundedCornerShape(14.dp))
                                    .clickable { Tema.set(ctx, idx) }
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.size(width = 56.dp, height = 22.dp).clip(RoundedCornerShape(50))
                                        .background(Brush.horizontalGradient(listOf(p.primary, p.light)))
                                )
                                Spacer(Modifier.height(6.dp))
                                Text("${p.emoji} ${p.nome}", fontSize = 12.sp, color = TextoForte, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("💰 Entradas automáticas")
                        Hint("Registrar sozinho Pix recebido, depósito e salário avisados pelo banco.")
                    }
                    Switch(checked = autoEntrada, onCheckedChange = { autoEntrada = it; Prefs.setAutoIncome(ctx, it) })
                }
            }
        }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("🔔 Avisar o que foi lido")
                        Hint("Mostra um aviso discreto (sem som) a cada gasto ou entrada que o app registrar sozinho.")
                    }
                    Switch(checked = avisarLido, onCheckedChange = { avisarLido = it; Prefs.setConfirmRead(ctx, it) })
                }
            }
        }
        item {
            AppCard {
                SectionTitle("⚙️ Mais opções")
                OutlinedButton(onClick = { go(Screen.LIMITS) }, modifier = Modifier.fillMaxWidth()) { Text("🎯 Limites por categoria") }
                OutlinedButton(onClick = { go(Screen.CATEGORIES) }, modifier = Modifier.fillMaxWidth()) { Text("🏷️ Categorias personalizadas") }
                OutlinedButton(onClick = { go(Screen.BACKUP) }, modifier = Modifier.fillMaxWidth()) { Text("📤 Exportar gastos / backup") }
                OutlinedButton(onClick = { go(Screen.IMPORT) }, modifier = Modifier.fillMaxWidth()) { Text("📥 Importar planilha") }
                OutlinedButton(onClick = { shareApp(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("📲 Indicar o app para alguém") }
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

}
