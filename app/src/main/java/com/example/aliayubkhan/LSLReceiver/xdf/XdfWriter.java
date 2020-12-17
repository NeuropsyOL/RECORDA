package com.example.aliayubkhan.LSLReceiver.xdf;

import com.example.aliayubkhan.LSLReceiver.recorder.TimingOffsetMeasurement;

import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class XdfWriter {
    static {
        System.loadLibrary("generate_xdf");
    }

    private String xdfFilePath;

    public void writeDataChunkFloat(int streamIndex, float[] lightSample, double[] lightTimestamps, int channelCount) {
        writeDataChunkFloat(xdfFilePath, lightSample, lightTimestamps, streamIndex, channelCount);
    }

    public void writeDataChunkInt(int streamIndex, int[] lightSample, double[] lightTimestamps, int channelCount) {
        writeDataChunkInt(xdfFilePath, lightSample, lightTimestamps, streamIndex, channelCount);
    }
    public void writeDataChunkDouble(int streamIndex, double[] lightSample, double[] lightTimestamps, int channelCount) {
        writeDataChunkDouble(xdfFilePath, lightSample, lightTimestamps, streamIndex, channelCount);
    }
    public void writeDataChunkStringMarker(int streamIndex, String[] lightSample, double[] lightTimestamps, int channelCount) {
        writeDataChunkStringMarker(xdfFilePath, lightSample, lightTimestamps, streamIndex, channelCount);
    }
    public void writeDataChunkShort(int streamIndex, short[] lightSample, double[] lightTimestamps, int channelCount) {
        writeDataChunkShort(xdfFilePath, lightSample, lightTimestamps, streamIndex, channelCount);
    }
    public void writeDataChunkByte(int streamIndex, byte[] lightSample, double[] lightTimestamps, int channelCount) {
        writeDataChunkByte(xdfFilePath, lightSample, lightTimestamps, streamIndex, channelCount);
    }
    public static String createFooterXml(double firstTimeStamp, double lastTimeStamp, int sampleCount, List<TimingOffsetMeasurement> timingOffsets) {
        NumberFormat nf = DecimalFormat.getInstance(Locale.US);
        nf.setMaximumFractionDigits(8);
        nf.setGroupingUsed(false);

        StringBuilder footer = new StringBuilder();
        footer.append("<?xml version=\"1.0\"?>\n")
                .append("<info>\n")
                .append("\t<first_timestamp>").append(firstTimeStamp).append("</first_timestamp>\n")
                .append("\t<last_timestamp>").append(lastTimeStamp).append("</last_timestamp>\n")
                .append("\t<sample_count>").append(sampleCount).append("</sample_count>\n")
                .append("\t<clock_offsets>\n");
        for (TimingOffsetMeasurement timingOffset : timingOffsets) {
            double time = timingOffset.collectionTime - timingOffset.offset;
            footer.append("\t\t<offset><time>").append(nf.format(time)).append("</time>")
                    .append("<value>").append(nf.format(timingOffset.offset)).append("</value></offset>\n");
        }
        footer.append("\t</clock_offsets>\n")
                .append("</info>\n");
        return footer.toString();
    }

    public void writeStreamHeader(int xdfStreamIndex, String headerXml) {
        writeStreamHeader(xdfFilePath, xdfStreamIndex, headerXml);
    }

    public void writeStreamFooter(int xdfStreamIndex, String footerXml) {
        writeStreamFooter(xdfFilePath, xdfStreamIndex, footerXml);
    }

    public void writeStreamOffset(int xdfStreamIndex, double collectionTime, double offset) {
        writeStreamOffset(xdfFilePath, xdfStreamIndex, collectionTime, offset);
    }

    public void setXdfFilePath(String xdfFilePath) {
        this.xdfFilePath = xdfFilePath;
    }

    public void setXdfFilePath(Path xdfFilePath) {
        setXdfFilePath(xdfFilePath.toString());
    }

    private static native void writeStreamHeader(String filename, int streamIndex, String headerXml);
    private static native void writeStreamFooter(String filename, int streamIndex, String footerXml);
    private static native void writeStreamOffset(String filename, int streamIndex, double collectionTime, double offset);

    private static native void writeDataChunkFloat(String filename, float[] lightSample, double[] lightTimestamps, int streamIndex, int channelCount);
    private static native void writeDataChunkInt(String filename, int[] lightSample, double[] lightTimestamps, int streamIndex, int channelCount);
    private static native void writeDataChunkDouble(String filename, double[] lightSample, double[] lightTimestamps, int streamIndex, int channelCount);
    private static native void writeDataChunkStringMarker(String filename, String[] lightSample, double[] lightTimestamps, int streamIndex, int channelCount);
    private static native void writeDataChunkShort(String filename, short[] lightSample, double[] lightTimestamps, int streamIndex, int channelCount);
    private static native void writeDataChunkByte(String filename, byte[] lightSample, double[] lightTimestamps, int streamIndex, int channelCount);
}
