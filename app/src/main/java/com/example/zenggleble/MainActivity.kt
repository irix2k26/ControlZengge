package com.example.zenggeble

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var ble: ZenggeBleManager

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) {
            iniciarServicio()
        } else {
            Toast.makeText(this, "Se requieren permisos Bluetooth para operar", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ble = ZenggeBleManager(this)

        pedirPermisos()

        // Botones manuales para pruebas directas
        findViewById<Button>(R.id.btnOn1)?.setOnClickListener {
            ble.enviarComando("08:65:f0:c5:71:2c", true) { Toast.makeText(this, "Foco1: $it", Toast.LENGTH_SHORT).show() }
        }
        findViewById<Button>(R.id.btnOff1)?.setOnClickListener {
            ble.enviarComando("08:65:f0:c5:71:2c", false) { Toast.makeText(this, "Foco1: $it", Toast.LENGTH_SHORT).show() }
        }
        findViewById<Button>(R.id.btnOn2)?.setOnClickListener {
            ble.enviarComando("08:65:f0:c4:d1:8a", true) { Toast.makeText(this, "Foco2: $it", Toast.LENGTH_SHORT).show() }
        }
        findViewById<Button>(R.id.btnOff2)?.setOnClickListener {
            ble.enviarComando("08:65:f0:c4:d1:8a", false) { Toast.makeText(this, "Foco2: $it", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun pedirPermisos() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        requestPerms.launch(perms.toTypedArray())
    }

    private fun iniciarServicio() {
        val it = Intent(this, FocosService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(it)
        } else {
            startService(it)
        }
    }
}