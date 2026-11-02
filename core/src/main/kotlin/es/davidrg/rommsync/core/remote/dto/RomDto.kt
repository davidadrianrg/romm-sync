package es.davidrg.rommsync.core.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Metadata from IGDB scraper (subset relevant for gamelist.xml export).
 */
@JsonClass(generateAdapter = true)
data class IgdbMetadataDto(
    @Json(name = "total_rating") val totalRating: String? = null,
    @Json(name = "first_release_date") val firstReleaseDate: Long? = null,
    val genres: List<String> = emptyList(),
    val companies: List<String> = emptyList(),
    @Json(name = "game_modes") val gameModes: List<String> = emptyList(),
    @Json(name = "player_count") val playerCount: String? = null,
)

@JsonClass(generateAdapter = true)
data class RomFileDto(
    @Json(name = "file_name") val filename: String,
    @Json(name = "file_size_bytes") val size: Long = 0,
    @Json(name = "file_path") val filePath: String? = null,
    @Json(name = "full_path") val fullPath: String? = null,
    @Json(name = "hash") val hash: String? = null,
)

@JsonClass(generateAdapter = true)
data class RomDto(
    val id: Int,
    @Json(name = "slug") val slug: String? = null,
    val name: String,
    @Json(name = "fs_name") val fileName: String,
    @Json(name = "fs_name_no_tags") val fileNameNoTags: String? = null,
    @Json(name = "fs_name_no_ext") val fileNameNoExt: String? = null,
    @Json(name = "file_extension") val fileExtension: String? = null,
    @Json(name = "file_path") val filePath: String? = null,
    @Json(name = "fs_size_bytes") val fileSizeBytes: Long = 0,
    @Json(name = "platform_id") val platformId: Int,
    @Json(name = "platform_slug") val platformSlug: String? = null,
    @Json(name = "path_cover_small") val pathCoverSmall: String? = null,
    @Json(name = "path_cover_large") val pathCoverLarge: String? = null,
    @Json(name = "url_cover") val urlCover: String? = null,
    val summary: String? = null,
    @Json(name = "igdb_id") val igdbId: Int? = null,
    val files: List<RomFileDto> = emptyList(),
    val multi: Boolean = false,
    @Json(name = "has_multiple_files") val hasMultipleFiles: Boolean = false,
    val revision: String? = null,
    val regions: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    // ── Media fields for ES-DE export ──────────────────────────────
    @Json(name = "merged_screenshots") val mergedScreenshots: List<String> = emptyList(),
    @Json(name = "path_video") val pathVideo: String? = null,
    @Json(name = "path_manual") val pathManual: String? = null,
    @Json(name = "youtube_video_id") val youtubeVideoId: String? = null,
    @Json(name = "igdb_metadata") val igdbMetadata: IgdbMetadataDto? = null,
    // ── Medio físico (cartucho, disco, caja) de ScreenScraper / gamelist ──
    @Json(name = "ss_metadata") val ssMetadata: PhysicalMetadataDto? = null,
    @Json(name = "gamelist_metadata") val gamelistMetadata: PhysicalMetadataDto? = null,
) {
    /**
     * Ruta relativa (a los resources de RomM) de la imagen del medio físico.
     * Prioriza ScreenScraper y cae al gamelist. Null si RomM no tiene ninguna.
     */
    val physicalMediaPath: String?
        get() = ssMetadata?.physicalPath?.takeIf { it.isNotBlank() }
            ?: gamelistMetadata?.physicalPath?.takeIf { it.isNotBlank() }
}

/**
 * Subconjunto de `ss_metadata` / `gamelist_metadata` relativo al medio físico.
 *
 * `physical_url` apunta a ScreenScraper (no a RomM) y se conserva solo como
 * dato. Los discos (`physical_disc`, `physical_extra_discs`) se modelan sin
 * tipo para que un cambio de forma en RomM no rompa el parseo del listado;
 * la fase 1 solo usa [physicalPath].
 */
@JsonClass(generateAdapter = true)
data class PhysicalMetadataDto(
    @Json(name = "physical_path") val physicalPath: String? = null,
    @Json(name = "physical_url") val physicalUrl: String? = null,
    @Json(name = "physical_disc") val physicalDisc: Any? = null,
    @Json(name = "physical_extra_discs") val physicalExtraDiscs: List<Any>? = null,
)

/**
 * Paginated response for GET /api/roms
 * RomM uses fastapi-pagination which wraps results in {items, total, page, size, pages}
 */
@JsonClass(generateAdapter = true)
data class RomResponse(
    @Json(name = "items") val items: List<RomDto> = emptyList(),
    @Json(name = "total") val total: Int = 0,
    @Json(name = "page") val page: Int? = null,
    @Json(name = "size") val size: Int? = null,
    @Json(name = "pages") val pages: Int? = null,
)
