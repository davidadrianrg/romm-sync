package com.davidadrianrg.atril.core.scrape

import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.core.remote.RomMApiService
import com.davidadrianrg.atril.core.remote.dto.RomDto
import com.davidadrianrg.atril.core.remote.dto.RomScrapeRequest
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

/**
 * Pide a RomM (no a ScreenScraper) que obtenga el medio físico de una rom:
 * `POST /api/tasks/scan`, sondeo de `GET /api/tasks/{id}` y, al terminar,
 * `GET /api/roms/{id}` para leer `ss_metadata.physical_path`.
 */
object PhysicalMediaScrape {

    enum class TaskState { QUEUED, RUNNING, FINISHED, FAILED, UNKNOWN }

    data class TaskInfo(val taskId: String?, val state: TaskState, val error: String? = null) {
        val isTerminal: Boolean get() = state == TaskState.FINISHED || state == TaskState.FAILED
    }

    sealed interface Outcome {
        /** RomM ya tiene el asset: [rom] es el detalle actualizado. */
        data class Found(val rom: RomDto) : Outcome

        /** La tarea terminó pero RomM sigue sin medio físico (sin asset o `ss` desactivado). */
        data object NoAsset : Outcome
        data object Unauthorized : Outcome
        data object Forbidden : Outcome
        data object TimedOut : Outcome
        data class TaskFailed(val reason: String?) : Outcome
        data class Network(val message: String?) : Outcome
    }

    /** Cuerpo exacto de la petición para [romId]. */
    fun payload(romId: Int) = RomScrapeRequest(type = "update", romsIds = listOf(romId), apis = listOf("ss"))

    private val anyAdapter = Moshi.Builder().build().adapter(Any::class.java)

    /** Interpreta la respuesta de RomM para una tarea (tanto el POST como el GET). */
    fun parseTask(json: String): TaskInfo {
        val map = runCatching { anyAdapter.fromJson(json) }.getOrNull() as? Map<*, *>
            ?: return TaskInfo(null, TaskState.UNKNOWN)
        val id = (map["task_id"] ?: map["id"])?.toString()?.takeIf { it.isNotBlank() }
        val state = when ((map["status"] as? String)?.lowercase()) {
            "queued", "deferred", "scheduled", "pending" -> TaskState.QUEUED
            "started", "running", "in_progress" -> TaskState.RUNNING
            "finished", "completed", "success" -> TaskState.FINISHED
            "failed", "stopped", "canceled", "cancelled" -> TaskState.FAILED
            else -> TaskState.UNKNOWN
        }
        val error = (map["error"] ?: map["exc_info"] ?: map["detail"])?.toString()?.takeIf { it.isNotBlank() }
        return TaskInfo(id, state, error)
    }

    /** Clasifica un fallo de red/HTTP en un [Outcome]. */
    fun classify(t: Throwable): Outcome = when {
        t is HttpException && t.code() == 401 -> Outcome.Unauthorized
        t is HttpException && t.code() == 403 -> Outcome.Forbidden
        t is HttpException -> Outcome.TaskFailed("HTTP ${t.code()}")
        t is IOException -> Outcome.Network(t.message)
        else -> Outcome.Network(t.message)
    }

    /** Texto localizado para el usuario; compartido por Android y escritorio. */
    fun describe(outcome: Outcome): String = when (outcome) {
        is Outcome.Found -> tr("scrape.done")
        Outcome.NoAsset -> tr("scrape.no_asset")
        Outcome.Unauthorized -> tr("scrape.unauthorized")
        Outcome.Forbidden -> tr("scrape.forbidden")
        Outcome.TimedOut -> tr("scrape.timeout")
        is Outcome.TaskFailed -> tr("scrape.failed", outcome.reason ?: "?")
        is Outcome.Network -> tr("scrape.network", outcome.message ?: "?")
    }

    fun describeProgress(state: TaskState): String = when (state) {
        TaskState.QUEUED -> tr("scrape.queued")
        else -> tr("scrape.running")
    }

    suspend fun run(
        api: RomMApiService,
        romId: Int,
        pollIntervalMs: Long = 1500,
        maxWaitMs: Long = 120_000,
        onProgress: (TaskState) -> Unit = {},
    ): Outcome {
        try {
            val started = parseTask(api.startRomScrape(payload(romId)).use { it.string() })
            onProgress(started.state)
            val taskId = started.taskId
            if (started.state == TaskState.FAILED) return Outcome.TaskFailed(started.error)

            if (taskId != null && !started.isTerminal) {
                var waited = 0L
                while (true) {
                    delay(pollIntervalMs)
                    waited += pollIntervalMs
                    val info = parseTask(api.getTask(taskId).use { it.string() })
                    onProgress(info.state)
                    if (info.state == TaskState.FAILED) return Outcome.TaskFailed(info.error)
                    if (info.state == TaskState.FINISHED) break
                    if (waited >= maxWaitMs) return Outcome.TimedOut
                }
            }
            val rom = api.getRom(romId)
            return if (rom.physicalMediaPath != null) Outcome.Found(rom) else Outcome.NoAsset
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return classify(e)
        }
    }
}
