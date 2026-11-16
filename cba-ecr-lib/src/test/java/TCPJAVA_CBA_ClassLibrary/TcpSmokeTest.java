package TCPJAVA_CBA_ClassLibrary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drives the unmodified TCP request flow against an in-process fake terminal. */
class TcpSmokeTest {

    @TempDir
    static Path logDir;

    private static String previousDir;

    @BeforeAll
    static void pointLogsAtTempDir() {
        previousDir = System.getProperty("cba.ecr.dir");
        System.setProperty("cba.ecr.dir", logDir.toString());
    }

    @AfterAll
    static void restore() {
        if (previousDir == null) {
            System.clearProperty("cba.ecr.dir");
        } else {
            System.setProperty("cba.ecr.dir", previousDir);
        }
    }

    /**
     * Accepts one connection, reads the request line, replies ACK, then (after a pause so the
     * library's ACK read does not swallow it) the final response. Completes with the request.
     */
    private static CompletableFuture<JSONObject> fakeTerminal(ServerSocket server, String finalResponse) {
        return CompletableFuture.supplyAsync(() -> {
            try (Socket client = server.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                String requestLine = in.readLine();
                OutputStream out = client.getOutputStream();
                out.write("{\"dataType\":\"ACK\"}".getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(300);
                out.write(finalResponse.getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(1500); // let the library finish its 1 s drain read before we close
                return new JSONObject(requestLine);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void doSaleReturnsTerminalFinalData() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            JSONObject finalData = new JSONObject()
                    .put("rrn", "123456789012")
                    .put("approvalCode", "A1B2C3")
                    .put("responseCode", "00");
            String finalResponse = new JSONObject()
                    .put("dataType", "sale")
                    .put("checksum", "ignored-by-client")
                    .put("data", finalData)
                    .toString();
            CompletableFuture<JSONObject> terminal = fakeTerminal(server, finalResponse);

            JSONObject sale = new JSONObject()
                    .put("dataType", "sale")
                    .put("data", new JSONObject().put("amount", "000000001000").put("ecrRef", "R1"));

            JSONObject response = new TCPJAVA_CBA_ClassLibrary()
                    .RequestDoSale("127.0.0.1", server.getLocalPort(), sale, false, 10);

            JSONObject request = terminal.get(10, TimeUnit.SECONDS);
            assertEquals("sale", request.getString("dataType"));
            assertNotNull(request.optString("checksum", null), "sale request carries a checksum");
            assertEquals(new Utility().sha256(request.getJSONObject("data").toString()), request.getString("checksum"));

            assertEquals("sale", response.getString("dataType"), response.toString());
            assertFalse(response.has("checksum"));
            assertEquals("123456789012", response.getJSONObject("data").getString("rrn"));
            assertEquals("A1B2C3", response.getJSONObject("data").getString("approvalCode"));
            assertEquals("00", response.getJSONObject("data").getString("responseCode"));
        }
        assertFalse(Files.list(logDir).findAny().isEmpty(), "logs written under cba.ecr.dir");
    }

    @Test
    void settlementSendsSettleRequest() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            String finalResponse = new JSONObject()
                    .put("dataType", "settleRequest")
                    .put("data", new JSONObject().put("status", "SETTLED"))
                    .toString();
            CompletableFuture<JSONObject> terminal = fakeTerminal(server, finalResponse);

            JSONObject response = new TCPJAVA_CBA_ClassLibrary()
                    .RequestDoSettlement("127.0.0.1", server.getLocalPort(), false);

            JSONObject request = terminal.get(10, TimeUnit.SECONDS);
            assertEquals("settleRequest", request.getString("dataType"));
            assertEquals("settleRequest", request.getJSONObject("data").getString("function"));
            assertFalse(request.has("checksum"), "settlement carries no checksum");

            assertEquals("settleRequest", response.getString("dataType"), response.toString());
            assertEquals("SETTLED", response.getJSONObject("data").getString("status"));
        }
    }
}
