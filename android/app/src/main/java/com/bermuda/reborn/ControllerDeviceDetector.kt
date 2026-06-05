package com.bermuda.reborn

import android.util.Log
import android.view.InputDevice

object ControllerDeviceDetector {
    private const val TAG = "ControllerDeviceDetector"

    fun isControllerConnected(): Boolean {
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id)
            if (device != null && id >= 0 && !device.isVirtual && isControllerSource(device.sources)) {
                Log.i(TAG, "Controller found: ${device.name} (id=$id, sources=${device.sources})")
                return true
            }
        }
        return false
    }

    fun isControllerSource(source: Int): Boolean =
        (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
}
