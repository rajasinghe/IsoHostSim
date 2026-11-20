package TCPJAVA_CBA_ClassLibrary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

// gimhan added
class TerminalFramesTest {

    @Test
    void framesAreRecordedAsReceivedWhileRecording() {
        TerminalFrames.begin();
        TerminalFrames.fromLog("Terminal ACK Response :{\"dataType\":\"ACK\",\"bankName\":\"HNB\",\"serialNo\":\"2841048495\"}\0\0\0");
        TerminalFrames.fromLog("Sent Command to terminal successfully");
        TerminalFrames.fromLog("Terminal Process Response(A910) :{\"checksum\":\"AB\",\"data\":{\"status\":\"A\"},\"dataType\":\"binRequest\"}");
        List<TerminalFrames.Frame> frames = TerminalFrames.end();

        assertEquals(2, frames.size());
        assertEquals(TerminalFrames.Kind.ACK, frames.get(0).kind);
        assertEquals("{\"dataType\":\"ACK\",\"bankName\":\"HNB\",\"serialNo\":\"2841048495\"}", frames.get(0).raw);
        assertEquals(TerminalFrames.Kind.FINAL, frames.get(1).kind);
        assertTrue(frames.get(1).raw.contains("\"checksum\":\"AB\""), "the checksum is kept");
    }

    @Test
    void nothingIsRecordedUnlessACallerStartedRecording() {
        TerminalFrames.fromLog("Terminal ACK Response :{\"dataType\":\"ACK\"}");
        assertTrue(TerminalFrames.end().isEmpty());
    }

    @Test
    void onlyFrameMessagesAreKept() {
        assertEquals(TerminalFrames.Kind.INVALID, TerminalFrames.kindOf("Terminal Response not valid :xx"));
        assertNull(TerminalFrames.kindOf("Terminal Status Ok"));
        assertNull(TerminalFrames.kindOf("Terminal Response Timeout"));
    }
}
