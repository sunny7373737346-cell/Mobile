package com.ankit.phonemirror

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import org.json.JSONObject
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var etServerUrl: EditText
    private lateinit var etRoomCode: EditText
    private lateinit var etRemoteText: EditText
    private lateinit var ivRemoteScreen: ImageView
    private lateinit var switchTouchControl: SwitchCompat

    private var viewerClient: MirrorWebSocketClient? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchDownTime = 0L

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenMirrorService::class.java).apply {
                putExtra(ScreenMirrorService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenMirrorService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenMirrorService.EXTRA_SERVER_URL, etServerUrl.text.toString().trim())
                putExtra(ScreenMirrorService.EXTRA_ROOM_CODE, etRoomCode.text.toString().trim())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            tvStatus.text = "Status: Host Active (Sharing Screen)"
        } else {
            tvStatus.text = "Status: Screen Capture Permission Denied"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        etServerUrl = findViewById(R.id.etServerUrl)
        etRoomCode = findViewById(R.id.etRoomCode)
        etRemoteText = findViewById(R.id.etRemoteText)
        ivRemoteScreen = findViewById(R.id.ivRemoteScreen)
        switchTouchControl = findViewById(R.id.switchTouchControl)

        switchTouchControl.setOnCheckedChangeListener { _, isChecked ->
            tvStatus.text = if (isChecked) "Status: Touch Screen Control ON" else "Status: View-Only Mode (Touch OFF)"
        }

        findViewById<Button>(R.id.btnEnableAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStartHost).setOnClickListener {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
        }

        findViewById<Button>(R.id.btnStartViewer).setOnClickListener {
            startViewerMode()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, ScreenMirrorService::class.java))
            viewerClient?.disconnect()
            viewerClient = null
            tvStatus.text = "Status: Stopped"
        }

        findViewById<Button>(R.id.btnSendText).setOnClickListener {
            val txt = etRemoteText.text.toString()
            if (txt.isNotEmpty()) {
                viewerClient?.sendCommand(JSONObject().apply {
                    put("type", "text")
                    put("text", txt)
                })
                etRemoteText.text.clear()
            }
        }

        findViewById<Button>(R.id.btnRemoteBack).setOnClickListener { sendNavAction("back") }
        findViewById<Button>(R.id.btnRemoteHome).setOnClickListener { sendNavAction("home") }
        findViewById<Button>(R.id.btnRemoteRecents).setOnClickListener { sendNavAction("recents") }
        findViewById<Button>(R.id.btnRemoteNotif).setOnClickListener { sendNavAction("notifications") }
        findViewById<Button>(R.id.btnRemoteQuickSettings).setOnClickListener { sendNavAction("quick_settings") }
        findViewById<Button>(R.id.btnRemoteVolUp).setOnClickListener { sendNavAction("vol_up") }
        findViewById<Button>(R.id.btnRemoteVolDown).setOnClickListener { sendNavAction("vol_down") }
        findViewById<Button>(R.id.btnRemoteLock).setOnClickListener { sendNavAction("lock") }

        ivRemoteScreen.setOnTouchListener { _, event ->
            if (!switchTouchControl.isChecked || viewerClient == null) {
                return@setOnTouchListener true
            }

            val norm = mapTouchToImageCoords(event.x, event.y) ?: return@setOnTouchListener true
            val nx = norm.first
            val ny = norm.second

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = nx
                    touchStartY = ny
                    touchDownTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchDownTime
                    val dist = hypot((nx - touchStartX).toDouble(), (ny - touchStartY).toDouble())
                    if (dist < 0.02) {
                        val cmdType = if (elapsed >= 500L) "long_press" else "tap"
                        viewerClient?.sendCommand(JSONObject().apply {
                            put("type", cmdType)
                            put("x", nx.toDouble())
                            put("y", ny.toDouble())
                        })
                    } else {
                        val duration = elapsed.coerceIn(120L, 1000L)
                        viewerClient?.sendCommand(JSONObject().apply {
                            put("type", "swipe")
                            put("startX", touchStartX.toDouble())
                            put("startY", touchStartY.toDouble())
                            put("endX", nx.toDouble())
                            put("endY", ny.toDouble())
                            put("duration", duration)
                        })
                    }
                }
            }
            true
        }
    }

    private fun mapTouchToImageCoords(touchX: Float, touchY: Float): Pair<Float, Float>? {
        val drawable = ivRemoteScreen.drawable ?: return null
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return null

        val inverse = Matrix()
        if (!ivRemoteScreen.imageMatrix.invert(inverse)) return null
        val pts = floatArrayOf(touchX, touchY)
        inverse.mapPoints(pts)

        val nx = (pts[0] / dw).coerceIn(0f, 1f)
        val ny = (pts[1] / dh).coerceIn(0f, 1f)
        return Pair(nx, ny)
    }

    private fun startViewerMode() {
        viewerClient?.disconnect()
        val url = etServerUrl.text.toString().trim()
        val room = etRoomCode.text.toString().trim()
        viewerClient = MirrorWebSocketClient(
            serverUrl = url,
            roomCode = room,
            role = "viewer",
            onStatus = { status ->
                runOnUiThread { tvStatus.text = "Status: $status" }
            },
            onFrameReceived = { bytes ->
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    runOnUiThread { ivRemoteScreen.setImageBitmap(bmp) }
                }
            }
        ).also { it.connect() }
    }

    private fun sendNavAction(action: String) {
        if (!switchTouchControl.isChecked) return
        viewerClient?.sendCommand(JSONObject().apply {
            put("type", "action")
            put("action", action)
        })
    }

    override fun onDestroy() {
        viewerClient?.disconnect()
        super.onDestroy()
    }
}
