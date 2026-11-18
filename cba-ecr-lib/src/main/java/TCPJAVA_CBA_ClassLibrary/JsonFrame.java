package TCPJAVA_CBA_ClassLibrary;

/**
 * ISO8583Studio patch: tells whether a terminal response received so far is one complete
 * JSON object, so a read loop can stop as soon as the whole message has arrived instead of
 * always waiting a further fixed second. Pure, no I/O.
 */
public final class JsonFrame {

    private JsonFrame() {
    }

    /**
     * True when {@code text}, ignoring leading/trailing whitespace and NUL padding, starts with
     * '{' and the braces outside string literals balance back to zero at the end. Encrypted
     * (hex) responses and partial JSON return false, so callers fall back to the original wait.
     */
    public static boolean isCompleteJson(String text) {
        if (text == null) {
            return false;
        }
        int start = 0;
        int end = text.length();
        while (start < end && isPadding(text.charAt(start))) {
            start++;
        }
        while (end > start && isPadding(text.charAt(end - 1))) {
            end--;
        }
        if (start >= end || text.charAt(start) != '{' || text.charAt(end - 1) != '}') {
            return false;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < end; i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && i != end - 1) {
                    return false;   // something after the first object: not a single frame
                }
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0 && !inString;
    }

    private static boolean isPadding(char ch) {
        return ch == '\0' || Character.isWhitespace(ch);
    }
}
