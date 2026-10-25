package es.davidrg.rommsync.core.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Guardado subido al servidor (GET /api/saves). */
@JsonClass(generateAdapter = true)
data class SaveDto(
    val id: Int,
    @Json(name = "rom_id") val romId: Int = 0,
    @Json(name = "file_name") val fileName: String = "",
    val emulator: String? = null,
    val slot: String? = null,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long = 0,
    @Json(name = "updated_at") val updatedAt: String? = null,
)

/** Save state subido al servidor (GET /api/states). */
@JsonClass(generateAdapter = true)
data class StateDto(
    val id: Int,
    @Json(name = "rom_id") val romId: Int = 0,
    @Json(name = "file_name") val fileName: String = "",
    val emulator: String? = null,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long = 0,
    @Json(name = "updated_at") val updatedAt: String? = null,
)

/** Body opcional de POST /api/roms/upload/start (subida a ROM existente). */
@JsonClass(generateAdapter = true)
data class UploadStartRequest(
    @Json(name = "rom_id") val romId: Int? = null,
    val folder: String? = null,
    val filename: String? = null,
    val overwrite: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class UploadStartResponse(
    @Json(name = "upload_id") val uploadId: String,
)

/** Body de POST /api/tasks/scan (escaneo rápido de plataformas concretas). */
@JsonClass(generateAdapter = true)
data class ScanRequest(
    val type: String = "quick",
    val platforms: List<Int> = emptyList(),
)
