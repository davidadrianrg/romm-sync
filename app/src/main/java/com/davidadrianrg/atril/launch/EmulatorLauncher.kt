package com.davidadrianrg.atril.launch

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.core.launch.EmulatorCatalog
import com.davidadrianrg.atril.core.launch.EmulatorCatalog.Family
import com.davidadrianrg.atril.core.launch.EmulatorSelection
import com.davidadrianrg.atril.core.launch.EmulatorSelector
import com.davidadrianrg.atril.core.launch.LaunchEnvironment
import com.davidadrianrg.atril.core.launch.LaunchExtra
import com.davidadrianrg.atril.core.launch.LaunchFile
import com.davidadrianrg.atril.core.launch.LaunchFileResolver
import com.davidadrianrg.atril.core.launch.LaunchFlag
import com.davidadrianrg.atril.core.launch.LaunchSpec
import com.davidadrianrg.atril.core.launch.LaunchSpecBuilder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lo que hace falta para lanzar un juego descargado. */
data class LaunchRequest(
    val platformSlug: String,
    val platformLabel: String,
    val localPath: String,
    val knownFileNames: List<String>,
    /** Override de lanzamiento elegido por el usuario (id de familia). */
    val overrideFamilyId: String?,
    /** Emulador de saves configurado para la plataforma (id), si lo hay. */
    val saveEmulatorId: String?,
)

sealed interface LaunchPlan {
    data class Ready(
        val spec: LaunchSpec,
        val family: Family,
        /** Familia preferida que no estaba instalada (se usó un fallback). */
        val fallbackFrom: Family?,
    ) : LaunchPlan

    data class Failed(val message: String) : LaunchPlan
}

/** Opción del selector de emulador por plataforma. */
data class EmulatorOption(val family: Family, val installed: Boolean)

/**
 * Dispatcher Android de los [LaunchSpec] de `:core`: detecta paquetes
 * instalados, crea el content URI con el FileProvider, concede lectura y
 * arranca la actividad. Nunca lanza excepciones a la UI: todo error vuelve
 * como mensaje traducido.
 */
class EmulatorLauncher(private val appContext: Context) {

    private val authority = "${appContext.packageName}.fileprovider"
    private val pm: PackageManager get() = appContext.packageManager

    fun installedPackages(): Set<String> = EmulatorCatalog.allPackages.filterTo(mutableSetOf()) { isInstalled(it) }

    fun options(platformSlug: String): List<EmulatorOption> {
        val installed = installedPackages()
        return EmulatorCatalog.familiesFor(platformSlug).map { family ->
            EmulatorOption(family, family.variants.any { it.packageName in installed })
        }
    }

    /** Familia que se usaría ahora mismo (para mostrarla en la UI). */
    fun currentFamily(platformSlug: String, overrideFamilyId: String?, saveEmulatorId: String?): Family? =
        when (val sel = EmulatorSelector.select(platformSlug, installedPackages(), overrideFamilyId, saveEmulatorId)) {
            is EmulatorSelection.Ready -> sel.family
            is EmulatorSelection.NotInstalled -> sel.candidates.firstOrNull()
            EmulatorSelection.Unsupported -> null
        }

    suspend fun prepare(request: LaunchRequest): LaunchPlan = withContext(Dispatchers.IO) {
        val romFile = when (val resolved = LaunchFileResolver.resolve(request.localPath, request.knownFileNames)) {
            is LaunchFile.Found -> resolved.file
            LaunchFile.Manual -> return@withContext LaunchPlan.Failed(tr("launch.error.manual"))
            is LaunchFile.Missing -> return@withContext LaunchPlan.Failed(tr("launch.error.missing", resolved.path))
        }
        val selection = EmulatorSelector.select(
            platformSlug = request.platformSlug,
            installedPackages = installedPackages(),
            overrideFamilyId = request.overrideFamilyId,
            saveEmulatorId = request.saveEmulatorId,
        )
        when (selection) {
            EmulatorSelection.Unsupported ->
                LaunchPlan.Failed(tr("launch.error.unsupported", request.platformLabel))
            is EmulatorSelection.NotInstalled ->
                LaunchPlan.Failed(
                    tr("launch.error.not_installed", selection.candidates.joinToString(" / ") { it.displayName }),
                )
            is EmulatorSelection.Ready -> {
                val env = LaunchEnvironment(Environment.getExternalStorageDirectory().absolutePath)
                val spec = LaunchSpecBuilder.build(
                    selection.family, selection.variant, romFile.absolutePath, request.platformSlug, env,
                ) ?: return@withContext LaunchPlan.Failed(tr("launch.error.no_core", request.platformLabel))
                val preferred = EmulatorSelector.candidates(
                    request.platformSlug, request.overrideFamilyId, request.saveEmulatorId,
                ).firstOrNull()
                LaunchPlan.Ready(
                    spec = spec,
                    family = selection.family,
                    fallbackFrom = preferred?.takeIf { !selection.preferred && it != selection.family },
                )
            }
        }
    }

    /**
     * Arranca el emulador. Devuelve null si se lanzó o el mensaje de error.
     * [activityContext] debe ser la Activity para no depender de NEW_TASK.
     */
    fun start(activityContext: Context, plan: LaunchPlan.Ready): String? {
        val spec = plan.spec
        val name = plan.family.displayName
        val needsUri = spec.romUriAsData || spec.grantRomRead ||
            spec.extras.any { it is LaunchExtra.RomUri }
        val uri: Uri? = if (needsUri) {
            try {
                FileProvider.getUriForFile(appContext, authority, File(spec.romPath))
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "FileProvider can't share ${spec.romPath}", e)
                // Los que solo necesitan la ruta (RetroArch) siguen sin URI.
                if (spec.romUriAsData || spec.extras.any { it is LaunchExtra.RomUri }) {
                    return tr("launch.error.file_provider", spec.romPath)
                }
                null
            }
        } else {
            null
        }

        val intent = Intent(spec.action).apply {
            if (spec.activityClass != null) {
                component = ComponentName(spec.packageName, spec.activityClass!!)
            } else {
                setPackage(spec.packageName)
            }
            if (spec.romUriAsData && uri != null) setDataAndType(uri, spec.mimeType)
            spec.extras.forEach { extra ->
                when (extra) {
                    is LaunchExtra.Text -> putExtra(extra.key, extra.value)
                    is LaunchExtra.RomUri -> putExtra(extra.key, uri)
                }
            }
            if (uri != null && spec.grantRomRead) {
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            spec.flags.forEach { flag ->
                addFlags(
                    when (flag) {
                        LaunchFlag.NEW_TASK -> Intent.FLAG_ACTIVITY_NEW_TASK
                        LaunchFlag.CLEAR_TASK -> Intent.FLAG_ACTIVITY_CLEAR_TASK
                        LaunchFlag.NO_HISTORY -> Intent.FLAG_ACTIVITY_NO_HISTORY
                    },
                )
            }
        }

        // Intent solo por paquete (hakuX): comprobar que alguien lo acepta.
        if (spec.activityClass == null && queryActivities(intent).isEmpty()) {
            return tr("launch.error.unresolved", name)
        }
        if (uri != null && spec.grantRomRead) {
            runCatching {
                appContext.grantUriPermission(spec.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        Log.i(TAG, "Launching ${spec.packageName}/${spec.activityClass} action=${spec.action} rom=${spec.romPath}")
        return try {
            activityContext.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Activity not found", e)
            tr("launch.error.failed", name, e.message ?: e.javaClass.simpleName)
        } catch (e: SecurityException) {
            Log.w(TAG, "Launch rejected", e)
            tr("launch.error.failed", name, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun queryActivities(intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    private fun isInstalled(packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val TAG = "EmulatorLauncher"
    }
}
