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
    private final Handler mHandler;
    private final int mSubID;
    private Listener mListener;

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (CommonUtil.isSIMLoaded(mContext, mSubID)){
                    CSLog.d(TAG, "Connected");
                    mListener.onConnected();
                    mListener = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (mListener != null)
                    mHandler.postDelayed(this, 2000);
            }
        }
    };

    public void unregister() {
        if (mListener != null) {
            mListener = null;
            mHandler.removeCallbacks(mRunnable);
        }
    }

    public SlotObserver(Context context, Handler handler, int subID, Listener listener) {
        if (subID == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            throw new IllegalArgumentException();

        mContext = context;
        mHandler = handler;
        mSubID = subID;
        mListener = listener;

        mHandler.post(mRunnable);
    }
}
