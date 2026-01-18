package com.example.dms

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    // Danh sách quyền
    private val REQUIRED_PERMISSIONS = arrayOf(
        "android.car.permission.CAR_ENERGY",
        "android.car.permission.CAR_POWERTRAIN",
        "android.car.permission.CAR_SPEED"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        btnStart = findViewById(R.id.btn_start_service)
        btnStop = findViewById(R.id.btn_stop_service)

        // 1. Kiểm tra quyền
        if (!arePermissionsGranted()) {
            statusText.text = "Status: Checking Permissions..."
            requestPermissions(REQUIRED_PERMISSIONS, 100)
        } else {
            // 2. Tự động Start nếu chưa chạy
            if (!DmsService.isServiceRunning) {
                startDmsService() // Hàm này giờ sẽ update UI luôn
            } else {
                updateStatusUI() // Nếu đang chạy rồi thì chỉ cần update UI
            }
        }

        // Nút Start (Thủ công)
        btnStart.setOnClickListener {
            if (arePermissionsGranted()) {
                startDmsService()
            } else {
                Toast.makeText(this, "Chưa đủ quyền!", Toast.LENGTH_SHORT).show()
                requestPermissions(REQUIRED_PERMISSIONS, 100)
            }
        }

        // Nút Stop (Thủ công)
        btnStop.setOnClickListener {
            stopDmsService()
        }
    }

    override fun onResume() {
        super.onResume()
        // Khi quay lại app, check lại trạng thái thực tế
        updateStatusUI()
    }

    // --- SỬA ĐỔI QUAN TRỌNG TẠI ĐÂY ---
    private fun startDmsService() {
        // 1. Gọi lệnh Start Service
        DmsService.startService(this)

        // 2. CƯỠNG ÉP cập nhật UI ngay lập tức (Không chờ Service phản hồi)
        statusText.text = "DMS Status: Đang chạy ngầm (Active)"
        btnStart.isEnabled = false  // Làm mờ nút Start ngay
        btnStop.isEnabled = true    // Làm sáng nút Stop ngay
    }

    private fun stopDmsService() {
        DmsService.stopService(this)

        // Cưỡng ép cập nhật UI về trạng thái dừng
        statusText.text = "DMS Status: Đã dừng / Sẵn sàng"
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        Toast.makeText(this, "Đã dừng dịch vụ", Toast.LENGTH_SHORT).show()
    }

    // Hàm này chỉ dùng để check trạng thái khi mở lại App
    private fun updateStatusUI() {
        if (DmsService.isServiceRunning) {
            statusText.text = "DMS Status: Đang chạy ngầm (Active)"
            btnStart.isEnabled = false
            btnStop.isEnabled = true
        } else {
            statusText.text = "DMS Status: Đã dừng / Sẵn sàng"
            btnStart.isEnabled = true
            btnStop.isEnabled = false
        }
    }
    // -----------------------------------

    private fun arePermissionsGranted(): Boolean {
        for (perm in REQUIRED_PERMISSIONS) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Có quyền -> Tự động chạy luôn
                startDmsService()
            } else {
                statusText.text = "Lỗi: Thiếu quyền!"
            }
        }
    }
}