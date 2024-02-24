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

    private boolean registered = false;

    private Handler handler;
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            try {
                synchronized (new Object()) {
                    int subId = getSubID();
                    if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        listener.onConnected(subId);
                        unregister();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (handler != null) {
                    handler.postDelayed(this, 2000);
                }
            }
        }
    };

    private Listener listener;

    public void register(Listener listener) {
        if (!registered) {
            this.listener = listener;
            handler = new Handler(mContext.getMainLooper());

            handler.post(runnable);
            registered = true;

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
        listener = null;
        handler.removeCallbacks(runnable);
        handler = null;

        registered = false;
        CSLog.d(TAG, "Unregistered");
    }

    public SubIdObserver(Context context) {
        this.mContext = context;
    }
}
