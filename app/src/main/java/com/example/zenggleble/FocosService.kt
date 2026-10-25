package com.example.zenggeble

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Calendar

class FocosService : Service() {

    private lateinit var bleManager: ZenggeBleManager
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    class Foco(val nombre: String, val mac: String, val hastaAmanecer: Boolean) {
        var aplicado = -1            // último estado confirmado (-1 = desconocido)
        var ultimaConfirmacion = 0L  // elapsedRealtime de la última confirmación
        var proximoIntento = 0L      // no reintentar antes de este momento
        var fallosSeguidos = 0
        var enProceso = false        // hay un comando en cola/en curso para este foco
    }

    private val focos = listOf(
        Foco("Afuera1", "08:65:f0:c5:71:2c", hastaAmanecer = false), // atardecer -> 22:00
        Foco("Afuera2", "08:65:f0:c4:d1:8a", hastaAmanecer = true)   // atardecer -> amanecer
    )

    private val runnable = object : Runnable {
        override fun run() {
            try {
                verificarHorarios()
            } catch (e: Exception) {
                Log.e(TAG, "Error en verificarHorarios", e)
            }
            handler.postDelayed(this, 15000) // Revisa cada 15 seg
        }
    }

    override fun onCreate() {
        super.onCreate()
        bleManager = ZenggeBleManager(this)
        iniciarNotificacionForeground()

        // Mantiene la CPU despierta con la pantalla apagada; si no, los timers se congelan.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ControlZengge:focos").apply {
            setReferenceCounted(false)
            acquire()
        }

        handler.post(runnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun verificarHorarios() {
        // Zona fija UTC-6: no depende de la zona horaria del celular
        val cal = Calendar.getInstance(SolarCalculator.ZONA)
        val diaDelAnio = cal.get(Calendar.DAY_OF_YEAR)
        val minAhora = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val ocaso = SolarCalculator.minutosSol(diaDelAnio, amanecer = false)
        val alba = SolarCalculator.minutosSol(diaDelAnio, amanecer = true)
        if (ocaso < 0 || alba < 0) return

        val offsetAtardecer = -15
        val offsetAmanecer = 0
        val horaApagado1 = 22 * 60 // 22:00

        val anochecido = minAhora >= (ocaso + offsetAtardecer)
        val ahora = SystemClock.elapsedRealtime()

        focos.forEach { f ->
            val deseado = if (f.hastaAmanecer) {
                anochecido || (minAhora < alba + offsetAmanecer)
            } else {
                anochecido && (minAhora < horaApagado1)
            }
            val estadoDeseadoInt = if (deseado) 1 else 0

            val cambio = f.aplicado != estadoDeseadoInt
            // Reafirma el estado cada tanto: si el foco se quedó sin luz y volvió
            // encendido, el celular lo corrige solo.
            val reafirmar = !cambio && (ahora - f.ultimaConfirmacion) >= REAFIRMAR_MS

            if ((cambio || reafirmar) && !f.enProceso && ahora >= f.proximoIntento) {
                f.enProceso = true
                Log.d(TAG, "${f.nombre}: pedir ${if (deseado) "ON" else "OFF"} (min=$minAhora, ocaso=$ocaso, alba=$alba)")
                bleManager.enviarComando(f.mac, deseado) { exito ->
                    f.enProceso = false
                    if (exito) {
                        f.aplicado = estadoDeseadoInt
                        f.ultimaConfirmacion = SystemClock.elapsedRealtime()
                        f.fallosSeguidos = 0
                    } else {
                        f.fallosSeguidos++
                        // Espera creciente: 20s, 40s, 80s, 160s, hasta 5 min
                        val espera = minOf(20_000L * (1L shl minOf(f.fallosSeguidos - 1, 4)), 300_000L)
                        f.proximoIntento = SystemClock.elapsedRealtime() + espera
                    }
                }
            }
        }
    }

    private fun iniciarNotificacionForeground() {
        val channelId = "focos_service_ch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Servicio Focos", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Control Focos Zengge")
            .setContentText("Supervisando amanecer/atardecer por BLE...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(101, notif)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FocosService"
        private const val REAFIRMAR_MS = 30 * 60 * 1000L
    }
}
