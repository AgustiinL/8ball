# Guía 8 Ball (superposición)

Guía de líneas para 8 Ball Pool que se dibuja encima del juego. Funciona sin internet.

## Compilar el APK en GitHub
1. Crea un repositorio nuevo en GitHub y sube todo el contenido de esta carpeta (incluida `.github`).
2. Ve a la pestaña **Actions**. El workflow **Build APK** corre solo al subir; también puedes lanzarlo con **Run workflow**.
3. Cuando termine, abre la ejecución y descarga el artefacto **guia8ball-apk** (zip con `app-debug.apk`).
4. Instala el APK en el celular (permite "instalar apps desconocidas").

## Uso
1. Abre **Guía 8 Ball** → "Dar permiso de superposición" → activa el permiso.
2. "Iniciar guía" → acepta la captura de pantalla y elige **Toda la pantalla** (si eliges una sola app, no funciona la detección).
3. Abre el juego. Burbuja flotante:
   - Toque corto: alterna **✏️ Editar** (mover bolas, ajustar mesa, apuntar) y **🎮 Jugar** (los toques pasan al juego; solo se ven las líneas).
   - Arrastrar: mueve la burbuja.
   - Mantener pulsada (~1 s): cierra la guía.

## Detección automática
En modo Editar:
- **📸 Detectar todo**: toma una captura y detecta solo la mesa (por el color del paño), el tamaño de las bolas, el tamaño de las troneras y la posición de todas las bolas. La blanca queda como bola de tiro y la más oscura como la 8.
- **🎱 Solo bolas**: usa la mesa que tú calzaste a mano y solo detecta las bolas (útil si la detección de la mesa falla).
- **Sens**: sensibilidad de detección de bolas. Súbela si faltan bolas, bájala si aparecen de más.
- **Tronera** y **Bola**: deslizadores para corregir el tamaño a mano; **📐 Ajustar mesa** para mover/redimensionar el rectángulo.

Todo se calcula en el celular (visión por computador, sin internet).

Limitaciones: una bola de color casi igual al paño puede no detectarse; las bolas pegadas a una tronera se descartan; los números de las bolas no se leen.

## Personalizar
La guía es `app/src/main/assets/index.html`. El detector está en `BallDetector.kt`.
