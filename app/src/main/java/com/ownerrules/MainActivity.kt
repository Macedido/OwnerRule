package com.ownerrules

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { Home, Check, Pin, Rules, Control, Intruders, Enroll }

class MainActivity : ComponentActivity() {
    lateinit var store: Store
    val screen = mutableStateOf(Screen.Home)
    val version = mutableIntStateOf(0)
    fun bump() { version.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = Store(this)
        OwnerService.start(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { OwnerTheme { AppRoot(this) } }
    }

    override fun onResume() {
        super.onResume()
        if (store.faceSig != null && store.needsCheck) screen.value = Screen.Check
        bump()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (screen.value != Screen.Check && screen.value != Screen.Pin) screen.value = Screen.Home
    }
}

@Composable
fun AppRoot(act: MainActivity) {
    val store = act.store
    var screen by act.screen
    val version = act.version.intValue
    val owner = store.ownerMode

    val becomeOwner: () -> Unit = {
        store.ownerMode = true; store.needsCheck = false; act.bump(); screen = Screen.Home
    }
    val becomeVisitor: () -> Unit = {
        store.ownerMode = false; store.needsCheck = false
        store.visitorSince = System.currentTimeMillis(); act.bump(); screen = Screen.Home
    }

    BackHandler(enabled = true) {
        if (screen != Screen.Check && screen != Screen.Pin) screen = Screen.Home
    }

    // Visitante nunca entra nas telas do dono
    val protectedScreens = listOf(Screen.Rules, Screen.Control, Screen.Intruders, Screen.Enroll)
    val s = if (!owner && screen in protectedScreens) Screen.Home else screen

    when (s) {
        Screen.Home -> Home(store, owner, version, act::bump) { screen = it }
        Screen.Check ->
            if (store.faceSig == null) PinScreen(store, becomeOwner, becomeVisitor)
            else FaceCheck(store, becomeOwner, becomeVisitor) { screen = Screen.Pin }
        Screen.Pin -> PinScreen(store, becomeOwner, becomeVisitor)
        Screen.Rules -> RulesScreen(store, version, { screen = Screen.Enroll }) { screen = Screen.Home }
        Screen.Control -> ControlScreen(store, version, act::bump) { screen = Screen.Home }
        Screen.Intruders -> IntrudersScreen { screen = Screen.Home }
        Screen.Enroll -> EnrollScreen(store) { screen = Screen.Rules }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Home(store: Store, owner: Boolean, version: Int, bump: () -> Unit, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val color = accent(if (owner) store.colorOwner else store.colorVisitor)
    val apps by produceState(Apps.cached(), version) { value = withContext(Dispatchers.IO) { Apps.load(ctx) } }
    var query by remember { mutableStateOf("") }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(5_000) } }

    val bm = remember { ctx.getSystemService(BatteryManager::class.java) }
    val battery = remember(now) { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }
    val charging = remember(now) { bm.isCharging }
    val isDO = remember(version) { Control.isOwner(ctx) }
    val hidden = if (owner) store.hiddenOwner else store.hiddenVisitor
    val paused = remember(version, apps, store.boomActive) {
        if (owner && isDO) apps.filter { Control.isSuspended(ctx, it.pkg) }.map { it.pkg }.toSet() else emptySet()
    }
    val visible = apps.filter { it.pkg !in hidden && (query.isBlank() || it.label.contains(query, ignoreCase = true)) }

    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(Date(now))
        .replaceFirstChar { it.uppercase() }

    Box(Modifier.fillMaxSize()) {
        Wallpaper(if (owner) store.wallOwner else store.wallVisitor, color)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(20.dp))
            Column(Modifier.pointerInput(owner) {
                detectTapGestures(onLongPress = { if (!owner) go(Screen.Check) })
            }) {
                Text(time, fontSize = 76.sp, fontWeight = FontWeight.Thin, color = Color.White)
                Text(date, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(if (owner) "👑 Modo Dono" else "👤 Visitante", color)
                Chip("${if (charging) "⚡" else "🔋"} $battery%", Color.White)
                if (owner && store.boomActive) Chip("💥 Boom ativo", Palette[5])
            }
            if (owner) {
                Spacer(Modifier.height(8.dp))
                Text(store.greeting, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (store.faceSig == null) {
                    Spacer(Modifier.height(10.dp))
                    Glass(Modifier.fillMaxWidth().clickable { go(Screen.Rules) }, padding = 12.dp) {
                        Text("⚠️ Cadastre seu rosto para ativar a proteção", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            TextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("🔍  Buscar apps") }, singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.12f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(store.columns.coerceIn(3, 6)),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visible, key = { it.pkg }) { app ->
                    AppCell(app, store.showLabels, app.pkg in paused,
                        onClick = { Apps.launch(ctx, app.pkg) },
                        onLong = { if (owner) menuApp = app })
                }
            }
            if (owner) {
                Glass(Modifier.fillMaxWidth().padding(bottom = 10.dp), padding = 10.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DockBtn("⚙️", "Regras") { go(Screen.Rules) }
                        DockBtn("⏸️", "Apps") { go(Screen.Control) }
                        DockBtn("💥", "Boom") { toast(ctx, Control.boom(ctx, store)); bump() }
                        DockBtn("🕵️", "Intrusos") { go(Screen.Intruders) }
                        DockBtn("👤", "Visitante") {
                            store.ownerMode = false; store.visitorSince = System.currentTimeMillis(); bump()
                        }
                    }
                }
            } else {
                Text("Segure o relógio para entrar como dono", color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
            }
        }
    }

    menuApp?.let { app ->
        AppMenu(app, store, isDO, app.pkg in paused, onDone = { menuApp = null; bump() })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(app: AppInfo, label: Boolean, paused: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(4.dp)
            .alpha(if (paused) 0.35f else 1f)
    ) {
        Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(54.dp))
        if (label) {
            Spacer(Modifier.height(4.dp))
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Color.White)
        }
    }
}

@Composable
fun DockBtn(icon: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(6.dp)) {
        Text(icon, fontSize = 22.sp)
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
fun AppMenu(app: AppInfo, store: Store, isDO: Boolean, paused: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val Item: @Composable (String, () -> Unit) -> Unit = { text, action -> MenuItem(text) { action(); onDone() } }
    AlertDialog(
        onDismissRequest = onDone,
        confirmButton = { TextButton(onClick = onDone) { Text("Fechar") } },
        icon = { Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(48.dp)) },
        title = { Text(app.label) },
        text = {
            Column {
                Item(if (app.pkg in store.hiddenVisitor) "👁️ Mostrar no modo visitante" else "🙈 Esconder do visitante (app secreto)") {
                    store.hiddenVisitor = store.hiddenVisitor.toggle(app.pkg)
                }
                Item("🫥 Esconder do modo dono") { store.hiddenOwner = store.hiddenOwner + app.pkg }
                if (isDO) {
                    Item(if (paused) "▶️ Retomar app" else "⏸️ Pausar app") { Control.suspend(ctx, listOf(app.pkg), !paused) }
                } else {
                    Item("⛔ Forçar parada") { Control.kill(ctx, listOf(app.pkg)); Apps.openInfo(ctx, app.pkg) }
                }
                Item(if (app.pkg in store.boomList) "💥 Tirar da Lista Boom" else "💥 Pôr na Lista Boom") {
                    store.boomList = store.boomList.toggle(app.pkg)
                }
                Item("ℹ️ Informações do app") { Apps.openInfo(ctx, app.pkg) }
            }
        }
    )
}

/** Tela que aparece ao conectar o carregador. */
class ChargingActivity : ComponentActivity() {
    private val level = mutableIntStateOf(0)
    private var test = false
    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_POWER_DISCONNECTED) { if (!test) finish(); return }
            val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (l >= 0 && sc > 0) level.intValue = l * 100 / sc
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        test = intent.getBooleanExtra("test", false)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(this, rx, f, ContextCompat.RECEIVER_EXPORTED)
        val s = Store(this)
        setContent {
            OwnerTheme {
                ChargingScreen(level.intValue, s.chargeConnectVideo, s.chargeLoopVideo, s.chargeMuted, accent(s.colorOwner)) { finish() }
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(rx) } catch (e: Exception) {}
        super.onDestroy()
    }
}

@Composable
fun ChargingScreen(level: Int, connectVideo: String?, loopVideo: String?, muted: Boolean, color: Color, onClose: () -> Unit) {
    var phase by remember { mutableIntStateOf(if (connectVideo != null) 0 else 1) }
    val usingVideo = (phase == 0 && connectVideo != null) || (phase == 1 && loopVideo != null)
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        when {
            phase == 0 && connectVideo != null ->
                key("connect") { VideoBox(Uri.parse(connectVideo), loop = false, muted = muted) { phase = 1 } }
            phase == 1 && loopVideo != null ->
                key("loop") { VideoBox(Uri.parse(loopVideo), loop = true, muted = muted) { phase = 2 } }
            else -> LiquidBattery(level, color)
        }
        Column(
            Modifier.align(if (usingVideo) Alignment.BottomCenter else Alignment.Center)
                .padding(bottom = if (usingVideo) 90.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$level%", fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("⚡ Carregando", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
        }
        Text("Toque para fechar", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp))
    }
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit) {
    Text(text, color = Color.White, fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp))
}
