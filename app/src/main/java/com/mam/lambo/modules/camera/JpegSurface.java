package com.mam.lambo.modules.camera;

import android.graphics.ImageFormat;
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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by jsvirzi on 1/10/17.
 */

public class JpegSurface {
    private static final String TAG = "JpegSurface";
    private ImageReader imageReader;
    ConfigurationParameters configurationParameters;
    HandlerThread thread;
    Handler handler;
    private static final int MaxImages = 4;
    private Surface surface;
    private byte[] buffer = new byte[1024 * 1024]; /* way large */
    private int snapshotCount = 0;
    private CaptureRequest captureRequest;
    private CameraCaptureSession captureSession;

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

    public JpegSurface(ConfigurationParameters inputConfigurationParameters) {
        String threadName = String.format("Camera_Jpeg_%s", inputConfigurationParameters.deviceId);
        thread = new HandlerThread(threadName);
        thread.start();
        handler = new Handler(thread.getLooper());
        configurationParameters = inputConfigurationParameters;
        imageReader = ImageReader.newInstance(configurationParameters.imageWidth, configurationParameters.imageHeight, ImageFormat.JPEG, MaxImages);
        imageReader.setOnImageAvailableListener(onImageAvailableListener, handler);
        surface = imageReader.getSurface();
    }

    private final ImageReader.OnImageAvailableListener onImageAvailableListener =
        new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = imageReader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer byteBuffer = planes[0].getBuffer();
                    final int jpegSize = byteBuffer.remaining();
                    byteBuffer.get(buffer, 0, jpegSize);
                    image.close();
                    String filename = String.format("%s/%s_snapshot_%d.jpg", configurationParameters.outputDirectory,
                        configurationParameters.deviceName, snapshotCount);
                    ++snapshotCount;
                    FileOutputStream fileOutputStream = null;
                    try {
                        fileOutputStream = new FileOutputStream(filename);
                    } catch (FileNotFoundException ex) {
                        Log.d(TAG, "FileNotFoundException", ex);
                    }
                    try {
                        fileOutputStream.write(buffer, 0, jpegSize);
                        fileOutputStream.close();
                    } catch (IOException ex) {
                        Log.d(TAG, "error writing jpeg", ex);
                    }
                }
            }
        };

    CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(final CameraCaptureSession session, final CaptureRequest request, TotalCaptureResult result) {
            /* submit request for next snapshot */
            configurationParameters.nextJpegTime += configurationParameters.deltaJpegTime;
            final Handler handler = configurationParameters.jpegSurface.getHandler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        session.capture(captureRequest, captureCallback, handler);
                    } catch (CameraAccessException ex) {
                        Log.e(TAG, "requestJpegSnapshot() failed", ex);
                    }
                }
            }, configurationParameters.deltaJpegTime);
        }
    };

    public void destroy() {
        Utils.goodbyeThread(thread);
    }
}
