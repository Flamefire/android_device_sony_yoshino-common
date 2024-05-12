package com.sonymobile.customizationselector.NS;

import com.sonymobile.customizationselector.CSLog;
import com.sonymobile.customizationselector.CommonUtil;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.RadioAccessFamily;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import com.android.internal.telephony.RILConstants;

/**
 * State of the Network switch operation helper methods
 * Based on work of shank03
 *
 * @author Flamefire
 */
public class SwitchState {
    private static final String TAG = "NetworkSwitcher";

    private static final String NS_LOWER_NETWORK = "ns_lowNet";
    private static final String NS_PREFERRED = "ns_preferred";

    public final int mSubID;
    private final Context mContext;
    private final ContentResolver mContent;
    private final Handler mHandler;
    private final TelephonyManager mTm;
    private final AirplaneModeObserver mAirplaneModeObserver;
    private final SimServiceObserver mSimServiceObserver;
    private SlotObserver mSlotObserver;

    public SwitchState(int subID, Context appContext, Handler handler) throws IllegalArgumentException {
        if (subID == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            throw new IllegalArgumentException();
        mSubID = subID;
        mContext = appContext;
        mContent = mContext.getContentResolver();
        mHandler = handler;
        mTm = mContext.getSystemService(TelephonyManager.class).createForSubscriptionId(mSubID);
        mAirplaneModeObserver = new AirplaneModeObserver(mHandler, mContent);
        mSimServiceObserver = new SimServiceObserver(mHandler, mTm);
    }

    /** Stop all observers and any pending messages */
    public void stop() {
        mAirplaneModeObserver.unregister();
        mSimServiceObserver.unregister();
        if (mSlotObserver != null) {
            mSlotObserver.unregister();
            mSlotObserver = null;
        }
        mHandler.removeCallbacksAndMessages(null);
    }


    /** Run the callback once the SIM is loaded */
    public void waitForSim(SlotObserver.Listener callback) {
        if (CommonUtil.isSIMLoaded(mContext, mSubID))
            callback.onConnected();
        else
            mSlotObserver = new SlotObserver(mContext, mHandler, mSubID, callback);
    }

    /** Run the callback once we have a signal */
    public void waitForSignal(Runnable callback) {
        if (!isAirplaneModeOn() && hasSignal())
            callback.run();
        else {
            SimServiceObserver.Listener signalListener = () -> {
                mAirplaneModeObserver.unregister();
                mSimServiceObserver.unregister();
                callback.run();
            };
            if (!isAirplaneModeOn())
                mSimServiceObserver.register(signalListener);
            mAirplaneModeObserver.register(() -> {
                if (isAirplaneModeOn())
                    mSimServiceObserver.unregister();
                else
                    mSimServiceObserver.register(signalListener);
            });
        }
    }

    public Handler getHandler() {
        return mHandler;
    }

    public void postDelayed(Runnable r, long delay) {
        mHandler.postDelayed(r, delay);
    }

    /** Change to the lower network storing the original one */
    public boolean changeNetworkDown() {
        return setOriginalNetwork(getPreferredNetwork()) && changeNetwork(getLowerNetwork());
    }

    /** Change back to the original network */
    public boolean changeToOriginalNetwork() {
        return changeNetwork(getOriginalNetwork());
    }

    /**
     * Change the network
     *
     * @param newNetwork     network to change to
     */
    private boolean changeNetwork(int newNetwork) {
        if (mTm.setPreferredNetworkTypeBitmask(RadioAccessFamily.getRafFromNetworkType(newNetwork))) {
            Settings.Global.putInt(mContent, Settings.Global.PREFERRED_NETWORK_MODE + mSubID, newNetwork);
            d("changeNetwork: Successfully changed to " + networkToString(newNetwork));
            return true;
        } else {
            d("changeNetwork: Failed changing to " + networkToString(newNetwork));
            return false;
        }
    }

    /**
     * Get the current in-use network mode preference
     *
     * @return default 3G {@link RILConstants#NETWORK_MODE_WCDMA_PREF} if no pref stored
     */
    public int getPreferredNetwork() {
        return Settings.Global.getInt(mContent, Settings.Global.PREFERRED_NETWORK_MODE + mSubID, RILConstants.NETWORK_MODE_WCDMA_PREF);
    }

    /**
     * Get the original network mode preference
     *
     * @return Stored value, defaults to {@link RILConstants#NETWORK_MODE_LTE_GSM_WCDMA}
     */
    public int getOriginalNetwork() {
        return Settings.System.getInt(mContent, NS_PREFERRED + mSubID, RILConstants.NETWORK_MODE_LTE_GSM_WCDMA);
    }

    public boolean setOriginalNetwork(int network) {
        return Settings.System.putInt(mContent, NS_PREFERRED + mSubID, network);
    }

    /**
     * This method returns the lower network to switch to
     */
    public int getLowerNetwork() {
        return Settings.System.getInt(mContent, NS_LOWER_NETWORK, RILConstants.NETWORK_MODE_WCDMA_PREF);
    }

    /**
     * Gets the state of Airplane Mode.
     *
     * @return true if enabled.
     */
    private boolean isAirplaneModeOn() {
        return Settings.System.getInt(mContent, Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    /**
     * Return true if there is a (network) signal for the SIM
     */
    private boolean hasSignal() {
        return CommonUtil.hasSignal(mTm);
    }

    /**
     * Returns whether @param network is LTE or not
     */
    public static boolean isLTE(int network) {
        int lteMask = RadioAccessFamily.RAF_LTE | RadioAccessFamily.RAF_LTE_CA;
        return (RadioAccessFamily.getRafFromNetworkType(network) & lteMask) != 0;
    }

    /**
     * Get the string version of the variables.
     * <p>
     * Too lazy to refer the {@link RILConstants}
     */
    public static String networkToString(int network) {
        switch (network) {
            case RILConstants.NETWORK_MODE_WCDMA_PREF:
                return "NETWORK_MODE_WCDMA_PREF";
            case RILConstants.NETWORK_MODE_WCDMA_ONLY:
                return "NETWORK_MODE_WCDMA_ONLY";
            case RILConstants.NETWORK_MODE_GSM_UMTS:
                return "NETWORK_MODE_GSM_UMTS";
            case RILConstants.NETWORK_MODE_GSM_ONLY:
                return "NETWORK_MODE_GSM_ONLY";
            case RILConstants.NETWORK_MODE_LTE_GSM_WCDMA:
                return "NETWORK_MODE_LTE_GSM_WCDMA";
            case RILConstants.NETWORK_MODE_LTE_ONLY:
                return "NETWORK_MODE_LTE_ONLY";
            case RILConstants.NETWORK_MODE_LTE_WCDMA:
                return "NETWORK_MODE_LTE_WCDMA";
            case RILConstants.NETWORK_MODE_GLOBAL:
                return "NETWORK_MODE_GLOBAL";
            case RILConstants.NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA:
                return "NETWORK_MODE_LTE_TDSCDMA_CDMA_EVDO_GSM_WCDMA";
            case RILConstants.NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
                return "NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA";
            default:
                return "N/A(" + network + ")";
        }
    }

    private void d(String msg) {
        CSLog.d(TAG, msg);
    }
}
