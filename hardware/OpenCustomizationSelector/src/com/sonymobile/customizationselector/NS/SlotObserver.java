package com.sonymobile.customizationselector.NS;

import android.content.Context;
import android.os.Handler;
import android.telephony.SubscriptionManager;
import com.sonymobile.customizationselector.CSLog;
import com.sonymobile.customizationselector.CommonUtil;

public class SlotObserver {

    private static final String TAG = "SlotObserver";

    interface Listener {
        void onConnected();
    }

    private final Context mContext;
    private Handler mHandler;
    private Listener mListener;
    private int mSubID = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    private final Runnable runnable = new Runnable() {
        @Override
        public synchronized void run() {
            try {
                if (mSubID == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                    unregister();
                else if (CommonUtil.isSIMLoaded(mContext, mSubID)) {
                    mListener.onConnected();
                    unregister();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if(mListener != null)
                    mHandler.postDelayed(this, 2000);
            }
        }
    };

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

    public SlotObserver(Context context) {
        this.mContext = context;
    }
}
