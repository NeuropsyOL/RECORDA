package de.uol.neuropsy.recorda;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import de.uol.neuropsy.recorda.recorder.QualityMetrics;
import de.uol.neuropsy.recorda.recorder.QualityState;
import de.uol.neuropsy.recorda.recorder.RecorderFactory;
import de.uol.neuropsy.recorda.recorder.StreamQualityListener;
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

    private List<StreamRecording> activeRecordings = new ArrayList<>();
    private XdfWriter xdfWriter;

    private final boolean recordTimingOffsets = true;

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        Log.i(TAG, "Service onStartCommand");
        MainActivity.isRunning = true;
        Toast.makeText(this,"Recording LSL!", Toast.LENGTH_SHORT).show();

        // this method is part of the mechanisms that allow this to be a foreground channel
        createNotificationChannel();

        xdfWriter = new XdfWriter();
        Path xdfFilePath = freshRecordingFilePath();
        xdfWriter.setXdfFilePath(xdfFilePath);

        // resolve all streams that are in the network
        LSL.StreamInfo[] lslStreams = LSL.resolve_streams();

        Collection<String> selectedLslStreams = MainActivity.selectedStreamNames.stream()
                .map(stream -> stream.lslName)
                .collect(Collectors.toSet());

        int xdfStreamIndex = 0;
        for (LSL.StreamInfo availableStream : lslStreams) {
            boolean isSelectedToBeRecorded = selectedLslStreams.contains(availableStream.name());
            if (isSelectedToBeRecorded) {
                StreamRecording rec = prepareRecording(availableStream, xdfStreamIndex++);
                if (rec != null) {
                    activeRecordings.add(rec);
                }
            }
        }
        activeRecordings.forEach(streamRecording -> {
            streamRecording.registerQualityListener((int streamIndex, QualityMetrics q) -> {
                /*
                 * TODO
                 *
                 * Call back into MainActivity in order to update the UI.
                 *
                 * In class MainActivity, switch to the UI thread (e.g. by calling method
                 * Activity::runOnUiThread). This listener is called from the recording thread which
                 * must not be blocked by the UI.
                 */
                Log.i(TAG, "Stream " + streamIndex + " srate: " + q.getCurrentSamplingRate() + " q: " + q.getCurrentQuality());
            });
            streamRecording.spawnRecorderThread();
        });

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

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static Path freshRecordingFilePath() {
        String isoTime = LocalDateTime.now().toString();
        String fileNameSafeTime = isoTime.replace(':', '-');
        String filename = MainActivity.filenamevalue + "-" + fileNameSafeTime + ".xdf";

        Path path = Environment.getExternalStorageDirectory().toPath().resolve("Download");
        return path.resolve(filename);
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