package com.ownerrules

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/** Fica rodando: detecta tela desligada, desbloqueio e carregador. */
class OwnerService : Service() {
    companion object {
        const val CH = "ownerrules"
        const val CH_ALERT = "ownerrules_charge"
        fun start(ctx: Context) {
            try { ContextCompat.startForegroundService(ctx, Intent(ctx, OwnerService::class.java)) } catch (e: Exception) {}
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val s = Store(c)
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> if (s.faceSig != null) s.needsCheck = true
                Intent.ACTION_USER_PRESENT -> if (s.faceSig != null && s.needsCheck) openLauncher(c)
                Intent.ACTION_POWER_CONNECTED -> if (s.chargeOn) showCharging(c)
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() { checkVisitor(); handler.postDelayed(this, 20_000) }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "OwnerRules ativo", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Tela de carregamento", NotificationManager.IMPORTANCE_HIGH))
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_boom)
            .setContentTitle("OwnerRules protegendo seu celular")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        ServiceCompat.startForeground(
            this, 1, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        ContextCompat.registerReceiver(this, rx, f, ContextCompat.RECEIVER_EXPORTED)
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        try { unregisterReceiver(rx) } catch (e: Exception) {}
        super.onDestroy()
    }

    private fun openLauncher(c: Context) {
        try { c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }

    private fun showCharging(c: Context) {
        val i = Intent(c, ChargingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Settings.canDrawOverlays(c)) {
            try { c.startActivity(i); return } catch (e: Exception) {}
        }
        val pi = PendingIntent.getActivity(c, 2, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CH_ALERT)
            .setSmallIcon(R.drawable.ic_boom)
            .setContentTitle("⚡ Carregando")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000)
            .build()
        try { NotificationManagerCompat.from(c).notify(2, n) } catch (e: SecurityException) {}
    }

    private fun checkVisitor() {
        val s = Store(this)
        if (!s.ownerMode && s.visitorMinutes > 0 &&
            System.currentTimeMillis() - s.visitorSince > s.visitorMinutes * 60_000L
        ) {
            s.visitorSince = System.currentTimeMillis()
            if (s.faceSig != null) s.needsCheck = true
            Control.lockNow(this)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) {
            val s = Store(c)
            if (s.faceSig != null) s.needsCheck = true
            OwnerService.start(c)
        }
    }
}

class AdminReceiver : DeviceAdminReceiver()

/** Botão "Boom" nas configurações rápidas (puxar a barra de notificações). */
class BoomTile : TileService() {
    override fun onStartListening() { super.onStartListening(); update() }

    override fun onClick() {
        super.onClick()
        val s = Store(this)
        if (!s.ownerMode || s.needsCheck) { toast(this, "Só o dono pode usar o Boom"); return }
        toast(this, Control.boom(this, s))
        update()
    }

    private fun update() {
        val t = qsTile ?: return
        t.state = if (Store(this).boomActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Boom"
        t.updateTile()
    }
}
