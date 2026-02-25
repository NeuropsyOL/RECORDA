package de.uol.neuropsy.recorda;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import de.uol.neuropsy.recorda.recorder.QualityMetrics;
import de.uol.neuropsy.recorda.recorder.QualityState;
import de.uol.neuropsy.recorda.recorder.RecorderFactory;
import de.uol.neuropsy.recorda.recorder.StreamRecorder;
import de.uol.neuropsy.recorda.recorder.StreamRecording;
import de.uol.neuropsy.recorda.xdf.XdfWriter;
import edu.ucsd.sccn.LSL;


/**
 * Created by aliayubkhan on 19/04/2018.
 * Edited by Sarah Blum on 21/08/2020
 * Edited by Sören Jeserich on 21/10/2020
 */

public class LSLService extends Service {

    private static final String TAG = "LSLService";

    private static final String QUALITY_NOTIFICATION_CHANNEL_NAME = "RECORDA stream quality";
    private static final String QUALITY_NOTIFICATION_CHANNEL_ID = "de.uol.neuropsy.Recorda.quality";

    private final List<StreamRecording> activeRecordings = new ArrayList<>();

    private List<String> streamNames = new ArrayList<>();

    private QualityState[] streamQualities = new QualityState[0];

    private XdfWriter xdfWriter;
    
    private final boolean recordTimingOffsets = true;

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        Log.i(TAG, "Service onStartCommand");
        MainActivity.isRunning = true;
        safeShowToast("Recording LSL!", Toast.LENGTH_SHORT);

        try {
            // this method is part of the mechanisms that allow this to be a foreground service
            // a notification channel must also registered in the system before we can send notifications
            // to the user
            createNotificationChannel();

            xdfWriter = new XdfWriter();
            Path xdfFilePath = freshRecordingFilePath();
            xdfWriter.setXdfFilePath(xdfFilePath);
            Log.i(TAG, "XDF file path set to: " + xdfFilePath);

            // resolve all streams that are in the network
            LSL.StreamInfo[] lslStreams = LSL.resolve_streams();
            Log.i(TAG, "Found " + lslStreams.length + " LSL streams");

            Collection<String> selectedLslStreams = MainActivity.selectedStreamNames.stream()
                    .map(stream -> stream.lslName)
                    .collect(Collectors.toSet());

            int xdfStreamIndex = 0;
            synchronized (this) {
                streamNames = new ArrayList<>();
                for (LSL.StreamInfo availableStream : lslStreams) {
                    boolean isSelectedToBeRecorded = selectedLslStreams.contains(availableStream.name());
                    if (isSelectedToBeRecorded) {
                        StreamRecording rec = prepareRecording(availableStream, xdfStreamIndex++);
                        if (rec != null) {
                            activeRecordings.add(rec);
                            streamNames.add(availableStream.name());
                        }
                    }
                }
                streamQualities = new QualityState[activeRecordings.size()];
                Arrays.fill(streamQualities, QualityState.OK);
            }

            Log.i(TAG, "Prepared " + activeRecordings.size() + " recordings");

            activeRecordings.forEach(streamRecording -> {
                streamRecording.registerQualityListener((int streamIndex, QualityMetrics q) -> {
                    QualityState qualityNow = q.getCurrentQuality();
                    Log.i(TAG, "Stream " + streamIndex + " srate: " + q.getCurrentSamplingRate() + " q: " + qualityNow);
                    if (streamQualities[streamIndex] != qualityNow) {
                        streamQualities[streamIndex] = qualityNow;
                        if (qualityNow != QualityState.OK) {
                            postStreamQualityNotification(streamNames.get(streamIndex), qualityNow);
                        }
                    }
                });
                streamRecording.spawnRecorderThread();
            });

            startMyOwnForeground();
            safeShowToast("LSL Recorder can safely run in background!", Toast.LENGTH_LONG);
            Log.i(TAG, "Service started successfully");

        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Error starting LSL recording service", e);
            safeShowToast("Failed to start recording: " + e.getMessage(), Toast.LENGTH_LONG);

            // Clean up partial initialization
            try {
                if (xdfWriter != null) {
                    Log.w(TAG, "Cleaning up partial initialization");
                }
                activeRecordings.clear();
                MainActivity.isRunning = false;
            } catch (Exception cleanupError) {
                Log.e(TAG, "Error during cleanup", cleanupError);
            }

            // Stop the service since initialization failed
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private StreamRecording prepareRecording(LSL.StreamInfo lslStream, int xdfStreamIndex) {
        try {
            RecorderFactory f = RecorderFactory.forLslStream(lslStream);
            StreamRecorder sourceStream = f.openInlet();
            StreamRecording recording = new StreamRecording(sourceStream, xdfWriter, xdfStreamIndex);
            recording.setRecordTimingOffsets(recordTimingOffsets);
            recording.writeStreamHeader();
            return recording;
        } catch (Exception e) {
            Log.e(TAG, "Unable to open LSL stream named: " + lslStream.name(), e);
            return null;
        }
    }

    /**
     * Return the quality of the stream with a given name, if a stream with that name is currently
     * being recorded.
     *
     * @param streamName the name of the stream as advertised by LSL
     * @return the current quality of the named stream; or null if no such stream is being recorded
     */
    public synchronized QualityState getCurrentStreamQuality(String streamName) {
        int index = streamNames.indexOf(streamName);
        if (index < 0 || index >= activeRecordings.size()) {
            return null;
        }
        return activeRecordings.get(index).getCurrentQuality();
    }

    public synchronized double getCurrentSamplingRate(String streamName) {
        int index = streamNames.indexOf(streamName);
        if (index < 0 || index >= activeRecordings.size()) {
            return Double.NaN;
        }
        return activeRecordings.get(index).getCurrentSamplingRate();
    }

    // From https://stackoverflow.com/questions/47531742/startforeground-fail-after-upgrade-to-android-8-1
    // and https://androidwave.com/foreground-service-android-example/
    private void startMyOwnForeground() {
        String NOTIFICATION_CHANNEL_ID = "de.uol.neuropsy.Recorda";
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

        // For Android 14+ (API 34+), we need to specify the foreground service type
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(information_id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(information_id, notification);
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.i(TAG, "Service onBind");
        return new LocalBinder();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service onDestroy called");

        try {
            stopRecordings();

            // Only try to finish XDF if it was initialized
            if (xdfWriter != null) {
                finishXdf();
            } else {
                Log.w(TAG, "Service destroyed before xdfWriter was initialized - no file to save");
            }

            // Forget about recordings after saving
            activeRecordings.clear();
            MainActivity.isComplete = true;

        } catch (Exception e) {
            Log.e(TAG, "Error during service destruction", e);
            // Continue with cleanup even if there's an error
            activeRecordings.clear();
            MainActivity.isComplete = true;
        }
    }

    /**
     * Stop all ongoing asynchronous recordings and wait for them to finish.
     */
    private void stopRecordings() {
        try {
            // Send stop signals to all recordings
            MainActivity.isRunning = false;
            Log.i(TAG, "Stopping " + activeRecordings.size() + " recordings");

            activeRecordings.forEach(recording -> {
                try {
                    recording.stop();
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping recording", e);
                }
            });

            Log.i(TAG, "Sent stop signal. Waiting for recording threads to terminate...");

            activeRecordings.forEach(recording -> {
                try {
                    recording.waitFinished();
                } catch (Exception e) {
                    Log.e(TAG, "Error waiting for recording to finish", e);
                }
            });

            Log.i(TAG, "All recording threads terminated.");
        } catch (Exception e) {
            Log.e(TAG, "Error during stopRecordings", e);
        }
    }

    private void finishXdf() {
        try {
            safeShowToast("Finishing XDF file...", Toast.LENGTH_LONG);

            // Write stream footers for all active recordings
            activeRecordings.forEach(recording -> {
                try {
                    recording.writeStreamFooter();
                } catch (Exception e) {
                    Log.e(TAG, "Error writing stream footer", e);
                }
            });

            // Show completion message with file path
            if (xdfWriter != null) {
                String filePath = xdfWriter.getXdfFilePath();
                if (filePath != null && !filePath.isEmpty()) {
                    safeShowToast("File written at: " + filePath, Toast.LENGTH_LONG);
                    Log.i(TAG, "XDF file written successfully: " + filePath);
                } else {
                    Log.w(TAG, "XDF file path is null or empty");
                }
            } else {
                Log.w(TAG, "xdfWriter is null, cannot get file path");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error finishing XDF file", e);
            safeShowToast("Error saving recording file", Toast.LENGTH_SHORT);
        }
    }

    private static Path freshRecordingFilePath() {
        String isoTime = LocalDateTime.now().toString();
        String fileNameSafeTime = isoTime.replace(':', '-');
        String filename = MainActivity.filenamevalue + "-" + fileNameSafeTime + ".xdf";

        Path path = Environment.getExternalStorageDirectory().toPath().resolve("Download");
        return path.resolve(filename);
    }

    private void createNotificationChannel() {
        String name = "Recorda Channel";
        String description = "Recorda Channel description";
            int importance = NotificationManager.IMPORTANCE_HIGH; //Important for heads-up notification
            NotificationChannel channel = null;
            channel = new NotificationChannel("1", name, importance);
            channel.setDescription(description);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
    }

    private void postStreamQualityNotification(String streamName, QualityState quality) {
        NotificationManager notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel channel = notifyManager.getNotificationChannel(QUALITY_NOTIFICATION_CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(QUALITY_NOTIFICATION_CHANNEL_ID,
                        QUALITY_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("RECORDA stream quality notifications");
                notifyManager.createNotificationChannel(channel);
            }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification =
                new NotificationCompat.Builder(this, QUALITY_NOTIFICATION_CHANNEL_ID)
                        .setContentTitle("LSL recording problem")
                        .setContentText(
                                "Stream is " + quality.displayName + ":\n" +
                                        "\t\u2022 " + streamName)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setTicker("Stream recording problem")
                        .build();

        notifyManager.notify(1007, notification);
    }

    /**
     * Safely show a Toast message, catching DeadObjectException that can occur
     * when the app process is being destroyed.
     */
    private void safeShowToast(String message, int duration) {
        try {
            Toast.makeText(this, message, duration).show();
        } catch (Exception e) {
            // DeadObjectException or other exceptions can occur if the app is being destroyed
            // Log it but don't crash
            Log.w(TAG, "Failed to show toast: " + message, e);
        }
    }


    class LocalBinder extends Binder {
        public LSLService getLSLService() {
            return LSLService.this;
        }

    }
}