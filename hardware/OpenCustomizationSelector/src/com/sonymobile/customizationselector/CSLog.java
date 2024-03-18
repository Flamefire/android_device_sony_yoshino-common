package com.sonymobile.customizationselector;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.icu.text.SimpleDateFormat;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Locale;

public class CSLog {

    private static final String PREFIX = "CS-";
    private static final String LOG_FILE = "/data/user_de/0/com.sonymobile.customizationselector/files/cs.log";

    public static void d(String tag, String msg) {
        Log.d(PREFIX + tag, msg);
        writeLog(PREFIX + tag, msg, "D");
    }

    public static void e(String tag, String msg) {
        Log.e(PREFIX + tag, msg);
        writeLog(PREFIX + tag, msg, "E");
    }

    public static void e(String tag, String msg, Exception e) {
        Log.e(PREFIX + tag, msg, e);
        writeLog(PREFIX + tag, msg + " " + e.toString(), "E");
    }

    public static void i(String tag, String msg) {
        Log.i(PREFIX + tag, msg);
        writeLog(PREFIX + tag, msg, "I");
    }

    public static void w(String tag, String msg) {
        Log.w(PREFIX + tag, msg);
        writeLog(PREFIX + tag, msg, "W");
    }

    public static void logSimValues(Context context, String tag) {
        String subscriberID = "", simOP = "", simOpName = "";

        int defaultSubscriptionId = SubscriptionManager.getDefaultSubscriptionId();
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);

        if (defaultSubscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            simOP = tm.getSimOperator(defaultSubscriptionId);
            if (simOP == null)
                simOP = "";
            subscriberID = tm.getSubscriberId(defaultSubscriptionId);
            String simOperatorName = tm.getSimOperatorName(defaultSubscriptionId);
            simOpName = simOperatorName != null ? simOperatorName.replaceAll("[\n\r]", "").trim() : "";
        }
        d(tag, "SimValues: MCC-MNC=" + simOP + ", SP-name=" + simOpName + ", IMSI=" + subscriberID);
    }

    public static void logVersion(Context context, String tag) {
        PackageManager packageManager = context.getPackageManager();
        PackageInfo packageInfo = null;
        if (packageManager != null) {
            try {
                packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            } catch (NameNotFoundException e) {
                e.printStackTrace();
                return;
            }
        }
        if (packageInfo != null)
            d(tag, "Version: " + packageInfo.versionName);
    }

    private static boolean sizeCheckDone = false;

    private static void writeLog(String tag, String msg, String type) {
        File logFile = new File(LOG_FILE);
        File logDir = logFile.getParentFile();
        BufferedWriter brw = null;
        try {
            if (!logDir.exists() && !logDir.mkdirs())
                return;
            if (!logFile.exists()) {
                if (!logFile.createNewFile())
                    return;
            } else if(!sizeCheckDone) {
                // Delete file if it grows larger than 1 MB
                if(logFile.length() > 1024 * 1024)
                    logFile.delete();
                sizeCheckDone = true;
            }
            brw = new BufferedWriter(new FileWriter(logFile, logFile.exists()));

            String timeStamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS").format(System.currentTimeMillis());
            brw.append(timeStamp + " " + type + " " + tag + ": " + msg);
            brw.newLine();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (brw != null)
                    brw.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
