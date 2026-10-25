package com.example.zenggeble

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
        ble = ZenggeBleManager(this)

        // Interfaz creada directo en código (no requiere archivos XML)
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(50, 50, 50, 50)
        }

        val titulo = TextView(this).apply {
            text = "Control Focos Zengge BLE"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        rootLayout.addView(titulo)

        // Controles Foco 1
        val txtFoco1 = TextView(this).apply { text = "Foco 1 (Afuera 1)"; textSize = 16f }
        rootLayout.addView(txtFoco1)

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 30)
        }
        val btnOn1 = Button(this).apply {
            text = "Encender"
            setOnClickListener {
                ble.enviarComando("08:65:f0:c5:71:2c", true) { exito ->
                    Toast.makeText(this@MainActivity, "Foco 1 ON: $exito", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val btnOff1 = Button(this).apply {
            text = "Apagar"
            setOnClickListener {
                ble.enviarComando("08:65:f0:c5:71:2c", false) { exito ->
                    Toast.makeText(this@MainActivity, "Foco 1 OFF: $exito", Toast.LENGTH_SHORT).show()
                }
            }
        }
        row1.addView(btnOn1)
        row1.addView(btnOff1)
        rootLayout.addView(row1)

        // Controles Foco 2
        val txtFoco2 = TextView(this).apply { text = "Foco 2 (Afuera 2)"; textSize = 16f }
        rootLayout.addView(txtFoco2)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 30)
        }
        val btnOn2 = Button(this).apply {
            text = "Encender"
            setOnClickListener {
                ble.enviarComando("08:65:f0:c4:d1:8a", true) { exito ->
                    Toast.makeText(this@MainActivity, "Foco 2 ON: $exito", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val btnOff2 = Button(this).apply {
            text = "Apagar"
            setOnClickListener {
                ble.enviarComando("08:65:f0:c4:d1:8a", false) { exito ->
                    Toast.makeText(this@MainActivity, "Foco 2 OFF: $exito", Toast.LENGTH_SHORT).show()
                }
            }
        }
        row2.addView(btnOn2)
        row2.addView(btnOff2)
        rootLayout.addView(row2)

        setContentView(rootLayout)

        pedirPermisos()
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
        pedirIgnorarBateria()
    }

    // Muestra el aviso del sistema (una sola vez) para excluir la app del ahorro de batería
    private fun pedirIgnorarBateria() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }
}
