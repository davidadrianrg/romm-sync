<div align="center">

# 🎮 Atril — Game launcher with RomM integration

🌐 **English** · [Español](README.es.md)

**A game launcher for Android and Linux with [RomM](https://github.com/rommapp/romm) integration.**

Atril turns your downloaded ROMs into a physical-media style launcher: collectible cards, real cartridge/disc art when RomM has it, and an insertion animation before the game starts. It also browses your RomM library, downloads ROMs into your frontend folders and keeps saves synced across Android, Steam Deck and PC.

- **Android** (Kotlin + Jetpack Compose): native APK, optimized for handheld consoles (Anbernic, Retroid Pocket...) with a touchscreen: dark UI, large buttons and finger-friendly navigation.
- **Linux** (Compose Desktop): self-contained AppImage for **x86_64** (SteamOS, Steam Deck, PCs) and **aarch64** (Raspberry Pi 5, ARM handhelds) with the Java runtime bundled in — nothing to install.

</div>

---

## 📥 Installation

### Option A — Android (recommended)

> **Atril 0.9.0 changes the Android application ID to `com.davidadrianrg.atril`.**
> It installs as a new app: if you come from RomM Sync, download and install this APK manually. The old app cannot self-update into the new package; configure Atril once and remove the old installation when you are ready.

1. Download `Atril-vX.Y.Z-android.apk` from [GitHub Releases](https://github.com/davidadrianrg/atril/releases/latest).
2. Configure your server (see below).
3. In **Settings → Updates**, tap *Check for updates* whenever you want to check for a new version. The app downloads the APK and launches the Android installer — without leaving the app.

> ⚠️ The first time, Android will ask you to grant Atril the **"Install unknown apps"** permission. It is the standard permission for any app to self-update outside the Play Store.

### Option B — Linux (AppImage)

1. Download the AppImage for your architecture from [GitHub Releases](https://github.com/davidadrianrg/atril/releases/latest):
   - `Atril-vX.Y.Z-linux-x86_64.AppImage` — SteamOS, Steam Deck, most PCs
   - `Atril-vX.Y.Z-linux-aarch64.AppImage` — Raspberry Pi 5, ARM handhelds
2. Give it execute permission and run it:
   ```bash
   chmod +x Atril-v*-linux-*.AppImage
   ./Atril-v*-linux-x86_64.AppImage
   ```
3. The first time it asks for the server URL, API Key and ROMs folder; it is saved for next time.

> The AppImage bundles the jlinked Java runtime inside (~70 MB): you don't need to install Java or anything else.

### Option C — Build from source

```bash
git clone https://github.com/davidadrianrg/atril.git
cd atril
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17 and Android SDK (API 35). For the desktop: just JDK 17 (`./gradlew :desktop:packageReleaseUberJarForCurrentOS`). To sign your own release, create `keystore.properties` at the root (it's in `.gitignore`):

```properties
storeFile=/path/to/your.keystore
storePassword=***
keyAlias=***
keyPassword=***
```

## ⚙️ Initial setup (2 minutes)

| What | Value |
|---|---|
| **Server URL** | `https://romm.yourdomain.com` (your RomM 4.9+ instance for sync) |
| **API Key** | `rmm_...` token from *RomM → User → API Keys* |
| **ROMs directory** | `/storage/emulated/0/ROMs` (or your microSD) |

The app requests the **"All files access"** permission — it needs it to write ROMs into the folders your emulator/frontend expects, without copying or moving anything manually.

<details>
<summary><b>Why an API Key and not username/password?</b></summary>

RomM supports OAuth/OIDC login behind proxies (Authentik, Authelia...), which usually breaks non-web clients with redirects and CSRF tokens. The API Key is a long per-user token that always works, whatever your web authentication setup.
</details>

## ✨ What the app does

- **📚 Library** — Browse your RomM server with cover art, search and filters (all / missing / downloaded). Detects games already present on disk with *Scan library*.
- **⬇️ Downloads** — Queue with configurable parallel downloads (1–5), automatic resume if the network drops, hash-based integrity verification and an out-of-space warning. *WiFi-only* option to avoid using data.
- **🔔 Update notifications** — checks GitHub on launch and once a day; posts a notification when a new Atril version is available.
- **💾 Save sync** — Your save files always up to date on every device, using RomM's [Device Sync Protocol](https://docs.romm.app/latest/developers/device-sync-protocol/) (v4.9+): upload what changed on the handheld, download what you played on the PC. Resolves conflicts explicitly instead of overwriting.
- **🗂️ Metadata export** — Generates ES-DE `gamelist.xml` files or copies the media (covers, fanart, logos, screenshots) into the RetroHRAI structure.
- **🔄 Auto-update** — Checks, downloads and installs new versions from GitHub Releases without uninstalling anything (see Installation).
- **🖥️ Linux on par with Android** — The desktop app shares the theme ("Midnight Arcade"), library with cover art and full game details (screenshots, IGDB metadata), download queue with cancel/retry/resume, save sync with conflict resolution and ES-DE export with non-destructive merge.
- **🌐 English & Spanish UI** — The interface is available in both English and Spanish.

### Emulators supported for save sync

| Platform | Emulator (default) |
|---|---|
| Retro (NES–N64, GBA, PSX...) | RetroArch |
| Nintendo DS | melonDS |
| PSP | PPSSPP |
| PS2 | AetherSX2 / NetherSX2 |
| GameCube / Wii | Dolphin |
| 3DS | Azahar |
| Wii U | Cemu |
| Switch | Eden |
| Native Android games | per-game configurable path |

> 💡 PS2 requires enabling **Folder Memory Card** in the emulator to sync per game.

## 🖼️ Permissions it uses and why

| Permission | Reason |
|---|---|
| All files access | Write ROMs/metadata into the folders the emulators read |
| Install unknown apps | Only if you use auto-update: it installs the APK it downloads itself |
| Notifications | Download progress and background sync |
| Ignore battery optimization | So the periodic sync isn't killed by the system |
| Internet | Talk to your RomM server and to GitHub (check for updates) |

No telemetry, no analytics, no accounts: your data goes from your device to **your** server.

## 🏗️ Architecture (for contributors)

Monorepo with three Gradle modules that share all the business logic:

```
core/src/main/kotlin/com/davidadrianrg/atril/core/   # Pure JVM — shared Android + Desktop
├── remote/              # Retrofit + OkHttp (RomM API, Device Sync Protocol)
├── sync/platform/       # Per-emulator save handlers (RetroArch, melonDS, PS2, PS3, 3DS...)
├── download/            # PathMapper (per-platform folder structure)
└── util/                # RootShell, ROM header reading...

app/src/main/java/com/davidadrianrg/atril/           # Android
├── data/local/          # Room + DataStore (persistent config)
├── data/repository/     # remote ↔ cache bridge
├── data/metadata/       # gamelist.xml / media export
├── data/update/         # Auto-update from GitHub Releases
├── download/            # WorkManager + resumable download
├── ui/                  # Compose Material3 (screens/viewmodel/components)
└── util/                # Android permissions

desktop/src/main/kotlin/com/davidadrianrg/atril/desktop/  # Linux (Compose Desktop)
├── Main.kt / AppContent.kt   # UI (connection, library, downloads, sync)
├── DesktopSyncCoordinator.kt # Sync loop without Room/WorkManager
├── DownloadEngine.kt         # Download stream→disk (single file + zip-stream)
└── DesktopConfig.kt          # java.util.prefs (persistent config)
```

- **Common stack**: Kotlin · Retrofit/OkHttp · Moshi · Coroutines
- **Android**: Jetpack Compose (Material 3) · Room · WorkManager · DataStore · Coil
- **Desktop**: Compose Desktop · AppImage (jlinked runtime included)
- **UI languages**: the interface is available in English and Spanish.
- **Code language**: English in identifiers/logs; UI texts are localized.
- **Tests**: `./gradlew test` (unit tests in `core/src/test` and `app/src/test`).

## 🔁 CI/CD

Stable releases are built from `master`:

1. **APK** (`.github/workflows/build-release.yml`): signed build (R8) → GitHub Release as `Atril-vX.Y.Z-android.apk`.
2. **AppImage** (`.github/workflows/build-appimage.yml`): build for **x86_64** and **aarch64** → `Atril-vX.Y.Z-linux-<arch>.AppImage`, attached to the same release as the APK.

Day-to-day development happens on **`develop`**. Every push there runs `.github/workflows/develop.yml` and updates a single **draft** release named `develop` with the APK and both AppImages. Draft releases are visible only to repository maintainers, so they are the internal test channel.

The tag and `versionName` match **exactly** the `versionName` in `app/build.gradle.kts` (e.g. `0.4.2` → `v0.4.2`); bump it there to publish a new version. The internal `versionCode` uses the workflow `run_number`, which always increases (Android requires it to update without uninstalling). Publishing the same version again replaces its release.

The release APK is signed with a fixed key (secrets `SIGNING_KEYSTORE_BASE64` and derivatives) so updates install over the previous version without uninstalling. **Keep a backup of the keystore**: if it is lost, users would have to uninstall in order to update.

## 🤝 Contributing

1. Fork + branch (`feat/my-thing`).
2. `./gradlew test` green.
3. PR to `master` — CI must pass.

Bugs, ideas and PRs welcome in [Issues](https://github.com/davidadrianrg/atril/issues).

## 📄 License

MIT
