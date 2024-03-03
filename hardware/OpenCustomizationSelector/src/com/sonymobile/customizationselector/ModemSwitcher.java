package com.sonymobile.customizationselector;

import android.content.Context;
import android.os.Environment;
import android.provider.Settings;
import com.sonymobile.miscta.MiscTaException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

public class ModemSwitcher {

    private static final String TAG = "ModemSwitcher";

    private static final int MODEM_COMMAND_UNIT = 2405;
    private static final int MODEM_MAGIC_COMMAND_LENGTH = 3;
    private static final byte MODEM_COMMAND_CHANGE = (byte) 1;
    private static final byte MODEM_MISC_TA_MAGIC1 = (byte) -16;
    private static final byte MODEM_MISC_TA_MAGIC2 = (byte) 122;
    private static final int TA_FOTA_INTERNAL = 2404;

    private static final String DEFAULT_MODEM = "default";
    private static final String RESET_MODEM_ST1 = "reset_modemst1";
    private static final String RESET_MODEM_ST2 = "reset_modemst2";

    public static final String MODEM_FS_PATH = Environment.getRootDirectory() + "/etc/customization/modem/";
    public static final String MODEM_REPORT_FILE = "/cache/modem/modem_switcher_report";
    public static final String MODEM_STATUS_FILE = "/cache/modem/modem_switcher_status";
    public static final String SINGLE_MODEM_FS = "single_filesystem";

    public static final String CS_REAPPLY_MODEM = "cs_re_apply_modem";

    private static final int MAXIMUM_STATUS_FILE_LENGTH = 128;
    public static final int UA_MODEM_SWITCHER_STATUS_SUCCESS = 0;

    private String[] mCachedModemConfigurations = null;
    private boolean mConfigurationSet = false;

    private static class ModemFilter implements FilenameFilter {
        private final String modemST1Name;
        private final String modemST2Name;

        ModemFilter(String st1Name, String st2Name) {
            modemST1Name = st1Name;
            modemST2Name = st2Name;
        }

        public boolean accept(File file, String fileName) {
            if (fileName.equals(modemST1Name) || fileName.equals(modemST2Name))
                return false;
            return fileName.endsWith(ModemConfiguration.MODEM_APPENDIX);
        }
    }

    public static class ModemStatusContent {
        final String success;
        final String currentModem;

        ModemStatusContent(String statusSuccess, String modem) {
            success = statusSuccess;
            currentModem = modem;
        }
    }

    public static class ModemStatus {
        final boolean success;
        final String currentModem;

        ModemStatus(boolean statusSuccess, String modem) {
            success = statusSuccess;
            currentModem = modem;
        }
    }

    private static String lookupSymlinkTarget(String filename) {
        String resolvedName;
        try {
            resolvedName = new File(MODEM_FS_PATH, filename).getCanonicalFile().getName();
        } catch (IOException e) {
            CSLog.e(TAG, "Error when getting canonical File: ", e);
            resolvedName = filename;
        }
        CSLog.d(TAG, "Target filename of: " + filename + " is: " + resolvedName);
        return resolvedName;
    }

    private static String readLineFromFile(File file) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            try {
                return reader.readLine();
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            CSLog.e(TAG, "Failed to read from " + file + ": " + e);
        }
        return null;
    }

    /** Read the modem status file and returns its content if it is valid, else null */
    public static ModemStatusContent readModemStatusFile() {
        File statusFile = new File(MODEM_STATUS_FILE);

        if (!statusFile.isFile()) {
            CSLog.e(TAG, "Status file does not exists or is not a file.");
            return null;
        } else if (statusFile.length() > MAXIMUM_STATUS_FILE_LENGTH) {
            CSLog.e(TAG, "Status file is too large: " + statusFile.length() + ", more than limit: " + MAXIMUM_STATUS_FILE_LENGTH);
            return null;
        }
        String line = readLineFromFile(statusFile);
        if (line == null) {
            CSLog.e(TAG, "Line is null");
            return null;
        }
        CSLog.d(TAG, "Read line: " + line);
        String[] values = line.split(",");

        if (values.length != 2) {
            CSLog.e(TAG, "Format error status file, nbr of fields found:" + values.length);
            return null;
        }
        return new ModemStatusContent(values[0], values[1]);
    }

    /** Read and parse the modem status file. Return null if invalid or error */
    public static ModemStatus readModemAndStatus() {
        try {
            ModemStatusContent content = readModemStatusFile();
            if (content != null)
                return new ModemStatus(Integer.parseInt(content.success) == UA_MODEM_SWITCHER_STATUS_SUCCESS, content.currentModem);
        } catch (Exception e) {
            CSLog.e(TAG, "Failed to read FOTA STATUS() " + e);
        }
        return null;
    }

    public String[] getAvailableModemConfigurations() {
        if (mCachedModemConfigurations == null) {
            File modemsDir = new File(MODEM_FS_PATH);
            if (!modemsDir.isDirectory()) {
                CSLog.e(TAG, "Could not open directory: " + MODEM_FS_PATH);
                mCachedModemConfigurations = new String[]{SINGLE_MODEM_FS};
            } else {
                String[] modemFiles = modemsDir.list(new ModemFilter(lookupSymlinkTarget(RESET_MODEM_ST1), lookupSymlinkTarget(RESET_MODEM_ST2)));
                if (modemFiles != null && modemFiles.length > 0) {
                    Arrays.sort(modemFiles, Comparator.comparingInt(String::length));
                    for (int i = 0; i < modemFiles.length; i++)
                        modemFiles[i] = MODEM_FS_PATH + modemFiles[i];
                    mCachedModemConfigurations = modemFiles;
                } else {
                    CSLog.e(TAG, "Could not get list of available modem filesystems");
                    mCachedModemConfigurations = new String[]{SINGLE_MODEM_FS};
                }
            }
        }
        return Arrays.copyOf(mCachedModemConfigurations, mCachedModemConfigurations.length);
    }

    public static String getCurrentModemConfig() throws IOException {
        ModemStatus modemStatus = readModemAndStatus();
        if (modemStatus == null || !modemStatus.success)
            throw new IOException("Current modem configuration could not be read due to error");

        String currentModem = lookupSymlinkTarget(modemStatus.currentModem);

        if (new File(MODEM_FS_PATH, currentModem).exists()) {
            if (DEFAULT_MODEM.equals(currentModem)) {
                String msg = "Default modem is a file node that does not point to valid modem fs";
                CSLog.e(TAG, msg);
                throw new IOException(msg);
            }
            return MODEM_FS_PATH + currentModem;
        } else if (DEFAULT_MODEM.equals(currentModem)) {
            CSLog.d(TAG, "No modem filesystems exists, return SINGLE_MODEM_FS");
            return SINGLE_MODEM_FS;
        } else {
            CSLog.w(TAG, "Current modem configuration is set to an invalid value: " + currentModem + ". Returning");
            return "";
        }
    }

    public boolean isModemStatusSuccess() {
        ModemStatus status = readModemAndStatus();
        return status != null && status.success;
    }

    public boolean setModemConfiguration(String modemConfig) {
        if (mConfigurationSet) {
            CSLog.e(TAG, "A configuration has already been set, phone needs to reboot");
            return false;
        }
        boolean hasModemConfig = false;
        for (String m : getAvailableModemConfigurations()) {
            if (m.equals(modemConfig)) {
                hasModemConfig = true;
                break;
            }
        }

        if (hasModemConfig) {
            try {
                if (getCurrentModemConfig().equals(modemConfig)) {
                    CSLog.e(TAG, "Selected modem configuration is already active!: " + modemConfig);
                    return false;
                }
            } catch (IOException e) {
                CSLog.w(TAG, "Unable to read out current configuration");
            }

            String modemFileName = new File(modemConfig).getName();
            if (writeModemToMiscTA(modemFileName)) {
                CSLog.d(TAG, "Modem set " + modemFileName);
                mConfigurationSet = true;
                return true;
            }
            CSLog.e(TAG, "Failed to write selected configuration to miscta");
            return false;
        }
        CSLog.e(TAG, "Modem name " + modemConfig + " is not valid.");
        return false;
    }

    public static boolean writeModemToMiscTA(String modemFileName) {
        byte[] bFileName = modemFileName.getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[(bFileName.length + MODEM_MAGIC_COMMAND_LENGTH)];
        data[0] = MODEM_MISC_TA_MAGIC1;
        data[1] = MODEM_MISC_TA_MAGIC2;
        data[2] = MODEM_COMMAND_CHANGE;
        System.arraycopy(bFileName, 0, data, MODEM_MAGIC_COMMAND_LENGTH, bFileName.length);
        try {
            MiscTA.write(MODEM_COMMAND_UNIT, data);
            return true;
        } catch (MiscTaException e) {
            CSLog.e(TAG, "Unable to write to miscta:" + e);
            return false;
        }
    }

    public static void reApplyModem(Context ctx) {
        if (Settings.System.getInt(ctx.getContentResolver(), CS_REAPPLY_MODEM, 1) == 0) {
            CSLog.d(TAG, "reApplyModem: Preference false. Returning ...");
            return;
        }
        try {
            String modem = getCurrentModemConfig();
            if (modem.isEmpty()) {
                CSLog.e(TAG, "reApplyModem - Modem is EMPTY !");
                return;
            }
            CSLog.d(TAG, "reApplyModem - current modem: " + modem);
            CSLog.d(TAG, "reApplyModem - Re-writing 2405 with modem " + modem.replace(MODEM_FS_PATH, ""));

            // Store preference without checks - ModemConfiguration:75
            Configurator.getPreferences(ctx).edit().putString(ModemConfiguration.SAVED_MODEM_CONFIG, modem).apply();

            // Way of writing to Misc TA - ModemSwitcher:226
            if (writeModemToMiscTA(new File(modem).getName())) {
                CSLog.i(TAG, "reApplyModem - 2405 was re-written successfully");

                try {
                    MiscTA.write(TA_FOTA_INTERNAL, "temporary_modem");
                    CSLog.i(TAG, "reApplyModem - Modem Switcher 2404 cleared");
                } catch (MiscTaException e) {
                    CSLog.e(TAG, "reApplyModem - There was an error clearing 2404: ", e);
                }
            } else
                CSLog.e(TAG, "reApplyModem - 2405 was NOT re-written");
        } catch (IOException e) {
            CSLog.e(TAG, "reApplyModem - There was exception getting current modem: ", e);
        }
    }

    public static void revertReApplyModem() {
        try {
            String modem = getCurrentModemConfig();
            if (modem.isEmpty()) {
                CSLog.e(TAG, "revertReApplyModem - Modem is EMPTY !");
                return;
            }
            CSLog.d(TAG, "revertReApplyModem - current modem: " + modem);

            try {
                MiscTA.write(TA_FOTA_INTERNAL, new File(modem).getName());
                CSLog.i(TAG, "revertReApplyModem - Modem Switcher 2404 was written with: " + modem.replace(MODEM_FS_PATH, ""));
            } catch (MiscTaException e) {
                CSLog.e(TAG, "revertReApplyModem - There was an error writing 2404: ", e);
            }
        } catch (IOException e) {
            CSLog.e(TAG, "revertReApplyModem - There was exception getting current modem: ", e);
        }
    }
}
