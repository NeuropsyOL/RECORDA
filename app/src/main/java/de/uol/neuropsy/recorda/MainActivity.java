package de.uol.neuropsy.recorda;

import static de.uol.neuropsy.recorda.recorder.QualityState.LAGGY;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
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

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import de.uol.neuropsy.recorda.recorder.QualityState;
import de.uol.neuropsy.recorda.util.ResolveStreamsTask;
import edu.ucsd.sccn.LSL;

/**
 * Edited by Sarah Blum on 21/08/2020
 * <p>
 * Changes: file handling adapted, storage location fixed
 */
public class MainActivity extends Activity {

    public static final int COLOR_QUALITY_RED = Color.rgb(210, 25, 25);
    public static final int COLOR_QUALITY_YELLOW = Color.rgb(255, 220, 0);
    public static ListView lv;
    public static List<StreamName> LSLStreamName = new ArrayList<>();
    public static List<StreamName> selectedStreamNames = new ArrayList<>();
    public static boolean writePermission = true;
    public static String filenamevalue;
    public static boolean isComplete = false;
    static TextView tv;
    static volatile boolean isRunning = false;

    private ServiceConnection serviceConnection;
    private volatile LSLService lslService;

    private static final String TAG = "MainActivity";

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

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                lslService = ((LSLService.LocalBinder)service).getLSLService();

            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                lslService = null;
            }
        };

        start.setOnClickListener(new View.OnClickListener() {
            Long tsLong = System.currentTimeMillis() / 1000;
            String ts = tsLong.toString();

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
                    LSL.StreamInfo[] available_streams = LSL.resolve_streams();
                    Boolean allOkay = true;
                    for (StreamName streamName : selectedStreamNames) {
                        if (Arrays.stream(available_streams).anyMatch(s -> s.name().equals(streamName.lslName))){
                            Log.i(TAG,"Found "+streamName.lslName);
                        }
                        else {
                            Log.e(TAG,"Did not find: "+streamName.lslName);
                            allOkay = false;
                            forceUpdateQualityIndicator(streamName.lslName, QualityState.NOT_RESPONDING);
                        }
                    }
                    if (!allOkay) {
                        Toast.makeText(MainActivity.this, "At least one of the streams you selected is no longer available. Refresh and try again.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    lv.setEnabled(false);
                    // make this a foreground service so that android does not kill it while it is in the background
                        myStartForegroundService(intent);

                    bindService(intent, serviceConnection, 0);
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
                if (intent != null && t!= null) {

                    if (lslService != null) {
                        lslService = null;
                        stopService(intent);
                        unbindService(serviceConnection);
                    }
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
                ArrayAdapter<StreamName> adapter = (ArrayAdapter<StreamName>) parent.getAdapter();
                StreamName selectedItem = adapter.getItem (position);

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
        new ResolveStreamsTask().execute(this);
    }

    public void onStreamRefresh(LSL.StreamInfo[] streams) {
        ArrayAdapter<StreamName> adapter = new ArrayAdapter<>(this, R.layout.list_view_text, LSLStreamName);
        lv.setEnabled(true);
        lv.setAdapter(adapter);
        for (LSL.StreamInfo stream1 : streams) {
            adapter.add(new StreamName(stream1));
        }
        for (int i = 0; i < lv.getAdapter().getCount(); i++) {
            lv.setItemChecked(i, true);
        }
        selectedStreamNames.addAll(LSLStreamName);
    }

    private static String streamDisplayNameOf(LSL.StreamInfo streamInfo) {
        String samplingRateAsString = samplingRateAsText(streamInfo.nominal_srate());
        return streamInfo.name() + " (" + samplingRateAsString + ")";
    }

    private static String samplingRateAsText(double rate) {
        if (rate == LSL.IRREGULAR_RATE) {
            return "irreg.";
        }
        if (rate < 10000.0) {
            return (int) rate + " Hz";
        }
        return String.format("%.1f kHz", rate / 1000.0);
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
                                updateStreamQualityIndicators();
                            }
                        });
                    }
                } catch (InterruptedException e) {
                }
            }
        };
        t.start();
    }

    private void forceUpdateQualityIndicator(String lslName, QualityState quality) {
        ArrayAdapter<StreamName> adapter = (ArrayAdapter<StreamName>) lv.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            StreamName stream = adapter.getItem(i);
            if (lslName == stream.lslName) {
                if (i < lv.getFirstVisiblePosition() || i > lv.getLastVisiblePosition()) {
                    Log.d("RECORDA", stream.lslName + " not visible, skipping");
                    return;
                }
                TextView listItem = (TextView) lv.getChildAt(i);
                if (listItem == null)
                    Log.e("RECORDA", "Could not find child: " + stream.lslName + " with id: " + i);
                else {
                    Log.e("RECORDA", "Setting quality for: " + stream.lslName + " with id: " + i);
                    setColorBasedOnQuality(listItem, quality);
                }
            }
        }
    }

    private void updateStreamQualityIndicators() {
        LSLService lsl = lslService;
        if (!isRunning || lsl == null) {
            return;
        }
        for (int i = lv.getFirstVisiblePosition(); i <= lv.getLastVisiblePosition(); i++) {
            StreamName stream = (StreamName) lv.getItemAtPosition(i);
            if (stream == null) {
                Log.w(TAG, "Unexpected: No data item at position " + i);
                continue;
            }
            QualityState quality = lsl.getCurrentStreamQuality(stream.lslName);
            if (quality == null) {
                continue; // that stream is not being recorded
            }
            TextView listItem = (TextView) lv.getChildAt(i - lv.getFirstVisiblePosition());
            if (listItem == null) {
                Log.w(TAG, "Unexpected: No view at index " + i);
                continue;
            }
            Log.i(TAG,"Trying to set stream quality for "+stream.lslName+" got:"+listItem.getText());
            setColorBasedOnQuality(listItem, quality);
            double currentSamplingRate = lsl.getCurrentSamplingRate(stream.lslName);
            setTextBasedOnSamplingRate(listItem, stream, currentSamplingRate);
        }
    }

    private static void setColorBasedOnQuality(TextView view, QualityState q) {
        int backgroundColor =
                q == QualityState.NOT_RESPONDING ? COLOR_QUALITY_RED
                        : q == LAGGY ? COLOR_QUALITY_YELLOW
                        : Color.TRANSPARENT;
        view.setBackgroundColor(backgroundColor);
        int textColor;
        if (Color.luminance(backgroundColor) > 0.5f || backgroundColor == Color.TRANSPARENT) {
            textColor = Color.BLACK;
        } else {
            textColor = Color.WHITE;
        }
        view.setTextColor(textColor);
    }

    private static void setTextBasedOnSamplingRate(TextView view, StreamName stream, double samplingRate) {
        double currentRate = samplingRate;
        String streamNameWithSamplingRate = stream.displayName;
        if (currentRate > 0.0) {
            streamNameWithSamplingRate += " " + samplingRateAsText(currentRate);
        }
        view.setText(streamNameWithSamplingRate);
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
        for (StreamName item : selectedStreamNames) {
            if (selItems == "")
                selItems = item.lslName;
            else
                selItems += "/" + item;
        }
        //Toast.makeText(this, selItems, Toast.LENGTH_LONG).show();
    }

    public void myStartForegroundService(Intent intent) {
        intent.putExtra("inputExtra", "Foreground Service Example in Android");
        ContextCompat.startForegroundService(this, intent);
    }

    /**
     * An LSL stream's name which is used to identify which stream to record, paired with a display
     * string that includes its nominal sampling rate.
     */
    static final class StreamName {
        public final String lslName;
        public final String displayName;

        public StreamName(LSL.StreamInfo streamInfo) {
            lslName = Objects.requireNonNull(streamInfo.name());
            displayName = streamDisplayNameOf(streamInfo);
        }

        @Override
        public String toString() {
            return displayName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StreamName that = (StreamName) o;
            return lslName.equals(that.lslName) && displayName.equals(that.displayName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lslName, displayName);
        }
    }
}