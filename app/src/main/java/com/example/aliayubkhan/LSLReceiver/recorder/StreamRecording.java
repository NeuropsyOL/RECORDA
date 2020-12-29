package com.example.aliayubkhan.LSLReceiver.recorder;

import android.util.Log;

import com.example.aliayubkhan.LSLReceiver.xdf.XdfWriter;

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
        sampleThread = new Thread(() -> recordingLoop(streamRecorder));
        sampleThread.start();
    }

    private void recordingLoop(StreamRecorder streamRecorder) {
        // First measurement of timing offset happens only after the first wait interval expired (5 sec) like LabRecorder does it.
        long nextTimeToMeasureOffset = OFFSET_MEASURE_INTERVAL + System.currentTimeMillis();

        isRunning = true;
        while (isRunning) {
            try {
                streamRecorder.pullChunk();

                long currentTimeMillis = System.currentTimeMillis();
                if (recordTimingOffsets && currentTimeMillis >= nextTimeToMeasureOffset) {
                    boolean success = streamRecorder.takeTimeOffsetMeasurement() != null;
                    if (!success) {
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
        streamRecorder.writeAllRecordedSamples(xdfWriter, xdfStreamIndex);
    }

    public void writeAllRecordedTimingOffsets() {
        streamRecorder.writeAllRecordedTimingOffsets(xdfWriter, xdfStreamIndex);
    }

    public void writeStreamFooter() {
        String footerXml = streamRecorder.getStreamFooterXml();
        xdfWriter.writeStreamFooter(xdfStreamIndex, footerXml);
    }
}
