package com.davidadrianrg.atril.data.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.davidadrianrg.atril.MainActivity
import com.davidadrianrg.atril.R
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.core.sync.WatchScope
import com.davidadrianrg.atril.core.sync.platform.SaveHandlerRegistry
import com.davidadrianrg.atril.data.local.RomSyncDatabase
import com.davidadrianrg.atril.data.local.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

/**
 * Vigilancia en tiempo real de las carpetas de saves.
 *
 * Android no permite temporizadores de 1 minuto en segundo plano (Doze +
 * límite de 15 min de WorkManager). La única forma fiable es una foreground
 * service con notificación permanente: este servicio observa las carpetas de
 * saves con [FileObserver] y lanza un sync con debounce cuando cambia algo.
 *
 * Las rutas dentro de `Android/data` y `Android/obb` no se pueden observar
 * (FUSE no expone eventos); esas siguen cubiertas por el worker de 15 min.
 */
class SaveSyncWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settings: SettingsDataStore
    private val observers = mutableMapOf<String, FileObserver>()
    private var lastWatchedFolders = 0
    private var lastWatchedGames = 0
    private var debounceJob: Job? = null
    private var lastFolderCount = -1
    private val saveSyncManager by lazy { SaveSyncManager(applicationContext) }
    @Volatile private var syncBusy = false
    @Volatile private var suppressEventsUntil = 0L

    override fun onCreate() {
        super.onCreate()
        settings = SettingsDataStore(this)
        createChannel()
        // Android exige startForeground en los primeros 5 s tras startForegroundService.
        startForeground(NOTIFICATION_ID, buildNotification(0))

        // Evita bucles: los ficheros que escribe el propio sync no deben
        // disparar otro sync. Mientras hay trabajo se ignoran eventos y se
        // deja un margen tras terminar.
        scope.launch {
            saveSyncManager.observeSyncState().collect { state ->
                val busy = state is SyncState.Running || state is SyncState.Pending
                syncBusy = busy
                if (!busy) {
                    suppressEventsUntil = System.currentTimeMillis() + COOLDOWN_MS
                }
            }
        }

        scope.launch {
            while (isActive) {
                if (!settings.getSaveSyncWatchEnabledBlocking() ||
                    !settings.getSaveSyncEnabledBlocking()
                ) {
                    stopSelf()
                    break
                }
                val folders = runCatching { refreshWatchers() }.getOrDefault(observers.size)
                updateNotification(folders)
                delay(REFRESH_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!settings.getSaveSyncWatchEnabledBlocking()) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        debounceJob?.cancel()
        observers.values.forEach { runCatching { it.stopWatching() } }
        observers.clear()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Android 15 limita las FGS dataSync a 6 h/día; al llegar, paramos limpiamente. */
    override fun onTimeout(startId: Int) {
        stopSelf()
    }

    // ── Observadores ─────────────────────────────────────────────────────

    /** Recalcula las carpetas y devuelve cuántas se están vigilando. */
    private fun refreshWatchers(): Int {
        val wanted = LinkedHashSet<String>()
        for (root in collectRoots()) {
            if (!root.isDirectory || !root.canRead() || WatchScope.isExcludedPath(root.absolutePath)) continue
            runCatching { collectDirs(root, 0, wanted) }
        }
        lastWatchedFolders = wanted.size

        (observers.keys - wanted).forEach { path ->
            observers.remove(path)?.let { runCatching { it.stopWatching() } }
        }

        for (path in wanted) {
            if (observers.containsKey(path)) continue
            val observer = object : FileObserver(path, EVENT_MASK) {
                override fun onEvent(event: Int, name: String?) {
                    if (name.isNullOrBlank()) return
                    onFileEvent(File(path, name))
                }
            }
            runCatching {
                observer.startWatching()
                observers[path] = observer
            }
        }
        return observers.size
    }

    /** Recorrido acotado: poca profundidad y sin directorios de ROMs/cachés/medios. */
    private fun collectDirs(dir: File, depth: Int, out: MutableSet<String>) {
        if (out.size >= WatchScope.MAX_DIRS || !dir.isDirectory || !dir.canRead()) return
        out.add(dir.absolutePath)
        if (depth >= WatchScope.MAX_DEPTH) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory && WatchScope.shouldDescend(child.name, depth + 1)) collectDirs(child, depth + 1, out)
        }
    }

    private fun collectRoots(): Set<File> {
        val roots = mutableSetOf<File>()
        val database = RomSyncDatabase.getDatabase(this)
        val retroBase = settings.getRetroArchBasePathBlocking()

        val roms = runCatching { database.romDao().getAllDownloadedRomsBlocking() }
            .getOrDefault(emptyList())
        val activeRoms = roms.filter { !it.excludedFromSync }
        val active = WatchScope.activePlatforms(roms.map { WatchScope.DownloadedGame(it.platformSlug, it.excludedFromSync) })
        lastWatchedGames = activeRoms.size
        if (active.isEmpty()) return emptySet() // nada con sync activo: no se vigila nada

        // Solo plataformas con juegos descargados y sync activo (no las ~70 del servidor).
        val platforms = runCatching { database.platformDao().getAllPlatformsBlocking() }
            .getOrDefault(emptyList())
            .filter { it.slug in active }
        for (platform in platforms) {
            val emulatorId = platform.emulatorId
                ?: SaveHandlerRegistry.getDefaultEmulator(platform.slug).id
            val path = platform.savesPathOverride?.takeIf { it.isNotBlank() }
                ?: SaveHandlerRegistry.getDefaultSavesPath(
                    emulatorId = emulatorId,
                    platformSlug = platform.slug,
                    retroArchBase = retroBase,
                )
            if (path.isNotBlank()) roots.add(File(path))
        }

        // Carpeta base de RetroArch solo si alguna plataforma activa la usa por defecto.
        if (retroBase.isNotBlank() && platforms.any { it.savesPathOverride.isNullOrBlank() }) {
            roots.add(File(retroBase, "saves"))
            roots.add(File(retroBase, "states"))
        }

        for (rom in activeRoms) {
            rom.savesPathOverride?.takeIf { it.isNotBlank() }?.let { roots.add(File(it)) }
        }

        return roots.filter { it.exists() }.toSet()
    }

    private fun onFileEvent(file: File) {
        if (syncBusy || System.currentTimeMillis() < suppressEventsUntil) return
        if (!isRelevant(file)) return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            // El emulador puede escribir varios ficheros seguidos.
            delay(DEBOUNCE_MS)
            if (!syncBusy &&
                settings.getSaveSyncWatchEnabledBlocking() &&
                settings.getSaveSyncEnabledBlocking()
            ) {
                saveSyncManager.triggerSync()
            }
        }
    }

    private fun isRelevant(file: File): Boolean {
        if (!file.isFile) return false
        val name = file.name.lowercase()
        if (name.startsWith(".")) return false
        return !IGNORED_SUFFIXES.any { name.endsWith(it) }
    }

    // ── Notificación ─────────────────────────────────────────────────────

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                tr("android.sync.watch.notification.channel"),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = tr("android.sync.watch.notification.channel_desc")
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(folders: Int) {
        if (folders == lastFolderCount) return
        lastFolderCount = folders
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(folders))
    }

    private fun buildNotification(folders: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SaveSyncWatchService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (folders > 0) {
            tr("android.sync.watch.notification.watching_scope", folders.toString(), lastWatchedGames.toString())
        } else {
            tr("android.sync.watch.notification.starting")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(tr("android.sync.watch.notification.title"))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, tr("android.sync.watch.notification.stop"), stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "save_sync_watch"
        private const val NOTIFICATION_ID = 42_001
        private const val ACTION_STOP = "com.davidadrianrg.atril.action.STOP_SAVE_WATCH"
        private const val REFRESH_MS = 60_000L
        private const val DEBOUNCE_MS = 8_000L
        private const val COOLDOWN_MS = 30_000L

        private const val EVENT_MASK = FileObserver.CLOSE_WRITE or
            FileObserver.MOVED_TO or
            FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.MODIFY

        private val IGNORED_SUFFIXES = listOf(
            ".tmp", ".part", ".lock", ".log", ".crdownload", ".download", ".bak",
        )

        fun start(context: Context) {
            val intent = Intent(context, SaveSyncWatchService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SaveSyncWatchService::class.java))
        }
    }
}
