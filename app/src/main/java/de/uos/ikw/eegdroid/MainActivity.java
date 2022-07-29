package de.uos.ikw.eegdroid;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.util.Objects;

import us.feras.mdv.BuildConfig;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {


    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private static File dirSessions;
    private final ManageSessions ManageSessions = new ManageSessions();

    public static File getDirSessions() {
        return dirSessions;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Retrieve saveDir, username & userID from sharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("userPreferences", MODE_PRIVATE);
        //If sharedPreferences not found (first time usage), use default values
        String saveDir = sharedPreferences.getString("saveDir", getResources().getString(R.string.default_folder));
        String username = sharedPreferences.getString("username", getResources().getString(R.string.default_username));
        String userID = sharedPreferences.getString("userID", getResources().getString(R.string.default_userID));

        // TODO: Use new scoped storage api to make the folder accessible through laptop

        // File object to save the directory to save the EEG recordings
//        dirSessions = new File(Objects.requireNonNull(this.getFilesDir()).getAbsolutePath() + saveDir);
        dirSessions = new File(String.valueOf(Environment.getExternalStorageDirectory().toPath().resolve("Download")));
        ManageSessions.createDirectory(dirSessions);
//        Uri dirUri = Uri.parse(Environment.getExternalStorageDirectory() + saveDir + "/"); //Uri to open the folder with sessions
        Uri dirUri = Uri.parse(String.valueOf(Environment.getExternalStorageDirectory().toPath().resolve("Download")));
        Log.d("Main Directory", dirUri.getPath());

        //Sets the lateral Menu
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //Shows basic information about the App
        TextView appName = findViewById(R.id.main_app_name);
        TextView appVersion = findViewById(R.id.main_app_version);
        TextView textViewUsername = findViewById(R.id.textViewUsername);
        textViewUsername.setText(username);
        TextView textViewUserID = findViewById(R.id.textViewUserID);
        textViewUserID.setText(userID);
        TextView textViewSaveDir = findViewById(R.id.textViewSaveDir);
        textViewSaveDir.setText("" + saveDir + "/");

        ApplicationInfo applicationInfo = getApplicationInfo();
        appName.setText(applicationInfo.loadLabel(getPackageManager()));
        appName.setTextColor(getResources().getColor(R.color.colorPrimary));
        appVersion.setText(BuildConfig.VERSION_NAME);

        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);

        TableRow tableRowRecord = findViewById(R.id.tableRowRecord);
        TableRow tableRowDisplay = findViewById(R.id.tableRowDisplay);
        TableRow tableRowManage = findViewById(R.id.tableRowManage);
        TableRow tableRowLearn = findViewById(R.id.tableRowLearn);
        TableRow tableRowTutorial = findViewById(R.id.tableRowTutorial);

        //Allows initialize activity when click in home
        tableRowRecord.setOnClickListener(v -> {
            Intent intent = new Intent(getBaseContext(), Record.class);
            startActivity(intent);
        });

        tableRowDisplay.setOnClickListener(v -> {

            // Avoid that app crashes if no permission
            if (ContextCompat.checkSelfPermission(getApplicationContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        getApplicationContext(),
                        getText(R.string.permission_storage),
                        Toast.LENGTH_LONG
                ).show();
            } else {
                Intent intent = new Intent(getBaseContext(), Display.class);
                intent.putExtra("dirString", dirSessions.getPath());
                startActivity(intent);
            }
        });

        tableRowManage.setOnClickListener(v -> {

            // Avoid that app crashes if no permission
            if (ContextCompat.checkSelfPermission(getApplicationContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        getApplicationContext(),
                        getText(R.string.permission_storage),
                        Toast.LENGTH_LONG
                ).show();
            } else {
                Intent intent = new Intent(getBaseContext(), ManageSessions.class);
                intent.putExtra("dirString", dirSessions.getPath());
                startActivity(intent);

            }
        });


        tableRowLearn.setOnClickListener(v -> {
            Intent intent = new Intent(getBaseContext(), Learn.class);
            startActivity(intent);
        });

        tableRowTutorial.setOnClickListener(v -> {
            Intent intent = new Intent(getBaseContext(), Tutorial.class);
            startActivity(intent);
        });

        // Check if permission to write is granted
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

            } else {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_EXTERNAL_STORAGE);

            }
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.record) {
            Intent intent = new Intent(this, Record.class);
            startActivity(intent);

        } else if (id == R.id.display) {

            // Avoid that app crashes if no permission
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        getApplicationContext(),
                        getText(R.string.permission_storage),
                        Toast.LENGTH_LONG
                ).show();
            } else {
                Intent intent = new Intent(this, Display.class);
                intent.putExtra("dirString", dirSessions.getPath());
                startActivity(intent);
            }

        } else if (id == R.id.manage) {

            // Avoid that app crashes if no permission
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        getApplicationContext(),
                        getText(R.string.permission_storage),
                        Toast.LENGTH_LONG
                ).show();
            } else {
                Intent intent = new Intent(this, ManageSessions.class);
                intent.putExtra("dirString", dirSessions.getPath());
                startActivity(intent);
            }

        } else if (id == R.id.tfanalysis) {
            Intent intent = new Intent(this, TFAnalysis.class);
            startActivity(intent);

        } else if (id == R.id.learn) {
            Intent intent = new Intent(this, Learn.class);
            startActivity(intent);

        } else if (id == R.id.tutorial) {
            Intent intent = new Intent(this, Tutorial.class);
            startActivity(intent);

        } else if (id == R.id.settings) {
            Intent intent = new Intent(this, Settings.class);
            startActivity(intent);
        } else if (id == R.id.info) {
            Intent intent = new Intent(this, Info.class);
            startActivity(intent);
        }
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

}