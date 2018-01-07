// #include <jni.h>
#include <com_nauto_modules_camera_DataStore.h>

#include <algorithm>
#include <cstring>
#include <string>
// #include <stdlib.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <jni.h>

extern "C" {

/*** MediaCodec ***/

static unsigned char **fileBuffers = 0;
static size_t *fileBufferPosition = 0;
static int fileBufferHead = 0;
static int nBuffers = 0;
static long int bufferSize = 0;
static const int ReservedFileDescriptor = -3;
static const int InvalidFileDescriptor = -2;
static const int UnusedFileDescriptor = -1;
static int started = 0;
std::string *fileNames;

std::string ConvertJString(JNIEnv *jenv, jstring str)
{
	const jsize len = jenv->GetStringUTFLength(str);
	const char* strChars = jenv->GetStringUTFChars(str, (jboolean *)0);
	std::string Result(strChars, len);
	jenv->ReleaseStringUTFChars(str, strChars);
	return Result;
}

JNIEXPORT jint JNICALL Java_com_nauto_modules_camera_DataStore_initJni(JNIEnv *jenv, jclass that,
	jint jnBuffers, jlong jbufferSize)
{
	/* buffer management */
	nBuffers = jnBuffers;
	bufferSize = jbufferSize;
	fileBuffers = new unsigned char * [ nBuffers ];
	fileBufferPosition = new size_t [ nBuffers ];
	fileNames = new std::string [ nBuffers ];
	for(int i=0;i<nBuffers;++i) { fileBuffers[i] = new unsigned char [ bufferSize ]; }
	fileBufferHead = 0;
	started = 1;
	return 0;
}

JNIEXPORT jint JNICALL Java_com_nauto_modules_camera_DataStore_closeJni(JNIEnv *jenv, jclass that)
{
	for(int i=0;i<nBuffers;++i) { delete [] fileBuffers[i]; }
	delete [] fileBuffers;
	delete [] fileBufferPosition;
	delete [] fileNames;
	fileBufferPosition = 0;
	fileBuffers = 0;
	fileNames = 0;
	started = 0;
	return 0;
}

JNIEXPORT jint JNICALL Java_com_nauto_modules_camera_DataStore_writeByteArrayToFile(JNIEnv *jenv, jclass that,
	jint jfdIndex, jbyteArray jdata, jint jsize)
{
	int fdIndex = jfdIndex;
	if((fdIndex < 0) || (fdIndex >= nBuffers)) return -1;
	int size = (int)jsize;
	int nWrite = ((fileBufferPosition[fdIndex] + size) < bufferSize) ?  size : (bufferSize - fileBufferPosition[fdIndex]);
	signed char *data = static_cast<signed char *>(jenv->GetByteArrayElements(jdata, 0));

#if 0
	// this way works as well, but involves two JNI calls
	signed char *data = static_cast<signed char *>(jenv->GetByteArrayElements(jdata, 0));
	if (data) {
		memcpy(fileBuffers[fdIndex] + fileBufferPosition[fdIndex], data, nWrite);
		jenv->ReleaseByteArrayElements(data, 0, JNI_ABORT);
	}
#else
	// recommended way
	jbyte *dst = (jbyte *)(fileBuffers[fdIndex] + fileBufferPosition[fdIndex]);
	jenv->GetByteArrayRegion(jdata, 0, nWrite, dst);
#endif

	fileBufferPosition[fdIndex] += nWrite;
	return nWrite; /* how much was written */
}

JNIEXPORT jint JNICALL Java_com_nauto_modules_camera_DataStore_writeByteBufferToFile(JNIEnv *jenv, jclass that,
	jint jfdIndex, jobject jByteBuffer, jint jsize)
{
	int fdIndex = jfdIndex;
	if((fdIndex < 0) || (fdIndex >= nBuffers)) return -1;
	const signed char *data = static_cast<signed char *>(jenv->GetDirectBufferAddress(jByteBuffer));
	int size = (int)jsize;
	int nWrite = ((fileBufferPosition[fdIndex] + size) < bufferSize) ? size : (bufferSize - fileBufferPosition[fdIndex]);
	memcpy(fileBuffers[fdIndex] + fileBufferPosition[fdIndex], data, nWrite);
	fileBufferPosition[fdIndex] += nWrite;
	return nWrite; /* how much was written */
}

/* calls to openFile must be synchronous. this routine modifies fileBufferHead */
JNIEXPORT jint JNICALL Java_com_nauto_modules_camera_DataStore_openFile(JNIEnv *jenv, jclass that,
	jstring jfileName)
{
	std::string fileName = ConvertJString(jenv, jfileName);
	if(started != 1) return InvalidFileDescriptor;
	int fdIndex = fileBufferHead;
	fileNames[fdIndex] = fileName;
	fileBufferPosition[fdIndex] = 0;
	fileBufferHead = (fileBufferHead + 1) % nBuffers;
	return fdIndex;
}

JNIEXPORT jint JNICALL Java_com_nauto_modules_camera_DataStore_closeFile(JNIEnv *jenv, jclass that,
	jint jfdIndex)
{
	int fdIndex = jfdIndex;
	if((fdIndex < 0) || (fdIndex >= nBuffers)) return InvalidFileDescriptor;
	int fd = open(fileNames[fdIndex].c_str(), O_CREAT|O_WRONLY|O_TRUNC);
	size_t nWrite = write(fd, fileBuffers[fdIndex], fileBufferPosition[fdIndex]);
	close(fd);
	return nWrite;
}

typedef struct {
	int fd, nBytesToWrite, cpuMask, errNo, status;
	size_t nBytesWritten;
	void *bufferToWrite;
} DiskStreamerParams;

void *streamToDisk(void *ptr);

JNIEXPORT jintArray JNICALL Java_com_nauto_modules_camera_DataStore_writeFile(JNIEnv *jenv, jclass that,
	jint jfdIndex)
{
	jintArray info = 0; // if value is not overridden, JNI should crash. it's what we want
	DiskStreamerParams diskStreamerParams;
	memset(&diskStreamerParams, 0, sizeof(DiskStreamerParams));

	int fdIndex = jfdIndex;
	if((0 <= fdIndex) && (fdIndex < nBuffers)) {
		diskStreamerParams.fd = jfdIndex;
		diskStreamerParams.nBytesToWrite = fileBufferPosition[fdIndex];
		diskStreamerParams.nBytesWritten = 0;
		diskStreamerParams.bufferToWrite = fileBuffers[fdIndex];
		diskStreamerParams.cpuMask = 0x0f;
		diskStreamerParams.errNo = 0;
		diskStreamerParams.status = 0;

#undef CAN_SET_CPU_AFFINITY
#ifdef CAN_SET_CPU_AFFINITY
		pthread_t thread;
		int err = pthread_create(&thread, NULL, &streamToDisk, (void *)&diskStreamerParams);
		err = pthread_join(thread, 0);
#else
		streamToDisk(&diskStreamerParams);
#endif
	} else {
		diskStreamerParams.status = InvalidFileDescriptor;
	}

	int i = 0, outputLength = 4;
	int *outputBuffer = new int [ outputLength ];
	memset(outputBuffer, 0, outputLength * sizeof(int));
	outputBuffer[i++] = diskStreamerParams.nBytesWritten;
	outputBuffer[i++] = diskStreamerParams.cpuMask;
	outputBuffer[i++] = diskStreamerParams.errNo;
	outputBuffer[i++] = diskStreamerParams.status;
	if(i <= outputLength) {
		info = jenv->NewIntArray(outputLength);
		if(info != 0) { jenv->SetIntArrayRegion(info, 0, outputLength, outputBuffer); }
	}
	delete [] outputBuffer;
	return info;
}

void *streamToDisk(void *ptr) {
	DiskStreamerParams *params = (DiskStreamerParams *)ptr;
	pid_t pid = gettid();
	int res;

#ifdef CAN_SET_CPU_AFFINITY
	res = syscall(__NR_sched_setaffinity, pid, sizeof(params->cpuMask), &params->cpuMask);
	if(res) { params->status |= 1; params->errNo = errno; } // failure setting affinity

	// read out CPU affinity
	params->cpuMask = 0;
	res = syscall(__NR_sched_getaffinity, pid, sizeof(params->cpuMask), &params->cpuMask);
	if(res) { params->status |= 2; params->errNo = errno; } // failure getting affinity
#endif

	params->nBytesWritten = write(params->fd, params->bufferToWrite, params->nBytesToWrite);
	close(params->fd);
	return 0;
}

}
