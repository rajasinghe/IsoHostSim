package TCPJAVA_CBA_ClassLibrary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JsonFrameTest {

    @Test
    void completeObjectsAreDetected() {
        assertTrue(JsonFrame.isCompleteJson("{\"dataType\":\"ACK\"}"));
        assertTrue(JsonFrame.isCompleteJson("{\"a\":{\"b\":\"}\"}}\n"));
        assertTrue(JsonFrame.isCompleteJson("{\"a\":\"x\\\"}\"}\0\0\0"), "escaped quote and NUL padding");
    }

    @Test
    void partialOrNonJsonIsNotComplete() {
        assertFalse(JsonFrame.isCompleteJson("{\"dataType\":\"binRequest\",\"data\":{"));
        assertFalse(JsonFrame.isCompleteJson("{\"a\":\"}"));
        assertFalse(JsonFrame.isCompleteJson("A1B2C3D4E5F6"), "encrypted hex response");
        assertFalse(JsonFrame.isCompleteJson("{\"a\":1}{\"b\":2}"), "two objects");
        assertFalse(JsonFrame.isCompleteJson(""));
        assertFalse(JsonFrame.isCompleteJson(null));
    }
}
