#include <jni.h>

extern "C" {

	JNIEXPORT void JNICALL Java_com_nauto_modules_camera_CameraModule_fillFrameData(JNIEnv *jenv,
		jclass that, jobject jFrameData,
		jobject YBufferL, jobject UVBufferL,
		jobject YBufferR, jobject UVBufferR,
		jbyteArray YvuRear, jbyteArray YvuFront, jintArray frontFrame,
		jint fillDualYuv, jint fillRearYuv, jint fillFrontYuv, jint fillFrontFrame);

	JNIEXPORT int JNICALL Java_com_nauto_modules_camera_CameraModule_reverseImage(JNIEnv *jenv, jclass that, jint flag);

#if 0

    JNIEXPORT void JNICALL Java_com_nauto_modules_camera_CameraModule_copyFrameData(JNIEnv *jenv, jclass that,
        jbyteArray jdstFrameData,
        jbyteArray jsrcFrameData,
        jint cols, jint rows, jint frontRear);

    JNIEXPORT void JNICALL Java_com_nauto_modules_camera_CameraModule_fillJpegSingleInputBuffer(JNIEnv *jenv, jclass that,
        jobject jFrameData, jobject jYBuffer, jobject jUVBuffer);

    JNIEXPORT void JNICALL Java_com_nauto_modules_camera_CameraModule_fillJpegInputBuffer(JNIEnv *jenv, jclass that,
        jbyteArray jdst, jobject jsrc);

#endif

}
