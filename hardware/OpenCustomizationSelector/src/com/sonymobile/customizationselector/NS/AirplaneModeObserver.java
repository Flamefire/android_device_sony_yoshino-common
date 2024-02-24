package com.sonymobile.customizationselector.NS;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import com.sonymobile.customizationselector.CSLog;

public class AirplaneModeObserver extends ContentObserver {

    private static final String TAG = "AirplaneModeObserver";

    interface Listener {
        void onChange(Uri uri);
    }

    private final Context mContext;
    private boolean mRegistered = false;
    private Listener mListener = null;

    public AirplaneModeObserver(Context context, Handler handler) {
        super(handler);
        mContext = context;
    }

    public void register(Listener listener) {
        if (!mRegistered) {
            mListener = listener;
            mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
                    false, this, UserHandle.USER_CURRENT);
            mRegistered = true;
            CSLog.d(TAG, "Registered");
        }
    }

    public void unregister() {
        if (mRegistered) {
            mContext.getContentResolver().unregisterContentObserver(this);
            mRegistered = false;
            CSLog.d(TAG, "Unregistered");
        }
    }

    @Override
    public void onChange(boolean b, Uri uri) {
        if (!b) {
            mListener.onChange(uri);
        }
    }
}
