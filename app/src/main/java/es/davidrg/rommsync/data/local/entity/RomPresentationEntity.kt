package es.davidrg.rommsync.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Presentación de un juego en el launcher: carta o medio físico, y la imagen
 * del medio físico cacheada en el dispositivo.
 *
 * Tabla aparte de `downloaded_roms` a propósito: esa fila se reescribe con
 * REPLACE en cada re-descarga y aquí la preferencia debe sobrevivir.
 */
@Entity(tableName = "rom_presentation")
data class RomPresentationEntity(
    @PrimaryKey val romId: Int,
    /** `card`, `physical` o null (= usar el valor global de Ajustes). */
    val displayMode: String? = null,
    /** Ruta absoluta de la imagen cacheada (filesDir/physical-media). */
    val physicalMediaPath: String? = null,
    /** URL de RomM de la que se descargó (para detectar cambios). */
    val physicalMediaSource: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
