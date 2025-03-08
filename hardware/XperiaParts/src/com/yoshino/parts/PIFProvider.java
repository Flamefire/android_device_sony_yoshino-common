package com.yoshino.parts;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class PIFProvider extends ContentProvider {
    private final String TAG = "PIFProvider";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("Query not supported");
    }

    @Override
    public String getType(Uri uri) {
        return "application/json";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Insert not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Delete not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Update not supported");
    }

    private ParcelFileDescriptor createFilePipe(File file) throws FileNotFoundException {
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
                     InputStream input = new FileInputStream(file)) {

                    final byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = input.read(buffer)) != -1)
                        output.write(buffer, 0, bytesRead);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
            return pipe[0];
        } catch (IOException e) {
            throw new FileNotFoundException("Could not open pipe for: " + file.toString());
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        if (context == null)
            throw new FileNotFoundException("Context is null");

        File file = PIFUpdater.CUSTOM_PIF_PATH;
        if (file.length() > 0)
            Log.i(TAG, "Using custom PIF config at " + file);
        else {
            file = new PIFUpdater(context, null).getInternalPIFLocation();
            if (file.exists())
                Log.i(TAG, "Using stored PIF config " + file);
            else
                throw new FileNotFoundException("File not found");
        }

        return createFilePipe(file);
    }
}
