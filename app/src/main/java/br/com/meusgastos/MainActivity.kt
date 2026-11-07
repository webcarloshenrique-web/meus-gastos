package br.com.meusgastos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen(val title: String, val emoji: String) {
    HOME("Meus Gastos", "🏠"),
    ADD("Adicionar gasto", "➕"),
    PENDING("Pendentes", "❓"),
    CALENDAR("Calendário", "📅"),
    REPORTS("Relatórios", "📊"),
    SETTINGS("Ajustes", "⚙️"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
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
        val start = runCatching { Screen.valueOf(intent.getStringExtra("screen") ?: "HOME") }.getOrDefault(Screen.HOME)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Verde,
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFD5EFE3),
                    onPrimaryContainer = Color(0xFF0B3D2C),
                    secondaryContainer = Color(0xFFD5EFE3),
                    onSecondaryContainer = Color(0xFF0B3D2C),
                    background = Fundo,
                    surface = Color.White,
                )
            ) {
                App(start)
            }
        }
    }
}

/** Estado da janela de atualização (compartilhado com a tela de Ajustes). */
object UpdateUi {
    var info by mutableStateOf<Updater.Info?>(null)
    var status by mutableStateOf("")
    var busy by mutableStateOf(false)
}

@Composable
fun App(start: Screen) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var screen by rememberSaveable { mutableStateOf(start) }
    var month by rememberSaveable { mutableIntStateOf(0) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val tick = Bus.tick.intValue
    val pendentes = remember(tick) { db.pendingCount() }
    val totalMes = remember(tick) { val r = Dates.range(0); db.sumBetween(r.first, r.second) }

    fun go(s: Screen) {
        screen = s
        scope.launch { drawer.close() }
    }
    val setMonth: (Int) -> Unit = { month = it.coerceIn(MIN_MONTH, 0) }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = !drawer.isOpen && screen != Screen.HOME) { screen = Screen.HOME }

    UpdateDialogHost()

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White, windowInsets = WindowInsets(0, 0, 0, 0)) {
                Column(
                    Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Verde, VerdeClaro)))
                        .statusBarsPadding().padding(20.dp)
                ) {
                    Text("💰", fontSize = 38.sp)
                    Text("Meus Gastos", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${Dates.full(0)}: ${money(totalMes)}",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                listOf(Screen.HOME, Screen.ADD, Screen.PENDING, Screen.CALENDAR, Screen.REPORTS).forEach { s ->
                    DrawerItem(s, screen == s, if (s == Screen.PENDING) pendentes else 0) { go(s) }
                }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                DrawerItem(Screen.SETTINGS, screen == Screen.SETTINGS, 0) { go(Screen.SETTINGS) }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopBar(
                    title = screen.title,
                    onMenu = { scope.launch { if (drawer.isOpen) drawer.close() else drawer.open() } },
                    onSettings = { screen = Screen.SETTINGS },
                )
            },
            floatingActionButton = {
                if (screen == Screen.HOME) {
                    ExtendedFloatingActionButton(
                        text = { Text("Adicionar", fontSize = 16.sp) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        onClick = { screen = Screen.ADD },
                        containerColor = Verde,
                        contentColor = Color.White,
                    )
                }
            },
            containerColor = Fundo,
        ) { pad ->
            when (screen) {
                Screen.HOME -> HomeScreen(db, pad, month, setMonth) { go(it) }
                Screen.ADD -> AddScreen(db, pad) { screen = Screen.HOME }
                Screen.PENDING -> PendingScreen(db, pad)
                Screen.CALENDAR -> CalendarScreen(db, pad, month, setMonth)
                Screen.REPORTS -> ReportsScreen(db, pad, month, setMonth)
                Screen.SETTINGS -> SettingsScreen(db, pad, month)
            }
        }
    }
}

@Composable
fun DrawerItem(s: Screen, selected: Boolean, badge: Int, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(if (s == Screen.HOME) "Início" else s.title, fontSize = 17.sp) },
        icon = { Text(s.emoji, fontSize = 22.sp) },
        selected = selected,
        onClick = onClick,
        badge = { if (badge > 0) Badge { Text("$badge") } },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Composable
fun TopBar(title: String, onMenu: () -> Unit, onSettings: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Verde, VerdeClaro)))
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = "Abrir menu", tint = Color.White)
            }
            Text(
                title, Modifier.weight(1f), color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, maxLines = 1,
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = Color.White)
            }
        }
    }
}

/** Procura atualização ao abrir o app e mostra a janela "Nova versão disponível". */
@Composable
fun UpdateDialogHost() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) { Updater.check() }
        if (r != null && r.version > BuildConfig.VERSION_CODE && r.version != Prefs.skipped(ctx)) UpdateUi.info = r
    }
    val info = UpdateUi.info ?: return

    AlertDialog(
        onDismissRequest = { if (!UpdateUi.busy) UpdateUi.info = null },
        title = { Text("🎉 Nova versão disponível") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A versão ${info.version} está pronta. Seus gastos continuam salvos.")
                if (info.notes.isNotBlank()) Text(info.notes.take(400), fontSize = 13.sp, color = TextoSuave)
                if (UpdateUi.status.isNotEmpty()) Text(UpdateUi.status, fontWeight = FontWeight.Medium)
                if (UpdateUi.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = !UpdateUi.busy,
                colors = ButtonDefaults.buttonColors(containerColor = Verde),
                onClick = {
                    if (!Updater.canInstall(ctx)) {
                        Updater.askInstallPermission(ctx)
                        UpdateUi.status = "Ligue \"Permitir desta fonte\" para o Meus Gastos, volte e toque em Atualizar de novo."
                    } else {
                        UpdateUi.busy = true
                        UpdateUi.status = "Baixando a nova versão…"
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { runCatching { Updater.download(ctx, info) }.getOrNull() }
                            UpdateUi.busy = false
                            if (f == null) {
                                UpdateUi.status = "Não consegui baixar. Confira a internet e tente de novo."
                            } else {
                                UpdateUi.status = ""
                                UpdateUi.info = null
                                Updater.install(ctx, f)
                            }
                        }
                    }
                },
            ) { Text("Atualizar agora") }
        },
        dismissButton = {
            TextButton(enabled = !UpdateUi.busy, onClick = {
                Prefs.skip(ctx, info.version)
                UpdateUi.info = null
            }) { Text("Depois") }
        },
    )
}
