#include <jni.h>
#include <string>
#include <iostream>
#include <cstring>
#include "xdfwriter.h"

// a non-zero value to let the constructor of class XDFWriter skip the file header
static const int SKIP_FILE_HEADER = 1;

streamid_t stream_id_from_index(jint index);

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_LSLService_createXdfFile(
        JNIEnv* env,
        jobject /* this */,
        jstring filename,
        jfloatArray arr,
        jdoubleArray arr2,
        jstring metadata,
        jstring stream_footer,
        jstring lastvalue,
        jint stream_index,
        jint channelCount
) {
    std::vector<float > vFloats;
    std::vector<double> vDoubles;

    jboolean isCopy;
    const char *convertedValue = (env)->GetStringUTFChars(filename, &isCopy);

    const char *convertedMetadata = (env)->GetStringUTFChars(metadata, &isCopy);
    std::string MedaDataStream  = std::string(convertedMetadata, strlen(convertedMetadata));

    const char *convertedstreamFooter = (env)->GetStringUTFChars(stream_footer, &isCopy);
    std::string MedaDataStreamFooter  = std::string(convertedstreamFooter, strlen(convertedstreamFooter));

    const char *convertedlastValue = (env)->GetStringUTFChars(lastvalue, &isCopy);
    std::string lastValue  = std::string(convertedlastValue, strlen(convertedlastValue));

    std::stringstream ss1(convertedlastValue); // construct stringstream object with string

    double lastValueDouble;
    ss1 >> lastValueDouble;

    unsigned int chanelCountTotal = (unsigned int)channelCount;

    XDFWriter w(convertedValue, SKIP_FILE_HEADER);

    const streamid_t sid = stream_id_from_index(stream_index);

    //for assigning  float
    jsize sz = env->GetArrayLength(arr);
    float* float_elems = env->GetFloatArrayElements(arr, 0);
    vFloats.assign(float_elems, float_elems+sz);

    //for assigning doubles
    jsize sz1 = env->GetArrayLength(arr2);
    double* double_elems = env->GetDoubleArrayElements(arr2, 0);
    vDoubles.assign(double_elems, double_elems+sz1);
    w.write_data_chunk(sid, vDoubles, vFloats, chanelCountTotal);

    return env->NewStringUTF(convertedValue);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_LSLService_createXdfFileInt(
        JNIEnv* env,
        jobject /* this */,
        jstring filename,
        jintArray arr,
        jdoubleArray arr2,
        jstring metadata,
        jstring stream_footer,
        jstring lastvalue,
        jint stream_index,
        jint channelCount
) {
    std::vector<int16_t> vInts;
    std::vector<double> vDoubles;

    jboolean isCopy;
    const char *convertedValue = (env)->GetStringUTFChars(filename, &isCopy);

    const char *convertedMetadata = (env)->GetStringUTFChars(metadata, &isCopy);
    std::string MedaDataStream  = std::string(convertedMetadata, strlen(convertedMetadata));

    const char *convertedstreamFooter = (env)->GetStringUTFChars(stream_footer, &isCopy);
    std::string MedaDataStreamFooter  = std::string(convertedstreamFooter, strlen(convertedstreamFooter));

    const char *convertedlastValue = (env)->GetStringUTFChars(lastvalue, &isCopy);
    std::string lastValue  = std::string(convertedlastValue, strlen(convertedlastValue));

    std::stringstream ss1(convertedlastValue); // construct stringstream object with string

    double lastValueDouble;
    ss1 >> lastValueDouble;

    unsigned int chanelCountTotal = (unsigned int)channelCount;

    XDFWriter w(convertedValue, SKIP_FILE_HEADER);
    const streamid_t sid = stream_id_from_index(stream_index);

//    w.write_stream_header(sid, convertedMetadata);
//    w.write_boundary_chunk();

    //for assigning  float
    jsize sz = env->GetArrayLength(arr);
    int* int_elems = env->GetIntArrayElements(arr, 0);
    vInts.assign(int_elems, int_elems+sz);

    //for assigning doubles
    jsize sz1 = env->GetArrayLength(arr2);
    double* double_elems = env->GetDoubleArrayElements(arr2, 0);
    vDoubles.assign(double_elems, double_elems+sz1);
    w.write_data_chunk(sid, vDoubles, vInts, chanelCountTotal);

//    w.write_boundary_chunk();
//    w.write_stream_offset(sid, lastValueDouble, offsetDouble);

//    const std::string footer(convertedstreamFooter);
//    w.write_stream_footer(sid, footer);
    return env->NewStringUTF(convertedValue);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_LSLService_createXdfFileDouble(
        JNIEnv* env,
        jobject /* this */,
        jstring filename,
        jdoubleArray arr,
        jdoubleArray arr2,
        jstring metadata,
        jstring stream_footer,
        jstring lastvalue,
        jint stream_index,
        jint channelCount
) {
    std::vector<double > vDoubleValues;
    std::vector<double> vDoubles;

    jboolean isCopy;
    const char *convertedValue = (env)->GetStringUTFChars(filename, &isCopy);

    const char *convertedMetadata = (env)->GetStringUTFChars(metadata, &isCopy);
    std::string MedaDataStream  = std::string(convertedMetadata, strlen(convertedMetadata));

    const char *convertedstreamFooter = (env)->GetStringUTFChars(stream_footer, &isCopy);
    std::string MedaDataStreamFooter  = std::string(convertedstreamFooter, strlen(convertedstreamFooter));

    const char *convertedlastValue = (env)->GetStringUTFChars(lastvalue, &isCopy);
    std::string lastValue  = std::string(convertedlastValue, strlen(convertedlastValue));

    std::stringstream ss1(convertedlastValue); // construct stringstream object with string

    double lastValueDouble;
    ss1 >> lastValueDouble;

    unsigned int chanelCountTotal = (unsigned int)channelCount;

    XDFWriter w(convertedValue, SKIP_FILE_HEADER);
    const streamid_t sid = stream_id_from_index(stream_index);

//    w.write_stream_header(sid, convertedMetadata);
//    w.write_boundary_chunk();

    //for assigning  float
    jsize sz = env->GetArrayLength(arr);
    double* float_elems = env->GetDoubleArrayElements(arr, 0);
    vDoubleValues.assign(float_elems, float_elems+sz);

    //for assigning doubles
    jsize sz1 = env->GetArrayLength(arr2);
    double* double_elems = env->GetDoubleArrayElements(arr2, 0);
    vDoubles.assign(double_elems, double_elems+sz1);
    w.write_data_chunk(sid, vDoubles, vDoubleValues, chanelCountTotal);

//    w.write_boundary_chunk();
//    w.write_stream_offset(sid, lastValueDouble, offsetDouble);

//    const std::string footer(convertedstreamFooter);
//    w.write_stream_footer(sid, footer);

    return env->NewStringUTF(convertedValue);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_LSLService_createXdfFileByte(
        JNIEnv* env,
        jobject /* this */,
        jstring filename,
        jbyteArray arr,
        jdoubleArray arr2,
        jstring metadata,
        jstring stream_footer,
        jstring lastvalue,
        jint stream_index,
        jint channelCount
) {
    std::vector<jbyte> vBytes;
    std::vector<double> vDoubles;

    jboolean isCopy;
    const char *convertedValue = (env)->GetStringUTFChars(filename, &isCopy);

    const char *convertedMetadata = (env)->GetStringUTFChars(metadata, &isCopy);
    std::string MedaDataStream  = std::string(convertedMetadata, strlen(convertedMetadata));

    const char *convertedstreamFooter = (env)->GetStringUTFChars(stream_footer, &isCopy);
    std::string MedaDataStreamFooter  = std::string(convertedstreamFooter, strlen(convertedstreamFooter));

    const char *convertedlastValue = (env)->GetStringUTFChars(lastvalue, &isCopy);
    std::string lastValue  = std::string(convertedlastValue, strlen(convertedlastValue));

    std::stringstream ss1(convertedlastValue); // construct stringstream object with string

    double lastValueDouble;
    ss1 >> lastValueDouble;

    unsigned int chanelCountTotal = (unsigned int)channelCount;

    XDFWriter w(convertedValue, SKIP_FILE_HEADER);
    const streamid_t sid = stream_id_from_index(stream_index);

//    w.write_stream_header(sid, convertedMetadata);
//    w.write_boundary_chunk();

    //for assigning  float
    jsize sz = env->GetArrayLength(arr);
    jbyte * byte_elems = env->GetByteArrayElements(arr, 0);
    vBytes.assign(byte_elems, byte_elems+sz);

    //for assigning doubles
    jsize sz1 = env->GetArrayLength(arr2);
    double* double_elems = env->GetDoubleArrayElements(arr2, 0);
    vDoubles.assign(double_elems, double_elems+sz1);
    w.write_data_chunk(sid, vDoubles, vBytes, chanelCountTotal);

//    w.write_boundary_chunk();
//    w.write_stream_offset(sid, lastValueDouble, offsetDouble);

//    const std::string footer(convertedstreamFooter);
//    w.write_stream_footer(sid, footer);
    return env->NewStringUTF(convertedValue);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_LSLService_createXdfFileShort(
        JNIEnv* env,
        jobject /* this */,
        jstring filename,
        jshortArray arr,
        jdoubleArray arr2,
        jstring metadata,
        jstring stream_footer,
        jstring lastvalue,
        jint stream_index,
        jint channelCount
) {
    std::vector<short> vShorts;
    std::vector<double> vDoubles;

    jboolean isCopy;
    const char *convertedValue = (env)->GetStringUTFChars(filename, &isCopy);

    const char *convertedMetadata = (env)->GetStringUTFChars(metadata, &isCopy);
    std::string MedaDataStream  = std::string(convertedMetadata, strlen(convertedMetadata));

    const char *convertedstreamFooter = (env)->GetStringUTFChars(stream_footer, &isCopy);
    std::string MedaDataStreamFooter  = std::string(convertedstreamFooter, strlen(convertedstreamFooter));

    const char *convertedlastValue = (env)->GetStringUTFChars(lastvalue, &isCopy);
    std::string lastValue  = std::string(convertedlastValue, strlen(convertedlastValue));

    std::stringstream ss1(convertedlastValue); // construct stringstream object with string

    double lastValueDouble;
    ss1 >> lastValueDouble;

    unsigned int chanelCountTotal = (unsigned int)channelCount;

    XDFWriter w(convertedValue, SKIP_FILE_HEADER);
    const streamid_t sid = stream_id_from_index(stream_index);

//    w.write_stream_header(sid, convertedMetadata);
//    w.write_boundary_chunk();

    //for assigning  float
    jsize sz = env->GetArrayLength(arr);
    short* short_elems = env->GetShortArrayElements(arr, 0);
    vShorts.assign(short_elems, short_elems+sz);

    //for assigning doubles
    jsize sz1 = env->GetArrayLength(arr2);
    double* double_elems = env->GetDoubleArrayElements(arr2, 0);
    vDoubles.assign(double_elems, double_elems+sz1);
    w.write_data_chunk(sid, vDoubles, vShorts, chanelCountTotal);

//    w.write_boundary_chunk();
//    w.write_stream_offset(sid, lastValueDouble, offsetDouble);

//    const std::string footer(convertedstreamFooter);
//    w.write_stream_footer(sid, footer);
    return env->NewStringUTF(convertedValue);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_LSLService_createXdfFileString(
        JNIEnv* env,
        jobject /* this */,
        jstring filename,
        jobjectArray arr,
        jdoubleArray arr2,
        jstring metadata,
        jstring stream_footer,
        jstring lastvalue,
        jint stream_index,
        jint channelCount
) {
    std::vector<std::string> vString;
    std::vector<double> vDoubles;

    jboolean isCopy;
    const char *convertedValue = (env)->GetStringUTFChars(filename, &isCopy);

    const char *convertedMetadata = (env)->GetStringUTFChars(metadata, &isCopy);
    std::string MedaDataStream  = std::string(convertedMetadata, strlen(convertedMetadata));

    const char *convertedstreamFooter = (env)->GetStringUTFChars(stream_footer, &isCopy);
    std::string MedaDataStreamFooter  = std::string(convertedstreamFooter, strlen(convertedstreamFooter));

    const char *convertedlastValue = (env)->GetStringUTFChars(lastvalue, &isCopy);
    std::string lastValue  = std::string(convertedlastValue, strlen(convertedlastValue));

    std::stringstream ss1(convertedlastValue); // construct stringstream object with string

    double lastValueDouble;
    ss1 >> lastValueDouble;

    XDFWriter w(convertedValue, SKIP_FILE_HEADER);
    const streamid_t sid = stream_id_from_index(stream_index);

//    w.write_stream_header(sid, convertedMetadata);
//    w.write_boundary_chunk();

    //for assigning  float
    jsize strArrayLen = env->GetArrayLength(arr);
    for (int i = 0; i < strArrayLen; ++i)
    {
        jstring jip = static_cast<jstring>((env)->GetObjectArrayElement(arr, i));
        const char* ip = (env)->GetStringUTFChars(jip, NULL);
        vString.push_back(ip);
    }

    //for assigning doubles
    jsize sz1 = env->GetArrayLength(arr2);
    double* double_elems = env->GetDoubleArrayElements(arr2, 0);
    vDoubles.assign(double_elems, double_elems+sz1);
    w.write_data_chunk(sid, vDoubles, vString, 1);

//    w.write_boundary_chunk();
//    w.write_stream_offset(sid, lastValueDouble, offsetDouble);

//    const std::string footer(convertedstreamFooter);
//    w.write_stream_footer(sid, footer);
    return env->NewStringUTF(convertedValue);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_aliayubkhan_LSLReceiver_LSLService_writeStreamOffset(
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
Java_com_example_aliayubkhan_LSLReceiver_LSLService_writeStreamHeader(
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
Java_com_example_aliayubkhan_LSLReceiver_LSLService_writeStreamFooter(
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
