package de.uol.neuropsy.LSLReceiver.recorder;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.uol.neuropsy.LSLReceiver.xdf.XdfWriter;
import edu.ucsd.sccn.LSL;

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

    /**
     * Constructor for stream recording
     *
     * @param sourceToRecord
     * @param xdfWriter may be null, in that case, nothing is written to file, but samples are received
     *                  for purposes of quality assessment
     * @param xdfStreamIndex
     */
    public StreamRecording(StreamRecorder sourceToRecord, XdfWriter xdfWriter, int xdfStreamIndex) {
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

    private final List<StreamQualityListener> qualityListeners = new ArrayList<>();

    private QualityState lastObservedQuality = QualityState.OK;

    private void sampleRecordingLoop(StreamRecorder streamRecorder) {
        while (isRunning) {
            try {
                int samples = streamRecorder.pullChunk();
                notifyQualityListeners();

                if (samples > 0) {
                    Log.d(TAG, "Stream " + xdfStreamIndex + ": Pulled " + samples + " values");
                    writeAllRecordedSamples();
                    if (xdfWriter != null) {
                        long size = new File(xdfWriter.getXdfFilePath()).length();
                        Log.d(TAG, "XDF file size now: " + size + " bytes");
                    }
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

    private void notifyQualityListeners() {
        QualityState newState = getCurrentQuality();
        if (newState != lastObservedQuality) {
            qualityListeners.forEach(l -> l.streamQualityChanged(newState));
            lastObservedQuality = newState;
        }
    }

    public void registerQualityListener(StreamQualityListener newListener) {
        qualityListeners.add(newListener);
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
        if (xdfWriter != null) {
            streamRecorder.writeStreamHeader(xdfWriter, xdfStreamIndex);
        }
    }

    public void writeAllRecordedSamples() {
        if (xdfWriter != null) {
            int samplesWritten = streamRecorder.writeAllRecordedSamples(xdfWriter, xdfStreamIndex);
            Log.i(TAG, "Stream " + xdfStreamIndex + ": Chunk of " + samplesWritten + " samples written to XDF");
        }
    }

    public void flushRecordedTimingOffsets() {
        if (xdfWriter != null) {
            streamRecorder.flushRecordedTimingOffsets(xdfWriter, xdfStreamIndex);
        }
    }

    public void writeStreamFooter() {
        if (xdfWriter != null) {
            String footerXml = streamRecorder.getStreamFooterXml();
            xdfWriter.writeStreamFooter(xdfStreamIndex, footerXml);
        }
    }

    public QualityState getCurrentQuality() {
        return QualityState.values()[(int) (((System.currentTimeMillis() / 5_000) + xdfStreamIndex) % 3) ];
    }
}
