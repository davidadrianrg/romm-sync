package com.davidadrianrg.atril.core.media

import com.davidadrianrg.atril.core.remote.dto.RomDto
import com.squareup.moshi.Moshi
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Generic "download media with the game" feature (frontend agnostic).
 *
 * Layout: `<mediaRoot>/<platformSlug>/{covers,screenshots,videos,manuals}/<base>.<ext>`
 * plus `<mediaRoot>/<platformSlug>/metadata/<base>.json`. Nothing here depends on
 * ES-DE or any other frontend; the ES-DE export reuses the same folders.
 */
enum class MediaKind(val dir: String, val defaultExt: String, val allowedExt: Set<String>) {
    COVERS("covers", "jpg", setOf("jpg", "jpeg", "png", "webp")),
    SCREENSHOTS("screenshots", "jpg", setOf("jpg", "jpeg", "png", "webp")),
    VIDEOS("videos", "mp4", setOf("mp4", "webm", "mkv")),
    MANUALS("manuals", "pdf", setOf("pdf")),
}

data class MediaItem(val kind: MediaKind, val url: String, val target: File)

object GameMediaPaths {
    const val DEFAULT_DIR_NAME = "downloaded_media"
    const val MAX_SCREENSHOTS = 5

    /** Default `<romsRoot>/downloaded_media`; [custom] wins when set. */
    fun resolveRoot(custom: String?, romsRoot: String): String =
        custom?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            ?: (romsRoot.trimEnd('/') + "/" + DEFAULT_DIR_NAME)

    /** Safe single path component: no separators, no traversal, never empty. */
    fun safeName(raw: String?, fallback: String): String {
        val cleaned = (raw ?: "")
            .replace(Regex("[^a-zA-Z0-9._ -]"), "")
            .trim()
            .trim('.')
            .trim()
        return cleaned.ifEmpty { fallback }
    }

    fun safeSlug(raw: String?): String =
        (raw ?: "").lowercase().replace(Regex("[^a-z0-9_-]"), "").ifEmpty { "unknown" }

    /** File base name shared by all media of a game (matches the ES-DE exporter). */
    fun baseName(dto: RomDto): String =
        safeName(dto.fileNameNoExt ?: dto.fileName.substringBeforeLast("."), "rom_${dto.id}")

    fun extensionFor(url: String, kind: MediaKind): String {
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext in kind.allowedExt) ext else kind.defaultExt
    }

    fun metadataFile(mediaRoot: String, platformSlug: String, base: String): File =
        File(File(File(mediaRoot), safeSlug(platformSlug)), "metadata/$base.json")
}

object GameMediaPlanner {
    /** Absolute URL for a RomM path; absolute URLs are kept (host-checked later). */
    fun absolute(serverUrl: String, pathOrUrl: String): String =
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) pathOrUrl
        else serverUrl.trimEnd('/') + "/" + pathOrUrl.removePrefix("/").replace(" ", "%20")

    fun plan(dto: RomDto, serverUrl: String, mediaRoot: String): List<MediaItem> {
        val slug = GameMediaPaths.safeSlug(dto.platformSlug)
        val base = GameMediaPaths.baseName(dto)
        fun target(kind: MediaKind, url: String, suffix: String = ""): File =
            File(File(File(mediaRoot), "$slug/${kind.dir}"), "$base$suffix.${GameMediaPaths.extensionFor(url, kind)}")

        val items = mutableListOf<MediaItem>()
        (dto.pathCoverLarge ?: dto.pathCoverSmall)?.takeIf { it.isNotBlank() }?.let {
            val u = absolute(serverUrl, it)
            items += MediaItem(MediaKind.COVERS, u, target(MediaKind.COVERS, u))
        }
        dto.mergedScreenshots.filter { it.isNotBlank() }.take(GameMediaPaths.MAX_SCREENSHOTS).forEachIndexed { i, s ->
            val u = absolute(serverUrl, s)
            // First keeps the plain name (ES-DE compatible); the rest get _2, _3…
            items += MediaItem(MediaKind.SCREENSHOTS, u, target(MediaKind.SCREENSHOTS, u, if (i == 0) "" else "_${i + 1}"))
        }
        dto.pathVideo?.takeIf { it.isNotBlank() }?.let {
            val u = absolute(serverUrl, it)
            items += MediaItem(MediaKind.VIDEOS, u, target(MediaKind.VIDEOS, u))
        }
        dto.pathManual?.takeIf { it.isNotBlank() }?.let {
            val u = absolute(serverUrl, it)
            items += MediaItem(MediaKind.MANUALS, u, target(MediaKind.MANUALS, u))
        }
        return items
    }

    /** Small, frontend-agnostic description of the game and its media (paths relative to the platform dir). */
    fun metadataJson(dto: RomDto, serverUrl: String, items: List<MediaItem>, mediaRoot: String): String {
        val platformDir = File(File(mediaRoot), GameMediaPaths.safeSlug(dto.platformSlug))
        val rel = items.groupBy({ it.kind.dir }, { it.target.relativeTo(platformDir).invariantSeparatorsPath })
        // Mapa en vez de una clase de datos: Moshi reflexivo se rompe con R8 (campos renombrados).
        val meta = linkedMapOf<String, Any?>(
            "title" to dto.name.ifBlank { dto.fileName },
            "platform" to GameMediaPaths.safeSlug(dto.platformSlug),
            "description" to dto.summary?.takeIf { it.isNotBlank() },
            "source" to "RomM ${serverUrl.trimEnd('/')} (id ${dto.id})",
            "romId" to dto.id,
            "files" to rel,
        )
        return Moshi.Builder().build().adapter(Any::class.java).indent("  ").serializeNulls().toJson(meta)
    }
}

/** Outcome of a media run: failures never abort the rest (nor the ROM download that triggered it). */
data class MediaReport(val downloaded: Int, val alreadyThere: Int, val failed: List<String>)

class GameMediaDownloader(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val serverUrl: String,
    private val log: (String) -> Unit = {},
) {
    private val serverUrlParsed = serverUrl.toHttpUrlOrNull()

    /** Downloads the planned items (skipping existing files) and writes the metadata JSON. */
    fun run(dto: RomDto, mediaRoot: String, kinds: Set<MediaKind> = MediaKind.entries.toSet()): MediaReport {
        val items = GameMediaPlanner.plan(dto, serverUrl, mediaRoot).filter { it.kind in kinds }
        var downloaded = 0
        var there = 0
        val failed = mutableListOf<String>()
        for (item in items) {
            if (item.target.exists() && item.target.length() > 0) { there++; continue }
            if (fetch(item)) downloaded++ else failed += item.url.substringBefore('?').substringAfterLast('/')
        }
        runCatching {
            val f = GameMediaPaths.metadataFile(mediaRoot, dto.platformSlug ?: "", GameMediaPaths.baseName(dto))
            if (!f.exists()) {
                f.parentFile.mkdirs()
                f.writeText(GameMediaPlanner.metadataJson(dto, serverUrl, items, mediaRoot), Charsets.UTF_8)
            }
        }.onFailure { log("metadata.json failed: ${it.javaClass.simpleName}") }
        return MediaReport(downloaded, there, failed)
    }

    private fun fetch(item: MediaItem): Boolean {
        val url = item.url.toHttpUrlOrNull() ?: return false
        val tmp = File(item.target.parentFile, item.target.name + ".part")
        return try {
            item.target.parentFile.mkdirs()
            val req = Request.Builder().url(url).apply {
                // Only the RomM server gets the token.
                if (serverUrlParsed != null && url.host == serverUrlParsed.host && url.port == serverUrlParsed.port && apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
            }.build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) { log("HTTP ${r.code} for ${item.kind.dir}"); return false }
                val body = r.body ?: return false
                body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            }
            if (tmp.length() == 0L) { tmp.delete(); return false }
            tmp.renameTo(item.target) || (tmp.copyTo(item.target, overwrite = true).let { tmp.delete(); true })
        } catch (e: IOException) {
            log("IO error ${item.kind.dir}: ${e.javaClass.simpleName}")
            tmp.delete()
            false
        }
    }
}
