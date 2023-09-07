package de.uol.neuropsy.LSLReceiver.recorder;

import de.uol.neuropsy.LSLReceiver.util.ListToPrimitiveArray;
import de.uol.neuropsy.LSLReceiver.xdf.XdfWriter;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import edu.ucsd.sccn.LSL;

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
    private static final int BUFFER_TIME_MILLIS = 750;

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

    final List<Sample> unwrittenRecordedSamples;
    final List<Double> unwrittenRecordedTimestamps;

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

        if (channelCount < 1) {
            throw new IllegalArgumentException("Stream has less than one channel: " + streamName);
        }

        double calculatedSamplesToBuffer = input.nominal_srate() * BUFFER_TIME_MILLIS / 1000.0;
        samplesToBuffer = Math.max(MIN_SAMPLES_TO_BUFFER, (int) Math.ceil(calculatedSamplesToBuffer));
        sampleBufferCapacity = channelCount * samplesToBuffer;
        sampleBuffer = bufferConstructor.apply(sampleBufferCapacity);
        timestamps = new double[samplesToBuffer];

        unwrittenRecordedSamples = new ArrayList<>(sampleBufferCapacity);
        unwrittenRecordedTimestamps = new ArrayList<>(samplesToBuffer);

        inlet = new LSL.StreamInlet(input);
        try {
            streamHeaderXml = inlet.info().as_xml();
            System.out.println("The stream's XML meta-data is:\n" + streamHeaderXml);
        } catch (Exception e) {
            throw new IOException("Information could not be retrieved from the Inlet", e);
        }
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
        return XdfWriter.createFooterXml(firstTimestamp, lastTimestamp, totalSamples, offsetMeasurements);
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

    private double[] processTimestampsForXdf(List<Double> unwrittenRecordedTimestamps) {
        int len = unwrittenRecordedTimestamps.size();
        double[] timestampArray = new double[len];
        for (int i = 0; i < len; i++) {
            timestampArray[i] = unwrittenRecordedTimestamps.get(i);
        }
        return timestampArray;
    }

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
    float[] processSamplesForXdf(List<Float> unwrittenRecordedSamples) {
        return ListToPrimitiveArray.toFloatArray(unwrittenRecordedSamples);
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
    double[] processSamplesForXdf(List<Double> unwrittenRecordedSamples) {
        return ListToPrimitiveArray.toDoubleArray(unwrittenRecordedSamples);
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
    int[] processSamplesForXdf(List<Integer> unwrittenRecordedSamples) {
        return ListToPrimitiveArray.toIntArray(unwrittenRecordedSamples);
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
    short[] processSamplesForXdf(List<Short> unwrittenRecordedSamples) {
        return ListToPrimitiveArray.toShortArray(unwrittenRecordedSamples);
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
    byte[] processSamplesForXdf(List<Byte> unwrittenRecordedSamples) {
        return ListToPrimitiveArray.toByteArray(unwrittenRecordedSamples);
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
    String[] processSamplesForXdf(List<String> unwrittenRecordedSamples) {
        return unwrittenRecordedSamples.toArray(new String[unwrittenRecordedSamples.size()]);
    }
}
