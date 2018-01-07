package com.mam.lambo;

import android.os.Handler;
import android.os.HandlerThread;

import com.mam.lambo.modules.utils.Utils;

import java.nio.ByteBuffer;

public class AlgoWrapper {

    private static final String TAG = "AlgoWrapper";
    private static AlgoWrapper instance;
    private HandlerThread thread;
    private Handler handler;

    private AlgoWrapper() {
        String threadName = TAG;
        thread = new HandlerThread(threadName);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public Handler getHandler() {
        return handler;
    }

    public static AlgoWrapper getInstance() {
        if (instance == null) {
            instance = new AlgoWrapper();
        }
        return instance;
    }

    /* Load the AlgoWrapper Jni Library */
    static {
        instance = null;
        System.loadLibrary("AlgoWrapper_JNI");
    }

    public void destroy() {
        Utils.goodbyeThread(thread);
    }

    public native int init();
    public native float[] execute(ByteBuffer Y, ByteBuffer U, ByteBuffer V, int index);
    public native void setFiducials(float[] fiducials);
    public native int initLane(int imageWidth, int imageHeight);
    public native int initTailgating(int imageWidth, int imageHeight, int bufferLength);
    public native float executeTailgating(float[] boxes, int boxOffset, int numberOfBoxes, float speed, long timestamp);
    public native int setPosThreshold(int posThreshold);
    public native float getPosThreshold();
    public native int setKappa(float kappa);
    public native float getKappa();
    /* 0 = external; 1 = internal. 0 = 1920x1080; 1 = 1280x720 */
    public native int initializeCameraUnwarping(int cameraIndex, int resolution);
    public native double[] getCameraUnwarping(int cameraIndex);
    public native int unwarpImage(int cameraIndex, ByteBuffer Y);
}
