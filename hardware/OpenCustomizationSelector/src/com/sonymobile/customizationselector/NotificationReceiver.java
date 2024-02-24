package com.sonymobile.customizationselector;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class NotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context != null) {
            Settings.System.putInt(context.getContentResolver(), EventReceiver.CS_NOTIFICATION, 0);
            context.getSystemService(NotificationManager.class).cancel(1);
        }
    }
}
