package com.sonymobile.customizationselector;

import android.content.Context;
import android.os.PersistableBundle;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.telephony.CellSignalStrength;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.sonymobile.customizationselector.Parser.DynamicConfigParser;
import com.sonymobile.customizationselector.Parser.ModemConfParser;

import java.util.HashMap;
import java.util.List;

import static com.sonymobile.customizationselector.Parser.XmlConstants.ANY_SIM;
import static com.sonymobile.customizationselector.Parser.XmlConstants.DEFAULT_CONFIG;

public class CommonUtil {

    private static final String TAG = "CommonUtil";
    private static final int MIN_MCC_MNC_LENGTH = 5;

    public static final String CS_IMS = "cs_ims";

    public static boolean isIMSEnabledBySetting(Context context) {
        return Settings.System.getInt(context.getContentResolver(), CS_IMS, 1) == 1;
    }

    public static PersistableBundle getCarrierBundle(Context context) {
        String simId = new SimConfigId(context).getId();

        HashMap<String, String> configuration = DynamicConfigParser.getConfiguration(context);
        String configId = configuration.get(simId);
        if (TextUtils.isEmpty(configId))
            configId = configuration.get(ANY_SIM);
        if (configId == null || DEFAULT_CONFIG.equalsIgnoreCase(configId))
            configId = "";
        String modem = ModemConfParser.parseModemConf(configId);
        CSLog.i(TAG, String.format("Returning bundle with sim id %s, modem: %s, config id: %s", simId, modem, configId));

        PersistableBundle bundle = new PersistableBundle(3);
        bundle.putString(Configurator.KEY_SIM_ID, simId);
        bundle.putString(Configurator.KEY_MODEM, modem);
        bundle.putString(Configurator.KEY_CONFIG_ID, configId);
        return bundle;
    }

    public static int getDefaultSubId(Context context) {
        int subscriptionId;
        int defaultDataSubscriptionId = SubscriptionManager.getDefaultDataSubscriptionId();

        if (!SubscriptionManager.isUsableSubIdValue(defaultDataSubscriptionId)) {
            List<SubscriptionInfo> activeSubscriptionInfoList = context.getSystemService(SubscriptionManager.class)
                    .getActiveSubscriptionInfoList();
            if (activeSubscriptionInfoList != null) {
                for (SubscriptionInfo subscriptionInfo : activeSubscriptionInfoList) {
                    if (SubscriptionManager.isUsableSubIdValue(subscriptionInfo.getSubscriptionId())) {
                        subscriptionId = subscriptionInfo.getSubscriptionId();
                        CSLog.d(TAG, "getDefaultSubId: " + subscriptionId);
                        return subscriptionId;
                    }
                }
            }
        }
        subscriptionId = defaultDataSubscriptionId;
        CSLog.d(TAG, "getDefaultSubId: " + subscriptionId);
        return subscriptionId;
    }

    public static boolean isDefaultDataSlot(Context context, int subID) {
        return getDefaultSubId(context) == subID;
    }

    public static int getSimSlotIndex(Context context, int defaultIdx){
        if (CommonUtil.isDualSim(context))
            return Settings.System.getInt(context.getContentResolver(), "ns_slot", defaultIdx);
        else
            return 0;
    }

    public static int getSimSlotIndex(Context context){
        return getSimSlotIndex(context, 0);
    }

    /**
     * Get the subscription IDs based on phone count and sim status.
     */
    public static int getSubID(Context context) {
        int[] subs = SubscriptionManager.getSubId(getSimSlotIndex(context));
        return subs == null ? SubscriptionManager.INVALID_SUBSCRIPTION_ID : subs[0];
    }

    public static boolean isDirectBootEnabled() {
        return StorageManager.isFileEncryptedNativeOrEmulated();
    }

    public static boolean isDualSim(Context context) {
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        return tm != null && tm.getPhoneCount() > 1;
    }

    public static boolean isMandatorySimParamsAvailable(Context context, int subId) {
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);

        if (tm != null) {
            String simOperator = tm.getSimOperator(subId);
            String subscriberId = tm.getSubscriberId(subId);
            String simSerialNumber = tm.getSimSerialNumber(subId);
            String simOperatorName = tm.getSimOperatorName(subId);
            String groupIdLevel1 = tm.getGroupIdLevel1(subId);
            CSLog.d(TAG, "SimOperator= " + simOperator + ", IMSI= " + subscriberId + ", ICCID = " + simSerialNumber
                    + ", SPN = " + simOperatorName + ", gid1 = " + groupIdLevel1);

            if (!TextUtils.isEmpty(simOperator) && !TextUtils.isEmpty(subscriberId) && !TextUtils.isEmpty(simSerialNumber)) {
                CSLog.d(TAG, "isMandatorySimParamsAvailable: true");
                return true;
            }
        }
        CSLog.d(TAG, "isMandatorySimParamsAvailable: false");
        return false;
    }

    public static boolean isSIMLoaded(Context context, int subID) {
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);

        if (tm != null && subID != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            int slotIndex = SubscriptionManager.getSlotIndex(subID);
            if (slotIndex != SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                boolean isLoaded = tm.getSimState(slotIndex) == TelephonyManager.SIM_STATE_READY
                        && !TextUtils.isEmpty(tm.getSubscriberId(subID))
                        && !TextUtils.isEmpty(tm.getSimOperator(subID))
                        && tm.getSimOperator(subID).length() >= MIN_MCC_MNC_LENGTH;

                CSLog.d(TAG, "isSIMLoaded: " + isLoaded);
                return isLoaded;
            }
        }
        CSLog.d(TAG, "isSIMLoaded: false");
        return false;
    }

    public static boolean hasSignal(TelephonyManager tm) {
        SignalStrength signal = tm.getSignalStrength();
        return signal != null && signal.getLevel() != CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
    }

    public static String[] getDefaultModems() {
        return new String[]{"amss_fsg_lilac_tar.mbn",
                "amss_fsg_poplar_tar.mbn", "amss_fsg_poplar_dsds_tar.mbn",
                "amss_fsg_maple_tar.mbn", "amss_fsg_maple_dsds_tar.mbn"};
    }

    public static boolean isModemDefault(String modem) {
        for (String m : getDefaultModems()) {
            if (m.equals(modem))
                return true;
        }
        return !modem.contains("ims") && !modem.contains("volte")
                && !modem.contains("vilte") && !modem.contains("vowifi");
    }
}
