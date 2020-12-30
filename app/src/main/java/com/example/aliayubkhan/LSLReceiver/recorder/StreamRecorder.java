package com.example.aliayubkhan.LSLReceiver.recorder;

import android.util.Log;

import com.example.aliayubkhan.LSLReceiver.LSL;
import com.example.aliayubkhan.LSLReceiver.xdf.XdfWriter;

import org.apache.commons.lang3.ArrayUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import static com.example.aliayubkhan.LSLReceiver.xdf.XdfWriter.createFooterXml;

public interface StreamRecorder extends Closeable {

    int pullChunk() throws Exception;

    TimingOffsetMeasurement takeTimeOffsetMeasurement() throws Exception;

    void writeStreamHeader(XdfWriter xdfWriter, int xdfStreamIndex);

    int writeAllRecordedSamples(XdfWriter xdfWriter, int xdfStreamIndex);

    void writeAllRecordedTimingOffsets(XdfWriter xdfWriter, int xdfStreamIndex);

    void flushRecordedTimingOffsets(XdfWriter xdfWriter, int xdfStreamIndex);

    String getStreamFooterXml();

    @Override
    void close();
}

abstract class TypedStreamRecorder<SampleArray, Sample> implements StreamRecorder {

    /**
     * Timeout in seconds for obtaining an LSL <em>timimg offset</em> measurement.
     */
    private static final double OFFSET_MEASURE_TIMEOUT_SECS = 2.0;

    /**
     * Default sample buffer length in milliseconds for each stream. The actual buffer length is
     * derived from that value and depends on the sampling rate and number of channels.
     */
    private static final int BUFFER_TIME_MILLIS = 100; //TODO reconsider using fixed buffer time

    /**
     * Minimal sample buffer capacity in number of samples.
     */
    private static final int MIN_SAMPLES_TO_BUFFER = 10;

    final LSL.StreamInlet inlet;
    final String streamName;
    final int channelCount;

    /**
     * Number of samples to buffer
     */
    final int samplesToBuffer;

    /**
     * Length of sample buffer: sized to hold as much as {@link #samplesToBuffer} values per
     * channel
     */
    final int sampleBufferCapacity;

    SampleArray sampleBuffer;
    double[] timestamps;

    int totalSamples = 0;
    double firstTimestamp = 0.0, lastTimestamp = 0.0;

    final List<Sample> unwrittenRecordedSamples = new ArrayList<>();
    final List<Double> unwrittenRecordedTimestamps = new ArrayList<>();

    private final String streamHeaderXml;

    final List<TimingOffsetMeasurement> offsetMeasurements = new ArrayList<>();

    /**
     * Index into the list of {@link #offsetMeasurements} of the next timing offset not yet written
     * into the XDF file.
     */
    int timingOffsetIndex = 0;

    public TypedStreamRecorder(LSL.StreamInfo input, IntFunction<SampleArray> bufferConstructor) throws IOException {
        channelCount = input.channel_count();
        streamName = input.name();
        streamHeaderXml = input.as_xml();
        if (channelCount < 1) {
            throw new IllegalArgumentException("Stream has less than one channel: " + streamName);
        }

        double calculatedSamplesToBuffer = input.nominal_srate() * BUFFER_TIME_MILLIS / 1000.0;
        samplesToBuffer = Math.max(MIN_SAMPLES_TO_BUFFER, (int) Math.ceil(calculatedSamplesToBuffer));
        sampleBufferCapacity = channelCount * samplesToBuffer;
        sampleBuffer = bufferConstructor.apply(sampleBufferCapacity);
        timestamps = new double[samplesToBuffer];

        inlet = new LSL.StreamInlet(input);
    }

    @Override
    public int pullChunk() throws Exception {
        int valuesRead = 0;
        int totalValuesRead = 0;
        do {
            totalValuesRead += (valuesRead = pullChunkImpl());
            addToRecordBuffer(valuesRead);
        } while (valuesRead == sampleBufferCapacity);
        int samplesRead = totalValuesRead / channelCount;
        return samplesRead;
    }

    void addToRecordBuffer(int dataValuesRead) {
        if (dataValuesRead > 0) {
            // add samples and timestamps to the end of the list whenever we received a sample
            for (int k = 0; k < dataValuesRead; k++) {
                unwrittenRecordedSamples.add(getSampleJustRead(k));
            }
            int samplesRead = dataValuesRead / channelCount;
            for (int t = 0; t < samplesRead; t++) {
                unwrittenRecordedTimestamps.add(timestamps[t]);
            }

            double mostRecentTimestamp = timestamps[samplesRead - 1];
            lastTimestamp = mostRecentTimestamp;
            if (totalSamples == 0) {
                firstTimestamp = timestamps[0];
            }
            totalSamples += samplesRead;
        }
    }

    protected boolean isAudioRecording() {
        return streamName != null && streamName.contains("Audio");
    }

    public void writeStreamHeader(XdfWriter writer, int xdfStreamIndex) {
        writer.writeStreamHeader(xdfStreamIndex, streamHeaderXml);
    }

    public String getStreamName() {
        return streamName;
    }

    public String getStreamHeaderXml() {
        return streamHeaderXml;
    }

    @Override
    public String getStreamFooterXml() {
        return createFooterXml(firstTimestamp, lastTimestamp, totalSamples, offsetMeasurements);
    }

    @Override
    public void writeAllRecordedTimingOffsets(XdfWriter writer, int xdfStreamIndex) {
        for (TimingOffsetMeasurement m : offsetMeasurements) {
            writer.writeStreamOffset(xdfStreamIndex, m.collectionTime, m.offset);
        }
    }

    @Override
    public void flushRecordedTimingOffsets(XdfWriter writer, int xdfStreamIndex) {
        while (timingOffsetIndex < offsetMeasurements.size()) {
            TimingOffsetMeasurement m = offsetMeasurements.get(timingOffsetIndex);
            writer.writeStreamOffset(xdfStreamIndex, m.collectionTime, m.offset);
            timingOffsetIndex++;
        }
    }

    @Override
    public TimingOffsetMeasurement takeTimeOffsetMeasurement() throws Exception {
        double now = LSL.local_clock();
        double offset = inlet.time_correction(OFFSET_MEASURE_TIMEOUT_SECS);
        TimingOffsetMeasurement measurement = new TimingOffsetMeasurement(now, offset);
        offsetMeasurements.add(measurement);
        return measurement;
    }

    abstract double[] processTimestampsForXdf(List<Double> unwrittenRecordedTimestamps);

    abstract SampleArray processSamplesForXdf(List<Sample> unwrittenRecordedSamples);

    @Override
    public final int writeAllRecordedSamples(XdfWriter xdfWriter, int xdfStreamIndex) {
        double[] xdfTimestamps = processTimestampsForXdf(unwrittenRecordedTimestamps);
        SampleArray xdfSamples = processSamplesForXdf(unwrittenRecordedSamples);
        writeStreamToXdf(xdfWriter, xdfStreamIndex, xdfSamples, xdfTimestamps);
        unwrittenRecordedTimestamps.clear();
        unwrittenRecordedSamples.clear();
        return xdfTimestamps.length;
    }

    @Override
    public void close() {
        inlet.close();
    }

    abstract Sample getSampleJustRead(int index);

    abstract int pullChunkImpl() throws Exception;

    abstract void writeStreamToXdf(XdfWriter xdfWriter, int xdfStreamIndex, SampleArray samples, double[] timestamps);
}

class FloatRecorder extends TypedStreamRecorder<float[], Float> {

    public FloatRecorder(LSL.StreamInfo input) throws IOException {
        super(input, float[]::new);
    }

    @Override
    protected void writeStreamToXdf(XdfWriter xdfWriter, int xdfStreamIndex, float[] samples, double[] timestamps) {
        xdfWriter.writeDataChunkFloat(xdfStreamIndex, samples, timestamps, channelCount);
    }

    @Override
    Float getSampleJustRead(int index) {
        return sampleBuffer[index];
    }

    @Override
    int pullChunkImpl() throws Exception {
        return inlet.pull_chunk(sampleBuffer, timestamps);
    }

    @Override
    double[] processTimestampsForXdf(List<Double> unwrittenRecordedTimestamps) {
        double[] lighttimestamps = ArrayUtils.toPrimitive(unwrittenRecordedTimestamps.toArray(new Double[0]), 0);

//        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);
//        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);
//
//        if (channelCount == 1) {
//            lighttimestamps = Arrays.copyOfRange(lighttimestamps, 0, lightsample.length);
//        }
        return lighttimestamps;
    }

    @Override
    float[] processSamplesForXdf(List<Float> unwrittenRecordedSamples) {
        float[] lightsample = ArrayUtils.toPrimitive(unwrittenRecordedSamples.toArray(new Float[0]), 0);

//        if (!isAudioRecording()) {
//            lightsample = removeZerosFloat(lightsample, lightsample.length);
//        }
//
//        if (channelCount != 1) {
//            lightsample = Arrays.copyOfRange(lightsample, 0, lighttimestamps.length * channelCount);
//        }
        return lightsample;
    }
}

class DoubleRecorder extends TypedStreamRecorder<double[], Double> {

    public DoubleRecorder(LSL.StreamInfo input) throws IOException {
        super(input, double[]::new);
    }

    @Override
    public void writeStreamToXdf(XdfWriter xdfWriter, int xdfStreamIndex, double[] samples, double[] timestamps) {
        xdfWriter.writeDataChunkDouble(xdfStreamIndex, samples, timestamps, channelCount);
    }

    @Override
    Double getSampleJustRead(int index) {
        return sampleBuffer[index];
    }

    @Override
    int pullChunkImpl() throws Exception {
        return inlet.pull_chunk(sampleBuffer, timestamps);
    }

    @Override
    double[] processTimestampsForXdf(List<Double> unwrittenRecordedTimestamps) {
        double[] lighttimestamps = ArrayUtils.toPrimitive(unwrittenRecordedTimestamps.toArray(new Double[0]), 0);

//        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);
//
//        if (channelCount == 1) {
//            lighttimestamps = Arrays.copyOfRange(lighttimestamps, 0, lightsample.length);
//        }
        return lighttimestamps;
    }

    @Override
    double[] processSamplesForXdf(List<Double> unwrittenRecordedSamples) {
        double[] lightsample = ArrayUtils.toPrimitive(unwrittenRecordedSamples.toArray(new Double[0]), 0);

//        lightsample = removeZerosDouble(lightsample, lightsample.length);
//
//        if (channelCount != 1) {
//            lightsample = Arrays.copyOfRange(lightsample, 0, lighttimestamps.length * channelCount);
//        }
        return lightsample;
    }
}

class IntRecorder extends TypedStreamRecorder<int[], Integer> {

    public IntRecorder(LSL.StreamInfo input) throws IOException {
        super(input, int[]::new);
    }

    @Override
    public void writeStreamToXdf(XdfWriter xdfWriter, int xdfStreamIndex, int[] samples, double[] timestamps) {
        xdfWriter.writeDataChunkInt(xdfStreamIndex, samples, timestamps, channelCount);
    }

    @Override
    Integer getSampleJustRead(int index) {
        return sampleBuffer[index];
    }

    @Override
    int pullChunkImpl() throws Exception {
        return inlet.pull_chunk(sampleBuffer, timestamps);
    }

    @Override
    double[] processTimestampsForXdf(List<Double> unwrittenRecordedTimestamps) {
        double[] lighttimestamps = ArrayUtils.toPrimitive(unwrittenRecordedTimestamps.toArray(new Double[0]), 0);

//        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);
//
//        if (channelCount == 1) {
//            lighttimestamps = Arrays.copyOfRange(lighttimestamps, 0, lightsample.length);
//        }
        return lighttimestamps;
    }

    @Override
    int[] processSamplesForXdf(List<Integer> unwrittenRecordedSamples) {
        int[] lightsample = ArrayUtils.toPrimitive(unwrittenRecordedSamples.toArray(new Integer[0]), 0);

//        lightsample = removeZerosInt(lightsample, lightsample.length);
//
//        if (channelCount != 1) {
//            lightsample = Arrays.copyOfRange(lightsample, 0, lighttimestamps.length * channelCount);
//        }
        return lightsample;
    }
}

class ShortRecorder extends TypedStreamRecorder<short[], Short> {

    public ShortRecorder(LSL.StreamInfo input) throws IOException {
        super(input, short[]::new);
    }

    @Override
    public void writeStreamToXdf(XdfWriter xdfWriter, int xdfStreamIndex, short[] samples, double[] timestamps) {
        xdfWriter.writeDataChunkShort(xdfStreamIndex, samples, timestamps, channelCount);
    }

    @Override
    Short getSampleJustRead(int index) {
        return sampleBuffer[index];
    }

    @Override
    int pullChunkImpl() throws Exception {
        return inlet.pull_chunk(sampleBuffer, timestamps);
    }

    @Override
    double[] processTimestampsForXdf(List<Double> unwrittenRecordedTimestamps) {
        double[] lighttimestamps = ArrayUtils.toPrimitive(unwrittenRecordedTimestamps.toArray(new Double[0]), 0);

//        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);
//
//        if (channelCount == 1) {
//            lighttimestamps = Arrays.copyOfRange(lighttimestamps, 0, lightsample.length);
//        }
        return lighttimestamps;
    }

    @Override
    short[] processSamplesForXdf(List<Short> unwrittenRecordedSamples) {
        short[] lightsample = ArrayUtils.toPrimitive(unwrittenRecordedSamples.toArray(new Short[0]), (short) 0);

//        lightsample = TimeSeriesUtil.removeZerosShort(lightsample, lightsample.length);
//
//        if (channelCount != 1) {
//            lightsample = Arrays.copyOfRange(lightsample, 0, lighttimestamps.length * channelCount);
//        }
        return lightsample;
    }
}

class ByteRecorder extends TypedStreamRecorder<byte[], Byte> {

    public ByteRecorder(LSL.StreamInfo input) throws IOException {
        super(input, byte[]::new);
    }

    @Override
    public void writeStreamToXdf(XdfWriter xdfWriter, int xdfStreamIndex, byte[] samples, double[] timestamps) {
        xdfWriter.writeDataChunkByte(xdfStreamIndex, samples, timestamps, channelCount);
    }

    @Override
    Byte getSampleJustRead(int index) {
        return sampleBuffer[index];
    }

    @Override
    int pullChunkImpl() throws Exception {
        return inlet.pull_chunk(sampleBuffer, timestamps);
    }

    @Override
    double[] processTimestampsForXdf(List<Double> unwrittenRecordedTimestamps) {
        double[] lighttimestamps = ArrayUtils.toPrimitive(unwrittenRecordedTimestamps.toArray(new Double[0]), 0);

//        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);
//
//        if (channelCount == 1) {
//            lighttimestamps = Arrays.copyOfRange(lighttimestamps, 0, lightsample.length);
//        }
        return lighttimestamps;
    }

    @Override
    byte[] processSamplesForXdf(List<Byte> unwrittenRecordedSamples) {
        byte[] lightsample = ArrayUtils.toPrimitive(unwrittenRecordedSamples.toArray(new Byte[0]), (byte) 0);

//        lightsample = removeZerosByte(lightsample, lightsample.length);
//
//        if (channelCount != 1) {
//            lightsample = Arrays.copyOfRange(lightsample, 0, lighttimestamps.length * channelCount);
//        }
        return lightsample;
    }
}

class StringRecorder extends TypedStreamRecorder<String[], String> {

    public StringRecorder(LSL.StreamInfo input) throws IOException {
        super(input, String[]::new);
    }

    @Override
    public void writeStreamToXdf(XdfWriter xdfWriter, int xdfStreamIndex, String[] samples, double[] timestamps) {
        xdfWriter.writeDataChunkStringMarker(xdfStreamIndex, samples, timestamps, channelCount);
    }

    @Override
    String getSampleJustRead(int index) {
        return sampleBuffer[index];
    }

    @Override
    int pullChunkImpl() throws Exception {
        return inlet.pull_chunk(sampleBuffer, timestamps);
    }

    @Override
    double[] processTimestampsForXdf(List<Double> unwrittenRecordedTimestamps) {
        double[] lighttimestamps = ArrayUtils.toPrimitive(unwrittenRecordedTimestamps.toArray(new Double[0]), 0);

//        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);
//        lighttimestamps = removeZerosDouble(lighttimestamps, lighttimestamps.length);
//
//        if (channelCount == 1) {
//            lighttimestamps = Arrays.copyOfRange(lighttimestamps, 0, lightsample.length);
//        }
        return lighttimestamps;
    }

    @Override
    String[] processSamplesForXdf(List<String> unwrittenRecordedSamples) {
        String[] lightsample = unwrittenRecordedSamples.toArray(new String[0]);

//        if (channelCount != 1) {
//            lightsample = Arrays.copyOfRange(lightsample, 0, lighttimestamps.length * channelCount);
//        }
        return lightsample;
    }
}
