package com.example.aliayubkhan.LSLReceiver.recorder;

import android.util.Log;

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
    private volatile boolean isRunning;

    public StreamRecording(StreamRecorder sourceToRecord, XdfWriter xdfWriter, int xdfStreamIndex) {
        Objects.requireNonNull(xdfWriter);
        Objects.requireNonNull(sourceToRecord);
        this.streamRecorder = sourceToRecord;
        this.xdfStreamIndex = xdfStreamIndex;
        this.xdfWriter = xdfWriter;
    }

    public void spawnRecorderThread() {
        if (sampleThread != null) {
            throw new IllegalStateException("Already recording.");
        }
        sampleThread = new Thread(() -> recordingLoop(streamRecorder));
        sampleThread.start();
    }

    private void recordingLoop(StreamRecorder streamRecorder) {
        // First measurement of timing offset happens only after the first wait interval expired (5 sec) like LabRecorder does it.
        long nextTimeToMeasureOffset = OFFSET_MEASURE_INTERVAL + System.currentTimeMillis();

        isRunning = true;
        while (isRunning) {
            try {
                int pulled = streamRecorder.pullChunk();
                if (pulled > 0) {
                    Log.d(TAG, "Stream " + xdfStreamIndex + ": Pulled " + pulled + " values");
                    writeAllRecordedSamples();
                    long size = Files.size(Paths.get(xdfWriter.getXdfFilePath()));
                    Log.d(TAG, "XDF file size now: " + size + " bytes");
                } else {
                    Log.d(TAG, "Stream " + xdfStreamIndex + ": No samples. Waiting " + XDF_WRITE_INTERVAL + " ms");
                }


                long currentTimeMillis = System.currentTimeMillis();
                if (recordTimingOffsets && currentTimeMillis >= nextTimeToMeasureOffset) {
                    boolean success = streamRecorder.takeTimeOffsetMeasurement() != null;
                    if (success) {
                        flushRecordedTimingOffsets();
                    } else {
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

                if (pulled <= 0) {
                    Thread.sleep(XDF_WRITE_INTERVAL);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to read or record stream chunk.", e);
            }
        }
    }

    public void stop() {
        isRunning = false;
    }

    public void waitFinished() {
        if (sampleThread == null) {
            return;
        }
        try {
            sampleThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Got interrupted while waiting to finish.", e);
        }
        sampleThread = null;
        streamRecorder.close();
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
