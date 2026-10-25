package es.davidrg.rommsync.core.upload

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * Partes multipart para subir guardados/estados desde la UI de ambas apps
 * (así desktop y Android no necesitan tocar okhttp directamente).
 */
object UploadParts {

    private val OCTET = "application/octet-stream".toMediaType()

    fun saveFile(file: File): MultipartBody.Part =
        MultipartBody.Part.createFormData("saveFile", file.name, file.asRequestBody(OCTET))

    fun stateFile(file: File): MultipartBody.Part =
        MultipartBody.Part.createFormData("stateFile", file.name, file.asRequestBody(OCTET))
}
