package `in`.aicortex.iso8583studio.ecr

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * An in-process stand-in for the terminal's ECR TCP server, behaving like the HNB app: it reads
 * one request line per connection, answers ACK, then the final reply on the same socket with no
 * newline. [reply] turns the received request line into the frames to send.
 */
class FakeEcrTerminal(
    private val gapMs: Long = 300,
    private val reply: (String) -> List<String>,
) : AutoCloseable {

    private val server = ServerSocket(0)
    val port: Int get() = server.localPort
    val received = CopyOnWriteArrayList<String>()

    private val acceptor = thread(isDaemon = true, name = "fake-ecr-terminal") {
        while (!server.isClosed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            thread(isDaemon = true) { serve(socket) }
        }
    }

    private fun serve(socket: Socket) = socket.use {
        val line = BufferedReader(InputStreamReader(it.getInputStream(), Charsets.UTF_8)).readLine() ?: return
        received += line
        val out = it.getOutputStream()
        for (frame in reply(line)) {
            out.write(frame.toByteArray(Charsets.UTF_8))
            out.flush()
            Thread.sleep(gapMs)
        }
        Thread.sleep(1_500) // keep the socket open while the library drains the reply
    }

    override fun close() {
        server.close()
        acceptor.join(1_000)
    }

    companion object {
        const val ACK = """{"dataType":"ACK"}"""
    }
}
