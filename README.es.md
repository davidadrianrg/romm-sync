<div align="center">

# 🎮 Atril — Game launcher con integración RomM

🌐 [English](README.md) · **Español**

**Un launcher de juegos para Android y Linux con integración [RomM](https://github.com/rommapp/romm).**

Atril convierte tus ROMs descargadas en un launcher con estética de medio físico: cartas coleccionables, arte real de cartucho o disco cuando RomM lo tiene y una animación de inserción antes de arrancar el juego. También explora tu biblioteca RomM, descarga ROMs en las carpetas de tu frontend y mantiene las partidas sincronizadas entre Android, Steam Deck y PC.

- **Android** (Kotlin + Jetpack Compose): APK nativo, optimizado para consolas portátiles (Anbernic, Retroid Pocket...) con pantalla táctil: UI oscura, botones grandes y navegación pensada para dedos.
- **Linux** (Compose Desktop): AppImage autocontenido para **x86_64** (SteamOS, Steam Deck, PCs) y **aarch64** (Raspberry Pi 5, handhelds ARM) con runtime Java incluido — nada que instalar.

</div>

---

## 📥 Instalación

### Opción A — Android (recomendado)

> **Atril 0.9.0 cambia el applicationId de Android a `com.davidadrianrg.atril`.**
> Se instala como una app nueva: si vienes de RomM Sync, descarga e instala este APK manualmente. La app antigua no puede autoactualizarse al paquete nuevo; configura Atril una vez y elimina la instalación antigua cuando quieras.

1. Descarga `Atril-vX.Y.Z-android.apk` desde [GitHub Releases](https://github.com/davidadrianrg/atril/releases/latest).
2. Configura tu servidor (ver abajo).
3. En **Configuración → Actualizaciones**, pulsa *Buscar actualizaciones* cada vez que quieras comprobar si hay versión nueva. La app descarga el APK y lanza el instalador de Android — sin salir de la aplicación.

> ⚠️ La primera vez, Android pedirá conceder a Atril el permiso **«Instalar apps desconocidas»**. Es el permiso estándar para auto-actualizarse cualquier app fuera de Play Store.

### Opción B — Linux (AppImage)

1. Descarga el AppImage de tu arquitectura desde [GitHub Releases](https://github.com/davidadrianrg/atril/releases/latest):
   - `Atril-vX.Y.Z-linux-x86_64.AppImage` — SteamOS, Steam Deck, la mayoría de PCs
   - `Atril-vX.Y.Z-linux-aarch64.AppImage` — Raspberry Pi 5, handhelds ARM
2. Dale permisos de ejecución y ejecútalo:
   ```bash
   chmod +x Atril-v*-linux-*.AppImage
   ./Atril-v*-linux-x86_64.AppImage
   ```
3. La primera vez pide URL del servidor, API Key y carpeta de ROMs; se guarda para la siguiente.

> El AppImage lleva el runtime Java jlinkeado dentro (~70 MB): no necesitas instalar Java ni nada más.

### Opción C — Compilar desde código

```bash
git clone https://github.com/davidadrianrg/atril.git
cd atril
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

Requisitos: JDK 17 y Android SDK (API 35). Para el desktop: solo JDK 17 (`./gradlew :desktop:packageReleaseUberJarForCurrentOS`). Para firmar tu propio release, crea `keystore.properties` en la raíz (está en `.gitignore`):

```properties
storeFile=/ruta/a/tu.keystore
storePassword=***
keyAlias=***
keyPassword=***
```

## ⚙️ Configuración inicial (2 minutos)

| Qué | Valor |
|---|---|
| **URL del servidor** | `https://romm.tudominio.com` (tu instancia RomM 4.9+ para sync) |
| **API Key** | Token `rmm_...` desde *RomM → User → API Keys* |
| **Directorio de ROMs** | `/storage/emulated/0/ROMs` (o tu microSD) |

La app pide el permiso **«Acceso a todos los archivos»** — lo necesita para escribir las ROMs en las carpetas que espera tu emulador/frontend, sin copiar ni mover nada manualmente.

<details>
<summary><b>¿Por qué API Key y no usuario/contraseña?</b></summary>

RomM admite login OAuth/OIDC tras proxys (Authentik, Authelia...), que suele romper clientes no-web con redirecciones y tokens CSRF. La API Key es un token largo por usuario que funciona siempre, sea cual sea tu setup de autenticación web.
</details>

## ✨ Qué hace la app

- **📚 Biblioteca** — Navega por tu servidor RomM con carátulas, búsqueda y filtros (todos / faltantes / descargados). Detecta juegos ya presentes en disco con *Escanear biblioteca*.
- **⬇️ Descargas** — Cola con descargas paralelas configurables (1–5), reanudación automática si se corta la red, verificación de integridad por hash y aviso de espacio insuficiente. Opción *solo WiFi* para no gastar datos.
- **🔔 Notificaciones de actualización** — comprueba GitHub al abrir la app y una vez al día; envía una notificación cuando hay una versión nueva de Atril.
- **💾 Sincronización de saves** — Tus partidas guardadas siempre al día en todos los dispositivos, con el [Device Sync Protocol](https://docs.romm.app/latest/developers/device-sync-protocol/) de RomM (v4.9+): sube lo que cambió en el portátil, bájate lo que jugaste en el PC. Resuelve conflictos explícitamente en vez de sobrescribir.
- **🗂️ Exportación de metadatos** — Opción genérica «Descargar media con los juegos» (covers, capturas, vídeo y manual en `<ROMs>/downloaded_media/<plataforma>/…` + un JSON de metadatos por juego, utilizable desde cualquier frontend) y exportación de `gamelist.xml` para ES-DE sobre esa misma media.
- **🔄 Auto-actualización** — Comprueba, descarga e instala nuevas versiones desde GitHub Releases sin desinstalar nada (ver Instalación).
- **🖥️ Linux a la par de Android** — La app de escritorio comparte tema ("Midnight Arcade"), biblioteca con carátulas y detalle de juego completo (screenshots, metadatos IGDB), cola de descargas con cancelar/reintentar/reanudar, sync de saves con resolución de conflictos y exportación ES-DE con merge no destructivo.

### Emuladores soportados para sync de saves

| Plataforma | Emulador (por defecto) |
|---|---|
| Retro (NES–N64, GBA, PSX...) | RetroArch |
| Nintendo DS | melonDS |
| PSP | PPSSPP |
| PS2 | AetherSX2 / NetherSX2 |
| GameCube / Wii | Dolphin |
| 3DS | Azahar |
| Wii U | Cemu |
| Switch | Eden |
| Juegos nativos Android | ruta configurable por juego |

> 💡 PS2 requiere activar **Folder Memory Card** en el emulador para sincronizar por juego.

## 🖼️ Permisos que usa y por qué

| Permiso | Motivo |
|---|---|
| Acceso a todos los archivos | Escribir ROMs/metadata en las carpetas que leen los emuladores |
| Instalar apps desconocidas | Solo si usas la auto-actualización: instala el APK que ella misma descarga |
| Notificaciones | Progreso de descargas y sincronización en segundo plano |
| Ignorar optimización de batería | Que la sincronización periódica no la mate el sistema |
| Internet | Hablar con tu servidor RomM y con GitHub (comprobar updates) |

Sin telemetría, sin analytics, sin cuentas: tus datos van de tu dispositivo a **tu** servidor.

## 🏗️ Arquitectura (para contribuir)

Monorepo con tres módulos Gradle que comparten todo el código de negocio:

```
core/src/main/kotlin/com/davidadrianrg/atril/core/   # JVM puro — compartido Android + Desktop
├── remote/              # Retrofit + OkHttp (API RomM, Device Sync Protocol)
├── sync/platform/       # Handlers de saves por emulador (RetroArch, melonDS, PS2, PS3, 3DS...)
├── download/            # PathMapper (estructura de carpetas por plataforma)
└── util/                # RootShell, lectura de cabeceras ROM...

app/src/main/java/com/davidadrianrg/atril/           # Android
├── data/local/          # Room + DataStore (config persistente)
├── data/repository/     # Puente remote ↔ cache
├── data/metadata/       # Exportación gamelist.xml / media
├── data/update/         # Auto-actualización desde GitHub Releases
├── download/            # WorkManager + descarga con reanudación
├── ui/                  # Compose Material3 (screens/viewmodel/components)
└── util/                # Permisos Android

desktop/src/main/kotlin/com/davidadrianrg/atril/desktop/  # Linux (Compose Desktop)
├── Main.kt / AppContent.kt   # UI (conexión, biblioteca, descargas, sync)
├── DesktopSyncCoordinator.kt # Ciclo de sync sin Room/WorkManager
├── DownloadEngine.kt         # Descarga stream→disco (fichero único + zip-stream)
└── DesktopConfig.kt          # java.util.prefs (config persistente)
```

- **Stack común**: Kotlin · Retrofit/OkHttp · Moshi · Coroutines
- **Android**: Jetpack Compose (Material 3) · Room · WorkManager · DataStore · Coil
- **Desktop**: Compose Desktop · AppImage (jlink runtime incluido)
- **Idioma del código**: inglés en identificadores/logs; español en textos de UI.
- **Tests**: `./gradlew test` (unit tests en `core/src/test` y `app/src/test`).

## 🔁 CI/CD

Las releases estables se construyen desde `master`:

1. **APK** (`.github/workflows/build-release.yml`): build firmado (R8) → GitHub Release como `Atril-vX.Y.Z-android.apk`.
2. **AppImage** (`.github/workflows/build-appimage.yml`): build para **x86_64** y **aarch64** → `Atril-vX.Y.Z-linux-<arch>.AppImage`, adjuntos a la misma release que el APK.

El desarrollo del día a día va en **`develop`**. Cada push allí ejecuta `.github/workflows/develop.yml` y actualiza una única release **draft** llamada `develop` con el APK y los dos AppImage. Las releases draft solo las ve el propietario/mantenedores del repo, así que son el canal de pruebas interno.

El tag y el `versionName` coinciden **exactamente** con `versionName` de `app/build.gradle.kts` (p. ej. `0.4.2` → `v0.4.2`); búmpalo ahí para publicar una versión nueva. El `versionCode` interno usa el `run_number` del workflow, que siempre crece (Android lo exige para actualizar sin desinstalar). Repetir una versión reemplaza su release.

El APK de release se firma con una clave fija (secrets `SIGNING_KEYSTORE_BASE64` y derivados) para que las actualizaciones se instalen sobre la versión anterior sin desinstalar. **Guarda copia del keystore**: si se pierde, los usuarios tendrían que desinstalar para actualizar.

## 🤝 Contribuir

1. Fork + rama (`feat/mi-cosa`).
2. `./gradlew test` en verde.
3. PR a `master` — CI debe pasar.

Errores, ideas y PRs bienvenidos en [Issues](https://github.com/davidadrianrg/atril/issues).

## 📄 Licencia

MIT
