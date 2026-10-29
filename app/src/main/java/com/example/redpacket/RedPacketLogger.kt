package com.example.redpacket

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.LinkedList
import java.util.Locale

/**
 * 简单的进程内日志缓冲，供 MainActivity 实时展示抢红包记录。
 * 与 AccessibilityService 同进程，无需跨进程通信。
 */
object RedPacketLogger {
    private const val MAX = 100
    private val buffer = LinkedList<String>()

    @Synchronized
    fun add(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        buffer.addLast("[$t] $msg")
        while (buffer.size > MAX) buffer.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<String> = ArrayList(buffer)
}
