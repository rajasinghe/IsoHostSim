/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TCPJAVA_CBA_ClassLibrary;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 */
public class Utility {

    String KeyEncrypt_Key = "8cc9aafc78f9a0a5ee634b5c212b614a";
    
    
        //tetsing log create based date
    public void LogWrite(String LogType, String ECRCommandType, String logMessage) {
              
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = new Date();
        String filename = dateFormat.format(date);
        String filenametext = filename + ".txt";
       
        String foldername = "PAX_EDC_ECR_logs";
        
        ProgramDirectoryUtilities programDirectoryUtilities = new ProgramDirectoryUtilities();
        String AbsolutePath = programDirectoryUtilities.getProgramDirectory();
        String folderPath = AbsolutePath + File.separator + foldername;   // ISO8583Studio patch: was "\\"
            
        File directory = new File(folderPath);
        if (! directory.exists()){
            directory.mkdir();
        }
                     
        String FILENAME = folderPath + File.separator + filenametext;   // ISO8583Studio patch: was "\\"
        
        if (LogType.equals("LogStart")) {

            deleteLogFile(folderPath);
            LogStart(ECRCommandType, logMessage, FILENAME);
            
        } else if (LogType.equals("NormalLog")) {

            Log(ECRCommandType, logMessage, FILENAME);
        }          
    }
    
    
    public void deleteLogFile(String folderPath){
        
        //delete erlier text files    
        // loop adding one day in each iteration      
        //System.out.println("starting");
        
        for(int i = -37; i< -30; i++){
            
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        
            Calendar cal = Calendar.getInstance();
            
            //System.out.println(i);
            cal.add(Calendar.DAY_OF_YEAR, i);                  
            //System.out.println(dateFormat.format(cal.getTime()));
            
            String deleteFileName = dateFormat.format(cal.getTime());
            String deleteFileNameText = deleteFileName + ".txt";
            
            String deleteFilePath = folderPath + File.separator + deleteFileNameText;   // ISO8583Studio patch: was "\\"
            
            try {
                
               Path fileDeletepath = Paths.get(deleteFilePath);
               boolean value = Files.deleteIfExists(fileDeletepath);
               if(value) {
                 System.out.println("file successfully deleted.");
               }
               else {
                 //System.out.println("File doesn't exit");
               }
               
            } catch (Exception e) {
              e.getStackTrace();
            }           
        }
        
    }
    
        //end    
    
    //Write log for transaction
//    public void LogWrite(String LogType, String ECRCommandType, String logMessage) {
//
//        
//        //String filename = "PAX_EDC_ECR_log.log";
//        //String FILENAME = "C:\\PAX_ECR\\" + filename;
//      
//        String filename = "PAX_EDC_ECR_log.log";
//        ProgramDirectoryUtilities programDirectoryUtilities = new ProgramDirectoryUtilities();
//        String AbsolutePath = programDirectoryUtilities.getProgramDirectory();
//        String FILENAME = AbsolutePath + "\\" + filename;
//              
//        if (LogType.equals("LogStart")) {
//
//            LogStart(ECRCommandType, logMessage, FILENAME);
//
//        } else if (LogType.equals("NormalLog")) {
//
//            Log(ECRCommandType, logMessage, FILENAME);
//        }
//    }

    //Write log for transaction
    public void LogStart(String ECRCommandType, String logMessage, String FILENAME) {

        BufferedWriter bw = null;
        FileWriter fw = null;
        try {
            String timeStamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
            fw = new FileWriter(FILENAME, true);
            bw = new BufferedWriter(fw);
            bw.write("\n-------------------------------");
            bw.write("\nLog Entry: ");
            bw.write(timeStamp);
            bw.write(": ECRCommandType: " + ECRCommandType);
            bw.write(": -->  Message :" + logMessage);
        } catch (IOException e) {
            //LogWrite("NormalLog", "LogStart", "IOException occurs :" + e.getMessage());
        } finally {
            try {
                if (bw != null) {
                    bw.close();
                }
                if (fw != null) {
                    fw.close();
                }
            } catch (IOException ex) {
                //LogWrite("NormalLog", "LogStart", "IOException2 occurs :" + ex.getMessage());
            }
        }

    }

    //Write log for transaction
    public void Log(String ECRCommandType, String logMessage, String FILENAME) {
        
        int lineCount;
        
//        try {
//            lineCount = count(FILENAME);
//                       
//            if(lineCount >= 50000){
//                
//                try (PrintWriter writer = new PrintWriter(FILENAME)) {
//                    writer.print("");
//                    writer.close();
//                }               
//            }                       
//            
//        } catch (IOException ex) {
//
//        }
        
        BufferedWriter bw = null;
        FileWriter fw = null;
        try {
            String timeStamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
            fw = new FileWriter(FILENAME, true);
            bw = new BufferedWriter(fw);
            bw.write("\nLog Entry: ");
            bw.write(timeStamp);
            bw.write(": ECRCommandType: " + ECRCommandType);
            bw.write(": -->  Message :" + logMessage);
        } catch (IOException e) {
            //LogWrite("NormalLog", "Log", "IOException occurs :" + e.getMessage());
        } finally {
            try {
                if (bw != null) {
                    bw.close();
                }
                if (fw != null) {
                    fw.close();
                }
            } catch (IOException ex) {
                //LogWrite("NormalLog", "Log", "IOException2 occurs :" + ex.getMessage());
            }
        }

    }
    
    
    public int count(String filename) throws IOException {
    InputStream is = new BufferedInputStream(new FileInputStream(filename));
    try {
        byte[] c = new byte[1024];
        int count = 0;
        int readChars = 0;
        boolean endsWithoutNewLine = false;
        while ((readChars = is.read(c)) != -1) {
            for (int i = 0; i < readChars; ++i) {
                if (c[i] == '\n')
                    ++count;
            }
            endsWithoutNewLine = (c[readChars - 1] != '\n');
        }
        if(endsWithoutNewLine) {
            ++count;
        } 
        return count;
    } finally {
        is.close();
    }
}

    //Write log for Store Key
    public boolean KeyLogWrite(String ECRCommandType, String logMessage) {

        //String filename = "PAX_EDC_ECR_Key.log";
        //String FILENAME = "C:\\PAX_ECR\\" + filename;
        
        String filename = "PAX_EDC_ECR_Key.log";
        ProgramDirectoryUtilities programDirectoryUtilities = new ProgramDirectoryUtilities();
        String AbsolutePath = programDirectoryUtilities.getProgramDirectory();
        String FILENAME = AbsolutePath + File.separator + filename;   // ISO8583Studio patch: was "\\"

        BufferedWriter bw = null;
        FileWriter fw = null;
        try {
            String timeStamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
            fw = new FileWriter(FILENAME, true);
            bw = new BufferedWriter(fw);
            bw.write("\nLog Entry: ");
            bw.write(timeStamp);
            bw.write(": ECRCommandType: " + ECRCommandType);
            bw.write("\n" + logMessage);
        } catch (IOException e) {
            LogWrite("NormalLog", "KeyLogWrite", "IOException occurs :" + e.getMessage());
            return false;
        } finally {
            try {
                if (bw != null) {
                    bw.close();
                }
                if (fw != null) {
                    fw.close();
                }
            } catch (IOException ex) {
                LogWrite("NormalLog", "KeyLogWrite", "IOException2 occurs :" + ex.getMessage());
                return false;
            }
        }

        return true;

    }

    //calculate sha256 
    public  String sha256(final String base) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(base.getBytes("UTF-8"));
            final StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < hash.length; i++) {
                final String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException ex) {
            LogWrite("NormalLog", "sha256", "Exception occurs :" + ex.getMessage());
            return "";
        }
    }
    
    
    //read last line in key file
    public String ReadLastLineKeyFile() {


        //String filename = "PAX_EDC_ECR_Key.log";
        //String FILENAME = "C:\\PAX_ECR\\" + filename;
          
        String filename = "PAX_EDC_ECR_Key.log";
        ProgramDirectoryUtilities programDirectoryUtilities = new ProgramDirectoryUtilities();
        String AbsolutePath = programDirectoryUtilities.getProgramDirectory();
        String FILENAME = AbsolutePath + File.separator + filename;   // ISO8583Studio patch: was "\\"

        File file = new File(FILENAME);
        RandomAccessFile fileHandler = null;
        try {
            fileHandler = new RandomAccessFile(file, "r");
            long fileLength = fileHandler.length() - 1;
            StringBuilder sb = new StringBuilder();

            for (long filePointer = fileLength; filePointer != -1; filePointer--) {
                fileHandler.seek(filePointer);
                int readByte = fileHandler.readByte();

                if (readByte == 0xA) {
                    if (filePointer == fileLength) {
                        continue;
                    }
                    break;

                } else if (readByte == 0xD) {
                    if (filePointer == fileLength - 1) {
                        continue;
                    }
                    break;
                }

                sb.append((char) readByte);
            }

            String lastLine = sb.reverse().toString();
            return lastLine;
        } catch (Exception ex) {
            LogWrite("NormalLog", "ReadLastLineKeyFile", "Exception occurs :" + ex.getMessage());
            return "";      
        } finally {
            if (fileHandler != null) {
                try {
                    fileHandler.close();
                } catch (IOException e) {                   
                    LogWrite("NormalLog", "ReadLastLineKeyFile", "IOException occurs :" + e.getMessage());
                    return "";
                    /* ignore */
                }
            }
        }
    }

    public String byte2HexStr(byte[] var0, int offset, int length) {

        int var3 = 0;
        try {
            if (var0 == null) {
                return "";
            } else {
                String var1 = "";
                StringBuilder var2 = new StringBuilder();

                for (var3 = offset; var3 < (offset + length); ++var3) {
                    var1 = Integer.toHexString(var0[var3] & 255);
                    var2.append(var1.length() == 1 ? "0" + var1 : var1);
                }

                return var2.toString().toUpperCase().trim();
            }
        } catch (Exception ex) {
            LogWrite("NormalLog", "byte2HexStr", "Exception occurs :" + ex.getMessage());
            ex.printStackTrace();
        }

        return "";

    }

    public static String bcd2Ascii(byte[] bcd) {
        if (bcd == null) {
            return "";
        } else {
            StringBuilder sb = new StringBuilder(bcd.length << 1);
            byte[] var2 = bcd;
            int var3 = bcd.length;

            for (int var4 = 0; var4 < var3; ++var4) {
                byte ch = var2[var4];
                byte half = (byte) (ch >> 4);
                sb.append((char) (half + (half > 9 ? 55 : 48)));
                half = (byte) (ch & 15);
                sb.append((char) (half + (half > 9 ? 55 : 48)));
            }

            return sb.toString();
        }
    }

    public static String byte2HexStr(byte[] var0) {
        if (var0 == null) {
            return "";
        } else {
            String var1 = "";
            StringBuilder var2 = new StringBuilder();

            for (int var3 = 0; var3 < var0.length; ++var3) {
                var1 = Integer.toHexString(var0[var3] & 255);
                var2.append(var1.length() == 1 ? "0" + var1 : var1);
            }

            return var2.toString().toUpperCase().trim();
        }
    }

    public static byte[] hexStr2Byte(String hexString) {
        //        Log.d(TAG, "hexStr2Byte:" + hexString);
        if (hexString == null || hexString.length() == 0) {
            return new byte[]{0};
        }
        String hexStrTrimed = hexString.replace(" ", "");
        //        Log.d(TAG, "hexStr2Byte:" + hexStrTrimed);
        {
            String hexStr = hexStrTrimed;
            int len = hexStrTrimed.length();
            if ((len % 2) == 1) {
                hexStr = hexStrTrimed + "0";
                ++len;
            }
            char highChar, lowChar;
            int high, low;
            byte result[] = new byte[len / 2];
            String s;
            for (int i = 0; i < hexStr.length(); i++) {
                // read 2 chars to convert to byte
                //                s = hexStr.substring(i,i+2);
                //                int v = Integer.parseInt(s, 16);
                //
                //                result[i/2] = (byte) v;
                //                i++;
                // read high byte and low byte to convert
                highChar = hexStr.charAt(i);
                lowChar = hexStr.charAt(i + 1);
                high = CHAR2INT(highChar);
                low = CHAR2INT(lowChar);
                result[i / 2] = (byte) (high * 16 + low);
                i++;
            }
            return result;

        }
    }

    public static byte[] hexStr2Byte(String hexString, int beginIndex, int length) {
        if (hexString == null || hexString.length() == 0) {
            return new byte[]{0};
        }
        {
            if (length > hexString.length()) {
                length = hexString.length();
            }
            String hexStr = hexString;
            int len = length;
            if ((len % 2) == 1) {
                hexStr = hexString + "0";
                ++len;
            }
            byte result[] = new byte[len / 2];
            String s;
            for (int i = beginIndex; i < len; i++) {
                s = hexStr.substring(i, i + 2);
                int v = Integer.parseInt(s, 16);

                result[i / 2] = (byte) v;
                i++;
            }
            return result;

        }
    }

    public static byte HEX2DEC(int hex) {
        return (byte) ((hex / 10) * 16 + hex % 10);
    }

    public static int DEC2INT(byte dec) {
        int high = ((0x007F & dec) >> 4);
        if (0 != (0x0080 & dec)) {
            high += 8;
        }
        return (high) * 10 + (dec & 0x0F);
    }

    public static int CHAR2INT(char c) {
        if ((c >= '0' && c <= '9')
                || (c == '=')) {
            return c - '0';
        } else if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        } else if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        } else {
            return 0;
        }

    }

    public static String getValueofTag(String TLVString, String tag) {
        String data = "";

        if (TLVString == null || tag == null) {
            return null;
        }

        int index = TLVString.indexOf(tag);
        if (index > 0) {
            //found the tag
            //get the next byte for the length
            String tagLenString = TLVString.substring(index + tag.length(), index + tag.length() + 2);
            int inLen = Integer.valueOf(tagLenString);
            int index_to_data = index + tag.length() + tagLenString.length();

            //extract the data portion from the tlv
            data = TLVString.substring(index_to_data, index_to_data + (inLen * 2));
            return data;
        }

        return null;
    }

    public static String getCurrentTime() {
        SimpleDateFormat dt = new SimpleDateFormat("HH/mm/ss");
        String currentTime = dt.format(new Date());
        return currentTime;
    }

    public static String getTimeFormatted(String rawTime) {
        rawTime = rawTime.substring(0, 2) + ":" + rawTime.substring(2, 4) + ":" + rawTime.substring(4, 6);
        return rawTime;
    }

    public static String getDateFormatted(String rawDate) {
        rawDate = rawDate.substring(0, 2) + "/" + rawDate.substring(2, 4);
        return rawDate;
    }

    public static String getCurrentDate() {
        SimpleDateFormat dt = new SimpleDateFormat("dd/MM/yy");
        String currentDate = dt.format(new Date());
        currentDate = currentDate.substring(0, currentDate.length() - 3);
        return currentDate;
    }

    public static String asciiToString(String asciiString) {
        int start = 0;
        int end = 2;

        String char_value = "";
        String str = "";

        while (end <= asciiString.length()) {
            char_value = asciiString.substring(start, end);
            int val = Integer.valueOf(char_value, 16);
            str += Character.toString((char) val);
            start = end;
            end += 2;
        }

        return str;
    }

    public static String StringToAscii(String str) {
        String asciiString = null;

        for (int i = 0; i < str.length(); i++) {
            int val = str.charAt(i);
            String asciiVal = String.valueOf(val);
            asciiString += asciiVal;
        }

        return asciiString;
    }

    public static byte[] xorArrays(byte[] a, byte[] b) {
        byte[] xor = new byte[a.length];

        for (int i = 0; i < a.length; i++) {
            xor[i] = (byte) (a[i] ^ b[i]);
        }

        return xor;
    }

    public static String convertHexToBinary(String hex) {

        StringBuilder binStrBuilder = new StringBuilder();
        int c = 1;
        for (int i = 0; i < hex.length() - 1; i += 2) {

            String output = hex.substring(i, (i + 2));
            int decimal = Integer.parseInt(output, 16);

            String binStr = Integer.toBinaryString(decimal);
            int len = binStr.length();
            StringBuilder sbf = new StringBuilder();

            if (len < 8) {

                for (int k = 0; k < (8 - len); k++) {
                    sbf.append("0");
                }
                sbf.append(binStr);
            } else {
                sbf.append(binStr);
            }

            c++;
            binStrBuilder.append(sbf.toString());
        }

        return binStrBuilder.toString();
    }

    public static boolean isWithinBinRange(String pan, String lBound, String hBound) {
        int intLBound = Integer.valueOf(lBound);
        int intHBound = Integer.valueOf(hBound);

        pan = pan.substring(0, 6);
        int iPan = Integer.valueOf(pan);

        return (iPan >= intLBound && iPan < intHBound);
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int readbytes = 0;

        while ((readbytes = in.read(buffer)) > 0) {
            out.write(buffer, 0, readbytes);
        }

    }

    public static boolean copyFile(String fromPath, String toPath) {
        byte[] buffer = new byte[1024];
        try {
            FileInputStream in = new FileInputStream(fromPath);
            FileOutputStream out = new FileOutputStream(toPath);

            int readBytes = 0;
            while ((readBytes = in.read(buffer)) > 0) {
                out.write(buffer, 0, readBytes);
            }

            if (in != null) {
                in.close();
                in = null;
            }

            if (out != null) {
                out.flush();
                out.close();
                out = null;
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }

        return true;
    }

    public byte[] decrypt3Des(String encData, String key) throws Exception {
        final byte[] keydata1 = Utility.hexStr2Byte(key);
        SecretKeySpec symKey1 = new SecretKeySpec(keydata1, "DESede");

        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");

            cipher.init(Cipher.DECRYPT_MODE, symKey1);

            byte[] encrypted = cipher.doFinal(Utility.hexStr2Byte(encData));
            return encrypted;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException | InvalidKeyException e) {
            LogWrite("NormalLog", "decrypt3Des", "Exception occurs :" + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public byte[] Keyencrypt(String message, String Key) throws Exception {
        final MessageDigest md = MessageDigest.getInstance("md5");
        final byte[] digestOfPassword = md.digest(Key
                .getBytes("utf-8"));
        final byte[] keyBytes = Arrays.copyOf(digestOfPassword, 24);
        for (int j = 0, k = 16; j < 8;) {
            keyBytes[k++] = keyBytes[j++];
        }

        final SecretKey key = new SecretKeySpec(keyBytes, "DESede");
        final IvParameterSpec iv = new IvParameterSpec(new byte[8]);
        final Cipher cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);

        final byte[] plainTextBytes = message.getBytes("utf-8");
        final byte[] cipherText = cipher.doFinal(plainTextBytes);
        // final String encodedCipherText = new sun.misc.BASE64Encoder()
        // .encode(cipherText);

        return cipherText;
    }

    public String Keydecrypt(byte[] message, String Key) throws Exception {
        final MessageDigest md = MessageDigest.getInstance("md5");
        final byte[] digestOfPassword = md.digest(Key
                .getBytes("utf-8"));
        final byte[] keyBytes = Arrays.copyOf(digestOfPassword, 24);
        for (int j = 0, k = 16; j < 8;) {
            keyBytes[k++] = keyBytes[j++];
        }

        final SecretKey key = new SecretKeySpec(keyBytes, "DESede");
        final IvParameterSpec iv = new IvParameterSpec(new byte[8]);
        final Cipher decipher = Cipher.getInstance("DESede/CBC/PKCS5Padding");
        decipher.init(Cipher.DECRYPT_MODE, key, iv);

        // final byte[] encData = new
        // sun.misc.BASE64Decoder().decodeBuffer(message);
        final byte[] plainText = decipher.doFinal(message);

        return new String(plainText, "UTF-8");
    }

    public String GetKeyFromLog() {
        
        String KeyDataFromLog = ReadLastLineKeyFile();

        if (KeyDataFromLog.equals("")) {
            return "";
        }

        byte[] EncryptedBytesFromLog = hexStr2Byte(KeyDataFromLog);

        String DecryptedKeyString = null;

        try {
            DecryptedKeyString = Keydecrypt(EncryptedBytesFromLog, KeyEncrypt_Key);
        } catch (Exception ex) {
           return "";
        }

        if (DecryptedKeyString.equals("")) {
            return "";
        }
        
        return DecryptedKeyString;
    }

}
