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
    private boolean mRegistered = false;
    private Handler mHandler;
    private Listener mListener;

    private final Runnable runnable = new Runnable() {
        @Override
        public synchronized void run() {
            try {
                int subId = getSubID();
                if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    mListener.onConnected(subId);
                    unregister();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (mHandler != null) {
                    mHandler.postDelayed(this, 2000);
                }
            }
        }
    };

    public void register(Listener listener) {
        if (!mRegistered) {
            mListener = listener;
            mHandler = new Handler(mContext.getMainLooper());

            mHandler.post(runnable);
            mRegistered = true;

            CSLog.d(TAG, "Registered");
        }
    }

    private int getSubID() {
        int[] subs = null;
        if (CommonUtil.isDualSim(mContext)) {
            subs = SubscriptionManager.getSubId(Settings.System.getInt(mContext.getContentResolver(), "ns_slot", 0));
        } else {
            subs = SubscriptionManager.getSubId(0);
        }
        return subs == null ? SubscriptionManager.INVALID_SUBSCRIPTION_ID : subs[0];
    }

    private void unregister() {
        mListener = null;
        mHandler.removeCallbacks(runnable);
        mHandler = null;

        mRegistered = false;
        CSLog.d(TAG, "Unregistered");
    }

    public SubIdObserver(Context context) {
        mContext = context;
    }
}
