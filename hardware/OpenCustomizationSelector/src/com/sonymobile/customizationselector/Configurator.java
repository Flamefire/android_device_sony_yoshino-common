package com.sonymobile.customizationselector;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import com.sonymobile.miscta.MiscTaException;

import java.io.File;
import java.io.IOException;

public class Configurator {

    private static final String TAG = "Configurator";
    private static final String PREF_PKG = "CS";

    public static final String KEY_CONFIG_ID = "config_id";
    public static final String KEY_MODEM = "modem";
    public static final String KEY_SIM_ID = "sim_id";

    private static final String OLD_CONFIG_KEY = "config_key";
    private static final String PROP_CUST = "ro.semc.version.cust";
    private static final String PROP_CUST_REV = "ro.semc.version.cust_revision";
    private static final String PROP_SIM_CONFIG_ID = "persist.sys.sim_config_ids";
    private static final String PROP_SW = "ro.semc.version.sw";
    private static final String PROP_SW_REV = "ro.semc.version.sw_revision";
    private static final String PROP_CS_VERSION = "ro.semc.version.opencs";
    public static final String PROP_TA_AC_VERSION = "ro.semc.version.cust.active";

    private static final int TA_AC_VERSION = 2212;

    private final PersistableBundle mBundle;
    private final Context mContext;
    private String mConfigId = "", mModem = "";

    public Configurator(Context context, PersistableBundle bundle) {
        mContext = context;
        mBundle = bundle;
    }

    public static void clearMiscTaConfigId() {
        CSLog.d(TAG, "Clear MiscTa value for Config Id");
        MiscTA.write(TA_AC_VERSION, "");
    }

    private boolean anythingChangedSinceLastEvaluation() {
        String configKey = getPreferences().getString(OLD_CONFIG_KEY, "");
        CSLog.d(TAG, "OldConfigKey: " + configKey);
        return !configKey.equals(createCurrentConfigurationKey());
    }

    public void saveConfigurationKey(String configKey) {
        getPreferences().edit().putString(OLD_CONFIG_KEY, configKey).apply();
    }

    public void saveConfigurationKey() {
        String newKey = createCurrentConfigurationKey();
        saveConfigurationKey(newKey);
        CSLog.d(TAG, "saveConfigKey - key saved: " + newKey);
    }

    public void clearConfigurationKey() {
        saveConfigurationKey("null");
    }

    private String createCurrentConfigurationKey() {
        String status = SystemProperties.get(PROP_CUST, "") + SystemProperties.get(PROP_CUST_REV, "") +
                SystemProperties.get(PROP_SW, "") + SystemProperties.get(PROP_SW_REV, "") +
                SystemProperties.get(PROP_CS_VERSION, "") + getIccid();
        CSLog.d(TAG, "CurrentConfKey: " + status);
        return status;
    }

    private String evaluateCarrierConfigId(String ID) {
        CSLog.d(TAG, "config_id = " + ID);
        return (ID == null || ID.equals(SystemProperties.get(PROP_TA_AC_VERSION, null))) ? null : ID;
    }

    private String evaluateModem(String modem) {
        CSLog.d(TAG, "modem = " + modem);
        return new ModemConfiguration(getPreferences()).getModemConfigurationNeeded(modem);
    }

    private String getIccid() {
        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        int defaultSubId = CommonUtil.getDefaultSubId(mContext);
        String simSerialNumber = (tm == null || defaultSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) ? "" : tm.getSimSerialNumber(defaultSubId);
        CSLog.d(TAG, "getIccid: " + simSerialNumber);
        return simSerialNumber != null ? simSerialNumber : "";
    }

    private static Context getTargetContext(Context context) {
        if (CommonUtil.isDirectBootEnabled()) {
            CSLog.d(TAG, "Direct Boot is enabled. Use device encrypted storage.");
            return context.createDeviceProtectedStorageContext();
        }
        CSLog.d(TAG, "Direct Boot is disabled. Use credential encrypted storage.");
        return context;
    }

    public static SharedPreferences getPreferences(Context context) {
        return getTargetContext(context).getSharedPreferences(PREF_PKG, Context.MODE_PRIVATE);
    }

    public SharedPreferences getPreferences() {
        return getPreferences(mContext);
    }

    public boolean isNewConfigurationNeeded() {
        if (anythingChangedSinceLastEvaluation()) {
            if (mBundle != null) {
                String simID = mBundle.getString(KEY_SIM_ID, "");
                SystemProperties.set(PROP_SIM_CONFIG_ID, simID);

                mModem = evaluateModem(mBundle.getString(KEY_MODEM, ""));
                mConfigId = evaluateCarrierConfigId(mBundle.getString(KEY_CONFIG_ID));

                CSLog.d(TAG, "isNewConfigurationNeeded - Sim Id: " + simID);
                CSLog.d(TAG, "isNewConfigurationNeeded - Modem: " + mModem);
                CSLog.d(TAG, "isNewConfigurationNeeded - Carrier Config Id: " + mConfigId);
            }
            return mConfigId != null || !TextUtils.isEmpty(mModem);
        } else {
            CSLog.d(TAG, "isNewConfigurationNeeded - ConfigKey not updated, no need to evaluate");
            return false;
        }
    }

    public void set() {
        CSLog.d(TAG, String.format("Set() - modem = '%s' - carrier config id = '%s'", mModem, mConfigId));
        if (anythingChangedSinceLastEvaluation()) {
            saveConfigurationKey();
            if (mConfigId != null)
                MiscTA.write(TA_AC_VERSION, mConfigId);
            if (!TextUtils.isEmpty(mModem))
                new ModemConfiguration(getPreferences()).setConfiguration(mModem);
        }
    }
}
