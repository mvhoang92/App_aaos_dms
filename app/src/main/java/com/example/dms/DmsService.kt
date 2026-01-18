package com.example.dms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RawRes
import androidx.core.app.NotificationCompat

class DmsService : Service() {

    companion object {
        private const val TAG = "DmsService"
        private const val CHANNEL_ID = "dms_channel_v2"
        private const val NOTIFICATION_ID = 1001
        private const val LOOP_DELAY_MS = 3000L // Tăng delay kiểm tra định kỳ

        var isServiceRunning = false

        private const val FAULT_NONE = 0
        private const val FAULT_SLEEP = 1
        private const val FAULT_SEATBELT = 2
        private const val FAULT_PHONE = 3
        private const val FAULT_CIGARETTE = 4

        private const val GEAR_PARK = 4
        private const val GEAR_REVERSE = 2
        private const val GEAR_DRIVE = 8

        data class FaultConfig(@RawRes val soundRes: Int, val message: String)

        private val FAULT_CONFIGS = mapOf(
            FAULT_SLEEP to FaultConfig(R.raw.sleep, "😴 Cảnh báo: Tài xế BUỒN NGỦ!"),
            FAULT_SEATBELT to FaultConfig(R.raw.seatbelt, "⚠️ Cảnh báo: Quên thắt DÂY AN TOÀN!"),
            FAULT_PHONE to FaultConfig(R.raw.phone, "📱 Cảnh báo: Đang dùng ĐIỆN THOẠI!"),
            FAULT_CIGARETTE to FaultConfig(R.raw.cigarette, "🚬 Cảnh báo: Phát hiện HÚT THUỐC!")
        )

        fun startService(context: Context) {
            val intent = Intent(context, DmsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, DmsService::class.java))
        }
    }

    private lateinit var vhalBridge: VhalBridge
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager

    private var currentSoundResId: Int = -1
    private var lastNotificationText: String = ""
    private var lastGear: Int = -1

    private var currentFaultType = FAULT_NONE
    private var isParked = true
    private var isReversing = false

    private val loopHandler = Handler(Looper.getMainLooper())

    private val loopRunnable = object : Runnable {
        override fun run() {
            // Chỉ log định kỳ khi thực sự đang có lỗi để theo dõi
            if (!isParked && !isReversing && currentFaultType != FAULT_NONE) {
                playAlertSound(currentFaultType)
            }
            loopHandler.postDelayed(this, LOOP_DELAY_MS)
        }
    }

    private val sensorListener = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VhalBridge.PROPERTY_GEAR -> {
                    val gear = value.value as? Int ?: return
                    if (gear != lastGear) { // Chỉ xử lý khi đổi số
                        Log.i(TAG, "⚙️ GEAR CHANGED: $lastGear -> $gear")
                        lastGear = gear
                        handleGearChange(gear)
                    }
                }
                VhalBridge.PROPERTY_SPEED -> {
                    val speed = value.value as? Float ?: 0f
                    // Chỉ log khi trạng thái di chuyển thay đổi để tránh spam
                    if (Math.abs(speed) > 0.5f && isParked) {
                        Log.i(TAG, "🚀 Xe bắt đầu di chuyển (Speed: $speed)")
                        updateDriveStatus(isVehicleParked = false)
                    } else if (Math.abs(speed) < 0.1f && !isParked && lastGear == GEAR_PARK) {
                        updateDriveStatus(isVehicleParked = true)
                    }
                }
                VhalBridge.PROPERTY_EV_BATTERY -> {
                    val level = value.value as? Float ?: return
                    val newFault = determineFault(level)
                    if (newFault != currentFaultType) {
                        Log.i(TAG, "🤖 AI State Change: $currentFaultType -> $newFault")
                        currentFaultType = newFault
                        checkAndTriggerAlert()
                    }
                }
            }
        }
        override fun onErrorEvent(propId: Int, zone: Int) {}
    }

    private fun handleGearChange(gear: Int) {
        when (gear) {
            GEAR_PARK -> {
                isReversing = false
                updateDriveStatus(isVehicleParked = true)
            }
            GEAR_REVERSE -> {
                isReversing = true
                Log.w(TAG, "🔙 Số Lùi: Tạm dừng mọi cảnh báo")
                stopAlertsImmediately()
                updateNotification("Đang lùi xe - Chế độ quan sát")
            }
            else -> { // Drive hoặc số khác
                isReversing = false
                updateDriveStatus(isVehicleParked = false)
            }
        }
    }

    private fun updateDriveStatus(isVehicleParked: Boolean) {
        if (isParked != isVehicleParked) {
            isParked = isVehicleParked
            Log.d(TAG, "Status Update: isParked = $isParked")
            checkAndTriggerAlert()
        }
    }

    private fun checkAndTriggerAlert() {
        if (isParked || isReversing) {
            stopAlertsImmediately()
            updateNotification(if(isParked) "Xe đang đỗ (P)" else "Đang lùi xe (R)")
        } else {
            if (currentFaultType != FAULT_NONE) {
                resumeAlerts()
            } else {
                stopAlertsImmediately()
                updateNotification("Hệ thống đang giám sát - An toàn")
            }
        }
    }

    private fun determineFault(level: Float): Int {
        return when {
            level in 5000f..15000f -> FAULT_SLEEP
            level in 15001f..25000f -> FAULT_SEATBELT
            level in 25001f..35000f -> FAULT_PHONE
            level in 35001f..45000f -> FAULT_CIGARETTE
            else -> FAULT_NONE
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        vhalBridge = VhalBridge(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Hệ thống DMS đã khởi động"))
        vhalBridge.registerSensors(sensorListener)
        loopHandler.post(loopRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        stopAlertsImmediately()
        vhalBridge.unregisterSensors(sensorListener)
        vhalBridge.disconnect()
        loopHandler.removeCallbacksAndMessages(null)
    }

    private fun resumeAlerts() {
        val config = FAULT_CONFIGS[currentFaultType]
        updateNotification(config?.message ?: "CẢNH BÁO NGUY HIỂM!")
        playAlertSound(currentFaultType)
    }

    private fun stopAlertsImmediately() {
        stopMediaPlayer()
    }

    private fun playAlertSound(faultType: Int) {
        val config = FAULT_CONFIGS[faultType] ?: return

        // Nếu đang phát đúng âm thanh này rồi thì không khởi tạo lại
        if (mediaPlayer?.isPlaying == true && currentSoundResId == config.soundRes) {
            return
        }

        Log.w(TAG, "🔊 ALERT START: ${config.message}")
        stopMediaPlayer()
        requestAudioFocus()

        try {
            currentSoundResId = config.soundRes
            mediaPlayer = MediaPlayer.create(this, config.soundRes).apply {
                isLooping = true
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi MediaPlayer: ${e.message}")
            currentSoundResId = -1
        }
    }

    private fun stopMediaPlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
                Log.d(TAG, "🔇 MediaPlayer Released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping player: ${e.message}")
        }
        mediaPlayer = null
        currentSoundResId = -1
        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            audioManager.requestAudioFocus(AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attr).build())
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).build())
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "DMS Alert Channel", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // Tắt tiếng notification để tránh xung đột với mediaPlayer
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DMS System")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(true) // Quan trọng: Không rung/chuông mỗi khi cập nhật text
            .build()
    }

    private fun updateNotification(text: String) {
        if (text == lastNotificationText) return // Debounce: Không cập nhật nếu text cũ
        lastNotificationText = text

        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
