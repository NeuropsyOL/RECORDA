package com.example.aliayubkhan.LSLReceiver.recorder;

import com.example.aliayubkhan.LSLReceiver.LSL;

import java.io.IOException;

public class RecorderFactory {

    private static boolean STRING_BASED_FORMAT_DETECTION = true;

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

        private final RecorderConstructor constructor;

        RecordingSampleType(RecorderConstructor constructor) {
            this.constructor = constructor;
        }

        public static RecordingSampleType fromXmlChannelFormat(String xmlChannelFormat) {
            if (xmlChannelFormat.contains("float")) {
                return FLOAT;
            } else if (xmlChannelFormat.contains("int")) {
                return INT;
            } else if (xmlChannelFormat.contains("double")) {
                return DOUBLE;
            } else if (xmlChannelFormat.contains("string")) {
                return STRING;
            } else if (xmlChannelFormat.contains("byte")) {
                return BYTE;
            } else if (xmlChannelFormat.contains("short")) {
                return SHORT;
            } else {
                throw new IllegalArgumentException("Unknown LSL channel format: " + xmlChannelFormat);
            }
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
        RecordingSampleType recordingType;
        if (STRING_BASED_FORMAT_DETECTION) {
            String format = getChannelFormatFromXml(streamInfo);
            recordingType = RecordingSampleType.fromXmlChannelFormat(format);
        } else {
            int format = streamInfo.channel_format();
            recordingType = RecordingSampleType.fromLslFormatConstant(format);
        }
        return new RecorderFactory(recordingType.constructor, streamInfo);
    }

    public StreamRecorder openInlet() throws IOException {
        return constructor.createRecorder(streamInfo);
    }

    private static String getChannelFormatFromXml(LSL.StreamInfo inf) {
        return getXmlNodeValue(inf.as_xml(), "channel_format");
    }

    private static String getXmlNodeValue(String xmlString, String nodeName) {
        int start = xmlString.indexOf("<" + nodeName + ">") + nodeName.length() + 2;
        int end = xmlString.indexOf("</" + nodeName + ">");
        return xmlString.substring(start, end);
    }
}
