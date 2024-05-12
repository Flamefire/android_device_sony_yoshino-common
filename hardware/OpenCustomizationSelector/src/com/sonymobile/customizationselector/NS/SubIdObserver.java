package com.sonymobile.customizationselector.NS;

import android.content.Context;
import android.os.Handler;
import android.telephony.SubscriptionManager;
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
            if (mListener == null)
                return;
            try {
                int subId = CommonUtil.getSubID(mContext);
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    mListener.onConnected(subId);
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

    public SubIdObserver(Context context, Listener listener) {
        mContext = context;
        mHandler = new Handler(mContext.getMainLooper());
        mListener = listener;
        mHandler.post(runnable);
   }
}
