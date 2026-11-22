package com.ownerrules

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ---------------- Verificação de rosto ---------------- */

@Composable
fun FaceCheck(store: Store, onOwner: () -> Unit, onVisitor: () -> Unit, onPin: () -> Unit) {
    val ctx = LocalContext.current
    val cam = rememberCameraPermission()
    val target = remember { FaceSig.decode(store.faceSig ?: "") }
    val dists = remember { mutableStateListOf<Float>() }
    var status by remember { mutableStateOf("Olhe para a câmera") }
    var preview by remember { mutableStateOf<PreviewView?>(null) }
    var done by remember { mutableStateOf(false) }

    val fail: () -> Unit = {
        if (!done) {
            done = true
            if (store.intruderOn) Intruders.save(ctx, preview)
            onVisitor()
        }
    }
    LaunchedEffect(cam) { if (cam) { delay(8_000); fail() } }

    Box(Modifier.fillMaxSize()) {
        Wallpaper(null, Palette[0])
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text("OWNERRULES", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Text("Quem está aí?", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(28.dp))
            ScanCircle(Palette[0]) {
                if (cam) {
                    FaceCamera(Modifier.fillMaxSize(), onPreview = { preview = it }) { sig, seen ->
                        if (!done) {
                            status = when {
                                !seen -> "Nenhum rosto encontrado"
                                sig == null -> "Olhe reto para a câmera"
                                else -> "Verificando…"
                            }
                            if (sig != null && target.isNotEmpty()) {
                                dists.add(FaceSig.distance(sig, target))
                                if (dists.size >= 3) {
                                    val avg = dists.takeLast(3).average().toFloat()
                                    if (avg < store.sensitivity) { done = true; onOwner() }
                                    else if (dists.size >= 15) fail()
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("📷\nPermita a câmera", color = Color.White, textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(status, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (store.pin.isNotBlank()) BigButton("🔐 Usar PIN", Palette[0]) { done = true; onPin() }
                OutlinedButton(onClick = fail, modifier = Modifier.heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("👤 Visitante", color = Color.White)
                }
            }
        }
    }
}

/* ---------------- PIN ---------------- */

@Composable
fun PinScreen(store: Store, onOwner: () -> Unit, onVisitor: () -> Unit) {
    if (store.pin.isBlank()) {
        LaunchedEffect(Unit) { onOwner() }
        return
    }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var tries by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        Wallpaper(null, Palette[0])
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text("🔐", fontSize = 48.sp)
            Text("PIN do dono", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(18.dp)) {
                repeat(input.length) { Box(Modifier.size(14.dp).clip(CircleShape).background(Palette[0])) }
            }
            Text(error, color = Palette[5], modifier = Modifier.padding(8.dp))
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK").chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.size(74.dp).clip(CircleShape).background(Color.White.copy(alpha = if (k == "OK") 0.25f else 0.1f))
                                .clickable {
                                    when (k) {
                                        "⌫" -> input = input.dropLast(1)
                                        "OK" -> if (input == store.pin) onOwner() else {
                                            tries++; input = ""
                                            error = "PIN errado (${3 - tries} tentativas)"
                                            if (tries >= 3) onVisitor()
                                        }
                                        else -> if (input.length < 8) input += k
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) { Text(k, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            TextButton(onClick = onVisitor) { Text("Entrar como visitante", color = Color.White.copy(alpha = 0.6f)) }
        }
    }
}

/* ---------------- Cadastro do rosto ---------------- */

@Composable
fun EnrollScreen(store: Store, onDone: () -> Unit) {
    val cam = rememberCameraPermission()
    val need = 20
    val samples = remember { mutableStateListOf<FloatArray>() }
    var saved by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf("Olhe reto, com o rosto bem iluminado") }

    ScreenScaffold("Cadastrar rosto", Palette[0], onDone) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            ScanCircle(if (saved) Palette[3] else Palette[0]) {
                if (cam) {
                    FaceCamera(Modifier.fillMaxSize()) { sig, seen ->
                        if (sig == null) {
                            if (!saved) status = if (seen) "Olhe reto para a câmera" else "Nenhum rosto encontrado"
                        } else if (!saved) {
                            samples.add(sig)
                            status = "Capturando… ${samples.size}/$need  (mexa um pouco a cabeça)"
                            if (samples.size >= need) {
                                val avg = FaceSig.average(samples.toList())
                                if (avg != null) {
                                    store.faceSig = FaceSig.encode(avg)
                                    store.sensitivity = (FaceSig.spread(samples.toList(), avg) * 2.5f).coerceIn(0.07f, 0.18f)
                                    store.needsCheck = false
                                    saved = true
                                    status = "Rosto cadastrado! ✅"
                                }
                            }
                        } else {
                            live = FaceSig.distance(sig, FaceSig.decode(store.faceSig ?: ""))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { (samples.size / need.toFloat()).coerceAtMost(1f) },
                modifier = Modifier.fillMaxWidth(0.7f).clip(RoundedCornerShape(50))
            )
            Spacer(Modifier.height(10.dp))
            Text(status, color = Color.White, textAlign = TextAlign.Center)
            if (saved) {
                Spacer(Modifier.height(16.dp))
                Glass(Modifier.fillMaxWidth()) {
                    val d = live
                    Text("Teste ao vivo", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        if (d == null) "Olhe para a câmera…"
                        else "Diferença: ${"%.3f".format(d)}  •  limite: ${"%.3f".format(store.sensitivity)}",
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    if (d != null) Text(
                        if (d < store.sensitivity) "✅ Seria reconhecido como dono" else "❌ Não seria reconhecido",
                        color = if (d < store.sensitivity) Palette[3] else Palette[5], fontWeight = FontWeight.Bold
                    )
                    Text("Peça pra outra pessoa olhar: ela deve dar ❌. Ajuste o rigor em Regras se precisar.",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                BigButton("Concluir", Palette[3], Modifier.fillMaxWidth()) { onDone() }
                TextButton(onClick = { samples.clear(); saved = false; live = null }) { Text("Refazer") }
            }
        }
    }
}

/* ---------------- Regras (configurações) ---------------- */

@Composable
fun RulesScreen(store: Store, version: Int, onEnroll: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var target by remember { mutableStateOf("") }
    var pinText by remember { mutableStateOf(store.pin) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            val s = uri.toString()
            when (target) {
                "wallOwner" -> store.wallOwner = s
                "wallVisitor" -> store.wallVisitor = s
                "vConnect" -> store.chargeConnectVideo = s
                "vLoop" -> store.chargeLoopVideo = s
            }
        }
    }
    val pick: (String, String) -> Unit = { t, mime -> target = t; picker.launch(arrayOf(mime)) }

    val isHome = remember(version) {
        ctx.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName == ctx.packageName
    }
    val overlay = remember(version) { Settings.canDrawOverlays(ctx) }
    val isDO = remember(version) { Control.isOwner(ctx) }

    ScreenScaffold("Regras", accent(store.colorOwner), onBack) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

            Title("PERMISSÕES")
            Glass(Modifier.fillMaxWidth()) {
                PermRow("🏠 Tela inicial padrão", isHome) {
                    ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                }
                PermRow("🪟 Mostrar sobre outros apps", overlay) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                }
                PermRow("🛡️ Controle total (pausar apps)", isDO) { toast(ctx, "Veja as instruções na tela Apps") }
            }

            Title("ROSTO DO DONO")
            Glass(Modifier.fillMaxWidth()) {
                Text(
                    if (store.faceSig != null) "✅ Rosto cadastrado" else "⚠️ Nenhum rosto cadastrado — qualquer um entra como dono",
                    color = Color.White
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton(if (store.faceSig != null) "Recadastrar" else "Cadastrar rosto", Palette[0], onClick = onEnroll)
                    if (store.faceSig != null) OutlinedButton(onClick = { store.faceSig = null; store.needsCheck = false },
                        modifier = Modifier.heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp)) { Text("Remover", color = Color.White) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Rigor: ${"%.2f".format(store.sensitivity)}  (menor = mais rigoroso)", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                Slider(value = store.sensitivity, onValueChange = { store.sensitivity = it }, valueRange = 0.04f..0.25f)
                OutlinedTextField(
                    value = pinText,
                    onValueChange = { if (it.all(Char::isDigit) && it.length <= 8) pinText = it },
                    label = { Text("PIN de reserva (4 a 8 números)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                BigButton("Salvar PIN", Palette[1]) {
                    if (pinText.length >= 4) { store.pin = pinText; toast(ctx, "PIN salvo 🔐") }
                    else toast(ctx, "O PIN precisa de pelo menos 4 números")
                }
                SwitchRow("📸 Foto do intruso", store.intruderOn) { store.intruderOn = it }
            }

            Title("MODO DONO 👑")
            Glass(Modifier.fillMaxWidth()) {
                WallRow(store.wallOwner != null, { pick("wallOwner", "image/*") }) { store.wallOwner = null }
                Spacer(Modifier.height(10.dp))
                Text("Cor", color = Color.White.copy(alpha = 0.8f)); Spacer(Modifier.height(6.dp))
                ColorRow(store.colorOwner) { store.colorOwner = it }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = store.greeting, onValueChange = { store.greeting = it },
                    label = { Text("Mensagem de boas-vindas") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { store.hiddenOwner = emptySet() }) { Text("Mostrar de novo os apps escondidos") }
            }

            Title("MODO VISITANTE 👤")
            Glass(Modifier.fillMaxWidth()) {
                WallRow(store.wallVisitor != null, { pick("wallVisitor", "image/*") }) { store.wallVisitor = null }
                Spacer(Modifier.height(10.dp))
                Text("Cor", color = Color.White.copy(alpha = 0.8f)); Spacer(Modifier.height(6.dp))
                ColorRow(store.colorVisitor) { store.colorVisitor = it }
                Spacer(Modifier.height(10.dp))
                val m = store.visitorMinutes
                Text(if (m == 0) "⏱️ Tempo do visitante: sem limite" else "⏱️ Bloquear o celular após $m min", color = Color.White)
                Slider(value = m.toFloat(), onValueChange = { store.visitorMinutes = it.toInt() }, valueRange = 0f..30f, steps = 29)
                Text("Segure um app na tela inicial pra escondê-lo do visitante.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                TextButton(onClick = { store.hiddenVisitor = emptySet() }) { Text("Mostrar todos os apps pro visitante") }
            }

            Title("TELA INICIAL")
            Glass(Modifier.fillMaxWidth()) {
                Text("Colunas: ${store.columns}", color = Color.White)
                Slider(value = store.columns.toFloat(), onValueChange = { store.columns = it.toInt() }, valueRange = 3f..6f, steps = 2)
                SwitchRow("Mostrar nome dos apps", store.showLabels) { store.showLabels = it }
            }

            Title("CARREGAMENTO ⚡")
            Glass(Modifier.fillMaxWidth()) {
                SwitchRow("Tela ao conectar o carregador", store.chargeOn) { store.chargeOn = it }
                VideoRow("🎬 Vídeo ao conectar", store.chargeConnectVideo != null, { pick("vConnect", "video/*") }) { store.chargeConnectVideo = null }
                VideoRow("🔁 Vídeo enquanto carrega", store.chargeLoopVideo != null, { pick("vLoop", "video/*") }) { store.chargeLoopVideo = null }
                Text("Sem vídeo, aparece a animação de bateria líquida.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                SwitchRow("Vídeos sem som", store.chargeMuted) { store.chargeMuted = it }
                Spacer(Modifier.height(6.dp))
                BigButton("Testar agora", Palette[4], Modifier.fillMaxWidth()) {
                    ctx.startActivity(Intent(ctx, ChargingActivity::class.java).putExtra("test", true))
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun PermRow(text: String, ok: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Color.White, modifier = Modifier.weight(1f))
        Text(if (ok) "✅" else "Ativar ›", color = if (ok) Palette[3] else Palette[4], fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WallRow(has: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🖼️ Plano de fundo", color = Color.White, modifier = Modifier.weight(1f))
        TextButton(onClick = onPick) { Text(if (has) "Trocar" else "Escolher") }
        if (has) TextButton(onClick = onRemove) { Text("Remover") }
    }
}

@Composable
fun VideoRow(label: String, has: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        TextButton(onClick = onPick) { Text(if (has) "Trocar" else "Escolher") }
        if (has) TextButton(onClick = onRemove) { Text("✕") }
    }
}

/* ---------------- Controle de apps ---------------- */

@Composable
fun ControlScreen(store: Store, version: Int, bump: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var local by remember { mutableIntStateOf(0) }
    val isDO = remember(version, local) { Control.isOwner(ctx) }
    val apps by produceState(Apps.cached(), version) { value = withContext(Dispatchers.IO) { Apps.load(ctx) } }
    val paused = remember(version, local, apps, store.boomActive) {
        if (isDO) apps.filter { Control.isSuspended(ctx, it.pkg) }.map { it.pkg }.toSet() else emptySet()
    }
    var confirmRemove by remember { mutableStateOf(false) }
    val boomCount = store.boomList.size

    ScreenScaffold("Apps", Palette[1], onBack) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Glass(Modifier.fillMaxWidth()) {
                    if (isDO) {
                        Text("🛡️ Controle total ativo", color = Palette[3], fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Apps pausados congelam na hora: não rodam, não notificam e não sabem que foram parados. Nada é apagado.",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    } else {
                        Text("🔓 Modo básico", color = Palette[4], fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Agora o botão Parar abre a tela do sistema pra forçar parada. Pra pausar com 1 clique, ative pelo PC uma vez só:",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("1. Ative a Depuração USB (Opções do desenvolvedor)\n2. Remova as contas (Google etc.) temporariamente\n3. Rode no PC o comando abaixo (toque pra copiar)\n4. Depois adicione as contas de volta",
                            color = Color.White, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(Control.ADB, color = Palette[1], fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.6f))
                                .clickable { clipboard.setText(AnnotatedString(Control.ADB)); toast(ctx, "Comando copiado") }
                                .padding(12.dp))
                    }
                }
            }
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Text("💥 Botão Boom", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("Pausa todos os apps da Lista Boom de uma vez. Toque de novo pra liberar.",
                        color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    BigButton(
                        if (store.boomActive) "▶️ Liberar $boomCount apps" else "💥 BOOM! Pausar $boomCount apps",
                        if (store.boomActive) Palette[3] else Palette[5], Modifier.fillMaxWidth()
                    ) { toast(ctx, Control.boom(ctx, store)); local++; bump() }
                    Text("Dica: adicione o bloco \"Boom\" nas Configurações rápidas (puxe a barra de cima e toque no lápis).",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
            item { Title("TODOS OS APPS  •  ☑ = Lista Boom") }
            items(apps, key = { it.pkg }) { app ->
                val isPaused = app.pkg in paused
                Glass(Modifier.fillMaxWidth(), padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (isPaused) Text("⏸ Pausado", color = Palette[4], fontSize = 12.sp)
                        }
                        Checkbox(checked = app.pkg in store.boomList,
                            onCheckedChange = { store.boomList = store.boomList.toggle(app.pkg) })
                        if (isDO) {
                            TextButton(onClick = { Control.suspend(ctx, listOf(app.pkg), !isPaused); local++ }) {
                                Text(if (isPaused) "Retomar" else "Pausar")
                            }
                        } else {
                            TextButton(onClick = { Control.kill(ctx, listOf(app.pkg)); Apps.openInfo(ctx, app.pkg) }) { Text("Parar") }
                        }
                    }
                }
            }
            if (isDO) item {
                TextButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Desativar controle total (Device Owner)", color = Palette[5])
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Desativar controle total?") },
            text = { Text("Todos os apps pausados serão liberados e o Boom para de funcionar até você ativar de novo pelo PC.") },
            confirmButton = {
                TextButton(onClick = { Control.removeOwner(ctx); store.boomActive = false; confirmRemove = false; local++; bump() }) { Text("Desativar") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") } }
        )
    }
}

/* ---------------- Intrusos ---------------- */

@Composable
fun IntrudersScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var files by remember { mutableStateOf(Intruders.list(ctx)) }
    val fmt = remember { SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR")) }
    ScreenScaffold("Intrusos 🕵️", Palette[5], onBack) {
        if (files.isEmpty()) {
            Glass(Modifier.fillMaxWidth()) {
                Text("Ninguém tentou mexer no seu celular. 😎", color = Color.White)
            }
        } else {
            BigButton("Apagar tudo (${files.size})", Palette[5], Modifier.fillMaxWidth()) {
                files.forEach { it.delete() }; files = emptyList()
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(files.size) { i ->
                    val f = files[i]
                    val ts = f.nameWithoutExtension.toLongOrNull()
                    Box(Modifier.aspectRatio(0.75f).clip(RoundedCornerShape(18.dp))) {
                        AsyncImage(model = f, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Text(if (ts != null) fmt.format(Date(ts)) else "", color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.6f)).padding(8.dp))
                    }
                }
            }
        }
    }
}
