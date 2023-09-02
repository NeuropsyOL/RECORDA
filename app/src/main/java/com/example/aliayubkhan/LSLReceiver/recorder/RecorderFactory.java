package com.example.aliayubkhan.LSLReceiver.recorder;

import java.io.IOException;

import edu.ucsd.sccn.LSL;

public class RecorderFactory {

    @FunctionalInterface
    public interface RecorderConstructor {

        StreamRecorder createRecorder(LSL.StreamInfo info) throws IOException;
    }

    enum RecordingSampleType {
        FLOAT(FloatRecorder::new),
        DOUBLE(DoubleRecorder::new),
        INT(IntRecorder::new),
        SHORT(ShortRecorder::new),
        BYTE(ByteRecorder::new),
        STRING(StringRecorder::new);

        private final RecorderConstructor recorderConstructor;

        RecordingSampleType(RecorderConstructor constructor) {
            this.recorderConstructor = constructor;
        }

        public static RecordingSampleType fromLslFormatConstant(int formatConstant) {
            switch (formatConstant) {
                case LSL.ChannelFormat.float32:
                    return FLOAT;
                case LSL.ChannelFormat.double64:
                    return DOUBLE;
                case LSL.ChannelFormat.string:
                    return STRING;
                case LSL.ChannelFormat.int32:
                    return INT;
                case LSL.ChannelFormat.int16:
                    return SHORT;
                case LSL.ChannelFormat.int8:
                    return BYTE;
                case LSL.ChannelFormat.undefined:
                    throw new IllegalArgumentException("Stream has undefined channel format.");
                default:
                    throw new IllegalArgumentException("Unknown LSL channel format constant: " + formatConstant);
            }
        }
    }

    private final RecorderConstructor constructor;
    private final LSL.StreamInfo streamInfo;

    private RecorderFactory(RecorderConstructor constructor, LSL.StreamInfo streamInfo) {
        this.constructor = constructor;
        this.streamInfo = streamInfo;

    }

    public static RecorderFactory forLslStream(LSL.StreamInfo streamInfo) {
        int format = streamInfo.channel_format();
        RecordingSampleType streamFormat = RecordingSampleType.fromLslFormatConstant(format);
        return new RecorderFactory(streamFormat.recorderConstructor, streamInfo);
    }

    public StreamRecorder openInlet() throws IOException {
        return constructor.createRecorder(streamInfo);
    }
}
