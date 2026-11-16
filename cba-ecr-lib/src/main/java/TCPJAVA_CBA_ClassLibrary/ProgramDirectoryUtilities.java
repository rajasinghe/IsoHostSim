/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package TCPJAVA_CBA_ClassLibrary;


import java.io.File;
import java.net.URISyntaxException;
/**
 *
 * @author HP PC
 */
public class ProgramDirectoryUtilities extends Utility {
    
    private String getJarName()
    {
        return new File(ProgramDirectoryUtilities.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath())
                .getName();
    }

    private boolean runningFromJAR()
    {
        String jarName = getJarName();
        return jarName.contains(".jar");
    }

    // ISO8583Studio patch: system property that overrides the base folder for logs and the key file.
    public static final String BASE_DIR_PROPERTY = "cba.ecr.dir";

    public String getProgramDirectory()
    {
        // ISO8583Studio patch: -Dcba.ecr.dir=<folder> wins; otherwise the original jar-folder / cwd logic.
        String overrideDir = System.getProperty(BASE_DIR_PROPERTY);
        if (overrideDir != null && !overrideDir.trim().isEmpty())
        {
            return overrideDir.trim();
        }
        if (runningFromJAR())
        {
            return getCurrentJARDirectory();
        } else
        {
            return getCurrentProjectDirectory();
        }
    }

    private String getCurrentProjectDirectory()
    {
        return new File("").getAbsolutePath();
    }

    private String getCurrentJARDirectory()
    {
        try
        {
            return new File(ProgramDirectoryUtilities.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getParent();
        } catch (URISyntaxException exception)
        {
            exception.printStackTrace();
            LogWrite("NormalLog", "getCurrentJARDirectory", "URISyntaxException occurs :" + exception.getMessage());
        }

        return null;
    }
    
}
