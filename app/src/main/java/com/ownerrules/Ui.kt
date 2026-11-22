package com.ownerrules

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

val Palette = listOf(
    Color(0xFF8B5CF6), Color(0xFF22D3EE), Color(0xFFF472B6), Color(0xFF34D399),
    Color(0xFFFBBF24), Color(0xFFF87171), Color(0xFF60A5FA), Color(0xFFE5E7EB)
)
fun accent(i: Int) = Palette[i.coerceIn(0, Palette.lastIndex)]

@Composable
fun OwnerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette[0], secondary = Palette[1],
            background = Color.Black, surface = Color(0xFF16131F)
        ),
        content = content
    )
}

@Composable
fun Glass(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .padding(padding),
        content = content
    )
}

@Composable
fun Wallpaper(uri: String?, accent: Color) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), Color(0xFF0B0A12), Color.Black))))
    if (uri != null) {
        AsyncImage(model = Uri.parse(uri), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.65f)))))
    }
}

@Composable
fun Title(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
fun Chip(text: String, color: Color) {
    Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.28f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
fun BigButton(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 50.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.Black)) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ColorRow(selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Palette.forEachIndexed { i, c ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(c)
                    .then(if (i == selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                    .clickable { onPick(i) }
            )
        }
    }
}

@Composable
fun ScreenScaffold(title: String, color: Color, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Wallpaper(null, color)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                Text("←", fontSize = 28.sp, color = Color.White,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(horizontal = 10.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            content()
        }
    }
}

@Composable
fun rememberCameraPermission(): Boolean {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    return granted
}

/** Câmera frontal + detecção de rosto. */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun FaceCamera(modifier: Modifier = Modifier, onPreview: (PreviewView) -> Unit = {}, onResult: (FloatArray?, Boolean) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val callback = rememberUpdatedState(onResult)
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build()
        )
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val providerRef = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    DisposableEffect(Unit) {
        onDispose {
            try { providerRef[0]?.unbindAll() } catch (e: Exception) {}
            executor.shutdown()
            detector.close()
        }
    }
    AndroidView(modifier = modifier, factory = { c ->
        val pv = PreviewView(c).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        onPreview(pv)
        val future = ProcessCameraProvider.getInstance(c)
        future.addListener({
            try {
                val provider = future.get()
                providerRef[0] = provider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null) {
                        proxy.close()
                    } else {
                        try {
                            detector.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                                .addOnSuccessListener { faces ->
                                    val f = faces.maxByOrNull { it.boundingBox.width() }
                                    callback.value(f?.let { FaceSig.from(it) }, f != null)
                                }
                                .addOnCompleteListener { proxy.close() }
                        } catch (e: Exception) { proxy.close() }
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            } catch (e: Exception) {}
        }, ContextCompat.getMainExecutor(c))
        pv
    })
}

@Composable
fun ScanCircle(color: Color, content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "scan")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "rot")
    val glow by t.animateFloat(0.3f, 0.9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(240.dp).clip(CircleShape).background(Color.Black)) { content() }
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color.copy(alpha = 0.25f), style = Stroke(3.dp.toPx()))
            drawArc(color.copy(alpha = glow), rot, 90f, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            drawArc(color.copy(alpha = glow), rot + 180f, 50f, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Animação padrão de carregamento: bateria líquida com ondas. */
@Composable
fun LiquidBattery(level: Int, color: Color) {
    val t = rememberInfiniteTransition(label = "liquid")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "phase")
    val pulse by t.animateFloat(0.92f, 1.12f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
    Canvas(Modifier.size(280.dp)) {
        val r = size.minDimension / 2f * 0.85f
        drawCircle(color.copy(alpha = 0.10f), radius = r * pulse * 1.1f)
        drawCircle(color.copy(alpha = 0.18f), radius = r * pulse)
        drawCircle(color.copy(alpha = 0.6f), radius = r, style = Stroke(5.dp.toPx()))
        val inner = r - 12.dp.toPx()
        val clip = Path().apply { addOval(Rect(center, inner)) }
        clipPath(clip) {
            drawCircle(Color.White.copy(alpha = 0.05f), radius = inner)
            val top = center.y + inner - (2f * inner) * level.coerceIn(0, 100) / 100f
            val amp = 9.dp.toPx()
            val wave = Path().apply {
                var x = center.x - inner
                moveTo(x, size.height)
                lineTo(x, top)
                while (x <= center.x + inner + 6f) {
                    lineTo(x, top + sin(x / 38f + phase) * amp)
                    x += 6f
                }
                lineTo(center.x + inner, size.height)
                close()
            }
            drawPath(wave, Brush.verticalGradient(listOf(color, color.copy(alpha = 0.45f)), startY = top - amp, endY = center.y + inner))
        }
    }
}

@Composable
fun VideoBox(uri: Uri, loop: Boolean, muted: Boolean, onEnd: () -> Unit) {
    val end = rememberUpdatedState(onEnd)
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
        VideoView(c).apply {
            setVideoURI(uri)
            setOnPreparedListener { mp ->
                mp.isLooping = loop
                if (muted) mp.setVolume(0f, 0f)
                start()
            }
            setOnCompletionListener { end.value() }
            setOnErrorListener { _, _, _ -> end.value(); true }
        }
    })
}
