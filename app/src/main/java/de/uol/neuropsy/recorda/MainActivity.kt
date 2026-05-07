package de.uol.neuropsy.recorda

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowManager
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import de.uol.neuropsy.recorda.LSLService.LocalBinder
import de.uol.neuropsy.recorda.recorder.QualityState
import de.uol.neuropsy.recorda.util.ResolveStreamsTask
import edu.ucsd.sccn.LSL
import edu.ucsd.sccn.LSL.StreamInfo
import org.apache.commons.lang3.StringUtils
import java.util.Arrays
import java.util.Objects

/**
 * Edited by Sarah Blum on 21/08/2020
 *
 *
 * Changes: file handling adapted, storage location fixed
 */
class MainActivity : AppCompatActivity() {
    private var serviceConnection: ServiceConnection? = null

    @Volatile
    private var lslService: LSLService? = null

    //Elapsed Time
    //Create placeholder for user's consent to record_audio permission.
    //This will be used in handling callback
    private val MY_PERMISSIONS_WRITE_LSL = 1
    var tdate: TextView? = null

    //Initializing Sensor data Variables
    var t: Thread? = null
    var startMillis: Long? = null
    var adapter: ArrayAdapter<String>? = null
    var start: Button? = null
    var stop: Button? = null
    var stream: List<String>? = null
    var refresh: ImageView? = null

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Wire the toolbar as the action bar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        tv = findViewById<View>(R.id.tv) as TextView
        start = findViewById<View>(R.id.startLSL) as Button
        stop = findViewById<View>(R.id.stopLSL) as Button
        refresh = findViewById<View>(R.id.refreshStreams) as ImageButton
        tdate = findViewById<View>(R.id.elapsedTime) as TextView
        requestWritePermissions()
        // set filename so that is not null, it gets changed if the user enters settings screen
        filenamevalue = "recording"
        lv = findViewById<View>(R.id.streams) as ListView
        lv!!.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        lv!!.emptyView = findViewById(R.id.emptyStreamsText)
        val intent = Intent(this, LSLService::class.java)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                lslService = (service as LocalBinder).lslService
            }

            override fun onServiceDisconnected(name: ComponentName) {
                lslService = null
            }
        }
        start!!.setOnClickListener(object : View.OnClickListener {
            var tsLong = System.currentTimeMillis() / 1000
            var ts = tsLong.toString()
            override fun onClick(v: View) {
                if (selectedStreamNames.isEmpty()) {
                    return
                }
                if (!isRunning) {
                    if (!writePermission) {
                        requestWritePermissions()
                        //Log.i("Path", path);
                    }
                    if (StringUtils.isEmpty(filenamevalue)) {
                        filenamevalue = "recording"
                    }
                    val available_streams = LSL.resolve_streams()
                    var allOkay = true
                    for (streamName in selectedStreamNames) {
                        if (Arrays.stream<StreamInfo>(available_streams)
                                .anyMatch { s: StreamInfo -> s.name() == streamName!!.lslName }
                        ) {
                            Log.i(TAG, "Found " + streamName!!.lslName)
                        } else {
                            Log.e(TAG, "Did not find: " + streamName!!.lslName)
                            allOkay = false
                            forceUpdateQualityIndicator(
                                streamName.lslName,
                                QualityState.NOT_RESPONDING
                            )
                        }
                    }
                    if (!allOkay) {
                        safeShowToast(
                            "At least one of the streams you selected is no longer available. Refresh and try again.",
                            Toast.LENGTH_LONG
                        )
                        return
                    }
                    lv!!.isEnabled = false
                    // make this a foreground service so that android does not kill it while it is in the background
                    myStartForegroundService(intent)
                    bindService(intent, serviceConnection!!, 0)
                    startMillis = System.currentTimeMillis()
                    tdate!!.text = "00:00"
                    ElapsedTime()
                    updateButtonStates(recording = true)
                }
            }
        })
        refresh!!.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        safeShowToast(
                            "Refreshing Streams...",
                            Toast.LENGTH_LONG
                        )
                        val view = v as ImageButton
                        view.background.setColorFilter(0x77000000, PorterDuff.Mode.SRC_ATOP)
                        view.invalidate()
                    }

                    MotionEvent.ACTION_UP -> {
                        RefreshStreams()
                        run {
                            val view = v as ImageButton
                            view.background.clearColorFilter()
                            view.invalidate()
                        }
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        val view = v as ImageButton
                        view.background.clearColorFilter()
                        view.invalidate()
                    }
                }
                return true
            }
        })
        stop!!.setOnClickListener {
            if (intent != null && t != null) {
                if (lslService != null) {
                    lslService = null
                    stopService(intent)
                    unbindService(serviceConnection!!)
                }
                t!!.interrupt()
                lv!!.isEnabled = true
                updateButtonStates(recording = false)
            }
        }
        tv!!.text = "Available Streams: "
        lv!!.onItemClickListener =
            OnItemClickListener { parent, view, position, id ->
                val adapter = parent.adapter as ArrayAdapter<StreamName>
                val selectedItem = adapter.getItem(position)
                if (selectedStreamNames.contains(selectedItem)) selectedStreamNames.remove(selectedItem)
                else selectedStreamNames.add(selectedItem)
                showSelectedItems()
            }


        val powerManager = (getSystemService(POWER_SERVICE) as PowerManager)
        val wakeLock =
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LSLRec:MyWakelockTag")
        wakeLock.acquire()
    }

    /** Swaps button colours to highlight the next meaningful action. */
    private fun updateButtonStates(recording: Boolean) {
        val primary = ContextCompat.getDrawable(this, R.drawable.shape_primary)
        val secondary = ContextCompat.getDrawable(this, R.drawable.shape)
        if (recording) {
            start!!.background = secondary   // grey — not the active action
            stop!!.background  = primary     // blue  — next action
        } else {
            start!!.background = primary     // blue  — next action
            stop!!.background  = secondary   // grey  — not the active action
        }
    }

    fun RefreshStreams() {
        LSLStreamName.clear()
        ResolveStreamsTask().execute(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_tutorial -> {
                startActivity(Intent(this, TutorialActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun onStreamRefresh(streams: Array<StreamInfo>) {
        val adapter = ArrayAdapter(this, R.layout.list_view_text, LSLStreamName)
        lv!!.isEnabled = true
        lv!!.adapter = adapter
        for (stream1 in streams) {
            adapter.add(StreamName(stream1))
        }
        for (i in 0 until lv!!.adapter.count) {
            lv!!.setItemChecked(i, true)
        }
        selectedStreamNames.addAll(LSLStreamName)
    }

    fun ElapsedTime() {
        t = object : Thread() {
            override fun run() {
                try {
                    while (!isInterrupted) {
                        sleep(1000)
                        runOnUiThread {
                            val now = System.currentTimeMillis()
                            val difference = now - startMillis!!
                            tdate!!.text = getElapsedTimeMinutesSecondsString(difference)
                            updateStreamQualityIndicators()
                        }
                    }
                } catch (e: InterruptedException) {
                }
            }
        }
        t!!.start()
    }

    private fun forceUpdateQualityIndicator(lslName: String, quality: QualityState) {
        val adapter = lv!!.adapter as ArrayAdapter<StreamName>
        for (i in 0 until adapter.count) {
            val stream = adapter.getItem(i)
            if (lslName === stream!!.lslName) {
                if (i < lv!!.firstVisiblePosition || i > lv!!.lastVisiblePosition) {
                    Log.d("RECORDA", stream!!.lslName + " not visible, skipping")
                    return
                }
                val listItem = lv!!.getChildAt(i) as TextView
                if (listItem == null) Log.e(
                    "RECORDA",
                    "Could not find child: " + stream!!.lslName + " with id: " + i
                ) else {
                    Log.e("RECORDA", "Setting quality for: " + stream!!.lslName + " with id: " + i)
                    setColorBasedOnQuality(this, listItem, quality)
                }
            }
        }
    }

    private fun updateStreamQualityIndicators() {
        val lsl = lslService
        if (!isRunning || lsl == null) {
            return
        }
        for (i in lv!!.firstVisiblePosition..lv!!.lastVisiblePosition) {
            val stream = lv!!.getItemAtPosition(i) as StreamName
            if (stream == null) {
                Log.w(TAG, "Unexpected: No data item at position $i")
                continue
            }
            val quality = lsl.getCurrentStreamQuality(stream.lslName)
                ?: continue  // that stream is not being recorded
            val listItem = lv!!.getChildAt(i - lv!!.firstVisiblePosition) as TextView
            if (listItem == null) {
                Log.w(TAG, "Unexpected: No view at index $i")
                continue
            }
            Log.i(
                TAG,
                "Trying to set stream quality for " + stream.lslName + " got:" + listItem.text
            )
            setColorBasedOnQuality(this, listItem, quality)
            val currentSamplingRate = lsl.getCurrentSamplingRate(stream.lslName)
            setTextBasedOnSamplingRate(listItem, stream, currentSamplingRate)
        }
    }

    /*
    We do not need to ask the user for write permissions for every single folder, since the app is
    prompting the user in the beginning for writing access to external storage.
     */
    private fun requestWritePermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {
                safeShowToast(
                    "Please grant permissions to write XDF file",
                    Toast.LENGTH_LONG
                )
                writePermission = false

                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    MY_PERMISSIONS_WRITE_LSL
                )
            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    MY_PERMISSIONS_WRITE_LSL
                )
                filenamevalue = "recording"
            }
        }
    }

    //Handling callback
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            MY_PERMISSIONS_WRITE_LSL -> {
                if (grantResults.size > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    writePermission = true
                    // permission was granted, yay!
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    writePermission = false
                }
            }
        }
    }

    private fun showSelectedItems() {
        var selItems = ""
        for (item in selectedStreamNames) {
            if (selItems === "") selItems = item!!.lslName else selItems += "/$item"
        }
        //Toast.makeText(this, selItems, Toast.LENGTH_LONG).show();
    }

    fun myStartForegroundService(intent: Intent?) {
        intent!!.putExtra("inputExtra", "Foreground Service Example in Android")
        ContextCompat.startForegroundService(this, intent)
    }

    /**
     * An LSL stream's name which is used to identify which stream to record, paired with a display
     * string that includes its nominal sampling rate.
     */
    class StreamName(streamInfo: StreamInfo) {
        @JvmField
        val lslName: String
        val displayName: String

        init {
            lslName = Objects.requireNonNull(streamInfo.name())
            displayName = streamDisplayNameOf(streamInfo)
        }

        override fun toString(): String {
            return displayName
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || javaClass != o.javaClass) return false
            val that = o as StreamName
            return lslName == that.lslName && displayName == that.displayName
        }

        override fun hashCode(): Int {
            return Objects.hash(lslName, displayName)
        }
    }

    /**
     * Safely show a Toast message, catching DeadObjectException that can occur
     * when the app process is being destroyed.
     */
    private fun safeShowToast(message: String, duration: Int) {
        try {
            Toast.makeText(this, message, duration).show()
        } catch (e: Exception) {
            // DeadObjectException or other exceptions can occur if the app is being destroyed
            // Log it but don't crash
            Log.w(TAG, "Failed to show toast: $message", e)
        }
    }

    companion object {
        var lv: ListView? = null
        var LSLStreamName: MutableList<StreamName?> = ArrayList()
        @JvmField
        var selectedStreamNames: MutableList<StreamName?> = ArrayList()
        var writePermission = true
        @JvmField
        var filenamevalue: String? = null
        @JvmField
        var saveFolderPath: String? = null   // user-chosen save directory, null = default Downloads
        @JvmField
        var isComplete = false
        var tv: TextView? = null

        @JvmField
        @Volatile
        var isRunning = false
        private const val TAG = "MainActivity"
        fun getElapsedTimeMinutesSecondsString(miliseconds: Long): String {
            @SuppressLint("DefaultLocale") val format = String.format("%%0%dd", 2)
            val seconds = String.format(format, miliseconds / 1000 % 60)
            val minutes = String.format(format, miliseconds / (1000 * 60) % 60)
            val hours = String.format(format, miliseconds / (1000 * 60 * 60) % 24)
            return "$hours:$minutes:$seconds"
        }

        private fun streamDisplayNameOf(streamInfo: StreamInfo): String {
            val samplingRateAsString = samplingRateAsText(streamInfo.nominal_srate())
            return streamInfo.name() + " (" + samplingRateAsString + ")"
        }

        private fun samplingRateAsText(rate: Double): String {
            if (rate == LSL.IRREGULAR_RATE) {
                return "irreg."
            }
            return if (rate < 10000.0) {
                rate.toInt().toString() + " Hz"
            } else String.format("%.1f kHz", rate / 1000.0)
        }

        private fun setColorBasedOnQuality(context: AppCompatActivity, view: TextView, q: QualityState) {
            val colorRed = ContextCompat.getColor(context, R.color.color_quality_red)
            val colorYellow = ContextCompat.getColor(context, R.color.color_quality_yellow)
            val backgroundColor =
                if (q == QualityState.NOT_RESPONDING) colorRed
                else if (q == QualityState.LAGGY) colorYellow
                else Color.TRANSPARENT
            view.setBackgroundColor(backgroundColor)
            val textColor =
                if (Color.luminance(backgroundColor) > 0.5f || backgroundColor == Color.TRANSPARENT) {
                    ContextCompat.getColor(context, R.color.stream_list_text)
                } else {
                    Color.WHITE
                }
            view.setTextColor(textColor)
        }

        private fun setTextBasedOnSamplingRate(
            view: TextView,
            stream: StreamName,
            samplingRate: Double
        ) {
            var streamNameWithSamplingRate = stream.displayName
            if (samplingRate > 0.0) {
                streamNameWithSamplingRate += " " + samplingRateAsText(
                    samplingRate
                )
            }
            view.text = streamNameWithSamplingRate
        }
    }
}