package TCPJAVA_CBA_ClassLibrary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ISO8583Studio patch: records every frame the terminal sent during one library call, exactly as
 * received, so the app can show the whole reply. The library itself only returns the final reply
 * (without its checksum), and for USB Check Status a rebuilt {"dataType":"ACK"}; the ACK frame,
 * which carries bankName, bankCode and serialNo, was only written to the log.
 *
 * Recording is per thread and only while a caller has called {@link #begin()}, so the library
 * behaves exactly as before for anyone who doesn't use it. Frames are taken from the messages the
 * library already logs for each frame it reads ({@link Utility#LogWrite}); nothing sent to or read
 * from the terminal changes.
 */
public final class TerminalFrames {

    /** What a frame was to the library. */
    public enum Kind { ACK, FINAL, INVALID }

    /** One frame from the terminal, as received (trailing NUL padding removed). */
    public static final class Frame {
        public final Kind kind;
        public final String raw;

        Frame(Kind kind, String raw) {
            this.kind = kind;
            this.raw = raw;
        }
    }

    private static final ThreadLocal<List<Frame>> RECORDING = new ThreadLocal<>();

    private TerminalFrames() {
    }

    /** Starts recording the frames of the library call about to run on this thread. */
    public static void begin() {
        RECORDING.set(new ArrayList<>());
    }

    /** Stops recording on this thread and returns what was received, in order. */
    public static List<Frame> end() {
        List<Frame> frames = RECORDING.get();
        RECORDING.remove();
        return frames == null ? Collections.emptyList() : frames;
    }

    /** Called by {@link Utility#LogWrite} with every log message; keeps the ones that carry a frame. */
    static void fromLog(String message) {
        List<Frame> frames = RECORDING.get();
        if (frames == null || message == null) {
            return;
        }
        Kind kind = kindOf(message);
        if (kind == null) {
            return;
        }
        int colon = message.indexOf(':');
        if (colon < 0) {
            return;
        }
        String raw = stripPadding(message.substring(colon + 1));
        if (!raw.isEmpty()) {
            frames.add(new Frame(kind, raw));
        }
    }

    /** The log messages the library writes right after reading a frame, and what each frame was. */
    static Kind kindOf(String message) {
        if (message.startsWith("Terminal ACK Response :")) {
            return Kind.ACK;
        }
        if (message.startsWith("Terminal Process Response")) {   // also "(A910) :" and "(A920) :"
            return Kind.FINAL;
        }
        if (message.startsWith("Terminal Response not valid :")
                || message.startsWith("Terminal Final Response Received data not valid --> Recieved data :")) {
            return Kind.INVALID;
        }
        return null;
    }

    private static String stripPadding(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\0' || Character.isWhitespace(s.charAt(end - 1)))) {
            end--;
        }
        int start = 0;
        while (start < end && Character.isWhitespace(s.charAt(start))) {
            start++;
        }
        return s.substring(start, end);
    }
}
