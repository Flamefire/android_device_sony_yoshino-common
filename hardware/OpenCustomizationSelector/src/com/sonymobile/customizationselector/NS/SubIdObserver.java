package com.sonymobile.customizationselector.NS;

import android.content.Context;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import com.sonymobile.customizationselector.CSLog;
import com.sonymobile.customizationselector.CommonUtil;

public class SubIdObserver {
    private static final String TAG = "SubIdObserver";

    interface Listener {
        void onConnected(int subID);
    }

    private final Context mContext;
    private Handler mHandler;
    private Listener mListener;

    private final Runnable runnable = new Runnable() {
        @Override
        public synchronized void run() {
            if(mListener == null)
                return;
            try {
                int subId = CommonUtil.getSubID(mContext);
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    mListener.onConnected(subId);
                    unregister();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (mListener != null)
                    mHandler.postDelayed(this, 2000);
            }
        }
    };

    public void register(Listener listener) {
        if (mListener != null)
            return;
        mListener = listener;
        if(mHandler == null)
            mHandler = new Handler(mContext.getMainLooper());
        mHandler.post(runnable);

        CSLog.d(TAG, "Registered");
    }

    private void unregister() {
        if(mListener == null)
            return;
        mHandler.removeCallbacks(runnable);
        mListener = null;
        CSLog.d(TAG, "Unregistered");
    }

    public SubIdObserver(Context context) {
        mContext = context;
    }
}
