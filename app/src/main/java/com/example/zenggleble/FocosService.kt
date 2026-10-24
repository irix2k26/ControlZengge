package com.example.zenggeble

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.*

class FocosService : Service() {

    private lateinit var bleManager: ZenggeBleManager
    private val handler = Handler(Looper.getMainLooper())

    data class Foco(val nombre: String, val mac: String, val hastaAmanecer: Boolean, var aplicado: Int = -1)

    private val focos = listOf(
        Foco("Afuera1", "08:65:f0:c5:71:2c", hastaAmanecer = false), // atardecer -> 22:00
        Foco("Afuera2", "08:65:f0:c4:d1:8a", hastaAmanecer = true)   // atardecer -> amanecer
    )

    private val runnable = object : Runnable {
        override fun run() {
            verificarHorarios()
            handler.postDelayed(this, 15000) // Revisa cada 15 seg
        }
    }

    override fun onCreate() {
        super.onCreate()
        bleManager = ZenggeBleManager(this)
        iniciarNotificacionForeground()
        handler.post(runnable)
    }

    private fun verificarHorarios() {
        val cal = Calendar.getInstance()
        val diaDelAnio = cal.get(Calendar.DAY_OF_YEAR)
        val minAhora = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val ocaso = SolarCalculator.minutosSol(diaDelAnio, amanecer = false)
        val alba = SolarCalculator.minutosSol(diaDelAnio, amanecer = true)
        if (ocaso < 0 || alba < 0) return

        val offsetAtardecer = -15
        val offsetAmanecer = 0
        val horaApagado1 = 22 * 60 // 22:00

        val anochecido = minAhora >= (ocaso + offsetAtardecer)

        focos.forEach { f ->
            val deseado = if (f.hastaAmanecer) {
                anochecido || (minAhora < alba + offsetAmanecer)
            } else {
                anochecido && (minAhora < horaApagado1)
            }

            val estadoDeseadoInt = if (deseado) 1 else 0
            if (f.aplicado != estadoDeseadoInt) {
                bleManager.enviarComando(f.mac, deseado) { exito ->
                    if (exito) f.aplicado = estadoDeseadoInt
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
}