package com.sonymobile.customizationselector;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class EventReceiver extends BroadcastReceiver {
    private static final String TAG = "EventReceiver";

    public static final String CS_IMS = "cs_ims";
    public static final String CS_NOTIFICATION = "cs_notification";
    public static final String SUBID_KEY = "event_subID";

    private static final String CHANNEL_ID = "Sony Modem";
    private static final String SUBSCRIPTION_KEY = "subscription"; // PhoneConstants.SUBSCRIPTION_KEY (internal)

    private int getSubId(Context context, Intent intent) {
        int subId = intent.getIntExtra(SUBSCRIPTION_KEY, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        CSLog.d(TAG, "Event received for subscription: " + subId);
        if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && CommonUtil.isMandatorySimParamsAvailable(context, subId))
            return subId;
        else
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            CSLog.e(TAG, "Context or Intent is null");
            return;
        }

        int subID = getSubId(context, intent);
        if (subID != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            CSLog.d(TAG, "Saving sub ID for later");
            Configurator.getPreferences(context).edit().putInt(SUBID_KEY, subID).apply();
        }

        if (Settings.System.getInt(context.getContentResolver(), CS_IMS, 1) == 0) {
            CSLog.d(TAG, "IMS disabled, not parsing");
            if (!CommonUtil.isModemDefault(readModemFile()[1])) {
                CSLog.d(TAG, "Modem not default but IMS turned off as per settings.");
                new ImsSwitcher(context).switchOffIMS();
            }
            return;
        }

        String action = intent.getAction();
        if (CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED.equals(action)) {
            CSLog.d(TAG, "Carrier config changed received");

            if (CommonUtil.isDefaultDataSlot(context, getSubId(context, intent))) {
                CSLog.d(TAG, "Default data SIM loaded");
                Intent service = new Intent(context, CustomizationSelectorService.class)
                    .setAction(CustomizationSelectorService.EVALUATE_ACTION);
                context.startService(service);
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            if (CommonUtil.isDualSim(context))
                DSDataSubContentJob.scheduleJob(context);
            notifyStatus(context);
        }
    }

    private void notifyStatus(Context context) {
        String[] status = readModemFile();

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        createChannel(manager);

        if (Settings.System.getInt(context.getContentResolver(), CS_NOTIFICATION, 1) == 1) {
            manager.notify(1, new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle(CHANNEL_ID)
                    .setContentText("Status: ...")
                    .setSmallIcon(R.drawable.ic_baseline_sim_card_24)
                    .setOngoing(false)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSound(null)
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText("Status: " + status[0] + "\nConfig: " + status[1] +
                                    "\nCust ID: " + SystemProperties.get(Configurator.PROP_TA_AC_VERSION, "N/A")))
                    .setColorized(true)
                    .addAction(R.drawable.ic_baseline_sim_card_24,
                               "Disable Notification",
                               PendingIntent.getBroadcast(context, 1, new Intent(context, NotificationReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT))
                    .build());
        }
    }

    private void createChannel(NotificationManager manager) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_HIGH);
        channel.setLightColor(Color.WHITE);
        channel.setSound(null, null);
        channel.enableVibration(false);

        manager.createNotificationChannel(channel);
    }

    private String[] readModemFile() {
        String[] stat = {"N/A", "N/A"};
        ModemSwitcher.ModemStatusContent data = ModemSwitcher.readModemStatusFile();
        if(data != null)
            stat = new String[]{data.success, data.currentModem};
        return stat;
    }
}
