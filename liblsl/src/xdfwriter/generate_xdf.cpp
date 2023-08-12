#include <jni.h>
#include <string>
#include <iostream>
#include <cstring>
#include "xdfwriter.h"

// a non-zero value to let the constructor of class XDFWriter skip the file header
static const int SKIP_FILE_HEADER = 1;

/**
 * Most simple way to get a unique stream ID from the zero-based index,
 * considering the stream ID should start at one.
 *
 * @param stream_index the zero-based index of a stream
 * @return a one-based stream ID, used in XDF chunks to identify a stream
 */
streamid_t stream_id_from_index(jint stream_index) {
    return stream_index + 1;
}

template<typename T>
void writeDataChunkAny(
        JNIEnv *env,
        jstring filename,
        std::vector<T> &vChannelData,
        jdoubleArray timestamps,
        jint stream_index,
        jint channel_count
) {
    // convert timestamp java array to c++ vector
    jsize size = env->GetArrayLength(timestamps);
    double *timestamp_doubles = env->GetDoubleArrayElements(timestamps, NULL);
    std::vector<double> vTimestamps;
    vTimestamps.assign(timestamp_doubles, timestamp_doubles + size);

    const streamid_t sid = stream_id_from_index(stream_index);
    const char *c_filename = env->GetStringUTFChars(filename, NULL);
    XDFWriter w(c_filename, SKIP_FILE_HEADER);
    w.write_data_chunk(sid, vTimestamps, vChannelData, (unsigned int) channel_count);

    env->ReleaseStringUTFChars(filename, c_filename);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_aliayubkhan_LSLReceiver_xdf_XdfWriter_writeDataChunkFloat(
        JNIEnv *env,
        jclass clazz,
        jstring filename,
        jfloatArray channel_data,
        jdoubleArray timestamps,
        jint stream_index,
        jint channel_count
) {
    // convert channel data from java array to c++ vector
    jsize sz = env->GetArrayLength(channel_data);
    float *float_elems = env->GetFloatArrayElements(channel_data, 0);
    std::vector<float> vChannelData;
    vChannelData.assign(float_elems, float_elems + sz);

    writeDataChunkAny(env, filename, vChannelData, timestamps, stream_index, channel_count);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_aliayubkhan_LSLReceiver_xdf_XdfWriter_writeDataChunkInt(
        JNIEnv *env,
        jclass clazz,
        jstring filename,
        jintArray channel_data,
        jdoubleArray timestamps,
        jint stream_index,
        jint channel_count
) {
    // convert channel data from java array to c++ vector
    jsize sz = env->GetArrayLength(channel_data);
    int *int_elems = env->GetIntArrayElements(channel_data, 0);
    std::vector<int16_t> vChannelData;
    vChannelData.assign(int_elems, int_elems + sz);

    writeDataChunkAny(env, filename, vChannelData, timestamps, stream_index, channel_count);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_aliayubkhan_LSLReceiver_xdf_XdfWriter_writeDataChunkDouble(
        JNIEnv *env,
        jclass clazz,
        jstring filename,
        jdoubleArray channel_data,
        jdoubleArray timestamps,
        jint stream_index,
        jint channel_count
) {
    // convert channel data from java array to c++ vector
    jsize sz = env->GetArrayLength(channel_data);
    double *float_elems = env->GetDoubleArrayElements(channel_data, 0);
    std::vector<double> vChannelData;
    vChannelData.assign(float_elems, float_elems + sz);

    writeDataChunkAny(env, filename, vChannelData, timestamps, stream_index, channel_count);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_aliayubkhan_LSLReceiver_xdf_XdfWriter_writeDataChunkByte(
        JNIEnv *env,
        jclass clazz,
        jstring filename,
        jbyteArray channel_data,
        jdoubleArray timestamps,
        jint stream_index,
        jint channel_count
) {
    // convert channel data from java array to c++ vector
    jsize sz = env->GetArrayLength(channel_data);
    jbyte *byte_elems = env->GetByteArrayElements(channel_data, 0);
    std::vector<jbyte> vChannelData;
    vChannelData.assign(byte_elems, byte_elems + sz);

    writeDataChunkAny(env, filename, vChannelData, timestamps, stream_index, channel_count);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_aliayubkhan_LSLReceiver_xdf_XdfWriter_writeDataChunkShort(
        JNIEnv *env,
        jclass clazz,
        jstring filename,
        jshortArray channel_data,
        jdoubleArray timestamps,
        jint stream_index,
        jint channel_count
) {
    // convert channel data from java array to c++ vector
    jsize sz = env->GetArrayLength(channel_data);
    short *short_elems = env->GetShortArrayElements(channel_data, 0);
    std::vector<short> vChannelData;
    vChannelData.assign(short_elems, short_elems + sz);

    writeDataChunkAny(env, filename, vChannelData, timestamps, stream_index, channel_count);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_aliayubkhan_LSLReceiver_xdf_XdfWriter_writeDataChunkStringMarker(
        JNIEnv *env,
        jclass clazz,
        jstring filename,
        jobjectArray channel_data,
        jdoubleArray timestamps,
        jint stream_index,
        jint channel_count
) {
    // convert marker strings  from java array to c++ vector
    std::vector<std::string> vMarkerStrings;
    jsize strArrayLen = env->GetArrayLength(channel_data);
    for (int i = 0; i < strArrayLen; ++i) {
        jstring jip = static_cast<jstring>((env)->GetObjectArrayElement(channel_data, i));
        const char *ip = (env)->GetStringUTFChars(jip, NULL);
        vMarkerStrings.push_back(ip);
    }

    writeDataChunkAny(env, filename, vMarkerStrings, timestamps, stream_index, channel_count);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_aliayubkhan_LSLReceiver_xdf_XdfWriter_writeStreamOffset(
        JNIEnv *env, jclass clazz,
        jstring filename,
        jint stream_index,
        jdouble collection_time,
        jdouble offset
) {
    const char *c_filename = (env)->GetStringUTFChars(filename, NULL);
    const std::string filename_string(c_filename);
    env->ReleaseStringUTFChars(filename, c_filename);

    XDFWriter w(filename_string, SKIP_FILE_HEADER);
    const streamid_t sid = stream_id_from_index(stream_index);
    w.write_stream_offset(sid, collection_time, offset);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_aliayubkhan_LSLReceiver_xdf_XdfWriter_writeStreamHeader(
        JNIEnv *env, jclass clazz,
        jstring filename,
        jint stream_index, // zero-based
        jstring header_xml
) {
    const char *c_header_xml = env->GetStringUTFChars(header_xml, NULL);
    const std::string header_xml_string(c_header_xml);
    env->ReleaseStringUTFChars(header_xml, c_header_xml);

    const char *c_file_name = env->GetStringUTFChars(filename, NULL);
    const std::string filename_string(c_file_name);
    env->ReleaseStringUTFChars(filename, c_file_name);

    XDFWriter w(filename_string, stream_index);
    const streamid_t sid = stream_id_from_index(stream_index);
    w.write_stream_header(sid, header_xml_string);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_aliayubkhan_LSLReceiver_xdf_XdfWriter_writeStreamFooter(
        JNIEnv *env, jclass clazz,
        jstring filename,
        jint stream_index, // zero-based
        jstring footer_xml
) {
    const char *c_footer_xml = env->GetStringUTFChars(footer_xml, NULL);
    const std::string footer(c_footer_xml);
    env->ReleaseStringUTFChars(footer_xml, c_footer_xml);

    const char *c_file_name = env->GetStringUTFChars(filename, NULL);
    const std::string file_name_string(c_file_name);
    env->ReleaseStringUTFChars(filename, c_file_name);

    XDFWriter w(file_name_string, SKIP_FILE_HEADER);
    // ^ force non-zero stream ID to avoid writing the file header again

    const streamid_t sid = stream_id_from_index(stream_index);
    w.write_stream_footer(sid, footer);
}
