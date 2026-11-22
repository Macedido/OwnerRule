package com.ownerrules

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

/** Todas as regras salvas. Qualquer mudança atualiza a tela automaticamente. */
class Store(ctx: Context) {
    companion object { val rev = mutableIntStateOf(0) }

    private val p: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("owner_rules", Context.MODE_PRIVATE)

    private fun r() = rev.intValue
    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val e = p.edit(); e.block(); e.apply(); rev.intValue++
    }
    private fun bool(k: String, d: Boolean): Boolean { r(); return p.getBoolean(k, d) }
    private fun int(k: String, d: Int): Int { r(); return p.getInt(k, d) }
    private fun str(k: String): String? { r(); return p.getString(k, null) }
    private fun strSet(k: String): Set<String> { r(); return p.getStringSet(k, emptySet())?.toSet() ?: emptySet() }

    var ownerMode: Boolean get() = bool("ownerMode", true)
        set(v) = edit { putBoolean("ownerMode", v) }
    var needsCheck: Boolean get() = bool("needsCheck", false)
        set(v) = edit { putBoolean("needsCheck", v) }
    var faceSig: String? get() = str("faceSig")
        set(v) = edit { putString("faceSig", v) }
    var pin: String get() = str("pin") ?: ""
        set(v) = edit { putString("pin", v) }
    var sensitivity: Float get() { r(); return p.getFloat("sens", 0.11f) }
        set(v) = edit { putFloat("sens", v) }
    var intruderOn: Boolean get() = bool("intruder", true)
        set(v) = edit { putBoolean("intruder", v) }

    var hiddenOwner: Set<String> get() = strSet("hiddenOwner")
        set(v) = edit { putStringSet("hiddenOwner", HashSet(v)) }
    var hiddenVisitor: Set<String> get() = strSet("hiddenVisitor")
        set(v) = edit { putStringSet("hiddenVisitor", HashSet(v)) }
    var boomList: Set<String> get() = strSet("boomList")
        set(v) = edit { putStringSet("boomList", HashSet(v)) }
    var boomActive: Boolean get() = bool("boomActive", false)
        set(v) = edit { putBoolean("boomActive", v) }

    var wallOwner: String? get() = str("wallOwner")
        set(v) = edit { putString("wallOwner", v) }
    var wallVisitor: String? get() = str("wallVisitor")
        set(v) = edit { putString("wallVisitor", v) }
    var colorOwner: Int get() = int("colorOwner", 0)
        set(v) = edit { putInt("colorOwner", v) }
    var colorVisitor: Int get() = int("colorVisitor", 6)
        set(v) = edit { putInt("colorVisitor", v) }
    var greeting: String get() = str("greeting") ?: "Bem-vindo de volta, chefe 👑"
        set(v) = edit { putString("greeting", v) }
    var columns: Int get() = int("columns", 4)
        set(v) = edit { putInt("columns", v) }
    var showLabels: Boolean get() = bool("labels", true)
        set(v) = edit { putBoolean("labels", v) }

    var chargeOn: Boolean get() = bool("chargeOn", true)
        set(v) = edit { putBoolean("chargeOn", v) }
    var chargeConnectVideo: String? get() = str("vConnect")
        set(v) = edit { putString("vConnect", v) }
    var chargeLoopVideo: String? get() = str("vLoop")
        set(v) = edit { putString("vLoop", v) }
    var chargeMuted: Boolean get() = bool("muted", true)
        set(v) = edit { putBoolean("muted", v) }

    var visitorMinutes: Int get() = int("visitorMin", 0)
        set(v) = edit { putInt("visitorMin", v) }
    var visitorSince: Long get() { r(); return p.getLong("visitorSince", 0L) }
        set(v) = edit { putLong("visitorSince", v) }
}

fun Set<String>.toggle(x: String): Set<String> = if (x in this) this - x else this + x

data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap)

object Apps {
    private val icons = HashMap<String, ImageBitmap>()
    @Volatile private var cache: List<AppInfo> = emptyList()
    fun cached() = cache

    fun load(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { r ->
                val pkg = r.activityInfo.packageName
                val icon = synchronized(icons) { icons[pkg] }
                    ?: r.loadIcon(pm).toBitmap(144, 144).asImageBitmap().also { synchronized(icons) { icons[pkg] = it } }
                AppInfo(pkg, r.loadLabel(pm).toString(), icon)
            }
            .sortedBy { it.label.lowercase() }
        cache = list
        return list
    }

    fun launch(ctx: Context, pkg: String) {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (e: Exception) { toast(ctx, "Esse app está pausado") }
    }

    fun openInfo(ctx: Context, pkg: String) {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Pausar / parar apps. Com Device Owner, pausa de verdade sem mexer nos dados do app. */
object Control {
    const val ADB = "adb shell dpm set-device-owner com.ownerrules/.AdminReceiver"

    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)
    private fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)

    fun isOwner(ctx: Context) = try { dpm(ctx).isDeviceOwnerApp(ctx.packageName) } catch (e: Exception) { false }

    fun isSuspended(ctx: Context, pkg: String): Boolean =
        if (!isOwner(ctx)) false else try { dpm(ctx).isPackageSuspended(admin(ctx), pkg) } catch (e: Exception) { false }

    fun suspend(ctx: Context, pkgs: Collection<String>, on: Boolean): Boolean {
        if (!isOwner(ctx)) return false
        return try {
            dpm(ctx).setPackagesSuspended(admin(ctx), pkgs.toTypedArray(), on)
            if (on) kill(ctx, pkgs)
            true
        } catch (e: Exception) { false }
    }

    fun kill(ctx: Context, pkgs: Collection<String>) {
        val am = ctx.getSystemService(ActivityManager::class.java)
        pkgs.forEach { try { am.killBackgroundProcesses(it) } catch (e: Exception) {} }
    }

    fun boom(ctx: Context, s: Store): String {
        if (!isOwner(ctx)) return "Ative o controle total na tela Apps primeiro"
        val list = s.boomList
        if (list.isEmpty()) return "Sua Lista Boom está vazia"
        val on = !s.boomActive
        val failed = try {
            dpm(ctx).setPackagesSuspended(admin(ctx), list.toTypedArray(), on)
        } catch (e: Exception) { return "Erro: ${e.message}" }
        if (on) kill(ctx, list)
        s.boomActive = on
        return if (on) "💥 ${list.size - failed.size} apps pausados" else "▶️ Apps liberados"
    }

    fun lockNow(ctx: Context) { try { dpm(ctx).lockNow() } catch (e: Exception) {} }

    @Suppress("DEPRECATION")
    fun removeOwner(ctx: Context) {
        try {
            val all = Apps.cached().map { it.pkg }
            if (all.isNotEmpty()) dpm(ctx).setPackagesSuspended(admin(ctx), all.toTypedArray(), false)
            dpm(ctx).clearDeviceOwnerApp(ctx.packageName)
        } catch (e: Exception) {}
    }
}

/** "Assinatura" do rosto a partir dos contornos do ML Kit. */
object FaceSig {
    private val types = intArrayOf(
        FaceContour.FACE, FaceContour.LEFT_EYE, FaceContour.RIGHT_EYE,
        FaceContour.NOSE_BRIDGE, FaceContour.NOSE_BOTTOM,
        FaceContour.UPPER_LIP_TOP, FaceContour.LOWER_LIP_BOTTOM,
        FaceContour.LEFT_EYEBROW_TOP, FaceContour.RIGHT_EYEBROW_TOP
    )

    fun from(face: Face): FloatArray? {
        if (abs(face.headEulerAngleY) > 14 || abs(face.headEulerAngleZ) > 14 || abs(face.headEulerAngleX) > 14) return null
        val le = face.getContour(FaceContour.LEFT_EYE)?.points ?: return null
        val re = face.getContour(FaceContour.RIGHT_EYE)?.points ?: return null
        if (le.isEmpty() || re.isEmpty()) return null
        val lx = le.map { it.x }.average().toFloat(); val ly = le.map { it.y }.average().toFloat()
        val rx = re.map { it.x }.average().toFloat(); val ry = re.map { it.y }.average().toFloat()
        val cx = (lx + rx) / 2f; val cy = (ly + ry) / 2f
        val d = hypot(rx - lx, ry - ly)
        if (d < 1f) return null
        val ang = -atan2(ry - ly, rx - lx)
        val c = cos(ang); val s = sin(ang)
        val out = ArrayList<Float>()
        for (t in types) {
            val pts = face.getContour(t)?.points ?: return null
            if (pts.isEmpty()) return null
            for (pt in pts) {
                val x = (pt.x - cx) / d; val y = (pt.y - cy) / d
                out.add(x * c - y * s); out.add(x * s + y * c)
            }
        }
        return out.toFloatArray()
    }

    fun distance(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 9f
        var sum = 0f
        for (i in a.indices) { val v = a[i] - b[i]; sum += v * v }
        return sqrt(sum / (a.size / 2))
    }

    fun average(list: List<FloatArray>): FloatArray? {
        if (list.isEmpty()) return null
        val n = list[0].size
        val ok = list.filter { it.size == n }
        val out = FloatArray(n)
        for (a in ok) for (i in 0 until n) out[i] += a[i] / ok.size
        return out
    }

    fun spread(list: List<FloatArray>, avg: FloatArray): Float =
        list.filter { it.size == avg.size }.maxOfOrNull { distance(it, avg) } ?: 0f

    fun encode(f: FloatArray) = f.joinToString(",")
    fun decode(s: String): FloatArray = try {
        if (s.isBlank()) FloatArray(0) else s.split(",").map { it.toFloat() }.toFloatArray()
    } catch (e: Exception) { FloatArray(0) }
}

object Intruders {
    fun dir(ctx: Context) = File(ctx.filesDir, "intruders").apply { mkdirs() }
    fun save(ctx: Context, pv: PreviewView?) {
        val bmp = pv?.bitmap ?: return
        try {
            FileOutputStream(File(dir(ctx), "${System.currentTimeMillis()}.jpg")).use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, it)
            }
        } catch (e: Exception) {}
    }
    fun list(ctx: Context): List<File> = dir(ctx).listFiles()?.sortedByDescending { it.name } ?: emptyList()
}
