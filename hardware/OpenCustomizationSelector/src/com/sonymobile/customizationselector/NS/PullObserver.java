package com.sonymobile.customizationselector.NS;

import android.os.Handler;
import com.sonymobile.customizationselector.CSLog;

public class PullObserver {

    interface Predicate {
        boolean test();
    }

    private final String TAG;

    private final Handler mHandler;
    private final Predicate mPredicate;
    private Runnable mListener;

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (mListener == null)
                return;
            try {
                if (mPredicate.test())
                    mListener.run();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (mListener != null)
                    mHandler.postDelayed(this, 2000);
            }
        }
    };

    public PullObserver(String tag, Handler handler, Predicate predicate) {
        TAG = tag;
        mHandler = handler;
        mPredicate = predicate;
    }

    public void register(Runnable listener) {
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
