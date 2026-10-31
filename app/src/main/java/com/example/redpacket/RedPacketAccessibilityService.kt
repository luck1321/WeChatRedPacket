package com.example.redpacket

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 微信红包自动抢 —— Android 无障碍服务（原生方案）
 * 仅使用官方 AccessibilityService API，不逆向、不注入微信进程。
 *
 * ⚠️ 免责：自动抢红包违反《微信软件许可及服务协议》，可能导致账号限制/封禁，
 *    本代码仅用于学习 Android 无障碍服务技术，后果自负。
 *
 * 使用场景：微信停留在某一个一直打开的群聊前台，本服务持续监控该群红包。
 */
class RedPacketAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RedPacketService"
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        private const val TEXT_RED_PACKET = "微信红包"
        private const val TEXT_OPEN = "开"
        private const val PROCESSED_TTL = 8000L
        private const val MIN_CLICK_INTERVAL = 1200L   // 限制点击频率，降低风控与误触
        private const val CHANNEL_ID = "redpacket_monitor"
    }

    // 已处理节点去重：key -> 处理时间戳（避免重复点击同一红包）
    private val processed = LinkedHashMap<String, Long>()
    private var lastClickTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        serviceInfo = info
        notifyStarted()
        RedPacketLogger.add("服务已启动，开始监控当前前台群")
        Log.d(TAG, "红包无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 只在微信内处理；packageName 可能为 null，用 toString 比较更安全
        if (event.packageName?.toString() != WECHAT_PACKAGE) return
        val root = rootInActiveWindow ?: return
        try {
            handle(root)
        } catch (e: Exception) {
            Log.w(TAG, "handle error: ${e.message}")
        } finally {
            // 必须回收 root，否则无障碍节点对象堆积导致崩溃/卡顿
            root.recycle()
        }
    }

    private fun handle(root: AccessibilityNodeInfo) {
        // 1) 优先：红包详情页点击「开」
        if (tryClickOpen(root)) return
        // 2) 聊天界面点击「微信红包」消息进入详情（当前前台群）
        tryClickRedPacket(root)
    }

    private fun tryClickOpen(root: AccessibilityNodeInfo): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(TEXT_OPEN)
        var clicked = false
        try {
            for (node in nodes) {
                val txt = node.text?.toString() ?: ""
                val desc = node.contentDescription?.toString() ?: ""
                // 只点精确为「开」的节点，避免误触「开通」「开启」等
                if (txt != TEXT_OPEN && desc != TEXT_OPEN) continue
                val target = if (node.isClickable) node else findClickableParent(node)
                if (target == null) continue
                if (target.parent == null) continue        // 不点整个窗口根节点
                if (isProcessed(keyOf(target))) continue
                if (!canClickNow()) continue
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                markProcessed(keyOf(target))
                lastClickTime = SystemClock.elapsedRealtime()
                RedPacketLogger.add("抢到红包")
                Log.d(TAG, "点击开红包")
                clicked = true
                if (target != node) target.recycle()        // 父节点不在 nodes 列表，需单独回收
                break
            }
        } catch (e: Exception) {
            Log.w(TAG, "tryClickOpen error: ${e.message}")
        } finally {
            for (n in nodes) n.recycle()                    // 回收 find 返回的节点
        }
        return clicked
    }

    private fun tryClickRedPacket(root: AccessibilityNodeInfo) {
        val nodes = root.findAccessibilityNodeInfosByText(TEXT_RED_PACKET)
        try {
            for (node in nodes) {
                val target = findClickableParent(node) ?: node
                if (target.parent == null) continue         // 不点根节点
                if (isProcessed(keyOf(target))) continue
                if (!canClickNow()) continue
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                markProcessed(keyOf(target))
                lastClickTime = SystemClock.elapsedRealtime()
                RedPacketLogger.add("发现红包消息，点击进入详情")
                Log.d(TAG, "点击红包消息")
                if (target != node) target.recycle()
                break
            }
        } catch (e: Exception) {
            Log.w(TAG, "tryClickRedPacket error: ${e.message}")
        } finally {
            for (n in nodes) n.recycle()
        }
    }

    /**
     * 向上回溯最近的「可点击」父节点（微信红包消息的可点击容器是列表项）。
     * 同时回收沿途的非点击中间父节点，避免节点泄漏。
     */
    private fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) return current
            val parent = current.parent
            if (current != node) current.recycle()   // 原始 node 由调用方负责，不在此回收
            current = parent
        }
        return null
    }

    /** 用文本 + 描述 + 屏幕边界生成稳定 key（同一红包位置不变时 key 稳定） */
    private fun keyOf(node: AccessibilityNodeInfo): String {
        val rect = Rect()
        return try {
            node.getBoundsInScreen(rect)
            (node.text?.toString() ?: "") + "|" +
                    (node.contentDescription?.toString() ?: "") + "|" + rect.toShortString()
        } catch (e: Exception) {
            // 节点已 detached 时退化为仅用文本，保证不崩
            (node.text?.toString() ?: "") + "|" + (node.contentDescription?.toString() ?: "")
        }
    }

    private fun isProcessed(key: String): Boolean {
        val now = System.currentTimeMillis()
        val it = processed.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value > PROCESSED_TTL) it.remove()
        }
        return processed.containsKey(key)
    }

    private fun markProcessed(key: String) {
        processed[key] = System.currentTimeMillis()
    }

    /** 限制两次点击最小间隔，避免极快连点触发微信风控 */
    private fun canClickNow(): Boolean {
        return SystemClock.elapsedRealtime() - lastClickTime >= MIN_CLICK_INTERVAL
    }

    /**
     * 普通通知：提示服务已开始监控当前前台群。
     * 使用 notify 而非 startForeground，规避 Android 14 前台服务类型限制。
     * 注意：后台监控由无障碍服务本身保证，与通知是否被划掉无关。
     */
    private fun notifyStarted() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "红包监控", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder.setContentTitle("微信红包助手")
            .setContentText("已开始监控当前前台群聊的红包")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
        nm.notify(1, builder.build())
    }

    override fun onInterrupt() {
        RedPacketLogger.add("服务中断")
        Log.d(TAG, "红包无障碍服务中断")
    }
}
