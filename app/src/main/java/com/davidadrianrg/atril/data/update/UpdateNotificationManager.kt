package com.davidadrianrg.atril.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.davidadrianrg.atril.MainActivity
import com.davidadrianrg.atril.R
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.data.local.SettingsDataStore

/**
 * Publica una notificación cuando GitHub tiene una versión más nueva de Atril.
 *
 * Se usa tanto al arrancar la app como desde [UpdateCheckWorker]. La última
 * versión notificada se guarda en DataStore para no repetirla en cada apertura
 * ni en cada ejecución periódica.
 */
object UpdateNotificationManager {

    const val CHANNEL_ID = "atril_updates"
    const val NOTIFICATION_ID = 4210

    suspend fun notifyIfAvailable(
        context: Context,
        result: UpdateCheckResult,
        settings: SettingsDataStore,
    ): Boolean {
        val update = result as? UpdateCheckResult.UpdateAvailable ?: return false
        if (update.latestVersion == settings.getSkippedVersionBlocking()) return false
        if (settings.getLastNotifiedUpdateVersionBlocking() == update.latestVersion) return false
        if (!canNotify(context)) return false

        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATES, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = tr("android.notif.update.text", update.latestVersion)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(tr("android.notif.update.title"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = runCatching { manager.notify(NOTIFICATION_ID, notification) }.isSuccess
        if (posted) settings.setLastNotifiedUpdateVersion(update.latestVersion)
        return posted
    }

    private fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            tr("android.notif.update.channel.name"),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = tr("android.notif.update.channel.description")
        }
        manager.createNotificationChannel(channel)
    }

    const val EXTRA_OPEN_UPDATES = "open_updates"
}
