package lk.cba.isohostsim.update

import com.sun.net.httpserver.HttpServer
import lk.cba.isohostsim.data.model.AppSettings
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

//gimhan added
/**
 * The update check and download against a real HTTP server laid out like the Azure VM:
 * `/iso8583studio/latest.json` next to the installers. Tests run outside an installed copy, so the
 * check reports [UpdateService.CheckResult.AvailableManualOnly] rather than offering to install.
 */
class UpdateServiceHttpTest {
    private val installer = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(installer).joinToString("") { "%02x".format(it) }
    private lateinit var server: HttpServer
    private var manifest = ""

    @BeforeTest fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/iso8583studio/") { ex ->
            val body = when (ex.requestURI.path) {
                "/iso8583studio/latest.json" -> manifest.toByteArray()
                "/iso8583studio/ISO8583Studio-9.9.9.msi" -> installer
                else -> null
            }
            if (body == null) { ex.sendResponseHeaders(404, -1); ex.close(); return@createContext }
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
            ex.close()
        }
        server.start()
        AppSettings.updateUpdateSource("http://127.0.0.1:${server.address.port}/iso8583studio")
    }

    @AfterTest fun stop() {
        AppSettings.updateUpdateSource("")
        server.stop(0)
    }

    private fun manifestWith(version: String, checksum: String) {
        manifest = """{ "msi": { "version": "$version", "file": "ISO8583Studio-$version.msi", "sha256": "$checksum" } }"""
    }

    @Test fun `finds a newer version over http and downloads it intact`() {
        manifestWith("9.9.9", sha)
        val result = UpdateService.check()
        assertEquals(UpdateService.CheckResult.AvailableManualOnly("9.9.9"), result)

        var lastProgress = 0L to 0L
        val file = UpdateService.download(UpdatePackage("9.9.9", "ISO8583Studio-9.9.9.msi", sha)) { c, t -> lastProgress = c to t }
        assertContentEquals(installer, file.readBytes())
        assertEquals(installer.size.toLong() to installer.size.toLong(), lastProgress)
        file.delete()
    }

    @Test fun `the same or an older version is not offered`() {
        manifestWith("1.0.0", sha)
        assertEquals(UpdateService.CheckResult.UpToDate, UpdateService.check())
    }

    @Test fun `a corrupted download is rejected`() {
        manifestWith("9.9.9", "0".repeat(64))
        assertFailsWith<IllegalStateException> {
            UpdateService.download(UpdatePackage("9.9.9", "ISO8583Studio-9.9.9.msi", "0".repeat(64)))
        }
    }

    @Test fun `an unreachable server is reported, not thrown`() {
        server.stop(0)
        assertIs<UpdateService.CheckResult.Failed>(UpdateService.check())
    }
}
