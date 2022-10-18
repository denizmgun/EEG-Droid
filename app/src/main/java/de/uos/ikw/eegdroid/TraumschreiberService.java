package de.uos.ikw.eegdroid;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;


public class TraumschreiberService {

    public final static String DEVICE_NAME = "traumschreiber";

    // UUIDS
    public static final UUID serviceUUID = UUID.fromString("00000ee6-0000-1000-8000-00805f9b34fb");
    public static final ArrayList<UUID> notifyingUUIDs = new ArrayList<UUID>() {
        {
            add(UUID.fromString("0000ee60-0000-1000-8000-00805f9b34fb"));
            add(UUID.fromString("0000ee61-0000-1000-8000-00805f9b34fb"));
            add(UUID.fromString("0000ee62-0000-1000-8000-00805f9b34fb"));
        }
    };
    public static final UUID configUUID = UUID.fromString("0000ecc0-0000-1000-8000-00805f9b34fb");
    public static final UUID codeUUID = UUID.fromString("0000c0de-0000-1000-8000-00805f9b34fb");
    public static final int nChannels = 24;
    private final static String TAG = "TraumschreiberService";
    private static final int[] decodedSignal = new int[24];
    public static UUID notifyingUUID = UUID.fromString("0000ee60-0000-1000-8000-00805f9b34fb");
    public static int[] signalBitShift = new int[24];
    public static int bitsPerCh = 16;
    public static int currentPkgID = 0;
    public static boolean warmedUp = false;
    private static int[] signalOffset = new int[24];
    private static int pkgCount;
    private static final boolean header = true;
    public String mTraumschreiberDeviceAddress;

    public TraumschreiberService() {
    }

    public static boolean isTraumschreiberDevice(String bluetoothDeviceName) {
        return bluetoothDeviceName.toLowerCase().contains(DEVICE_NAME);
    }

    public static boolean isNewModel(String bluetoothDeviceName) {
        return bluetoothDeviceName.startsWith("T");
    }

    public static void setNotifyingUUID(int i) {
        // 0->10bit, 1->14bit, 2->16bit
        notifyingUUID = notifyingUUIDs.get(i);
        Log.d(TAG, "Notifying UUID of TraumschreiberService is now: " + notifyingUUID.toString());
    }

    /***
     * Decodes all kinds of data packages received via bluetooth from a Traumschreiber:
     *  # Signal Data - Units of voltage (distributed over the 3 notifying characteristics)
     *  # Encoding Updates
     *  # Config Data
     * @param dataBytes     raw bytes received from the Traumschreiber over bluetooth
     * @param characteristicId id of characteristic from which the data was received
     * @return decodedPkg   pos 0: bluetooth id, pos 1: dropped samples, pos 2:26 channel values
     */
    public static int[] decode(byte[] dataBytes, UUID characteristicId) {
        /* CHANNEL VALUES */
        if (characteristicId.equals(notifyingUUID)) {
            if (header) {
                int[] decodedPkg = new int[nChannels + 2];

                // bluetooth pkg id
                decodedPkg[0] = (dataBytes[0] >> 4) & 0x0f; // bluetooth pkg id
                if ((decodedPkg[0] - currentPkgID) > 1) {
                    Log.d(TAG, "##########LOST A BLUETOOTH PKG!!\n#\n#\n#########");
                    Log.d(TAG, "Decoded PKG: " + Arrays.toString(decodedPkg));
                }
                currentPkgID = decodedPkg[0];

                // dropped sample counts
                decodedPkg[1] = dataBytes[0] & 0x0f;
                if (decodedPkg[1] > 0) {
                    Log.v(TAG, "##########DROPPED A SAMPLE!!\n#\n#\n#########");
                    Log.v(TAG, "Decoded PKG: " + Arrays.toString(decodedPkg));
                }

                // channel values --- write decodedDeltas to positions [2:26] on decodedPkg
                int[] decodedDeltas = decodeDpcm(Arrays.copyOfRange(dataBytes, 1, dataBytes.length));
                System.arraycopy(decodedDeltas, 0, decodedPkg, 2, decodedDeltas.length);
                if (!warmedUp) return null;
                return decodedPkg;
            } else {
                if (!warmedUp) return null;
                return decodeDpcm(dataBytes);
            }

            /* ENCODING FACTORS */
        } else if (characteristicId.equals(codeUUID)) {
            //Log.v(TAG, "RECEIVED FROM C0DE, RAW: " + Arrays.toString(dataBytes));
            // Iterate through the 12 received bytes and split them into unsigned nibbles
            for (int i = 0; i < 12; i++) {
                signalBitShift[i * 2] = (dataBytes[i] >> 4) & 0xf;
                signalBitShift[i * 2 + 1] = dataBytes[i] & 0xf;
            }
            //Log.d(TAG, "RECEIVED FROM C0DE: " + Arrays.toString(signalBitShift));
            int[] encodingData = new int[]{0xc0de, signalBitShift[1], dataBytes[13]}; //
            return encodingData;

            /* CONFIG DATA */
        } else if (characteristicId.equals(configUUID)) {
            Log.d(TAG, "RECEIVED FROM CONFIG: " + Arrays.toString(dataBytes));
            int[] configData = new int[dataBytes.length];
            for (int i = 0; i < dataBytes.length; i++) {
                configData[i] = dataBytes[i];
            }
            return configData;

        } else {
            return null;
        }
    }

    /***
     * Converts bytes to ints and adds the values of the current data to the previous data.
     * @param  deltaBytes
     * @return int[] data
     */
    public static int[] decodeDpcm(byte[] deltaBytes) {
        //Log.v(TAG, "Encoded Delta: " + Arrays.toString(deltaBytes));
        int[] delta;

        switch (bitsPerCh) {
            case 10:
                delta = bytesTo10bitInts(deltaBytes);
                break;
            case 14:
                delta = bytesTo14bitInts(deltaBytes);
                break;
            case 16:
                delta = bytesTo16bitInts(deltaBytes);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + bitsPerCh);
        }
        //Log.v(TAG, "Decoded Delta: " + Arrays.toString(delta));

        for (int i = 0; i < nChannels; i++) {
            /*ch1RunningVarianceEstimate += 0.001 * Math.pow(delta[0],2);
            if (pkgCount%1000 == 0){
                ch1Sigma = (float) Math.sqrt(ch1RunningVarianceEstimate);
                ch1RunningVarianceEstimate = 0;
            }*/

            decodedSignal[i] += (delta[i] << signalBitShift[i]);
            // Centering the Signal
            if (pkgCount < 250)
                signalOffset[i] += 0.004 * decodedSignal[i]; // moving average over 500 pkgs
            if (pkgCount == 250) {
                decodedSignal[i] -= signalOffset[i];
                Log.d(TAG, "Means of all Channels: " + Arrays.toString(signalOffset));
                warmedUp = true;
            }
        }
        pkgCount++;
        return decodedSignal;
    }

    /***
     * Turns an array of bytes into an array of 10bit ints.
     * @param bytes
     * @return int[] data
     */
    public static int[] bytesTo10bitInts(byte[] bytes) {
        // Number of ints : bytes*8/10 (8bits per byte and 10bits per int)
        int[] data = new int[bytes.length * 8 / 10];
        /*
         * Pattern repeats after 5 bytes. Therefore we process the bytes in chunks of 5.
         * Processing 5 bytes yields 4 (10bit) ints.
         */
        int idx = 0;
        for (int i = 0; i <= bytes.length - 5; i += 5) {
            idx = i * 4 / 5;
            data[idx] = ((bytes[i] & 0xff) << 2) | ((bytes[i + 1] & 0xc0) >>> 6);
            data[idx + 1] = ((bytes[i + 1] & 0x3f) << 4) | ((bytes[i + 2] & 0xf0) >>> 4);
            data[idx + 2] = ((bytes[i + 2] & 0x0f) << 6) | ((bytes[i + 3] & 0xfc) >>> 2);
            data[idx + 3] = ((bytes[i + 3] & 0x03) << 8) | ((bytes[i + 4] & 0xff));
        }
        // Subtracting 1024 turns unsigned 10bit ints into their 2's complement
        for (int i = 0; i < data.length; i++) {
            if (data[i] >= Math.pow(2, 9)) data[i] -= Math.pow(2, 10);
        }
        return data;
    }

    /***
     * Turns an array of bytes into an array of 14bit ints.
     * @param bytes
     * @return int[] data
     */
    public static int[] bytesTo14bitInts(byte[] bytes) {
        // Number of ints : bytes*8/14 (8bits per byte and 14bits per int)
        int[] data = new int[nChannels];
        /*
         * Decoding pattern repeats after 7 Bytes or 4 14-bit-ints (=56bit).
         * Therefore, we process the data in chunks of 4.
         */
        int idx = 0;
        for (int i = 0; i <= data.length - 4; i += 4) {
            idx = i * 7 / 4; //adjusted after every loop step : 7 after 1, 14 after 2, 21 after 3, etc.

            data[i] = ((bytes[idx] & 0xff) << 6) | ((bytes[idx + 1] & 0xfc) >> 2);
            data[i + 1] = ((bytes[idx + 1] & 0x03) << 12) | ((bytes[idx + 2] & 0xff) << 4) | ((bytes[idx + 3] & 0xf0) >> 4);
            data[i + 2] = ((bytes[idx + 3] & 0x0f) << 10) | ((bytes[idx + 4] & 0xff) << 2) | ((bytes[idx + 5] & 0xc0) >> 6);
            data[i + 3] = ((bytes[idx + 5] & 0x3f) << 8) | ((bytes[idx + 6] & 0xff));
        }
        // Subtracting 2^14 turns unsigned 14bit ints into their 2's complement
        for (int i = 0; i < data.length; i++) {
            if (data[i] >= Math.pow(2, 13)) data[i] -= Math.pow(2, 14);
        }
        return data;
    }

    /***
     * Turns an array of bytes into an array of 16bit ints.
     * @param bytes
     * @return int[] data
     */
    public static int[] bytesTo16bitInts(byte[] bytes) {
        // Number of ints : bytes*8/14 (8bits per byte and 14bits per int)
        int[] data = new int[nChannels];
        for (int i = 0; i < nChannels; i++) {
            data[i] = ((bytes[i * 2] & 0xff) << 8) | (bytes[i * 2 + 1] & 0xff);
        }
        // Subtracting 2^16 turns unsigned 16bit ints into their 2's complement
        for (int i = 0; i < data.length; i++) {
            if (data[i] >= Math.pow(2, 15)) data[i] -= Math.pow(2, 16);
        }
        return data;
    }

    public void warmUp() {
        warmedUp = false;
        initiateCentering();
    }

    public void initiateCentering() {
        signalOffset = new int[24];
        pkgCount = 0;
    }
}