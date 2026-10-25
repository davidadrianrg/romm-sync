package es.davidrg.rommsync.core.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Resultado de `GET /api/search/roms` (búsqueda de metadatos por nombre o ID
 * de proveedor). Se usa para el "buscar coincidencia" manual de un juego.
 */
@JsonClass(generateAdapter = true)
data class SearchRomDto(
    val name: String? = null,
    val slug: String? = null,
    val summary: String? = null,
    @Json(name = "igdb_id") val igdbId: Int? = null,
    @Json(name = "ss_id") val ssId: Int? = null,
    @Json(name = "moby_id") val mobyId: Int? = null,
    @Json(name = "igdb_url_cover") val igdbUrlCover: String? = null,
    @Json(name = "ss_url_cover") val ssUrlCover: String? = null,
)
