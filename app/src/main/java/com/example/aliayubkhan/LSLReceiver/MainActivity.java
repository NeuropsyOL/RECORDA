package com.example.aliayubkhan.LSLReceiver;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

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
    Button start, stop;
    ImageView refresh;
    //Settings button
    ImageView settings_button;

    public static String getElapsedTimeMinutesSecondsString(Long miliseconds) {
        @SuppressLint("DefaultLocale") String format = String.format("%%0%dd", 2);
        String seconds = String.format(format, (miliseconds / 1000) % 60);
        String minutes = String.format(format, ((miliseconds / (1000 * 60)) % 60));
        String hours = String.format(format, ((miliseconds / (1000 * 60 * 60)) % 24));
        return hours + ":" + minutes + ":" + seconds;
    }

    @SuppressLint({"SetTextI18n", "ClickableViewAccessibility"})
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv = findViewById(R.id.tv);
        start = findViewById(R.id.startLSL);
        stop = findViewById(R.id.stopLSL);
        refresh = findViewById(R.id.refreshStreams);
        tdate = findViewById(R.id.elapsedTime);
        requestWritePermissions();
        // set filename so that is not null, it gets changed if the user enters settings screen
        filenamevalue = "recording";
        lv = findViewById(R.id.streams);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        settings_button = findViewById(R.id.settings_btn);
        settings_button.setVisibility(View.VISIBLE);

        final Intent intent = new Intent(this, LSLService.class);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        start.setOnClickListener(v -> {
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
        });

        refresh.setOnTouchListener((v, event) -> {
            ImageButton view;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Toast.makeText(MainActivity.this, "Refreshing Streams...",
                            Toast.LENGTH_LONG).show();
                    view = (ImageButton) v;
                    view.getBackground().setColorFilter(0x77000000, PorterDuff.Mode.SRC_ATOP);
                    view.invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    RefreshStreams();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    view = (ImageButton) v;
                    view.getBackground().clearColorFilter();
                    view.invalidate();
                    break;
            }
            return true;
        });

        stop.setOnClickListener(v -> {
            stopService(intent);
            if (t != null) t.interrupt();
            lv.setEnabled(true);
        });

        settings_button.setOnClickListener(v -> {
            Intent intent1 = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent1);
        });

        tv.setText("Available Streams: ");
        lv.setOnItemClickListener((parent, view, position, id) -> {
            // selected item
            String selectedItem = ((TextView) view).getText().toString();
            if (selectedStreamNames.contains(selectedItem))
                selectedStreamNames.remove(selectedItem); //remove deselected item from the list of selected items
            else
                selectedStreamNames.add(selectedItem); //add selected item to the list of selected items
            showSelectedItems();
        });
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        assert powerManager != null;
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LSLRec:MyWakelockTag");
        wakeLock.acquire();
    }

    public void RefreshStreams() {
        selectedStreamNames.clear();
        LSLStreamName.clear();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.list_view_text, LSLStreamName);
        lv.setEnabled(true);
        lv.setAdapter(adapter);
        streams = LSL.resolve_streams();
        for (LSL.StreamInfo stream1 : streams) {
            adapter.add(stream1.name());
        }
        for (int i = 0; i < lv.getAdapter().getCount(); i++) {
            lv.setItemChecked(i, true);
        }
        selectedStreamNames.addAll(LSLStreamName);
    }

    public void ElapsedTime() {
        t = new Thread() {
            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(1000);
                        runOnUiThread(() -> {
                            long now = System.currentTimeMillis();
                            long difference = now - startMillis;
                            tdate.setText(getElapsedTimeMinutesSecondsString(difference));
                        });
                    }
                } catch (InterruptedException ignored) {
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
                                           String[] permissions, int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_WRITE_LSL) {
            // permission was granted, yay!
            // permission denied, boo! Disable the
            // functionality that depends on this permission.
            writePermission = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void showSelectedItems() {
        StringBuilder selItems = new StringBuilder();
        for (String item : selectedStreamNames) {
            if (selItems.toString().equals(""))
                selItems = new StringBuilder(item);
            else
                selItems.append("/").append(item);
        }
        //Toast.makeText(this, selItems, Toast.LENGTH_LONG).show();
    }

    public void myStartForegroundService(Intent intent) {
        intent.putExtra("inputExtra", "Foreground Service Example in Android");
        ContextCompat.startForegroundService(this, intent);
    }

}

