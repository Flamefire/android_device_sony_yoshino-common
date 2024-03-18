package com.sonymobile.customizationselector;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatEditText;

import java.util.ArrayList;
import java.util.Locale;

public class ModemSwitcherActivity extends Activity {

    private static final String TAG = "ModemSwitcherActivity";

    private static final String INITIAL_MODEM_PREF = "initialModem";

    private String mInitialModem;
    private ListView mModemListView;
    private ModemSwitcher mModemSwitcher;
    private SharedPreferences mPreference;

    private void applyModem(String modemFileName) {
        CSLog.d(TAG, "selected modem is " + modemFileName);

        if (mModemSwitcher.setModemConfiguration(ModemSwitcher.MODEM_FS_PATH + modemFileName))
            getSystemService(PowerManager.class).reboot(getApplicationContext().getString(R.string.reboot_reason_modem_debug));
    }

    private void saveInitialModem(String initialModem) {
        mInitialModem = mPreference.getString(INITIAL_MODEM_PREF, "");
        CSLog.d(TAG, "Save initial modem" + mInitialModem);

        if (TextUtils.isEmpty(mInitialModem) && !TextUtils.isEmpty(initialModem)) {
            CSLog.d(TAG, "Save initial modem in preference");
            mInitialModem = initialModem;
            mPreference.edit().putString(INITIAL_MODEM_PREF, initialModem).apply();
        }
    }

    private void verifyPick(final String modemFileName) {
        Builder builder = new Builder(this);
        builder.setTitle(R.string.debug_verify_title).setMessage(getResources().getString(R.string.debug_verify_text, modemFileName));
        builder.setPositiveButton(R.string.ok_button_label, (dialogInterface, i) -> applyModem(modemFileName));
        builder.setNegativeButton(R.string.cancel_button_label, (dialogInterface, i) -> dialogInterface.dismiss());
        builder.create().show();
    }

    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mPreference = Configurator.getPreferences(this);

        setRequestedOrientation(1);
        mModemSwitcher = new ModemSwitcher();
        try {
            String currentModem = ModemSwitcher.getCurrentModemConfig().replace(ModemSwitcher.MODEM_FS_PATH, "");
            CSLog.d(TAG, "current modem" + currentModem);

            if (currentModem.equals(ModemSwitcher.SINGLE_MODEM_FS) || currentModem.equals("")) {
                CSLog.d(TAG, "Single modem device");
                setContentView(R.layout.modem_handling_single);
                return;
            }
            saveInitialModem(currentModem);
            setContentView(R.layout.modem_handling);

            ((TextView) findViewById(R.id.initial_modem)).setText(mInitialModem);
            ((TextView) findViewById(R.id.current_modem)).setText(currentModem);
            AppCompatEditText editText = findViewById(R.id.search_modem);

            Button button = findViewById(R.id.restore_button);
            button.setClickable(true);

            ArrayList<String> modemList = new ArrayList<>();
            for (String m : mModemSwitcher.getAvailableModemConfigurations()) {
                modemList.add(m.replace(ModemSwitcher.MODEM_FS_PATH, ""));
            }

            mModemListView = findViewById(R.id.modem_list);
            setupListAdapter(modemList);
            button.setOnClickListener(view -> {
                if (mInitialModem != null)
                    verifyPick(mInitialModem);
            });

            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String searchString = s.toString().toLowerCase();
                    ArrayList<String> sModems = new ArrayList<>();
                    for (String modemName : modemList) {
                        if (modemName.toLowerCase().contains(searchString))
                            sModems.add(modemName);
                    }

                    setupListAdapter(sModems);
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });
        } catch (Exception e) {
            CSLog.e(TAG, "Not possible to read the modem files", e);
            finish();
        }
    }

    private synchronized void setupListAdapter(ArrayList<String> modemList) {
        mModemListView.setAdapter(null);
        mModemListView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, modemList));
        mModemListView.setOnItemClickListener((adapterView, view, i, j) -> {
            Object itemAtPosition = mModemListView.getItemAtPosition(i);
            if (itemAtPosition != null)
                verifyPick(itemAtPosition.toString());
        });
    }
}
