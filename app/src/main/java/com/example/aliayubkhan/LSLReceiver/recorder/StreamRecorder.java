package com.example.aliayubkhan.LSLReceiver.recorder;

import com.example.aliayubkhan.LSLReceiver.LSL;
import com.example.aliayubkhan.LSLReceiver.xdf.XdfWriter;

import org.apache.commons.lang3.ArrayUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.example.aliayubkhan.LSLReceiver.recorder.TimeSeriesUtil.removeZerosByte;
import static com.example.aliayubkhan.LSLReceiver.recorder.TimeSeriesUtil.removeZerosDouble;
import static com.example.aliayubkhan.LSLReceiver.recorder.TimeSeriesUtil.removeZerosFloat;
import static com.example.aliayubkhan.LSLReceiver.recorder.TimeSeriesUtil.removeZerosInt;
import static com.example.aliayubkhan.LSLReceiver.xdf.XdfWriter.createFooterXml;

public interface StreamRecorder extends Closeable {

    int pullChunk() throws Exception;

    TimingOffsetMeasurement takeTimeOffsetMeasurement() throws Exception;

    void writeStreamHeader(XdfWriter xdfWriter, int xdfStreamIndex);

    void writeAllRecordedSamples(XdfWriter xdfWriter, int xdfStreamIndex);

    void writeAllRecordedTimingOffsets(XdfWriter xdfWriter, int xdfStreamIndex);

    String getStreamFooterXml();

    @Override
    void close();
}

abstract class TypedStreamRecorder<SampleArray, Sample> implements StreamRecorder {

    final LSL.StreamInlet inlet;
    final String streamName;
    final int channelCount;

    SampleArray sampleBuffer;
    double[] timestamps;

    int totalSamples = 0;
    double firstTimestamp = 0.0, lastTimestamp = 0.0;

    final List<Sample> unwrittenRecordedSamples = new ArrayList<>();
    final List<Double> unwrittenRecordedTimestamps = new ArrayList<>();
    final List<TimingOffsetMeasurement> offsetMeasurements = new ArrayList<>();

    private final String streamHeaderXml;

    public TypedStreamRecorder(LSL.StreamInfo input) throws IOException {
        channelCount = input.channel_count();
        streamName = input.name();
        timestamps = new double[channelCount];
        streamHeaderXml = input.as_xml();

        inlet = new LSL.StreamInlet(input);
    }

    @Override
    public int pullChunk() throws Exception {
        int samplesRead = pullChunkImpl();
        addToRecordBuffer(samplesRead);
        return samplesRead;
    }

    void addToRecordBuffer(int dataValuesRead) {
        if (dataValuesRead > 0) {
            // add samples and timestamps to the end of the list whenever we received a sample
            double currentTimestamp = timestamps[0];
            unwrittenRecordedTimestamps.add(currentTimestamp);
            lastTimestamp = currentTimestamp;
            if (totalSamples == 0) {
                firstTimestamp = currentTimestamp;
            }
            totalSamples += dataValuesRead / channelCount;
            for (int k = 0; k < dataValuesRead; k++) {
                unwrittenRecordedSamples.add(getSampleJustRead(k));
            }
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
    public TimingOffsetMeasurement takeTimeOffsetMeasurement() throws Exception {
        double now = LSL.local_clock();
        double offset = inlet.time_correction(2.0);
        TimingOffsetMeasurement measurement = new TimingOffsetMeasurement(now, offset);
        offsetMeasurements.add(measurement);
        return measurement;
    }

    abstract double[] processTimestampsForXdf(List<Double> unwrittenRecordedTimestamps);

    abstract SampleArray processSamplesForXdf(List<Sample> unwrittenRecordedSamples);

    @Override
    public final void writeAllRecordedSamples(XdfWriter xdfWriter, int xdfStreamIndex) {
        double[] xdfTimestamps = processTimestampsForXdf(unwrittenRecordedTimestamps);
        SampleArray xdfSamples = processSamplesForXdf(unwrittenRecordedSamples);
        writeStreamToXdf(xdfWriter, xdfStreamIndex, xdfSamples, xdfTimestamps);
        unwrittenRecordedTimestamps.clear();
        unwrittenRecordedSamples.clear();
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
        super(input);
        sampleBuffer = new float[channelCount];
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
        super(input);
        sampleBuffer = new double[channelCount];
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
        super(input);
        sampleBuffer = new int[channelCount];
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
        super(input);
        sampleBuffer = new short[channelCount];
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
        super(input);
        sampleBuffer = new byte[channelCount];
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
        super(input);
        sampleBuffer = new String[channelCount];
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
