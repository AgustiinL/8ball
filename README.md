# Guía 8 Ball (superposición)

Guía de líneas para 8 Ball Pool que se dibuja encima del juego. Funciona sin internet.

## Compilar el APK en GitHub
1. Crea un repositorio nuevo en GitHub y sube todo el contenido de esta carpeta (incluida `.github`).
2. Ve a la pestaña **Actions**. El workflow **Build APK** corre solo al subir; también puedes lanzarlo con **Run workflow**.
3. Cuando termine, abre la ejecución y descarga el artefacto **guia8ball-apk** (zip con `app-debug.apk`).
4. Instala el APK en el celular (permite "instalar apps desconocidas").

## Uso
1. Abre **Guía 8 Ball** → "Dar permiso de superposición" → activa el permiso.
2. "Iniciar guía" y abre el juego.
3. Burbuja flotante:
   - Toque corto: alterna **✏️ Editar** (puedes mover bolas, ajustar mesa, apuntar) y **🎮 Jugar** (los toques pasan al juego; solo se ven las líneas).
   - Arrastrar: mueve la burbuja.
   - Mantener pulsada (~1 s): cierra la guía.
4. En Editar, usa "📐 Ajustar mesa" para calzar el rectángulo con la mesa del juego, y el deslizador "Bola" para el tamaño de las bolas.

## Personalizar
La guía es `app/src/main/assets/index.html`. Edítala y vuelve a subir para recompilar.
