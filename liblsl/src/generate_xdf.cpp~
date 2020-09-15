#include <jni.h>
#include <string>
#include <iostream>
#include <cstring>
#include "xdfwriter.h"
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_SettingsActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */,
        jstring temp){


    std::string hello = "Hello from C++";
    return temp;
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_SettingsActivity_createXdfFile
        (
        JNIEnv* env,
        jobject /* this */,
        jstring temp,
        //jfloatArray arr
        jintArray arr,
        //jstring metadata
        jint count
) {

    std::vector<int16_t > vFloats;

    jboolean isCopy;
    const char *convertedValue = (env)->GetStringUTFChars(temp, &isCopy);
    std::string string123  = std::string(convertedValue, strlen(convertedValue));

    //const char *convertedMetadata = (env)->GetStringUTFChars(metadata, &isCopy);
    //std::string MedaDataStream  = std::string(convertedValue, strlen(convertedValue));


    XDFWriter w(convertedValue, count); //do we need to change something here??yeah i know
    const uint32_t sid = 0x02C0FFEE;

    const std::string footer(
            "<?xml version=\"1.0\"?>"
            "<info>"
            "<first_timestamp>5.1</first_timestamp>"
            "<last_timestamp>5.9</last_timestamp>"
            "<sample_count>9</sample_count>"
            "<clock_offsets>"
            "<offset><time>50979.7660030605</time><value>-3.436503902776167e-06</value></offset>"
            "</clock_offsets></info>");

    std::vector<double> ts{5.2, 0, 0, 5.5};
    if(count ==1) {
        w.write_stream_header(0, "<?xml version=\"1.0\"?>"
                                 "<info>"
                                 "<name>SendDataC</name>"
                                 "<type>EEG</type>"
                                 "<channel_count>3</channel_count>"
                                 "<nominal_srate>10</nominal_srate>"
                                 "<channel_format>int16</channel_format>"
                                 "<created_at>50942.723319709003</created_at>"
                                 "</info>");

        w.write_boundary_chunk();


        jsize sz = env->GetArrayLength(arr);
        int *float_elems = env->GetIntArrayElements(arr, 0);
        vFloats.assign(float_elems, float_elems + sz);

        std::vector<int16_t> data{12, 22, 32, 13, 23, 33, 14, 24, 34, 15, 25, 35};
        w.write_data_chunk(0, ts, data, 3);
//    w.write_data_chunk(sid, ts, data_str, 1);

        // write data from nested vectors
//    ts = std::vector<double>{5.6, 0, 0, 0};
//    std::vector<std::vector<int16_t>> data2{{12, 22, 32}, {13, 23, 33}, {14, 24, 34}, {15, 25, 35}};
//    std::vector<std::vector<std::string>> data2_str{{"Hello"}, {"World"}, {"from"}, {"LSL"}};
//    w.write_data_chunk_nested(0, ts, data2);
//    w.write_data_chunk_nested(sid, ts, data2_str);

        w.write_boundary_chunk();
        w.write_stream_offset(0, 6, -.1);
//    w.write_stream_offset(sid, 5, -.2);

        w.write_stream_footer(0, footer);
//    w.write_stream_footer(sid, footer);
    }

    if (count == 2) {
        w.write_stream_header(sid, "<?xml version=\"1.0\"?>"
                                   "<info>"
                                   "<name>SendDataString</name>"
                                   "<type>StringMarker</type>"
                                   "<channel_count>1</channel_count>"
                                   "<nominal_srate>10</nominal_srate>"
                                   "<channel_format>string</channel_format>"
                                   "<created_at>50942.723319709003</created_at>"
                                   "</info>");


        w.write_boundary_chunk();
        std::vector<std::string> data_str{"Hello", "World", "from", "LSL"};
        w.write_data_chunk(sid, ts, data_str, 1);
        w.write_boundary_chunk();
        w.write_stream_offset(sid, 5, -.2);
        w.write_stream_footer(sid, footer);
    }

    return env->NewStringUTF(convertedValue);

}
extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_LSLService_createXdfFile(
        JNIEnv* env,
        jobject /* this */,
        jstring temp,
        jfloatArray arr,
        //jintArray arr,
        jdoubleArray arr2,
        jstring metadata,
        jstring streamFooter,
        jstring offset,
        jstring lastvalue,
        jint count,
        jint channelCount
) {
// FILE* file = fopen("/sdcard/hello.txt","w+");

    std::vector<float > vFloats;
    std::vector<double> vDoubles;


//
//    for (jsize index=0; index<sz;index++) {
//        vFloats.push_back((float)float_elems[index]);
//    }

    jboolean isCopy;
    const char *convertedValue = (env)->GetStringUTFChars(temp, &isCopy);
    std::string string123  = std::string(convertedValue, strlen(convertedValue));

    const char *convertedMetadata = (env)->GetStringUTFChars(metadata, &isCopy);
    std::string MedaDataStream  = std::string(convertedMetadata, strlen(convertedMetadata));

    const char *convertedstreamFooter = (env)->GetStringUTFChars(streamFooter, &isCopy);
    std::string MedaDataStreamFooter  = std::string(convertedstreamFooter, strlen(convertedstreamFooter));

    const char *convertedoffset = (env)->GetStringUTFChars(offset, &isCopy);
    std::string offsetValue  = std::string(convertedoffset, strlen(convertedoffset));

    const char *convertedlastValue = (env)->GetStringUTFChars(lastvalue, &isCopy);
    std::string lastValue  = std::string(convertedlastValue, strlen(convertedlastValue));

    std::stringstream ss(convertedoffset); // construct stringstream object with string

    double offsetDouble;
    ss >> offsetDouble;

    std::stringstream ss1(convertedlastValue); // construct stringstream object with string

    double lastValueDouble;
    ss1 >> lastValueDouble;

    unsigned int chanelCountTotal = (unsigned int)channelCount;

    //int chanelCountTotal = (int)channelCount;

    XDFWriter w(convertedValue,count); //do we need to change something here??yeah i know

    const uint32_t sid = 0x02C0FFEE;

    const std::string footer(convertedstreamFooter);


    w.write_stream_header(0, convertedMetadata);


    w.write_boundary_chunk();

    //for assigning  float
    jsize sz = env->GetArrayLength(arr);
    float* float_elems = env->GetFloatArrayElements(arr, 0);
    vFloats.assign(float_elems, float_elems+sz);

    //for assigning doubles
    jsize sz1 = env->GetArrayLength(arr2);
    double* double_elems = env->GetDoubleArrayElements(arr2, 0);
    vDoubles.assign(double_elems, double_elems+sz1);

    w.write_data_chunk(0, vDoubles, vFloats, chanelCountTotal);

    w.write_boundary_chunk();
    w.write_stream_offset(0, lastValueDouble, offsetDouble);

    w.write_stream_footer(0, footer);
    return env->NewStringUTF(convertedValue);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_LSLService_createXdfFileInt(
        JNIEnv* env,
        jobject /* this */,
        jstring temp,
        jintArray arr,
        //jintArray arr,
        jdoubleArray arr2,
        jstring metadata,
        jstring streamFooter,
        jstring offset,
        jstring lastvalue,
        jint count,
        jint channelCount
) {
// FILE* file = fopen("/sdcard/hello.txt","w+");

    std::vector<int16_t> vInts;
    std::vector<double> vDoubles;

    jboolean isCopy;
    const char *convertedValue = (env)->GetStringUTFChars(temp, &isCopy);
    std::string string123  = std::string(convertedValue, strlen(convertedValue));

    const char *convertedMetadata = (env)->GetStringUTFChars(metadata, &isCopy);
    std::string MedaDataStream  = std::string(convertedMetadata, strlen(convertedMetadata));

    const char *convertedstreamFooter = (env)->GetStringUTFChars(streamFooter, &isCopy);
    std::string MedaDataStreamFooter  = std::string(convertedstreamFooter, strlen(convertedstreamFooter));

    const char *convertedoffset = (env)->GetStringUTFChars(offset, &isCopy);
    std::string offsetValue  = std::string(convertedoffset, strlen(convertedoffset));

    const char *convertedlastValue = (env)->GetStringUTFChars(lastvalue, &isCopy);
    std::string lastValue  = std::string(convertedlastValue, strlen(convertedlastValue));

    std::stringstream ss(convertedoffset); // construct stringstream object with string

    double offsetDouble;
    ss >> offsetDouble;

    std::stringstream ss1(convertedlastValue); // construct stringstream object with string

    double lastValueDouble;
    ss1 >> lastValueDouble;

    unsigned int chanelCountTotal = (unsigned int)channelCount;

    //int chanelCountTotal = (int)channelCount;
    XDFWriter w(convertedValue,count); //do we need to change something here??yeah i know
    const uint32_t sid = 0x02C0FFEE;

    const std::string footer(convertedstreamFooter);

    w.write_stream_header(0, convertedMetadata);

    w.write_boundary_chunk();

    //for assigning  float
    jsize sz = env->GetArrayLength(arr);
    int* int_elems = env->GetIntArrayElements(arr, 0);
    vInts.assign(int_elems, int_elems+sz);

    //for assigning doubles
    jsize sz1 = env->GetArrayLength(arr2);
    double* double_elems = env->GetDoubleArrayElements(arr2, 0);
    vDoubles.assign(double_elems, double_elems+sz1);

    w.write_data_chunk(0, vDoubles, vInts, chanelCountTotal);

    w.write_boundary_chunk();
    w.write_stream_offset(0, lastValueDouble, offsetDouble);

    w.write_stream_footer(0, footer);
    return env->NewStringUTF(convertedValue);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_LSLService_createXdfFileDouble(
        JNIEnv* env,
        jobject /* this */,
        jstring temp,
        jdoubleArray arr,
        //jintArray arr,
        jdoubleArray arr2,
        jstring metadata,
        jstring streamFooter,
        jstring offset,
        jstring lastvalue,
        jint count,
        jint channelCount
) {
// FILE* file = fopen("/sdcard/hello.txt","w+");

    std::vector<double > vDoubleValues;
    std::vector<double> vDoubles;


    jboolean isCopy;
    const char *convertedValue = (env)->GetStringUTFChars(temp, &isCopy);
    std::string string123  = std::string(convertedValue, strlen(convertedValue));

    const char *convertedMetadata = (env)->GetStringUTFChars(metadata, &isCopy);
    std::string MedaDataStream  = std::string(convertedMetadata, strlen(convertedMetadata));

    const char *convertedstreamFooter = (env)->GetStringUTFChars(streamFooter, &isCopy);
    std::string MedaDataStreamFooter  = std::string(convertedstreamFooter, strlen(convertedstreamFooter));

    const char *convertedoffset = (env)->GetStringUTFChars(offset, &isCopy);
    std::string offsetValue  = std::string(convertedoffset, strlen(convertedoffset));

    const char *convertedlastValue = (env)->GetStringUTFChars(lastvalue, &isCopy);
    std::string lastValue  = std::string(convertedlastValue, strlen(convertedlastValue));

    std::stringstream ss(convertedoffset); // construct stringstream object with string

    double offsetDouble;
    ss >> offsetDouble;

    std::stringstream ss1(convertedlastValue); // construct stringstream object with string

    double lastValueDouble;
    ss1 >> lastValueDouble;

    unsigned int chanelCountTotal = (unsigned int)channelCount;

    //int chanelCountTotal = (int)channelCount;
    XDFWriter w(convertedValue,count); //do we need to change something here??yeah i know
    const uint32_t sid = 0x02C0FFEE;


    const std::string footer(convertedstreamFooter);


    w.write_stream_header(0, convertedMetadata);


    w.write_boundary_chunk();



    //for assigning  float
    jsize sz = env->GetArrayLength(arr);
    double* float_elems = env->GetDoubleArrayElements(arr, 0);
    vDoubleValues.assign(float_elems, float_elems+sz);

    //for assigning doubles
    jsize sz1 = env->GetArrayLength(arr2);
    double* double_elems = env->GetDoubleArrayElements(arr2, 0);
    vDoubles.assign(double_elems, double_elems+sz1);

    w.write_data_chunk(0, vDoubles, vDoubleValues, chanelCountTotal);


    w.write_boundary_chunk();
    w.write_stream_offset(0, lastValueDouble, offsetDouble);


    w.write_stream_footer(0, footer);

    return env->NewStringUTF(convertedValue);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_LSLService_createXdfFileByte(
        JNIEnv* env,
        jobject /* this */,
        jstring temp,
        jbyteArray arr,
        //jintArray arr,
        jdoubleArray arr2,
        jstring metadata,
        jstring streamFooter,
        jstring offset,
        jstring lastvalue,
        jint count,
        jint channelCount
) {
// FILE* file = fopen("/sdcard/hello.txt","w+");

    std::vector<jbyte> vBytes;
    std::vector<double> vDoubles;


    jboolean isCopy;
    const char *convertedValue = (env)->GetStringUTFChars(temp, &isCopy);
    std::string string123  = std::string(convertedValue, strlen(convertedValue));

    const char *convertedMetadata = (env)->GetStringUTFChars(metadata, &isCopy);
    std::string MedaDataStream  = std::string(convertedMetadata, strlen(convertedMetadata));

    const char *convertedstreamFooter = (env)->GetStringUTFChars(streamFooter, &isCopy);
    std::string MedaDataStreamFooter  = std::string(convertedstreamFooter, strlen(convertedstreamFooter));

    const char *convertedoffset = (env)->GetStringUTFChars(offset, &isCopy);
    std::string offsetValue  = std::string(convertedoffset, strlen(convertedoffset));

    const char *convertedlastValue = (env)->GetStringUTFChars(lastvalue, &isCopy);
    std::string lastValue  = std::string(convertedlastValue, strlen(convertedlastValue));

    std::stringstream ss(convertedoffset); // construct stringstream object with string

    double offsetDouble;
    ss >> offsetDouble;

    std::stringstream ss1(convertedlastValue); // construct stringstream object with string

    double lastValueDouble;
    ss1 >> lastValueDouble;

    unsigned int chanelCountTotal = (unsigned int)channelCount;

    //int chanelCountTotal = (int)channelCount;
    XDFWriter w(convertedValue,count); //do we need to change something here??yeah i know
    const uint32_t sid = 0x02C0FFEE;


    const std::string footer(convertedstreamFooter);


    w.write_stream_header(0, convertedMetadata);


    w.write_boundary_chunk();



    //for assigning  float
    jsize sz = env->GetArrayLength(arr);
    jbyte * byte_elems = env->GetByteArrayElements(arr, 0);
    vBytes.assign(byte_elems, byte_elems+sz);

    //for assigning doubles
    jsize sz1 = env->GetArrayLength(arr2);
    double* double_elems = env->GetDoubleArrayElements(arr2, 0);
    vDoubles.assign(double_elems, double_elems+sz1);

    w.write_data_chunk(0, vDoubles, vBytes, chanelCountTotal);


    w.write_boundary_chunk();
    w.write_stream_offset(0, lastValueDouble, offsetDouble);


    w.write_stream_footer(0, footer);
    return env->NewStringUTF(convertedValue);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_LSLService_createXdfFileShort(
        JNIEnv* env,
        jobject /* this */,
        jstring temp,
        jshortArray arr,
        //jintArray arr,
        jdoubleArray arr2,
        jstring metadata,
        jstring streamFooter,
        jstring offset,
        jstring lastvalue,
        jint count,
        jint channelCount
) {
// FILE* file = fopen("/sdcard/hello.txt","w+");

    std::vector<short> vShorts;
    std::vector<double> vDoubles;


    jboolean isCopy;
    const char *convertedValue = (env)->GetStringUTFChars(temp, &isCopy);
    std::string string123  = std::string(convertedValue, strlen(convertedValue));

    const char *convertedMetadata = (env)->GetStringUTFChars(metadata, &isCopy);
    std::string MedaDataStream  = std::string(convertedMetadata, strlen(convertedMetadata));

    const char *convertedstreamFooter = (env)->GetStringUTFChars(streamFooter, &isCopy);
    std::string MedaDataStreamFooter  = std::string(convertedstreamFooter, strlen(convertedstreamFooter));

    const char *convertedoffset = (env)->GetStringUTFChars(offset, &isCopy);
    std::string offsetValue  = std::string(convertedoffset, strlen(convertedoffset));

    const char *convertedlastValue = (env)->GetStringUTFChars(lastvalue, &isCopy);
    std::string lastValue  = std::string(convertedlastValue, strlen(convertedlastValue));

    std::stringstream ss(convertedoffset); // construct stringstream object with string

    double offsetDouble;
    ss >> offsetDouble;

    std::stringstream ss1(convertedlastValue); // construct stringstream object with string

    double lastValueDouble;
    ss1 >> lastValueDouble;

    unsigned int chanelCountTotal = (unsigned int)channelCount;

    //int chanelCountTotal = (int)channelCount;
    XDFWriter w(convertedValue,count); //do we need to change something here??yeah i know
    const uint32_t sid = 0x02C0FFEE;


    const std::string footer(convertedstreamFooter);


    w.write_stream_header(0, convertedMetadata);


    w.write_boundary_chunk();


    //for assigning  float
    jsize sz = env->GetArrayLength(arr);
    short* short_elems = env->GetShortArrayElements(arr, 0);
    vShorts.assign(short_elems, short_elems+sz);

    //for assigning doubles
    jsize sz1 = env->GetArrayLength(arr2);
    double* double_elems = env->GetDoubleArrayElements(arr2, 0);
    vDoubles.assign(double_elems, double_elems+sz1);

    w.write_data_chunk(0, vDoubles, vShorts, chanelCountTotal);


    w.write_boundary_chunk();
    w.write_stream_offset(0, lastValueDouble, offsetDouble);


    w.write_stream_footer(0, footer);
    return env->NewStringUTF(convertedValue);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_aliayubkhan_LSLReceiver_LSLService_createXdfFileString(
        JNIEnv* env,
        jobject /* this */,
        jstring temp,
        jobjectArray arr,
        //jintArray arr,
        jdoubleArray arr2,
        jstring metadata,
        jstring streamFooter,
        jstring offset,
        jstring lastvalue,
        jint count,
        jint channelCount
) {
// FILE* file = fopen("/sdcard/hello.txt","w+");


    std::vector<std::string> vString;
    std::vector<double> vDoubles;


    jboolean isCopy;



    const char *convertedValue = (env)->GetStringUTFChars(temp, &isCopy);
    std::string string123  = std::string(convertedValue, strlen(convertedValue));

    const char *convertedMetadata = (env)->GetStringUTFChars(metadata, &isCopy);
    std::string MedaDataStream  = std::string(convertedMetadata, strlen(convertedMetadata));

    const char *convertedstreamFooter = (env)->GetStringUTFChars(streamFooter, &isCopy);
    std::string MedaDataStreamFooter  = std::string(convertedstreamFooter, strlen(convertedstreamFooter));

    const char *convertedoffset = (env)->GetStringUTFChars(offset, &isCopy);
    std::string offsetValue  = std::string(convertedoffset, strlen(convertedoffset));

    const char *convertedlastValue = (env)->GetStringUTFChars(lastvalue, &isCopy);
    std::string lastValue  = std::string(convertedlastValue, strlen(convertedlastValue));

    std::stringstream ss(convertedoffset); // construct stringstream object with string

    double offsetDouble;
    ss >> offsetDouble;

    std::stringstream ss1(convertedlastValue); // construct stringstream object with string

    double lastValueDouble;
    ss1 >> lastValueDouble;

    unsigned int chanelCountTotal = (unsigned int)channelCount;

    //int chanelCountTotal = (int)channelCount;
    XDFWriter w(convertedValue,count); //do we need to change something here??yeah i know
    const uint32_t sid = 0x02C0FFEE;


    const std::string footer(convertedstreamFooter);


    w.write_stream_header(0, convertedMetadata);


    w.write_boundary_chunk();


    //for assigning  float

    jsize strArrayLen = env->GetArrayLength(arr);

    for (int i = 0; i < strArrayLen; ++i)
    {
        jstring jip = static_cast<jstring>((env)->GetObjectArrayElement(arr, i));
        const char* ip = (env)->GetStringUTFChars(jip, NULL);
        vString.push_back(ip);
    }

   // v.push_back( "Some string" );


    //for assigning doubles
    jsize sz1 = env->GetArrayLength(arr2);
    double* double_elems = env->GetDoubleArrayElements(arr2, 0);
    vDoubles.assign(double_elems, double_elems+sz1);

    w.write_data_chunk(sid, vDoubles, vString, 1);


    w.write_boundary_chunk();
    w.write_stream_offset(sid, lastValueDouble, offsetDouble);

    w.write_stream_footer(sid, footer);
    return env->NewStringUTF(convertedValue);
}