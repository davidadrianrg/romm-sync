package es.davidrg.rommsync.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Iconos vectoriales propios (paths estándar de Material Design, 24dp).
 *
 * Se construyen a mano en vez de depender de material-icons-core/extended
 * porque el set básico no incluye los que esta UI necesita (download, sync,
 * gamepad, cloud…) y material-icons-extended añadiría decenas de MB al
 * uber-jar con ProGuard desactivado.
 */
object AppIcons {

    /** Deportes/e-consola: mando de juego. Icono de la app en la navegación. */
    val Gamepad: ImageVector = icon("gamepad") {
        "M21 6H3c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2z" to "M11 13H8v3H6v-3H3v-2h3V8h2v3h3v2z"
    }

    /** Flecha hacia bandeja: descarga de ROM. */
    val Download: ImageVector = icon("download") {
        "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z" to null
    }

    /** Descarga completada (check sobre bandeja). */
    val DownloadDone: ImageVector = icon("download_done") {
        "M20 20H4v-2h16v2zM12 16L5 8.94 6.62 7.26 11 11.84V2h2v9.84l4.38-4.58L18 8.94 12 16z" to null
    }

    /** Flecha desde bandeja: subida de save. */
    val Upload: ImageVector = icon("upload") {
        "M9 16h6v-6h4l-7-7-7 7h4v6z" to "M5 18v2h14v-2H5z"
    }

    /** Flechas circulares: sincronización. */
    val Sync: ImageVector = icon("sync") {
        "M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8z" to
            "M12 18c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"
    }

    /** Disquete: sección de saves. */
    val Save: ImageVector = icon("save") {
        "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4z" to null
    }

    /** Engranaje: ajustes. */
    val Settings: ImageVector = icon("settings") {
        "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58z" to
            "M12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"
    }

    /** Lupa: búsqueda. */
    val Search: ImageVector = icon("search") {
        "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z" to null
    }

    /** Flecha circular: refrescar. */
    val Refresh: ImageVector = icon("refresh") {
        "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-8 8s3.57 8 8 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z" to null
    }

    /** Papelera: eliminar. */
    val Delete: ImageVector = icon("delete") {
        "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" to null
    }

    /** Aspa: cerrar/cancelar. */
    val Close: ImageVector = icon("close") {
        "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" to null
    }

    /** Círculo con check: completado / conectado. */
    val CheckCircle: ImageVector = icon("check_circle") {
        "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" to null
    }

    /** Círculo con exclamación (contorno): error. */
    val ErrorOutline: ImageVector = icon("error_outline") {
        "M11 15h2v2h-2zm0-8h2v6h-2zm.99-5C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8z" to null
    }

    /** Círculo con aspa: cancelar tarea. */
    val Cancel: ImageVector = icon("cancel") {
        "M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z" to null
    }

    /** Flecha atrás: volver. */
    val ArrowBack: ImageVector = icon("arrow_back") {
        "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z" to null
    }

    /** Chevron arriba: volver arriba. */
    val KeyboardArrowUp: ImageVector = icon("keyboard_arrow_up") {
        "M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6z" to null
    }

    /** Carpeta: rutas / directorios. */
    val Folder: ImageVector = icon("folder") {
        "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z" to null
    }

    /** Carpeta abierta: seleccionar carpeta. */
    val FolderOpen: ImageVector = icon("folder_open") {
        "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z" to null
    }

    /** Nube con flecha arriba: gana local (subir). */
    val CloudUpload: ImageVector = icon("cloud_upload") {
        "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z" to null
    }

    /** Nube con flecha abajo: gana servidor (bajar). */
    val CloudDownload: ImageVector = icon("cloud_download") {
        "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13l-5 5-5-5h3V9h4v4h3z" to null
    }

    /** Barras de almacenamiento: plataformas. */
    val Storage: ImageVector = icon("storage") {
        "M2 20h20v-4H2v4zm2-3h2v2H4v-2zM2 4v4h20V4H2zm4 3H4V5h2v2zm-4 7h20v-4H2v2zm2-3h2v2H4v-2z" to null
    }

    /** Ojo: mostrar contraseña. */
    val Visibility: ImageVector = icon("visibility") {
        "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z" to null
    }

    /** Ojo tachado: ocultar contraseña. */
    val VisibilityOff: ImageVector = icon("visibility_off") {
        "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 21 21 19.73 3.27 2 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z" to null
    }

    /** Reloj: en cola / programado. */
    val Schedule: ImageVector = icon("schedule") {
        "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z" to null
    }

    /** Disco: badge multi-disc. */
    val Album: ImageVector = icon("album") {
        "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm3-8c0 1.66-1.34 3-3 3s-3-1.34-3-3 1.34-3 3-3 3 1.34 3 3z" to null
    }

    /** Imagen: multimedia/screenshots. */
    val Image: ImageVector = icon("image") {
        "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z" to null
    }

    /** Play: tráiler. */
    val PlayArrow: ImageVector = icon("play_arrow") {
        "M8 5v14l11-7z" to null
    }

    /** Enlace externo: abrir en navegador. */
    val OpenInNew: ImageVector = icon("open_in_new") {
        "M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z" to null
    }

    /** Triángulo con exclamación: aviso. */
    val Warning: ImageVector = icon("warning") {
        "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z" to null
    }

    /** Check sencillo. */
    val Done: ImageVector = icon("done") {
        "M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z" to null
    }

    /** Estrella: valoración IGDB. */
    val Star: ImageVector = icon("star") {
        "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" to null
    }
}

/** Construye un ImageVector de 24dp a partir de 1-2 path data de Material. */
private fun icon(name: String, paths: () -> Pair<String, String?>): ImageVector {
    val (p1, p2) = paths()
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(p1),
        fill = SolidColor(Color.Black),
        fillAlpha = 1f,
    )
    val withSecond = if (p2 != null) {
        builder.addPath(pathData = addPathNodes(p2), fill = SolidColor(Color.Black), fillAlpha = 1f)
    } else {
        builder
    }
    return withSecond.build()
}
