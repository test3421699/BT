package com.example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

private const val TAG = "BluetoothManager"

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

@SuppressLint("MissingPermission")
class ESP32BluetoothManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _commandLogs = MutableStateFlow<List<String>>(emptyList())
    val commandLogs: StateFlow<List<String>> = _commandLogs.asStateFlow()

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    // Standard SPP UUID (Serial Port Profile) used by ESP32 Classic Bluetooth Serial
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /**
     * Checks if Bluetooth is supported on this device.
     */
    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    /**
     * Checks if Bluetooth is enabled.
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }

    /**
     * Checks if required Bluetooth permissions are granted.
     */
    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Permissions are granted automatically upon installation on Android 11 and below
        }
    }

    /**
     * Gets a list of paired (bonded) Bluetooth devices.
     */
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasRequiredPermissions()) return emptyList()
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException reading bonded devices", e)
            emptyList()
        }
    }

    /**
     * Connects to a remote Bluetooth device by its MAC address.
     */
    fun connect(deviceAddress: String, scope: CoroutineScope, onResult: (Boolean, String?) -> Unit) {
        if (bluetoothAdapter == null) {
            onResult(false, "Bluetooth is not supported on this device.")
            return
        }

        if (!hasRequiredPermissions()) {
            onResult(false, "Bluetooth connect permission is missing.")
            return
        }

        scope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            _connectedDeviceName.value = null
            
            var deviceName = deviceAddress
            try {
                val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                try {
                    deviceName = device.name ?: deviceAddress
                } catch (se: SecurityException) {
                    Log.w(TAG, "Could not get device name due to missing permission", se)
                }

                withContext(Dispatchers.IO) {
                    // Cancel discovery if running, to avoid slowing down connection
                    try {
                        if (bluetoothAdapter.isDiscovering) {
                            bluetoothAdapter.cancelDiscovery()
                        }
                    } catch (se: SecurityException) {
                        Log.w(TAG, "Could not cancel discovery due to missing permission", se)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error checking/cancelling discovery", e)
                    }

                    // Create RFCOMM Socket using standard SPP UUID
                    val socket = device.createRfcommSocketToServiceRecord(sppUuid)
                    bluetoothSocket = socket
                    
                    // Attempt to connect (blocking call)
                    socket.connect()
                    outputStream = socket.outputStream
                }

                _connectionState.value = ConnectionState.CONNECTED
                _connectedDeviceName.value = deviceName
                logCommand("Connected to $deviceName")
                onResult(true, null)

            } catch (e: IOException) {
                Log.e(TAG, "Connection failed", e)
                closeConnection()
                _connectionState.value = ConnectionState.DISCONNECTED
                onResult(false, "Failed to connect to $deviceName: ${e.localizedMessage}")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException during connection", e)
                closeConnection()
                _connectionState.value = ConnectionState.DISCONNECTED
                onResult(false, "Bluetooth permission error: ${e.localizedMessage}. Please grant Bluetooth permissions in Android Settings.")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected connection error", e)
                closeConnection()
                _connectionState.value = ConnectionState.DISCONNECTED
                onResult(false, "Error: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Disconnects the active Bluetooth connection.
     */
    fun disconnect() {
        val deviceName = _connectedDeviceName.value
        closeConnection()
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceName.value = null
        if (deviceName != null) {
            logCommand("Disconnected from $deviceName")
        }
    }

    /**
     * Sends a command string to the ESP32.
     */
    fun sendCommand(command: String) {
        val currentSocket = bluetoothSocket
        val currentStream = outputStream

        if (currentSocket == null || currentStream == null || _connectionState.value != ConnectionState.CONNECTED) {
            // Log command even if disconnected, to assist with simulation visual feedback
            logCommand("Simulated Send: \"$command\" (No Device Connected)")
            return
        }

        try {
            currentStream.write(command.toByteArray())
            currentStream.flush()
            logCommand("Sent: \"$command\"")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send command: $command", e)
            logCommand("Error: Failed to send \"$command\". Connection lost.")
            disconnect()
        }
    }

    private fun closeConnection() {
        try {
            outputStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing output stream", e)
        } finally {
            outputStream = null
        }

        try {
            bluetoothSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing socket", e)
        } finally {
            bluetoothSocket = null
        }
    }

    private fun logCommand(message: String) {
        val currentList = _commandLogs.value.toMutableList()
        currentList.add(0, "[${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())}] $message")
        // Keep only last 50 entries
        if (currentList.size > 50) {
            currentList.removeAt(currentList.lastIndex)
        }
        _commandLogs.value = currentList
    }

    /**
     * Clears visual command console history.
     */
    fun clearLogs() {
        _commandLogs.value = emptyList()
    }
}
