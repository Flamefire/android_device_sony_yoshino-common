package com.sonymobile.customizationselector.NS;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.telephony.SubscriptionManager;
import com.sonymobile.customizationselector.CSLog;
import com.sonymobile.customizationselector.CommonUtil;

/**
 * Service to handle Network mode
 * - On boot if preference network is set to LTE switch to the selected lower network (e.g. 3G)
 * - When the device is unlocked and the SIM has got data connection
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

    private boolean isUserUnlocked;
    private SwitchState mState;

    @Override
    public void onCreate() {
        d("onCreate");
        final Context appContext = getApplicationContext();

        if (CommonUtil.isDirectBootEnabled()) {
            isUserUnlocked = false;
            registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        isUserUnlocked = true;
                        unregisterReceiver(this);
                    }
                }, new IntentFilter(Intent.ACTION_USER_UNLOCKED)
            );
        } else
            isUserUnlocked = true;

        // Start process
        try {
            if (CommonUtil.isDualSim(appContext))
                d("device is dual sim");
            else
                d("single sim device");

            int subID = CommonUtil.getSubID(appContext);
            if (subID == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                new SubIdObserver(appContext, this::initProcess);
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

    /** Stop the Service after it has started processing by {@link initProcess} */
    private void stopProcess() {
        mState.stop();
        mState = null;
        stopSelf();
    }

    /** Start the switching process for the given subscription ID */
    private void initProcess(int subID) {
        if (subID == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            e("Error: Invalid subID");
            stopSelf();
        } else {
            mState = new SwitchState(subID, getApplicationContext(), new Handler(getMainLooper()));
            mState.waitForSim(() -> onSIMLoaded());
        }
    }

    /** To be called once the SIM is ready */
    private void onSIMLoaded() {
        d("SIM loaded for subID " + mState.mSubID);
        mState.postDelayed(() -> switchDown(), 1400);
    }

    /** Switch to the lower network and continue once the user unlocked the phone */
    private void switchDown() {
        if (mState.isLTE()) {
            if (!mState.changeNetworkDown()) {
                e("Error: Failed changing to lower network");
                stopProcess();
                return;
            }

            // Delay resetting the network until phone is unlocked.
            if (isUserUnlocked)
                startWaitForLowerNetwork();
            else {
                d("Waiting for user unlock");
                registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            unregisterReceiver(this);
                            startWaitForLowerNetwork();
                        }
                    }, new IntentFilter(Intent.ACTION_USER_UNLOCKED)
                );
            }
        } else {
            i("Network is not LTE, no work.");
            stopProcess();
        }
    }

    /** Start waiting for a signal on the lower network */
    private void startWaitForLowerNetwork() {
        d("Waiting for lower network");
        mState.waitForSignal(() -> onLowerSignal());
    }

    private void onLowerSignal() {
        if(mState.getDataState() == SwitchState.DataState.DISABLED) {
            i("Data disabled");
            // Wait a bit then just switch back
            mState.postDelayed(()-> resetNetworkAndFinish(), 2000);
        } else {
            Runnable runnable = new Runnable() {
                private int attempt = 0;
                private final long delay = 500;

                @Override
                public void run() {
                    switch(mState.getDataState()) {
                        case CONNECTED:
                            i("Data connected");
                            break;
                        case DISCONNECTED:
                            attempt++;
                            // Keep trying for 1min
                            if(attempt < 60 * 1000 / delay) {
                                mState.postDelayed(this, delay);
                                return; // Don't reset yet
                            } else
                                e("Still no data connection, aborting");
                            break;
                        case DISABLED:
                            i("Data disabled");
                            break;
                    }
                    resetNetworkAndFinish();
                }
            };
            mState.postDelayed(runnable, 1000);
        }
    }

    private void resetNetworkAndFinish() {
        if (mState.changeToOriginalNetwork())
            d("Changed back to original network");
        else
            e("Failed changing back to original network");
        stopProcess();
    }

    private void d(String msg) {
        CSLog.d(TAG, msg);
    }

    private void i(String msg) {
        CSLog.i(TAG, msg);
    }

    private void e(String msg) {
        CSLog.e(TAG, msg);
    }
}
