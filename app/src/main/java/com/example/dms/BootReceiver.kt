package com.example.dms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Lắng nghe tín hiệu khởi động
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.i("DmsBoot", "🔥 NHẬN TÍN HIỆU BOOT. Đang tự động bật DMS Service...")
            DmsService.startService(context)
        }
    }
}