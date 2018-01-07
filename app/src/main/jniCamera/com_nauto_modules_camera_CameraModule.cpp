// #include <jni.h>
#include <com_nauto_modules_camera_CameraModule.h>

#include <algorithm>
#include <cstring>
#include <string>
// #include <stdlib.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <jni.h>

extern "C" {

int imageWidth = 1920, imageHeight = 1080, reverse = 0;

#if 0
JNIEXPORT void JNICALL Java_com_nauto_modules_camera_CameraModule_fillJpegSingleInputBuffer(JNIEnv *jenv, jclass that,
	jobject jFrameData, jobject jYBuffer, jobject jUVBuffer)
{
	unsigned char *data = static_cast<unsigned char *>(jenv->GetDirectBufferAddress(jFrameData));
	unsigned char *y = static_cast<unsigned char *>(jenv->GetDirectBufferAddress(jYBuffer));
	unsigned char *uv = static_cast<unsigned char *>(jenv->GetDirectBufferAddress(jUVBuffer));
	memcpy(data, y, imageHeight * imageWidth);
	int offset = imageHeight * imageWidth;
	int n = imageHeight * imageWidth / 4 - 1; /* all but that last byte */
	unsigned char *dst = &data[offset], *src = uv;
	for(int i=0;i<n;++i) { /* flip uv into vu */
		dst[0] = src[1];
		dst[1] = src[0];
		src += 2;
		dst += 2;
	}
// do this after the call
//	jclass cls = jenv->GetObjectClass(jFrameData);
//	jmethodID id = jenv->GetMethodID(cls, "limit", "(I)Ljava/nio/Buffer;");
//	jenv->CallObjectMethod(jFrameData, id, totalImageSize);

}

JNIEXPORT void JNICALL Java_com_nauto_modules_camera_CameraModule_fillJpegInputBuffer(JNIEnv *jenv, jclass that,
	jbyteArray jdst, jobject jsrc)
{
	signed char *dst = static_cast<signed char *>(jenv->GetByteArrayElements(jdst, 0));
	unsigned char *src = static_cast<unsigned char *>(jenv->GetDirectBufferAddress(jsrc));
	memcpy(dst, src, 2 * imageHeight * imageWidth);
	int i, n = imageHeight * imageWidth / 2, offset = 2 * imageHeight * imageWidth;
	for(int i=0;i<n;++i) {
		dst[offset] = src[offset+1];
		dst[offset+1] = src[offset];
		offset += 2;
	}
}

#endif

void yuv420spToArgb888(int *rgb, unsigned char *ySrc, unsigned char *uvSrc, int width, int height) {
	int i, j, k, dk, y, u, v, yOffset, uvOffset;
    if(reverse == 1) {
        k = height * width - 1;
        dk = -1;
    } else {
        k = 0;
        dk = 1;
    }
    for (j = 0, yOffset = 0; j < height; j++) {
        uvOffset = (j >> 1) * width;
		u = v = 0;
        for (i = 0; i < width; i++, yOffset++) {
            y = (0xff & ((int)(ySrc[yOffset]))) - 16;
            if (y < 0) y = 0;
            if ((i & 1) == 0) {
                u = (0xff & uvSrc[uvOffset++]) - 128;
                v = (0xff & uvSrc[uvOffset++]) - 128;
            }
            int y1192 = 1192 * y;
            // red
            int red = (y1192 + 1634 * v);
            if (red < 0) { red = 0; }
            else if (red > 262143) { red = 262143; }
            // green
            int green = (y1192 - 833 * v - 400 * u);
            if (green < 0) { green = 0; }
            else if (green > 262143) { green = 262143; }
            // blue
            int blue = (y1192 + 2066 * u);
            if (blue < 0) { blue = 0; }
            else if (blue > 262143) { blue = 262143; }
            rgb[k] = 0xff000000 | ((red << 6) & 0xff0000) | ((green >> 2) & 0xff00) | ((blue >> 10) & 0xff);
            k += dk;
        }
    }
}

void yuv420spToRgb565(int *rgb, unsigned char *ySrc, unsigned char *uvSrc, int width, int height) {
	int i, j, k, dk, y, u, v, yOffset, uvOffset, tRgb;
    if(reverse == 1) {
        k = height * width - 1;
        dk = -1;
    } else {
        k = 0;
        dk = 1;
    }
    for (j = 0, yOffset = 0; j < height; j++) {
        uvOffset = (j >> 1) * width;
		u = v = 0;
        for (i = 0; i < width; i++, yOffset++) {
            y = (0xff & ((int)(ySrc[yOffset]))) - 16;
            if (y < 0) y = 0;
            if ((i & 1) == 0) {
                u = (0xff & uvSrc[uvOffset++]) - 128;
                v = (0xff & uvSrc[uvOffset++]) - 128;
            }
            int y1192 = 1192 * y;
            // red
            int red = (y1192 + 1634 * v);
            if (red < 0) { red = 0; }
            else if (red > 262143) { red = 262143; }
            // green
            int green = (y1192 - 833 * v - 400 * u);
            if (green < 0) { green = 0; }
            else if (green > 262143) { green = 262143; }
            // blue
            int blue = (y1192 + 2066 * u);
            if (blue < 0) { blue = 0; }
            else if (blue > 262143) { blue = 262143; }

			if ((i%2) == 0) {
				tRgb = ((red >> 2) & 0xf800) | ((green >> 7) & 0x07d0) | ((blue >> 13) & 0x001f);
			} else {
				rgb[k] = (tRgb << 16) | ((red >> 2) & 0xf800) | ((green >> 7) & 0x07d0) | ((blue >> 13) & 0x001f);
                k += dk;
			}
        }
    }
}

JNIEXPORT jint JNICALL Java_com_nauto_modules_camera_CameraModule_initJni(JNIEnv *jenv, jclass that,
    jint jnBuffers, jlong jbufferSize)
{
    imageWidth = 1920;
    imageHeight = 1080;
    return 0;
}

JNIEXPORT int JNICALL Java_com_nauto_modules_camera_CameraModule_reverseImage(JNIEnv *jenv, jclass that, jint flag) {
    if (flag == 0 || flag == 1) {
        reverse = flag;
    }
    return reverse;
}

JNIEXPORT void JNICALL Java_com_nauto_modules_camera_CameraModule_fillFrameData(JNIEnv *jenv, jclass that,
	jobject jFrameData, jobject jYBufferL, jobject jUVBufferL, jobject jYBufferR, jobject jUVBufferR,
    jbyteArray jYvuDataRear, jbyteArray jYvuDataFront, jintArray jRgbDataFront, jint jFillDualYuv, jint jFillRearYuv, jint jFillFrontYuv, jint jFillFrontRgb) {

	unsigned char *yL = static_cast<unsigned char *>(jenv->GetDirectBufferAddress(jYBufferL));
	unsigned char *uvL = static_cast<unsigned char *>(jenv->GetDirectBufferAddress(jUVBufferL));
	unsigned char *yR = static_cast<unsigned char *>(jenv->GetDirectBufferAddress(jYBufferR));
	unsigned char *uvR = static_cast<unsigned char *>(jenv->GetDirectBufferAddress(jUVBufferR));
	int offset;

    /* check once at beginning to make thread safe. ok to change here, just not later
     * the private variable will not change once set, even if another thread executes this function */
    bool reverseImage = (reverse == 1);

	if ((jFillDualYuv != 0) && (reverseImage == false)) {
		unsigned char *data = static_cast<unsigned char *>(jenv->GetDirectBufferAddress(jFrameData));
		for (int row = 0; row < imageHeight; ++row) {
			offset = row * imageWidth;
			memcpy(&data[2 * offset], &yL[offset], imageWidth);
			memcpy(&data[2 * offset + imageWidth], &yR[offset], imageWidth);
		}
		int UVOffset = imageWidth * imageHeight * 2;
		for (int row = 0; row < imageHeight / 2; ++row) {
			offset = row * imageWidth;
            /* normally could just do a straight copy. long story, but the very last pixel
             * can't be accessed in this efficient way, because the interleaved buffers are 1 byte short.
             * Even tried to change byteBuffer.limit() */
			if (row < (imageHeight / 2 - 1)) {
				memcpy(&data[UVOffset + 2 * offset], &uvL[offset], imageWidth);
				memcpy(&data[UVOffset + 2 * offset + imageWidth], &uvR[offset], imageWidth);
			} else {
				memcpy(&data[UVOffset + 2 * offset], &uvL[offset], imageWidth - 1);
				memcpy(&data[UVOffset + 2 * offset + imageWidth], &uvR[offset], imageWidth - 1);
			}
		}
	} else if ((jFillDualYuv != 0) && (reverseImage == true)) {

        /* TODO very important note. I'm blasting through the whole byteBuffer ignoring the
         * limit() which is 1 less than what we need. Most likely, it DOES NOT matter
         * but we're not guaranteed that. if we start having problems on other platforms
         * with reversed orientation of images (e.g. - we start using this code), then consider
         * omitting the last pixel (which is the first pixel on the reversed output) */

		unsigned char *data = static_cast<unsigned char *>(jenv->GetDirectBufferAddress(jFrameData));
		for (int row = 0; row < imageHeight; ++row) {
			int rDstOffset = 2 * (imageHeight - row) * imageWidth - 1;
			int lDstOffset = rDstOffset - imageWidth;
			unsigned char *rDst = &data[rDstOffset];
			unsigned char *lDst = &data[lDstOffset];
			int srcOffset = row * imageWidth;
			unsigned char *lSrc = &yL[srcOffset];
			unsigned char *rSrc = &yR[srcOffset];
			for (int col = 0; col < imageWidth; ++col) {
				*rDst-- = *rSrc++;
				*lDst-- = *lSrc++;
			}
		}

		int dstOffset = 3 * imageHeight * imageWidth;
		uint16_t *p0 = (uint16_t *)&data[dstOffset];
		--p0; // we were one past the starting point
		int h = imageHeight / 2, w = imageWidth / 2;
		uint16_t *lSrc = (uint16_t *) &uvL[0];
		uint16_t *rSrc = (uint16_t *) &uvR[0];
		for (int row = 0; row < h; ++row) {
			uint16_t *rDst = p0;
			p0 = p0 - w;
			uint16_t *lDst = p0;
			p0 = p0 - w;
			for (int col = 0; col < w; ++col) {
				*rDst-- = *rSrc++;
				*lDst-- = *lSrc++;
			}
		}

	}

	if (jFillFrontYuv != 0) {
		jbyte *jBytePtr = static_cast<jbyte *>(jenv->GetByteArrayElements(jYvuDataFront, 0));
		unsigned char *yvuF = (unsigned char *) jBytePtr;
		/* fill the front byte buffer for use by the jpeg encoder. it is in NV21 which is YVU */
		memcpy(yvuF, yL, imageWidth * imageHeight);
		int i;
		int n = imageWidth * imageHeight / 4 - 1; /* all except that last pixel */
		uint16_t *pSrc = (uint16_t *) uvL;
		uint16_t *pDst = (uint16_t *) &yvuF[imageWidth * imageHeight];
		for (i = 0; i < n; ++i) {
			uint16_t datum = *pSrc++;
			uint16_t datumLo = (datum << 8) & 0xff00;
			uint16_t datumHi = (datum >> 8) & 0x00ff;
			datum = datumLo | datumHi;
			*pDst++ = datum;
		}
		jenv->ReleaseByteArrayElements(jYvuDataFront, (jbyte *) yvuF, 0);

	}
// do this after the call (at Java level)
//	jclass cls = jenv->GetObjectClass(jFrameData);
//	jmethodID id = jenv->GetMethodID(cls, "limit", "(I)Ljava/nio/Buffer;");
//	jenv->CallObjectMethod(jFrameData, id, totalImageSize);

	if(jFillRearYuv != 0) {
        jbyte *jBytePtr = static_cast<jbyte *>(jenv->GetByteArrayElements(jYvuDataRear, 0));
        unsigned char *yvuR = (unsigned char *)jBytePtr;
        /* fill the front byte buffer for use by the jpeg encoder. it is in NV21 which is YVU */
        memcpy(yvuR, yR, imageWidth * imageHeight);
        int i;
        int n = imageWidth * imageHeight / 4 - 1; /* all except that last pixel */
        uint16_t *pSrc = (uint16_t *)uvR;
        uint16_t *pDst = (uint16_t *)&yvuR[imageWidth * imageHeight];
        for(i=0;i<n;++i) {
            uint16_t datum = *pSrc++;
            uint16_t datumLo = (datum << 8) & 0xff00;
            uint16_t datumHi = (datum >> 8) & 0x00ff;
            datum = datumLo | datumHi;
            *pDst++ = datum;
        }
        jenv->ReleaseByteArrayElements(jYvuDataRear, (jbyte *)yvuR, 0);

    }
// do this after the call (at Java level)
//	jclass cls = jenv->GetObjectClass(jFrameData);
//	jmethodID id = jenv->GetMethodID(cls, "limit", "(I)Ljava/nio/Buffer;");
//	jenv->CallObjectMethod(jFrameData, id, totalImageSize);

    if (jFillFrontRgb == 1) {
        int *dst = static_cast<int *>(jenv->GetIntArrayElements(jRgbDataFront, 0));
        yuv420spToArgb888(dst, yL, uvL, imageWidth, imageHeight);
        jenv->ReleaseIntArrayElements(jRgbDataFront, dst, 0);
    } else if (jFillFrontRgb == 2) {
		int *dst = static_cast<int *>(jenv->GetIntArrayElements(jRgbDataFront, 0));
		yuv420spToRgb565(dst, yL, uvL, imageWidth, imageHeight);
		jenv->ReleaseIntArrayElements(jRgbDataFront, dst, 0);
	}

}

}
