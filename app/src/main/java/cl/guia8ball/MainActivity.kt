package cl.guia8ball

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val reqCapture = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Guía 8 Ball\n\n1) Da el permiso de superposición\n2) Inicia la guía y acepta la captura de pantalla " +
                "(elige \"Toda la pantalla\")\n3) Abre el juego\n\n" +
                "Burbuja: toque corto = alterna Editar / Jugar\n(Jugar deja pasar los toques al juego)\n" +
                "Mantén pulsada la burbuja para cerrar.\n\n" +
                "En Editar: 📸 Detectar bolas coloca las bolas de la mesa automáticamente."
            textSize = 16f
        }

        val bPerm = Button(this).apply {
            text = "1) Dar permiso de superposición"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
        }

        val bStart = Button(this).apply {
            text = "2) Iniciar guía"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Primero da el permiso de superposición", Toast.LENGTH_LONG).show()
                } else {
                    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    startActivityForResult(mpm.createScreenCaptureIntent(), reqCapture)
                }
            }
        }

        val bStop = Button(this).apply {
            text = "Detener guía"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
            }
        }

        root.addView(title)
        root.addView(bPerm)
        root.addView(bStart)
        root.addView(bStop)
        setContentView(root)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqCapture) {
            val i = Intent(this, OverlayService::class.java)
            if (resultCode == RESULT_OK && data != null) {
                i.putExtra("code", resultCode)
                i.putExtra("data", data)
                Toast.makeText(this, "Guía activa. Abre el juego.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Guía activa sin captura (no aceptaste el permiso)", Toast.LENGTH_LONG).show()
            }
            startForegroundService(i)
        }
    }
}
