package com.example.dms

import android.car.Car
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

class VhalBridge(context: Context) {

    companion object {
        const val PROPERTY_EV_BATTERY = 291504909
        const val PROPERTY_GEAR = 289408000
        const val PROPERTY_SPEED = 291504647
    }

    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var isConnected = false

    init {
        connectToCar(context)
    }

    private fun connectToCar(context: Context) {
        try {
            car = Car.createCar(context)
            propertyManager = car?.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            isConnected = true
            Log.i("VhalBridge", "✅ Kết nối Car Service thành công")
        } catch (e: Exception) {
            Log.e("VhalBridge", "Connect Error", e)
        }
    }

    fun registerSensors(callback: CarPropertyManager.CarPropertyEventCallback) {
        if (!isConnected || propertyManager == null) return
        try {
            propertyManager?.registerCallback(callback, PROPERTY_EV_BATTERY, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        } catch (e: Exception) {}
        try {
            propertyManager?.registerCallback(callback, PROPERTY_GEAR, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        } catch (e: Exception) {}
        try {
            propertyManager?.registerCallback(callback, PROPERTY_SPEED, 1.0f)
        } catch (e: Exception) {}
    }

    fun unregisterSensors(callback: CarPropertyManager.CarPropertyEventCallback) {
        try { propertyManager?.unregisterCallback(callback) } catch (e: Exception) {}
    }

    fun disconnect() {
        car?.disconnect()
    }
}