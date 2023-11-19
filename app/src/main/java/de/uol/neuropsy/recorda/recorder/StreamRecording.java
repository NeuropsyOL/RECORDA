package de.uol.neuropsy.recorda.recorder;

import android.util.Log;

import de.uol.neuropsy.recorda.xdf.XdfWriter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    private static final long DEFAULT_MILLIS_THRESHOLD_NOT_RESPONDING = 3000;

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

    /*
     * Stream quality indicators
     */

    private long lastMillisAnySamplesReceived;

    private long millisThresholdNotResponding = DEFAULT_MILLIS_THRESHOLD_NOT_RESPONDING;

    private QualityState lastObservedQuality = QualityState.OK;

    private void sampleRecordingLoop(StreamRecorder streamRecorder) {
        lastMillisAnySamplesReceived = System.currentTimeMillis();
        while (isRunning) {
            try {
                int samples = streamRecorder.pullChunk();

                /*
                 * Calculate stream quality
                 *
                 * Stream quality has three possible values and is determined as follows:
                 *
                 * - If 'pullChunk' throws, the quality is NOT_RESPONDING
                 * - If no samples have been received for certain (configurable) amount of time,
                 *   the quality is NOT_RESPONDING
                 *
                 * Otherwise, the stream quality is either OK or LAGGY depending on the actual
                 * number of samples recently received compared to the nominal sampling rate:
                 *
                 * - Irregular streams, i.e. ones without a nominal sampling rate, are
                 *   always OK.
                 * - Streams with a nominal sampling rate: The average sampling rate in a time
                 *   window of the recent past is calculated and compared to the nominal sampling
                 *   rate. A deviation up to a certain threshold is tolerated. If the actual
                 *   sampling rate is too low by more than that threshold, the stream quality is
                 *   deemed LAGGY. Otherwise, it is OK.
                 */
                QualityState quality = QualityState.OK;

                if (samples > 0) {
                    Log.d(TAG, "Stream " + xdfStreamIndex + ": Pulled " + samples + " values");
                    lastMillisAnySamplesReceived = System.currentTimeMillis();
                    writeAllRecordedSamples();
                    if (xdfWriter != null) {
                        long size = new File(xdfWriter.getXdfFilePath()).length();
                        Log.d(TAG, "XDF file size now: " + size + " bytes");
                    }
                } else {
                    Log.d(TAG, "Stream " + xdfStreamIndex + ": No samples. Waiting " + XDF_WRITE_INTERVAL + " ms");
                    if (streamRecorder.hasRegularRate()) {
                        long millisSinceLastReceived = System.currentTimeMillis() - lastMillisAnySamplesReceived;
                        if (millisSinceLastReceived > millisThresholdNotResponding) {
                            quality = QualityState.NOT_RESPONDING;
                        }
                    }
                }

                if (quality != QualityState.NOT_RESPONDING) {
                    // Do not upgrade from NOT_RESPONDING to LAGGY

                    /*
                     * TODO Finish quality calculation: Detect LAGGY.
                     */
                }
                setQuality(quality);

                try {
                    Thread.sleep(XDF_WRITE_INTERVAL);
                } catch (InterruptedException ie) {
                    // Sleep aborted.
                }

            } catch (Exception e) {
                Log.e(TAG, "Stream " + xdfStreamIndex + ": Failed to read or record chunk.", e);
                setQuality(QualityState.NOT_RESPONDING);
            }
        }
    }

    private void setQuality(QualityState quality) {
        QualityState newState = getCurrentQuality();
        if (newState != lastObservedQuality) {
            lastObservedQuality = newState;
            notifyQualityListeners(newState);
        }
    }

    private void notifyQualityListeners(QualityState newState) {
        qualityListeners.forEach(l -> l.streamQualityChanged(newState));
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
