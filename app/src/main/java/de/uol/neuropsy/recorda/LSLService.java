package de.uol.neuropsy.recorda;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

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
        Toast.makeText(this,"Recording LSL!", Toast.LENGTH_SHORT).show();

        // this method is part of the mechanisms that allow this to be a foreground service
        // a notification channel must also registered in the system before we can send notifications
        // to the user
        createNotificationChannel();

        xdfWriter = new XdfWriter();
        Path xdfFilePath = freshRecordingFilePath();;
        xdfWriter.setXdfFilePath(xdfFilePath);

        // resolve all streams that are in the network
        LSL.StreamInfo[] lslStreams = LSL.resolve_streams();

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
            Toast.makeText(this, "LSL Recorder can safely run in background!", Toast.LENGTH_LONG).show();
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
        startForeground(information_id, notification);
    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.i(TAG, "Service onBind");
        return new LocalBinder();
    }

    @Override
    public void onDestroy() {
        stopRecordings();
        finishXdf();

        // Forget about recordings after saving
        activeRecordings.clear();
        MainActivity.isComplete = true;
    }

    /**
     * Stop all ongoing asynchronous recordings and wait for them to finish.
     */
    private void stopRecordings() {
        // Send stop signals to all recordings
        MainActivity.isRunning = false;
        activeRecordings.forEach(StreamRecording::stop);
        Log.i(TAG, "Sent stop signal. Waiting for recording threads to terminate...");
        activeRecordings.forEach(StreamRecording::waitFinished);
        Log.i(TAG, "All recording threads terminated.");
    }

    private void finishXdf() {
        Toast.makeText(this, "Finishing XDF file...", Toast.LENGTH_LONG).show();
        activeRecordings.forEach(StreamRecording::writeStreamFooter);
        Toast.makeText(this, "File written at: " + xdfWriter.getXdfFilePath(), Toast.LENGTH_LONG).show();
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


    class LocalBinder extends Binder {
        public LSLService getLSLService() {
            return LSLService.this;
        }

    }
}