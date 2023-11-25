package de.uol.neuropsy.recorda.recorder;

import java.util.Deque;
import java.util.LinkedList;

import edu.ucsd.sccn.LSL;

public class QualityMetrics {

    // Quality parameters:

    /**
     * Default value for {@link #millisTimeoutNotResponding}
     */
    private static final long DEFAULT_MILLIS_NOT_RESPONDING = 7000;

    /**
     * Default value for {@link #millisTimeoutLaggy}
     */
    private static final long DEFAULT_MILLIS_LAGGY = 1500;

    private final long millisTimeoutLaggy = DEFAULT_MILLIS_LAGGY;

    private final long millisTimeoutNotResponding = DEFAULT_MILLIS_NOT_RESPONDING;
    /**
     * The lowest ratio between the actual sampling rate and the nominal sampling rate that is
     * tolerated for the quality being considered OK. If the actual sampling rate falls below that,
     * the stream quality will be deemed {@code LAGGY}.
     * <p>
     * Example: A value of 0.95 means that the actual rate of receiving samples must not be less
     * than 95 % of the nominal sampling rate.
     */
    private final double qualitySampleRateThreshold = 0.90;

    /**
     * The number of seconds over which to average the actual rate of receiving samples.
     * MUST not be less than 0.001 (a millisecond). SHOULD be at least a second since samples
     * depending on how frequently the client pulls samples from the stream source.
     */
    private final double sampleRateLookBackWindowSeconds = 10.0;

    // END quality parameters

    private long startTimeOfCurrentQualityWindow;
    private final double nominalSamplingRate;
    private final Deque<ReceivedSampleCount> history;

    // Quality verdict:

    /**
     * The current average number of samples received per second over a recent time frame, defined
     * by {@link #sampleRateLookBackWindowSeconds}.
     * <p>
     * The value is only defined for a stream with a regular sampling rate as set in the constructor
     * of this {@link QualityMetrics}.
     */
    private double currentSamplingRate;

    /**
     * The current quality of the stream that this {@link QualityMetrics} assesses.
     */
    private QualityState currentQuality;

    public QualityMetrics(double nominalSamplingRate) {
        this.nominalSamplingRate = nominalSamplingRate;
        this.currentQuality = QualityState.OK;
        this.history = new LinkedList<>();
        this.startTimeOfCurrentQualityWindow = -1;
        this.currentSamplingRate = 0.0;
    }

    /**
     * Accumulate counts of received samples and continually calculate current stream quality.
     * <p>
     * Stream quality has three possible values (see enum {@link QualityState} and is determined
     * as follows based on two metrics:
     * <p>
     * - If 'pullChunk' throws, the quality is {@code NOT_RESPONDING}
     * - If no samples have been received for a certain amount of time,
     *   the quality is first {@code LAGGY}, and then {@code NOT_RESPONDING} after some more time.
     * <p>
     * Otherwise, the stream quality is either {@code OK} or {@code LAGGY} depending on the actual
     * number of samples recently received compared to the nominal sampling rate:
     * <p>
     * - Irregular streams, i.e. ones without a nominal sampling rate, are
     *   always {@code OK} based on this metric.
     * - Streams with a nominal sampling rate: The average sampling rate in a time
     *   window of the recent past is calculated and compared to the nominal sampling
     *   rate. A deviation up to a certain threshold is tolerated. If the actual
     *   sampling rate is too low by more than that threshold, the stream quality is
     *   deemed {@code LAGGY}. Otherwise, it is OK.
     */
    public QualityState received(int samples) {
        QualityState q = recordNumberOfSamplesReceivedAndDetermineQuality(samples);
        return setQuality(q);
    }

    private QualityState recordNumberOfSamplesReceivedAndDetermineQuality(int samples) {
        final long timeOfReceiving = System.currentTimeMillis();
        if (!hasRegularRate()) {
            /*
             * For source with no regular rate, there is not expectation about receiving any number
             * of samples in any time frame.
             */
            return QualityState.OK;
        }

        if (samples > 0) {
            boolean anySamplesReceivedBefore = startTimeOfCurrentQualityWindow > 0;
            /*
             * The first time we receive samples, an arbitrary number of samples may have
             * accumulated in the stream source. We discard that first measurement so that the
             * quality assessment is not influenced by a seemingly higher sample count in a
             * short amount of time at the beginning which just resulted from backlog.
             */
            if (anySamplesReceivedBefore) {
                ReceivedSampleCount s = new ReceivedSampleCount(samples, startTimeOfCurrentQualityWindow);
                history.addFirst(s);
            }
            startTimeOfCurrentQualityWindow = timeOfReceiving;

        } else { // no samples received since the last call to this method
            long millisSinceLastReceived = timeOfReceiving - startTimeOfCurrentQualityWindow;
            if (millisSinceLastReceived > millisTimeoutNotResponding) {
                return QualityState.NOT_RESPONDING;
            } else if (millisSinceLastReceived > millisTimeoutLaggy) {
                return QualityState.LAGGY;
            }
        }

        /*
         * The part below only decides whether to downgrade from OK to LAGGY based on a comparison
         * between the nominal (i.e. the expected) sampling rate and the actual number of samples
         * recently received.
         */

        long lookBackWindowMillis = (long) (1000.0 * sampleRateLookBackWindowSeconds);
        if (lookBackWindowMillis < 1) {
            // unable to measure with a zero denominator
            return QualityState.OK;
        }
        double averageRateInWindow = getAverageRateInWindow(lookBackWindowMillis, timeOfReceiving);
        if (averageRateInWindow < 0.0) {
            /*
             * Negative values mean indeterminate. Until enough time has passed
             * (field sampleRateLookBackWindowSeconds), the quality is not downgraded based on rate.
             */
            return QualityState.OK;
        }

        currentSamplingRate = averageRateInWindow;
        if (averageRateInWindow < nominalSamplingRate * qualitySampleRateThreshold) {
            return QualityState.LAGGY;
        }
        return QualityState.OK;
    }

    /**
     * Determines the average number of samples received per second in the recent history.
     *
     * @param lookBackWindowMillis the length of the lookback window in milliseconds
     * @param nowMillis reference time in millis considered 'now'
     * @return the average sampling rate in Hz
     */
    private double getAverageRateInWindow(long lookBackWindowMillis, long nowMillis) {
        long samplesReceivedInQualityWindow = 0;
        long actualWindowMillis = -1;
        int historyIndex = 0;
        for (ReceivedSampleCount rc : history) {
            /*
             * History is in 'most recent first' order. This loop iterates into the past and tries
             * to go back at least as far as the look back window.
             */
            samplesReceivedInQualityWindow += rc.sampleCount;
            if (rc.timestamp <= nowMillis - lookBackWindowMillis) {
                actualWindowMillis = nowMillis - rc.timestamp;
                truncateAfter(historyIndex, history);
                break;
            }
            historyIndex++;
        }
        if (actualWindowMillis < lookBackWindowMillis) {
            // history does not go back far enough b/c not enough time has passed, yet
            return -1.0;
        }
        double averageRateInWindow = (double) (1000L * samplesReceivedInQualityWindow) / (double) actualWindowMillis;
        return averageRateInWindow;
    }

    private static void truncateAfter(int lastIndexToKeep, Deque<?> queue) {
        for (int i = queue.size() - 1; i > lastIndexToKeep; i--) {
            queue.remove(i);
        }
    }

    private boolean hasRegularRate() {
        return nominalSamplingRate >= 0.0;
    }

    public QualityState exceptionHappened(Exception e) {
        return setQuality(QualityState.NOT_RESPONDING);
    }

    private QualityState setQuality(QualityState quality) {
        return this.currentQuality = quality;
    }

    public QualityState getCurrentQuality() {
        return currentQuality;
    }

    public double getCurrentSamplingRate() {
        return hasRegularRate() ? currentSamplingRate : LSL.IRREGULAR_RATE;
    }

    private static class ReceivedSampleCount {

        /**
         * The point in time (value of {@link System#currentTimeMillis()}) since which we waited for
         * the samples just received.
         */
        final long timestamp;

        /**
         * The number of samples received in this window {@link #timestamp}.
         */
        final long sampleCount;

        ReceivedSampleCount(int sampleCount, long timestamp) {
            this.sampleCount = sampleCount;
            this.timestamp = timestamp;
        }
    }
}
