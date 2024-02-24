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
        void onChange();
    }

    private final Context mContext;
    private Listener mListener = null;

    public AirplaneModeObserver(Context context, Handler handler) {
        super(handler);
        mContext = context;
    }

    public void register(Listener listener) {
        if (mListener != null)
            return;
        mContext.getContentResolver().registerContentObserver(getUri(), false, this, UserHandle.USER_CURRENT);
        mListener = listener;
        CSLog.d(TAG, "Registered");
    }

    public void unregister() {
        if (mListener == null)
            return;
        mContext.getContentResolver().unregisterContentObserver(this);
        mListener = null;
        CSLog.d(TAG, "Unregistered");
    }

    private static Uri getUri(){
        return Settings.System.getUriFor(Settings.Global.AIRPLANE_MODE_ON);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        if (!selfChange && mListener != null && getUri().equals(uri))
            mListener.onChange();
    }
}
