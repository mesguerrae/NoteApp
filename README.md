# NoteApp

App Android (Kotlin) para:
1. Cargar páginas `.mhtml`/`.mht` empaquetadas en la app, en orden por nombre de archivo, y escribir notas sobre ellas (persistidas en SQLite interno vía Room).
2. Abrir cualquier PDF del dispositivo, hacer zoom (pellizco y doble toque), desplazarte, y escribir texto en cualquier posición de la hoja — también guardado en SQLite — con opción de exportar un PDF nuevo con las notas ya "aplanadas" sobre el contenido.

Compatible con Android 10 (minSdk 29, el que trae el Galaxy Tab S10 Lite) hasta Android 15 (compileSdk/targetSdk 35) y preparado para seguir funcionando en Android 16/17 sin cambios (las apps compiladas para una versión siguen instalándose y ejecutándose en versiones posteriores salvo que usen APIs retiradas, que aquí no se usan).

> **Para retomar este proyecto más adelante (o si eres una IA asistiendo en él):** lee `CLAUDE.md` primero — documenta decisiones de diseño importantes (en especial un problema de entorno con atributos XML de AndroidX/Material en esta máquina), convenciones del código, y el checklist para agregar migraciones de base de datos sin perder datos de usuarios.

## 1. Cómo cargar tus archivos MHTML antes de compilar

Copia tus archivos `.mhtml` (o `.mht`) dentro de:

```
app/src/main/assets/mhtml/
```

Nómbralos de forma que el orden alfabético/numérico sea el orden de lectura que quieres, por ejemplo:

```
pagina01.mhtml
pagina02.mhtml
pagina10.mhtml
```

La app los lista y abre exactamente en ese orden (ver `MhtmlAssetRepository.kt`). No hace falta tocar código para añadir/quitar páginas: solo copiar/borrar archivos en esa carpeta y volver a compilar.

## 2. Instalar el JDK requerido

El proyecto compila con **JDK 17** (ver `compileOptions`/`kotlinOptions` en `app/build.gradle.kts`). Instálalo con winget:

```powershell
winget install --id Microsoft.OpenJDK.17 -e --source winget
```

Se instala en `C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot` (la versión exacta puede variar). Opcionalmente, puedes fijarlo como `JAVA_HOME` de usuario (solo necesario si vas a compilar con `gradlew` fuera de Android Studio):

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot", "User")
```

Dentro de Android Studio **no es obligatorio usar ese JDK 17**: el IDE trae su propio runtime embebido (JetBrains Runtime) y lo pedirá en el primer *Sync* con un diálogo "Please Select Gradle JVM to Import Project". Ahí:

- Si te ofrece **"Use JVM 21"** (el embebido del IDE), acéptalo — es lo más simple y evita tener que descargar nada extra.
- Si en cambio aparece un error **"Downloading JDK… Failed to download JDK"** (por red/proxy), ve a `File > Settings > Build, Execution, Deployment > Build Tools > Gradle`, y en **Gradle JDK** elige "Add JDK…" apuntando a la ruta del JDK 17 instalado arriba. Luego vuelve a sincronizar (`File > Sync Project with Gradle Files`).

## 3. Abrir el proyecto en Android Studio

1. Instala **Android Studio Ladybug (2024.2) o más reciente**, que trae el SDK de Android 15 y soporta compileSdk 35.
2. `File > Open…` y selecciona esta carpeta (`APP`).
3. Este repositorio **no incluye el binario `gradle-wrapper.jar`** (no puede generarse como texto). Antes de abrirlo, genera el wrapper una sola vez con cualquiera de estas opciones:
   - Si tienes Gradle instalado en tu PC: abre una terminal en esta carpeta y ejecuta `gradle wrapper --gradle-version 8.9`.
   - O simplemente abre el proyecto en Android Studio: al hacer *Sync*, si falta el wrapper, Android Studio ofrece crear/descargarlo automáticamente ("Create Gradle Wrapper" / "Try Again").
4. Deja que Android Studio sincronice (descargará las dependencias de AndroidX, Room, etc. definidas en `app/build.gradle.kts`). Mientras sincroniza/indexa verás un proceso "Analyzing project to enable smart features" — es normal, solo espera a que termine.

## 4. Crear y usar el emulador (AVD)

El proyecto no trae un emulador configurado: los AVDs son una configuración de Android Studio, no parte del código. Para crear uno equivalente a la Galaxy Tab S10 Lite:

1. Abre **Tools > Device Manager** (o el ícono de teléfono en la barra lateral derecha).
2. **Add a new device > Create Virtual Device**.
3. En **Form Factor**, elige **Tablet** (la Tab S10 Lite es una tablet, no un teléfono).
4. Selecciona **Medium Tablet** (2560x1600) — es el perfil disponible más parecido a la pantalla de la Tab S10 Lite.
5. En la siguiente pantalla, descarga la imagen de sistema **API 35 (Android 15)**, que es con la que sale de fábrica el dispositivo real. Opcionalmente crea un segundo AVD con **API 29 (Android 10)** para probar el mínimo soportado (`minSdk 29`).
6. Finaliza la creación.

Para correr la app en ese emulador:

1. En la barra superior de Android Studio, junto al botón ▶ (Run), usa el **desplegable de dispositivos** y selecciona el AVD que creaste (si no aparece encendido, enciéndelo primero desde *Device Manager* con su botón ▶).
2. Verifica que el desplegable de configuración de ejecución tenga seleccionado el módulo **"app"**.
3. Pulsa **▶ Run 'app'** (o `Shift+F10`).
4. Android Studio compilará el proyecto, arrancará el emulador si no estaba encendido (la primera vez tarda más), instalará el APK y abrirá la app automáticamente.

Alternativa con dispositivo físico: activa **Opciones de desarrollador > Depuración USB** en tu Galaxy Tab S10 Lite, conéctala por USB, y selecciónala en ese mismo desplegable de dispositivos — suele ser más representativo que el emulador.

## 5. Qué hace cada pantalla

- **MainActivity**: pantalla principal con apariencia Material 3 y una **barra de navegación inferior** con dos secciones:
  - **Páginas**: lista en tarjetas las páginas MHTML empaquetadas, en orden, cada una abre el visor al tocarla (`fragments/PagesFragment.kt`).
  - **PDF**: tarjeta con botón para elegir un PDF del dispositivo y abrirlo en el lector propio (`fragments/PdfFragment.kt`).
- **MhtmlViewerActivity**: `ViewPager2` que muestra cada MHTML en un `WebView` (con zoom nativo del WebView). Toca cualquier punto para añadir una nota; toca una nota existente para editarla/borrarla; arrástrala para moverla. Todo se guarda en la tabla `mhtml_notes` de Room/SQLite, asociado al nombre de archivo.
- **PdfViewerActivity**: `ViewPager2` + `PdfRenderer` (API nativa de Android, sin dependencias externas) para mostrar cada página como mapa de bits dentro de una vista con zoom por pellizco/doble toque (`ZoomableImageView`). Las notas se dibujan sincronizadas con el zoom/paneo actual (misma matriz de transformación), se guardan en la tabla `pdf_notes` por documento+página, y el botón de la barra superior "Exportar PDF anotado" genera un PDF nuevo (no modifica el original) con las notas ya dibujadas sobre cada página, listo para compartir.

## 6. Base de datos interna

Room crea automáticamente `noteapp.db` (SQLite) en el almacenamiento privado de la app la primera vez que se ejecuta. No requiere configuración adicional. Ver `db/Entities.kt`, `db/Daos.kt`, `db/AppDatabase.kt`.

## 7. Limitaciones conocidas / posibles mejoras

- El zoom del visor MHTML usa el zoom nativo del `WebView`; las notas se ubican en proporción al tamaño de pantalla (no siguen el desplazamiento interno del WebView al hacer zoom). El visor de PDF sí sincroniza notas con el zoom/paneo exacto de la página.
- La exportación de PDF dibuja el texto de la nota en una sola línea; si necesitas notas multilínea más elaboradas o un selector de color/fuente, es un buen siguiente paso sobre `PdfRepository.exportWithNotes`.
- No se han añadido íconos PNG personalizados: se generó un icono vectorial simple (`drawable/ic_launcher_foreground.xml`) que puedes reemplazar desde Android Studio (`File > New > Image Asset`).
