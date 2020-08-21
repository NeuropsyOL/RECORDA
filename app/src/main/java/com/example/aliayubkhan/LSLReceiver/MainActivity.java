package com.example.aliayubkhan.LSLReceiver;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity
{
    static TextView tv;
    public static ListView lv;

    ArrayAdapter<String> adapter;
    Button start, stop, Reset;

    static boolean isRunning  = false;
    static int i = 0;
    static boolean checkFlag = false;

    List<String> stream;
    public static List<String> LSLStreamName = new ArrayList<String>();
    public static ArrayList<String> selectedItems=new ArrayList<String>();

    ImageView refresh;


    //Requesting run-time permissions

    //Create placeholder for user's consent to record_audio permission.
    //This will be used in handling callback
    private final int MY_PERMISSIONS_WRITE_LSL = 1;

    public static boolean writePermission = true;

    //Elapsed Time

    public TextView tdate;

    public Thread t;
    //Initializing Sensor data Variables

    public Long startMillis;

    //filename

    public static String filenamevalue;

    //Settings button
    ImageView settings_button;


    //Streams
    static LSL.StreamInfo[] streams;

    public static String path;

    public static boolean isComplete = false;
    public static boolean isAlreadyExecuted = false;

    /** Called when the activity is first created. */
    @SuppressLint({"SetTextI18n", "ClickableViewAccessibility"})
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
//        tv = new TextView(this);
        setContentView(R.layout.activity_main);
        tv = (TextView)findViewById(R.id.tv);
        start = (Button)findViewById(R.id.startLSL);
        stop = (Button)findViewById(R.id.stopLSL);
        refresh = (ImageButton) findViewById(R.id.refreshStreams);
        tdate = (TextView) findViewById(R.id.elapsedTime);
        //requestWritePermissions();
        filenamevalue = "default.xdf"; // gets changed if the user enters settings screen
        path = Environment.getExternalStorageDirectory() + "/Download/";
        lv = (ListView) findViewById (R.id.streams);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        settings_button = (ImageView) findViewById(R.id.settings_btn);
        settings_button.setVisibility(View.VISIBLE);

        Reset = (Button)findViewById(R.id.reset_button);

        final Intent intent = new Intent(this, LSLService.class);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        start.setOnClickListener(new View.OnClickListener() {

            Long tsLong = System.currentTimeMillis()/1000;
            String ts = tsLong.toString();

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View v) {
                if(!isRunning){
                    if(!writePermission){
                        requestWritePermissions();
                        //Log.i("Path", path);
                    }

                    if(path != null){

                        if(!isAlreadyExecuted){
                            startService(intent);
                            startMillis = System.currentTimeMillis();
                            tdate.setText("00:00");
                            ElapsedTime();

                            System.out.println(path);
                        } else {
                            Toast.makeText(MainActivity.this, "Close existing inlets first!", Toast.LENGTH_LONG).show();
                        }

                    } else {
                        //Toast.makeText(MainActivity.this, "Filepath not chosen!", Toast.LENGTH_LONG).show();
                        Toast.makeText(MainActivity.this, "Filepath invalid: "+ path, Toast.LENGTH_LONG).show();
                    }


                    //startAsyncTask(ts);
                }

            }
        });

        refresh.setOnTouchListener(new View.OnTouchListener() {


            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        Toast.makeText(MainActivity.this, "Refreshing Streams...", Toast.LENGTH_LONG).show();
                        ImageButton view = (ImageButton ) v;
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
                stopService(intent);
                t.interrupt();

            }
        });

        settings_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });

        Reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isComplete){
                    restart(0);
                }

            }
        });

        tv.setText("Available Streams: ");



        lv.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // selected item
                String selectedItem = ((TextView) view).getText().toString();
                if(selectedItems.contains(selectedItem))
                    selectedItems.remove(selectedItem); //remove deselected item from the list of selected items
                else
                    selectedItems.add(selectedItem); //add selected item to the list of selected items
                showSelectedItems();
            }

        });

    }

    public void RefreshStreams(){

        streams = LSL.resolve_streams();
        LSLStreamName.clear();
        lv.setAdapter(new ArrayAdapter<String>(this,R.layout.list_view_text , LSLStreamName));


        for (LSL.StreamInfo stream1 : streams) {
            LSLStreamName.add(stream1.name());
        }
        lv.setAdapter(new ArrayAdapter<String>(this,R.layout.list_view_text , LSLStreamName));

    }



    public void ElapsedTime(){
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

    public static String getElapsedTimeMinutesSecondsString(Long miliseconds) {
        Long elapsedTime = miliseconds;
        @SuppressLint("DefaultLocale") String format = String.format("%%0%dd", 2);
//        elapsedTime = elapsedTime / 1000;

        String seconds = String.format(format, (elapsedTime / 1000) % 60 );
        String minutes = String.format(format, ((elapsedTime / (1000*60)) % 60));
        String hours = String.format(format, ((elapsedTime / (1000*60*60)) % 24));
        return hours + ":" + minutes + ":" + seconds;
    }


    private void requestWritePermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "Please grant permissions to write LSL Stream", Toast.LENGTH_LONG).show();
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
                path = Environment.getExternalStorageDirectory() + "/Download/";
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
        String selItems="";
        for(String item:selectedItems){
            if(selItems=="")
                selItems=item;
            else
                selItems+="/"+item;
        }
        //Toast.makeText(this, selItems, Toast.LENGTH_LONG).show();
    }

    public void restart(int delay) {
        selectedItems.clear();
        isComplete = false;
        Intent launchIntent = new Intent(this, MainActivity.class);
        PendingIntent intent = PendingIntent.getActivity(getApplicationContext(), 0, launchIntent , 0);
        AlarmManager manager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        manager.set(AlarmManager.RTC, System.currentTimeMillis() + delay, intent);
        System.exit(2);
    }

    public static void showText(String s){
        tv.setText(s);

    }

}

