# CLAUDE.md — NoteApp

Referencia para quien (humano o IA) retome este proyecto. Resume qué se construyó, por qué,
y las convenciones a seguir para no romper nada al seguir agregando funciones.

## 1. Qué es esta app

App Android (Kotlin, sin Jetpack Compose) para:
1. Leer páginas `.mhtml`/`.mht` empaquetadas dentro del propio APK, en el orden de su nombre
   de archivo, organizarlas en grupos colapsables, marcarlas como hechas/no hechas, renombrarlas,
   y escribir notas en cualquier posición sobre cada página.
2. Abrir cualquier PDF del dispositivo, hacer zoom/pan, escribir notas en cualquier posición de
   la hoja, y exportar un PDF nuevo con esas notas ya dibujadas encima.
3. Una libreta de "notas generales" (no ligadas a ninguna página/documento), accesible como
   popup desde un botón "+" en cualquier pantalla principal.
4. Tema claro/oscuro con persistencia, y la app bloqueada en orientación vertical.

Todo el contenido que el usuario escribe (notas, nombres, grupos, estado hecho/no-hecho,
notas generales) vive en **SQLite interno** (Room), nunca en memoria ni en archivos sueltos.
Sobrevive a reinicios del dispositivo y cierres de la app; solo se pierde si el usuario
desinstala la app o borra sus datos desde Ajustes.

Compatible desde Android 10 (`minSdk 29`) hasta Android 15 (`compileSdk`/`targetSdk 35`), y
debería seguir funcionando sin cambios en Android 16/17.

## 2. Estructura del proyecto

```
app/src/main/java/com/mee/noteapp/
├── db/                         — Room: entidades, DAOs, base de datos, migraciones
│   ├── Entities.kt
│   ├── Daos.kt
│   ├── AppDatabase.kt
│   └── Migrations.kt
├── mhtml/
│   └── MhtmlAssetRepository.kt — lista app/src/main/assets/mhtml/*.mhtml en orden natural
├── pdf/
│   └── PdfRepository.kt        — PdfRenderer (render de páginas) + export con notas dibujadas
└── ui/
    ├── MainActivity.kt         — pantalla raíz: barra inferior Páginas/PDF + menú superior
    ├── MhtmlViewerActivity.kt  — visor de páginas MHTML (ViewPager2 + WebView + overlay)
    ├── PdfViewerActivity.kt    — visor de PDF (ViewPager2 + PdfRenderer + overlay con zoom)
    ├── AnnotationOverlayView.kt— capa transparente de notas: tocar/arrastrar/redimensionar
    ├── ZoomableImageView.kt    — ImageView con pinch-zoom/pan vía Matrix (usado por el PDF)
    ├── ThemePrefs.kt           — persistencia y aplicación del modo claro/oscuro
    ├── GeneralNotesDialog.kt   — popup de notas generales, compartido entre activities
    ├── NoteDialog.kt           — popup "escribir nota" con selector de estilo, compartido
    └── fragments/
        ├── PagesFragment.kt    — lista de páginas MHTML: grupos, renombrar, marcar hecho
        └── PdfFragment.kt      — botón para elegir un PDF del dispositivo
```

Capas de assets/recursos clave:
- `app/src/main/assets/mhtml/` — carpeta donde se colocan los `.mhtml` ANTES de compilar.
- `app/src/main/res/drawable/ic_*.xml` — iconos vectoriales simples, dibujados a mano (ver §4).
- `app/src/main/res/values(-night)/colors.xml` — paleta clara/oscura.

## 3. Decisiones de diseño importantes (y el porqué)

### 3.1 Cero atributos XML personalizados de AndroidX/Material

**Esto es la decisión más importante de todo el proyecto.** La instalación de Android SDK/AGP
en la máquina de desarrollo original tiene un bug de entorno (confirmado con un proyecto Android
mínimo de prueba, totalmente aparte de este) donde **ningún atributo `app:` definido por una
librería externa (Material Components, CoordinatorLayout, incluso atributos básicos de
AppCompat) logra enlazarse** en el paso de `aapt2 link`, aunque las dependencias se resuelvan
bien y estén presentes en el classpath. El error típico:

```
error: attribute auto:cardCornerRadius not found.
```

Se investigó a fondo (caché de Gradle, versión de AGP, JDK, rutas largas de Windows, etc.) y
se descartó todo lo demás: hasta un proyecto Android nuevo y mínimo con un solo `MaterialCardView`
falla igual en esa máquina. Por eso:

- **No se usa `com.google.android.material` ni `androidx.coordinatorlayout` como dependencias.**
- Toda la UI "moderna" (tarjetas redondeadas, barra inferior, botones con esquinas redondeadas,
  iconos con tinte) está hecha con **widgets 100% framework** (`LinearLayout`, `FrameLayout`,
  `ImageButton`, `Button`, drawables `<shape>` para fondos redondeados) usando solo atributos
  `android:` — nunca `app:`.
- Si en el futuro se retoma el proyecto en OTRA máquina donde esa librería sí enlaza bien, se
  podría volver a introducir Material Components, pero no es necesario: el resultado visual ya
  es moderno sin ella.
- Si aparece de nuevo un error "attribute auto:X not found" en cualquier librería, sospecha
  primero de este mismo problema de entorno antes de tocar el código del proyecto.

### 3.2 Toolchain de compilación en esta máquina

- JDK: Microsoft Build of OpenJDK 17 (`winget install --id Microsoft.OpenJDK.17`), requerido
  porque AGP 8.x necesita JDK 17 y Gradle no soporta JDK 25+ (el que trae por defecto esta PC).
- AGP: **8.7.3** (no 8.5.2 — esa versión solo estaba probada hasta `compileSdk 34` y generaba
  warnings, aunque el bug real de §3.1 es independiente de la versión de AGP).
- Gradle: 8.9. El repo no incluye `gradle-wrapper.jar` (no se puede generar como texto); se
  descargó una distribución de Gradle aparte a `C:\tools\gradle-8.9` para compilar/depurar
  desde terminal sin depender de Android Studio. Si no existe esa carpeta en tu máquina,
  Android Studio regenera el wrapper automáticamente al sincronizar.
- El proyecto vive en `C:\APP` (fuera de OneDrive) — se movió ahí porque OneDrive puede
  interferir con la compilación (bloqueo de archivos mientras Gradle escribe en `build/`),
  aunque en este caso el problema real resultó ser el de §3.1, no OneDrive.

### 3.3 Base de datos: migraciones explícitas, no destructivas

Desde la v5 del esquema, `AppDatabase` usa **migraciones explícitas** (`Migrations.kt`) en vez
de `fallbackToDestructiveMigration()`. Esto es crítico: actualizar la app a una versión nueva
del APK **nunca debe borrar las notas del usuario**. Checklist completo para agregar un cambio
de esquema está al final de `Migrations.kt`; resumen:

1. Cambia la entidad en `Entities.kt`. Si agregas una columna NOT NULL a una tabla existente,
   dale un valor por defecto en Kotlin **y** un `@ColumnInfo(defaultValue = "...")` que
   coincida exactamente con el `DEFAULT` que vas a usar en el `ALTER TABLE` (ver ejemplos de
   `style`/`sizeScale` en `MhtmlNote`/`PdfNote`).
2. Sube `version` en `@Database` (`AppDatabase.kt`).
3. Agrega un nuevo `MIGRATION_x_y` en `Migrations.kt` con el SQL correspondiente.
4. Agrégalo al array `ALL_MIGRATIONS`.
5. Prueba instalando la versión VIEJA primero y actualizando encima (no desinstalar) para
   confirmar que la migración corre sin crashear sobre datos reales.

Solo se usa `fallbackToDestructiveMigrationOnDowngrade()` (borra solo si alguien instala una
versión más VIEJA sobre una base de datos más nueva — un caso raro de pruebas, no de uso real).

**Salvaguarda contra bases de datos de prueba inconsistentes:** `AppDatabase.get()` abre la
base de datos inmediatamente al crearla (`db.openHelper.writableDatabase`) para forzar la
validación de esquema de Room en ese momento, en vez de que reviente más tarde en cualquier
pantalla. Si esa validación falla con `IllegalStateException` ("Expected identity hash... found..."),
se borra el archivo de base de datos y se recrea desde cero — esto SOLO debería dispararse con
bases de datos de desarrollo/prueba que quedaron en un estado inconsistente (por ejemplo, una
instalación vieja que llegó a la versión 5 por `fallbackToDestructiveMigration` ANTES de que
existieran los `@ColumnInfo(defaultValue=...)`, cuyo esquema físico no tiene los `DEFAULT` que
las entidades actuales esperan). Un usuario real que pase por `ALL_MIGRATIONS` en secuencia
desde una versión consistente nunca debería disparar este camino. Si ves este log
(`Room schema validation failed, recreating the database from scratch`) en producción con
usuarios reales, es señal de que alguna migración no quedó bien escrita — revisa el esquema
real (`adb exec-out run-as <pkg> cat /data/data/<pkg>/databases/noteapp.db` + `sqlite3 ... "SELECT sql FROM sqlite_master"`)
contra lo que las entidades esperan.

### 3.4 Notas sobre páginas: posición, estilo y tamaño

Cada nota (`MhtmlNote`/`PdfNote`) guarda:
- `xPercent`/`yPercent`: posición como fracción (0f..1f) de la página, no de la pantalla —
  así se mantiene correcta sin importar el tamaño de pantalla o el zoom.
- `style`: `"general"` (amarillo) o `"important"` (rojo), elegido en el diálogo al crear/editar.
- `sizeScale`: factor de tamaño manual (1f = normal), ajustado arrastrando el asa circular en
  la esquina inferior derecha de la nota (`AnnotationOverlayView`). El texto escala junto con
  la caja.

El tamaño visual final de una nota es `tamañoBase × sizeScale × zoomActual` — dos factores de
escala independientes: uno que el usuario fija a mano (persistente) y otro automático según el
zoom de la página en ese momento (para que la nota no se vea minúscula o gigante al hacer zoom).

`AnnotationOverlayView` es compartido entre el visor MHTML y el de PDF; distingue el "espacio
de página" vía `pageMatrix`+`pageSize` (PDF, zoom por Matrix) o `externalScaleProvider` (MHTML,
zoom nativo del `WebView`, leído con `webView.scale`).

### 3.5 Grupos de páginas

`PageGroup` (nombre + `collapsed`) y `PageMeta.groupId` (nullable) implementan la agrupación.
`PagesFragment` arma una lista plana de `Row` (encabezados de grupo + páginas) cada vez que
cambian los datos observados (`combine` de los dos `Flow` de Room). El estado colapsado/expandido
se persiste en SQLite, no en memoria, para que sobreviva a cerrar la app.

### 3.6 Orientación fija

Las tres activities tienen `android:screenOrientation="portrait"` en el `AndroidManifest.xml`
— la app no rota aunque el dispositivo (una tablet) se gire.

### 3.7 Progreso visual: colores por página y porcentajes

- `item_file.xml` (cada fila de página) cambia de color de fondo según `PageMeta.completed`:
  verde (`page_done_bg`/`page_done_accent`) si está hecha, amarillo (`page_pending_bg`/
  `page_pending_accent`) si no — ver `PagesFragment.FileHolder.bind()`, que tiñe el
  `GradientDrawable` de `bg_card` en tiempo de ejecución (`itemView.background.mutate()`).
  Ambos colores tienen variante en `values-night/colors.xml`.
- Cada **encabezado de grupo** (`item_group_header.xml`) muestra el % de páginas completadas
  de ese grupo (`txtGroupProgress`), recalculado en `GroupHolder.bind()` cada vez que cambian
  los datos observados.
- El título de la pestaña de páginas (antes "Páginas MHTML", ahora **"Libro"** —
  `R.string.tab_mhtml`, el nombre del string no cambió para no romper referencias) muestra
  debajo el **% global** de completitud sobre TODAS las páginas del libro
  (`txtGlobalProgress`, calculado en `PagesFragment.updateGlobalProgress()`).

### 3.8 Notas generales: editables, con fecha de creación y origen

- `GeneralNote` tiene `createdAt` (inmutable, puesto una sola vez al insertar) separado de
  `updatedAt` (se actualiza en cada edición) — igual que `MhtmlNote`/`PdfNote` desde la v6.
  Nunca reutilices `updatedAt` para mostrar "fecha de creación": edítalo y se movería.
- Tocar el texto de una nota general (`GeneralNotesDialog.kt` → `GeneralNoteHolder`) abre un
  diálogo de edición simple (un solo `EditText`); guardar con texto vacío la borra, igual que
  las notas de página.
- `GeneralNote.sourcePage` (nullable) registra desde qué página/PDF se creó la nota, SOLO
  cuando el popup se abre desde el botón "+" de un visor (`MhtmlViewerActivity`/
  `PdfViewerActivity` pasan el nombre de la página/documento actual a
  `showGeneralNotesDialog(activity, sourcePage)`); si se abre desde `MainActivity` (sin
  ninguna página abierta) queda `null`. Se muestra junto a la fecha
  (`R.string.note_created_at_from`) solo si no es nulo/vacío.
- Las notas de página (`MhtmlNote`/`PdfNote`) muestran su fecha de creación en el diálogo de
  edición (`showNoteStyleDialog` en `NoteDialog.kt`, parámetro `createdAt: Long?` — `null`
  para una nota nueva, oculta el campo).

## 4. Convenciones a seguir

- **Nunca** agregues un atributo `app:` de una librería externa en un layout XML sin antes
  verificar que compila en esta máquina (ver §3.1). Si necesitas un color/forma personalizada,
  usa un `drawable/*.xml` tipo `<shape>` o un `<vector>` simple con `android:fillColor`.
- Los iconos (`ic_*.xml`) son vectores dibujados a mano con paths simples — no usar
  `androidx.vectordrawable` más allá de lo que trae el framework por defecto.
- Toda persistencia de datos de usuario va a Room (`db/`), nunca a `SharedPreferences` —
  excepto `ThemePrefs` (modo claro/oscuro), que es una preferencia de UI pura, no un dato
  de contenido del usuario.
- Diálogos y popups reutilizables (notas generales, diálogo de nota con estilo) viven como
  funciones top-level en `ui/` (`GeneralNotesDialog.kt`, `NoteDialog.kt`) para no duplicar
  código entre `MainActivity`, `MhtmlViewerActivity` y `PdfViewerActivity`.
- Strings de UI van en `res/values/strings.xml` (español) — no hay otros idiomas configurados.
- Antes de dar por buena una compilación, verifícala realmente (`gradle :app:assembleDebug`),
  no asumas que un cambio de layout/Kotlin compila solo porque "se ve bien".

## 5. Cómo compilar / ejecutar

Ver `README.md` para el paso a paso completo (instalar JDK, abrir en Android Studio, crear el
emulador tipo tablet, correr la app). Resumen rápido si ya tienes todo instalado:

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
cd C:\APP
& "C:\tools\gradle-8.9\bin\gradle.bat" :app:assembleDebug
```

## 6. Historial de funciones agregadas (orden cronológico resumido)

1. Proyecto base: visor de páginas MHTML empaquetadas + lector de PDF con notas en SQLite.
2. Rediseño visual "moderno": barra de navegación inferior, tarjetas — luego reescrito sin
   atributos de librería por el bug de §3.1.
3. Navegador de páginas (anterior/siguiente + indicador) y botones de zoom +/− en ambos visores.
4. Notas que escalan su tamaño (caja y texto) según el zoom de la página, con límites legibles.
5. Renombrar páginas (se refleja en la lista y en el título del visor), marcar página
   hecha/no-hecha, popup de notas generales, modo claro/oscuro, orientación vertical forzada.
6. Agrupar páginas en grupos con nombre, colapsables (estado persistido), botón "+" para
   agregar una nota general desde el menú de cada visor.
7. Selector de estilo de nota (general / muy importante en rojo) al crear o editar una nota,
   reflejado también en el PDF exportado.
8. Redimensionar notas a mano arrastrando un asa en su esquina (el texto escala junto con la
   caja), persistido en SQLite.
9. Migraciones explícitas de Room (en vez de borrado destructivo) para no perder datos del
   usuario al actualizar la app, más una salvaguarda que recrea la base de datos solo si la
   validación de esquema de Room falla de forma inesperada (ver §3.3).
10. Páginas en verde/amarillo según completadas o no; porcentaje de completitud por grupo y
    global (título renombrado a "Libro") — ver §3.7.
11. Notas generales editables, con fecha de creación propia (no se mueve al editar) y, si se
    crearon desde el botón "+" de un visor, el nombre de la página/PDF de origen — ver §3.8.
    Esto fue la migración v6 (`createdAt` en las 3 tablas de notas) y v7 (`sourcePage` en
    `general_notes`); la base de datos va en versión 7.

Mantén este archivo al día: cada vez que agregues una función no trivial, añade una línea al
historial y, si introduce una convención o decisión de diseño nueva, documéntala en la §3.
