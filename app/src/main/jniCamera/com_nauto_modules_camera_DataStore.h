#include <jni.h>

extern "C" {

JNIEXPORT jint JNICALL Java_com_nauto_modules_camera_DataStore_initJni(JNIEnv *jenv, jclass that,
    jint jnBuffers, jlong jbufferSize);

JNIEXPORT jint JNICALL Java_com_nauto_modules_camera_DataStore_closeJni(JNIEnv *jenv, jclass that);

JNIEXPORT jint JNICALL Java_com_nauto_modules_camera_DataStore_writeByteArrayToFile(JNIEnv *jenv, jclass that,
	jint fdIndex, jbyteArray data, jint size);

JNIEXPORT jint JNICALL Java_com_nauto_modules_camera_DataStore_writeByteBufferToFile(JNIEnv *jenv, jclass that,
    jint fdIndex, jobject ByteBuffer, jint jsize);

JNIEXPORT jint JNICALL Java_com_nauto_modules_camera_DataStore_openFile(JNIEnv *jenv, jclass that,
    jstring fileName);

JNIEXPORT jint JNICALL Java_com_nauto_modules_camera_DataStore_closeFile(JNIEnv *jenv, jclass that,
    jint fd);

JNIEXPORT jintArray JNICALL Java_com_nauto_modules_camera_DataStore_writeFile(JNIEnv *jenv, jclass that,
	jint fd);

}
