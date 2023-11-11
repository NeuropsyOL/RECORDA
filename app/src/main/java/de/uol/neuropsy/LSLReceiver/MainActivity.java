package de.uol.neuropsy.LSLReceiver;

import static de.uol.neuropsy.LSLReceiver.recorder.QualityState.LAGGY;
import static de.uol.neuropsy.LSLReceiver.recorder.QualityState.OK;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.aliayubkhan.LSLReceiver.R;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import de.uol.neuropsy.LSLReceiver.recorder.QualityState;
import de.uol.neuropsy.LSLReceiver.recorder.RecorderFactory;
import de.uol.neuropsy.LSLReceiver.recorder.StreamQualityListener;
import de.uol.neuropsy.LSLReceiver.recorder.StreamRecorder;
import de.uol.neuropsy.LSLReceiver.recorder.StreamRecording;
import edu.ucsd.sccn.LSL;

/**
 * Edited by Sarah Blum on 21/08/2020
 * <p>
 * Changes: file handling adapted, storage location fixed
 */
public class MainActivity extends Activity {
    public static ListView lv;
    public static List<String> LSLStreamName = new ArrayList<>();
    public static List<String> selectedStreamNames = new ArrayList<>();
    public static boolean writePermission = true;
    public static String filenamevalue;
    public static boolean isComplete = false;
    static TextView tv;
    static volatile boolean isRunning = false;
    private static final String TAG = "MainActivity";

    //Streams
    static LSL.StreamInfo[] streams;

    //Elapsed Time
    //Create placeholder for user's consent to record_audio permission.
    //This will be used in handling callback
    private final int MY_PERMISSIONS_WRITE_LSL = 1;
    public TextView tdate;
    //Initializing Sensor data Variables
    public Thread t;

    public Long startMillis;
    ArrayAdapter<String> adapter;
    Button start, stop;
    List<String> stream;
    ImageView refresh;
    //Settings button
    ImageView settings_button;


    @SuppressLint({"SetTextI18n", "ClickableViewAccessibility"})
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv = (TextView) findViewById(R.id.tv);
        start = (Button) findViewById(R.id.startLSL);
        stop = (Button) findViewById(R.id.stopLSL);
        refresh = (ImageButton) findViewById(R.id.refreshStreams);
        tdate = (TextView) findViewById(R.id.elapsedTime);
        requestWritePermissions();
        // set filename so that is not null, it gets changed if the user enters settings screen
        filenamevalue = "recording";
        lv = (ListView) findViewById(R.id.streams);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        settings_button = (ImageView) findViewById(R.id.settings_btn);
        settings_button.setVisibility(View.VISIBLE);

        final Intent intent = new Intent(this, LSLService.class);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        start.setOnClickListener(new View.OnClickListener() {
            Long tsLong = System.currentTimeMillis() / 1000;
            String ts = tsLong.toString();

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View v) {
                if (selectedStreamNames.isEmpty()) {
                    return;
                }
                if (!isRunning) {
                    if (!writePermission) {
                        requestWritePermissions();
                        //Log.i("Path", path);
                    }

                    if (StringUtils.isEmpty(filenamevalue)) {
                        filenamevalue = "recording";
                    }
                    lv.setEnabled(false);
                    // make this a foreground service so that android does not kill it while it is in the background
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        myStartForegroundService(intent);
                    } else { // try our best with older Androids
                        startService(intent);
                    }
                    startMillis = System.currentTimeMillis();
                    tdate.setText("00:00");
                    ElapsedTime();
                }
            }
        });

        refresh.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        Toast.makeText(MainActivity.this, "Refreshing Streams...",
                                Toast.LENGTH_LONG).show();
                        ImageButton view = (ImageButton) v;
                        view.getBackground().setColorFilter(0x77000000, PorterDuff.Mode.SRC_ATOP);
                        view.invalidate();
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                        RefreshStreams();

                    case MotionEvent.ACTION_CANCEL: {
                        ImageButton view = (ImageButton) v;
                        view.getBackground().clearColorFilter();
                        view.invalidate();
                        break;
                    }
                }

                return true;
            }
        });

        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (intent != null && t!=null) {
                    stopService(intent);
                    t.interrupt();
                    lv.setEnabled(true);
                }
            }
        });

        settings_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });

        tv.setText("Available Streams: ");
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // selected item
                String selectedItem = ((TextView) view).getText().toString();
                if (selectedStreamNames.contains(selectedItem))
                    selectedStreamNames.remove(selectedItem); //remove deselected item from the list of selected items
                else
                    selectedStreamNames.add(selectedItem); //add selected item to the list of selected items
                showSelectedItems();
            }
        });
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        assert powerManager != null;
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LSLRec:MyWakelockTag");
        wakeLock.acquire();
    }


    public static String getElapsedTimeMinutesSecondsString(Long miliseconds) {
        Long elapsedTime = miliseconds;
        @SuppressLint("DefaultLocale") String format = String.format("%%0%dd", 2);
        String seconds = String.format(format, (elapsedTime / 1000) % 60);
        String minutes = String.format(format, ((elapsedTime / (1000 * 60)) % 60));
        String hours = String.format(format, ((elapsedTime / (1000 * 60 * 60)) % 24));
        return hours + ":" + minutes + ":" + seconds;
    }

    public void RefreshStreams() {
        selectedStreamNames.clear();
        LSLStreamName.clear();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_view_text, LSLStreamName);
        lv.setEnabled(true);
        lv.setAdapter(adapter);
        streams = LSL.resolve_streams();
        // start fake xdfwriter to check stream quality
        spawnListeningToStreamsWithoutRecording();
        for (LSL.StreamInfo stream1 : streams) {
            adapter.add(stream1.name());
        }
        for (int i = 0; i < lv.getAdapter().getCount(); i++) {
            lv.setItemChecked(i, true);
        }
        selectedStreamNames.addAll(LSLStreamName);
    }

    private void spawnListeningToStreamsWithoutRecording() {
        int xdfStreamIndex = 0;
        List<StreamRecording> activeRecordings = new ArrayList<>();
        for (LSL.StreamInfo availableStream : streams) {
            StreamRecording rec = prepareRecording(availableStream, xdfStreamIndex++);
            if (rec != null) {
                rec.registerQualityListener((QualityState streamQualityNow) -> {
                    indicateQualityInUi(availableStream.name(), streamQualityNow);
                });
                activeRecordings.add(rec);
            } else {
                // TODO mark stream as bad (quality)
            }
        }
        activeRecordings.forEach(StreamRecording::spawnRecorderThread);
    }

    private void indicateQualityInUi(String name, QualityState streamQualityNow) {
        View streamListItem = lv.getChildAt(LSLStreamName.indexOf(name));
        streamListItem.setBackgroundColor(streamQualityNow == OK ? Color.GREEN :
                streamQualityNow == LAGGY ? Color.YELLOW : Color.RED);
    }

    private StreamRecording prepareRecording(LSL.StreamInfo lslStream, int xdfStreamIndex) {
        try {
            RecorderFactory f = RecorderFactory.forLslStream(lslStream);
            StreamRecorder sourceStream = f.openInlet();
            StreamRecording recording = new StreamRecording(sourceStream, null, xdfStreamIndex);
            return recording;
        } catch (Exception e) {
            Log.e(TAG, "Unable to open LSL stream named: " + lslStream.name(), e);
            return null;
        }
    }

    public void ElapsedTime() {
        t = new Thread() {
            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(1000);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                long now = System.currentTimeMillis();
                                long difference = now - startMillis;
                                tdate.setText(getElapsedTimeMinutesSecondsString(difference));
                            }
                        });
                    }
                } catch (InterruptedException e) {
                }
            }
        };
        t.start();
    }

    /*
    We do not need to ask the user for write permissions for every single folder, since the app is
    prompting the user in the beginning for writing access to external storage.
     */
    private void requestWritePermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "Please grant permissions to write XDF file",
                        Toast.LENGTH_LONG).show();
                writePermission = false;

                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_WRITE_LSL);

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_WRITE_LSL);
                filenamevalue = "hardcoded.xdf";
            }
        }
    }

    //Handling callback
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_WRITE_LSL: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    writePermission = true;
                    // permission was granted, yay!
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    writePermission = false;
                }
            }
        }
    }

    private void showSelectedItems() {
        String selItems = "";
        for (String item : selectedStreamNames) {
            if (selItems == "")
                selItems = item;
            else
                selItems += "/" + item;
        }
        //Toast.makeText(this, selItems, Toast.LENGTH_LONG).show();
    }

    public void myStartForegroundService(Intent intent) {
        intent.putExtra("inputExtra", "Foreground Service Example in Android");
        ContextCompat.startForegroundService(this, intent);
    }

}

