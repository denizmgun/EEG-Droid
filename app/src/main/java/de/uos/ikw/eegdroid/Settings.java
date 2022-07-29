package de.uos.ikw.eegdroid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.Objects;

public class Settings extends AppCompatActivity {

    private EditText editText_saveDir;
    private EditText editText_username;
    private EditText editText_userID;
    private EditText editText_IP;
    private EditText editText_port;
    private SwitchCompat switch_inAppFilter;
    private SwitchCompat switch_eegLabels;
    private SwitchCompat switch_showStats;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        //Activates Back button in Menu
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        //Retrieve configuration from sharedPreferences
        sharedPreferences = getSharedPreferences("userPreferences", MODE_PRIVATE);
        String saveDir = sharedPreferences.getString("saveDir", getResources().getString(R.string.default_folder));
        String username = sharedPreferences.getString("username", getResources().getString(R.string.default_username));
        String userID = sharedPreferences.getString("userID", getResources().getString(R.string.default_userID));
        String IP = sharedPreferences.getString("IP", "192.168.1.125");
        String port = sharedPreferences.getString("port", "65432");
        boolean inAppFilterEnabled = sharedPreferences.getBoolean("inAppFilter", true);
        boolean eegLabelsEnabled = sharedPreferences.getBoolean("eegLabels", true);
        boolean showStatsEnabled = sharedPreferences.getBoolean("showStats", false);

        editText_saveDir = findViewById(R.id.editText_saveDir);
        editText_username = findViewById(R.id.editText_username);
        editText_userID = findViewById(R.id.editText_userID);
        editText_IP = findViewById(R.id.editText_IP);
        editText_port = findViewById(R.id.editText_port);
        switch_inAppFilter = findViewById(R.id.switch_inAppFilter);
        switch_eegLabels = findViewById(R.id.switch_eegLabels);
        switch_showStats = findViewById(R.id.switch_showStats);
        Button applyChangesButton = findViewById(R.id.settings_apply_changes);
        Runnable updateUI = () -> {
            editText_saveDir.setText(saveDir);
            editText_username.setText(username);
            editText_userID.setText(userID);
            editText_IP.setText(IP);
            editText_port.setText(port);
            switch_inAppFilter.setChecked(inAppFilterEnabled);
            switch_eegLabels.setChecked(eegLabelsEnabled);
            switch_showStats.setChecked(showStatsEnabled);
        };
        runOnUiThread(updateUI);


        //Button to apply changes introduced in EditText
        applyChangesButton.setOnClickListener(v -> {

            //Write the new data in sharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("saveDir", editText_saveDir.getText().toString());
            editor.putString("username", editText_username.getText().toString());
            editor.putString("userID", editText_userID.getText().toString());
            editor.putString("IP", editText_IP.getText().toString());
            editor.putString("port", editText_port.getText().toString());
            editor.putBoolean("inAppFilter", switch_inAppFilter.isChecked());
            sharedPreferences.edit().putBoolean("inAppFilter", switch_inAppFilter.isChecked()).apply();
            editor.putBoolean("eegLabels", switch_eegLabels.isChecked());
            sharedPreferences.edit().putBoolean("eegLabels", switch_eegLabels.isChecked()).apply();
            editor.putBoolean("showStats", switch_showStats.isChecked());
            sharedPreferences.edit().putBoolean("showStats", switch_eegLabels.isChecked()).apply();
            editor.apply();

            //Notifies the user
            Toast.makeText(getApplicationContext(), R.string.settings_saved, Toast.LENGTH_LONG).show();

            //Restart the App
            Intent restartIntent = getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(getBaseContext().getPackageName());
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(restartIntent);
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case android.R.id.home:
                onBackPressed();
                return true;

            case R.id.restore_default_settings:
                //Handles the Dialog to confirm default restore
                AlertDialog.Builder alert = new AlertDialog.Builder(this)
                        .setTitle("Restore confirmation")
                        .setMessage("Do you want to restore the default settings?");
                alert.setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    editText_saveDir.setText(getResources().getString(R.string.default_folder));
                    editText_username.setText(getResources().getString(R.string.default_username));
                    editText_userID.setText(getResources().getString(R.string.default_userID));
                    editText_IP.setText(getResources().getString(R.string.default_IP));
                    editText_port.setText(getResources().getString(R.string.default_port));
                });
                alert.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    // close dialog
                    dialog.cancel();
                });
                alert.show();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
