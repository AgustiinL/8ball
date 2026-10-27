package cl.guia8ball

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.WebView
import android.widget.TextView
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var web: WebView? = null
    private var bubble: TextView? = null
    private lateinit var webParams: WindowManager.LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var play = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        addWeb()
        addBubble()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("g8", "Guía 8 Ball", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "g8")
            .setContentTitle("Guía 8 Ball activa")
            .setContentText("Mantén pulsada la burbuja para cerrar")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun addWeb() {
        webParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 28) {
            webParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val w = WebView(this)
        w.setBackgroundColor(Color.TRANSPARENT)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.allowFileAccess = true
        w.loadUrl("file:///android_asset/index.html?overlay=1")
        wm.addView(w, webParams)
        web = w
    }

    private fun addBubble() {
        val b = TextView(this).apply {
            text = "✏️"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(170, 0, 0, 0))
            }
        }
        bubbleParams = WindowManager.LayoutParams(
            dp(52), dp(52),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = dp(120)
        }

        var sx = 0
        var sy = 0
        var tx = 0f
        var ty = 0f
        var moved = false
        var downT = 0L
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = bubbleParams.x
                    sy = bubbleParams.y
                    tx = e.rawX
                    ty = e.rawY
                    moved = false
                    downT = System.currentTimeMillis()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt()
                    val dy = (e.rawY - ty).toInt()
                    if (abs(dx) > dp(8) || abs(dy) > dp(8)) moved = true
                    if (moved) {
                        bubbleParams.x = sx + dx
                        bubbleParams.y = sy + dy
                        wm.updateViewLayout(v, bubbleParams)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (System.currentTimeMillis() - downT > 800) stopSelf() else toggle()
                    }
                }
            }
            true
        }
        wm.addView(b, bubbleParams)
        bubble = b
    }

    private fun toggle() {
        play = !play
        webParams.flags = if (play) {
            webParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            webParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        web?.let { wm.updateViewLayout(it, webParams) }
        web?.evaluateJavascript("setPlay($play)", null)
        bubble?.text = if (play) "🎮" else "✏️"
    }

    override fun onDestroy() {
        try { web?.let { wm.removeView(it); it.destroy() } } catch (_: Exception) {}
        try { bubble?.let { wm.removeView(it) } } catch (_: Exception) {}
        web = null
        bubble = null
        super.onDestroy()
    }
}
