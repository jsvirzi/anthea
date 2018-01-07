package com.mam.lambo.modules.camera;

import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;

/**
 * Created by jsvirzi on 1/10/17.
 */

/* whoever extends this class must provide an onImageAvailableListener */

public abstract class AlgoSurface {
    private static final String TAG = "AlgoSurface";
    protected ConfigurationParameters configurationParameters;
    private long lastAlgoTime;
    protected ImageReader imageReader;
    private Surface surface;
    protected Handler handler;
    private HandlerThread thread;
    private static final int MaxImages = 4;
    private CaptureRequest captureRequest;
    private CameraCaptureSession captureSession;

    public AlgoSurface() {
    }

    public AlgoSurface(ConfigurationParameters inputConfigurationParameters) {
        configurationParameters = inputConfigurationParameters;
        imageReader = ImageReader.newInstance(configurationParameters.imageWidth, configurationParameters.imageHeight, ImageFormat.YUV_420_888, MaxImages);
        String threadName = String.format("Camera_Algo_%s", configurationParameters.deviceId);
        thread = new HandlerThread(threadName);
        thread.start();
        handler = new Handler(thread.getLooper());
        surface = imageReader.getSurface();
        lastAlgoTime = 0;
    }

    public Surface getSurface() {
        return surface;
    }

    public Handler getHandler() {
        return handler;
    }

    public CameraCaptureSession.CaptureCallback getCaptureCallback() {
        return captureCallback;
    }

    public void setCaptureRequest(CaptureRequest inputCaptureRequest) {
        captureRequest = inputCaptureRequest;
    }

    public CaptureRequest getCaptureRequest() {
        return captureRequest;
    }

    public void setCaptureSession(CameraCaptureSession session) {
        captureSession = session;
    }

    public void trigger() {
        try {
            captureSession.capture(captureRequest, captureCallback, handler);
        } catch (CameraAccessException ex) {
            String msg = String.format(Common.LOCALE, "CameraAccessException encountered for capture request on device=%s",
                configurationParameters.deviceId);
            Log.d(TAG, msg, ex);
        }
    }

    CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
//            Handler handler = configurationParameters.algoSurface.getHandler();
//            try {
//                session.capture(captureRequest, captureCallback, handler);
//            } catch (CameraAccessException ex) {
//                Log.e(TAG, "requestAlgoSnapshot() failed", ex);
//            }
        }
    };

    public void destroy() {
        Utils.goodbyeThread(thread);
    }
}
