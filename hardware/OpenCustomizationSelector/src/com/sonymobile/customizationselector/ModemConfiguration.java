package com.sonymobile.customizationselector;

import android.content.SharedPreferences;
import android.text.TextUtils;

import java.io.IOException;

public class ModemConfiguration {

    private static final String TAG = "ModemConfiguration";

    public static final String MODEM_APPENDIX = "_tar.mbn";
    public static final String SAVED_MODEM_CONFIG = "saved_config";

    private final ModemSwitcher mModemSwitcher = new ModemSwitcher();
    private final SharedPreferences mPreference;

    public ModemConfiguration(SharedPreferences sharedPreferences) {
        mPreference = sharedPreferences;
    }

    public String getModemConfiguration(String variant) {
        if (variant == null)
            variant = "";
        String modemToFind = variant + MODEM_APPENDIX;

        String modemConfig = "";
        String[] availableModemConfigurations = mModemSwitcher.getAvailableModemConfigurations();

        if (availableModemConfigurations.length > 0) {
            CSLog.d(TAG, "getModemConfiguration - Finding modem with suffix: " + modemToFind);
            modemConfig = availableModemConfigurations[0];
            for (String m : availableModemConfigurations) {
                if (m.endsWith(modemToFind)) {
                    modemConfig = m;
                    break;
                }
            }
        }
        CSLog.d(TAG, "modemConfiguration: " + modemConfig);
        return modemConfig;
    }

    public String getModemConfigurationNeeded(String variant) {
        CSLog.d(TAG, "updateModem - modemVariant = " + variant);
        try {
            String currentModemConfig = ModemSwitcher.getCurrentModemConfig();
            CSLog.d(TAG, "Current modem: " + currentModemConfig);
            if (ModemSwitcher.SINGLE_MODEM_FS.equalsIgnoreCase(currentModemConfig))
                return "";
            String modemConfiguration = getModemConfiguration(variant);
            if (!TextUtils.isEmpty(modemConfiguration) && !modemConfiguration.equalsIgnoreCase(currentModemConfig))
                return modemConfiguration;
            CSLog.d(TAG, "updateModem - No need to update modem.");
            return "";
        } catch (IOException e) {
            CSLog.e(TAG, "Could not retrieve current modem configuration.");
            return "";
        }
    }

    public boolean setConfiguration(String config) {
        CSLog.d(TAG, "setConfiguration - modem configuration = " + config);
        if (TextUtils.isEmpty(config))
            return false;

        String savedConfig = mPreference.getString(SAVED_MODEM_CONFIG, "");
        if (!mModemSwitcher.isModemStatusSuccess() && savedConfig.equals(config)) {
            CSLog.e(TAG, "Modem id: " + config + " has been unsuccessfully tried");
            return false;
        } else {
            mPreference.edit().putString(SAVED_MODEM_CONFIG, config).apply();
            CSLog.d(TAG, "Updating the modem configuration with " + config);

            if (mModemSwitcher.setModemConfiguration(config)) {
                CSLog.d(TAG, "Modem configuration set");
                return true;
            }
            CSLog.d(TAG, "Unable to set modem configuration");
            return false;
        }
    }
}
