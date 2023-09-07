package de.uol.neuropsy.LSLReceiver.recorder;

/**
 * One measurement sample of the offset of a stream's time compared to a reference clock.
 */
public class TimingOffsetMeasurement {
    /**
     * The {@link LSL#local_clock()} time.
     */
    public final double collectionTime;
    /**
     * The current measured timing offset of the stream's time as determined by {@link LSL.StreamInlet#time_correction()}.
     */
    public final double offset;

    TimingOffsetMeasurement(double collectionTime, double offset) {
        this.collectionTime = collectionTime;
        this.offset = offset;
    }
}
