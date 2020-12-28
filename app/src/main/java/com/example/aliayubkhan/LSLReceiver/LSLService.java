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

import com.example.aliayubkhan.LSLReceiver.recorder.RecorderFactory;
import com.example.aliayubkhan.LSLReceiver.recorder.StreamRecorder;
import com.example.aliayubkhan.LSLReceiver.xdf.XdfWriter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    private static final String TAG = "LSLService";

    private int streamCount;
    private String[] streamNames;
    private StreamRecorder[] activeRecorders;
    private XdfWriter xdfWriter;

    private final boolean recordTimingOffsets = false;
    private final boolean writeStreamFooters = true;

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        Log.i(TAG, "Service onStartCommand");
        Toast.makeText(this,"Recording LSL!", Toast.LENGTH_SHORT).show();

        // this method is part of the mechanisms that allow this to be a foreground channel
        createNotificationChannel();

        // resolve all streams that are in the network
        LSL.StreamInfo[] results = LSL.resolve_streams();
        streamCount = results.length;
        streamNames = new String[results.length];
        activeRecorders = new StreamRecorder[results.length];
        xdfWriter = new XdfWriter();

        //TODO only do all the things for selected streams instead of all streams
        for (int i=0; i<results.length; i++){
            startRecordingStream(results, i);
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

    private void startRecordingStream(LSL.StreamInfo[] results, int lslIndex) {
        try {
            LSL.StreamInfo inf = results[lslIndex];
            streamNames[lslIndex] = inf.name();
            System.out.println("The stream's XML meta-data is:\n" + inf.as_xml());

            RecorderFactory f = RecorderFactory.forLslStream(inf);
            StreamRecorder streamRecorder = f.openInlet();
            activeRecorders[lslIndex] = streamRecorder;

            spawnRecorderThread(streamRecorder);
        } catch (Exception e) {
            Log.e(TAG, "Unable to open LSL stream named: " + streamNames[lslIndex], e);
        }
    }

    private void spawnRecorderThread(StreamRecorder streamRecorder) {
        new Thread(() -> recordingLoop(streamRecorder)).start();
    }

    private void recordingLoop(StreamRecorder streamRecorder) {
        // First measurement of timing offset happens only after the first wait interval expired (5 sec) like LabRecorder does it.
        long nextTimeToMeasureOffset = OFFSET_MEASURE_INTERVAL + System.currentTimeMillis();

        while (!MainActivity.checkFlag) {
            try {
                while (true) {
                    streamRecorder.pullChunk();

                    long currentTimeMillis = System.currentTimeMillis();
                    if (recordTimingOffsets && currentTimeMillis >= nextTimeToMeasureOffset) {
                        boolean success = streamRecorder.takeTimeOffsetMeasurement() != null;
                        if (!success) {
                            Log.e(TAG, "LSL failed to obtain a clock offset measurement.");
                        }
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
                Log.e(TAG, "Failed to read or record stream chunk.", e);
            }
        }
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

        Path xdfFilePath = freshRecordingFilePath();
        MainActivity.path = xdfFilePath.toString();
        writeXdf(xdfFilePath);

        isAlreadyExecuted = true;
        MainActivity.isComplete = true;
//        for (StreamRecorder activeRecorder : activeRecorders) {
//            activeRecorder.close();
//        }
//        activeRecorders = null;
    }

    private void writeXdf(Path xdfFilePath) {
        Toast.makeText(this, "Writing file please wait!", Toast.LENGTH_LONG).show();

        List<Integer> selectedStreamIndices = new ArrayList<>(streamCount);
        for (int i = 0; i < streamCount; i++) {
            if (selectedItems.contains(streamNames[i])) {
                selectedStreamIndices.add(i);
            }
        }

        xdfWriter.setXdfFilePath(xdfFilePath);
        int xdfStreamIndex = 0;
        for (int i : selectedStreamIndices) {
            activeRecorders[i].writeStreamHeader(xdfWriter, xdfStreamIndex);
            xdfStreamIndex++;
        }

        xdfStreamIndex = 0;
        for (int i : selectedStreamIndices) {
            activeRecorders[i].writeAllRecordedSamples(xdfWriter, xdfStreamIndex);
            xdfStreamIndex++;
        }

        if (recordTimingOffsets) {
            xdfStreamIndex = 0;
            for (int i : selectedStreamIndices) {
                activeRecorders[i].writeAllRecordedTimingOffsets(xdfWriter, xdfStreamIndex);
                xdfStreamIndex++;
            }
        }

        if (writeStreamFooters) {
            xdfStreamIndex = 0;
            for (int i : selectedStreamIndices) {
                String footer = activeRecorders[i].getStreamFooterXml();
                xdfWriter.writeStreamFooter(xdfStreamIndex, footer);
                xdfStreamIndex++;
            }
        }

        Toast.makeText(this, "File written at: " + xdfFilePath, Toast.LENGTH_LONG).show();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static Path freshRecordingFilePath() {
        String isoTime = LocalDateTime.now().toString();
        String fileNameSafeTime = isoTime.replace(':', '-');
        return Paths.get(MainActivity.path, "recording-" + fileNameSafeTime + ".xdf");
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