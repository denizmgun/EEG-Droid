package de.uni_osnabrueck.ikw.eegdroid;
// initial commits
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.opengl.Visibility;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.MPPointD;
import com.github.mikephil.charting.utils.MPPointF;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Array;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


public class Record extends AppCompatActivity {


    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String EXTRAS_DEVICE_MODEL = "DEVICE_MODEL"; // "2": old, "3": new
    private final static String TAG = Record.class.getSimpleName();
    private final Handler handler = new Handler();
    private final List<Float> timestamps = new ArrayList<>();
    private final List<Float> samplingTimes = new ArrayList<>();
    private final List<List<Float>> accumulatedSamples = new ArrayList<>();
    private final int plottingFPS = 25;
    private Float[] channelOffsets = new Float[24];
    private final int maxVisibleXRange = 8000;  // see 8s at the time on the plot
    private int leftAxisUpperLimit = 200000;
    private int leftAxisLowerLimit = -200000;
    private int leftAxisManualVScale = 1;
    private int leftAxisManualHScale = 1;
    private final ArrayList<Integer> pkgIDs = new ArrayList<>();
    private final int nChannels = 24;
    private float samplingRate = 500/3;  // alternative: 500, 500/2, 500/3, 500/4, etc.
    private final float traumschreiberWarmup = 500/3; // keep same as samplingRate for 1s.

    private final ArrayList<ArrayList<Entry>> plottingBuffer = new ArrayList<ArrayList<Entry>>() {
        {
            for (int i = 0; i < nChannels; i++) {
                ArrayList<Entry> lineEntries = new ArrayList<>();
                add(lineEntries);
            }
        }
    };
    private int plotMax = 0;
    private int plotMin = 0;
    private final String serviceUuid = "00000ee6-0000-1000-8000-00805f9b34fb";
    private final ArrayList<String> notifyingUUIDs = new ArrayList<String>() {
        {
            add("0000ee60-0000-1000-8000-00805f9b34fb");
            add("0000ee61-0000-1000-8000-00805f9b34fb");
            add("0000ee62-0000-1000-8000-00805f9b34fb");
        }
    };
    private final String configCharacteristicUuid = "0000ecc0-0000-1000-8000-00805f9b34fb";
    private final String codeCharacteristicUuid = "0000c0de-0000-1000-8000-00805f9b34fb";
    private ArrayList<BluetoothGattCharacteristic> notifyingCharacteristics = new ArrayList<>();
    private BluetoothGattCharacteristic configCharacteristic;
    private BluetoothGattCharacteristic codeCharacteristic;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private String selectedGain = "1";
    private byte selectedGainB;
    private int selectedGainPos = 0;
    private byte bitsPerChB = 0b00110000; //0b00xx0000
    private int selectedBitsPerChPos = 0; //10 bit
    private byte runningAverageFilterB;
    private boolean runningAverageFilterCheck = false;
    private byte sendOnOneCharB;
    private boolean sendOnOneCharCheck = false;
    private byte generateDataB;
    private boolean generateDataCheck = false;
    private byte transmissionRateB = 0b00000001; //0b00000001
    private int selectedTransmissionRatePos = 0; ///167hz
    private byte o1HighpassB;
    private int o1HighpassPos = 0;
    private byte iirHighpassB;
    private int  iirHighpassPos = 1;
    private byte lowpassB;
    private int lowpassPos = 1;
    private byte filter50hzB;
    private int filter50hzPos = 1;
    private byte bitshiftMinB;
    private int bitshiftMinPos = 0;
    private byte bitshiftMaxB;
    private int bitshiftMaxPos = 15;
    private byte encodingSafetyFactorB;
    private int encodingSafetyPos = 8;

    private final int[] channelColors = new int[nChannels];
    private final boolean[] channelsShown = new boolean[nChannels];
    private final CheckBox[] checkBoxes = new CheckBox[nChannels];
    private final TextView[] channelValueViews = new TextView[nChannels];
    private AlertDialog traumConfigDialog;
    LSL.StreamInfo streamInfo;
    LSL.StreamOutlet streamOutlet = null;
    private TextView mConnectionState;
    private TextView viewDeviceAddress;
    private boolean mNewDevice;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private TraumschreiberService mTraumService = new TraumschreiberService();

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

            // hack for ensuring a successful connection
            // constants
            int CONNECT_DELAY = 2000;
            handler.postDelayed(() -> mBluetoothLeService.connect(mDeviceAddress), CONNECT_DELAY);  // connect with a defined delay
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            if (mBluetoothLeService != null) mBluetoothLeService = null;
        }
    };

    private int selectedScale;
    private byte selectedScaleB = 0b00000000;
    private boolean recording = false;
    private boolean notifying = false;
    private float resolutionTime;
    private float resolutionFrequency;
    private int plottedPkgCount = 0;
    private int visiblyPlottedPkgs = 0;
    private int enabledCheckboxes = 0;
    private TextView mXAxis;
    private TextView mDataResolution;
    private LineChart mChart;
    private ImageButton imageButtonRecord;
    //private ImageButton imageButtonSave;
    //private ImageButton imageButtonDiscard;
    private androidx.appcompat.widget.SwitchCompat plotSwitch;
    private View layout_plots;
    private boolean plotting = true;
    private int plottingUpdateInterval= 30;
    private androidx.appcompat.widget.SwitchCompat channelViewsSwitch;
    private boolean channelViewsEnabled = true;
    
    private List<float[]> mainData;
    private int adaptiveEncodingFlag = 0; //Indicates whether adaptive encoding took place in this instant.
    private final ArrayList<Integer> adaptiveEncodingFlags = new ArrayList<>();
    private int signalBitShift = 0;
    private final ArrayList<Integer> signalBitShifts =  new ArrayList<>();
    private final View.OnClickListener imageDiscardOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mainData = new ArrayList<>();
            Toast.makeText(
                    getApplicationContext(),
                    "Your EEG session was discarded.",
                    Toast.LENGTH_LONG
            ).show();
            buttons_prerecording();
        }
    };
    private int pkgCount;
    private int  storedPkgCount;
    private String start_time;
    private String end_time;
    private long startTime;
    private String recording_time;
    private long start_timestamp;
    private long end_timestamp;
    private long plottingLastRefresh;
    private long pkgArrivalTime;
    
    private final View.OnClickListener imageRecordOnClickListener = v -> {
        if (!recording) {
            startRecording();
        } else {
            endRecording();

        }
    };
    private final View.OnClickListener imageSaveOnClickListener = v -> {
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(getApplicationContext());
        View mView = layoutInflaterAndroid.inflate(R.layout.input_dialog_string, null);
        AlertDialog.Builder alertDialogBuilderUserInput = new AlertDialog.Builder(Record.this);
        alertDialogBuilderUserInput.setView(mView);

        final EditText userInputLabel = mView.findViewById(R.id.input_dialog_string_Input);

        alertDialogBuilderUserInput
                .setCancelable(false)
                .setTitle(R.string.session_label_title)
                .setMessage(getResources().getString(R.string.enter_session_label))
                .setPositiveButton(R.string.save, (dialogBox, id) -> {
                    if (!userInputLabel.getText().toString().isEmpty()) {
                        try {
                            saveSession(userInputLabel.getText().toString());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            saveSession();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

        AlertDialog alertDialogAndroid = alertDialogBuilderUserInput.create();
        alertDialogAndroid.show();
        buttons_prerecording();
    };
    private final CompoundButton.OnCheckedChangeListener plotSwitchOnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (!isChecked) {
                plotting = false;
            } else {
                if (enabledCheckboxes != 0) {
                    plotting = true;
                    plottingLastRefresh = System.currentTimeMillis();
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Need to select a Channel first",
                            Toast.LENGTH_SHORT).show();
                    buttonView.setChecked(false);
                }
            }
        }
    };
    private final CompoundButton.OnCheckedChangeListener channelViewsSwitchListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            LinearLayout channelViewsContainer = findViewById(R.id.ChannelViewsContainer);
            if (isChecked) {
                channelViewsEnabled = true;
                channelViewsContainer.setVisibility(View.VISIBLE);
            }
            else  {
                channelViewsEnabled = false;
                channelViewsContainer.setVisibility(View.GONE);
            }
        }
    };
    private boolean deviceConnected = false;
    private boolean casting = false;
    private Menu menu;
    private List<List<Float>> recentlyDisplayedData;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private List<Float> microV;
    private CastThread caster;
    private Timer timer;
    private TimerTask timerTask;
    private boolean timerRunning = false;
    // Handles various events fired by the Service.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            // CONNECTION EVENT
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                deviceConnected = true;
                buttons_prerecording();
                setConnectionStatus(true);
                storedPkgCount = 0;
            // DISCONNECTION EVENT
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                deviceConnected = false;
                setConnectionStatus(false);
                clearUI();
                disableCheckboxes();
                if (timer != null) {
                    timer.cancel();
                    timer.purge();
                }
                timerRunning = false;

            // BLUETOOTH SERVICE REGISTRATION
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                BluetoothGattService bleService = mBluetoothLeService.getService(TraumschreiberService.serviceUUID);
                mNotifyCharacteristic = bleService.getCharacteristic(TraumschreiberService.notifyingUUID);
                codeCharacteristic = bleService.getCharacteristic(TraumschreiberService.codeUUID);
                configCharacteristic = bleService.getCharacteristic(TraumschreiberService.configUUID);
                mBluetoothLeService.setCharacteristicNotification(codeCharacteristic, true);
                waitForBluetoothCallback(mBluetoothLeService);
              
            // HANDLE INCOMING STREAM
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                int[] data = intent.getIntArrayExtra(BluetoothLeService.EXTRA_DATA);

                // CONFIG DATA
                if (data.length == 8) {
                    int[] configData = intent.getIntArrayExtra(BluetoothLeService.EXTRA_DATA);
                    Toast.makeText(getApplicationContext(),"Received Config Data", Toast.LENGTH_SHORT).show();
                    displayReceivedTraumConfigValues(configData);
                    return;
                }

                if(!notifying) return;

                // ENCODING UPDATES
                if (data[0] == 0xC0DE){
                    signalBitShift = data[1];
                    //pkgLossCount = data[2];
                    //Log.v(TAG,"Updated signalBitshift of CH1: " + signalBitShift);
                    // Give the next row in our recording an adaptive encoding flag
                    adaptiveEncodingFlag = 1;
                    return; //prevent further processing
                }

                // no processing (for testing bluetooth transmission rates)
                if(data==null) return;

                // CHANNEL DATA
                pkgCount++;
                if (!timerRunning) startTimer();


                // parse header, if there is a header
                if (data.length > nChannels){
                    currentPkgId = data[0];
                    currentPkgLoss = data[1];
                    currentBtPkgLoss = calculateBtPkgLoss();
                    data = Arrays.copyOfRange(data, 2, data.length);
                }
                microV = convertToMicroV(data);
                //streamData(microV);
                if (channelViewsEnabled && pkgCount % 100 == 0) displayNumerical(microV);
                if (plotting) storeForPlotting(microV);
                if (recording) storeData(microV);
            }
        }
    };


    private List<Integer> pkgsLost = new ArrayList<>();
    private int currentPkgLoss = 0;
    private int currentPkgId  = 0;
    private int lastPkgId = 0;
    private int currentBtPkgLoss = 0;
    private Thread plottingThread;
    private File recordingFile;
    private String tempFileName;
    private FileWriter fileWriter;
    final String delimiter = ",";
    final String lineBreak = "\n";


    public Record() {
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.EXTRA_DATA);
        return intentFilter;
    }

    private int calculateBtPkgLoss(){
        int btloss = 0;
        if (storedPkgCount>0){
            if (lastPkgId > currentPkgId){
                btloss = (15 - lastPkgId) + currentPkgId;
            } else{
                btloss = (currentPkgId-1) - lastPkgId;
            }
        }
        lastPkgId = currentPkgId;
        return btloss;
    }

    private void updateConfiguration() {
        // Declare bytearray
        byte[] configBytes = new byte[8];

        // Concatenate binary strings
        configBytes[0] = (byte) (selectedGainB| bitsPerChB | runningAverageFilterB | sendOnOneCharB | generateDataB | transmissionRateB);
        configBytes[1] = (byte) 0; // Reserved
        configBytes[2] = (byte) (o1HighpassB | iirHighpassB);
        configBytes[3] = (byte) (lowpassB | filter50hzB);
        configBytes[4] = (byte) (bitshiftMinB | bitshiftMaxB);
        configBytes[5] = (byte) encodingSafetyFactorB;
        configBytes[6] = (byte) 0; // battery status
        configBytes[7] = (byte) 0; // battery status

        Log.d(TAG, "configBytes before writing: " + Arrays.toString(configBytes));
        configCharacteristic.setValue(configBytes);

        boolean togglingRequired = notifying;


        if (togglingRequired) toggleNotifying();
        mBluetoothLeService.writeCharacteristic(configCharacteristic);
        if (togglingRequired) toggleNotifying();

        Log.d(TAG, "New Value of Config: " + Arrays.toString(configCharacteristic.getValue()));

        Toast.makeText(getApplicationContext(), "Succesfully applied configuration.", Toast.LENGTH_SHORT).show();
    }


    private void startTimer() {
        //set a new Timer
        timer = new Timer();
        //initialize the TimerTask's job
        initializeTimerTask();
        // timerTask will be executed every 5000 ms
        timer.schedule(timerTask, 5000, 5000);
        mDataResolution.setText("calculating");
        timerRunning = true;
    }

    private void endTimer(){
        // End the Timer Task
        mDataResolution.setTextColor((int) R.color.defaultTextView);
        mDataResolution.setText(R.string.default_resolution_text);
        if (timerRunning) {
            timerTask.cancel();
            timerRunning = false;
        }
        pkgCount = 0;
    }
    private void initializeTimerTask() {

        timerTask = new TimerTask() {
            public void run() {
                handler.post(() -> {

                    if (!notifying) {
                        return;
                    }

                    if (pkgCount > 0) {
                        resolutionTime = (float) 5000/pkgCount;    // ms per package
                        resolutionFrequency = pkgCount / 5;  // packages per second
                    }
                    String hertz = (int) resolutionFrequency + "Hz";


                    @SuppressLint("DefaultLocale") String resolution = String.format("%.2f", resolutionTime) + "ms - ";
                    String content = resolution + hertz;

                    // Just here for stability in case I change the code for the transmissionRate selector
                    //if(transmissionRateB == (byte) 1) samplingRate = 500/2;
                    //else samplingRate = 500/3;

                    int color;
                    if (resolutionFrequency >= samplingRate) color = getResources().getColor(R.color.green);
                    else if (resolutionFrequency >= samplingRate - 2) color = getResources().getColor(R.color.orange);
                    else {
                        content += "  Bad Signal";
                        color = getResources().getColor(R.color.red);
                    }

                    if (pkgCount != 0) {
                        mDataResolution.setText(content);
                        mDataResolution.setTextColor(color);
                    }

                    pkgCount = 0;
                });
            }
        };
    }



    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        setContentView(R.layout.activity_record);

        // LSL stuff
        final UUID uid = UUID.randomUUID();
        try {
            streamInfo = new LSL.StreamInfo("Traumschreiber-EEG", "Markers", 24, LSL.IRREGULAR_RATE, LSL.ChannelFormat.float32, uid.toString());
        } catch (Error ex){
            Log.e(TAG, " LSL issue: " + ex.getMessage());
        }
        try {
            streamOutlet = new LSL.StreamOutlet(streamInfo);
        } catch (IOException ex) {
            Log.d("LSL issue:", Objects.requireNonNull(ex.getMessage()));
            return;
        }

        imageButtonRecord = findViewById(R.id.imageButtonRecord);
        //imageButtonSave = findViewById(R.id.imageButtonSave);
        //imageButtonDiscard = findViewById(R.id.imageButtonDiscard);
        plotSwitch = findViewById(R.id.switch_plots);
        channelViewsSwitch = findViewById(R.id.channel_views_switch);

        layout_plots = findViewById(R.id.linearLayout_chart);
        layout_plots.setVisibility(ViewStub.VISIBLE);

        mXAxis = findViewById(R.id.XAxis_title);
        mXAxis.setVisibility(ViewStub.VISIBLE);
        imageButtonRecord.setOnClickListener(imageRecordOnClickListener);
        //imageButtonSave.setOnClickListener(imageSaveOnClickListener);
        //imageButtonDiscard.setOnClickListener(imageDiscardOnClickListener);
        plotSwitch.setOnCheckedChangeListener(plotSwitchOnCheckedChangeListener);
        channelViewsSwitch.setOnCheckedChangeListener(channelViewsSwitchListener);

        // Sets up UI references.
        mConnectionState = findViewById(R.id.connection_state);
        viewDeviceAddress = findViewById(R.id.device_address);
        mConnectionState = findViewById(R.id.connection_state);
        mDataResolution = findViewById(R.id.resolution_value);

        // Checkboxes and Channel Values
        LinearLayout[] checkBoxRows = new LinearLayout[3];
        checkBoxRows[0] = findViewById(R.id.checkBoxRow1);
        checkBoxRows[1] = findViewById(R.id.checkBoxRow2);
        checkBoxRows[2] = findViewById(R.id.checkBoxRow3);

        LinearLayout[] channelValueRows = new LinearLayout[3];
        channelValueRows[0] = findViewById(R.id.channelValueRow1);
        channelValueRows[1] = findViewById(R.id.channelValueRow2);
        channelValueRows[2] = findViewById(R.id.channelValueRow3);

        getChannelColors(); // fills int[] channelColors with values
        for (int i = 0; i < nChannels; i++) {

            channelValueViews[i] = createChannelValueView(i);
            channelValueRows[i / 8].addView(channelValueViews[i]);

            checkBoxes[i] = createPlottingCheckbox(i);
            checkBoxRows[i / 8].addView(checkBoxes[i]);
        }
        if(plotting) checkBoxes[0].setChecked(true);
        
        // Traumschreiber Config Dialog
        traumConfigDialog = createTraumConfigDialog();

        createChart();
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        //Remove temporary files
        deleteTempFiles();
    }

    private TextView createChannelValueView (int i){
        // Create View for Channel Value
        TextView channelValueView = new TextView(getApplicationContext());
        LinearLayout.LayoutParams valueLayout = new LinearLayout.LayoutParams(15, -1, 1f);
        //valueLayout.width = 6;
        //valueLayout.height = ViewGroup.LayoutParams.MATCH_PARENT;
        //valueLayout.weight = 1;
        valueLayout.topMargin = 2;
        channelValueView.setLayoutParams(valueLayout);
        channelValueView.setTextAlignment(RelativeLayout.TEXT_ALIGNMENT_VIEW_END);
        channelValueView.setText("0μV");
        channelValueView.setTextColor(channelColors[i]);
        channelValueView.setTextSize(11);
        // channelValueView.setGravity(0);
        return channelValueView;
    }
    private CheckBox createPlottingCheckbox (int i) {
        // Create Checkbox for displaying channel
        CheckBox box = new CheckBox(getApplicationContext());
        LinearLayout.LayoutParams boxLayout = new LinearLayout.LayoutParams(15, -2, 1f);
        //boxLayout.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        //boxLayout.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        //boxLayout.weight = 1;
        box.setLayoutParams(boxLayout);
        box.setText(Integer.toString(i + 1));
        box.setTextSize(8);
        box.setTextColor(channelColors[i]);
        box.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int channelId = Integer.parseInt(buttonView.getText().toString()) - 1;
            if (isChecked) {
                enabledCheckboxes++;
                if (enabledCheckboxes <= 8) {
                    channelsShown[channelId] = true;
                } else {
                    Toast.makeText(
                            getApplicationContext(),
                            "Can't plot more than 8 channels simultaneously.",
                            Toast.LENGTH_LONG
                    ).show();
                    box.setChecked(false);
                    enabledCheckboxes--;
                }
            }
            if (!isChecked) {
                if (!plotting | enabledCheckboxes > 1) {
                    enabledCheckboxes--;
                    channelsShown[channelId] = false;
                } else {
                    Toast.makeText(
                            getApplicationContext(),
                            "Need to plot at least one channel",
                            Toast.LENGTH_SHORT
                    ).show();
                    buttonView.setChecked(true); //Check this box again
                }
            }
        });
        return box;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unbindService(mServiceConnection);
        } catch(Exception e) {
            Log.w(TAG, e.toString());
        }
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.bluetooth_connect, menu);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        int id = item.getItemId();
        if (id == R.id.scan) {
            if (!deviceConnected) {
                Intent intent = new Intent(this, DeviceScanActivity.class);
                startActivityForResult(intent, 1200);
            } else {
                //Handles the Dialog to confirm the closing of the activity
                AlertDialog.Builder alert = new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_title)
                        .setMessage(getResources().getString(R.string.confirmation_disconnect));
                alert.setPositiveButton(android.R.string.yes, (dialog, which) -> mBluetoothLeService.disconnect());
                alert.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    // close dialog
                    dialog.cancel();
                });
                alert.show();
            }
            return true;
        }

        if (id == android.R.id.home) {
            if (recording) {
                //Handles the Dialog to confirm the closing of the activity
                AlertDialog.Builder alert = new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_title)
                        .setMessage(getResources().getString(R.string.confirmation_close_record));
                alert.setPositiveButton(android.R.string.yes, (dialog, which) -> onBackPressed());
                alert.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    // close dialog
                    dialog.cancel();
                });
                alert.show();
            } else {
                onBackPressed();
            }
            return true;
        }

        if (id == R.id.notify) toggleNotifying();

        if (id == R.id.cast) {
            MenuItem menuItemCast = menu.findItem(R.id.cast);
            if (!casting) {
                casting = true;
                caster = new CastThread();
                caster.start();
                menuItemCast.setIcon(R.drawable.ic_cast_blue_24dp);
            } else {
                casting = false;
                caster.staph();
                menuItemCast.setIcon(R.drawable.ic_cast_white_24dp);
            }
        }

        if (id==R.id.centering) {
            Toast.makeText(getApplicationContext(),
                    "Centering Signal around 0 in 2 seconds",
                    Toast.LENGTH_LONG).show();
            mTraumService.initiateCentering();
        }

        if (id==R.id.traumConfig) showTraumConfigDialog();

        return super.onOptionsItemSelected(item);
    }

    public void toggleNotifying() {
        MenuItem menuItemNotify = menu.findItem(R.id.notify);
        //menuItemNotify.setEnabled(false);
        waitForBluetoothCallback(mBluetoothLeService);

        if (!notifying) {
            Log.d(TAG, "Notifications Button pressed: ENABLED");
            notifying = true;
            mTraumService.warmUp();
            mDataResolution.setText("warming up");
            mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, true);
            menuItemNotify.setIcon(R.drawable.ic_notifications_active_blue_24dp);

        } else {
            Log.d(TAG, "Notifications Button pressed: DISABLED");
            notifying = false;
            mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, false);
            endTimer();
            menuItemNotify.setIcon(R.drawable.ic_notifications_off_white_24dp);

        }
        if(codeCharacteristic!=null) logDescriptorValue(codeCharacteristic);
        menuItemNotify.setEnabled(true);
    }

    public void logDescriptorValue(BluetoothGattCharacteristic c){
        String cId = c.getUuid().toString().substring(4,8);
        BluetoothGattDescriptor descriptor = c.getDescriptor(
                UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        byte[] descVal = descriptor.getValue();
        String descValS = (descVal==BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) ? "NOTIFICATIONS DISABLED" : "NOTIFICATIONS ENABLED";
        Log.d(TAG, "Characteristic " + cId + " DESCRIPTOR VALUE: " + descValS);
    }

    private AlertDialog createTraumConfigDialog(){
        // inflate the view
        View traumConfigView = getLayoutInflater().inflate(R.layout.traum_config_popup, null);
        // create the dialog
        AlertDialog.Builder configDialogBuilder = new AlertDialog.Builder(this,
                R.style.traumConfigDialog);
        configDialogBuilder.setView(traumConfigView);
        return configDialogBuilder.create();
    }

    private void showTraumConfigDialog(){

        traumConfigDialog.show();
        // Link the Items to Functions

        // Link Close Button
        View closeConfig = (View) traumConfigDialog.findViewById(R.id.traum_config_close_button);
        closeConfig.setOnClickListener(v -> {traumConfigDialog.cancel();});

        // Link gainSpinner
        Spinner gainSpinner = (Spinner) traumConfigDialog.findViewById(R.id.gain_spinner);
        gainSpinner.setSelection(selectedGainPos);
        gainSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //Itempositions: {0,1,2,3} <=> {00,01,10,11} -- (<<6) --> {0b00..,0b01..,0b10..
                selectedGainPos = position;
                selectedGainB = (byte) ((position& 0x3) << 6);
                byte[] binaryString = {selectedGainB};
                Log.d(TAG, "Binary rep of selected value: " + Arrays.toString(binaryString));
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // nothing
            }
        });

        // Link bitsPerChSpinner
        Spinner bitsPerChSpinner = (Spinner) traumConfigDialog.findViewById(R.id.bits_per_ch_spinner);
        bitsPerChSpinner.setSelection(selectedBitsPerChPos);
        bitsPerChSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //Itempositions: {0,1,2} <=> {00,01,10,11} -- (<<4) --> {0b0000,0b0001..,0b0010
                selectedBitsPerChPos = position;
                bitsPerChB = (byte) ((position& 0x3) << 4);

                int bitsPerCh;
                Log.d(TAG, "Position of BitsPerCh: " + position);
                switch(position){
                    case 0:
                        bitsPerCh=10;
                        break;
                    case 1:
                        bitsPerCh=14;
                        break;
                    case 2:
                        bitsPerCh=16;
                        break;
                    case 3:
                        position = 0;  // 3 and 0 have same effect, later we set NotifyingUUID, needs to be 0 instead of 3
                        bitsPerCh=10;
                        break;

                    default:
                        throw new IllegalStateException("Unexpected value: " + position);
                }
                TraumschreiberService.bitsPerCh = bitsPerCh;

                // Turn notifications off.
                if(notifying) toggleNotifying();
                
                //Update Characteristic on Which data is sent
                TraumschreiberService.setNotifyingUUID(position); //0, 1 or 2
                BluetoothGattService bleService = mBluetoothLeService.getService(TraumschreiberService.serviceUUID);
                mNotifyCharacteristic = bleService.getCharacteristic(TraumschreiberService.notifyingUUID);

                byte[] binaryString = {bitsPerChB};
                Log.d(TAG, "Binary rep of selected value for bits per CH:  " + Arrays.toString(binaryString));
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // nothing
            }
        });

        // Link RunningAverageSwitch
        SwitchCompat rafSwitch = (SwitchCompat) traumConfigDialog.findViewById(R.id.average_filter_switch);
        rafSwitch.setChecked(runningAverageFilterCheck);
        rafSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    runningAverageFilterCheck = true;
                    runningAverageFilterB = 1 << 3;
                } else {
                    runningAverageFilterCheck = false;
                    runningAverageFilterB = 0;
                }
            }
        });

        // Link oneCharacteristicSwitch
        SwitchCompat oneCharSwitch = (SwitchCompat) traumConfigDialog.findViewById(R.id.one_char_switch);
        oneCharSwitch.setChecked(sendOnOneCharCheck);
        oneCharSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    sendOnOneCharCheck = true;
                    sendOnOneCharB = 1 << 2;
                } else {
                    sendOnOneCharCheck = false;
                    sendOnOneCharB = 0;
                }
            }
        });

        // Link GenerateDataSwitch
        SwitchCompat genDataSwitch = (SwitchCompat) traumConfigDialog.findViewById(R.id.generate_data_switch);
        genDataSwitch.setChecked(generateDataCheck);
        genDataSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b) {
                    generateDataCheck = true;
                    generateDataB = 0b10;
                } else {
                    generateDataCheck = false;
                    generateDataB = 0;
                }
            }
        });

        // Link transmissionRateSpinner
        Spinner transmissionRateSpinner = (Spinner) traumConfigDialog.findViewById(R.id.transmission_rate_spinner);
        transmissionRateSpinner.setSelection(selectedTransmissionRatePos);
        transmissionRateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //Itempositions: {0,1,2,3} <=> {00,01,10,11} -- (<<4) --> {0b0000,0b0001..,0b0010..
                selectedTransmissionRatePos = position;
                transmissionRateB = (byte) (position); // its 0 or 1 anyways

                // Small Extra for UI
                if(transmissionRateB == (byte) 1) samplingRate = 500/2;
                else samplingRate = 500/3;

                byte[] binaryString = {transmissionRateB};
                Log.d(TAG, "Binary rep of selected value: " + Arrays.toString(binaryString));
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // nothing
            }
        });

        // Link o1HighpassSpinner
        Spinner o1HighpassSpinner = (Spinner) traumConfigDialog.findViewById(R.id.o1_highpass_spinner);
        o1HighpassSpinner.setSelection(o1HighpassPos);
        o1HighpassSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                o1HighpassPos = position;
                o1HighpassB = (byte) ((position & 0xff) << 4);

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // well Nothing
            }

        });

        // Link IIRSpinner
        Spinner IIRSpinner = (Spinner) traumConfigDialog.findViewById(R.id.iir_highpass_spinner);
        IIRSpinner.setSelection(iirHighpassPos);
        IIRSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                iirHighpassPos = position;
                iirHighpassB = (byte) (position & 0xff);

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // well Nothing
            }

        });

        // Link LowPass
        Spinner lowPassSpinner = (Spinner) traumConfigDialog.findViewById(R.id.lowpass_spinner);
        lowPassSpinner.setSelection(lowpassPos);
        lowPassSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                lowpassPos = position;
                lowpassB = (byte) ((position & 0xff) << 4);

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // well Nothing
            }

        });

        // Link 50hz
        Spinner filter50hzSpinner = (Spinner) traumConfigDialog.findViewById(R.id.filter50hz_spinner);
        filter50hzSpinner.setSelection(filter50hzPos);
        filter50hzSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                filter50hzPos = position;
                filter50hzB = (byte) (position & 0xff);

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // well Nothing
            }

        });

        // Link bitshift min
        Spinner bitshiftMinSpinner = (Spinner) traumConfigDialog.findViewById(R.id.bitshift_min_spinner);
        bitshiftMinSpinner.setSelection(bitshiftMinPos);
        bitshiftMinSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                bitshiftMinPos = position;
                bitshiftMinB = (byte)((position & 0xff) <<4);

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // well Nothing
            }

        });

        // Link bitshift max
        Spinner bitshiftMaxSpinner = (Spinner) traumConfigDialog.findViewById(R.id.bitshift_max_spinner);
        bitshiftMaxSpinner.setSelection(bitshiftMaxPos);
        bitshiftMaxSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                bitshiftMaxPos = position;
                bitshiftMaxB = (byte)(position & 0xff);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // well Nothing
            }

        });

        // Links safeEncodingFactor
        Spinner encodingSafetySpinner = (Spinner) traumConfigDialog.findViewById(R.id.encoding_safety_spinner);
        encodingSafetySpinner.setSelection(encodingSafetyPos);
        encodingSafetySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                encodingSafetyPos = position;
                encodingSafetyFactorB = (byte) ((position & 0xff) << 4);

                Log.i(TAG, "Selected Encoding Safety Factor : " + encodingSafetyFactorB);

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // well Nothing
            }

        });

        // Link UpdateConfigButton
        Button updateConfigButton = (Button) traumConfigDialog.findViewById(R.id.apply_config_button);
        updateConfigButton.setOnClickListener(v -> {
            if(deviceConnected) updateConfiguration();
            else Toast.makeText(getApplicationContext(), "No Device Connected", Toast.LENGTH_SHORT).show();
        });

        // Link readConfigButton
        Button readConfigButton = (Button) traumConfigDialog.findViewById(R.id.read_config_button);
        readConfigButton.setOnClickListener(v -> {
            if(deviceConnected) mBluetoothLeService.readCharacteristic(configCharacteristic, true);
            else Toast.makeText(getApplicationContext(), "No Device Connected", Toast.LENGTH_SHORT).show();
        });

        // Link ResetonfigButton
        Button resetConfigButton = (Button) traumConfigDialog.findViewById(R.id.reset_config_button);
        resetConfigButton.setOnClickListener(v -> {
            resetTraumConfig();
            Toast.makeText(getApplicationContext(), "Selected Default Values", Toast.LENGTH_SHORT).show();
        });

    }

    private void displayReceivedTraumConfigValues(int[] configData){
        selectedGainPos = (configData[0] & 0xff) >> 6; // 0bxx00 0000
        selectedBitsPerChPos = (configData[0] & 0x30) >> 4; // 0b00xx 0000
        runningAverageFilterCheck = (configData[0] & 0x8) > 1; //0b0000 x000
        sendOnOneCharCheck = (configData[0] & 0x4) > 1; // 0b0000 0x00
        generateDataCheck = (configData[0] & 0x2) > 1; // 0b0000 00x0
        selectedTransmissionRatePos = (configData[0] & 0x03); // 0b0000 000x
        o1HighpassPos = (configData[2]&0xff) >> 4;
        iirHighpassPos = (configData[2]&0x0f);
        lowpassPos = (configData[3]&0xff) >> 4;
        filter50hzPos = (configData[3]&0x0f);
        bitshiftMinPos = (configData[4]&0xff) >> 4;
        bitshiftMaxPos= (configData[4]&0x0f);
        encodingSafetyPos = (configData[5]&0xff) >> 4;
        int batteryValue = ((configData[6]&0xff) << 8) + configData[7] & 0xff;
        Log.d(TAG, "Battery Value: " + batteryValue);

        Spinner gainSpinner = (Spinner) traumConfigDialog.findViewById(R.id.gain_spinner);
        gainSpinner.setSelection(selectedGainPos);
        Spinner bitsPerChSpinner = (Spinner) traumConfigDialog.findViewById(R.id.bits_per_ch_spinner);
        bitsPerChSpinner.setSelection(selectedBitsPerChPos);
        SwitchCompat rafSwitch = (SwitchCompat) traumConfigDialog.findViewById(R.id.average_filter_switch);
        rafSwitch.setChecked(runningAverageFilterCheck);
        SwitchCompat oneCharSwitch = (SwitchCompat) traumConfigDialog.findViewById(R.id.one_char_switch);
        oneCharSwitch.setChecked(sendOnOneCharCheck);
        SwitchCompat genDataSwitch = (SwitchCompat) traumConfigDialog.findViewById(R.id.generate_data_switch);
        genDataSwitch.setChecked(generateDataCheck);
        Spinner transmissionRateSpinner = (Spinner) traumConfigDialog.findViewById(R.id.transmission_rate_spinner);
        transmissionRateSpinner.setSelection(selectedTransmissionRatePos);
        Spinner o1HighpassSpinner = (Spinner) traumConfigDialog.findViewById(R.id.o1_highpass_spinner);
        o1HighpassSpinner.setSelection(o1HighpassPos);
        Spinner IIRSpinner = (Spinner) traumConfigDialog.findViewById(R.id.iir_highpass_spinner);
        IIRSpinner.setSelection(iirHighpassPos);
        Spinner lowPassSpinner = (Spinner) traumConfigDialog.findViewById(R.id.lowpass_spinner);
        lowPassSpinner.setSelection(lowpassPos);
        Spinner filter50hzSpinner = (Spinner) traumConfigDialog.findViewById(R.id.filter50hz_spinner);
        filter50hzSpinner.setSelection(filter50hzPos);
        Spinner bitshiftMinSpinner = (Spinner) traumConfigDialog.findViewById(R.id.bitshift_min_spinner);
        bitshiftMinSpinner.setSelection(bitshiftMinPos);
        Spinner bitshiftMaxSpinner = (Spinner) traumConfigDialog.findViewById(R.id.bitshift_max_spinner);
        bitshiftMaxSpinner.setSelection(bitshiftMaxPos);
        Spinner encodingSafetySpinner = (Spinner) traumConfigDialog.findViewById(R.id.encoding_safety_spinner);
        encodingSafetySpinner.setSelection(encodingSafetyPos);
    }

    private void resetTraumConfig(){
        selectedGainPos = 0;
        selectedBitsPerChPos = 2; //16 bit
        runningAverageFilterCheck = true;
        sendOnOneCharCheck = false;
        generateDataCheck = false;
        selectedTransmissionRatePos = 1; //250Hz
        o1HighpassPos = 0;
        iirHighpassPos = 1;
        lowpassPos = 1;
        filter50hzPos = 1;
        bitshiftMinPos = 0;
        bitshiftMaxPos = 15;
        encodingSafetyPos = 8;

        Spinner gainSpinner = (Spinner) traumConfigDialog.findViewById(R.id.gain_spinner);
        gainSpinner.setSelection(selectedGainPos);
        Spinner bitsPerChSpinner = (Spinner) traumConfigDialog.findViewById(R.id.bits_per_ch_spinner);
        bitsPerChSpinner.setSelection(selectedBitsPerChPos);
        SwitchCompat rafSwitch = (SwitchCompat) traumConfigDialog.findViewById(R.id.average_filter_switch);
        rafSwitch.setChecked(runningAverageFilterCheck);
        SwitchCompat oneCharSwitch = (SwitchCompat) traumConfigDialog.findViewById(R.id.one_char_switch);
        oneCharSwitch.setChecked(sendOnOneCharCheck);
        SwitchCompat genDataSwitch = (SwitchCompat) traumConfigDialog.findViewById(R.id.generate_data_switch);
        genDataSwitch.setChecked(generateDataCheck);
        Spinner transmissionRateSpinner = (Spinner) traumConfigDialog.findViewById(R.id.transmission_rate_spinner);
        transmissionRateSpinner.setSelection(selectedTransmissionRatePos);
        Spinner o1HighpassSpinner = (Spinner) traumConfigDialog.findViewById(R.id.o1_highpass_spinner);
        o1HighpassSpinner.setSelection(o1HighpassPos);
        Spinner IIRSpinner = (Spinner) traumConfigDialog.findViewById(R.id.iir_highpass_spinner);
        IIRSpinner.setSelection(iirHighpassPos);
        Spinner lowPassSpinner = (Spinner) traumConfigDialog.findViewById(R.id.lowpass_spinner);
        lowPassSpinner.setSelection(lowpassPos);
        Spinner filter50hzSpinner = (Spinner) traumConfigDialog.findViewById(R.id.filter50hz_spinner);
        filter50hzSpinner.setSelection(filter50hzPos);
        Spinner bitshiftMinSpinner = (Spinner) traumConfigDialog.findViewById(R.id.bitshift_min_spinner);
        bitshiftMinSpinner.setSelection(bitshiftMinPos);
        Spinner bitshiftMaxSpinner = (Spinner) traumConfigDialog.findViewById(R.id.bitshift_max_spinner);
        bitshiftMaxSpinner.setSelection(bitshiftMaxPos);
        Spinner encodingSafetySpinner = (Spinner) traumConfigDialog.findViewById(R.id.encoding_safety_spinner);
        encodingSafetySpinner.setSelection(encodingSafetyPos);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // Check which request we're responding to
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == 1200) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected
                String mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
                mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
                String model = intent.getStringExtra(EXTRAS_DEVICE_MODEL);
                if (model != null) mNewDevice = model.equals("3");
                Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
                bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
            }
        }
    }
    private void prepareNotifications() {
        // set notifications of all notifyingCharacteristics except the one used for toggling.
        mBluetoothLeService.setNewTraumschreiber(mNewDevice);

        if (codeCharacteristic!=null) {
            waitForBluetoothCallback(mBluetoothLeService);
            mBluetoothLeService.setCharacteristicNotification(codeCharacteristic, true);
        }
        for (BluetoothGattCharacteristic characteristic : notifyingCharacteristics) {
            waitForBluetoothCallback(mBluetoothLeService);
            if (characteristic != mNotifyCharacteristic) {
                mBluetoothLeService.setCharacteristicNotification(characteristic, true);
            }
        }

        waitForBluetoothCallback(mBluetoothLeService);
        mBluetoothLeService.requestMtu(45);

    }
    private void waitForBluetoothCallback(BluetoothLeService service){
        while (service.isBusy) {
            Handler handler = new Handler();
            handler.postDelayed(() -> Log.v(TAG, "Waiting for bluetooth operation to finish."), 300);
        }

    }

    private void clearUI() {
        for (TextView view : channelValueViews) view.setText("0μV");
        mDataResolution.setText(R.string.default_resolution_text);
        pkgCount = 0;
    }

    private void enableCheckboxes(int n) {
        for (int i = 0; i < n; i++) checkBoxes[i].setEnabled(true);
    }

    private void disableCheckboxes() {
        for (CheckBox box : checkBoxes) box.setEnabled(false);
    }

    /* This is the last processing step before the data is displayed and saved
     Note that gain is 1 by default */
    private List<Float> convertToMicroV(int[] data) {
        // Conversion formula (old): V_in = X * 1.65V / (1000 * GAIN * PRECISION)
        // Conversion formula (new): V_in = X * (298 / (1000 * gain))

        float gain = Float.parseFloat(selectedGain); // = 1 by default
        List<Float> data_trans = new ArrayList<>();
        for (float datapoint : data) data_trans.add(datapoint * 5/4 * 298/(1000*gain));
        return data_trans;
    }

    @SuppressLint("DefaultLocale")
    private void displayNumerical(List<Float> signalMicroV) {
        if (signalMicroV != null) {
            for (int i = 0; i < nChannels; i++) {
                String channelValueS = "";
                float channelValueF = signalMicroV.get(i);
                //if(signalMicroV.get(i) > 0) value += "+";
                if (channelValueF >= 1000 | channelValueF <= -1000) {
                    channelValueF = channelValueF / 1000;
                    channelValueS += String.format("%.1f", channelValueF);
                    channelValueS += "mV";
                } else {
                    channelValueS += String.format("%.0f", channelValueF);
                    channelValueS += "μV";
                }
                channelValueViews[i].setText(channelValueS);
            }
        }
    }

    private void streamData(List<Float> data_microV) {
        float[] sample = new float[24];

        for (int i = 0; i < data_microV.size(); i++) {
            sample[i] = data_microV.get(i);
        }
        streamOutlet.push_sample(sample);
        Log.v("LSL", "Sample sent!");
        try {
            streamOutlet.push_sample(sample);
        } catch (Exception ex) {
            Log.d("LSL issue", Objects.requireNonNull(ex.getMessage()));
            streamOutlet.close();
            streamInfo.destroy();
        }
    }


    private void createChart() {
        OnChartValueSelectedListener ol = new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry entry, Highlight h) {
            }
            @Override
            public void onNothingSelected() {
            }
        };

        mChart = findViewById(R.id.layout_chart);
        mChart.setOnChartValueSelectedListener(ol);
        // enable description text
        mChart.getDescription().setEnabled(false);
        // enable touch gestures
        mChart.setTouchEnabled(true);
        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(true);
        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(false);
        // disable automatic resetting
        //mChart.setViewPortOffsets(0f,0f,0f,0f);
        // set an alternative background color
        LineData data = new LineData();
        data.setValueTextColor(Color.BLACK);
        data.setDrawValues(true);
        // add empty data
        mChart.setData(data);
        // get the legend (only possible after setting data)
        Legend l1 = mChart.getLegend();
        // modify the legend ...
        l1.setForm(Legend.LegendForm.LINE);
        l1.setTextColor(Color.BLACK);
        // set the y left axis
        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTextColor(Color.GRAY);
        leftAxis.setAxisMaximum(leftAxisUpperLimit);
        leftAxis.setAxisMinimum(leftAxisLowerLimit);
        leftAxis.setLabelCount(13, true); // from -35 to 35, a label each 5 microV
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.WHITE);
        // disable the y right axis
        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);
        // set the x bottom axis
        XAxis bottomAxis = mChart.getXAxis();
        bottomAxis.setLabelCount(5, true);
        bottomAxis.setValueFormatter(new MyXAxisValueFormatter());
        bottomAxis.setPosition(XAxis.XAxisPosition.TOP);
        bottomAxis.setGridColor(Color.WHITE);
        bottomAxis.setTextColor(Color.GRAY);
    }

    private LineDataSet createPlottableSet(int channelId) {

        LineDataSet set = new LineDataSet(plottingBuffer.get(channelId), String.format("Ch-%d", channelId + 1));
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(channelColors[channelId]);
        set.setDrawCircles(false);
        set.setLineWidth(1f);
        set.setValueTextColor(channelColors[channelId]);
        set.setVisible(channelsShown[channelId]);
        return set;
    }

    private void storeForPlotting(final List<Float> microV) {

        //Track lost packages to keep the chart timing accurate
        Float[] lostSample = new Float[24];
        Arrays.fill(lostSample, Float.NaN);
        List<Float> lostSampleL = Arrays.asList(lostSample);
        for (int i=0; i<currentBtPkgLoss;i++) accumulatedSamples.add(lostSampleL);
        for (int i=0; i<currentPkgLoss;i++) accumulatedSamples.add(lostSampleL);

        accumulatedSamples.add(microV);
        pkgArrivalTime = System.currentTimeMillis();
        long plottingElapsed = pkgArrivalTime - plottingLastRefresh;
        if (plottingElapsed < 1000/plottingFPS) {
            // only update the plot below if enough time has passed.
            return;
        }

        final List<ILineDataSet> plottableDatasets = new ArrayList<>();  // for adding multiple plots
        float t = 0;
        float pkgInterval = 1000/samplingRate;

        /** Add all accumulatedSamples to the datasets that are used for plotting **/
        for (int i = 0; i < accumulatedSamples.size(); i++) {
            plottedPkgCount += 1;
            t = plottedPkgCount * pkgInterval; // timestamp for x axis in ms
            List<Float> channelFloats = accumulatedSamples.get(i);
            float lastChannelSigma = 0;
            float displayedChannelsBelow = 0;
            for (int ch = 0; ch < nChannels; ch++) {
                channelOffsets[ch] = 40 * (lastChannelSigma+1) * displayedChannelsBelow;
                float plotValue = channelFloats.get(ch) + channelOffsets[ch];
                plottingBuffer.get(ch).add(new Entry(t, plotValue));

                if(channelsShown[ch]) {
                    lastChannelSigma = 2 << mTraumService.signalBitShift[ch];
                    displayedChannelsBelow++;
                }
            }
        }
        
        // Remove old entries
        if (plottingBuffer.get(0).size() > maxVisibleXRange/pkgInterval) {
            int start = accumulatedSamples.size();
            int end = plottingBuffer.get(0).size() -1;
            for(int ch=0; ch<nChannels; ch++){
                ArrayList<Entry> trimmed = new ArrayList<>(plottingBuffer.get(ch).subList(start,end));
                plottingBuffer.set(ch,trimmed);
            }
        }

        if (plottingThread != null) plottingThread.interrupt();
        //Update Plot
        final float centerX = t;
        final Runnable runnablePlottingThread = () -> {
            /* Create plottable datasets from plottingBuffer */
            float lastChannelSigma = 0;
            float displayedChannelsBelow = 0;
            for (int ch = 0; ch < nChannels; ch++) {
                if (channelsShown[ch]) {
                    LineDataSet set = createPlottableSet(ch);
                    plottableDatasets.add(set);
                }
            }
            mChart.getLineData().clearValues();
            mChart.notifyDataSetChanged();

            LineData graphData = new LineData(plottableDatasets);
            mChart.setData(graphData);
            mChart.notifyDataSetChanged();

            // limit the number of visible entries
            mChart.setVisibleXRangeMaximum(maxVisibleXRange);

            // keep current Y Position
            MPPointF centerPointPx = mChart.getViewPortHandler().getContentCenter();
            MPPointD centerPointValue = mChart.getValuesByTouchPoint(centerPointPx.x, centerPointPx.y, YAxis.AxisDependency.LEFT);
            float centerY = (float) centerPointValue.y;
            //Log.d(TAG, "CenterY " +  centerY);
            mChart.moveViewTo(centerX, centerY, YAxis.AxisDependency.LEFT); // What happens without this? I expect it sticks
        };
        // Execute the above defined thread
        plottingThread = new Thread(() -> runOnUiThread(runnablePlottingThread));
        plottingThread.start();
        plottingLastRefresh = System.currentTimeMillis();
        accumulatedSamples.clear();
    }

    //Starts a recording session
    @SuppressLint({"SimpleDateFormat", "SetTextI18n"})
    private void startRecording() {
        // Reset Variables used for recording
        plottedPkgCount = 0;
        storedPkgCount = 0;
        mainData = new ArrayList<>();
        start_time = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        start_timestamp = new Timestamp(startTime).getTime();
        recording = true;

        createRecordingFile();

        //UI Feedback
        mConnectionState.setText(R.string.recording);
        mConnectionState.setTextColor(Color.RED);
        Toast.makeText(getApplicationContext(), "Recording in process.", Toast.LENGTH_LONG
        ).show();
        buttons_recording();
    }

    private void createRecordingFile(){
        //transmission time, sampling time, channel values, transmissionID, pkgslosses, resolution
        String date = new SimpleDateFormat("yyyyddMM_HH-mm-ss").format(new Date());
        tempFileName = date+"_" + "recording.temp";
        try {
            recordingFile = new File(MainActivity.getDirSessions(),tempFileName);
            // if file doesn't exists, then create it
            if (!recordingFile.exists())
                recordingFile.createNewFile();
            fileWriter = new FileWriter(recordingFile);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }

        final StringBuilder header = new StringBuilder();
        header.append("time");
        header.append(delimiter + "sampling_time");
        for (int i = 1; i <= nChannels; i++) header.append(delimiter + String.format("ch%d", i));
        //for (int i = 1; i <= 1; i++) header.append(String.format("enc_ch%d,",i));
        header.append(delimiter+"pkgid");
        header.append(delimiter+"pkgloss_bluetooth");
        header.append(delimiter+"pkgloss_internal");
        header.append(delimiter+"transmission_rate");
        header.append(lineBreak);
        try {
            fileWriter.append(header);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Finish a recording session
    @SuppressLint("SimpleDateFormat")
    private void endRecording() {

        // Resetting and Clearing Variables
        recording = false;
        end_time = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        long stop_watch = System.currentTimeMillis();
        end_timestamp = new Timestamp(stop_watch).getTime();
        recording_time = Long.toString(stop_watch - startTime);

        timestamps.clear();
        samplingTimes.clear();
        pkgIDs.clear();
        pkgsLost.clear();

        // Open Save Dialog
        showSaveDialog();

        // UI Update
        mConnectionState.setText(R.string.device_connected);
        mConnectionState.setTextColor(Color.GREEN);
        buttons_prerecording();

    }

    private void showSaveDialog(){
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(getApplicationContext());
        View mView = layoutInflaterAndroid.inflate(R.layout.input_dialog_string, null);
        AlertDialog.Builder alertDialogBuilderUserInput = new AlertDialog.Builder(Record.this);
        alertDialogBuilderUserInput.setView(mView);

        final EditText userInputLabel = mView.findViewById(R.id.input_dialog_string_Input);

        alertDialogBuilderUserInput
                .setCancelable(false)
                .setTitle(R.string.session_label_title)
                .setMessage(getResources().getString(R.string.enter_session_label))
                .setPositiveButton("Save", (dialogBox, id) -> {
                    if (!userInputLabel.getText().toString().isEmpty()) {
                        try {
                            saveSession(userInputLabel.getText().toString());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            saveSession();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton("Discard", (dialogBox, id) -> {
                    deleteTempFiles();
                    recordingFile.delete();
                });

        AlertDialog alertDialogAndroid = alertDialogBuilderUserInput.create();
        alertDialogAndroid.show();
    }


    /** Stores
     * transmission time, sampling time, channel values, transmissionID, pkgslosses, resolution
     * @param data_microV
     */
    private void storeData(List<Float> data_microV) {

        float[] f_microV = new float[nChannels];
        Arrays.fill(f_microV, Float.NaN);
        //NaN rows on top (uninitialized)
        for (int i=0;i<currentPkgLoss; i++) appendSampleToCsv(f_microV);
        for (int i=0;i<currentBtPkgLoss; i++) appendSampleToCsv(f_microV);
        // Channel Values
        int i = 0;
        for (Float f : data_microV)
            f_microV[i++] = (f != null ? f : Float.NaN); // Or whatever default you want
        appendSampleToCsv(f_microV);
        adaptiveEncodingFlag = 0;

    }

    private void appendSampleToCsv(float[] sample){
        /*** WRITE TO CSV
         *  order: timestamp, samplingtime,channelvalues,pkgid,pkgsloss **/
        // Real Time Stamps
        if (storedPkgCount == 0) startTime = System.currentTimeMillis();
        float time = System.currentTimeMillis() - startTime;

        // Expected Time Stamps
        float samplingInterval = 1000/samplingRate;
        float samplingTime = samplingInterval*storedPkgCount;

        // Correct Pkg loss counts to have "pkg loss" at 0 for NaN rows
        int NaNCorrectionBtLoss = 0;
        int NaNCorrectionInternalLoss = 0;
        if (Float.isNaN(sample[0])){
            NaNCorrectionBtLoss = currentBtPkgLoss;
            NaNCorrectionInternalLoss = currentPkgLoss;
        }
        try {
            fileWriter.append(String.valueOf(time));
            fileWriter.append(delimiter + samplingTime);
            for (int j = 0; j < nChannels; j++) {
                fileWriter.append(delimiter + sample[j]);
            }
            fileWriter.append(delimiter + currentPkgId);
            fileWriter.append(delimiter + (currentBtPkgLoss - NaNCorrectionBtLoss));      //Bluetooth loss
            fileWriter.append(delimiter + (currentPkgLoss - NaNCorrectionInternalLoss)); //Internal loss
            fileWriter.append(delimiter + resolutionFrequency);
            fileWriter.append("\n");
            storedPkgCount++;

        } catch (Exception e){
            Log.e(TAG, e.getMessage());
        }
    }

    private void saveSession() throws IOException {
        saveSession("default");
    }



    //OBSOLETE ATM
    //Saves the data at the end of session
    @SuppressLint("DefaultLocale")
    private void saveSession(final String tag) throws IOException {
        // Column Names of Footer
        final String footerLabels = "Username,User ID,Session ID,Session Tag,Date,Shape (rows x columns)," +
                "Duration (ms),Starting Time,Ending Time,Sampling Rate,Bits per Channel," +
                "measurement unit,Starting Timestamp,Ending Timestamp";
        fileWriter.append(footerLabels);
        fileWriter.append("\n");
        // Footer Values
        final String username = getSharedPreferences("userPreferences", 0).getString("username", "user");
        final String userID = getSharedPreferences("userPreferences", 0).getString("userID", "12345678");
        final UUID id = UUID.randomUUID();
        String date = new SimpleDateFormat("yyyyddMMHHmmss").format(new Date());

        fileWriter.append(username);
        fileWriter.append(delimiter + userID);
        fileWriter.append(delimiter + id.toString());
        fileWriter.append(delimiter + tag);
        fileWriter.append(delimiter + date);
        fileWriter.append(delimiter + (storedPkgCount + "x" + nChannels));
        fileWriter.append(delimiter + recording_time);
        fileWriter.append(delimiter + start_time);
        fileWriter.append(delimiter + end_time);
        fileWriter.append(delimiter + samplingRate);
        fileWriter.append(delimiter + TraumschreiberService.bitsPerCh);
        fileWriter.append(delimiter + "µV");
        fileWriter.append(delimiter + start_timestamp);
        fileWriter.append(delimiter + end_timestamp);

        // rename your temp file to the desired tag
        String permFileName = date + "_" + tag + ".csv";
        File tempFile = new File(MainActivity.getDirSessions(),tempFileName);
        File permFile = new File(MainActivity.getDirSessions(),permFileName);
        boolean success = tempFile.renameTo(permFile);
        Toast.makeText(getApplicationContext(),"Stored Recording as " + permFileName, Toast.LENGTH_LONG
        ).show();
        fileWriter.close();

    }

    private void buttons_nodata() {
        imageButtonRecord.setImageResource(R.drawable.ic_fiber_manual_record_pink_24dp);
        imageButtonRecord.setEnabled(false);
        /*imageButtonSave.setImageResource(R.drawable.ic_save_gray_24dp);
        imageButtonSave.setEnabled(false);
        imageButtonDiscard.setImageResource(R.drawable.ic_delete_gray_24dp);
        imageButtonDiscard.setEnabled(false);*/
    }

    private void buttons_prerecording() {
        imageButtonRecord.setImageResource(R.drawable.ic_fiber_manual_record_red_24dp);
        imageButtonRecord.setEnabled(true);
        /*imageButtonSave.setImageResource(R.drawable.ic_save_gray_24dp);
        imageButtonSave.setEnabled(false);
        imageButtonDiscard.setImageResource(R.drawable.ic_delete_gray_24dp);
        imageButtonDiscard.setEnabled(false);*/
    }

    private void buttons_recording() {
        imageButtonRecord.setImageResource(R.drawable.ic_stop_black_24dp);
        /*imageButtonSave.setImageResource(R.drawable.ic_save_gray_24dp);
        imageButtonSave.setEnabled(false);
        imageButtonDiscard.setImageResource(R.drawable.ic_delete_gray_24dp);
        imageButtonDiscard.setEnabled(false);*/
    }

    private void buttons_postrecording() {
        /*imageButtonRecord.setImageResource(R.drawable.ic_fiber_manual_record_pink_24dp);
        imageButtonRecord.setEnabled(true);
        imageButtonSave.setEnabled(true);
        imageButtonSave.setImageResource(R.drawable.ic_save_black_24dp);
        imageButtonDiscard.setEnabled(true);
        imageButtonDiscard.setImageResource(R.drawable.ic_delete_black_24dp);*/
    }

    private void setConnectionStatus(boolean connected) {
        MenuItem menuItem = menu.findItem(R.id.scan);
        MenuItem menuItemSettings = menu.findItem(R.id.traumConfig);
        MenuItem menuItemNotify = menu.findItem(R.id.notify);
        MenuItem menuItemCast = menu.findItem(R.id.cast);
        MenuItem menuItemCentering = menu.findItem(R.id.centering);

        if (connected) {
            menuItem.setIcon(R.drawable.ic_bluetooth_connected_blue_24dp);
            mConnectionState.setText(R.string.device_connected);
            mConnectionState.setTextColor(Color.GREEN);
            plotSwitch.setEnabled(true);
            viewDeviceAddress.setText(mDeviceAddress);
            menuItemSettings.setVisible(true);
            menuItemNotify.setVisible(true);
            menuItemCast.setVisible(true);
            menuItemCentering.setVisible(true);
        } else {
            menuItem.setIcon(R.drawable.ic_bluetooth_searching_white_24dp);
            mConnectionState.setText(R.string.no_device);
            mConnectionState.setTextColor(Color.LTGRAY);
            buttons_nodata();
            plotSwitch.setEnabled(false);
            viewDeviceAddress.setText(R.string.no_address);
            menuItemNotify.setVisible(false);
            menuItemCast.setVisible(false);
            menuItemCentering.setVisible(false);
        }
    }

    private void getChannelColors() {
        // If you figure out a way to do this in a for loop, please feel free to make this better.
        channelColors[0] = ContextCompat.getColor(this, R.color.Ch1);
        channelColors[1] = ContextCompat.getColor(this, R.color.Ch2);
        channelColors[2] = ContextCompat.getColor(this, R.color.Ch3);
        channelColors[3] = ContextCompat.getColor(this, R.color.Ch4);
        channelColors[4] = ContextCompat.getColor(this, R.color.Ch5);
        channelColors[5] = ContextCompat.getColor(this, R.color.Ch6);
        channelColors[6] = ContextCompat.getColor(this, R.color.Ch7);
        channelColors[7] = ContextCompat.getColor(this, R.color.Ch8);
        channelColors[8] = ContextCompat.getColor(this, R.color.Ch9);
        channelColors[9] = ContextCompat.getColor(this, R.color.Ch10);
        channelColors[10] = ContextCompat.getColor(this, R.color.Ch11);
        channelColors[11] = ContextCompat.getColor(this, R.color.Ch12);
        channelColors[12] = ContextCompat.getColor(this, R.color.Ch13);
        channelColors[13] = ContextCompat.getColor(this, R.color.Ch14);
        channelColors[14] = ContextCompat.getColor(this, R.color.Ch15);
        channelColors[15] = ContextCompat.getColor(this, R.color.Ch16);
        channelColors[16] = ContextCompat.getColor(this, R.color.Ch17);
        channelColors[17] = ContextCompat.getColor(this, R.color.Ch18);
        channelColors[18] = ContextCompat.getColor(this, R.color.Ch19);
        channelColors[19] = ContextCompat.getColor(this, R.color.Ch20);
        channelColors[20] = ContextCompat.getColor(this, R.color.Ch21);
        channelColors[21] = ContextCompat.getColor(this, R.color.Ch22);
        channelColors[22] = ContextCompat.getColor(this, R.color.Ch23);
        channelColors[23] = ContextCompat.getColor(this, R.color.Ch24);
    }

    public void deleteTempFiles(){
        File dir = new File(MainActivity.getDirSessions(),"");
        File [] files = dir.listFiles();
        for (File tempFile : files) {
            if (tempFile.getName().endsWith(".temp")) {
                tempFile.delete();
                Log.d(TAG, "deleted temp file!");
            }
        }
    }

    // Streaming related
    class CastThread extends Thread {
        String IP = getSharedPreferences("userPreferences", 0).getString("IP", getResources().getString(R.string.default_IP));
        String PORT = getSharedPreferences("userPreferences", 0).getString("port", getResources().getString(R.string.default_port));
        // best way found until now to encode the values, a stringified JSON. Looks like:
        JSONObject toSend = new JSONObject();
//        private volatile boolean exit = false;
        // {'pkg': 1, 'time': 1589880540884, '1': -149.85352, '2': -18.530273, '3': 191.74805, '4': -305.34668, '5': 0, '6': -142.60254, '7': -1.6113281, '8': -29.80957}

        public void run() {
            try {
                WSClient c = new WSClient(new URI("ws://" + IP + ":" + PORT));
                c.setReuseAddr(true);
                // c.setConnectionLostTimeout(0); // default is 60 seconds
                // TODO: check if TCP_NODELAY improves speed, also .connect() vs .connectBlocking()
                // TODO: Add connect/disconnect control by cast button pressed and message received
                c.setTcpNoDelay(true);
                c.connectBlocking();
                int pkg = 0;
                List<Float> lastV = null; // store last octet of EEG values
                while (c.isOpen()) {
                    if (microV != null && lastV != microV) {
                        toSend = new JSONObject();
                        // timestamp in milliseconds since January 1, 1970, 00:00:00 GMT
                        long time = new Date().getTime();
                        toSend.put("pkg", pkg); // add pkg number
                        toSend.put("time", time); // add time
                        for (int i = 0; i < microV.size(); i++) {
                            // add voltage amplitudes
                            toSend.put(Integer.toString(i + 1), microV.get(i));
                        }
                        c.send(toSend.toString());
                        lastV = microV; // store current as last
                        pkg++; // increase package counter
//                        Log.d("WS", "Sent: " + toSend.toString());
                    }
                }
            } catch (URISyntaxException | JSONException | InterruptedException e) {
                e.printStackTrace();
                Log.d("WS", "URI error:" + e);
            }
        }

        public void staph() {

            Log.d("CastThread", "Stopped");
//            exit = true;
            if (out != null) {
                out.close();
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}
