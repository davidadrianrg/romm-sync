package com.davidadrianrg.atril.desktop

import com.davidadrianrg.atril.core.i18n.tr
import java.io.IOException
import retrofit2.HttpException

/** Mensaje traducido para un fallo al cargar la biblioteca; mismos casos que `RomRepository` en Android. */
internal fun libraryLoadErrorMessage(e: Throwable): String = when (e) {
    is HttpException -> when (e.code()) {
        401, 403 -> tr("android.library.error_auth")
        404 -> tr("android.library.error_not_found")
        in 500..599 -> tr("android.library.error_server")
        else -> tr("android.library.error_unknown")
    }
    is IOException -> tr("android.library.error_network")
    else -> tr("android.library.error_unknown")
}
