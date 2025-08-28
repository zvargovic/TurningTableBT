package com.pravimputem.okretnistol

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.concurrent.thread

class BluetoothHelper(private val ctx: Context) {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var readerThread: Thread? = null

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onLineReceived: ((String) -> Unit)? = null
    var onLineSent: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun isConnected() = socket?.isConnected == true

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        input = null
        output = null
        readerThread = null
    }

    private fun hasBtPerms(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val perms = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        return perms.all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun listBondedDeviceNames(): List<String> {
        if (!hasBtPerms()) {
            onError?.invoke("Bluetooth permissions missing")
            return emptyList()
        }
        return try {
            adapter?.bondedDevices?.map { it.name } ?: emptyList()
        } catch (e: SecurityException) {
            onError?.invoke("No permission to list devices")
            emptyList()
        }
    }

    fun connectToBondedByName(name: String, cb: (Boolean, String) -> Unit) {
        if (!hasBtPerms()) {
            cb(false, "Bluetooth permissions missing")
            return
        }
        val dev: BluetoothDevice? = try {
            adapter?.bondedDevices?.firstOrNull { it.name == name }
        } catch (e: SecurityException) {
            onError?.invoke("No permission to connect")
            null
        }
        if (dev == null) {
            cb(false, "Device $name not paired")
            return
        }
        thread {
            try {
                socket = dev.createRfcommSocketToServiceRecord(uuid)
                socket?.connect()
                input = socket?.inputStream
                output = socket?.outputStream
                startReader()
                onConnected?.invoke()
                cb(true, "OK")
            } catch (e: Exception) {
                close()
                cb(false, "Connect failed: ${e.message}")
            }
        }
    }

    private fun startReader() {
        readerThread = thread {
            try {
                val buf = ByteArray(1024)
                var line = ""
                while (socket?.isConnected == true) {
                    val n = input?.read(buf) ?: break
                    if (n > 0) {
                        line += String(buf, 0, n)
                        while (line.contains("\n")) {
                            val idx = line.indexOf("\n")
                            val one = line.substring(0, idx).trim()
                            if (one.isNotEmpty()) onLineReceived?.invoke(one)
                            line = line.substring(idx + 1)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                onDisconnected?.invoke()
                close()
            }
        }
    }

    fun sendLine(s: String, cb: ((Boolean, String) -> Unit)? = null) {
        if (!isConnected()) {
            cb?.invoke(false, "Not connected")
            return
        }
        try {
            output?.write((s + "\n").toByteArray())
            output?.flush()
            onLineSent?.invoke(s)
            cb?.invoke(true, "OK")
        } catch (e: Exception) {
            cb?.invoke(false, "Send failed: ${e.message}")
        }
    }
}