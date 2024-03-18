package com.sonymobile.customizationselector;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.view.WindowManager;

import java.io.IOException;

public class ImsSwitcher {

    private static final String TAG = "IMS_Switcher";

    private final Context mContext;

    public ImsSwitcher(Context context) {
        mContext = context;
    }

    public void switchOnIMS(int subID) {
        CSLog.d(TAG, "switching IMS ON");
        // Need to reset configuration preference in order to allow reboot dialog to appear.
        new Configurator(mContext, null).clearConfigurationKey();

        if (CommonUtil.isDefaultDataSlot(mContext, subID)) {
            CSLog.d(TAG, "Default data SIM loaded");
            Intent service = new Intent(mContext, CustomizationSelectorService.class)
                .setAction(CustomizationSelectorService.EVALUATE_ACTION);
            mContext.startService(service);
        }
    }

    public void switchOffIMS() {
        CSLog.d(TAG, "switching IMS OFF");
        try {
            String currentModem = ModemSwitcher.getCurrentModemConfig().replace(ModemSwitcher.MODEM_FS_PATH, "");
            CSLog.d(TAG, "Current modem: " + currentModem);
            if (CommonUtil.isModemDefault(currentModem)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext, R.style.AppDialog);
                builder.setMessage("Your modem is already default, no reboot required");
                builder.setPositiveButton(R.string.ok_button_label, (dialogInterface, i) -> dialogInterface.dismiss());
                AlertDialog dialog = builder.create();
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
                if (!dialog.isShowing())
                    dialog.show();
            } else {
                String[] defaultModems = CommonUtil.getDefaultModems();
                String build = SystemProperties.get("ro.build.flavor", "none");
                if (build.contains("maple_dsds"))
                    applyModem(defaultModems[4]);
                else if (build.contains("maple"))
                    applyModem(defaultModems[3]);
                else if (build.contains("poplar_dsds"))
                    applyModem(defaultModems[2]);
                else if (build.contains("poplar"))
                    applyModem(defaultModems[1]);
                else if (build.contains("lilac"))
                    applyModem(defaultModems[0]);
                else
                    CSLog.e(TAG, "Unable to find default modem for build: " + build);
            }
        } catch (IOException e) {
            CSLog.e(TAG, "ERROR: ", e);
        }
    }

    private void applyModem(String modem) {
        CSLog.d(TAG, "Turning to default to: " + modem);

        if (new ModemSwitcher().setModemConfiguration(ModemSwitcher.MODEM_FS_PATH + modem)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext, R.style.AppDialog);
            builder.setCancelable(false);
            builder.setMessage("Your device has now switched to default modem " + modem + "\nReboot required.");
            builder.setPositiveButton("Reboot", (dialogInterface, i) -> {
                dialogInterface.dismiss();
                mContext.getSystemService(PowerManager.class).reboot(mContext.getString(R.string.reboot_reason_modem_debug));
            });
            AlertDialog dialog = builder.create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            if (!dialog.isShowing())
                dialog.show();
        }
    }
}
