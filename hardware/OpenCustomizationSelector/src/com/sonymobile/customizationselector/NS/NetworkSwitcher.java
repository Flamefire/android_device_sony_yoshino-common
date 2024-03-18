package com.sonymobile.customizationselector.NS;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.RadioAccessFamily;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.sonymobile.customizationselector.CSLog;
import com.sonymobile.customizationselector.CommonUtil;

/**
 * Service to handle Network mode
 * - On boot if preference network is set to LTE switch to the selected lower network (e.g. 3G)
 * - When the device is unlocked and the SIM has got service connection
 *   switch back to the previous network and exit
 *
 * @author shank03
 */
public class NetworkSwitcher extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final String TAG = "NetworkSwitcher";
    private static final String NS_LOWER_NETWORK = "ns_lowNet";
    private static final String NS_PREFERRED = "ns_preferred";

    private AirplaneModeObserver mAirplaneModeObserver;
    private SimServiceObserver mSimServiceObserver;
    // Set until the phone is unlocked
    private BroadcastReceiver mUnlockObserver;

    @Override
    public void onCreate() {
        d("onCreate");
        final Context appContext = getApplicationContext();

        mAirplaneModeObserver = new AirplaneModeObserver(appContext, new Handler(getMainLooper()));
        mSimServiceObserver = new SimServiceObserver(appContext);
        mUnlockObserver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                unregisterReceiver(mUnlockObserver);
                mUnlockObserver = null;
            }
        };
        registerReceiver(mUnlockObserver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));

        // Start process
        try {
            if (CommonUtil.isDualSim(appContext))
                d("device is dual sim");
            else
                d("single sim device");

            int subID = CommonUtil.getSubID(appContext);
            if (subID == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                new SubIdObserver(appContext).register(this::initProcess);
            else
                initProcess(subID);
        } catch (Exception e) {
            CSLog.e(TAG, "Error: ", e);
        }

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void initProcess(int subID) {
        if (CommonUtil.isSIMLoaded(getApplicationContext(), subID)) {
            new Handler(getMainLooper()).postDelayed(() -> switchDown(subID), 1400);
        } else {
            new SlotObserver(getApplicationContext()).register(subID,
                    () -> new Handler(getMainLooper()).postDelayed(() -> switchDown(subID), 1400));
        }
    }

    private void switchDown(int subID) {
        if (subID < 0) {
            d("switchDown: Error, invalid subID");
            stopSelf();
            return;
        }

        TelephonyManager tm = getSystemService(TelephonyManager.class).createForSubscriptionId(subID);

        long currentNetwork = getPreferredNetwork(tm);
        if (isLTE(currentNetwork)) {
            setOriginalNetwork(subID, currentNetwork);
            changeNetwork(tm, subID, getLowerNetwork());

            if (CommonUtil.isDirectBootEnabled() && mUnlockObserver != null) {
                // Delay resetting the network until phone is unlocked.
                // The current unlock observer is no longer required
                unregisterReceiver(mUnlockObserver);
                mUnlockObserver = null;
                registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        unregisterReceiver(this);
                        handleConnection(tm, subID);
                    }
                }, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
            } else {
                handleConnection(tm, subID);
            }
        } else {
            d("Network is not LTE, no work.");
            stopSelf();
        }
    }

    private void handleConnection(TelephonyManager tm, int subID) {
        if (isAirplaneModeOn()) {
            mAirplaneModeObserver.register(() -> {
                if (isAirplaneModeOn()) {
                    mSimServiceObserver.unregister();
                } else {
                    mSimServiceObserver.register(subID, () -> {
                        mAirplaneModeObserver.unregister();
                        changeNetwork(tm, subID, getOriginalNetwork(subID));
                        stopSelf();
                    });
                }
            });
        } else {
            if (CommonUtil.hasSignal(tm)) {
                changeNetwork(tm, subID, getOriginalNetwork(subID));
                stopSelf();
            } else {
                mSimServiceObserver.register(subID, () -> {
                    changeNetwork(tm, subID, getOriginalNetwork(subID));
                    stopSelf();
                });
            }
        }
    }

    /**
     * The method to change the network
     *
     * @param tm             {@link TelephonyManager} specific to subID
     * @param subID          the subscription ID from [subscriptionsChangedListener]
     * @param newNetwork     network to change to
     */
    private void changeNetwork(TelephonyManager tm, int subID, long newNetwork) {
        d("changeNetwork: To be changed to = " + TelephonyManager.convertNetworkTypeBitmaskToString(newNetwork));

        try {
            tm.setAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER, newNetwork);
        } catch (IllegalStateException e) {
            d("changeNetwork: Failed to change network, no telephony service!");
            return;
	}
        d("changeNetwork: Successfully changed to " + TelephonyManager.convertNetworkTypeBitmaskToString(newNetwork));
    }

    /**
     * Get the current in-use network mode preference, i.e. RAF
     * <p>
     * There are no defaults other than {@link #INVALID_NETWORK}
     */
    private @TelephonyManager.NetworkTypeBitMask long getPreferredNetwork(TelephonyManager tm) {
        return tm.getAllowedNetworkTypesForReason(TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);
    }

    /**
     * Get the original network mode preference
     *
     * @return Stored value, defaults to LTE, GSM & WCDMA
     */
    private @TelephonyManager.NetworkTypeBitMask long getOriginalNetwork(int subID) {
        return Settings.System.getLong(getApplicationContext().getContentResolver(), NS_PREFERRED + subID,
                                       RadioAccessFamily.getRafFromNetworkType(TelephonyManager.NETWORK_MODE_LTE_GSM_WCDMA));
    }

    private void setOriginalNetwork(int subID, @TelephonyManager.NetworkTypeBitMask long network) {
        Settings.System.putLong(getApplicationContext().getContentResolver(), NS_PREFERRED + subID, network);
    }

    /**
     * Returns whether @param network is LTE or not
     */
    private boolean isLTE(@TelephonyManager.NetworkTypeBitMask long network) {
        return (network & TelephonyManager.NETWORK_CLASS_BITMASK_4G) != 0;
    }

    /**
     * This method returns the lower network to switch to
     */
    private int getLowerNetwork() {
        return RadioAccessFamily.getRafFromNetworkType(
            Settings.System.getInt(getApplicationContext().getContentResolver(), NS_LOWER_NETWORK, TelephonyManager.NETWORK_MODE_WCDMA_PREF));
    }

    /**
     * Gets the state of Airplane Mode.
     *
     * @return true if enabled.
     */
    private boolean isAirplaneModeOn() {
        return Settings.System.getInt(getApplicationContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void d(String msg) {
        CSLog.d(TAG, msg);
    }
}
