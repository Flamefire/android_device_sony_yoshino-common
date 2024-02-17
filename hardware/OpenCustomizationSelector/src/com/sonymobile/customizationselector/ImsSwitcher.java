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
        this.mContext = context;
    }

    public void switchOnIMS(int subID) {
        CSLog.d(TAG, "switching IMS ON");
        // Need to reset configuration preference in order to allow reboot dialog to appear.
        new Configurator(mContext, null).clearConfigurationKey();

        if (CommonUtil.isDefaultDataSlot(mContext, subID)) {
            CSLog.d(TAG, "Default data SIM loaded");
            Intent service = new Intent(mContext, CustomizationSelectorService.class);
            service.setAction("evaluate_action");
            mContext.startService(service);
        }
    }

    public void switchOffIMS() {
        CSLog.d(TAG, "switching IMS OFF");
        try {
            String currentModem = ModemSwitcher.getCurrentModemConfig().replace(ModemSwitcher.MODEM_FS_PATH, "");
            if (CommonUtil.isModemDefault(currentModem)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                builder.setMessage("Your modem is already default, no reboot required");
                builder.setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.dismiss());
                AlertDialog dialog = builder.create();
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
                if (!dialog.isShowing()) {
                    dialog.show();
                }
            } else {
                String[] defaultModems = CommonUtil.getDefaultModems();
                String build = SystemProperties.get("ro.build.flavor", "none");
                if (build.contains("maple_dsds")) {
                    applyModem(defaultModems[4]);
                    return;
                }
                if (build.contains("maple")) {
                    applyModem(defaultModems[3]);
                    return;
                }
                if (build.contains("poplar_dsds")) {
                    applyModem(defaultModems[2]);
                    return;
                }
                if (build.contains("poplar") || build.contains("poplar_canada")) {
                    applyModem(defaultModems[1]);
                    return;
                }
                if (build.contains("lilac")) {
                    applyModem(defaultModems[0]);
                    return;
                }
                CSLog.e(TAG, "Unable to find default modem for build: " + build);
            }
        } catch (IOException e) {
            CSLog.e(TAG, "ERROR: ", e);
        }
    }

    private void applyModem(String modem) {
        CSLog.d(TAG, "Turning to default to: " + modem);

        if (new ModemSwitcher().setModemConfiguration(ModemSwitcher.MODEM_FS_PATH + modem)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setCancelable(false);
            builder.setMessage("Your device has now switched to default modem " + modem + "\nReboot required.");
            builder.setPositiveButton("Reboot", (dialogInterface, i) -> {
                dialogInterface.dismiss();
                mContext.getSystemService(PowerManager.class).reboot(mContext.getString(R.string.reboot_reason_modem_debug));
            });
            AlertDialog dialog = builder.create();
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            if (!dialog.isShowing()) {
                dialog.show();
            }
        }
    }
}
