package com.sonymobile.customizationselector.NS;

import android.content.Context;
import android.os.Handler;
import android.telephony.CellSignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.sonymobile.customizationselector.CSLog;

public class SimServiceObserver {

    private static final String TAG = "SimServiceObserver";

    interface Listener {
        void onConnected();
    }

    private final Context mContext;
    private boolean mRegistered = false;
    private int mSubID = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private Handler mHandler;
    private Listener mListener;

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            try {
                synchronized (new Object()) {
                    if (mSubID != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class).createForSubscriptionId(mSubID);
                        if (tm.getSignalStrength() != null && tm.getSignalStrength().getLevel() != CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
                            mListener.onConnected();
                            unregister();
                        }
                    } else {
                        unregister();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mHandler.postDelayed(this, 2000);
            }
        }
    };

    public SimServiceObserver(Context context) {
        mContext = context;
    }

    public void register(int subID, Listener listener) {
        if (!mRegistered) {
            mSubID = subID;
            mListener = listener;
            mHandler = new Handler(mContext.getMainLooper());

            mHandler.post(runnable);
            mRegistered = true;
            CSLog.d(TAG, "Registered");
        }
    }

    public void unregister() {
        if (mRegistered) {
            mListener = null;
            mSubID = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

            mHandler.removeCallbacks(runnable);

            mRegistered = false;
            CSLog.d(TAG, "Unregistered");
        }
    }
}
