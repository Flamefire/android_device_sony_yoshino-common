package com.sonymobile.customizationselector;

import android.content.Context;
import android.os.Environment;
import android.provider.Settings;
import com.sonymobile.miscta.MiscTA;
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

    private static class ModemStatus {
        final String currentModem;
        final boolean modemStatusSuccessful;

        ModemStatus(boolean statusSuccess, String modem) {
            modemStatusSuccessful = statusSuccess;
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

    private static ModemStatus readModemAndStatus() {
        File statusFile = new File(MODEM_STATUS_FILE);
        String currentModem = DEFAULT_MODEM;

        if (!statusFile.isFile()) {
            CSLog.e(TAG, "Status file does not exists or is not a file.");
            return new ModemStatus(false, currentModem);

        } else if (statusFile.length() > MAXIMUM_STATUS_FILE_LENGTH) {
            CSLog.e(TAG, "Status file is too large: " + statusFile.length() + ", more than limit: " + MAXIMUM_STATUS_FILE_LENGTH);
            return new ModemStatus(false, currentModem);

        } else {
            BufferedReader in = null;
            try {
                in = new BufferedReader(new InputStreamReader(new FileInputStream(statusFile), StandardCharsets.UTF_8));

                String line = in.readLine();
                if (line == null) {
                    CSLog.e(TAG, "Line is null");
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return new ModemStatus(false, currentModem);
                }

                String[] values = line.split(",");
                CSLog.d(TAG, "Read line: " + line);

                if (values.length != 2) {
                    CSLog.e(TAG, "Format error status file, nbr of fields found:" + values.length);
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return new ModemStatus(false, currentModem);
                }

                int tmpStatus = Integer.parseInt(values[0]);
                if (tmpStatus != UA_MODEM_SWITCHER_STATUS_SUCCESS) {
                    CSLog.d(TAG, "Unsuccessful status code found: " + tmpStatus);
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return new ModemStatus(false, currentModem);
                }
                currentModem = values[1];
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return new ModemStatus(true, currentModem);
            } catch (Exception e) {
                CSLog.e(TAG, "Failed to read FOTA STATUS() " + e);
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
                return null;
            }
        }
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
        ModemStatus readModemAndStatus = readModemAndStatus();

        if (readModemAndStatus != null && readModemAndStatus.modemStatusSuccessful) {
            String lookupSymlinkTarget = lookupSymlinkTarget(readModemAndStatus.currentModem);

            if (new File(MODEM_FS_PATH, lookupSymlinkTarget).exists()) {
                if (DEFAULT_MODEM.equals(lookupSymlinkTarget)) {
                    String msg = "Default modem is a file node that does not point to valid modem fs";
                    CSLog.e(TAG, msg);
                    throw new IOException(msg);
                }
                return MODEM_FS_PATH + lookupSymlinkTarget;

            } else if (DEFAULT_MODEM.equals(lookupSymlinkTarget)) {
                CSLog.d(TAG, "No modem filesystems exists, return SINGLE_MODEM_FS");
                return SINGLE_MODEM_FS;

            } else {
                CSLog.w(TAG, "Current modem configuration is set to an invalid value: " + lookupSymlinkTarget + ". Returning");
                return "";
            }
        }
        throw new IOException("Current modem configuration could not be read due to error ");
    }

    public boolean isModemStatusSuccess() {
        ModemStatus status = readModemAndStatus();
        return status != null && status.modemStatusSuccessful;
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
        byte[] bytes = modemFileName.getBytes(StandardCharsets.UTF_8);
        byte[] bArr = new byte[(bytes.length + MODEM_MAGIC_COMMAND_LENGTH)];
        bArr[0] = MODEM_MISC_TA_MAGIC1;
        bArr[1] = MODEM_MISC_TA_MAGIC2;
        bArr[2] = MODEM_COMMAND_CHANGE;
        System.arraycopy(bytes, 0, bArr, MODEM_MAGIC_COMMAND_LENGTH, bytes.length);
        try {
            MiscTA.write(MODEM_COMMAND_UNIT, bArr);
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
            if (modem == null || modem.isEmpty()) {
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
                    MiscTA.write(TA_FOTA_INTERNAL, "temporary_modem".getBytes(StandardCharsets.UTF_8));
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
            if (modem == null || modem.isEmpty()) {
                CSLog.e(TAG, "revertReApplyModem - Modem is EMPTY !");
                return;
            }
            CSLog.d(TAG, "revertReApplyModem - current modem: " + modem);

            try {
                MiscTA.write(TA_FOTA_INTERNAL, new File(modem).getName().getBytes(StandardCharsets.UTF_8));
                CSLog.i(TAG, "revertReApplyModem - Modem Switcher 2404 was written with: " + modem.replace(MODEM_FS_PATH, ""));
            } catch (MiscTaException e) {
                CSLog.e(TAG, "revertReApplyModem - There was an error writing 2404: ", e);
            }
        } catch (IOException e) {
            CSLog.e(TAG, "revertReApplyModem - There was exception getting current modem: ", e);
        }
    }
}
