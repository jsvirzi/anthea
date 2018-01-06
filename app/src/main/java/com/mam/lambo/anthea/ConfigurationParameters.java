package com.nauto.modules.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.widget.ImageView;

import com.mam.lambo.AlgoWrapper;
import com.mam.lambo.CustomSurfaceView;
import com.nauto.IStatusListener;
import com.nauto.Nautobahn;
import com.nauto.modules.distraction.AttentionDeficit;
import com.nauto.modules.facedetection.QtiFaceDetection;
import com.nauto.modules.snapdnn.SnpeModuleVehicleDetection;
import com.nauto.modules.snapdnn.SnpeModuleDistractionDetection;
import com.mam.lambo.snapdnn.SnpeConfigurationParameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jsvirzi on 11/30/16.
 */

public class ConfigurationParameters implements Cloneable {
    public Context context;
    public String deviceName;
    public String deviceId;
    public int cameraIndex;
    public String outputDirectory;
    public String videoOutputDirectory;
    public String videoOutputFile;
    public Surface displaySurface;
    public boolean useJpeg;
    public boolean useDistraction;
    public boolean jpegActive = false;
    public long deltaJpegTime;
    public long nextJpegTime;
    public byte jpegQuality;
    public boolean useH264;
    public boolean useAutoExposure;
    public boolean useAutoFocus;
    public boolean useOpticalStabilization;
    public boolean nightMode;
    public Anthea instance;
    public int imageHeight;
    public int imageWidth;
    public int frameRate;
    public int bitRate;
    public int keyFrameInterval;
    public boolean useGoogleFaceDetection;
    public Activity activity;
    public boolean useAlgo;
    public CustomSurfaceView overlayView;
    public CustomSurfaceView oppositeOverlayView;
    public boolean useEncoder;
    public boolean useAttentionDeficit;
    public AttentionDeficit attentionDeficit;
    public AlgoWrapper algoWrapper;
    public int keyFramesPerFile;
    public int dnnWidth;
    public int dnnHeight;
    public String laneFiducialDataFilename;
    public AlgoSurface algoSurface;
    public JpegSurface jpegSurface;
    public H264Surface h264Surface;
    public List<IStatusListener> statusListeners;
    public ICameraModuleStatusListener cameraModuleStatusListener;
    public boolean snapToBestFrameRate;
    public SnpeConfigurationParameters snpeConfigurationParameters;
    public ImageView imageView;
    public boolean isExternal;
    public boolean unwarp;
    public boolean reverseImage;

    public ConfigurationParameters() {
        /* these are all the default Java initialization values. just to be explicit */
        displaySurface = null;
        overlayView = null;
        oppositeOverlayView = null;
        useAlgo = false;
        algoSurface = null;
        snpeModuleDistractionDetection = null;
        snpeModuleVehicleDetection = null;
        useVehicleDetection = false;
        useQualcommFaceDetection = false;
        useGoogleFaceDetection = false;
        useAttentionDeficit = false;
        attentionDeficit = null;
        snapToBestFrameRate = false;
        nightMode = false;
        unwarp = false;
    }

    public ConfigurationParameters(String deviceId, int imageWidth, int imageHeight, int bitRate, int frameRate, int keyFrameInterval, int keyFramesPerFile) {

        snpeConfigurationParameters = new SnpeConfigurationParameters(imageWidth, imageHeight);

        /* these are all the default Java initialization values. just to be explicit */
        displaySurface = null;
        overlayView = null;
        oppositeOverlayView = null;
        useAlgo = false;
        algoSurface = null;
        snpeModuleDistractionDetection = null;
        snpeModuleVehicleDetection = null;
        useVehicleDetection = false;
        useQualcommFaceDetection = false;
        useGoogleFaceDetection = false;
        useAttentionDeficit = false;
        attentionDeficit = null;
        snapToBestFrameRate = false;

        /* from input parameters */
        this.deviceId = deviceId;
        if (deviceId == SimpleCameraModule.DeviceIdExternal) {
            deviceName = "external";
        } else if (deviceId == SimpleCameraModule.DeviceIdInternal) {
            deviceName = "internal";
        }
        useEncoder = true;
        if (useEncoder) {
            this.bitRate = bitRate;
            this.frameRate = frameRate;
            this.keyFrameInterval = keyFrameInterval;
            this.keyFramesPerFile = keyFramesPerFile;
        }
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;

        dnnWidth = imageWidth;
        dnnHeight = imageHeight;
        statusListeners = new ArrayList<IStatusListener>(1);
    }

    public static final ConfigurationParameters DEFAULT_EXTERNAL_CONFIGURATION_HD = new ConfigurationParameters(
        SimpleCameraModule.DeviceIdExternal,
        Common.IMAGE_WIDTH_HD,
        Common.IMAGE_HEIGHT_HD,
        Common.DEFAULT_BIT_RATE_HD,
        Common.DEFAULT_FRAME_RATE_HD,
        Common.DEFAULT_KEY_FRAME_INTERVAL,
        Common.DEFAULT_KEY_FRAMES_PER_FILE);

    public static final ConfigurationParameters DEFAULT_INTERNAL_CONFIGURATION_HD = new ConfigurationParameters(
        SimpleCameraModule.DeviceIdInternal,
        Common.IMAGE_WIDTH_HD,
        Common.IMAGE_HEIGHT_HD,
        Common.DEFAULT_BIT_RATE_HD,
        Common.DEFAULT_FRAME_RATE_HD,
        Common.DEFAULT_KEY_FRAME_INTERVAL,
        Common.DEFAULT_KEY_FRAMES_PER_FILE);

    public static final ConfigurationParameters DEFAULT_EXTERNAL_CONFIGURATION_LD = new ConfigurationParameters(
        SimpleCameraModule.DeviceIdExternal,
        Common.IMAGE_WIDTH_LD,
        Common.IMAGE_HEIGHT_LD,
        Common.DEFAULT_BIT_RATE_LD,
        Common.DEFAULT_FRAME_RATE_LD,
        Common.DEFAULT_KEY_FRAME_INTERVAL,
        Common.DEFAULT_KEY_FRAMES_PER_FILE);

    public static final ConfigurationParameters DEFAULT_INTERNAL_CONFIGURATION_LD = new ConfigurationParameters(
        SimpleCameraModule.DeviceIdInternal,
        Common.IMAGE_WIDTH_LD, Common.IMAGE_HEIGHT_LD,
        Common.DEFAULT_BIT_RATE_LD,
        Common.DEFAULT_FRAME_RATE_LD,
        Common.DEFAULT_KEY_FRAME_INTERVAL,
        Common.DEFAULT_KEY_FRAMES_PER_FILE);

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
