package com.sonymobile.customizationselector.NS;

import android.content.Context;
import android.os.Handler;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.sonymobile.customizationselector.CSLog;
import com.sonymobile.customizationselector.CommonUtil;

public class SimServiceObserver {

    private static final String TAG = "SimServiceObserver";

    interface Listener {
        void onConnected();
    }

    private final Context mContext;
    private int mSubID = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    private Handler mHandler;
    private Listener mListener;

    private final Runnable runnable = new Runnable() {
        @Override
        public synchronized void run() {
            try {
                if (mSubID == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                    unregister();
                else {
                    TelephonyManager tm = mContext.getSystemService(TelephonyManager.class).createForSubscriptionId(mSubID);
                    if (CommonUtil.hasSignal(tm)) {
                        mListener.onConnected();
                        unregister();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if(mListener != null)
                    mHandler.postDelayed(this, 2000);
            }
        }
    };

    public SimServiceObserver(Context context) {
        mContext = context;
    }

    public void register(int subID, Listener listener) {
        if (mListener != null)
            return;
        mSubID = subID;
        mListener = listener;
        if(mHandler == null)
            mHandler = new Handler(mContext.getMainLooper());
        mHandler.post(runnable);
        CSLog.d(TAG, "Registered");
    }

    public void unregister() {
        if (mListener == null)
            return;
        mHandler.removeCallbacks(runnable);
        mSubID = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        mListener = null;
        CSLog.d(TAG, "Unregistered");
    }
}
