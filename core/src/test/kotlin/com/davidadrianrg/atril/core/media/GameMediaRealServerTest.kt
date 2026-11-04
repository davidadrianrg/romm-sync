package com.davidadrianrg.atril.core.media

import com.davidadrianrg.atril.core.remote.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Prueba real SOLO DE LECTURA contra RomM con un juego de prueba (Gex). Escribe únicamente
 * en una carpeta temporal. Se omite salvo que existan ATRIL_E2E_URL, ATRIL_E2E_KEY y
 * ATRIL_E2E_MEDIA_ROM_ID. Nunca guarda credenciales en el repo.
 */
class GameMediaRealServerTest {
    private fun env(n: String) = System.getenv(n)?.takeIf { it.isNotBlank() }

    @Test
    fun `real media of a test game is downloaded and non empty`() = runBlocking {
        val url = env("ATRIL_E2E_URL"); val key = env("ATRIL_E2E_KEY"); val id = env("ATRIL_E2E_MEDIA_ROM_ID")?.toIntOrNull()
        assumeTrue("e2e env not set", url != null && key != null && id != null)
        val dto = NetworkModule.createApiService(url!!, key!!).getRom(id!!)
        val root = Files.createTempDirectory("atril-media-e2e").toFile()
        try {
            val report = GameMediaDownloader(OkHttpClient(), key, url).run(dto, root.path)
            val files = root.walkTopDown().filter { it.isFile }.toList()
            println("e2e media: downloaded=${report.downloaded} failed=${report.failed.size} files=" +
                files.joinToString { it.relativeTo(root).path + "(" + it.length() + "B)" })
            assertTrue("something was downloaded", report.downloaded > 0)
            assertTrue("metadata json written", files.any { it.name.endsWith(".json") })
            assertTrue("no empty files", files.all { it.length() > 0 })
        } finally {
            root.deleteRecursively()
        }
    }
}
