package com.sonymobile.customizationselector.NS;

import android.os.Handler;
import android.telephony.TelephonyManager;
import com.sonymobile.customizationselector.CSLog;
import com.sonymobile.customizationselector.CommonUtil;

public class SimServiceObserver {

    private static final String TAG = "SimServiceObserver";

    interface Listener {
        void onConnected();
    }

    private final Handler mHandler;
    private final TelephonyManager mTm;
    private Listener mListener;

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (mListener == null)
                return;
            try {
                if (CommonUtil.hasSignal(mTm))
                    mListener.onConnected();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (mListener != null)
                    mHandler.postDelayed(this, 2000);
            }
        }
    };

    public SimServiceObserver(Handler handler, TelephonyManager tm) {
        mHandler = handler;
        mTm = tm;
    }

    public void register(Listener listener) {
        if (mListener != null)
            return;
        mListener = listener;
        mHandler.post(runnable);
        CSLog.d(TAG, "Registered");
    }

    public void unregister() {
        if (mListener == null)
            return;
        mHandler.removeCallbacks(runnable);
        mListener = null;
        CSLog.d(TAG, "Unregistered");
    }
}
