# Launcher: inmersivo, carta / medio físico y lanzamiento de emuladores

Estado: implementado en `feat/launcher-physical-media` (sin commit). Este documento
recoge los hallazgos, las decisiones, los riesgos y el plan de ficheros.

## 1. Hallazgos

### App actual
- `PlayScreen` pinta siempre un objeto "físico" falso (cartucho/jewel case dibujado
  alrededor de la carátula). El toque abre `RomDetailSheet`; no lanza nada.
- `RomMSyncApp` alterna entre un `Row` con `NavigationRail` (horizontal) y un
  `Scaffold` con `NavigationBar` (vertical). Ambas barras son permanentes.
- `DownloadedRomEntity.localPath` es una ruta absoluta (`/storage/...`). Ojo:
  - Juegos marcados a mano: `localPath == ""` → no lanzables.
  - ROMs multiarchivo descargadas como *zip-stream* (mod_zip) se extraen en la
    carpeta de la plataforma y `localPath` apunta al `.zip` teórico, que **no existe**.
    Hace falta un resolvedor de fichero lanzable.
- `insertDownloadedRom` es `REPLACE`: al re-descargar, `DownloadWorker` copia a mano las
  columnas por juego. Guardar la preferencia de presentación en esa tabla obligaría a
  tocar todos los inserts → se usa una tabla aparte.
- El `FileProvider` solo exponía `cacheDir` (APKs de actualización).
- `SaveHandlerRegistry.EmulatorId` es el emulador *de saves* por plataforma
  (`PlatformEntity.emulatorId`). Reutilizamos esos ids para que el emulador que se lanza
  sea el mismo cuyos saves se sincronizan.

### RomM (medio físico)
- `GET /api/roms` devuelve `ss_metadata.physical_path` y `gamelist_metadata.physical_path`
  (ruta relativa a resources) servida en `<server>/assets/romm/resources/<path>`.
- `physical_url` apunta a ScreenScraper: **no se usa** (el servidor es la fuente de verdad).
- `physical_disc` / `physical_extra_discs` se modelan como `Any?`/`List<Any>` para no
  romper el parseo si RomM cambia el tipo; la fase 1 solo usa `physical_path`.
- `is_physical` (juego sin fichero) no tiene relación y no se toca.

### Argosy (referencia de intents)
Fuente: `argosy-launcher/.../data/emulator/EmulatorRegistry.kt`, `GameLauncher.kt`,
`LaunchCommand.kt`.

| Familia | Paquetes (orden de fallback) | Acción / actividad | ROM |
|---|---|---|---|
| RetroArch | `com.retroarch`, `com.retroarch.aarch64`, `com.retroarch.ra32` | MAIN, `com.retroarch.browser.retroactivity.RetroActivityFuture` | extras `ROM`, `LIBRETRO`=`/data/data/<pkg>/cores/<core>_libretro_android.so`, `CONFIGFILE`, `IME`, `DATADIR`, `SDCARD`, `EXTERNAL` + grant content URI en clipData |
| Mupen64Plus FZ | `org.mupen64plusae.v3.fzurita`, `.pro` | VIEW, `paulscode.android.mupen64plusae.SplashActivity` | data = content URI |
| ARMSX2 | `com.armsx2` (`com.armsx2.MainActivity`), `come.nanodata.armsx2` (`kr.co.iefriends.pcsx2.activities.MainActivity`) | VIEW | data = content URI |
| ARMSX3 | `com.armsx3`, `com.armsx3.play` | MAIN, `com.armsx2.BootSplashActivity` | extra `path` = ruta absoluta |
| Dolphin | `org.dolphinemu.dolphinemu`, `org.dolphinemu.handheld` | MAIN, `org.dolphinemu.dolphinemu.ui.main.MainActivity` | extra `AutoStartFile` = content URI (Uri) |
| Eden | `dev.eden.eden_emulator` | VIEW, `org.yuzu.yuzu_emu.activities.EmulationActivity` | data = content URI |
| XenDroid+ | `xendroid.compose.fork` (+ `xendroid.compose` como fallback) | `xendroid.intent.action.xendroid`, `xendroid.compose.EmulatorHostActivity` | extra `game_uri` = ruta absoluta |
| hakuX | `com.rfandango.haku_x` | VIEW sin actividad (config por defecto de Argosy, sin verificar) | data = content URI |
| melonDS | `me.magnum.melonds` | `me.magnum.melonds.LAUNCH_ROM`, `...ui.emulator.EmulatorActivity` | extra `uri` (Uri) + data |
| PPSSPP | `org.ppsspp.ppsspp`, `org.ppsspp.ppssppgold` | VIEW, `org.ppsspp.ppsspp.PpssppActivity` | data |
| Azahar | `org.azahar_emu.azahar` | VIEW, `org.citra.citra_emu.activities.EmulationActivity` | data + extra `SelectedGame` |
| Cemu | `info.cemu.cemu` | VIEW, `info.cemu.cemu.emulation.EmulationActivity` | data |

Notas:
- El brief indicaba `com.armsx2` / `com.armsx2.MainActivity` para ARMSX2: coincide con la
  entrada `armsx2_refresh` de Argosy. La de Play Store (`come.nanodata.armsx2`) usa otra
  actividad; se incluyen ambas.
- Argosy prefiere `am start` (SHELL) para Dolphin/Eden/ARMSX2, pero **cae a Intent en
  cuanto hay un `content://`**, que es justo nuestro caso. Aquí se usa siempre
  `startActivity` con grants explícitos; no se implementa el modo shell.
- hakuX: Argosy no tiene configuración propia y no pude consultar su código. Se usa
  VIEW + content URI y, antes de lanzar, se comprueba con `PackageManager` que alguna
  actividad resuelve el intent; si no, se muestra un error claro.
- **DroidDeck** es un frontend de Steam/PC (Argosy lo trata como "frontend sync"), no un
  emulador de ROMs: queda **fuera de alcance** y no se mapea a ninguna plataforma.

## 2. Decisiones

### Datos
- `RomDto` gana `ss_metadata` y `gamelist_metadata` (`PhysicalMetadataDto`). Helper
  `RomDto.physicalMediaPath` (ss → gamelist) y `PhysicalMedia.resourceUrl(server, path)`
  en `:core`.
- `Rom.physicalMediaUrl` (dominio) se rellena en `RomRepository` y `ShelfViewModel`.
- **Persistencia**: tabla Room nueva `rom_presentation(romId PK, displayMode, physicalMediaPath, physicalMediaSource, updatedAt)`,
  migración 7→8 (`CREATE TABLE`). Motivo: reactiva (Flow), sobrevive a re-descargas
  (`REPLACE` en `downloaded_roms`) y no obliga a tocar los inserts existentes.
  `displayMode = null` → usa el valor global.
- **Valor global**: DataStore `launcher_default_display` (`card` | `physical`, por defecto
  `card`), con un interruptor en Ajustes.
- **Override de emulador por plataforma (lanzamiento)**: DataStore
  `launch_emulator_overrides` (`slug=familia;…`). Se elige desde el detalle del juego.
  Sin override, se usa el emulador de saves de la plataforma si es lanzable y, si no,
  RetroArch.
- **Caché**: `filesDir/physical-media/<romId>.<ext>`, descarga con OkHttp + API key,
  escritura a `.part` y `rename` atómico. Al borrar un juego del launcher se borran la fila
  y la imagen.
- Modo efectivo = preferencia del juego ?: global; si es `physical` pero no hay imagen
  cacheada → carta (nunca se dibuja un cartucho falso).

### Lanzamiento
- `:core` `es.davidrg.rommsync.core.launch`:
  - `LaunchPlatforms`: normaliza slugs de RomM/IGDB (`dc`, `ngc`, `genesis-slash-megadrive`…).
  - `EmulatorCatalog`: familias, variantes de paquete y cores libretro (de Argosy).
  - `EmulatorSelector`: candidatos ordenados (override → emulador de saves → defecto de la
    plataforma → RetroArch) y selección del primer paquete instalado.
  - `LaunchSpecBuilder`: descripción pura del intent (`LaunchSpec`), con placeholders para
    el content URI.
  - `LaunchFileResolver`: fichero lanzable (fichero, carpeta, `.m3u` existente, ficheros
    extraídos de un zip-stream, prioridad por extensión).
- `:app` `es.davidrg.rommsync.launch.EmulatorLauncher`: comprueba paquetes instalados
  (`<queries>` en el manifest), crea el content URI con `FileProvider`, concede permisos,
  arma el `Intent` y llama a `startActivity`. Errores → mensaje traducido.
- `FileProvider`: se añaden `external-path` (almacenamiento principal) y
  `root-path path="storage"` (microSD en `/storage/XXXX-XXXX`). No se expone `/data`. El
  provider sigue sin exportarse: solo se puede leer lo que se concede URI a URI.

### UI
- `RomMSyncApp`: un único árbol (rail + contenido + barra inferior). En la ruta Play el
  rail y la barra se ocultan con animación y el contenido no recibe padding de insets:
  el launcher pinta a sangre y gestiona sus propios insets.
- `PlayScreen`:
  - Cabecera flotante: marca, contador, descargas y menú.
  - Menú overlay (`LauncherMenuOverlay`): scrim + panel que entra deslizando (desde la
    izquierda en horizontal, desde abajo en vertical, al alcance del pulgar). Se abre con
    el botón o arrastrando desde el borde izquierdo; se cierra tocando fuera o con Atrás.
  - Carta por defecto (`GameCard`): carátula, degradado, título, plataforma, insignias
    (multidisco, manual), borde ámbar fino y sombra.
  - Medio físico: la imagen real de RomM con halo y sombra de suelo. Transición
    carta↔físico con `AnimatedContent` (fundido + escala).
  - Botón **Jugar** en el pie; en modo físico reproduce la animación de inserción
    (el objeto baja hacia una ranura, se inclina/gira, la ranura brilla) y luego lanza.
    Cancelable tocando o con Atrás; el overlay se limpia en `finally`.
  - Errores (sin ROM local, sin emulador instalado, intent no resuelto) → snackbar.
- `RomDetailSheet`: parámetro opcional `launcherControls` con: botón Jugar, selector
  Carta/Medio físico, estado de la caché, botón "Descargar medio físico" o explicación de
  que RomM no tiene medio físico, y selector de emulador por plataforma.

## 3. Riesgos
- Intents de hakuX y del fallback XenDroid original sin verificar → capa de error explícita.
- Cores de RetroArch: no se puede comprobar si el core existe en `/data/data/<pkg>/cores`
  sin root; si falta, RetroArch mostrará su propio error.
- Algunos emuladores (Dolphin con `content://`) dependen de su versión para aceptar URIs.
- Package visibility: solo se ven los paquetes declarados en `<queries>`.
- Multidisco: se usa un `.m3u` existente; no se genera uno.

## 4. Plan de ficheros
- `core/.../remote/dto/RomDto.kt` — DTO físico.
- `core/.../library/PhysicalMedia.kt` — URL de recursos.
- `core/.../launch/*.kt` — catálogo, selector, spec, resolvedor.
- `core/src/test/.../launch/*Test.kt`, `.../remote/dto/PhysicalMediaTest.kt`.
- `app/.../data/local/entity/RomPresentationEntity.kt`, `dao/RomPresentationDao.kt`,
  `RomSyncDatabase.kt` (v8 + migración), `app/schemas/.../8.json` (generado).
- `app/.../data/local/SettingsDataStore.kt`, `data/repository/SettingsRepository.kt`.
- `app/.../data/repository/PhysicalMediaRepository.kt` — caché + preferencias.
- `app/.../launch/EmulatorLauncher.kt` — dispatcher Android.
- `app/.../data/AppContainer.kt`, `domain/model/Rom.kt`, `data/repository/RomRepository.kt`.
- `app/.../ui/viewmodel/ShelfViewModel.kt`, `ui/screens/PlayScreen.kt`,
  `ui/screens/LauncherComponents.kt` (nuevo), `ui/screens/LibraryScreen.kt`,
  `ui/screens/ConfigScreen.kt`, `ui/navigation/RomMSyncApp.kt`.
- `app/src/main/AndroidManifest.xml` (`<queries>`), `res/xml/file_paths.xml`.
- `core/src/main/resources/i18n/messages*.properties`.
