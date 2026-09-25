<div align="center">

# 🎮 RomM Sync — Android & Linux

**Descarga y sincroniza tu biblioteca de [RomM](https://github.com/rommapp/romm) en Android y Linux.**

Cliente multiplataforma para servidores RomM: explora tu biblioteca, descarga ROMs directamente a la estructura de carpetas de tu frontend favorito y mantiene las partidas guardadas sincronizadas entre todos tus dispositivos — portátil Android, Steam Deck, PC.

- **Android** (Kotlin + Jetpack Compose): APK nativo, optimizado para consolas portátiles (Anbernic, Retroid Pocket...) con pantalla táctil: UI oscura, botones grandes y navegación pensada para dedos.
- **Linux** (Compose Desktop): AppImage autocontenido para **x86_64** (SteamOS, Steam Deck, PCs) y **aarch64** (Raspberry Pi 5, handhelds ARM) con runtime Java incluido — nada que instalar.

</div>

---

## 📥 Instalación

### Opción A — Android (recomendado)

1. Descarga `RomM-Sync-vX.Y.Z-android.apk` desde [GitHub Releases](https://github.com/davidadrianrg/romm-sync/releases/latest).
2. Configura tu servidor (ver abajo).
3. En **Configuración → Actualizaciones**, pulsa *Buscar actualizaciones* cada vez que quieras comprobar si hay versión nueva. La app descarga el APK y lanza el instalador de Android — sin salir de la aplicación.

> ⚠️ La primera vez, Android pedirá conceder a RomM Sync el permiso **«Instalar apps desconocidas»**. Es el permiso estándar para auto-actualizarse cualquier app fuera de Play Store.

### Opción B — Linux (AppImage)

1. Descarga el AppImage de tu arquitectura desde [GitHub Releases](https://github.com/davidadrianrg/romm-sync/releases/latest):
   - `RomM-Sync-vX.Y.Z-linux-x86_64.AppImage` — SteamOS, Steam Deck, la mayoría de PCs
   - `RomM-Sync-vX.Y.Z-linux-aarch64.AppImage` — Raspberry Pi 5, handhelds ARM
2. Dale permisos de ejecución y ejecútalo:
   ```bash
   chmod +x RomM-Sync-v*-linux-*.AppImage
   ./RomM-Sync-v*-linux-x86_64.AppImage
   ```
3. La primera vez pide URL del servidor, API Key y carpeta de ROMs; se guarda para la siguiente.

> El AppImage lleva el runtime Java jlinkeado dentro (~70 MB): no necesitas instalar Java ni nada más.

### Opción C — Compilar desde código

```bash
git clone https://github.com/davidadrianrg/romm-sync.git
cd romm-sync
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
- **💾 Sincronización de saves** — Tus partidas guardadas siempre al día en todos los dispositivos, con el [Device Sync Protocol](https://docs.romm.app/latest/developers/device-sync-protocol/) de RomM (v4.9+): sube lo que cambió en el portátil, bájate lo que jugaste en el PC. Resuelve conflictos explícitamente en vez de sobrescribir.
- **🗂️ Exportación de metadatos** — Genera los `gamelist.xml` de ES-DE o copia la media (covers, fanart, logos, screenshots) a la estructura de RetroHRAI.
- **🔄 Auto-actualización** — Comprueba, descarga e instala nuevas versiones desde GitHub Releases sin desinstalar nada (ver Instalación).

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
core/src/main/kotlin/es/davidrg/rommsync/core/   # JVM puro — compartido Android + Desktop
├── remote/              # Retrofit + OkHttp (API RomM, Device Sync Protocol)
├── sync/platform/       # Handlers de saves por emulador (RetroArch, melonDS, PS2, PS3, 3DS...)
├── download/            # PathMapper (estructura de carpetas por plataforma)
└── util/                # RootShell, lectura de cabeceras ROM...

app/src/main/java/es/davidrg/rommsync/           # Android
├── data/local/          # Room + DataStore (config persistente)
├── data/repository/     # Puente remote ↔ cache
├── data/metadata/       # Exportación gamelist.xml / media
├── data/update/         # Auto-actualización desde GitHub Releases
├── download/            # WorkManager + descarga con reanudación
├── ui/                  # Compose Material3 (screens/viewmodel/components)
└── util/                # Permisos Android

desktop/src/main/kotlin/es/davidrg/rommsync/desktop/  # Linux (Compose Desktop)
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

Cada push a `master`:

1. **APK** (`.github/workflows/build-release.yml`): build firmado (R8) → GitHub Release como `RomM-Sync-vX.Y.Z-android.apk`.
2. **AppImage** (`.github/workflows/build-appimage.yml`): build para **x86_64** y **aarch64** → `RomM-Sync-vX.Y.Z-linux-<arch>.AppImage`, adjuntos a la misma release que el APK.

El tag y el `versionName` coinciden **exactamente** con `versionName` de `app/build.gradle.kts` (p. ej. `0.4.2` → `v0.4.2`); búmpalo ahí para publicar una versión nueva. El `versionCode` interno usa el `run_number` del workflow, que siempre crece (Android lo exige para actualizar sin desinstalar). Repetir una versión reemplaza su release.

El APK de release se firma con una clave fija (secrets `SIGNING_KEYSTORE_BASE64` y derivados) para que las actualizaciones se instalen sobre la versión anterior sin desinstalar. **Guarda copia del keystore**: si se pierde, los usuarios tendrían que desinstalar para actualizar.

## 🤝 Contribuir

1. Fork + rama (`feat/mi-cosa`).
2. `./gradlew test` en verde.
3. PR a `master` — CI debe pasar.

Errores, ideas y PRs bienvenidos en [Issues](https://github.com/davidadrianrg/romm-sync/issues).

## 📄 Licencia

MIT
