package com.example.zenggeble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Calendar

private const val TAG = "ZenggeBLE"
private const val TIMEOUT_MS = 20_000L   // máximo por comando antes de abortar
private const val PAUSA_MS = 1_000L      // respiro entre comandos

private class Pedido(
    val ctx: Context,
    val mac: String,
    val encender: Boolean,
    val alTerminar: (Boolean) -> Unit
)

// Todo el BLE pasa por una sola cola y un solo hilo (el principal):
// un comando a la vez, aunque lo pidan el servicio y la pantalla al mismo tiempo.
private val bleHandler = Handler(Looper.getMainLooper())
private val cola = mutableListOf<Pedido>()
private var enCurso = false
private var seq: Byte = 0x20

private fun siguienteEnCola() {
    if (enCurso) return
    val p = cola.removeFirstOrNull() ?: return
    enCurso = true
    Sesion(p).iniciar()
}

// Construye la trama: [00 seq 80 00 00 N N+1 0a] + cuerpo
private fun empaquetar(cuerpo: ByteArray): ByteArray {
    val n = cuerpo.size
    val paquete = ByteArray(8 + n)
    paquete[0] = 0x00
    paquete[1] = seq++
    paquete[2] = 0x80.toByte()
    paquete[3] = 0x00
    paquete[4] = 0x00
    paquete[5] = n.toByte()
    paquete[6] = (n + 1).toByte()
    paquete[7] = 0x0A
    System.arraycopy(cuerpo, 0, paquete, 8, n)
    return paquete
}

private fun tramaHora(): ByteArray {
    val c = Calendar.getInstance(SolarCalculator.ZONA)
    val yy = (c.get(Calendar.YEAR) % 100).toByte()
    val mm = (c.get(Calendar.MONTH) + 1).toByte()
    val dd = c.get(Calendar.DAY_OF_MONTH).toByte()
    val hh = c.get(Calendar.HOUR_OF_DAY).toByte()
    val mi = c.get(Calendar.MINUTE).toByte()
    val ss = c.get(Calendar.SECOND).toByte()
    val wd = (c.get(Calendar.DAY_OF_WEEK) - 1).toByte() // 0 = Domingo
    val cuerpoHora = byteArrayOf(0x10, 0x14, yy, mm, dd, hh, mi, ss, wd, 0x00)
    return empaquetar(cuerpoHora)
}

private fun tramaPower(on: Boolean): ByteArray {
    val arg = if (on) 0x23.toByte() else 0x24.toByte()
    val chk = ((0x71 + arg.toInt()) and 0xFF).toByte()
    val cuerpoPower = byteArrayOf(0x71, arg, chk)
    return empaquetar(cuerpoPower)
}

@SuppressLint("MissingPermission")
private class Sesion(private val p: Pedido) : BluetoothGattCallback() {

    private var gatt: BluetoothGatt? = null
    private var terminado = false
    private val timeout = Runnable { terminar(false, "timeout") }

    fun iniciar() {
        val adapter = (p.ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            terminar(false, "Bluetooth apagado")
            return
        }
        val device = try {
            adapter.getRemoteDevice(p.mac.uppercase())
        } catch (e: Exception) {
            terminar(false, "MAC invalida")
            return
        }
        bleHandler.postDelayed(timeout, TIMEOUT_MS)
        gatt = try {
            // Los callbacks llegan por bleHandler (hilo principal): sin carreras.
            device.connectGatt(p.ctx, false, this, BluetoothDevice.TRANSPORT_LE,
                BluetoothDevice.PHY_LE_1M_MASK, bleHandler)
        } catch (e: Exception) {
            Log.e(TAG, "connectGatt lanzó excepción", e)
            null
        }
        if (gatt == null) terminar(false, "connectGatt no devolvió conexión")
    }

    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
        if (terminado) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) cerrar(g)
            return
        }
        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
            if (!g.requestMtu(185)) g.discoverServices()
        } else {
            terminar(false, "conexión fallida (status=$status, estado=$newState)")
        }
    }

    override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
        if (!terminado) g.discoverServices()
    }

    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
        if (terminado) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            terminar(false, "discoverServices status=$status")
            return
        }

        // Buscar la característica de escritura (típicamente FFE9 o cualquiera con WriteNoResponse)
        var charEscritura: BluetoothGattCharacteristic? = null
        for (service in g.services) {
            for (ch in service.characteristics) {
                val prop = ch.properties
                if ((prop and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) ||
                    (prop and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)) {
                    if (ch.uuid.toString().contains("ffe9") || ch.instanceId == 0x18 || charEscritura == null) {
                        charEscritura = ch
                    }
                }
            }
        }

        val ch = charEscritura
        if (ch == null) {
            terminar(false, "sin característica de escritura")
            return
        }

        // Hora primero, luego power (mismos tiempos que antes)
        val okHora = escribir(g, ch, tramaHora())
        bleHandler.postDelayed({
            if (terminado) return@postDelayed
            val okPower = escribir(g, ch, tramaPower(p.encender))
            Log.d(TAG, "${p.mac}: hora=$okHora power=$okPower")
            bleHandler.postDelayed({
                terminar(okPower, if (okPower) "ok" else "escritura de power rechazada")
            }, 700)
        }, 200)
    }

    private fun escribir(g: BluetoothGatt, ch: BluetoothGattCharacteristic, data: ByteArray): Boolean {
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        ch.value = data
        return g.writeCharacteristic(ch)
    }

    private fun cerrar(g: BluetoothGatt) {
        try { g.close() } catch (e: Exception) { }
    }

    private fun terminar(exito: Boolean, motivo: String) {
        if (terminado) return
        terminado = true
        bleHandler.removeCallbacks(timeout)
        Log.d(TAG, "${p.mac} ${if (p.encender) "ON" else "OFF"}: ${if (exito) "OK" else "FALLO"} ($motivo)")
        gatt?.let { g ->
            try { g.disconnect() } catch (e: Exception) { }
            bleHandler.postDelayed({ cerrar(g) }, 500)
        }
        try {
            p.alTerminar(exito)
        } catch (e: Exception) {
            Log.e(TAG, "el callback lanzó excepción", e)
        }
        enCurso = false
        bleHandler.postDelayed({ siguienteEnCola() }, PAUSA_MS)
    }
}

class ZenggeBleManager(context: Context) {

    private val appContext = context.applicationContext

    fun enviarComando(mac: String, encender: Boolean, alTerminar: (Boolean) -> Unit) {
        bleHandler.post {
            cola.add(Pedido(appContext, mac, encender, alTerminar))
            siguienteEnCola()
        }
    }
}
