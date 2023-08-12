package com.example.aliayubkhan.LSLReceiver.recorder;

import android.util.Log;

import com.example.aliayubkhan.LSLReceiver.LSL;
import com.example.aliayubkhan.LSLReceiver.xdf.XdfWriter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * An ongoing asynchronous recording of one LSL stream into an XDF file.
 * <p>
 * The XDF file may be shared with parallel recordings of other streams.
 */
public class StreamRecording {

    private static final String TAG = "StreamRecording";

    /**
     * Milliseconds between consecutive measurements of all stream's timing offsets.
     */
    private static final int OFFSET_MEASURE_INTERVAL = 5000;

    /**
     * Milliseconds between flushing current recording buffer to XDF file.
     */
    private static final int XDF_WRITE_INTERVAL = 500;

    private final StreamRecorder streamRecorder;
    private final XdfWriter xdfWriter;
    private final int xdfStreamIndex;

    private boolean recordTimingOffsets = true;

    private Thread sampleThread;
    private Thread timingOffsetThread;
    private volatile boolean isRunning;

    public StreamRecording(StreamRecorder sourceToRecord, XdfWriter xdfWriter, int xdfStreamIndex) {
        Objects.requireNonNull(xdfWriter);
        Objects.requireNonNull(sourceToRecord);
        this.streamRecorder = sourceToRecord;
        this.xdfStreamIndex = xdfStreamIndex;
        this.xdfWriter = xdfWriter;
    }

    public void spawnRecorderThread() {
        if (isRunning || sampleThread != null) {
            throw new IllegalStateException("Already recording.");
        }
        isRunning = true;
        sampleThread = new Thread(() -> sampleRecordingLoop(streamRecorder));
        sampleThread.start();
        if (recordTimingOffsets) {
            timingOffsetThread = new Thread(() -> timingOffsetLoop(streamRecorder));
            timingOffsetThread.start();
        }
    }

    private void sampleRecordingLoop(StreamRecorder streamRecorder) {
        while (isRunning) {
            try {
                int samples = streamRecorder.pullChunk();
                if (samples > 0) {
                    Log.d(TAG, "Stream " + xdfStreamIndex + ": Pulled " + samples + " values");
                    writeAllRecordedSamples();
                    long size = Files.size(Paths.get(xdfWriter.getXdfFilePath()));
                    Log.d(TAG, "XDF file size now: " + size + " bytes");
                } else {
                    Log.d(TAG, "Stream " + xdfStreamIndex + ": No samples. Waiting " + XDF_WRITE_INTERVAL + " ms");
                }

                try {
                    Thread.sleep(XDF_WRITE_INTERVAL);
                } catch (InterruptedException ie) {
                    // Sleep aborted.
                }

            } catch (Exception e) {
                Log.e(TAG, "Stream " + xdfStreamIndex + ": Failed to read or record chunk.", e);
            }
        }
    }

    private void timingOffsetLoop(StreamRecorder streamRecorder) {
        while (isRunning) {
            try {
                Thread.sleep(OFFSET_MEASURE_INTERVAL);
            } catch (InterruptedException ie) {
                // Sleep aborted.
                if (!isRunning) {
                    break;
                }
            }
            try {
                streamRecorder.takeTimeOffsetMeasurement();
                flushRecordedTimingOffsets();
            } catch (LSL.TimeoutException te) {
                Log.e(TAG, "Stream " + xdfStreamIndex + ": Timeout trying to obtain timing offset from LSL.", te);
            } catch (Exception e) {
                Log.e(TAG, "Stream " + xdfStreamIndex + ": Failed to determine or record timing offset.", e);
            }
        }
    }

    public void stop() {
        isRunning = false;
        Thread st = sampleThread;
        if (st != null) {
            st.interrupt();
        }
        st = timingOffsetThread;
        if (st != null) {
            st.interrupt();
        }
        Log.i(TAG, "Stream " + xdfStreamIndex + ": Received stop signal");
    }

    public void waitFinished() {
        if (sampleThread == null) {
            return;
        }
        try {
            sampleThread.join(); // wait for all threads to finish
            Thread t = timingOffsetThread;
            if (t != null) {
                t.join();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Got interrupted while waiting to finish.", e);
        }
        sampleThread = null;
        timingOffsetThread = null;
        streamRecorder.close();
        Log.i(TAG, "Stream " + xdfStreamIndex + " terminated");
    }

    public void setRecordTimingOffsets(boolean recordTimingOffsets) {
        this.recordTimingOffsets = recordTimingOffsets;
    }

    public void writeStreamHeader() {
        streamRecorder.writeStreamHeader(xdfWriter, xdfStreamIndex);
    }

    public void writeAllRecordedSamples() {
        int samplesWritten = streamRecorder.writeAllRecordedSamples(xdfWriter, xdfStreamIndex);
        Log.i(TAG, "Stream " + xdfStreamIndex + ": Chunk of " + samplesWritten + " samples written to XDF");
    }

    public void flushRecordedTimingOffsets() {
        streamRecorder.flushRecordedTimingOffsets(xdfWriter, xdfStreamIndex);
    }

    public void writeStreamFooter() {
        String footerXml = streamRecorder.getStreamFooterXml();
        xdfWriter.writeStreamFooter(xdfStreamIndex, footerXml);
    }
}
