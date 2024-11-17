/*
 * Copyright (c) 2024 Alexander Grund (Flamefire)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 */

 package com.yoshino.parts;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PIFUpdater extends AsyncTask<Void, Void, String> {

    public interface Listener{
        void OnCompleted();
    }

    private static final String TAG = "PIFUpdater";

    private static final String PIF_DOWNLOAD_URL =
            "https://raw.githubusercontent.com/Flamefire/lineageos_lilac/refs/heads/main/tools/pif.json";
    private static final String CUSTOM_PIF_PATH = "/sdcard/pif.json";

    private final Context mContext;
    private final Listener mListener;

    public PIFUpdater(Context context, Listener listener) {
        mContext = context;
        mListener = listener;
    }

    @Override
    protected String doInBackground(Void... voids) {
        File tempFile = null;
        try {
            final URL url = new URL(PIF_DOWNLOAD_URL);
            tempFile = File.createTempFile("tempDownload", ".json", mContext.getCacheDir());

            if (!downloadFile(url, tempFile))
                return "Download failed";
            Log.i(TAG, "Downloaded PIF from " + url);

            final File customPifFile = new File(CUSTOM_PIF_PATH);
            if (customPifFile.exists()) {
                Log.i(TAG, "Checking existing config");
                if (areFilesEqual(tempFile, customPifFile))
                    return "Config is already up-to-date.";
                Log.i(TAG, "Update required");
                if (!customPifFile.delete())
                    return "Failed to remove old config at " + customPifFile.getPath();
            }

            if (tempFile.renameTo(customPifFile))
                return "Config updated";
            else
                return "Failed to write to config file at " + customPifFile.getPath();

        } catch (Exception e) {
            e.printStackTrace();
            return "An error occurred";
        } finally {
            if (tempFile != null && tempFile.exists())
                tempFile.delete();
        }
    }

    @Override
    protected void onPostExecute(OperationResult result) {
        String customConfig = null;
        try {
            if(result.success && CUSTOM_PIF_PATH.exists() && !areFilesEqual(getInternalPIFLocation(), CUSTOM_PIF_PATH))
                customConfig = CUSTOM_PIF_PATH.getPath();
        } catch(Exception e) {
            e.printStackTrace();
        }
        Toast.makeText(mContext, result.message, Toast.LENGTH_SHORT).show();
        mListener.OnCompleted(customConfig);
    }

    private static boolean downloadFile(URL url, File destination) {
        try {
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            final int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Connection failed: Response code " + responseCode);
                return false;
            }
            try (InputStream inputStream = connection.getInputStream();
                OutputStream outputStream = new FileOutputStream(destination)) {
                final byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1)
                    outputStream.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Download failed: Error " + e);
            return false;
        }
        return true;
    }

    private static boolean areFilesEqual(File file1, File file2) throws Exception {
        if (file1.length() != file2.length())
            return false;
        try (BufferedReader reader1 = new BufferedReader(new InputStreamReader(new FileInputStream(file1)));
             BufferedReader reader2 = new BufferedReader(new InputStreamReader(new FileInputStream(file2)))) {

            while (true) {
                final String line1 = reader1.readLine();
                final String line2 = reader2.readLine();
                if (line1 == null)
                    return line2 == null; // both EOF
                else if (!line1.equals(line2))
                    return false;
            }
        }
    }
}
