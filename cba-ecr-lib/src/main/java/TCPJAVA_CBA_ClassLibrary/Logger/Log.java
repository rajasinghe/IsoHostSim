package TCPJAVA_CBA_ClassLibrary.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class Log {

    private static final String LOG_FOLDER = "PAX_EDC_ECR_logs";
    private static final String LOG_FILE_EXT = ".txt";

    private static String getLogFilePath() {
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String filename = date + LOG_FILE_EXT;
        String folderPath = System.getProperty("user.dir") + File.separator + LOG_FOLDER;
        File dir = new File(folderPath);
        if (!dir.exists()) {
            dir.mkdir();
        }
        return folderPath + File.separator + filename;
    }

    private static void deleteOldLogFiles(String folderPath) {
        // Delete log files older than 7 days (from -37 to -30 days as in Utility)
        for (int i = -37; i < -30; i++) {
            String deleteFileName = new SimpleDateFormat("yyyy-MM-dd").format(new Date(System.currentTimeMillis() + i * 24 * 60 * 60 * 1000L));
            String deleteFileNameText = deleteFileName + LOG_FILE_EXT;
            String deleteFilePath = folderPath + File.separator + deleteFileNameText;
            try {
                Path fileDeletePath = Paths.get(deleteFilePath);
                Files.deleteIfExists(fileDeletePath);
            } catch (Exception e) {
                // Ignore exceptions
            }
        }
    }

    private static void writeLogEntry(String logType, String ecrCommandType, String logMessage) {
        String filePath = getLogFilePath();
        String folderPath = System.getProperty("user.dir") + File.separator + LOG_FOLDER;

        if (logType.equals("LogStart")) {
            deleteOldLogFiles(folderPath);
        }

        BufferedWriter bw = null;
        FileWriter fw = null;
        try {
            String timeStamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
            fw = new FileWriter(filePath, true);
            bw = new BufferedWriter(fw);
            if (logType.equals("LogStart")) {
                bw.write("\n-------------------------------");
                bw.write("\nLog Entry: ");
                bw.write(timeStamp);
                bw.write(": ECRCommandType: " + ecrCommandType);
                bw.write(": -->  Message :" + logMessage);
            } else {
                bw.write("\nLog Entry: ");
                bw.write(timeStamp);
                bw.write(": ECRCommandType: " + ecrCommandType);
                bw.write(": -->  Message :" + logMessage);
            }
        } catch (IOException e) {
            // Handle exception if needed
        } finally {
            try {
                if (bw != null) {
                    bw.close();
                }
                if (fw != null) {
                    fw.close();
                }
            } catch (IOException ex) {
                // Handle exception if needed
            }
        }
    }

    // Android-like logging methods
    public static void d(String tag, String message) {
        writeLogEntry("NormalLog", tag, message);
    }

    public static void i(String tag, String message) {
        writeLogEntry("NormalLog", tag, message);
    }

    public static void w(String tag, String message) {
        writeLogEntry("NormalLog", tag, message);
    }

    public static void e(String tag, String message) {
        writeLogEntry("NormalLog", tag, message);
    }

    // For starting a log session, similar to LogStart
    public static void start(String tag, String message) {
        writeLogEntry("LogStart", tag, message);
    }
}
