package com.ankit.phonemirror

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream

class ScreenMirrorService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_ROOM_CODE = "room_code"
        private const val CHANNEL_ID = "mirror_channel"
        private const val NOTIF_ID = 101
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var wsClient: MirrorWebSocketClient? = null
    private var handlerThread: HandlerThread? = null
    private var lastFrameTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        val serverUrl = intent?.getStringExtra(EXTRA_SERVER_URL) ?: return START_NOT_STICKY
        val roomCode = intent.getStringExtra(EXTRA_ROOM_CODE) ?: return START_NOT_STICKY

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        wsClient = MirrorWebSocketClient(
            serverUrl = serverUrl,
            roomCode = roomCode,
            role = "host",
            onStatus = {},
            onCommandReceived = { json ->
                RemoteControlAccessibilityService.instance?.handleRemoteCommand(json)
            }
        ).also { it.connect() }

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        startScreenCapture()
        return START_NOT_STICKY
    }

    private fun startScreenCapture() {
        val metrics = resources.displayMetrics
        // Scale down to 540px width for smooth low-latency streaming
        val targetWidth = 540
        val targetHeight = (metrics.heightPixels * (targetWidth.toFloat() / metrics.widthPixels)).toInt()
        val density = metrics.densityDpi

        handlerThread = HandlerThread("ScreenCaptureThread").apply { start() }
        val handler = Handler(handlerThread!!.looper)

        imageReader = ImageReader.newInstance(targetWidth, targetHeight, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val now = System.currentTimeMillis()
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                // Limit to ~12-15 FPS to keep bandwidth and CPU low
                if (now - lastFrameTime < 70) return@setOnImageAvailableListener
                lastFrameTime = now

                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * targetWidth

                val bitmap = Bitmap.createBitmap(
                    targetWidth + rowPadding / pixelStride,
                    targetHeight,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, targetWidth, targetHeight)
                if (cropped != bitmap) bitmap.recycle()

                val out = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, 55, out)
                cropped.recycle()
                wsClient?.sendFrame(out.toByteArray())
            } finally {
                image.close()
            }
        }, handler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "PhoneMirrorDisplay",
            targetWidth,
            targetHeight,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        wsClient?.disconnect()
        handlerThread?.quitSafely()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Mirroring",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneMirrorControl Active")
            .setContentText("Sharing screen and accepting remote touch control")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }
}
