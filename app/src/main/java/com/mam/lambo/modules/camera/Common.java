package com.mam.lambo.modules.camera;

import android.os.Process;

import java.util.Locale;

/**
 * Created by jsvirzi on 8/21/16.
 */
public class Common {
    public static final int DEFAULT_KEY_FRAMES_PER_FILE = 5;
    public static final int DEFAULT_KEY_FRAME_INTERVAL = 2;
    public static final int DEFAULT_FRAME_RATE_HD = 30;
    public static final int DEFAULT_BIT_RATE_HD = 6000000;
    public static final int DEFAULT_FRAME_RATE_LD = 15;
    public static final int DEFAULT_BIT_RATE_LD = 2500000;
    public static final Locale LOCALE = Locale.US;
    public static final int MEDIUM_THREAD_PRIORITY = Process.THREAD_PRIORITY_BACKGROUND + 1;
    public static final int DEFAULT_SENSOR_DATA_RECORDING_LENGTH = 300000; // 5 minutes
    public static final int MAX_NUMBER_OF_FILES_PER_DIRECTORY = 1000;
    public static final int IMAGE_WIDTH_HD = 1920;
    public static final int IMAGE_HEIGHT_HD = 1080;
    public static final int IMAGE_WIDTH_LD = 1280;
    public static final int IMAGE_HEIGHT_LD = 720;
    public static final float RADIANS_TO_DEGREES = 180.0f / 3.1415926535f;
    public static final int DEFAULT_SNAPSHOT_FREQUENCY = 10000; /* 10 seconds */
    public static final int ExternalCamera = 0;
    public static final int InternalCamera = 1;
}
