package com.sonymobile.customizationselector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.telephony.SubscriptionManager;

public class PreferenceReceiver extends BroadcastReceiver {

    private static final String TAG = "PreferenceReceiver";
    private static final int ENABLED = 1;
    private static final int DISABLED = 0;
    private static final int INVALID = -1;
    private static final String PREF_EXTRA_KEY = "pref";
    private static final int PREF_IMS = 0;
    private static final int PREF_REAPPLY_MODEM = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || context == null) {
            CSLog.e(TAG, "Context or Intent is null");
            return;
        }

        int pref = intent.getIntExtra(PREF_EXTRA_KEY, INVALID);
        if (pref == PREF_IMS) {
            int change = intent.getIntExtra(EventReceiver.CS_IMS, INVALID);
            CSLog.d(TAG, "change received: " + change);
            int subID = Configurator.getPreferences(context).getInt(EventReceiver.SUBID_KEY, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            if (subID == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                CSLog.e(TAG, "Invalid sub ID, returning");
                return;
            }

            ImsSwitcher switcher = new ImsSwitcher(context);
            if (change == ENABLED)
                switcher.switchOnIMS(subID);
            else if (change == DISABLED)
                switcher.switchOffIMS();
        } else if (pref == PREF_REAPPLY_MODEM) {
            int apply = intent.getIntExtra(ModemSwitcher.CS_REAPPLY_MODEM, INVALID);
            if (apply == ENABLED)
                ModemSwitcher.reApplyModem(context);
            else if (apply == DISABLED)
                ModemSwitcher.revertReApplyModem();
            context.getSystemService(PowerManager.class).reboot(context.getString(R.string.reboot_reason));
        } else
            CSLog.d(TAG, "Invalid pref, returning ...");
    }
}
