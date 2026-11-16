package TCPJAVA_CBA_ClassLibrary.Exceptions;

public class DeviceExceptions extends Exception {
    private  String message;
    private String code;

    public DeviceExceptions(String message,String code) {
        this.message = message;
        this.code = code;
    }

    public String getMessage() {
         return message;
    }

    public String getCode() {
        return code;
    }

    public static class SendException extends DeviceExceptions {
        public SendException(String message) {
            super(message, "17");
        }
    }


}
