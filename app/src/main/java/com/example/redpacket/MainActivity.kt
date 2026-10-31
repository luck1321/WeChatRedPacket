package com.example.redpacket

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            updateStatus()
            tvLog.text = RedPacketLogger.snapshot().asReversed().joinToString("\n")
            handler.postDelayed(this, 800)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvStatus = findViewById(R.id.tv_status)
        tvLog = findViewById(R.id.tv_log)
        findViewById<Button>(R.id.btn_open).setOnClickListener {
            // 跳转到系统无障碍设置，用户需手动开启本服务
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    private fun updateStatus() {
        val enabled = isServiceEnabled()
        tvStatus.text = if (enabled) "监控中（当前前台群）" else "未开启"
        tvStatus.setTextColor(if (enabled) 0xFF388E3C.toInt() else 0xFFD32F2F.toInt())
    }

    /** 检查本无障碍服务是否已在系统设置中开启（精确匹配本应用组件，避免误判同名服务） */
    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val component = "com.example.redpacket/.RedPacketAccessibilityService"
        return enabled.split(":").any {
            it.equals(component, ignoreCase = true) || it.contains("com.example.redpacket")
        }
    }
}
