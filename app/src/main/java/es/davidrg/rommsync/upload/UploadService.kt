package es.davidrg.rommsync.upload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import es.davidrg.rommsync.MainActivity
import es.davidrg.rommsync.R
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.core.remote.NetworkModule
import es.davidrg.rommsync.core.upload.RomUploader
import es.davidrg.rommsync.data.local.SettingsDataStore
import es.davidrg.rommsync.data.storage.SafFolder
import es.davidrg.rommsync.data.storage.SafUploadSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Subida de juegos en primer plano.
 *
 * Mantiene el proceso vivo aunque el usuario cierre el diálogo o la app, y
 * publica el progreso en [UploadBus] para que la UI lo observe. La lectura se
 * hace directamente del SAF: los juegos grandes ya no se copian al cache.
 */
class UploadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                job?.cancel()
                return START_NOT_STICKY
            }
            ACTION_START -> startUpload(intent)
        }
        return START_NOT_STICKY
    }

    private fun startUpload(intent: Intent) {
        if (job?.isActive == true) return

        val platformId = intent.getIntExtra(EXTRA_PLATFORM_ID, -1)
        val singleUri = intent.getStringExtra(EXTRA_SINGLE_URI)
        val treeUri = intent.getStringExtra(EXTRA_TREE_URI)
        val folderName = intent.getStringExtra(EXTRA_FOLDER_NAME).orEmpty()
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
        if (platformId <= 0 || (singleUri == null && treeUri == null)) {
            UploadBus.fail(tr("library.upload.error", "invalid request"))
            stopSelf()
            return
        }

        startAsForeground(0, tr("library.upload.preparing"))
        UploadBus.start()

        job = scope.launch {
            try {
                val message = runUpload(platformId, singleUri, treeUri, folderName, displayName)
                UploadBus.success(message)
                notifyFinished(message, failed = false)
            } catch (e: CancellationException) {
                UploadBus.cancelled(tr("library.upload.cancelled"))
                notifyFinished(tr("library.upload.cancelled"), failed = true)
                throw e
            } catch (e: Exception) {
                val error = tr("library.upload.error", e.message ?: "")
                UploadBus.fail(error)
                notifyFinished(error, failed = true)
            } finally {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private suspend fun runUpload(
        platformId: Int,
        singleUri: String?,
        treeUri: String?,
        folderName: String,
        displayName: String,
    ): String {
        val settings = SettingsDataStore(this)
        val serverUrl = settings.getServerUrlBlocking()
        val apiKey = settings.getApiKeyBlocking()
        require(serverUrl.isNotBlank() && apiKey.isNotBlank()) { tr("common.server_not_configured") }

        val uploader = RomUploader(NetworkModule.createApiService(serverUrl, apiKey))

        if (singleUri != null) {
            SafUploadSource.from(this, Uri.parse(singleUri)).use { source ->
                uploader.upload(source, platformId) { percent ->
                    publish(percent, tr("library.upload.uploading", percent))
                }.getOrThrow()
            }
            val scanned = uploader.triggerQuickScan(platformId)
            return if (scanned) {
                tr("library.upload.ok_scan", displayName)
            } else {
                tr("library.upload.ok", displayName)
            }
        }

        val tree = Uri.parse(treeUri ?: error("missing tree uri"))
        val docs = SafFolder.listFiles(tree, this).filter { it.isFile && it.length() > 0 }
        require(docs.isNotEmpty()) { tr("library.upload.error", "empty folder") }

        val total = docs.sumOf { it.length() }.coerceAtLeast(1)
        var done = 0L
        val count = docs.size
        val maxBefore = uploader.maxRomId(platformId)

        val first = docs.first()
        publish(0, tr("library.upload.phase_file", 1, count, 0))
        SafUploadSource.from(this, first).use { source ->
            uploader.upload(source, platformId) { percent ->
                publish(
                    ((done + first.length() * percent / 100) * 100 / total).toInt(),
                    tr("library.upload.phase_file", 1, count, percent),
                )
            }.getOrThrow()
        }
        done += first.length()

        UploadBus.phase(tr("library.upload.phase_scan", 0))
        val romId = uploader.awaitNewRomId(platformId, maxBefore) { seconds ->
            UploadBus.phase(tr("library.upload.phase_scan", seconds))
        } ?: error(tr("library.upload.timeout"))

        for ((index, document) in docs.drop(1).withIndex()) {
            val number = index + 2
            SafUploadSource.from(this, document).use { source ->
                uploader.uploadIntoRom(source, romId, platformId) { percent ->
                    publish(
                        ((done + document.length() * percent / 100) * 100 / total).toInt(),
                        tr("library.upload.phase_file", number, count, percent),
                    )
                }.getOrThrow()
            }
            done += document.length()
        }

        publish(100, tr("library.upload.phase_rename"))
        uploader.renameRom(romId, folderName)
        val scanned = uploader.triggerQuickScan(platformId)
        return if (scanned) {
            tr("library.upload.ok_scan", folderName)
        } else {
            tr("library.upload.ok", folderName)
        }
    }

    private fun publish(percent: Int, phase: String) {
        UploadBus.progressAndPhase(percent, phase)
        runCatching {
            notificationManager.notify(
                NOTIFICATION_ID,
                buildNotification(percent, phase, indeterminate = false),
            )
        }
    }

    private fun startAsForeground(percent: Int, text: String) {
        val notification = buildNotification(percent, text, indeterminate = percent <= 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyFinished(text: String, failed: Boolean) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(tr(if (failed) "library.upload.title" else "library.upload.title"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        runCatching { notificationManager.notify(NOTIFICATION_ID, notification) }
    }

    private fun buildNotification(percent: Int, text: String, indeterminate: Boolean): Notification {
        val cancelIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, UploadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(tr("library.upload.title"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), indeterminate)
            .addAction(0, tr("common.cancel"), cancelIntent)
            .setContentIntent(contentIntent())
            .build()
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        3,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                tr("library.upload.title"),
                NotificationManager.IMPORTANCE_LOW,
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "romm_upload"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_START = "es.davidrg.rommsync.upload.START"
        private const val ACTION_CANCEL = "es.davidrg.rommsync.upload.CANCEL"
        private const val EXTRA_PLATFORM_ID = "platform_id"
        private const val EXTRA_SINGLE_URI = "single_uri"
        private const val EXTRA_TREE_URI = "tree_uri"
        private const val EXTRA_FOLDER_NAME = "folder_name"
        private const val EXTRA_DISPLAY_NAME = "display_name"

        fun start(
            context: Context,
            platformId: Int,
            singleUri: Uri?,
            treeUri: Uri?,
            folderName: String,
            displayName: String,
        ) {
            val intent = Intent(context, UploadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PLATFORM_ID, platformId)
                .putExtra(EXTRA_SINGLE_URI, singleUri?.toString())
                .putExtra(EXTRA_TREE_URI, treeUri?.toString())
                .putExtra(EXTRA_FOLDER_NAME, folderName)
                .putExtra(EXTRA_DISPLAY_NAME, displayName)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, UploadService::class.java).setAction(ACTION_CANCEL),
            )
        }
    }
}
