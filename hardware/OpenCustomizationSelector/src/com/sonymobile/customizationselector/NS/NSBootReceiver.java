package com.sonymobile.customizationselector.NS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import com.sonymobile.customizationselector.CSLog;
import com.sonymobile.customizationselector.CommonUtil;

public class NSBootReceiver extends BroadcastReceiver {
    private static final String TAG = "NSBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) {
            CSLog.d(TAG, "Error: Context was null");
            return;
        }
        if (Settings.System.getInt(context.getContentResolver(), "ns_service", 0) == 1) {
            if (CommonUtil.isDualSim(context) && Settings.System.getInt(context.getContentResolver(), "ns_slot", -1) == -1) {
                CSLog.d("NSBootReceiver", "Device is dual sim, but slot pref is invalid");
                return;
            }
            CSLog.d(TAG, "Starting service ...");
            context.startServiceAsUser(new Intent(context, NetworkSwitcher.class), UserHandle.CURRENT);
        }
    }
}
