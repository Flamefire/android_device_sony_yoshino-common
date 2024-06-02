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

    private final PullObserver mPullObserver;

    public void unregister() {
        mPullObserver.unregister();
    }

    public SlotObserver(Context context, Handler handler, int subID, Listener listener) {
        if (subID == SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            throw new IllegalArgumentException();

        mPullObserver = new PullObserver(TAG, handler, () -> {
            return CommonUtil.isSIMLoaded(context, subID);
        });
        mPullObserver.register(() -> {
            mPullObserver.unregister();
            listener.onConnected();
        });
    }
}
