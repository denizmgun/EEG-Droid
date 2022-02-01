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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.view.WindowManager;
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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
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
    final String delimiter = ",";
    final String lineBreak = "\n";
    private final Handler handler = new Handler();
    private final List<Float> timestamps = new ArrayList<>();
    private final List<Float> samplingTimes = new ArrayList<>();
    private final List<List<Float>> accumulatedSamples = new ArrayList<>();
    private final Float[] channelOffsets = new Float[24];
    private final int maxVisibleXRange = 8000;  // see 8s at the time on the plot
    private final int leftAxisUpperLimit = (int) (2 * Math.pow(10, 6));
    private final int leftAxisLowerLimit = (int) (-2 * Math.pow(10, 6));
    private final ArrayList<Integer> pkgIDs = new ArrayList<>();
    private final int nChannels = 24;
    private final ArrayList<ArrayList<Entry>> plottingBuffer = new ArrayList<ArrayList<Entry>>() {
        {
            for (int i = 0; i < nChannels; i++) {
                ArrayList<Entry> lineEntries = new ArrayList<>();
                add(lineEntries);
            }
        }
    };
    private final int[] channelColors = new int[nChannels];
    private final boolean[] channelsShown = new boolean[nChannels];
    private final CheckBox[] checkBoxes = new CheckBox[nChannels];
    private final TextView[] channelValueViews = new TextView[nChannels];
    private final TraumschreiberService mTraumService = new TraumschreiberService();
    private final ArrayList<Integer> adaptiveEncodingFlags = new ArrayList<>();
    private final ArrayList<Integer> signalBitShifts = new ArrayList<>();
    LSL.StreamInfo streamInfo;
    LSL.StreamOutlet streamOutlet = null;
    private final String[] channelLabels = new String[]{"FP1", "FPZ", "FP2", "F7", "F3", "Fz", "F4", "F8", "M1", "T7", "C3", "CZ", "C4", "T8", "M2", "P7", "P3", "Pz", "P4", "P8", "POZ", "O1", "OZ", "O2"};
    private float samplingRate = 500 / 2f;  // alternative: 500, 500/2, 500/3, 500/4, etc.
    private BluetoothGattCharacteristic configCharacteristic;
    private BluetoothGattCharacteristic codeCharacteristic;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private float selectedGain = 1f;
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
    private int iirHighpassPos = 1;
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
    private AlertDialog traumConfigDialog;
    private TextView mConnectionState;
    private TextView viewDeviceAddress;
    private boolean mNewDevice;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
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
    private boolean recording = false;
    private boolean notifying = false;
    private float resolutionTime;
    private float resolutionFrequency;
    private TextView mDataResolution;
    private int batteryValue;
    private TextView mBatteryValue;
    private androidx.appcompat.widget.SwitchCompat plotSwitch;
    private boolean plotting = true;
    private androidx.appcompat.widget.SwitchCompat channelViewsSwitch;
    private boolean channelViewsEnabled = true;
    private final CompoundButton.OnCheckedChangeListener channelViewsSwitchListener = (buttonView, isChecked) -> {
        LinearLayout channelViewsContainer = findViewById(R.id.ChannelViewsContainer);
        if (isChecked) {
            channelViewsEnabled = true;
            channelViewsContainer.setVisibility(View.VISIBLE);
        } else {
            channelViewsEnabled = false;
            channelViewsContainer.setVisibility(View.GONE);
        }
    };
    private int plottedPkgCount = 0;
    private int enabledCheckboxes = 0;
    private LineChart mChart;
    private Button tapZoomButton;
    private int tapZoomExponent = 0;
    @SuppressLint("SetTextI18n")
    private final View.OnClickListener tapZoomButtonOnClickListener = v -> {
        if (plottingBuffer.size() == 0) {
            Toast.makeText(getApplicationContext(), "Need Data First", Toast.LENGTH_SHORT).show();
            return;
        }
        tapZoomExponent++;
        if (tapZoomExponent > 4) tapZoomExponent = 0;
        int yRange = (int) ((leftAxisUpperLimit * 2) / Math.pow(10, tapZoomExponent));
        Log.d(TAG, "Visible yRange + " + yRange);
        /* First constrain the yRange to the desired limits (e.g. 2000muV,-2000muV) */
        mChart.setVisibleYRangeMaximum(yRange, YAxis.AxisDependency.LEFT);
        /* Now that our view is limited to the desired range, we can relax it again, but it wont zoom out by itself.*/
        mChart.setVisibleYRangeMaximum(leftAxisUpperLimit * 2, YAxis.AxisDependency.LEFT);
        if (tapZoomExponent == 0) mChart.fitScreen();
        mChart.moveViewTo(plottedPkgCount * 1000 / samplingRate, 0, YAxis.AxisDependency.LEFT);
        tapZoomButton.setText("10^" + tapZoomExponent);
    };
    private ImageButton recordingButton;
    private List<float[]> mainData;
    private int adaptiveEncodingFlag = 0; //Indicates whether adaptive encoding took place in this instant.
    private int signalBitShift = 0;
    private int pkgCountTotal;                //total count of packages that  arrived
    private int storedPkgCount;         //no. of samples added to the csv
    private int lostPkgCountTotal;            // total count of lost samples based on info in header
    private float pkgLossPercent;       // proportion of currently lost packages
    private final float pkgLossLimit = 0.02f; // tolerance for this proportion of package loss
    private boolean ignorePkgLoss = false;
    private float lastTimeStamp;
    private String start_time;
    private String end_time;
    private long startTime;
    private String recording_time;
    private long start_timestamp;
    private long end_timestamp;
    private long plottingLastRefresh;
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
    private long pkgArrivalTime;
    private boolean deviceConnected = false;
    private boolean casting = false;
    private Menu menu;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private List<Float> microV;
    private CastThread caster;
    private final List<Integer> pkgsLost = new ArrayList<>();
    private int currentPkgLoss = 0;
    private int currentPkgId = 0;
    private int lastPkgId = 0;
    private int currentBtPkgLoss = 0;
    private Thread plottingThread;
    private File recordingFile;
    private String tempFileName;
    private FileWriter fileWriter;
    private final View.OnClickListener recordingButtonOnClickListener = v -> {
        if (!recording) {
            startRecording();
        } else {
            // Open Save Dialog
            showSaveDialog();
        }
    };
    private final int srmUpdateInterval = 5000; // ms
    private boolean samplingRateMonitorRunning = false;
    private Timer srmTimer;
    private TimerTask srmTimerTask;
    private boolean showingPkgLossWarning = false;
    private final float[] hpFilteredNow = new float[nChannels];
    private float[] hpFilteredPrevious = new float[nChannels];
    private float[] samplePrevious = new float[nChannels];
    // Handles various events fired by the Service.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            // CONNECTION EVENT
            if (action.equals(BluetoothLeService.ACTION_GATT_CONNECTED)) {
                deviceConnected = true;
                buttons_prerecording();
                setConnectionStatus(true);
                storedPkgCount = 0;
                // DISCONNECTION EVENT
            } else if (action.equals(BluetoothLeService.ACTION_GATT_DISCONNECTED)) {
                deviceConnected = false;
                setConnectionStatus(false);
                clearUI();
                disableCheckboxes();
                if (srmTimer != null) {
                    srmTimer.cancel();
                    srmTimer.purge();
                }
                samplingRateMonitorRunning = false;

                // BLUETOOTH SERVICE REGISTRATION
            } else if (action.equals(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED)) {
                BluetoothGattService bleService = mBluetoothLeService.getService(TraumschreiberService.serviceUUID);
                mNotifyCharacteristic = bleService.getCharacteristic(TraumschreiberService.notifyingUUID);
                codeCharacteristic = bleService.getCharacteristic(TraumschreiberService.codeUUID);
                configCharacteristic = bleService.getCharacteristic(TraumschreiberService.configUUID);
                mBluetoothLeService.setCharacteristicNotification(codeCharacteristic, true);
                waitForBluetoothCallback(mBluetoothLeService);
                /*Apply default configuration once we have the config characteristic;
                  A bit inelegant, but it is more tedious to change default values on by repgroamming
                   the traumschreiber*/
                applyDefaultConfiguration();

                // HANDLE INCOMING STREAM
            } else if (action.equals(BluetoothLeService.ACTION_DATA_AVAILABLE)) {

                int[] data = intent.getIntArrayExtra(BluetoothLeService.EXTRA_DATA);

                // CONFIG DATA
                if (data.length == 8) {
                    int[] configData = intent.getIntArrayExtra(BluetoothLeService.EXTRA_DATA);
                    Toast.makeText(getApplicationContext(), "Received Config Data", Toast.LENGTH_SHORT).show();
                    updateTraumConfigValues(configData);
                    if (traumConfigDialog.isShowing()) displayTraumConfigValues();
                    return;
                }

                if (!notifying) return;

                // ENCODING UPDATES
                if (data[0] == 0xC0DE) {
                    signalBitShift = data[1];
                    //pkgLossCount = data[2];
                    //Log.v(TAG,"Updated signalBitshift of CH1: " + signalBitShift);
                    // Give the next row in our recording an adaptive encoding flag
                    adaptiveEncodingFlag = 1;
                    return; //prevent further processing
                }

                // no processing (for testing bluetooth transmission rates)
                //if(data==null) return;

                // CHANNEL DATA
                // First, parse the header (if there is a header)
                if (data.length > nChannels) {
                    currentPkgId = data[0];
                    currentPkgLoss = data[1];
                    currentBtPkgLoss = calculateBtPkgLoss();
                    data = Arrays.copyOfRange(data, 2, data.length);
                }
                pkgCountTotal++;
                lostPkgCountTotal += (currentBtPkgLoss + currentPkgLoss);
                if (!samplingRateMonitorRunning) startSamplingRateMonitoring();

                microV = convertToMicroV(data);
                if (getSharedPreferences("userPreferences", MODE_PRIVATE).getBoolean("inAppFilter", true)) {
                    microV = highPassFilter(microV);
                }
                streamData(microV);
                if (channelViewsEnabled && pkgCountTotal % 100 == 0) displayNumerical(microV);
                if (plotting) storeForPlotting(microV);
                if (recording) storeData(microV);
            }
        }
    };

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

    private int calculateBtPkgLoss() {
        int btloss = 0;
        if (storedPkgCount > 0) {
            if (lastPkgId > currentPkgId) {
                btloss = (15 - lastPkgId) + currentPkgId;
            } else {
                btloss = (currentPkgId - 1) - lastPkgId;
            }
        }
        lastPkgId = currentPkgId;
        return btloss;
    }

    private void applyConfiguration() {
        // Declare bytearray
        byte[] configBytes = new byte[8];

        // Concatenate binary strings
        configBytes[0] = (byte) (selectedGainB | bitsPerChB | runningAverageFilterB | sendOnOneCharB | generateDataB | transmissionRateB);
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
        Toast.makeText(getApplicationContext(), "Applied configuration.", Toast.LENGTH_SHORT).show();
    }

    private void applyDefaultConfiguration() {
        resetTraumConfig();
        // Declare bytearray
        byte[] configBytes = new byte[8];
        TraumschreiberService.setNotifyingUUID(2);
        configBytes = new byte[]{-23, 0, 0, 35, 0, -128, 0, 0};
        configCharacteristic.setValue(configBytes);
        mBluetoothLeService.writeCharacteristic(configCharacteristic);
        BluetoothGattService bleService = mBluetoothLeService.getService(TraumschreiberService.serviceUUID);
        mNotifyCharacteristic = bleService.getCharacteristic(TraumschreiberService.notifyingUUID);
    }

    /**
     * Calling this method opens up an alertdialog that displays the current
     * traumschreiber configuration. This method is used for debugging! It is called by
     * the onClickListener of the "apply_config_button" defined in showTraumConfigDialog()
     * The values of Byte 0 to Byte 5 of the configCharacteristic are shown as binary strings.
     * The settings encoded within 1 byte are listed right below it.
     * "x"'s indicate which bits of the byte are reserved for the corresponding setting.
     */
    private void showConfigValues() {

        // Byte Values of Config
        byte[] configValuesB = configCharacteristic.getValue();

        // Message Builder
        StringBuilder configValuesString = new StringBuilder();
        configValuesString.append("These settings are currently selected in the app interface: \n\n");

        configValuesString.append("Byte 0: \n");
        String configByteS = String.format("%8s", Integer.toBinaryString(configValuesB[0] & 0xFF))
                .replace(' ', '0');
        configValuesString.append(configByteS + "\n\n");

        Spinner gainSpinner = traumConfigDialog.findViewById(R.id.gain_spinner);
        configValuesString.append("Gain: " + gainSpinner.getSelectedItem().toString());
        configValuesString.append("\nxx00 0000 (00: 1, 01: 2, 10: 4, 11:8)\n\n");

        Spinner bitsPerChSpinner = traumConfigDialog.findViewById(R.id.bits_per_ch_spinner);
        configValuesString.append("Bits/Ch: " + bitsPerChSpinner.getSelectedItem().toString());
        configValuesString.append("\n00xx 0000 (00: 10, 01: 14, 10: 16, 11:10)\n\n");

        SwitchCompat runningAvgSwitch = traumConfigDialog.findViewById(R.id.average_filter_switch);
        int isChecked = runningAvgSwitch.isChecked() ? 1 : 0;
        configValuesString.append("Running Average Filter: " + isChecked);
        configValuesString.append("\n0000 x000 (0: off, 1: on)\n\n");

        SwitchCompat genData = traumConfigDialog.findViewById(R.id.generate_data_switch);
        isChecked = genData.isChecked() ? 1 : 0;
        configValuesString.append("DummyData: " + isChecked);
        configValuesString.append("\n0000 00x0 (0: off, 1: on)\n\n");

        Spinner tr = traumConfigDialog.findViewById(R.id.transmission_rate_spinner);
        configValuesString.append("Transmission: " + tr.getSelectedItem().toString());
        configValuesString.append("\n0000 000x (0: 167, 1: 250)\n\n");

        configValuesString.append("Byte 1: \n");
        configByteS = String.format("%8s", Integer.toBinaryString(configValuesB[1] & 0xFF))
                .replace(' ', '0');
        configValuesString.append(configByteS + "\n\n");


        configValuesString.append("Byte 2: \n");
        configByteS = String.format("%8s", Integer.toBinaryString(configValuesB[2] & 0xFF))
                .replace(' ', '0');
        configValuesString.append(configByteS + "\n\n");

        Spinner hp = traumConfigDialog.findViewById(R.id.o1_highpass_spinner);
        configValuesString.append("High Pass O1: " + hp.getSelectedItem().toString());
        configValuesString.append("\nxxxx 0000\n\n");

        Spinner iirhp = traumConfigDialog.findViewById(R.id.iir_highpass_spinner);
        configValuesString.append("High Pass IIR: " + iirhp.getSelectedItem().toString());
        configValuesString.append("\n0000 xxxx\n\n");

        configValuesString.append("Byte 3: \n");
        configByteS = String.format("%8s", Integer.toBinaryString(configValuesB[3] & 0xFF))
                .replace(' ', '0');
        configValuesString.append(configByteS + "\n\n");

        Spinner lp = traumConfigDialog.findViewById(R.id.lowpass_spinner);
        configValuesString.append("Low Pass: " + lp.getSelectedItem().toString());
        configValuesString.append("\nxxxx 0000\n\n");

        Spinner f50 = traumConfigDialog.findViewById(R.id.filter50hz_spinner);
        configValuesString.append("Filter 50hz: " + f50.getSelectedItem().toString());
        configValuesString.append("\n0000 xxxx\n\n");

        configValuesString.append("Byte 4: \n");
        configByteS = String.format("%8s", Integer.toBinaryString(configValuesB[4] & 0xFF))
                .replace(' ', '0');
        configValuesString.append(configByteS + "\n\n");

        Spinner bsMin = traumConfigDialog.findViewById(R.id.bitshift_min_spinner);
        configValuesString.append("Bitshift Min: " + bsMin.getSelectedItem().toString());
        configValuesString.append("\nxxxx 0000\n\n");

        Spinner bsMax = traumConfigDialog.findViewById(R.id.bitshift_max_spinner);
        configValuesString.append("Bitshift Min: " + bsMax.getSelectedItem().toString());
        configValuesString.append("\n0000 xxxx\n\n");

        configValuesString.append("Byte 5: \n");
        configByteS = String.format("%8s", Integer.toBinaryString(configValuesB[5] & 0xFF))
                .replace(' ', '0');
        configValuesString.append(configByteS + "\n\n");

        Spinner enc = traumConfigDialog.findViewById(R.id.encoding_safety_spinner);
        configValuesString.append("Encoding Safety: " + enc.getSelectedItem().toString());
        configValuesString.append("\nxxxx 0000\n\n");

        AlertDialog.Builder configInfoBuilder = new AlertDialog.Builder(this)
                .setTitle("Config Data")
                .setMessage(configValuesString.toString());
        AlertDialog configInfoDialog = configInfoBuilder.create();
        configInfoDialog.show();
    }

    private void startSamplingRateMonitoring() {
        //set a new Timer
        srmTimer = new Timer();
        initSrmTimerTask();
        srmTimer.schedule(srmTimerTask, srmUpdateInterval, srmUpdateInterval);
        mDataResolution.setText("calculating");
        samplingRateMonitorRunning = true;
    }

    private void initSrmTimerTask() {
        srmTimerTask = new TimerTask() {
            private int pkgCountSrm = 0; //packages arrived since last check
            private int lostPkgCountSrm = 0; //packages lost since last check

            public void run() {
                handler.post(() -> {
                    if (!notifying || pkgCountTotal == 0) return;

                    pkgCountSrm = pkgCountTotal - pkgCountSrm;
                    resolutionTime = (float) srmUpdateInterval / pkgCountSrm;    // ms per package
                    resolutionFrequency = pkgCountSrm / (float) (srmUpdateInterval / 1000);  // packages per second

                    String hertz = (int) resolutionFrequency + "Hz";
                    String resolution = String.format("%.2f", resolutionTime) + "ms - ";
                    String content = resolution + hertz;

                    lostPkgCountSrm = lostPkgCountTotal - lostPkgCountSrm;
                    float expectedPkgs = (float) lostPkgCountSrm + pkgCountSrm;
                    pkgLossPercent = (float) lostPkgCountSrm / expectedPkgs;
                    StringBuilder pkgLossDebug = new StringBuilder();
                    pkgLossDebug.append("Expected Pkgs: " + expectedPkgs);
                    pkgLossDebug.append("\nLost Packages: " + lostPkgCountSrm);
                    pkgLossDebug.append("\nPkg Loss %: " + pkgLossPercent);
                    Log.d(TAG, pkgLossDebug.toString());

                    int color; //green, orange and red for good, moderate and bad sampling rates
                    if (pkgLossPercent < 0.01) color = getResources().getColor(R.color.green);
                    else if (pkgLossPercent < pkgLossLimit)
                        color = getResources().getColor(R.color.orange);
                    else {
                        showPkgLossWarning();
                        color = getResources().getColor(R.color.red);
                    }

                    if (pkgCountTotal > 0) {
                        mDataResolution.setText(content);
                        mDataResolution.setTextColor(color);
                    }
                    pkgCountSrm = pkgCountTotal;
                    lostPkgCountSrm = lostPkgCountTotal;
                });
            }

        };
    }

    private void showPkgLossWarning() {
        // Avoids stacking Warnings on top of each other
        if (showingPkgLossWarning || ignorePkgLoss) return;

        String pkgLossPercentFormatted = String.format("%.02f", pkgLossPercent * 100);
        AlertDialog.Builder PkgLossWarningBuilder = new AlertDialog.Builder(this)
                .setTitle("Data Loss")
                .setMessage(pkgLossPercentFormatted + "% of samples are being lost. Change to less demanding" +
                        " settings?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    showingPkgLossWarning = false;
                    toggleNotifying();
                    showTraumConfigDialog();
                    highlightRelevantOptions();
                    unhighlightRelevantOptions();
                })
                .setNegativeButton("No", (dialog, which) -> {
                    showingPkgLossWarning = false;
                })
                .setNeutralButton("Ignore from now on", (dialog, which) -> {
                    showingPkgLossWarning = false;
                    ignorePkgLoss = true;
                })
                .setCancelable(false);
        AlertDialog pkgLossWarning = PkgLossWarningBuilder.create();
        pkgLossWarning.show();
        showingPkgLossWarning = true;
    }

    private void highlightRelevantOptions() {
        View bitsPerChRow = traumConfigDialog.findViewById(R.id.config_row_bits_per_ch);
        bitsPerChRow.setBackgroundColor(getResources().getColor(R.color.colorAccent));
        bitsPerChRow.setAlpha(0.8f);

        View transmissionRow = traumConfigDialog.findViewById(R.id.config_row_transmission_rate);
        transmissionRow.setBackgroundColor(getResources().getColor(R.color.colorAccent));
        transmissionRow.setAlpha(0.8f);
    }

    private void unhighlightRelevantOptions() {
        new java.util.Timer().schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        View bitsPerChRow = traumConfigDialog.findViewById(R.id.config_row_bits_per_ch);
                        bitsPerChRow.setBackgroundColor(getResources().getColor(R.color.design_default_color_background));
                        bitsPerChRow.setAlpha(1f);

                        View transmissionRow = traumConfigDialog.findViewById(R.id.config_row_transmission_rate);
                        transmissionRow.setBackgroundColor(getResources().getColor(R.color.design_default_color_background));
                        transmissionRow.setAlpha(1f);
                    }
                },
                8000);
    }

    private void endTimer() {
        // End the Timer Task
        mDataResolution.setTextColor((int) R.color.defaultTextView);
        mDataResolution.setText(R.string.default_resolution_text);
        if (samplingRateMonitorRunning) {
            srmTimerTask.cancel();
            samplingRateMonitorRunning = false;
        }
        pkgCountTotal = 0;
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        setContentView(R.layout.activity_record);

        // LSL stuff
        prepareLslStream();

        // UI References
        mConnectionState = findViewById(R.id.connection_state);
        viewDeviceAddress = findViewById(R.id.device_address);
        mConnectionState = findViewById(R.id.connection_state);
        mDataResolution = findViewById(R.id.resolution_value);
        mBatteryValue = findViewById(R.id.battery_value);

        plotSwitch = findViewById(R.id.switch_plots);
        channelViewsSwitch = findViewById(R.id.channel_views_switch);

        plotSwitch.setOnCheckedChangeListener(plotSwitchOnCheckedChangeListener);
        channelViewsSwitch.setOnCheckedChangeListener(channelViewsSwitchListener);

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

        if (plotting) checkBoxes[0].setChecked(true);

        createChart();
        TextView mXAxis = findViewById(R.id.XAxis_title);
        mXAxis.setVisibility(ViewStub.VISIBLE);

        recordingButton = findViewById(R.id.recordingButton);
        recordingButton.setOnClickListener(recordingButtonOnClickListener);

        tapZoomButton = findViewById(R.id.tapZoomButton);
        tapZoomButton.setOnClickListener(tapZoomButtonOnClickListener);

        // Traumschreiber Config Dialog
        traumConfigDialog = createTraumConfigDialog();

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        //Remove left over temporary files
        deleteTempFiles();

    }

    private void prepareLslStream() {
        final UUID uid = UUID.randomUUID();

        try {
            streamInfo = new LSL.StreamInfo("Traumschreiber-EEG", "EEG", 24,
                    samplingRate, LSL.ChannelFormat.float32, uid.toString());
            if (getSharedPreferences("userPreferences", MODE_PRIVATE).getBoolean("eegLabels", true)) {
                LSL.XMLElement chns = streamInfo.desc().append_child("channels");
                for (String label : channelLabels) {
                    LSL.XMLElement ch = chns.append_child("channel");
                    ch.append_child_value("label", label);
                    ch.append_child_value("unit", "microvolts");
                    ch.append_child_value("type", "EEG");
                }
            } else {
                for (int i = 1; i <= 24; i++)
                    streamInfo.desc().append_child(String.format("Ch-%d", i));
            }
        } catch (Error ex) {
            Log.e(TAG, " LSL issue: " + ex.getMessage());
        }
        try {
            streamOutlet = new LSL.StreamOutlet(streamInfo);
        } catch (IOException ex) {
            Log.d("LSL issue:", Objects.requireNonNull(ex.getMessage()));
            return;
        }
    }

    private TextView createChannelValueView(int i) {
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

    private CheckBox createPlottingCheckbox(int i) {
        int BoxIdRef = 94843913; //Just some pseudo random number to avoid confusion in onCheckedListener
        // Create Checkbox for displaying channel
        CheckBox box = new CheckBox(getApplicationContext());
        LinearLayout.LayoutParams boxLayout = new LinearLayout.LayoutParams(15, -2, 1f);
        //boxLayout.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        //boxLayout.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        //boxLayout.weight = 1;
        box.setLayoutParams(boxLayout);
        box.setId(BoxIdRef + i);
        box.setText(Integer.toString(i + 1));
        box.setTextSize(6);
        if (getSharedPreferences("userPreferences", MODE_PRIVATE).getBoolean("eegLabels", true)) {
            box.setText(channelLabels[i]);
        }
        box.setTextSize(8);
        box.setTextColor(channelColors[i]);
        box.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int channelId = buttonView.getId() - BoxIdRef;
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

    private void clearLSL() {
        // close and delete the stream outlet and info if possible
        if (streamOutlet != null) {
            streamOutlet.close();
            streamInfo.destroy();
            streamOutlet = null;
            streamInfo = null;
            Log.d(TAG, "LSL stream closed and deleted.");
        }
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "Called onBackPressed");

        try {
            clearLSL();
        } catch (Exception e) {
            Log.w(TAG, e.toString());
        }

        finish();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {

            unbindService(mServiceConnection);
            Log.d(TAG, "Called onDestroy");
            clearLSL();
        } catch (Exception e) {
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

        if (id == R.id.centering) {
            Toast.makeText(getApplicationContext(),
                    "Centering Signal around 0 in 2 seconds",
                    Toast.LENGTH_LONG).show();
            mTraumService.initiateCentering();
        }

        if (id == R.id.traumConfig) showTraumConfigDialog();

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
            useGainInConversion = getSharedPreferences("userPreferences", MODE_PRIVATE)
                    .getBoolean("useGainInConversion", false);
            mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, true);
            menuItemNotify.setIcon(R.drawable.ic_notifications_active_blue_24dp);
            //Prevent Screen from turning off
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        } else {
            Log.d(TAG, "Notifications Button pressed: DISABLED");
            notifying = false;
            mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, false);
            endTimer();
            menuItemNotify.setIcon(R.drawable.ic_notifications_off_white_24dp);
            //Stop screen from staying on
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        }
        if (codeCharacteristic != null) logDescriptorValue(codeCharacteristic);
        menuItemNotify.setEnabled(true);
    }

    public void logDescriptorValue(BluetoothGattCharacteristic c) {
        String cId = c.getUuid().toString().substring(4, 8);
        BluetoothGattDescriptor descriptor = c.getDescriptor(
                UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        byte[] descVal = descriptor.getValue();
        String descValS = (descVal == BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) ? "NOTIFICATIONS DISABLED" : "NOTIFICATIONS ENABLED";
        Log.d(TAG, "Characteristic " + cId + " DESCRIPTOR VALUE: " + descValS);
    }

    private AlertDialog createTraumConfigDialog() {
        // inflate the view
        View traumConfigView = getLayoutInflater().inflate(R.layout.traum_config_popup, null);
        // create the dialog
        AlertDialog.Builder configDialogBuilder = new AlertDialog.Builder(this,
                R.style.traumConfigDialog);
        configDialogBuilder.setView(traumConfigView);
        return configDialogBuilder.create();
    }

    private void showTraumConfigDialog() {

        traumConfigDialog.show();
        // Link the Items to Functions

        // Link Close Button
        View closeConfig = traumConfigDialog.findViewById(R.id.traum_config_close_button);
        closeConfig.setOnClickListener(v -> {
            traumConfigDialog.cancel();
        });


        // Link gainSpinner
        Spinner gainSpinner = traumConfigDialog.findViewById(R.id.gain_spinner);
        gainSpinner.setSelection(selectedGainPos);
        gainSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //Itempositions: {0,1,2,3} <=> {00,01,10,11} -- (<<6) --> {0b00..,0b01..,0b10..
                selectedGainPos = position;
                selectedGainB = (byte) ((position & 0x3) << 6);
                selectedGain = (float) Math.pow(2, position);
                byte[] binaryString = {selectedGainB};
                Log.d(TAG, "Binary rep of selected value: " + Arrays.toString(binaryString));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // nothing
            }
        });

        // Link bitsPerChSpinner
        Spinner bitsPerChSpinner = traumConfigDialog.findViewById(R.id.bits_per_ch_spinner);
        bitsPerChSpinner.setSelection(selectedBitsPerChPos);
        bitsPerChSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //Itempositions: {0,1,2} <=> {00,01,10,11} -- (<<4) --> {0b0000,0b0001..,0b0010
                selectedBitsPerChPos = position;
                bitsPerChB = (byte) ((position & 0x3) << 4);

                int bitsPerCh;
                Log.d(TAG, "Position of BitsPerCh: " + position);
                switch (position) {
                    case 0:
                        bitsPerCh = 10;
                        break;
                    case 1:
                        bitsPerCh = 14;
                        break;
                    case 2:
                        bitsPerCh = 16;
                        break;
                    case 3:
                        position = 0;  // 3 and 0 have same effect, later we set NotifyingUUID, needs to be 0 instead of 3
                        bitsPerCh = 10;
                        break;

                    default:
                        throw new IllegalStateException("Unexpected value: " + position);
                }
                TraumschreiberService.bitsPerCh = bitsPerCh;

                // Turn notifications off.
                if (notifying) toggleNotifying();

                //Update Characteristic on Which data is sent
                TraumschreiberService.setNotifyingUUID(position); //0, 1 or 2
                if (deviceConnected) {
                    BluetoothGattService bleService = mBluetoothLeService.getService(TraumschreiberService.serviceUUID);
                    mNotifyCharacteristic = bleService.getCharacteristic(TraumschreiberService.notifyingUUID);
                }

                byte[] binaryString = {bitsPerChB};
                Log.d(TAG, "Binary rep of selected value for bits per CH:  " + Arrays.toString(binaryString));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // nothing
            }
        });

        // Link RunningAverageSwitch
        SwitchCompat rafSwitch = traumConfigDialog.findViewById(R.id.average_filter_switch);
        rafSwitch.setChecked(runningAverageFilterCheck);
        rafSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                runningAverageFilterCheck = true;
                runningAverageFilterB = 1 << 3;
            } else {
                runningAverageFilterCheck = false;
                runningAverageFilterB = 0;
            }
        });

        // Link oneCharacteristicSwitch
        SwitchCompat oneCharSwitch = traumConfigDialog.findViewById(R.id.one_char_switch);
        oneCharSwitch.setChecked(sendOnOneCharCheck);
        oneCharSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                sendOnOneCharCheck = true;
                sendOnOneCharB = 1 << 2;
            } else {
                sendOnOneCharCheck = false;
                sendOnOneCharB = 0;
            }
        });


        // Link GenerateDataSwitch
        SwitchCompat genDataSwitch = traumConfigDialog.findViewById(R.id.generate_data_switch);
        genDataSwitch.setChecked(generateDataCheck);
        genDataSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                generateDataCheck = true;
                generateDataB = 0b10;
            } else {
                generateDataCheck = false;
                generateDataB = 0;
            }
        });

        // Link transmissionRateSpinner
        Spinner transmissionRateSpinner = traumConfigDialog.findViewById(R.id.transmission_rate_spinner);
        transmissionRateSpinner.setSelection(selectedTransmissionRatePos);
        transmissionRateSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //Itempositions: {0,1,2,3} <=> {00,01,10,11} -- (<<4) --> {0b0000,0b0001..,0b0010..
                selectedTransmissionRatePos = position;
                transmissionRateB = (byte) (position); // its 0 or 1 anyways

                // Small Extra for correct plotting timing
                if (transmissionRateB == (byte) 1) samplingRate = 500 / 2f;
                else samplingRate = 500 / 3f;

                byte[] binaryString = {transmissionRateB};
                Log.d(TAG, "Binary rep of selected value: " + Arrays.toString(binaryString));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // nothing
            }
        });


        // Link o1HighpassSpinner
        Spinner o1HighpassSpinner = traumConfigDialog.findViewById(R.id.o1_highpass_spinner);
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
        Spinner IIRSpinner = traumConfigDialog.findViewById(R.id.iir_highpass_spinner);
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
        Spinner lowPassSpinner = traumConfigDialog.findViewById(R.id.lowpass_spinner);
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
        Spinner filter50hzSpinner = traumConfigDialog.findViewById(R.id.filter50hz_spinner);
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
        Spinner bitshiftMinSpinner = traumConfigDialog.findViewById(R.id.bitshift_min_spinner);
        bitshiftMinSpinner.setSelection(bitshiftMinPos);
        bitshiftMinSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                bitshiftMinPos = position;
                bitshiftMinB = (byte) ((position & 0xff) << 4);

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // well Nothing
            }

        });

        // Link bitshift max
        Spinner bitshiftMaxSpinner = traumConfigDialog.findViewById(R.id.bitshift_max_spinner);
        bitshiftMaxSpinner.setSelection(bitshiftMaxPos);
        bitshiftMaxSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                bitshiftMaxPos = position;
                bitshiftMaxB = (byte) (position & 0xff);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                // well Nothing
            }

        });

        // Links safeEncodingFactor
        Spinner encodingSafetySpinner = traumConfigDialog.findViewById(R.id.encoding_safety_spinner);
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
        Button updateConfigButton = traumConfigDialog.findViewById(R.id.apply_config_button);
        updateConfigButton.setOnClickListener(v -> {
            if (deviceConnected) applyConfiguration();
            else
                Toast.makeText(getApplicationContext(), "No Device Connected", Toast.LENGTH_SHORT).show();
            // showConfigValues();  ONLY USED FOR DEBUGGING THE CONFIG MENU
        });

        // Link readConfigButton
        Button readConfigButton = traumConfigDialog.findViewById(R.id.read_config_button);
        readConfigButton.setOnClickListener(v -> {
            if (deviceConnected) mBluetoothLeService.readCharacteristic(configCharacteristic, true);
            else
                Toast.makeText(getApplicationContext(), "No Device Connected", Toast.LENGTH_SHORT).show();
        });

        // Link ResetconfigButton
        Button resetConfigButton = traumConfigDialog.findViewById(R.id.reset_config_button);
        resetConfigButton.setOnClickListener(v -> {
            resetTraumConfig();
            Toast.makeText(getApplicationContext(), "Selected Default Values", Toast.LENGTH_SHORT).show();
        });

        displayTraumConfigValues();

    }

    private void updateTraumConfigValues(int[] configData) {
        selectedGainPos = (configData[0] & 0xff) >> 6; // 0bxx00 0000
        selectedBitsPerChPos = (configData[0] & 0x30) >> 4; // 0b00xx 0000
        runningAverageFilterCheck = (configData[0] & 0x8) > 1; //0b0000 x000
        sendOnOneCharCheck = (configData[0] & 0x4) > 1; // 0b0000 0x00
        generateDataCheck = (configData[0] & 0x2) > 1; // 0b0000 00x0
        selectedTransmissionRatePos = (configData[0] & 0x03); // 0b0000 000x
        o1HighpassPos = (configData[2] & 0xff) >> 4; // 0bxxxx 0000
        iirHighpassPos = (configData[2] & 0x0f); // 0b 0000 xxxx
        lowpassPos = (configData[3] & 0xff) >> 4; // 0bxxxx 0000
        filter50hzPos = (configData[3] & 0x0f); // 0b0000 xxx
        bitshiftMinPos = (configData[4] & 0xff) >> 4; // 0bxxxx 000
        bitshiftMaxPos = (configData[4] & 0x0f); // 0b0000 xxxx
        encodingSafetyPos = (configData[5] & 0xff) >> 4; // 0bxxxx 0000


        float batteryValueF = ((configData[7] & 0xff) << 4) + ((configData[6] & 0xf0) >> 4);
        float batteryValueuV = (float) (2 * batteryValueF * 0.298);
        float batteryValueV = (float) (batteryValueuV / Math.pow(10, 6));
        Log.d(TAG, "Battery Value in V " + batteryValueV);
        batteryValue = (int) ((3.7f - batteryValueV) / 0.7) * 100;
        mBatteryValue.setText(batteryValue + "%");
    }

    private void displayTraumConfigValues() {
        Spinner gainSpinner = traumConfigDialog.findViewById(R.id.gain_spinner);
        gainSpinner.setSelection(selectedGainPos);
        Spinner bitsPerChSpinner = traumConfigDialog.findViewById(R.id.bits_per_ch_spinner);
        bitsPerChSpinner.setSelection(selectedBitsPerChPos);
        SwitchCompat rafSwitch = traumConfigDialog.findViewById(R.id.average_filter_switch);
        rafSwitch.setChecked(runningAverageFilterCheck);
        SwitchCompat oneCharSwitch = traumConfigDialog.findViewById(R.id.one_char_switch);
        oneCharSwitch.setChecked(sendOnOneCharCheck);
        SwitchCompat genDataSwitch = traumConfigDialog.findViewById(R.id.generate_data_switch);
        genDataSwitch.setChecked(generateDataCheck);
        Spinner transmissionRateSpinner = traumConfigDialog.findViewById(R.id.transmission_rate_spinner);
        transmissionRateSpinner.setSelection(selectedTransmissionRatePos);
        Spinner o1HighpassSpinner = traumConfigDialog.findViewById(R.id.o1_highpass_spinner);
        o1HighpassSpinner.setSelection(o1HighpassPos);
        Spinner IIRSpinner = traumConfigDialog.findViewById(R.id.iir_highpass_spinner);
        IIRSpinner.setSelection(iirHighpassPos);
        Spinner lowPassSpinner = traumConfigDialog.findViewById(R.id.lowpass_spinner);
        lowPassSpinner.setSelection(lowpassPos);
        Spinner filter50hzSpinner = traumConfigDialog.findViewById(R.id.filter50hz_spinner);
        filter50hzSpinner.setSelection(filter50hzPos);
        Spinner bitshiftMinSpinner = traumConfigDialog.findViewById(R.id.bitshift_min_spinner);
        bitshiftMinSpinner.setSelection(bitshiftMinPos);
        Spinner bitshiftMaxSpinner = traumConfigDialog.findViewById(R.id.bitshift_max_spinner);
        bitshiftMaxSpinner.setSelection(bitshiftMaxPos);
        Spinner encodingSafetySpinner = traumConfigDialog.findViewById(R.id.encoding_safety_spinner);
        encodingSafetySpinner.setSelection(encodingSafetyPos);
    }

    /**
     * Defines and sets all options of the traumschreiber config menu
     * Method defines the positions of the selected values in the drop down
     * menus of the config window. Values to corresponding positions are
     * defined in strings.xml
     */
    private void resetTraumConfig() {
        selectedGainPos = 3; // = 8
        selectedBitsPerChPos = 2; //16 bit
        runningAverageFilterCheck = true;
        sendOnOneCharCheck = false;  // off
        generateDataCheck = false;  // off
        selectedTransmissionRatePos = 1; //250Hz
        o1HighpassPos = 0;  // off
        iirHighpassPos = 0; //off
        lowpassPos = 2;    // 60Hz 6th Order
        filter50hzPos = 3;  // [46,54] 4th Order
        bitshiftMinPos = 0;
        bitshiftMaxPos = 0;
        encodingSafetyPos = 8;

        // Would otherwise crash if config window is currently not opened
        try {
            Spinner gainSpinner = traumConfigDialog.findViewById(R.id.gain_spinner);
            gainSpinner.setSelection(selectedGainPos);
            Spinner bitsPerChSpinner = traumConfigDialog.findViewById(R.id.bits_per_ch_spinner);
            bitsPerChSpinner.setSelection(selectedBitsPerChPos);
            SwitchCompat rafSwitch = traumConfigDialog.findViewById(R.id.average_filter_switch);
            rafSwitch.setChecked(runningAverageFilterCheck);
            SwitchCompat oneCharSwitch = traumConfigDialog.findViewById(R.id.one_char_switch);
            oneCharSwitch.setChecked(sendOnOneCharCheck);
            SwitchCompat genDataSwitch = traumConfigDialog.findViewById(R.id.generate_data_switch);
            genDataSwitch.setChecked(generateDataCheck);
            Spinner transmissionRateSpinner = traumConfigDialog.findViewById(R.id.transmission_rate_spinner);
            transmissionRateSpinner.setSelection(selectedTransmissionRatePos);
            Spinner o1HighpassSpinner = traumConfigDialog.findViewById(R.id.o1_highpass_spinner);
            o1HighpassSpinner.setSelection(o1HighpassPos);
            Spinner IIRSpinner = traumConfigDialog.findViewById(R.id.iir_highpass_spinner);
            IIRSpinner.setSelection(iirHighpassPos);
            Spinner lowPassSpinner = traumConfigDialog.findViewById(R.id.lowpass_spinner);
            lowPassSpinner.setSelection(lowpassPos);
            Spinner filter50hzSpinner = traumConfigDialog.findViewById(R.id.filter50hz_spinner);
            filter50hzSpinner.setSelection(filter50hzPos);
            Spinner bitshiftMinSpinner = traumConfigDialog.findViewById(R.id.bitshift_min_spinner);
            bitshiftMinSpinner.setSelection(bitshiftMinPos);
            Spinner bitshiftMaxSpinner = traumConfigDialog.findViewById(R.id.bitshift_max_spinner);
            bitshiftMaxSpinner.setSelection(bitshiftMaxPos);
            Spinner encodingSafetySpinner = traumConfigDialog.findViewById(R.id.encoding_safety_spinner);
            encodingSafetySpinner.setSelection(encodingSafetyPos);
        } catch (Exception e) {
            Log.w(TAG, e);
        }
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

    private void waitForBluetoothCallback(BluetoothLeService service) {
        while (service.isBusy) {
            Handler handler = new Handler();
            handler.postDelayed(() -> Log.v(TAG, "Waiting for bluetooth operation to finish."), 300);
        }

    }

    private void clearUI() {
        for (TextView view : channelValueViews) view.setText("0μV");
        mDataResolution.setText(R.string.default_resolution_text);
        pkgCountTotal = 0;
    }

    private void disableCheckboxes() {
        for (CheckBox box : checkBoxes) box.setEnabled(false);
    }

    /* This is the last processing step before the data is displayed and saved
     Note that gain is 1 by default */
    private boolean useGainInConversion;
    private List<Float> convertToMicroV(int[] data) {
        // Conversion formula (new): V_in = X * (298 / (1000 * gain))
        List<Float> dataMicroV = new ArrayList<>();

        if (useGainInConversion) {
            Log.d(TAG, "Using Old Conversion");
            for (float datapoint : data)
                dataMicroV.add(datapoint * 5 / 4 * 298 / (1000 * selectedGain));
        } else {
            Log.d(TAG, "Using Simple Conversion");
            for (float datapoint : data)
                dataMicroV.add(datapoint * 298/1000);
        }
        return dataMicroV;
    }

    private List<Float> highPassFilter(List<Float> data) {
        Log.v(TAG, "HIGHPASSFILTERING");
        // Convert sample to float array
        float[] sampleNow = new float[data.size()];
        for (int i = 0; i <= data.size() - 1; i++) {
            sampleNow[i] = data.get(i);
        }

        for (int i = 0; i < nChannels; i++) {
            hpFilteredNow[i] = (float) (0.999 * (hpFilteredPrevious[i]
                    + sampleNow[i] - samplePrevious[i]));
        }
        hpFilteredPrevious = hpFilteredNow;
        samplePrevious = sampleNow;

        // Convert hpFilteredNow to List<Float> and return
        List<Float> hpFiltered = new ArrayList<>();
        for (float f : hpFilteredNow) hpFiltered.add(f);
        return hpFiltered;
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
        try {
            streamOutlet.push_sample(sample);
        } catch (Exception ex) {
            Log.d("LSL issue", Objects.requireNonNull(ex.getMessage()));
            clearLSL();
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

        mChart = findViewById(R.id.liveChart);
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

        mChart.setDragDecelerationFrictionCoef(0.6f);
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
        for (int i = 0; i < currentBtPkgLoss; i++) accumulatedSamples.add(lostSampleL);
        for (int i = 0; i < currentPkgLoss; i++) accumulatedSamples.add(lostSampleL);

        accumulatedSamples.add(microV);
        pkgArrivalTime = System.currentTimeMillis();
        long plottingElapsed = pkgArrivalTime - plottingLastRefresh;
        int plottingFPS = 25;
        if (plottingElapsed < 1000 / plottingFPS) {
            // only update the plot below if enough time has passed.
            return;
        }

        final List<ILineDataSet> plottableDatasets = new ArrayList<>();  // for adding multiple plots
        float t = 0;
        float pkgInterval = 1000 / samplingRate;

        /* Add all accumulatedSamples to the datasets that are used for plotting **/
        for (int i = 0; i < accumulatedSamples.size(); i++) {
            plottedPkgCount += 1;
            t = plottedPkgCount * pkgInterval; // timestamp for x axis in ms
            List<Float> channelFloats = accumulatedSamples.get(i);
            float lastChannelSigma = 0;
            float displayedChannelsBelow = 0;
            for (int ch = 0; ch < nChannels; ch++) {
                channelOffsets[ch] = 40 * (lastChannelSigma + 1) * displayedChannelsBelow;
                float plotValue = channelFloats.get(ch) + channelOffsets[ch];
                plottingBuffer.get(ch).add(new Entry(t, plotValue));

                if (channelsShown[ch]) {
                    lastChannelSigma = 2 << TraumschreiberService.signalBitShift[ch];
                    displayedChannelsBelow++;
                }
            }
        }

        // Remove old entries
        if (plottingBuffer.get(0).size() > maxVisibleXRange / pkgInterval) {
            int start = accumulatedSamples.size();
            int end = plottingBuffer.get(0).size() - 1;
            for (int ch = 0; ch < nChannels; ch++) {
                ArrayList<Entry> trimmed = new ArrayList<>(plottingBuffer.get(ch).subList(start, end));
                plottingBuffer.set(ch, trimmed);
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
        lostPkgCountTotal = 0;
        mainData = new ArrayList<>();
        start_time = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
        start_timestamp = new Timestamp(startTime).getTime();
        recording = true;

        createRecordingFile();

        //UI Feedback
        Toast.makeText(getApplicationContext(), "Recording in progress.", Toast.LENGTH_LONG
        ).show();
        buttons_recording();

        //Prevent Screen from turning off
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private boolean header = true;
    private void createRecordingFile()  {
        //transmission time, sampling time, channel values, transmissionID, pkgslosses, resolution
        String date = new SimpleDateFormat("yyyyddMM_HH-mm-ss").format(new Date());
        tempFileName = date + "_" + "recording.temp";
        try {
            recordingFile = new File(MainActivity.getDirSessions(), tempFileName);
            // if file doesn't exists, then create it
            if (!recordingFile.exists())
                recordingFile.createNewFile();
            fileWriter = new FileWriter(recordingFile);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        
        //write a header with metadata
        if(header) writeHeader(fileWriter);

        // Specify column names here
        final StringBuilder columnNames = new StringBuilder();
        columnNames.append("time");
        columnNames.append(delimiter + "sampling_time");
        
        // use electrode labels ("FP1", etc.) or channel numbers ("ch1")
        boolean useChannelLabels = getSharedPreferences("userPreferences", MODE_PRIVATE)
                .getBoolean("eegLabels", true);
        if (useChannelLabels) {
            for (int i = 0; i < nChannels; i++){
                columnNames.append(delimiter + channelLabels[i]);
            }
        } else {
            for (int i = 1; i <= nChannels; i++){
                columnNames.append(String.format("ch%d,", i));
            }
        }

        columnNames.append(delimiter + "pkgid");
        columnNames.append(delimiter + "pkgloss_bluetooth");
        columnNames.append(delimiter + "pkgloss_internal");
        columnNames.append(delimiter + "transmission_rate");
        columnNames.append(lineBreak);

        try{
            fileWriter.append(columnNames);
        } catch (IOException e){
            e.printStackTrace();
        }

     
    }
    
    private void writeHeader(FileWriter fileWriter) {
        final StringBuilder header = new StringBuilder();
        
        // First Row
        header.append("Username" + delimiter);
        header.append("UserID" + delimiter);
        header.append("SessionTag" + delimiter);
        header.append("Date" + delimiter);
        header.append("SamplingRate" + delimiter);
        header.append("Bits" + delimiter);
        header.append("unit" + delimiter);
        header.append("\n");
   
     

        final String username = getSharedPreferences("userPreferences", 0).getString("username", "user");
        final String userID = getSharedPreferences("userPreferences", 0).getString("userID", "12345678");
        String date = new SimpleDateFormat("yyyyddMMHHmmss").format(new Date());

        // Second Row
        header.append(username+ delimiter);
        header.append(userID + delimiter);
        header.append("SessionTag" + delimiter);
        header.append(date + delimiter);
        header.append(samplingRate + delimiter);
        header.append(mTraumService.bitsPerCh + delimiter);
        header.append("microVolt" + delimiter);
        header.append("\n");

        try {
            fileWriter.write(header.toString());
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

        //Clear Files
        deleteTempFiles();

        // UI Update
        buttons_prerecording();
        //Prevent Screen from turning off
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Show Debug Statistics
        if (getSharedPreferences("userPreferences", MODE_PRIVATE).getBoolean("showStats", false)) {
            showStats();
        }


    }

    private void showStats() {
        StringBuilder statistics = new StringBuilder();
        statistics.append("Device: \t" + mDeviceAddress + "\n\n");
        statistics.append("Last Timestamp: \t" + lastTimeStamp + "\n");
        statistics.append("Sampling Rate: \t" + samplingRate + "\n");
        statistics.append("Bit resolution: \t" + TraumschreiberService.bitsPerCh + "\n");
        statistics.append("Total Number of PKGs: \t" + storedPkgCount + "\n");
        statistics.append("Total Number of Lost PKGs: \t" + lostPkgCountTotal + "\n");
        if (storedPkgCount > 0) {
            statistics.append("Package Loss (%): " + (float) (lostPkgCountTotal * 100) / (float) storedPkgCount);
        }
        AlertDialog.Builder statsDialogBuilder = new AlertDialog.Builder(Record.this)
                .setTitle("Recording statistics for debugging:")
                .setMessage(statistics.toString())
                .setCancelable(true)
                .setPositiveButton("OK", (dialogInterface, i) -> {
                });
        AlertDialog statsDialog = statsDialogBuilder.create();
        statsDialog.show();
    }

    private void showSaveDialog() {
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(getApplicationContext());
        View mView = layoutInflaterAndroid.inflate(R.layout.input_dialog_string, null);
        AlertDialog.Builder alertDialogBuilderUserInput = new AlertDialog.Builder(Record.this);
        alertDialogBuilderUserInput.setView(mView);

        final EditText userInputLabel = mView.findViewById(R.id.input_dialog_string_Input);

        alertDialogBuilderUserInput
                .setTitle(R.string.session_label_title)
                .setMessage(getResources().getString(R.string.enter_session_label))
                .setCancelable(true)
                .setOnCancelListener(dialogInterface -> {

                })
                .setPositiveButton("Save", (dialogBox, id) -> {
                    if (!userInputLabel.getText().toString().isEmpty()) {
                        try {
                            saveSession(userInputLabel.getText().toString());
                            endRecording();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            saveSession();
                            endRecording();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton("Discard", (dialogBox, id) -> showConfirmDiscardDialog());

        AlertDialog alertDialogAndroid = alertDialogBuilderUserInput.create();
        alertDialogAndroid.show();
    }

    private void showConfirmDiscardDialog() {
        AlertDialog.Builder confirmDiscardDialogBuilder = new AlertDialog.Builder(Record.this)
                .setMessage("Discard current recording?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialogInterface, i) -> endRecording())
                .setNegativeButton("No", (dialogInterface, i) -> showSaveDialog());
        AlertDialog confirmDiscardDialog = confirmDiscardDialogBuilder.create();
        confirmDiscardDialog.show();

    }

    /**
     * Stores
     * transmission time, sampling time, channel values, transmissionID, pkgslosses, resolution
     *
     * @param data_microV Voltages recorded by the Traumschreiber in micro Volts
     */
    private void storeData(List<Float> data_microV) {

        float[] f_microV = new float[nChannels];
        Arrays.fill(f_microV, Float.NaN);
        //NaN rows on top (uninitialized)
        for (int i = 0; i < currentPkgLoss; i++) writeSampleToCsv(f_microV);
        for (int i = 0; i < currentBtPkgLoss; i++) writeSampleToCsv(f_microV);
        // Channel Values
        int i = 0;
        for (Float f : data_microV)
            f_microV[i++] = (f != null ? f : Float.NaN); // Or whatever default you want
        writeSampleToCsv(f_microV);
        adaptiveEncodingFlag = 0;

    }

    /**
     * Adds a row to the recording with columns in the following order
     * timestamp, samplingtime,channelvalues,pkgid,pkgsloss
     *
     * @param sample float array with channel values
     */
    private void writeSampleToCsv(float[] sample) {

        // Real Time Stamps
        if (storedPkgCount == 0) startTime = System.currentTimeMillis();
        lastTimeStamp = System.currentTimeMillis() - startTime;

        // Expected Time Stamps
        float samplingInterval = 1000 / samplingRate;
        float samplingTime = samplingInterval * storedPkgCount;

        // Correct Pkg loss counts: NaN rows should have a pkg loss of 0.
        int NaNCorrectionBtLoss = 0;
        int NaNCorrectionInternalLoss = 0;
        if (Float.isNaN(sample[0])) {
            NaNCorrectionBtLoss = currentBtPkgLoss;
            NaNCorrectionInternalLoss = currentPkgLoss;
        }
        try {
            fileWriter.append(String.valueOf(lastTimeStamp));
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


        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void saveSession() throws IOException {
        saveSession("default");
    }

    /**
     * Writes a footer with meta data to the end of the file and saves it acc. to user preferences
     **/
    private boolean footer = false;
    @SuppressLint("DefaultLocale")
    private void saveSession(final String tag) throws IOException {
        if(footer) writeFooter(tag);
        
        //Get the date
        String date = new SimpleDateFormat("yyyyddMMHHmmss").format(new Date());
        
        // give the temp file a proper file name
        String permFileName = date + "_" + tag + ".csv";
        File tempFile = new File(MainActivity.getDirSessions(), tempFileName);
        File permFile = new File(MainActivity.getDirSessions(), permFileName);
        tempFile.renameTo(permFile);
        
        Toast.makeText(getApplicationContext(), "Stored Recording as " + permFileName, Toast.LENGTH_LONG
        ).show();
        
        fileWriter.close();
    }
    
    private void writeFooter(String tag) throws IOException{
        
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
    }

    private void buttons_nodata() {
        recordingButton.setImageResource(R.drawable.ic_fiber_manual_record_pink_24dp);
        recordingButton.setEnabled(false);
    }

    private void buttons_prerecording() {
        recordingButton.setImageResource(R.drawable.ic_fiber_manual_record_red_24dp);
        recordingButton.setEnabled(true);
    }

    private void buttons_recording() {
        recordingButton.setImageResource(R.drawable.ic_stop_black_24dp);
    }

    private void setConnectionStatus(boolean connected) {
        MenuItem menuItem = menu.findItem(R.id.scan);
        MenuItem menuItemSettings = menu.findItem(R.id.traumConfig);
        MenuItem menuItemNotify = menu.findItem(R.id.notify);
        MenuItem menuItemCast = menu.findItem(R.id.cast);
        MenuItem menuItemCentering = menu.findItem(R.id.centering);

        if (connected) {
            // Visual feedback
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

    public void deleteTempFiles() {
        File dir = new File(MainActivity.getDirSessions(), "");
        File[] files = dir.listFiles();
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
