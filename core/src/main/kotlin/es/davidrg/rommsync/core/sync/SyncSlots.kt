package es.davidrg.rommsync.core.sync

/**
 * Slot estable que se usa para los saves automáticos.
 *
 * RomM solo empareja saves con `slot` no nulo en la negociación: los saves
 * con slot nulo se consideran subidas manuales/archivo y NUNCA se ofrecen
 * como descarga. Sin un slot estable la sincronización solo sube.
 */
object SyncSlots {
    const val DEFAULT = "default"
}
