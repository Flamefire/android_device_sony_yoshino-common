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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.SystemProperties;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private static final String CUSTOM_PIF_NAME = "pif.json";
    private static final Uri STORAGE_URI = MediaStore.Downloads.EXTERNAL_CONTENT_URI;

    private final Context mContext;
    private final Listener mListener;

    private static String readInputStream(InputStream stream) throws IOException{
        try(InputStreamReader reader = new InputStreamReader(stream)) {
            char[] buffer = new char[128];
            StringBuilder content = new StringBuilder();
            for (int numRead; (numRead = reader.read(buffer, 0, buffer.length)) > 0; )
                content.append(buffer, 0, numRead);
            return content.toString();
        }
    }

    private Uri getCustomPIFConfigURI() {
        final String[] projection = {
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME
        };

        final String selection = MediaStore.Downloads.DISPLAY_NAME + " = ?";
        final String[] selectionArgs = new String[]{CUSTOM_PIF_NAME};

        final Cursor cursor = mContext.getContentResolver().query(STORAGE_URI, projection, selection, selectionArgs, null);
        if (cursor != null && cursor.moveToFirst()) {
            final long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
            return Uri.withAppendedPath(STORAGE_URI, String.valueOf(id));
        } else
            return null;
    }

    private String readCustomPIFConfigFile() {
        final File[] locations = new File[] {
            new File("/sdcard/pif.json"),
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "pif.json"),
            getCustomPIFLocation()
        };
        for (File file: locations) {
            Log.w(TAG, "Checking file " + file);
            if (!file.exists())
                continue;
            Log.w(TAG, "Exists!");
            try (InputStream is = new FileInputStream(file)){
                String content = readInputStream(is);
                Log.w(TAG, "File read: " + content);
            } catch (IOException e) {
                Log.e(TAG, "Failed reading " + e);
            }
        }

        final Uri provUri = Uri.parse("content://com.yoshino.parts.pifprovider/pif");
        try (InputStream inputStream = mContext.getContentResolver().openInputStream(provUri)) {
            if (inputStream != null) {
                final String content = readInputStream(inputStream);
                Log.w(TAG, "Read from " + provUri + ": " + content);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        final Uri fileUri = getCustomPIFConfigURI();
        if (fileUri == null)
            return null;

        try (InputStream inputStream = mContext.getContentResolver().openInputStream(fileUri)) {
            return readInputStream(inputStream);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read from Uri: " + e);
            return null;
        }
    }

    public boolean saveCustomPIFConfigFile(String name, String jsonContent) {
        final Uri existingFile = getCustomPIFConfigURI();
        if (existingFile != null)
            mContext.getContentResolver().delete(existingFile, null, null);

        final ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Downloads.DISPLAY_NAME, name);
        contentValues.put(MediaStore.Downloads.MIME_TYPE, "application/json");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        contentValues.put(MediaStore.Downloads.IS_PENDING, 1);

        final Uri uri = mContext.getContentResolver().insert(STORAGE_URI, contentValues);

        if (uri == null)
            return false;
        
        try(OutputStream outputStream = mContext.getContentResolver().openOutputStream(uri)) {
            if (outputStream != null) {
                outputStream.write(jsonContent.getBytes());
                outputStream.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to write content: " + e);
        }

        contentValues.clear();
        contentValues.put(MediaStore.Downloads.IS_PENDING, 0);
        mContext.getContentResolver().update(uri, contentValues, null, null);
        return true;
    }

    public PIFUpdater(Context context, Listener listener) {
        mContext = context;
        mListener = listener;
    }

    public File getCustomPIFLocation() {
        return new File(mContext.getCacheDir(), CUSTOM_PIF_NAME);
    }

    @Override
    protected String doInBackground(Void... voids) {
        try {
            final URL url = new URL(PIF_DOWNLOAD_URL);

            final String newConfig = downloadFile(url);
            if (newConfig == null)
                return "Download failed";
            Log.i(TAG, "Downloaded PIF from " + url);

            final String oldConfig = readCustomPIFConfigFile();

            if (oldConfig != null) {
                if (newConfig.equals(oldConfig))
                    return "Config is already up-to-date.";
                Log.i(TAG, "Update required");
            }

            if (saveCustomPIFConfigFile(CUSTOM_PIF_NAME, newConfig))
                return "Config updated";
            else
                return "Failed to write to config file";

        } catch (Exception e) {
            e.printStackTrace();
            return "An error occurred?";
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

    private static String downloadFile(URL url) {
        try {
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();
            final int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Connection failed: Response code " + responseCode);
                return null;
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return readInputStream(inputStream);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Download failed: Error " + e);
            return null;
        }
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
