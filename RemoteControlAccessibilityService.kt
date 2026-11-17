package com.ankit.phonemirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

class RemoteControlAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: RemoteControlAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    fun handleRemoteCommand(json: JSONObject) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        when (json.optString("type")) {
            "tap" -> {
                val x = (json.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f)) * width
                val y = (json.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f)) * height
                performTap(x, y, 50L)
            }
            "double_tap" -> {
                val x = (json.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f)) * width
                val y = (json.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f)) * height
                performDoubleTap(x, y)
            }
            "long_press" -> {
                val x = (json.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f)) * width
                val y = (json.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f)) * height
                performTap(x, y, 650L)
            }
            "swipe" -> {
                val sx = (json.optDouble("startX", 0.5).toFloat().coerceIn(0f, 1f)) * width
                val sy = (json.optDouble("startY", 0.5).toFloat().coerceIn(0f, 1f)) * height
                val ex = (json.optDouble("endX", 0.5).toFloat().coerceIn(0f, 1f)) * width
                val ey = (json.optDouble("endY", 0.5).toFloat().coerceIn(0f, 1f)) * height
                val duration = json.optLong("duration", 250L).coerceIn(50L, 1500L)
                performSwipe(sx, sy, ex, ey, duration)
            }
            "text" -> {
                val input = json.optString("text", "")
                if (input.isNotEmpty()) {
                    val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    if (focused != null) {
                        val args = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, input)
                        }
                        focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        focused.recycle()
                    }
                }
            }
            "action" -> {
                when (json.optString("action")) {
                    "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                    "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
                    "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                    "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                    "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                    "power_menu" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
                    "lock" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                    }
                    "vol_up" -> adjustVolume(AudioManager.ADJUST_RAISE)
                    "vol_down" -> adjustVolume(AudioManager.ADJUST_LOWER)
                }
            }
        }
    }

    private fun adjustVolume(direction: Int) {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun performTap(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun performDoubleTap(x: Float, y: Float) {
        val path1 = Path().apply { moveTo(x, y) }
        val path2 = Path().apply { moveTo(x, y) }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0L, 40L)
        val stroke2 = GestureDescription.StrokeDescription(path2, 90L, 40L)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performSwipe(sx: Float, sy: Float, ex: Float, ey: Float, durationMs: Long) {
        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(ex, ey)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
