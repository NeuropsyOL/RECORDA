package com.example.aliayubkhan.LSLReceiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.lang3.ArrayUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import static com.example.aliayubkhan.LSLReceiver.MainActivity.isAlreadyExecuted;
import static com.example.aliayubkhan.LSLReceiver.MainActivity.selectedItems;


/**
 * Created by aliayubkhan on 19/04/2018.
 * Edited by Sarah Blum on 21/08/2020
 * Edited by Sören Jeserich on 21/10/2020
 */

public class LSLService extends Service {

    /**
     * Milliseconds between consecutive measurements of all stream's timing offsets.
     */
    private static final int OFFSET_MEASURE_INTERVAL = 5000;

    /**
     * One measurement sample of the offset of a stream's time compared to a reference clock.
     */
    private static class TimingOffsetMeasurement {
        /**
         * The {@link LSL#local_clock()} time.
         */
        final double collectionTime;
        /**
         * The current measured timing offset of the stream's time as determined by {@link LSL.StreamInlet#time_correction()}.
         */
        final double offset;

        private TimingOffsetMeasurement(double collectionTime, double offset) {
            this.collectionTime = collectionTime;
            this.offset = offset;
        }
    }

    private static final String TAG = "LSLService";
    public static Thread t2;

    LSL.StreamInlet[] inlet;
    LSL.StreamInfo[] results;

    //for int channel format
    ArrayList<Float>[] lightSample = new ArrayList[30];// = new ArrayList[];
    ArrayList<Double>[] lightTimestamp = new ArrayList[30];


    //for int channel format
    ArrayList<Integer>[] lightSampleInt = new ArrayList[30];// = new ArrayList[];

    //for Double channel format
    ArrayList<Double>[] lightSampleDouble = new ArrayList[30];// = new ArrayList[];

    //for String channel format
    ArrayList<String>[] lightSampleString = new ArrayList[30];// = new ArrayList[];

    //for String channel format
    ArrayList<Short>[] lightSampleShort = new ArrayList[30];// = new ArrayList[];

    //for String channel format
    ArrayList<Byte>[] lightSampleByte = new ArrayList[30];// = new ArrayList[];

    // records measured timing offsets
    private List<TimingOffsetMeasurement>[] offsetLists = new ArrayList[30];

    float[][] sample = new float[1][1];
    int[][] sampleInt = new int[1][1];
    double[][] sampleDouble = new double[1][1];
    short[][] sampleShort = new short[1][1];
    byte[][] sampleByte = new byte[1][1];
    String[][] sampleString = new String[1][1];

    String[] streamHeader;
    String[] streamFooter;
    int streamCount;
    int[] chanelCount;
    String[] name;
    String[] format;

    double[][] timestamps;

    private boolean recordTimingOffsets = true;
    private boolean writeStreamFooters = true;

    public LSLService(){
        super();
    }

    @Override
    public void onCreate() {
        //Toast.makeText(this,"Service Created!", Toast.LENGTH_SHORT).show();
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        Log.i(TAG, "Service onStartCommand");
        Toast.makeText(this,"Recording LSL!", Toast.LENGTH_SHORT).show();

        // this method is part of the mechanisms that allow this to be a foreground channel
        createNotificationChannel();

        // resolve all streams that are in the network
        //TODO only do all the things for selected streams instead of all streams
        results = LSL.resolve_streams();
        streamCount = results.length;

        inlet = new LSL.StreamInlet[results.length];
        streamHeader = new String[results.length];
        streamFooter = new String[results.length];
        chanelCount = new int[results.length];
        sample = new float[results.length][];
        sampleInt = new int[results.length][];
        sampleDouble = new double[results.length][];
        sampleString = new String[results.length][];
        sampleShort = new short[results.length][];
        sampleByte = new byte[results.length][];
        timestamps = new double[results.length][];
        name = new String[results.length];
        format = new String[results.length];

        for (int i=0; i<inlet.length; i++){
            final int finalI = i;
            final LSL.StreamInlet streamInlet;
            try {
                streamInlet = new LSL.StreamInlet(results[finalI]);
                inlet[finalI] = streamInlet;
                LSL.StreamInfo inf = streamInlet.info();
                chanelCount[finalI] = streamInlet.info().channel_count();
                System.out.println("The stream's XML meta-data is: ");
                System.out.println(inf.as_xml());
                streamHeader[finalI] = inf.as_xml();
                format[finalI] = getXmlNodeValue(inf.as_xml(), "channel_format");

                if (format[finalI].contains("float")){
                    sample[finalI] = new float[streamInlet.info().channel_count()];
                    lightSample[finalI] = new ArrayList<>(4);
                } else if(format[finalI].contains("int")){
                    sampleInt[finalI] = new int[streamInlet.info().channel_count()];
                    lightSampleInt[finalI] = new ArrayList<>(4);
                } else if(format[finalI].contains("double")){
                    sampleDouble[finalI] = new double[streamInlet.info().channel_count()];
                    lightSampleDouble[finalI] = new ArrayList<>(4);
                } else if(format[finalI].contains("string")){
                    sampleString[finalI] = new String[streamInlet.info().channel_count()];
                    lightSampleString[finalI] = new ArrayList<>(4);
                } else if(format[finalI].contains("byte")){
                    sampleByte[finalI] = new byte[streamInlet.info().channel_count()];
                    lightSampleByte[finalI] = new ArrayList<>(4);
                } else if(format[finalI].contains("short")){
                    sampleShort[finalI] = new short[streamInlet.info().channel_count()];
                    lightSampleShort[finalI] = new ArrayList<>(4);
                }
                timestamps[finalI] = new double[1];
                lightTimestamp[finalI] = new ArrayList<>(4);
                name[finalI] = streamInlet.info().name();
                offsetLists[finalI] = new ArrayList<>(4);

            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }

            new Thread(() -> {
                // First measurement of timing offset happens only after the first wait interval expired (5 sec) like LabRecorder does it.
                long nextTimeToMeasureOffset = OFFSET_MEASURE_INTERVAL + System.currentTimeMillis();

                while (!MainActivity.checkFlag) {
                    try {

                        while (true) {
                            final int samplesRead;
                            // assemble data structures
                            if (format[finalI].contains("float")){
                                samplesRead = streamInlet.pull_chunk(sample[finalI], timestamps[finalI]);
                            } else if(format[finalI].contains("int")){
                                samplesRead = streamInlet.pull_chunk(sampleInt[finalI], timestamps[finalI]);
                            } else if(format[finalI].contains("double")){
                                samplesRead = streamInlet.pull_chunk(sampleDouble[finalI], timestamps[finalI]);
                            } else if(format[finalI].contains("string")){
                                samplesRead = streamInlet.pull_chunk(sampleString[finalI], timestamps[finalI]);
                            } else if(format[finalI].contains("byte")){
                                samplesRead = streamInlet.pull_chunk(sampleByte[finalI], timestamps[finalI]);
                            } else if(format[finalI].contains("short")){
                                samplesRead = streamInlet.pull_chunk(sampleShort[finalI], timestamps[finalI]);
                            } else {
                                samplesRead = 0;
                            }

                            // add samples and timestamps to the end of the list whenever we received a sample
                            if (samplesRead > 0) {
                                if (format[finalI].contains("float")){
                                    lightTimestamp[finalI].add(timestamps[finalI][0]);
                                    for(int k=0;k<samplesRead;k++){
                                        lightSample[finalI].add(sample[finalI][k]);
                                    }
                                } else if(format[finalI].contains("int")){
                                    lightTimestamp[finalI].add(timestamps[finalI][0]);
                                    for(int k=0;k<samplesRead;k++){
                                        lightSampleInt[finalI].add(sampleInt[finalI][k]);
                                    }
                                } else if(format[finalI].contains("double")){
                                    lightTimestamp[finalI].add(timestamps[finalI][0]);
                                    for(int k=0;k<samplesRead;k++){
                                        lightSampleDouble[finalI].add(sampleDouble[finalI][k]);
                                    }
                                } else if(format[finalI].contains("string")){
                                    lightTimestamp[finalI].add(timestamps[finalI][0]);
                                    for(int k=0;k<samplesRead;k++){
                                        lightSampleString[finalI].add(sampleString[finalI][k]);
                                    }
                                } else if(format[finalI].contains("byte")){
                                    lightTimestamp[finalI].add(timestamps[finalI][0]);
                                    for(int k=0;k<samplesRead;k++){
                                        lightSampleByte[finalI].add(sampleByte[finalI][k]);
                                    }
                                } else if(format[finalI].contains("short")){
                                    lightTimestamp[finalI].add(timestamps[finalI][0]);
                                    for(int k=0;k<samplesRead;k++){
                                        lightSampleShort[finalI].add(sampleShort[finalI][k]);
                                    }
                                }
                            }

                            long currentTimeMillis = System.currentTimeMillis();
                            if (recordTimingOffsets && currentTimeMillis >= nextTimeToMeasureOffset) {
                                double now = LSL.local_clock();
                                double offset = streamInlet.time_correction(2.0);
                                offsetLists[finalI].add(new TimingOffsetMeasurement(now, offset));

                                /*
                                 * Adding the wait interval needs to be repeated only if the recording thread skipped
                                 * a measurement because it could not keep up.
                                 */
                                while (nextTimeToMeasureOffset <= currentTimeMillis) {
                                    nextTimeToMeasureOffset += OFFSET_MEASURE_INTERVAL;
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
        MainActivity.isRunning = true;

        // This service is killed by the OS if it is not started as background service
        // This feature is only supported in Android 10 or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startMyOwnForeground();
            Toast.makeText(this, "LSL Recorder can safely run in background!", Toast.LENGTH_LONG).show();
        } else {
            startForeground(1, new Notification());
            Toast.makeText(this, "LSL Recorder will be killed when in background!", Toast.LENGTH_LONG).show();
        }
        return START_NOT_STICKY;
    }

    // From https://stackoverflow.com/questions/47531742/startforeground-fail-after-upgrade-to-android-8-1
    // and https://androidwave.com/foreground-service-android-example/
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startMyOwnForeground() {
        String NOTIFICATION_CHANNEL_ID = "com.example.aliayubkhan.LSLReceiver";
        String channelName = "LSLReceiver Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        chan.setLightColor(Color.GREEN);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("LSLReceiver is running in background!")
                .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        int information_id = 2; // this must be unique and not 0, otherwise it does not have a meaning
        startForeground(information_id, notification);
    }

    public static native void writeStreamHeader(String filename, int streamIndex, String headerXml);
    public static native void writeStreamFooter(String filename, int streamIndex, String footerXml);
    public static native void writeStreamOffset(String filename, int streamIndex, double collectionTime, double offset);

    public native void writeDataChunkFloat(String filename, float[] lightSample, double[] lightTimestamps, int streamIndex, int channelCount);
    public native void writeDataChunkInt(String filename, int[] lightSample, double[] lightTimestamps, int streamIndex, int channelCount);
    public native void writeDataChunkDouble(String filename, double[] lightSample, double[] lightTimestamps, int streamIndex, int channelCount);
    public native void writeDataChunkStringMarker(String filename, String[] lightSample, double[] lightTimestamps, int streamIndex, int channelCount);
    public native void writeDataChunkShort(String filename, short[] lightSample, double[] lightTimestamps, int streamIndex, int channelCount);
    public native void writeDataChunkByte(String filename, byte[] lightSample, double[] lightTimestamps, int streamIndex, int channelCount);

    static {
        System.loadLibrary("generate_xdf");
    }

    String getXmlNodeValue(String xmlString, String nodeName){
        int start = xmlString.indexOf("<"+nodeName+">") + nodeName.length() + 2;
        int end = xmlString.indexOf("</"+nodeName+">");
        return xmlString.substring(start, end);
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.i(TAG, "Service onBind");
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onDestroy() {
        MainActivity.isRunning = false;
        Log.i(TAG, "Service onDestroy");

        String isoTime = LocalDateTime.now().toString();
        String fileNameSafeTime = isoTime.replace(':', '-');
        Path path = Paths.get(MainActivity.path, "recording-" + fileNameSafeTime + ".xdf");
        MainActivity.path = path.toString();
        Toast.makeText(this, "Writing file please wait!", Toast.LENGTH_LONG).show();

        List<Integer> selectedStreamIndices = new ArrayList<>(streamCount);
        for (int i = 0; i < streamCount; i++) {
            if (selectedItems.contains(name[i])) {
                selectedStreamIndices.add(i);
            }
        }

        int xdfStreamIndex = 0;
        for (int i : selectedStreamIndices) {
            writeStreamHeader(MainActivity.path, xdfStreamIndex, streamHeader[i]);
            xdfStreamIndex++;
        }

        xdfStreamIndex = 0;
        for (int i : selectedStreamIndices) {
            if (format[i].contains("float")){
                writeFloatStreamToXdf(i, xdfStreamIndex);
            } else if(format[i].contains("int")){
                writeIntStreamToXdf(i, xdfStreamIndex);
            } else if(format[i].contains("double")){
                writeDoubleStreamToXdf(i, xdfStreamIndex);
            } else if(format[i].contains("string")){
                writeMarkerStreamToXdf(i, xdfStreamIndex);
            } else if(format[i].contains("short")){
                writeShortStreamToXdf(i, xdfStreamIndex);
            } else if(format[i].contains("byte")){
                writeByteStreamToXdf(i, xdfStreamIndex);
            }
            xdfStreamIndex++;
        }

        if (recordTimingOffsets) {
            xdfStreamIndex = 0;
            for (int i : selectedStreamIndices) {
                for (TimingOffsetMeasurement m : offsetLists[i]) {
                    writeStreamOffset(MainActivity.path, xdfStreamIndex, m.collectionTime, m.offset);
                }
                xdfStreamIndex++;
            }
        }

        if (writeStreamFooters) {
            xdfStreamIndex = 0;
            for (int i : selectedStreamIndices) {
                writeStreamFooter(MainActivity.path, xdfStreamIndex, streamFooter[i]);
                xdfStreamIndex++;
            }
        }

        isAlreadyExecuted = true;
        MainActivity.isComplete = true;
        Toast.makeText(this, "File written at: " + MainActivity.path, Toast.LENGTH_LONG).show();
    }

    private void writeByteStreamToXdf(int i, int xdfStreamIndex) {
        byte[] lightsample = new byte[1];

        for (int k=0; k<lightSampleByte[i].size(); k++){
            lightsample[i] = lightSampleByte[i].get(k).byteValue();
        }
        double[] lighttimestamps = ArrayUtils.toPrimitive(lightTimestamp[i].toArray(new Double[0]), 0);

        lightsample = removeZerosByte(lightsample, lightsample.length);
        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);

        if (chanelCount[i] == 1){
            lighttimestamps = Arrays.copyOfRange(lighttimestamps, 0, lightsample.length);
        } else {
            lightsample = Arrays.copyOfRange(lightsample, 0, lighttimestamps.length*chanelCount[i]);
        }

        streamFooter[i] = createFooterXml(lighttimestamps, lightsample.length, offsetLists[i]);

        writeDataChunkByte(MainActivity.path, lightsample, lighttimestamps, xdfStreamIndex, chanelCount[i]);
    }

    private void writeShortStreamToXdf(int i, int xdfStreamIndex) {
        short[] lightsample = new short[1];

        for (int k=0; k<lightSampleShort[i].size(); k++){
            lightsample[i] = lightSampleShort[i].get(k).shortValue();
        }
        double[] lighttimestamps = ArrayUtils.toPrimitive(lightTimestamp[i].toArray(new Double[0]), 0);

        lightsample = removeZerosShort(lightsample, lightsample.length);
        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);

        if (chanelCount[i] == 1){
            lighttimestamps = Arrays.copyOfRange(lighttimestamps, 0, lightsample.length);
        } else {
            lightsample = Arrays.copyOfRange(lightsample, 0, lighttimestamps.length*chanelCount[i]);
        }

        streamFooter[i] = createFooterXml(lighttimestamps, lightsample.length, offsetLists[i]);

        writeDataChunkShort(MainActivity.path, lightsample, lighttimestamps, xdfStreamIndex, chanelCount[i]);
    }

    private void writeMarkerStreamToXdf(int i, int xdfStreamIndex) {
        String[] lightsample = lightSampleString[i].toArray(new String[0]);
        double[] lighttimestamps = ArrayUtils.toPrimitive(lightTimestamp[i].toArray(new Double[0]), 0);

        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);
        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);

        if (chanelCount[i] == 1){
            lighttimestamps = Arrays.copyOfRange(lighttimestamps, 0, lightsample.length);
        } else {
            lightsample = Arrays.copyOfRange(lightsample, 0, lighttimestamps.length*chanelCount[i]);
        }

        streamFooter[i] = createFooterXml(lighttimestamps, lightsample.length, offsetLists[i]);

        writeDataChunkStringMarker(MainActivity.path, lightsample, lighttimestamps, xdfStreamIndex, chanelCount[i]);
    }

    private void writeDoubleStreamToXdf(int i, int xdfStreamIndex) {
        double[] lightsample = ArrayUtils.toPrimitive(lightSampleDouble[i].toArray(new Double[0]), 0);
        double[] lighttimestamps = ArrayUtils.toPrimitive(lightTimestamp[i].toArray(new Double[0]), 0);

        lightsample = removeZerosDouble(lightsample, lightsample.length);
        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);

        if (chanelCount[i] == 1){
            lighttimestamps = Arrays.copyOfRange(lighttimestamps, 0, lightsample.length);
        } else {
            lightsample = Arrays.copyOfRange(lightsample, 0, lighttimestamps.length*chanelCount[i]);
        }

        streamFooter[i] = createFooterXml(lighttimestamps, lightsample.length, offsetLists[i]);

        writeDataChunkDouble(MainActivity.path, lightsample, lighttimestamps, xdfStreamIndex, chanelCount[i]);
    }

    private void writeIntStreamToXdf(int i, int xdfStreamIndex) {
        int[] lightsample = ArrayUtils.toPrimitive(lightSampleInt[i].toArray(new Integer[0]), 0);
        double[] lighttimestamps = ArrayUtils.toPrimitive(lightTimestamp[i].toArray(new Double[0]), 0);

        lightsample = removeZerosInt(lightsample, lightsample.length);
        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);

        if (chanelCount[i] == 1){
            lighttimestamps = Arrays.copyOfRange(lighttimestamps, 0, lightsample.length);
        } else {
            lightsample = Arrays.copyOfRange(lightsample, 0, lighttimestamps.length*chanelCount[i]);
        }

        streamFooter[i] = createFooterXml(lighttimestamps, lightsample.length, offsetLists[i]);

        writeDataChunkInt(MainActivity.path, lightsample, lighttimestamps, xdfStreamIndex, chanelCount[i]);
    }

    private void writeFloatStreamToXdf(int i, int xdfStreamIndex) {
        float[] lightsample = ArrayUtils.toPrimitive(lightSample[i].toArray(new Float[0]), 0);
        double[] lighttimestamps = ArrayUtils.toPrimitive(lightTimestamp[i].toArray(new Double[0]), 0);

        if(!name[i].contains("Audio")){
            lightsample = removeZerosFloat(lightsample, lightsample.length);
        }

        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);
        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);

        if (chanelCount[i] == 1){
            if(name[i].contains("Audio")){
                lightsample = appendZeros(lightsample, lighttimestamps);
            }
            lighttimestamps = Arrays.copyOfRange(lighttimestamps, 0, lightsample.length);
        } else {
            lightsample = Arrays.copyOfRange(lightsample, 0, lighttimestamps.length*chanelCount[i]);
        }

        streamFooter[i] = createFooterXml(lighttimestamps, lightsample.length, offsetLists[i]);

        writeDataChunkFloat(MainActivity.path, lightsample, lighttimestamps, xdfStreamIndex, chanelCount[i]);
    }

    private static String createFooterXml(double[] timestamps, int sampleCount, List<TimingOffsetMeasurement> timingOffsets) {
        NumberFormat nf = DecimalFormat.getInstance(Locale.US);
        nf.setMaximumFractionDigits(8);
        nf.setGroupingUsed(false);

        StringBuilder footer = new StringBuilder();
        footer.append("<?xml version=\"1.0\"?>\n")
                .append("<info>\n");
        if (timestamps.length > 0) {
            footer.append("\t<first_timestamp>").append(timestamps[0]).append("</first_timestamp>\n")
                    .append("\t<last_timestamp>").append(timestamps[timestamps.length - 1]).append("</last_timestamp>\n");
        }
        footer.append("\t<sample_count>").append(sampleCount).append("</sample_count>\n")
                .append("\t<clock_offsets>\n");
        for (TimingOffsetMeasurement timingOffset : timingOffsets) {
            double time = timingOffset.collectionTime - timingOffset.offset;
            footer.append("\t\t<offset><time>").append(nf.format(time)).append("</time>")
                    .append("<value>").append(nf.format(timingOffset.offset)).append("</value></offset>\n");
        }
        footer.append("\t</clock_offsets>\n")
                .append("</info>\n");
        return footer.toString();
    }

    public float[] appendZeros(float[] sample, double[] timestamps){
        float[] data1 = new float[1];

        int dif = timestamps.length - sample.length;

        data1 = Arrays.copyOf(sample, sample.length + dif);

        for (int i=sample.length; i<timestamps.length; i++){
            data1[i] = 0;
        }

        return data1;
    }

    // removing leading zeros
    static int[] removeZerosInt(int[] a, int n)
    {

        // index to store the first
        // non-zero number
        int ind = -1;

        // traverse in the array and find the first
        // non-zero number
        for (int i = 0; i < n; i++) {
            if (a[i] != 0) {
                ind = i;
                if (i == 0){
                    Log.i("LSLService", "No action in remove zeros");
                    return a; // the very first element was non-zero, there is nothing else to do in this method
                } else {
                    Log.i("LSLService", "We have found leading zeros!");
                    break; // leave the loop and treat the rest of the elements
                }
            }
        }

        // if no non-zero number is there
        if (ind == -1) {
            System.out.print("Array has leading zeros only");
            return a;
        }

        // Create an array to store
        // numbers apart from leading zeros
        int[] b = new int[n - ind];

        // store the numbers removing leading zeros
        for (int i = 0; i < n - ind; i++)
            b[i] = a[ind + i];

        return b;
    }

    // removing leading zeros
    static float[] removeZerosFloat(float[] a, int n)
    {

        // index to store the first
        // non-zero number
        int ind = -1;

        // traverse in the array and find the first
        // non-zero number
        for (int i = 0; i < n; i++) {
            if (a[i] != 0) {
                ind = i;
                if (i == 0){
                    Log.i("LSLService", "No action in remove zeros");
                    return a; // the very first element was non-zero, there is nothing else to do in this method
                } else {
                    Log.i("LSLService", "We have found leading zeros!");
                    break; // leave the loop and treat the rest of the elements
                }
            }
        }

        // if no non-zero number is there
        if (ind == -1) {
            System.out.print("Array has leading zeros only");
            return a;
        }

        // Create an array to store
        // numbers apart from leading zeros
        float[] b = new float[n - ind];

        // store the numbers removing leading zeros
        for (int i = 0; i < n - ind; i++)
            b[i] = a[ind + i];

        return b;
    }

    // removing leading zeros
    static double[] removeZerosDouble(double[] a, int n)
    {

        // index to store the first
        // non-zero number
        int ind = -1;

        // traverse in the array and find the first
        // non-zero number
        for (int i = 0; i < n; i++) {
            if (a[i] != 0) {
                ind = i;
                if (i == 0){
                    Log.i("LSLService", "No action in remove zeros");
                    return a; // the very first element was non-zero, there is nothing else to do in this method
                } else {
                    Log.i("LSLService", "We have found leading zeros!");
                    break; // leave the loop and treat the rest of the elements
                }
            }
        }

        // if no non-zero number is there
        if (ind == -1) {
            System.out.print("Array has leading zeros only");
            return a;
        }

        // Create an array to store
        // numbers apart from leading zeros
        double[] b = new double[n - ind];

        // store the numbers removing leading zeros
        for (int i = 0; i < n - ind; i++)
            b[i] = a[ind + i];

        return b;
    }

    // removing leading zeros
    static short[] removeZerosShort(short[] a, int n)
    {

        // index to store the first
        // non-zero number
        int ind = -1;

        // traverse in the array and find the first
        // non-zero number
        for (int i = 0; i < n; i++) {
            if (a[i] != 0) {
                ind = i;
                if (i == 0){
                    Log.i("LSLService", "No action in remove zeros");
                    return a; // the very first element was non-zero, there is nothing else to do in this method
                } else {
                    Log.i("LSLService", "We have found leading zeros!");
                    break; // leave the loop and treat the rest of the elements
                }
            }
        }

        // if no non-zero number is there
        if (ind == -1) {
            System.out.print("Array has leading zeros only");
            return a;
        }

        // Create an array to store
        // numbers apart from leading zeros
        short[] b = new short[n - ind];

        // store the numbers removing leading zeros
        for (int i = 0; i < n - ind; i++)
            b[i] = a[ind + i];

        return b;
    }

    // removing leading zeros
    static byte[] removeZerosByte(byte[] a, int n)
    {

        // index to store the first
        // non-zero number
        int ind = -1;

        // traverse in the array and find the first
        // non-zero number
        for (int i = 0; i < n; i++) {
            if (a[i] != 0) {
                ind = i;
                if (i == 0){
                    Log.i("LSLService", "No action in remove zeros");
                    return a; // the very first element was non-zero, there is nothing else to do in this method
                } else {
                    Log.i("LSLService", "We have found leading zeros!");
                    break; // leave the loop and treat the rest of the elements
                }
            }
        }

        // if no non-zero number is there
        if (ind == -1) {
            System.out.print("Array has leading zeros only");
            return a;
        }

        // Create an array to store
        // numbers apart from leading zeros
        byte[] b = new byte[n - ind];

        // store the numbers removing leading zeros
        for (int i = 0; i < n - ind; i++)
            b[i] = a[ind + i];

        return b;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "FOREGROUNDCHANNEL",
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
}