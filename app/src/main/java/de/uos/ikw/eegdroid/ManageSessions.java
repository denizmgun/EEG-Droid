package de.uos.ikw.eegdroid;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ShareActionProvider;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuItemCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import de.uos.ikw.eegdroid.utilities.SessionAdapter;

public class ManageSessions extends AppCompatActivity {

    private SessionAdapter adapter;
    private String saveDir;
    private ArrayList<File> arrayListOfFiles;
    private ShareActionProvider shareActionProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_sessions);
        readDirectory(MainActivity.getDirSessions());

        //List <Files> to save the current state directory
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new SessionAdapter(arrayListOfFiles, getApplicationContext());
        recyclerView.setAdapter(adapter);

        // Add line between items of RecyclerView
        DividerItemDecoration mDividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(), 1);
        recyclerView.addItemDecoration(mDividerItemDecoration);

        // Receive the directory of the EEG Sessions
        Intent intent = getIntent();
        saveDir = intent.getExtras().getString("dirString");

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_manage, menu);
        MenuItem item = menu.findItem(R.id.send_session);
        MenuItem launchFileManager = menu.findItem(R.id.launch_file_manager);
        shareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
        MenuItem openFile = menu.findItem(R.id.open_with_app);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        boolean[] positions = adapter.getSelectedPositions();
        ArrayList<Integer> selectedPositions = new ArrayList<Integer>();
        for (int i = 0; i<positions.length ;i++){
            if (positions[i]) selectedPositions.add(i);
        }

        //Handles if no session has been selected
        if (selectedPositions.size() == 0) {

            if (item.getItemId() == R.id.launch_file_manager){
                launchFileManager();
                return true;
            }

            if (item.getItemId() == android.R.id.home) {
                onBackPressed();
                return true;
            }
            Toast.makeText(getApplicationContext(), R.string.warning_select_session, Toast.LENGTH_LONG).show();
            return super.onOptionsItemSelected(item);

        } else {

            switch (item.getItemId()) {
                case android.R.id.home:
                    onBackPressed();
                    return true;
                case R.id.send_session:
                    sendSessions(selectedPositions);
                    return true;
                case R.id.rename_session:
                    renameSessions(selectedPositions);
                    return true;
                case R.id.delete_session:
                    deleteSessions(selectedPositions);
                    return true;
                case R.id.launch_file_manager:
                    launchFileManager();
                    return true;
                case R.id.open_with_app:
                    openWithApp(selectedPositions);
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }
    }

    private void sendSessions(ArrayList<Integer> selectedPositions){
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.putExtra(Intent.EXTRA_SUBJECT, "EEG Sessions");
        intent.setType("text/csv");

        ArrayList<Uri> files = new ArrayList<Uri>();
        for(int position : selectedPositions){
            Uri uri = FileProvider.getUriForFile(
                    this,
                    "de.uos.ikw.eegdroid.provider",
                    arrayListOfFiles.get(position));
            files.add(uri);
        }
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
        startActivity(intent);
        adapter.resetSelectedPos();
    }

    private void renameSessions(ArrayList<Integer> selectedPositions){
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(this);
        View mView = layoutInflaterAndroid.inflate(R.layout.input_dialog_string, null);
        AlertDialog.Builder alertDialogBuilderUserInput = new AlertDialog.Builder(this);
        alertDialogBuilderUserInput.setView(mView);
        final EditText userInputDialogEditText = mView.findViewById(R.id.input_dialog_string_Input);

        alertDialogBuilderUserInput
                .setCancelable(false)
                .setTitle(R.string.rename_title)
                .setMessage(getResources().getString(R.string.ask_new_name) + " " + "selection" + ":")
                .setPositiveButton(R.string.rename, (dialogBox, id) -> {
                    int renameCount = 0;
                    for (int i = selectedPositions.size()-1; i > -1 ; i--) {
                        int position = selectedPositions.get(i);
                        String oldName = arrayListOfFiles.get(position).getName().substring(0, 15);
                        String numbering = (renameCount==0) ? "" : "_"+Integer.toString(renameCount);
                        File newName = new File(saveDir, oldName +
                                userInputDialogEditText.getText().toString() +
                                numbering + ".csv");
                        renameCount++;
                        //Check if exist another file with this name
                        if (arrayListOfFiles.contains(newName)) {
                            Toast.makeText(getApplicationContext(), R.string.warning_rename, Toast.LENGTH_LONG).show();
                            dialogBox.cancel();
                            adapter.resetSelectedPos();
                        } else {
                            arrayListOfFiles.get(position).renameTo(newName);
                            arrayListOfFiles.set(position, newName);
                            Collections.sort(arrayListOfFiles, Collections.reverseOrder());
                            //adapter.notifyItemChanged(position);
                            adapter.notifyDataSetChanged();
                            adapter.resetSelectedPos();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel,
                        (dialogBox, id) -> {
                            dialogBox.cancel();
                            adapter.resetSelectedPos();
                        });

        AlertDialog alertDialogAndroid = alertDialogBuilderUserInput.create();
        alertDialogAndroid.show();
    }

    private void deleteSessions(ArrayList<Integer> selectedPositions){
        //Handles the Dialog to confirm the file delete
        AlertDialog.Builder alert = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title)
                .setMessage(getResources().getString(R.string.confirmation_delete) + " " + "selection" + "?");
        alert.setPositiveButton(android.R.string.yes, (dialog, which) -> {

            for(int position:selectedPositions) {
                arrayListOfFiles.get(position).delete();
                arrayListOfFiles.remove(position);
                adapter.notifyItemRemoved(position);
                adapter.resetSelectedPos();
            }
        });
        alert.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
            // close dialog
            dialog.cancel();
            adapter.resetSelectedPos();
        });
        alert.show();
        adapter.resetSelectedPos();
    }

    private void launchFileManager(){
        // Construct an intent for opening a folder
        Uri selectedUri = Uri.parse(MainActivity.getDirSessions().toString());
        Log.d("ManageSessions: ", MainActivity.getDirSessions().toString());
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setDataAndType(selectedUri, "resource/folder");

        if (intent.resolveActivityInfo(getPackageManager(), 0) != null)
        {
            startActivity(intent);
        }
        else
        {
            Toast.makeText(getApplicationContext(), "No file explorer installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void openWithApp(ArrayList<Integer> selectedPositions){
        if (selectedPositions.size() != 1) {
            Toast.makeText(getApplicationContext(), "Can only open one file", Toast.LENGTH_SHORT).show();
            return;
        }
        int position = selectedPositions.get(0);
        File file = arrayListOfFiles.get(position);

        // Get URI and MIME type of file
        Uri uri = FileProvider.getUriForFile(this,
                "de.uos.ikw.eegdroid.provider",
                file);
        String mime = getContentResolver().getType(uri);

        // Open file with user selected app
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    public void createDirectory(File dir) {
        if (!dir.exists()) {
            dir.mkdirs(); // creates needed dirs
        }
    }

    //Returns a list of recordings in directory
    public void readDirectory(File dir) {
        arrayListOfFiles = new ArrayList<>(Arrays.asList(Objects.requireNonNull(dir.listFiles((d, name) -> name.toLowerCase().endsWith(".csv") || name.toLowerCase().endsWith(".xdf") ))));
        Collections.sort(arrayListOfFiles, Collections.reverseOrder());
        //Add if here?
    }

    //Returns a ListArray of the files in the directory
    public ArrayList<File> getArrayListOfFiles() {
        readDirectory(MainActivity.getDirSessions());
        Log.d("MANAGE: ", "Directory: " + MainActivity.getDirSessions());
        return arrayListOfFiles;
    }


}
