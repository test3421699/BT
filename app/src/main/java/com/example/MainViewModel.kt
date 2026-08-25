package com.example

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class CustomButton(
    val id: String,
    val label: String,
    val command: String
)

data class CustomSlider(
    val id: String,
    val label: String,
    val prefix: String,
    val min: Int,
    val max: Int,
    val current: Int
)

enum class DriveMode(val label: String, val description: String) {
    CLICK_TOGGLE(
        "Click to Start / Stop",
        "Tap direction to start moving, tap again or tap STOP to stop."
    ),
    PRESS_HOLD(
        "Press and Hold Mode",
        "Hold button to drive, release button to stop instantly."
    )
}

class MainViewModel(private val context: Context) : ViewModel() {

    // Button Configuration Persistence using SharedPreferences
    private val sharedPrefs = context.getSharedPreferences("rc_controller_prefs", Context.MODE_PRIVATE)

    val bluetoothManager = ESP32BluetoothManager(context)

    // Controller Active / Started state
    private val _isControllerStarted = MutableStateFlow(false)
    val isControllerStarted: StateFlow<Boolean> = _isControllerStarted.asStateFlow()

    // Drive Mode configuration loaded from presets
    private val _driveMode = MutableStateFlow(
        try {
            val modeStr = sharedPrefs.getString("preset_drive_mode", DriveMode.CLICK_TOGGLE.name)
            DriveMode.valueOf(modeStr ?: DriveMode.CLICK_TOGGLE.name)
        } catch (e: Exception) {
            DriveMode.CLICK_TOGGLE
        }
    )
    val driveMode: StateFlow<DriveMode> = _driveMode.asStateFlow()

    // Current active movement command ("F", "B", "L", "R", "S" or null)
    private val _activeMovement = MutableStateFlow("S")
    val activeMovement: StateFlow<String> = _activeMovement.asStateFlow()

    // Current speed value loaded from presets (100 to 255)
    private val _currentSpeed = MutableStateFlow(sharedPrefs.getInt("preset_current_speed", 200))
    val currentSpeed: StateFlow<Int> = _currentSpeed.asStateFlow()

    // Steering Trim value loaded from presets (-50 to 50, default 0)
    private val _steeringTrim = MutableStateFlow(sharedPrefs.getInt("preset_steering_trim", 0))
    val steeringTrim: StateFlow<Int> = _steeringTrim.asStateFlow()

    // Error messages
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Dialog state for device picker
    private val _isDevicePickerOpen = MutableStateFlow(false)
    val isDevicePickerOpen: StateFlow<Boolean> = _isDevicePickerOpen.asStateFlow()

    // List of paired devices
    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    private val _cmdForward = MutableStateFlow(sharedPrefs.getString("cmd_forward", "F") ?: "F")
    val cmdForward: StateFlow<String> = _cmdForward.asStateFlow()

    private val _cmdBackward = MutableStateFlow(sharedPrefs.getString("cmd_backward", "B") ?: "B")
    val cmdBackward: StateFlow<String> = _cmdBackward.asStateFlow()

    private val _cmdLeft = MutableStateFlow(sharedPrefs.getString("cmd_left", "L") ?: "L")
    val cmdLeft: StateFlow<String> = _cmdLeft.asStateFlow()

    private val _cmdRight = MutableStateFlow(sharedPrefs.getString("cmd_right", "R") ?: "R")
    val cmdRight: StateFlow<String> = _cmdRight.asStateFlow()

    private val _cmdStop = MutableStateFlow(sharedPrefs.getString("cmd_stop", "S") ?: "S")
    val cmdStop: StateFlow<String> = _cmdStop.asStateFlow()

    // Robot firmware mode ("D" or "O") - D: Drive Mode, O: Obstacle Mode
    private val _robotMode = MutableStateFlow(sharedPrefs.getString("preset_robot_mode", "D") ?: "D")
    val robotMode: StateFlow<String> = _robotMode.asStateFlow()

    // Speed slider limits and prefix configuration
    private val _speedMin = MutableStateFlow(sharedPrefs.getInt("speed_min", 100))
    val speedMin: StateFlow<Int> = _speedMin.asStateFlow()

    private val _speedMax = MutableStateFlow(sharedPrefs.getInt("speed_max", 255))
    val speedMax: StateFlow<Int> = _speedMax.asStateFlow()

    private val _speedPrefix = MutableStateFlow(sharedPrefs.getString("speed_prefix", "") ?: "")
    val speedPrefix: StateFlow<String> = _speedPrefix.asStateFlow()

    // Steering trim slider limits and prefix configuration
    private val _trimMin = MutableStateFlow(sharedPrefs.getInt("trim_min", -50))
    val trimMin: StateFlow<Int> = _trimMin.asStateFlow()

    private val _trimMax = MutableStateFlow(sharedPrefs.getInt("trim_max", 50))
    val trimMax: StateFlow<Int> = _trimMax.asStateFlow()

    private val _trimPrefix = MutableStateFlow(sharedPrefs.getString("trim_prefix", "B:") ?: "B:")
    val trimPrefix: StateFlow<String> = _trimPrefix.asStateFlow()

    // Custom Buttons and Sliders state flows
    private val _customButtons = MutableStateFlow<List<CustomButton>>(emptyList())
    val customButtons: StateFlow<List<CustomButton>> = _customButtons.asStateFlow()

    private val _customSliders = MutableStateFlow<List<CustomSlider>>(emptyList())
    val customSliders: StateFlow<List<CustomSlider>> = _customSliders.asStateFlow()

    // Dialog state for command configuration settings
    private val _isConfigDialogOpen = MutableStateFlow(false)
    val isConfigDialogOpen: StateFlow<Boolean> = _isConfigDialogOpen.asStateFlow()

    init {
        // Load custom components
        _customButtons.value = loadCustomButtons()
        _customSliders.value = loadCustomSliders()

        // Automatically check paired devices if permissions are already granted
        refreshPairedDevices()

        // Automatically stop & lock controller if the device disconnects
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { state ->
                if (state == ConnectionState.DISCONNECTED && _isControllerStarted.value) {
                    _isControllerStarted.value = false
                    _activeMovement.value = "S"
                    Toast.makeText(context, "Bluetooth Disconnected: Controller Locked", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun toggleControllerStart() {
        if (_isControllerStarted.value) {
            stopController()
        } else {
            startController()
        }
    }

    /**
     * Starts the controller, activates the UI buttons, and sends the default / presetted values
     * for speed, steering trim, custom sliders (prefix + value), and robot mode to the ESP32.
     */
    fun startController() {
        _isControllerStarted.value = true

        viewModelScope.launch {
            // 1. Send Stop command first to ensure safe stationary startup
            _activeMovement.value = "S"
            bluetoothManager.sendCommand(resolveCommand("S"))
            delay(40)

            // 2. Send presetted Speed value
            val speedCmd = "${_speedPrefix.value}${_currentSpeed.value}"
            bluetoothManager.sendCommand(speedCmd)
            delay(40)

            // 3. Send presetted Steering Trim value
            val trimCmd = "${_trimPrefix.value}${_steeringTrim.value}"
            bluetoothManager.sendCommand(trimCmd)
            delay(40)

            // 4. Send all presetted Custom Slider values along with their configured prefixes
            val currentSliders = _customSliders.value
            currentSliders.forEach { slider ->
                val sliderCmd = "${slider.prefix}${slider.current}"
                bluetoothManager.sendCommand(sliderCmd)
                delay(40)
            }

            // 5. Send robot operating mode if set
            if (_robotMode.value.isNotBlank()) {
                bluetoothManager.sendCommand(_robotMode.value)
                delay(40)
            }

            val slidersSummary = if (currentSliders.isNotEmpty()) {
                " + ${currentSliders.size} slider(s): " + currentSliders.joinToString(", ") { "${it.prefix}${it.current}" }
            } else ""
            Toast.makeText(context, "Controller Started! Presets sent: $speedCmd, $trimCmd$slidersSummary", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Stops the controller, disarms/deactivates the directional buttons and sends stop command.
     */
    fun stopController() {
        _isControllerStarted.value = false
        triggerStop()
        Toast.makeText(context, "System Stopped (Standby)", Toast.LENGTH_SHORT).show()
    }

    fun setDriveMode(mode: DriveMode) {
        _driveMode.value = mode
        // Safe measure: stop the car when switching modes
        triggerStop()
    }

    fun updateSpeed(newSpeed: Int) {
        val min = _speedMin.value
        val max = _speedMax.value
        val clampedSpeed = newSpeed.coerceIn(min, max)
        if (_currentSpeed.value != clampedSpeed) {
            _currentSpeed.value = clampedSpeed
            // Send new speed over Bluetooth
            bluetoothManager.sendCommand("${_speedPrefix.value}$clampedSpeed")
        }
    }

    fun updateSteeringTrim(newTrim: Int) {
        val min = _trimMin.value
        val max = _trimMax.value
        val clampedTrim = newTrim.coerceIn(min, max)
        if (_steeringTrim.value != clampedTrim) {
            _steeringTrim.value = clampedTrim
            // Send new steering trim over Bluetooth with custom prefix
            bluetoothManager.sendCommand("${_trimPrefix.value}$clampedTrim")
        }
    }

    fun savePresets() {
        sharedPrefs.edit().apply {
            putInt("preset_current_speed", _currentSpeed.value)
            putInt("preset_steering_trim", _steeringTrim.value)
            putString("preset_drive_mode", _driveMode.value.name)
            apply()
        }
        Toast.makeText(context, "Presets saved successfully!", Toast.LENGTH_SHORT).show()
    }

    fun refreshPairedDevices() {
        if (bluetoothManager.hasRequiredPermissions()) {
            _pairedDevices.value = bluetoothManager.getPairedDevices()
        }
    }

    fun openDevicePicker() {
        refreshPairedDevices()
        _isDevicePickerOpen.value = true
    }

    fun closeDevicePicker() {
        _isDevicePickerOpen.value = false
    }

    fun connectToDevice(deviceAddress: String) {
        _isDevicePickerOpen.value = false
        _errorMessage.value = null
        
        bluetoothManager.connect(deviceAddress, viewModelScope) { success, error ->
            if (!success) {
                _errorMessage.value = error
            }
        }
    }

    fun disconnectDevice() {
        bluetoothManager.disconnect()
        _activeMovement.value = "S"
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun openConfigDialog() {
        _isConfigDialogOpen.value = true
    }

    fun closeConfigDialog() {
        _isConfigDialogOpen.value = false
    }

    fun updateButtonConfigurations(forward: String, backward: String, left: String, right: String, stop: String) {
        sharedPrefs.edit().apply {
            putString("cmd_forward", forward)
            putString("cmd_backward", backward)
            putString("cmd_left", left)
            putString("cmd_right", right)
            putString("cmd_stop", stop)
            apply()
        }
        _cmdForward.value = forward
        _cmdBackward.value = backward
        _cmdLeft.value = left
        _cmdRight.value = right
        _cmdStop.value = stop
    }

    /**
     * Helper to resolve physical command string from logical direction
     */
    fun resolveCommand(logicalDir: String): String {
        return when (logicalDir) {
            "F" -> _cmdForward.value
            "B" -> _cmdBackward.value
            "L" -> _cmdLeft.value
            "R" -> _cmdRight.value
            "S" -> _cmdStop.value
            else -> logicalDir
        }
    }

    /**
     * Handles movement input triggers based on current Drive Mode
     */
    fun handleMovementAction(command: String, isPressDown: Boolean) {
        val resolvedCmd = resolveCommand(command)
        if (_driveMode.value == DriveMode.PRESS_HOLD) {
            if (isPressDown) {
                // Press down: Send movement command
                _activeMovement.value = command
                bluetoothManager.sendCommand(resolvedCmd)
            } else {
                // Press up (Release): Send stop command
                triggerStop()
            }
        } else {
            // CLICK_TOGGLE Mode (Tap to toggle)
            if (isPressDown) { // Only handle on click event
                if (_activeMovement.value == command) {
                    // Tap again to stop
                    triggerStop()
                } else {
                    // Tap to start moving in this direction
                    _activeMovement.value = command
                    bluetoothManager.sendCommand(resolvedCmd)
                }
            }
        }
    }

    /**
     * Instantly stops the RC car and updates local state.
     */
    fun triggerStop() {
        _activeMovement.value = "S"
        bluetoothManager.sendCommand(resolveCommand("S"))
    }

    fun sendObstacleMode() {
        _robotMode.value = "O"
        sharedPrefs.edit().putString("preset_robot_mode", "O").apply()
        bluetoothManager.sendCommand("O")
    }

    fun sendDriveMode() {
        _robotMode.value = "D"
        sharedPrefs.edit().putString("preset_robot_mode", "D").apply()
        bluetoothManager.sendCommand("D")
    }

    // --- Dynamic Slider Configurations ---
    fun updateSpeedConfig(min: Int, max: Int, prefix: String) {
        _speedMin.value = min
        _speedMax.value = max
        _speedPrefix.value = prefix
        sharedPrefs.edit().apply {
            putInt("speed_min", min)
            putInt("speed_max", max)
            putString("speed_prefix", prefix)
            apply()
        }
        val current = _currentSpeed.value
        val clamped = current.coerceIn(min, max)
        if (clamped != current) {
            _currentSpeed.value = clamped
        }
        bluetoothManager.sendCommand("$prefix$clamped")
    }

    fun updateTrimConfig(min: Int, max: Int, prefix: String) {
        _trimMin.value = min
        _trimMax.value = max
        _trimPrefix.value = prefix
        sharedPrefs.edit().apply {
            putInt("trim_min", min)
            putInt("trim_max", max)
            putString("trim_prefix", prefix)
            apply()
        }
        val current = _steeringTrim.value
        val clamped = current.coerceIn(min, max)
        if (clamped != current) {
            _steeringTrim.value = clamped
        }
        bluetoothManager.sendCommand("$prefix$clamped")
    }

    // --- Custom Buttons Persistence ---
    private fun loadCustomButtons(): List<CustomButton> {
        val jsonStr = sharedPrefs.getString("custom_buttons", "[]") ?: "[]"
        val list = mutableListOf<CustomButton>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomButton(
                        id = obj.getString("id"),
                        label = obj.getString("label"),
                        command = obj.getString("command")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveCustomButtons(list: List<CustomButton>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("label", item.label)
                obj.put("command", item.command)
                array.put(obj)
            }
            sharedPrefs.edit().putString("custom_buttons", array.toString()).apply()
            _customButtons.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addCustomButton(label: String, command: String) {
        val currentList = _customButtons.value.toMutableList()
        currentList.add(CustomButton(id = java.util.UUID.randomUUID().toString(), label = label, command = command))
        saveCustomButtons(currentList)
    }

    fun editCustomButton(id: String, label: String, command: String) {
        val currentList = _customButtons.value.map {
            if (it.id == id) it.copy(label = label, command = command) else it
        }
        saveCustomButtons(currentList)
    }

    fun deleteCustomButton(id: String) {
        val currentList = _customButtons.value.filter { it.id != id }
        saveCustomButtons(currentList)
    }

    fun sendCustomButtonCommand(command: String) {
        bluetoothManager.sendCommand(command)
    }

    // --- Custom Sliders Persistence ---
    private fun loadCustomSliders(): List<CustomSlider> {
        val jsonStr = sharedPrefs.getString("custom_sliders", "[]") ?: "[]"
        val list = mutableListOf<CustomSlider>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomSlider(
                        id = obj.getString("id"),
                        label = obj.getString("label"),
                        prefix = obj.getString("prefix"),
                        min = obj.getInt("min"),
                        max = obj.getInt("max"),
                        current = obj.getInt("current")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveCustomSliders(list: List<CustomSlider>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("label", item.label)
                obj.put("prefix", item.prefix)
                obj.put("min", item.min)
                obj.put("max", item.max)
                obj.put("current", item.current)
                array.put(obj)
            }
            sharedPrefs.edit().putString("custom_sliders", array.toString()).apply()
            _customSliders.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addCustomSlider(label: String, prefix: String, min: Int, max: Int, defaultVal: Int) {
        val currentList = _customSliders.value.toMutableList()
        val clampedVal = defaultVal.coerceIn(min, max)
        currentList.add(
            CustomSlider(
                id = java.util.UUID.randomUUID().toString(),
                label = label,
                prefix = prefix,
                min = min,
                max = max,
                current = clampedVal
            )
        )
        saveCustomSliders(currentList)
    }

    fun editCustomSlider(id: String, label: String, prefix: String, min: Int, max: Int) {
        val currentList = _customSliders.value.map {
            if (it.id == id) {
                val clampedVal = it.current.coerceIn(min, max)
                it.copy(label = label, prefix = prefix, min = min, max = max, current = clampedVal)
            } else it
        }
        saveCustomSliders(currentList)
    }

    fun deleteCustomSlider(id: String) {
        val currentList = _customSliders.value.filter { it.id != id }
        saveCustomSliders(currentList)
    }

    fun updateCustomSliderValue(id: String, newValue: Int) {
        val currentList = _customSliders.value.map {
            if (it.id == id) {
                val clampedVal = newValue.coerceIn(it.min, it.max)
                bluetoothManager.sendCommand("${it.prefix}$clampedVal")
                it.copy(current = clampedVal)
            } else it
        }
        saveCustomSliders(currentList)
    }
}
