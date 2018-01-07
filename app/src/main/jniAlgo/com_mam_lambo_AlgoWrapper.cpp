#include "com_mam_lambo_AlgoWrapper.h"

#include <android/log.h>
#include <sstream>
#include <string>
#include <map>
#include <numeric>

#include <chrono>

#include "opencv2/highgui/highgui.hpp"
#include "opencv2/imgproc/imgproc.hpp"

#include "lane_detection.h"

static const char* TAG = "AlgoWrapper_JNI";
using namespace std;
using namespace cv;

typedef struct {
    int imageWidth, imageHeight;
} AlgorithmConfigurationInfo;

typedef struct {
    double fx, fy, cx, cy, radialDistortion[3], tangentialDistortion[2];
} CameraIntrinsicParameters;

enum {
    ResolutionHd,
    ResolutionLd,
    NumberOfResolutions
};

enum {
    ExternalCamera,
    InternalCamera,
    NumberOfCameras
};

int resolution[NumberOfCameras]; /* one external and one internal */

Mat cameraMatrix[NumberOfCameras];
Mat distCoeffs[NumberOfCameras];
Mat unwarpedFrame[NumberOfCameras];

static CameraIntrinsicParameters cameraIntrinsicParameters[2] = {
/* the first is a native 1920x1080 calibration performed by Vaibhav Ghadiok */
    { 825.9843, 826.7130, 966.6968, 514.9236, { -0.3398, 0.1208, -0.0194 }, { -6.5814e-04, 0.0018 } },
/* second is 1280x720 which we obtain from 1920x1080 by scaling */
    { 825.9843, 826.7130, 966.6968 * 1280.0 / 1920.0, 514.9236 * 1280.0 / 1920.0, { -0.3398, 0.1208, -0.0194 }, { -6.5814e-04, 0.0018 } }
};

JNIEXPORT jint JNICALL Java_com_mam_lambo_AlgoWrapper_initializeCameraUnwarping(JNIEnv* env, jobject that,
    jint cameraIndex, jint resolution) {
    cameraMatrix[cameraIndex] = Mat(3, 3, CV_64F);
    distCoeffs[cameraIndex] = Mat(5, 1, CV_64F);
    CameraIntrinsicParameters *params = &cameraIntrinsicParameters[cameraIndex];
    double *matData = (double *)cameraMatrix[cameraIndex].data;
    matData[0] = params->fx;
    matData[1] = 0.0;
    matData[2] = params->cx;
    matData[3] = 0.0;
    matData[4] = params->fy;
    matData[5] = params->cy;
    matData[6] = 0.0;
    matData[7] = 0.0;
    matData[8] = 1.0;
    matData = (double *)distCoeffs[cameraIndex].data;
    matData[0] = params->radialDistortion[0];
    matData[1] = params->radialDistortion[1];
    matData[2] = params->tangentialDistortion[0];
    matData[3] = params->tangentialDistortion[1];
    matData[4] = params->radialDistortion[2];
    return 0;
}

JNIEXPORT jdoubleArray JNICALL Java_com_mam_lambo_AlgoWrapper_getCameraUnwarping(JNIEnv* env, jobject that,
    jint cameraIndex) {
	int numElements1 = cameraMatrix[cameraIndex].rows * cameraMatrix[cameraIndex].cols;
	int numElements2 = distCoeffs[cameraIndex].rows * distCoeffs[cameraIndex].cols;
	int i, j = 0, size = numElements1 + numElements2;
	double *outputBuffer = new double [ size ];
	jdoubleArray jresults = env->NewDoubleArray(size);
    double *matData = (double *)cameraMatrix[cameraIndex].data;
	for(i=0;i<numElements1;++i) { outputBuffer[j++] = matData[i]; }
    matData = (double *)distCoeffs[cameraIndex].data;
	for(i=0;i<numElements2;++i) { outputBuffer[j++] = matData[i]; }
	env->SetDoubleArrayRegion(jresults, 0, size, outputBuffer);
	delete [] outputBuffer;
	return jresults;
}

enum {
    AlgoTailgating,
    AlgoLaneDetection,
    AlgoVehicleDetection,
    AlgoUnwarpExternalImage,
    AlgoUnwarpInternalImage,
    NumberOfAlgos
};

AlgorithmConfigurationInfo algorithmConfigurationInfo[NumberOfAlgos];

extern const int inputVecSize = 1152 * 1080 * 3 / 2;
extern unsigned char **inputVecYuv;
extern int inputVecYuvHead;
extern const int inputVecYuvSize;

void setKappa(float kappa);
void setPosThreshold(int posThreshold);
void tail_gating_init(int imageWidth, int imageHeight, int bufferLength);
float executeTailgating(float *boxes, int numberOfBoxes, float speed, long timeStamp);
float getKappa();
float getPosThreshold();

void log(const char *msg) {
    __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", msg);
}

void laneinitfunc();
void setFiducials(float *data);
std::vector<LaneDetectionResults *> *lanedetectAlgo(cv::Mat &,int,double,double,int,int);

/**
 * This is automatically called by the system when loading the shared library.
 * In the case that the Java class is unloaded, this will be called again.
 */
jint JNI_OnLoad( JavaVM* vm, void* reserved ) {
   // Request a JNI interface object that supports JNI 1.6
   JNIEnv* env;
   if ( vm->GetEnv( reinterpret_cast<void**>( &env ), JNI_VERSION_1_6 )
        != JNI_OK ) {
      __android_log_print(ANDROID_LOG_ERROR, TAG, "Error JNI 1.6 not available" );
       return -1;
   }
   return JNI_VERSION_1_6; // Tell loader that we've loaded successfully and will use v1.6
}

JNIEXPORT jint JNICALL Java_com_mam_lambo_AlgoWrapper_initLane(JNIEnv* env, jobject that, jint jImageWidth, jint jImageHeight) {
	algorithmConfigurationInfo[AlgoLaneDetection].imageWidth = jImageWidth;
	algorithmConfigurationInfo[AlgoLaneDetection].imageHeight = jImageHeight;
	laneinitfunc();
	return 0;
}

JNIEXPORT jint JNICALL Java_com_mam_lambo_AlgoWrapper_initTailgating(JNIEnv* env, jobject that, jint jImageWidth, jint jImageHeight, jint jBufferLength) {
	tail_gating_init(jImageWidth, jImageHeight, jBufferLength);
	algorithmConfigurationInfo[AlgoTailgating].imageWidth = jImageWidth;
	algorithmConfigurationInfo[AlgoTailgating].imageHeight = jImageHeight;
	return AlgoTailgating;
}

JNIEXPORT jint JNICALL Java_com_mam_lambo_AlgoWrapper_setKappa(JNIEnv* env, jobject that, jfloat kappa) {
	setKappa(kappa);
	return 0;
}

JNIEXPORT jfloat JNICALL Java_com_mam_lambo_AlgoWrapper_getKappa(JNIEnv* env, jobject that) {
    float kappa = getKappa();
	return kappa;
}

JNIEXPORT jint JNICALL Java_com_mam_lambo_AlgoWrapper_setPosThreshold(JNIEnv* env, jobject that, jint jPosThreshold) {
	setPosThreshold(jPosThreshold);
	return 0;
}

JNIEXPORT jfloat JNICALL Java_com_mam_lambo_AlgoWrapper_getPosThreshold(JNIEnv* env, jobject that) {
    float posThreshold = getPosThreshold();
	return posThreshold;
}

typedef float BoundingBox[4];

JNIEXPORT jfloat JNICALL Java_com_mam_lambo_AlgoWrapper_executeTailgating(JNIEnv* jenv, jobject that, jfloatArray jBoxes, jint jBoxOffset, jint numberOfBoxes, jfloat jspeed, jlong jnow) {
    jsize inputLen = jenv->GetArrayLength(jBoxes);
    float *boxes = jenv->GetFloatArrayElements(jBoxes, 0);
    int i;
    for(i=0;i<numberOfBoxes;++i) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "box[%d] = (%f, %f, %f, %f)", i,
            boxes[jBoxOffset + 4 * i + 0], boxes[jBoxOffset + 4 * i + 1], boxes[jBoxOffset + 4 * i + 2], boxes[jBoxOffset + 4 * i + 3]);
    }
    float score = executeTailgating(&boxes[jBoxOffset], numberOfBoxes, jspeed, jnow);
    __android_log_print(ANDROID_LOG_ERROR, TAG, "score = %f", score);
    jenv->ReleaseFloatArrayElements(jBoxes, boxes, 0);
    return score;
}

JNIEXPORT jint JNICALL Java_com_mam_lambo_AlgoWrapper_unwarpImage(JNIEnv* env, jobject that, jint cameraIndex, jobject jYBuffer) {
    unsigned char *y = static_cast<unsigned char *>(env->GetDirectBufferAddress(jYBuffer));
    std::chrono::high_resolution_clock::time_point beginTime = std::chrono::high_resolution_clock::now();
    // AlgorithmConfigurationInfo *info = &algorithmConfigurationInfo[AlgoLaneDetection];
	// cv::Mat grayMat = cv::Mat(info->imageHeight, info->imageWidth, CV_8UC1, y);
	int imageHeight = 1080, imageWidth = 1920;
	cv::Mat grayMat = cv::Mat(imageHeight, imageWidth, CV_8UC1, y);
    cv::undistort(grayMat, unwarpedFrame[cameraIndex], cameraMatrix[cameraIndex], distCoeffs[cameraIndex]);
    std::chrono::high_resolution_clock::time_point finalTime = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(finalTime - beginTime).count();
    __android_log_print(ANDROID_LOG_INFO, TAG, "unwarp %d time = %lld", cameraIndex, duration);
    return 0;
}

JNIEXPORT jfloatArray JNICALL Java_com_mam_lambo_AlgoWrapper_execute(JNIEnv* env, jobject that,
    jobject jYBuffer, jobject jUBuffer, jobject jVBuffer, jint index) {

   int nY = env->GetDirectBufferCapacity(jYBuffer);
   int nU = env->GetDirectBufferCapacity(jUBuffer);
   int nV = env->GetDirectBufferCapacity(jVBuffer);

   unsigned char *y = static_cast<unsigned char *>(env->GetDirectBufferAddress(jYBuffer));
   unsigned char *u = static_cast<unsigned char *>(env->GetDirectBufferAddress(jUBuffer));
   unsigned char *v = static_cast<unsigned char *>(env->GetDirectBufferAddress(jVBuffer));

#if 0
	char filename[200];
	snprintf(filename, sizeof(filename), "/data/local/tmp/frame%d.jpg", index);
	__android_log_print(ANDROID_LOG_ERROR, TAG, "filename = %s", filename);
	cv::Mat inputImage = imread(filename, CV_LOAD_IMAGE_COLOR);
	__android_log_print(ANDROID_LOG_ERROR, TAG, "decoded file");
	cv::Mat grayScale;
	cvtColor(inputImage, grayScale, CV_BGR2GRAY);
	size_t iSize = inputImage.total() * inputImage.elemSize();
	size_t oSize = grayScale.total() * grayScale.elemSize();
	__android_log_print(ANDROID_LOG_ERROR, TAG, "transcoded mat to grayscale. size = %zu to %zu", iSize, oSize);
#else
    AlgorithmConfigurationInfo *info = &algorithmConfigurationInfo[AlgoLaneDetection];
	__android_log_print(ANDROID_LOG_ERROR, TAG, "image size = W=%d x H=%d", info->imageWidth, info->imageHeight);
	cv::Mat grayScale = cv::Mat(info->imageHeight, info->imageWidth, CV_8UC1, y);
#endif

	vector<LaneDetectionResults *> *laneDetectionResults = lanedetectAlgo(grayScale, 100, 0, 0, index, 0);
	__android_log_print(ANDROID_LOG_ERROR, TAG, "done!");

	int numElements = laneDetectionResults->size();
	int size = numElements * sizeof(LaneDetectionResults) / sizeof(float);
	float *outputBuffer = new float [ size ];
	jfloatArray jresults = env->NewFloatArray(size);
	int i, j = 0;
	for(i=0;i<numElements;++i) {
		outputBuffer[j++] = laneDetectionResults->at(i)->line[0];
		outputBuffer[j++] = laneDetectionResults->at(i)->line[1];
		outputBuffer[j++] = laneDetectionResults->at(i)->line[2];
		outputBuffer[j++] = laneDetectionResults->at(i)->line[3];
		outputBuffer[j++] = laneDetectionResults->at(i)->ldscore;
		outputBuffer[j++] = laneDetectionResults->at(i)->rdscore;
		delete laneDetectionResults->at(i);
	}
	delete laneDetectionResults;
	env->SetFloatArrayRegion(jresults, 0, size, outputBuffer);
	delete [] outputBuffer;
	return jresults;
}

JNIEXPORT jint JNICALL Java_com_mam_lambo_AlgoWrapper_YuvToMat(JNIEnv* env, jobject that, jobject Y, jobject U, jobject V) {
	int nY = env->GetDirectBufferCapacity(Y);
	int nU = env->GetDirectBufferCapacity(U);
	int nV = env->GetDirectBufferCapacity(V);
	return 0;
}

JNIEXPORT void JNICALL Java_com_mam_lambo_AlgoWrapper_setFiducials(JNIEnv* jenv, jobject that, jfloatArray jinput) {
   jsize inputLen = jenv->GetArrayLength(jinput);
   if(inputLen < 8) {
      ostringstream err;
      __android_log_print(ANDROID_LOG_ERROR, TAG, "setFiducials(insufficient array length)");
      return;
   }

   float *data = jenv->GetFloatArrayElements(jinput, 0);
   if(data == NULL) {
      __android_log_print( ANDROID_LOG_ERROR, TAG, "Couldn't retrieve java tensor");
      return;
   }

   setFiducials(data);
   jenv->ReleaseFloatArrayElements(jinput, data, 0);
}

