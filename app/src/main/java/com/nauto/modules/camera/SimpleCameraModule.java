package com.nauto.modules.camera;

/**
 * Created by jsvirzi on 7/2/16.
 */

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.view.Surface;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class SimpleCameraModule {

    public static final String DeviceIdExternal = "0";
    public static final String DeviceIdInternal = "1";

    private static final String TAG = "SimpleCameraModule";
    private Context context = null;
    private CameraCaptureSession captureSession = null;
    private CameraDevice device = null;
    private Surface surfaceDisp = null;
    private String deviceName = null;
    private String deviceId = null;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private boolean stopRepeatingWaitOnClosed;
    private boolean captureSessionWaitOnClosed;
    private boolean deviceWaitOnClosed;
    public boolean gracefulClose;
    public String statusString;
    private ConfigurationParameters configurationParameters;
//    private DataStore dataStore;
    private int frameCounter = 0;
    private Range<Integer> fpsRange;
    private int[] sceneModes;
    private int[] effectModes;

    public int getFrameCounter() {
        return frameCounter;
    }

    public SimpleCameraModule(ConfigurationParameters configurationParameters) {

        this.configurationParameters = configurationParameters;

        context = configurationParameters.context;
        deviceName = configurationParameters.deviceName;
        surfaceDisp = configurationParameters.displaySurface;
        deviceId = configurationParameters.deviceId;

//        if (deviceId == null) {
//            Log.d(TAG, "you are an idiot");
//            getCameraResolutions(0);
//            getCameraResolutions(1);
//        }
//        String jsv = String.format("device id = %s = %d", deviceId, Integer.getInteger(deviceId));
//        Log.d(TAG, jsv);
//        getCameraResolutions(Integer.getInteger(deviceId));

//        if (configurationParameters.useDataStore) {
//            int numberOfDataBuffers = 16;
//            int dataBufferPayloadSize = 1024 * 1024;
//            dataStore = new DataStore(deviceId, numberOfDataBuffers, dataBufferPayloadSize);
//        } else {
//            dataStore = null;
//        }

        String threadName;

        threadName = String.format("CameraBackground%s", deviceId);
        backgroundThread = new HandlerThread(threadName);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        /* TODO. at some point reinstate checking external camera vs lens facing characteristic */
//        configurationParameters.deviceId = getCameraId(deviceName);

        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        boolean frameRateOk = false;
        Range<Integer>[] ranges = getSupportedFrameRates();
        for (Range<Integer> range : ranges) {
            boolean lowerOk = (range.getLower() == configurationParameters.frameRate);
            boolean upperOk = (range.getUpper() == configurationParameters.frameRate);
            if (lowerOk && upperOk) {
                frameRateOk = true;
                fpsRange = range;
                break;
            }
        }

        sceneModes = getSupportedSceneModes();

        effectModes = getSupportedEffectModes();

        if ((configurationParameters.snapToBestFrameRate == false) && (frameRateOk == false)) {
            String msg = String.format(Common.LOCALE, "unsupported frame rate %d fps requested", configurationParameters.frameRate);
            Log.e(TAG, msg);
            return;
        } else if (configurationParameters.snapToBestFrameRate) {
            fpsRange = ranges[ranges.length-1];
            String msg = String.format(Common.LOCALE, "requested frame rate modified to (%d,%d) fps",
                fpsRange.getLower().intValue(), fpsRange.getUpper().intValue());
            Log.d(TAG, msg);
        }

        try {
            cameraManager.openCamera(configurationParameters.deviceId, cameraStateCallback, backgroundHandler);
        } catch (CameraAccessException ex) {
            String msg = String.format(Common.LOCALE, "CameraAccessException encountered on camera %s", configurationParameters.deviceId);
            Log.e(TAG, msg, ex);
        } catch (SecurityException ex) {
            String msg = String.format(Common.LOCALE, "SecurityException encountered on camera %s", configurationParameters.deviceId);
            Log.e(TAG, msg, ex);
        }
    }

    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {

            device = cameraDevice;

            Surface surface;

            List<Surface> surfaceList = new LinkedList<>();
            if (configurationParameters.useEncoder && (configurationParameters.h264Surface != null)) {
                surface = configurationParameters.h264Surface.getSurface();
                if (surface != null) {
                    surfaceList.add(surface);
                }
            }

            surface = surfaceDisp;
            if (surface != null) {
                surfaceList.add(surface);
            }

            if (configurationParameters.useAlgo && (configurationParameters.algoSurface != null)) {
                surface = configurationParameters.algoSurface.getSurface();
                if (surface != null) {
                    surfaceList.add(surface);
                }
            }

            if (configurationParameters.useJpeg && (configurationParameters.jpegSurface != null)) {
                surface = configurationParameters.jpegSurface.getSurface();
                if (surface != null) {
                    surfaceList.add(surface);
                }
            }

            try {
                cameraDevice.createCaptureSession(surfaceList, captureSessionCallback, null); // TODO background handler
            } catch (CameraAccessException ex) {
                ex.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            //    http://developer.android.com/reference/android/hardware/camera2/CameraDevice.StateCallback.html
            String msg = String.format("onError(error=%d) called for %s camera", error, deviceName);
            Log.d(TAG, msg);
            switch (error) {
                case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                    msg = String.format("ERROR: camera is in use");
                    break;
                case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                    msg = String.format("ERROR: maximum cameras in use");
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                    msg = String.format("ERROR: camera disabled");
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                    msg = String.format("ERROR: camera device");
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                    msg = String.format("ERROR: camera service");
                    break;
                default:
                    msg = String.format("ERROR: unclassified");
                    break;
            }
            Log.d(TAG, msg);
            try {
                camera.close();
            } catch (IllegalStateException ex) {
                msg = String.format("IllegalStateException(error=%d) caught for %s camera", error, deviceName);
                Log.e(TAG, msg, ex);
            }
        }

        @Override
        public void onClosed(final CameraDevice cameraDevice) {
            deviceWaitOnClosed = false;
        };
    };

    CameraCaptureSession.StateCallback captureSessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            captureSession = session;
            Surface surface;
            CaptureRequest captureRequest = null;

            try {
                CaptureRequest.Builder captureRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                if (configurationParameters.useAutoExposure) {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                } else {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                }

//                Range<Integer> range = Range.create(configurationParameters.frameRate, configurationParameters.frameRate);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);

                int mode;
                if (configurationParameters.nightMode) {
                    mode = CaptureRequest.CONTROL_SCENE_MODE_NIGHT;
                    if (sceneModes != null) {
                        for (int sceneMode : sceneModes) {
                            if (sceneMode == mode) {
                                captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, mode);
                                Log.d(TAG, "scene night mode set");
                            }
                        }
                    }

                    mode = CaptureRequest.CONTROL_EFFECT_MODE_MONO;
                    if (effectModes != null) {
                        for (int effectMode : effectModes) {
                            if (effectMode == mode) {
                                captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, mode);
                                Log.d(TAG, "effect night mode set");
                            }
                        }
                    }
                }

                if (configurationParameters.useOpticalStabilization) {
                    mode = CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON;
                } else {
                    mode = CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF;
                }
                captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, mode);

                if (deviceId == DeviceIdExternal) {
                    captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, mode);
                } else if (deviceId == DeviceIdInternal) {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
                }

                if (configurationParameters.useAutoFocus) {
                    if (deviceId == DeviceIdExternal) {
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                    } else {
                        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF); /* fixed focus */
                    }
                }

                /*
                 * first order of business: establish the repeating request
                 * this includes recorder and display
                 */
                int numberOfTargets = 0;

                /* recording surface */
                H264Surface h264Surface = configurationParameters.h264Surface;
                if (configurationParameters.useEncoder && (h264Surface != null)) {
                    surface = h264Surface.getSurface();
                    if (surface != null) {
                        captureRequestBuilder.addTarget(surface);
                        ++numberOfTargets;
                    }
                }

                /* display surface */
                if (surfaceDisp != null) {
                    captureRequestBuilder.addTarget(surfaceDisp);
                    ++numberOfTargets;
                }

                if (numberOfTargets > 0) {
                    captureRequest = captureRequestBuilder.build();
                    if (h264Surface != null) { /* first priority for callback goes to encoder */
                        session.setRepeatingRequest(captureRequest, h264Surface.getCaptureCallback(), h264Surface.getHandler());
                    } else if (surfaceDisp != null) { /* we're obviously not doing anything useful */
                        session.setRepeatingRequest(captureRequest, captureCallback, backgroundHandler);
                    }
                }

                /*
                 * construct request for algorithm surface(s)
                 */

                AlgoSurface algoSurface = configurationParameters.algoSurface;
                if (configurationParameters.useAlgo && (algoSurface != null)) {
                    surface = algoSurface.getSurface();
                    if (surface != null) {
                        captureRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
                        captureRequestBuilder.addTarget(surface);
                        captureRequest = captureRequestBuilder.build();
                        algoSurface.setCaptureSession(session);
                        algoSurface.setCaptureRequest(captureRequest);
                        session.capture(captureRequest, algoSurface.getCaptureCallback(), algoSurface.getHandler());
                    }
                }

                /*
                 * snapshot surface(s)
                 */

                JpegSurface jpegSurface = configurationParameters.jpegSurface;
                if (configurationParameters.useJpeg && (jpegSurface != null)) {
                    surface = jpegSurface.getSurface();
                    if (surface != null) {
                        captureRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
                        captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, configurationParameters.jpegQuality);
                        captureRequestBuilder.addTarget(surface);
                        captureRequest = captureRequestBuilder.build();
                        jpegSurface.setCaptureSession(session);
                        jpegSurface.setCaptureRequest(captureRequest);
                        session.capture(captureRequest, jpegSurface.getCaptureCallback(), jpegSurface.getHandler());
                    }
                }

            } catch (CameraAccessException ex) {
                ex.printStackTrace();
            }
        } // onConfigured

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Log.d(TAG, "unable to configure camera");
        }

        @Override
        public void onSurfacePrepared(CameraCaptureSession session, Surface surface) {
        }

        @Override
        public void onReady(CameraCaptureSession session) {
            stopRepeatingWaitOnClosed = false;
        }

        @Override
        public void onClosed(CameraCaptureSession session) {
            captureSessionWaitOnClosed = false;
        }
    };

    /* default fallback callback */
    public CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
        }
    };

    public Range<Integer>[] getSupportedFrameRates() {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        Range<Integer> ranges[];
        try {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(deviceId);
            ranges = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        } catch (CameraAccessException ex) {
            String msg = String.format(Common.LOCALE, "CameraAccessException in getSupportedFrameRates(camera=%s)", deviceId);
            Log.e(TAG, msg, ex);
            return null;
        }
        return ranges;
    }

    public int[] getSupportedSceneModes() {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        int[] sceneModes;
        try {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(deviceId);
            sceneModes = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
        } catch (CameraAccessException ex) {
            String msg = String.format(Common.LOCALE, "CameraAccessException in getSupportedSceneModes(camera=%s)", deviceId);
            Log.e(TAG, msg, ex);
            return null;
        }
        return sceneModes;
    }

    public int[] getSupportedEffectModes() {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        int[] effectModes;
        try {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(deviceId);
            effectModes = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS);
        } catch (CameraAccessException ex) {
            String msg = String.format(Common.LOCALE, "CameraAccessException in getSupportedEffectModes(camera=%s)", deviceId);
            Log.e(TAG, msg, ex);
            return null;
        }
        return effectModes;
    }

    public String getCameraId(String deviceName) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        String[] cameraIdList;
        try {
            cameraIdList = cameraManager.getCameraIdList();
        } catch (CameraAccessException ex) {
            String msg = String.format(Common.LOCALE, "CameraAccessException caught calling getCameraIdList()");
            Log.e(TAG, msg, ex);
            return null;
        }

        for (final String cameraId : cameraIdList) {
            CameraCharacteristics characteristics;
            try {
                characteristics = cameraManager.getCameraCharacteristics(cameraId);
            } catch (CameraAccessException ex) {
                String msg = String.format(Common.LOCALE, "CameraAccessException caught getCameraCharacteristics(%s)", cameraId);
                Log.d(TAG, msg, ex);
                return null;
            }
            int cameraOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (cameraOrientation == CameraCharacteristics.LENS_FACING_FRONT && deviceName.equals("internal")) {
                return cameraId;
            } else if (cameraOrientation == CameraCharacteristics.LENS_FACING_BACK && deviceName.equals("external")) {
                return cameraId;
            } else if (cameraOrientation == CameraCharacteristics.LENS_FACING_EXTERNAL && deviceName.equals("external")) {
                return cameraId;
            }
        }
        return null;
    }

    Runnable seppakuRunnable = new Runnable() {
        @Override
        public void run() {

            boolean status;
            status = stopRepeatingVideoCaptures(true, true);
            while(status == false) {
                status = stopRepeatingVideoCaptures(false, true);
            }

            try {
                captureSession.abortCaptures();
            } catch (CameraAccessException ex) {
                String msg = String.format(Common.LOCALE, "CameraAccessException caught abortCaptures(%s)", deviceId);
                Log.d(TAG, msg, ex);
            }

            try {
                statusString = "session.stopRepeating";
                stopRepeatingWaitOnClosed = true;
                captureSession.stopRepeating();
            } catch (CameraAccessException ex) {
                String msg = String.format(Common.LOCALE, "CameraAccessException caught stopRepeating(%s)", deviceId);
                Log.d(TAG, msg, ex);
            }

            while (stopRepeatingWaitOnClosed) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            if (captureSession != null) {
                statusString = "session.close";
                captureSessionWaitOnClosed = true;
                captureSession.close();
            }

            while (captureSessionWaitOnClosed) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            if (device != null) {
                statusString = "device.close";
                deviceWaitOnClosed = true;
                device.close();
            }

            while (deviceWaitOnClosed) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            statusString = "quit thread";
            backgroundThread.quitSafely();
            try {
                statusString = "join thread";
                backgroundThread.join();
                backgroundThread = null;
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    };

    public boolean stopRepeatingVideoCaptures(boolean forceStop, boolean syncMode) {
        if (forceStop) {
            if (stopRepeatingWaitOnClosed == false) { /* only set if cleared */
                if (captureSession != null) {
                    stopRepeatingWaitOnClosed = true;
                    try {
                        captureSession.stopRepeating();
                    } catch (CameraAccessException ex) {
                        String msg = String.format(Common.LOCALE, "%s CameraAccessException caught stopRepeating()", deviceName);
                        Log.e(TAG, msg, ex);
                    } catch (IllegalStateException ex) {
                        String msg = String.format(Common.LOCALE, "%s IllegalStateException caught stopRepeating()", deviceName);
                        Log.e(TAG, msg, ex);
                    }
                }
            }
        }
        return (stopRepeatingWaitOnClosed == false);
    }

    public void destroy() {

        try {
            captureSession.stopRepeating();
        } catch (CameraAccessException ex) {
            ex.printStackTrace();
        }

        if (captureSession != null) {
            return;
        }

//        if (dataStore != null) {
//            dataStore.destroy();
//            dataStore = null;
//        }

        gracefulClose = false;
        statusString = "closing";
        HandlerThread seppakuThread = new HandlerThread("SimpleCameraModule_seppaku");
        seppakuThread.start();
        Handler seppakuHandler = new Handler(seppakuThread.getLooper());
        seppakuHandler.post(seppakuRunnable);

        long now = System.currentTimeMillis();
        long timeout = now + 10000; /* give 10 seconds to timeout; otherwise something went horribly wrong */
        while(gracefulClose == false) { /* sepaku will close gracefully */
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            statusString = "waiting seppaku";
            Log.d(TAG, statusString);
            if (System.currentTimeMillis() > timeout) {
                String msg = String.format(Common.LOCALE, "timeout condition waiting for %s camera", deviceName);
                Log.d(TAG, msg);
                break;
            }
        }

        configurationParameters.jpegSurface.destroy();
        configurationParameters.h264Surface.destroy();
        configurationParameters.algoSurface.destroy();

        statusString = "closed";
        gracefulClose = true;
    }

    public void getCameraResolutions(int id) {
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String cameraId = cameraManager.getCameraIdList()[id];
            // get one of the cameras in the list (on LG, 0 is rear facing, 1 is front facing)
            // we want to look at resolution, supported hardware level, etc
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

            // find out supported hardware level - legacy means that many of the camera2 features are not supported
            Integer hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            String[] hardwarelevels = {"LIMITED", "FULL", "LEGACY"};
            Log.d(TAG, "Hardware level " + hardwarelevels[hardwareLevel] + " " + hardwareLevel);

            // looking for the resolutions that the camera supports
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            android.util.Size[] sizesJpeg = map.getOutputSizes(ImageFormat.JPEG);
            android.util.Size[] sizesRawSensor = map.getOutputSizes(ImageFormat.RAW_SENSOR);
            android.util.Size[] sizesNv21 = map.getOutputSizes(ImageFormat.YUV_420_888);

            Log.d(TAG, "Sizes for JPEG: " + Arrays.toString(sizesJpeg));
            Log.d(TAG, "Sizes for RAWSENSOR: " + Arrays.toString(sizesRawSensor));
            Log.d(TAG, "Sizes for NV21: " + Arrays.toString(sizesNv21));
        } catch (CameraAccessException exc) {
            exc.printStackTrace();
        } catch (SecurityException exc) {
            Log.d(TAG, exc.getMessage());
        }
    }
}
