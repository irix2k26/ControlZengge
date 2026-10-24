package com.example.zenggeble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.*

@SuppressLint("MissingPermission")
class ZenggeBleManager(private val context: Context) {

    private var seq: Byte = 0x20

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

    private fun getTramaHora(): ByteArray {
        val c = Calendar.getInstance()
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

    private fun getTramaPower(on: Boolean): ByteArray {
        val arg = if (on) 0x23.toByte() else 0x24.toByte()
        val chk = ((0x71 + arg.toInt()) and 0xFF).toByte()
        val cuerpoPower = byteArrayOf(0x71, arg, chk)
        return empaquetar(cuerpoPower)
    }

    fun enviarComando(mac: String, encender: Boolean, alTerminar: (Boolean) -> Unit) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device = try {
            adapter.getRemoteDevice(mac.uppercase())
        } catch (e: Exception) {
            alTerminar(false)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var gattRef: BluetoothGatt? = null

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.requestMtu(185)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close()
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    gatt.disconnect()
                    alTerminar(false)
                    return
                }

                // Buscar la característica de escritura (típicamente FFE9 o cualquiera con WriteNoResponse)
                var charEscritura: BluetoothGattCharacteristic? = null
                for (service in gatt.services) {
                    for (ch in service.characteristics) {
                        val prop = ch.properties
                        if ((prop and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) ||
                            (prop and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)) {
                            // Si coincide con servicios propietarios Zengge
                            if (ch.uuid.toString().contains("ffe9") || ch.instanceId == 0x18 || charEscritura == null) {
                                charEscritura = ch
                            }
                        }
                    }
                }

                if (charEscritura != null) {
                    // Enviar Hora primero
                    escribir(gatt, charEscritura, getTramaHora())
                    
                    handler.postDelayed({
                        // Enviar Power
                        escribir(gatt, charEscritura, getTramaPower(encender))
                        
                        handler.postDelayed({
                            gatt.disconnect()
                            alTerminar(true)
                        }, 700)
                    }, 200)
                } else {
                    gatt.disconnect()
                    alTerminar(false)
                }
            }
        }

        gattRef = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun escribir(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, data: ByteArray) {
        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        ch.value = data
        gatt.writeCharacteristic(ch)
    }
}