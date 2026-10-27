package cl.guia8ball

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var bubble: TextView? = null
    private lateinit var webParams: WindowManager.LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var play = false

    // Captura de pantalla
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var vdisplay: VirtualDisplay? = null
    private var bgThread: HandlerThread? = null
    private var capW = 0
    private var capH = 0
    @Volatile private var wantFrame = false
    private var scanX = 0
    private var scanY = 0
    private var scanW = 0
    private var scanH = 0
    private var scanRad = 0f
    private var scanSens = 55
    private var scanAuto = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    @Suppress("DEPRECATION")
    private fun readData(i: Intent?): Intent? =
        if (Build.VERSION.SDK_INT >= 33) i?.getParcelableExtra("data", Intent::class.java)
        else i?.getParcelableExtra<Intent>("data")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (web != null) return START_NOT_STICKY
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data = readData(intent)
        val hasProj = code == Activity.RESULT_OK && data != null
        startAsForeground(hasProj)
        addWeb()
        addBubble()
        if (hasProj && data != null) setupCapture(code, data)
        return START_NOT_STICKY
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun startAsForeground(withProjection: Boolean) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("g8", "Guía 8 Ball", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "g8")
            .setContentTitle("Guía 8 Ball activa")
            .setContentText("Mantén pulsada la burbuja para cerrar")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        var type = 0
        if (Build.VERSION.SDK_INT >= 34) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (withProjection) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else if (Build.VERSION.SDK_INT >= 29 && withProjection) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        if (type != 0) startForeground(1, n, type) else startForeground(1, n)
    }

    // ---------- Ventana de superposición ----------

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
        w.addJavascriptInterface(Bridge(), "Android")
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

    // ---------- Captura de pantalla y detección ----------

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val m = DisplayMetrics()
        dm.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(m)
        return Pair(m.widthPixels, m.heightPixels)
    }

    private fun setupCapture(code: Int, data: Intent) {
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = mpm.getMediaProjection(code, data)
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    main.post {
                        vdisplay?.release()
                        vdisplay = null
                    }
                }
            }, main)
            projection = p
            val (w, h) = screenSize()
            capW = w
            capH = h
            val t = HandlerThread("g8cap")
            t.start()
            bgThread = t
            val ir = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            ir.setOnImageAvailableListener({ r -> onFrame(r) }, Handler(t.looper))
            reader = ir
            vdisplay = p.createVirtualDisplay(
                "g8cap", w, h, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir.surface, null, null
            )
        } catch (e: Exception) {
            projection = null
            vdisplay = null
            capW = 0
        }
    }

    // Si la pantalla giró desde que se inició, ajusta la captura
    private fun rebuildCaptureIfNeeded() {
        val (w, h) = screenSize()
        if (w == capW && h == capH) return
        val t = bgThread ?: return
        val ir = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        ir.setOnImageAvailableListener({ r -> onFrame(r) }, Handler(t.looper))
        vdisplay?.resize(w, h, resources.displayMetrics.densityDpi)
        vdisplay?.surface = ir.surface
        reader?.close()
        reader = ir
        capW = w
        capH = h
    }

    private fun err(msg: String): String = JSONObject().put("error", msg).toString()

    private fun sendResult(json: String) {
        bubble?.visibility = View.VISIBLE
        web?.evaluateJavascript("onScan(" + JSONObject.quote(json) + ")", null)
    }

    private fun startScan(x: Int, y: Int, w: Int, h: Int, rad: Float, sens: Int, auto: Boolean) {
        if (vdisplay == null) {
            sendResult(err("Captura no disponible: reinicia la guía y acepta el permiso de captura (Toda la pantalla)"))
            return
        }
        try {
            rebuildCaptureIfNeeded()
        } catch (e: Exception) {
            sendResult(err("No se pudo ajustar la captura a la pantalla"))
            return
        }
        scanX = x
        scanY = y
        scanW = w
        scanH = h
        scanRad = rad
        scanSens = sens
        scanAuto = auto
        bubble?.visibility = View.INVISIBLE
        // Espera a que la guía desaparezca de la pantalla y pide el siguiente cuadro
        main.postDelayed({
            wantFrame = true
            main.postDelayed({
                if (wantFrame) {
                    wantFrame = false
                    sendResult(err("No llegó la imagen de la pantalla. Intenta de nuevo."))
                }
            }, 2500)
        }, 350)
    }

    private fun onFrame(r: ImageReader) {
        val img = r.acquireLatestImage() ?: return
        try {
            if (!wantFrame) return
            wantFrame = false
            val plane = img.planes[0]
            val bw = plane.rowStride / plane.pixelStride
            val bmp = Bitmap.createBitmap(bw, img.height, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(plane.buffer)
            val o = JSONObject().put("w", capW)
            val dets: List<Det>
            if (scanAuto) {
                val a = BallDetector.autoDetect(bmp, capW, capH, scanSens)
                bmp.recycle()
                if (a == null) {
                    main.post { sendResult(err("No encontré la mesa. Ajústala a mano y usa 🎱 Solo bolas.")) }
                    return
                }
                dets = a.dets
                o.put("table", JSONObject().put("x", a.tx).put("y", a.ty).put("w", a.tw).put("h", a.th))
                o.put("rad", a.rad.toDouble())
                o.put("pc", a.pc.toDouble())
                o.put("ps", a.ps.toDouble())
            } else {
                dets = BallDetector.detect(bmp, scanX, scanY, scanW, scanH, scanRad, scanSens)
                bmp.recycle()
            }
            val arr = JSONArray()
            for (d in dets) {
                arr.put(
                    JSONObject()
                        .put("x", d.x.toDouble())
                        .put("y", d.y.toDouble())
                        .put("c", "rgb(${d.r},${d.g},${d.b})")
                        .put("white", d.white)
                        .put("dark", d.dark)
                )
            }
            o.put("balls", arr)
            main.post { sendResult(o.toString()) }
        } catch (e: Exception) {
            main.post { sendResult(err("Error al analizar la imagen")) }
        } finally {
            img.close()
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun screenW(): Int = if (vdisplay != null) screenSize().first else 0

        @JavascriptInterface
        fun scan(x: Double, y: Double, w: Double, h: Double, rad: Double, sens: Int, auto: Boolean) {
            main.post { startScan(x.toInt(), y.toInt(), w.toInt(), h.toInt(), rad.toFloat(), sens, auto) }
        }
    }

    override fun onDestroy() {
        try { web?.let { wm.removeView(it); it.destroy() } } catch (_: Exception) {}
        try { bubble?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { vdisplay?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        try { bgThread?.quitSafely() } catch (_: Exception) {}
        web = null
        bubble = null
        vdisplay = null
        reader = null
        projection = null
        super.onDestroy()
    }
}
