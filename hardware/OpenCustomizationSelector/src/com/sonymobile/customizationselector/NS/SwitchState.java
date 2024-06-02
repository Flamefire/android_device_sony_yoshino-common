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
    private final PullObserver mSimServiceObserver;
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
        mSimServiceObserver = new PullObserver("SimServiceObserver", mHandler, () -> { return CommonUtil.hasSignal(mTm); });
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
            Runnable signalListener = () -> {
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
    private boolean changeNetwork(@TelephonyManager.NetworkTypeBitMask long newNetwork) {
        final String networkString = TelephonyManager.convertNetworkTypeBitmaskToString(newNetwork);

        try {
            mTm.setAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER, newNetwork);
            d("changeNetwork: Successfully changed to " + networkString);
            return true;
        } catch (Exception e) {
            d("changeNetwork: Failed changing to " + networkString + ": " + e.getMessage());
            return false;
        }
	}

    /**
     * Get the current in-use network mode preference, i.e. RAF
     * <p>
     * There are no defaults other than {@link #INVALID_NETWORK}
     */
    private @TelephonyManager.NetworkTypeBitMask long getPreferredNetwork() {
        return mTm.getAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
    }

    /**
     * Get the original network mode preference
     *
     * @return Stored value, defaults to LTE, GSM & WCDMA
     */
    private @TelephonyManager.NetworkTypeBitMask long getOriginalNetwork() {
        return Settings.System.getLong(mContent, NS_PREFERRED + mSubID,
                                       RadioAccessFamily.getRafFromNetworkType(TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA));
    }

    private boolean setOriginalNetwork(@TelephonyManager.NetworkTypeBitMask long network) {
        return Settings.System.putLong(mContent, NS_PREFERRED + mSubID, network);
    }

    /**
     * This method returns the lower network to switch to
     */
    public @TelephonyManager.NetworkTypeBitMask long getLowerNetwork() {
        return RadioAccessFamily.getRafFromNetworkType(
            Settings.System.getInt(mContent, NS_LOWER_NETWORK, TelephonyManager.NETWORK_MODE_WCDMA_PREF));
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
    private boolean isLTE(@TelephonyManager.NetworkTypeBitMask long network) {
        return (network & TelephonyManager.NETWORK_CLASS_BITMASK_4G) != 0;
    }

    private void d(String msg) {
        CSLog.d(TAG, msg);
    }
}
