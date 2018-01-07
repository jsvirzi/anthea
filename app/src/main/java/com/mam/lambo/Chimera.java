package com.mam.lambo;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.mam.lambo.AlgoWrapper;
import com.mam.lambo.CustomSurfaceView;
import com.nauto.modules.camera.Common;
import com.nauto.modules.camera.ConfigurationParameters;
import com.nauto.modules.camera.H264Surface;
import com.nauto.modules.camera.JpegSurface;
import com.nauto.modules.camera.SimpleCameraModule;
import com.nauto.modules.sensor.SensorModule;
import com.nauto.modules.sensor.SensorRecorder;
import com.nauto.modules.server.SimpleServer;
import com.nauto.modules.utils.DataLogger;
import com.nauto.modules.utils.Utils;
import com.mam.lambo.IStatusListener;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by jsvirzi on 6/10/16.
 */
public class Chimera extends Activity implements IStatusListener {
    private static String TAG = "Chimera";
    public static Chimera chimera;
    SurfaceView externalSurfaceView;
    SurfaceView internalSurfaceView;
    CustomSurfaceView externalOverlay;
    CustomSurfaceView internalOverlay;
    Context context;
    private Button exitButton;
    private Button startExternalButton;
    private Button startInternalButton;
    private Button startImuButton;
    private SurfaceHolder externalSurfaceHolder;
    private SurfaceHolder internalSurfaceHolder;
    private Surface externalSurface = null;
    private Surface internalSurface = null;
    private ImageView imageViewStatus;
    Bitmap redCircle;
    Bitmap blueCircle;
    Bitmap greenCircle;
    TextView textView1;
    TextView textView2;
    Timer recordingTimer;
    CheckBox checkBoxExternalVideo;
    CheckBox checkBoxInternalVideo;
    CheckBox checkBoxRecord;
    CheckBox checkBoxNN;
    CheckBox checkBoxExternalDisplay;
    CheckBox checkBoxInternalDisplay;
    CheckBox checkBoxExternalEncodeH264;
    CheckBox checkBoxInternalEncodeH264;
    CheckBox checkBoxExternalDnn;
    CheckBox checkBoxInternalDnn;
    CheckBox checkBoxQtiFaceDetection;
    CheckBox checkBoxAutoExposureFocus;
    CheckBox checkBoxOpticalStabilization;
    CheckBox checkBoxSnapshot;
    CheckBox checkBoxExternalHd;
    CheckBox checkBoxInternalHd;
    CheckBox checkBoxFastImu;
    CheckBox checkBoxExternalUnwarp;
    CheckBox checkBoxInternalUnwarp;
    int imageWidth = 1920;
    int imageHeight = 1080;
    public ConfigurationParameters internalConfigurationParameters;
    public ConfigurationParameters externalConfigurationParameters;
    SimpleServer simpleServer;
    Anthea anthea;
    Activity activity;
    HandlerThread workerThread;
    Handler workerHandler;
    SensorRecorder sensorRecorder;
    String outputDirectory;

    private SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
        }

    };

    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM-dd-HH-mm-ss");;

    String humanReadableTime(long timestamp) {
        return simpleDateFormat.format(new Date(timestamp));
    }

    String humanReadableTime() {
        long timestamp = System.currentTimeMillis();
        return humanReadableTime(timestamp);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        setContentView(R.layout.chimera);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        final Intent intent = getIntent();
        String value = intent.getStringExtra("key"); // passing data between activities

        checkPermissions();

        /* setup buttons, etc. */
        checkBoxExternalVideo = (CheckBox) findViewById(R.id.videoSourceExternal);
        checkBoxInternalVideo = (CheckBox) findViewById(R.id.videoSourceInternal);
        checkBoxExternalHd = (CheckBox) findViewById(R.id.externalHd);
        checkBoxInternalHd = (CheckBox) findViewById(R.id.internalHd);
        checkBoxNN = (CheckBox) findViewById(R.id.dnn);
        checkBoxExternalDisplay = (CheckBox) findViewById(R.id.externalDisplay);
        checkBoxInternalDisplay = (CheckBox) findViewById(R.id.internalDisplay);
        checkBoxExternalDnn = (CheckBox) findViewById(R.id.externalDnn);
        checkBoxInternalDnn = (CheckBox) findViewById(R.id.internalDnn);
        checkBoxQtiFaceDetection = (CheckBox) findViewById(R.id.useQtiFace);
        checkBoxAutoExposureFocus = (CheckBox) findViewById(R.id.autoExposureFocus);
        checkBoxOpticalStabilization = (CheckBox) findViewById(R.id.opticalStabilization);
        checkBoxSnapshot = (CheckBox) findViewById(R.id.snapshot);
        checkBoxRecord = (CheckBox) findViewById(R.id.record);
        checkBoxFastImu = (CheckBox) findViewById(R.id.fastImu);
        checkBoxExternalUnwarp = (CheckBox) findViewById(R.id.externalUnwarp);
        checkBoxInternalUnwarp = (CheckBox) findViewById(R.id.internalUnwarp);

        externalOverlay = (CustomSurfaceView) findViewById(R.id.externalOverlay);
        externalOverlay.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        externalOverlay.setActivity(this);
        externalOverlay.setImageDimensions(imageWidth, imageHeight);
        externalSurfaceView = (SurfaceView) findViewById(R.id.externalSurfaceView);
        externalSurfaceHolder = externalSurfaceView.getHolder();

        internalOverlay = (CustomSurfaceView) findViewById(R.id.internalOverlay);
        internalOverlay.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        internalOverlay.setActivity(this);
        internalOverlay.setImageDimensions(imageWidth, imageHeight);
        internalSurfaceView = (SurfaceView) findViewById(R.id.internalSurfaceView);
        internalSurfaceHolder = internalSurfaceView.getHolder();

        checkBoxExternalEncodeH264 = (CheckBox) findViewById(R.id.externalEncodeH264);
        checkBoxInternalEncodeH264 = (CheckBox) findViewById(R.id.internalEncodeH264);

        textView1 = (TextView) findViewById(R.id.text1);
        textView1.setText("");
        textView2 = (TextView) findViewById(R.id.text2);
        textView2.setText("");

        redCircle = BitmapFactory.decodeResource(getResources(), R.drawable.redcircle);
        blueCircle = BitmapFactory.decodeResource(getResources(), R.drawable.bluecircle);
        greenCircle = BitmapFactory.decodeResource(getResources(), R.drawable.greencircle);

        imageViewStatus = (ImageView) findViewById(R.id.status1);
        imageViewStatus.setImageBitmap(null);

        String threadName = TAG;
        workerThread = new HandlerThread(threadName);
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());

        chimera = this;
        activity = this;

        anthea = Anthea.getInstance();

        outputDirectory = String.format("%s/%s", anthea.outputDataDirectory, humanReadableTime());
        File dir = new File(outputDirectory);
        dir.mkdirs();
        textView1.setText(outputDirectory);

        String outputFilename = String.format("%s/sensor_data.csv", outputDirectory);
        final DataLogger dataLogger = DataLogger.getInstance(outputFilename);

        startImuButton = (Button) findViewById(R.id.startImu);
        startImuButton.setTag(1);
        startImuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int action = (int) startImuButton.getTag();
                if (action == 1) {
                    SensorModule sensorModule = SensorModule.getInstance(context);
                    sensorModule.start(checkBoxFastImu.isChecked());
                    sensorRecorder = new SensorRecorder(sensorModule, 10000, dataLogger);
                    if (recordingTimer == null) {
                        recordingTimer = new Timer("recording");
                        TimerTask timerTask = new Chimera.recordingTimerTask();
                        recordingTimer.schedule(timerTask, 1000, 1000);
                    }
                    /* change meaning of the button */
                    startImuButton.setText("stop imu");
                    startImuButton.setTag(0);
                } else if (action == 0) {
                    SensorModule sensorModule = SensorModule.getInstance(context);
                    sensorModule.stop();
                    sensorRecorder.destroy();
                    sensorRecorder = null;
                    imageViewStatus.setImageBitmap(null);
                    if (recordingTimer != null) {
                        recordingTimer.cancel();
                        recordingTimer = null;
                    }
                    /* change meaning of the button */
                    startImuButton.setText("start imu");
                    startImuButton.setTag(1);
                }
            }
        });

        startExternalButton = (Button) findViewById(R.id.startExternal);
        startExternalButton.setTag(1);
        startExternalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                int action = (int) startExternalButton.getTag();

                if (action == 1) {
                    long now = System.currentTimeMillis();

                    if (checkBoxExternalDisplay.isChecked()) {
                        externalSurface = externalSurfaceHolder.getSurface();
                    } else {
                        externalSurface = null;
                    }

                    if (checkBoxExternalVideo.isChecked()) {

                        if (checkBoxExternalHd.isChecked()) {
                            try {
                                externalConfigurationParameters = (ConfigurationParameters) ConfigurationParameters.DEFAULT_EXTERNAL_CONFIGURATION_HD.clone();
                            } catch (CloneNotSupportedException ex) {
                                ex.printStackTrace();
                            }
                        } else {
                            try {
                                externalConfigurationParameters = (ConfigurationParameters) ConfigurationParameters.DEFAULT_EXTERNAL_CONFIGURATION_LD.clone();
                            } catch (CloneNotSupportedException ex) {
                                ex.printStackTrace();
                            }
                        }

                        ConfigurationParameters configurationParameters = externalConfigurationParameters;
                        configurationParameters.context = context;
                        configurationParameters.statusListeners.add((IStatusListener) activity);
                        configurationParameters.instance = Anthea.getInstance();
                        configurationParameters.activity = activity;
                        configurationParameters.useEncoder = checkBoxRecord.isChecked();
                        configurationParameters.unwarp = checkBoxExternalUnwarp.isChecked();
                        if (configurationParameters.unwarp) {
                            AlgoWrapper algoWrapper = AlgoWrapper.getInstance();
                            int cameraIndex = Common.ExternalCamera;
                            int resolution = (configurationParameters.imageWidth == Common.IMAGE_WIDTH_HD) ? 0 : 1;
                            algoWrapper.initializeCameraUnwarping(cameraIndex, resolution);
                            double[] parameters = algoWrapper.getCameraUnwarping(cameraIndex);
                            double a = parameters[0];
                        }

                        if (configurationParameters.useEncoder) {
                            configurationParameters.videoOutputDirectory = outputDirectory;
                            configurationParameters.useH264 = checkBoxExternalEncodeH264.isChecked();
                            if (configurationParameters.useH264) {
                                configurationParameters.videoOutputFile = String.format("%s/%s_video.h264",
                                    outputDirectory, configurationParameters.deviceName);
                            } else {
                                configurationParameters.videoOutputFile = String.format("%s/%s_video.mp4",
                                    outputDirectory, configurationParameters.deviceName);
                            }
//                            configurationParameters.imageHeight = 1088; // TODO
                            configurationParameters.h264Surface = new H264Surface(configurationParameters);
                        }

                        configurationParameters.jpegActive = checkBoxSnapshot.isChecked();
                        if (configurationParameters.jpegActive) {
                            configurationParameters.deltaJpegTime = 10000;
                            configurationParameters.outputDirectory = outputDirectory;
                            configurationParameters.nextJpegTime = now + configurationParameters.deltaJpegTime;
                            configurationParameters.jpegQuality = 80;
                            configurationParameters.jpegSurface = new JpegSurface(configurationParameters);
                        }

                        if (checkBoxExternalDisplay.isChecked()) {
                            configurationParameters.displaySurface = externalSurface;
                            configurationParameters.overlayView = externalOverlay;
                            configurationParameters.oppositeOverlayView = internalOverlay;
                        }

                        if (checkBoxExternalDnn.isChecked()) {
                            configurationParameters.useAlgo = true;
                            configurationParameters.useVehicleDetection = true;
                            String dlcPath;
                            if (checkBoxExternalHd.isChecked()) {
                                dlcPath = "/data/local/tmp/NNFv4_nv21_resize_meansub.dlc";
                            } else {
                                dlcPath = "/data/local/tmp/NNFv4_nv21_resize_meansub_720p.dlc";
                            }
                        }

                        configurationParameters.useAutoExposure = checkBoxAutoExposureFocus.isChecked();
                        configurationParameters.useAutoFocus = checkBoxAutoExposureFocus.isChecked();
                        configurationParameters.useOpticalStabilization = checkBoxOpticalStabilization.isChecked();

//                        Runnable runnable = new Runnable() {
//                            @Override
//                            public void run() {
//                                nautobahn.simpleCameraModuleExternal = new SimpleCameraModule(externalConfigurationParameters);
//                            }
//                        };
//                        Utils.post(workerHandler, runnable, true);
                        anthea.simpleCameraModuleExternal = new SimpleCameraModule(externalConfigurationParameters);
                    }

                    /* change meaning of the button */
                    startExternalButton.setText("STOP");
                    startExternalButton.setTag(0);
                } else {
                    if (anthea.simpleCameraModuleExternal != null) {
                        anthea.simpleCameraModuleExternal.destroy();
                        while(anthea.simpleCameraModuleExternal.gracefulClose == false) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException ex) {
                                ex.printStackTrace();
                            }
                            textView1.setText(anthea.simpleCameraModuleExternal.statusString);
                            textView2.setText("waiting for external camera to shut down");
                        }
                        textView1.setText("external camera successfully closed");
                    }

                    /* change meaning of the button */
                    startExternalButton.setText("START");
                    startExternalButton.setTag(1);
                }
            }
        });

        startInternalButton = (Button) findViewById(R.id.startInternal);
        startInternalButton.setTag(1);
        startInternalButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                int action = (int) startInternalButton.getTag();

                if (action == 1) {
                    long now = System.currentTimeMillis();

                    if (checkBoxInternalDisplay.isChecked()) {
                        internalSurface = internalSurfaceHolder.getSurface();
                    } else {
                        internalSurface = null;
                    }

                    if (checkBoxInternalVideo.isChecked()) {

                        if (checkBoxInternalHd.isChecked()) {
                            try {
                                internalConfigurationParameters = (ConfigurationParameters) ConfigurationParameters.DEFAULT_INTERNAL_CONFIGURATION_HD.clone();
                            } catch (CloneNotSupportedException ex) {
                                ex.printStackTrace();
                            }
                        } else {
                            try {
                                internalConfigurationParameters = (ConfigurationParameters) ConfigurationParameters.DEFAULT_INTERNAL_CONFIGURATION_LD.clone();
                            } catch (CloneNotSupportedException ex) {
                                ex.printStackTrace();
                            }
                        }

                        ConfigurationParameters configurationParameters = internalConfigurationParameters;
                        configurationParameters.context = context;
                        configurationParameters.statusListeners.add((IStatusListener) activity);
                        configurationParameters.instance = Anthea.getInstance();
                        configurationParameters.activity = activity;
                        configurationParameters.useEncoder = checkBoxRecord.isChecked();
                        configurationParameters.unwarp = checkBoxInternalUnwarp.isChecked();
                        if (configurationParameters.unwarp) {
                            AlgoWrapper algoWrapper = AlgoWrapper.getInstance();
                            int cameraIndex = Common.InternalCamera;
                            int resolution = (configurationParameters.imageWidth == Common.IMAGE_WIDTH_HD) ? 0 : 1;
                            algoWrapper.initializeCameraUnwarping(cameraIndex, resolution);
                            double[] parameters = algoWrapper.getCameraUnwarping(cameraIndex);
                            double a = parameters[0];
                        }

                        if (configurationParameters.useEncoder) {
                            configurationParameters.videoOutputDirectory = outputDirectory;
                            configurationParameters.useH264 = checkBoxInternalEncodeH264.isChecked();
                            if (configurationParameters.useH264) {
                                configurationParameters.videoOutputFile = String.format("%s/%s_video.h264",
                                    outputDirectory, configurationParameters.deviceName);
                            } else {
                                configurationParameters.videoOutputFile = String.format("%s/%s_video.mp4",
                                    outputDirectory, configurationParameters.deviceName);
                            }
                            configurationParameters.h264Surface = new H264Surface(configurationParameters);
                        }

                        configurationParameters.jpegActive = checkBoxSnapshot.isChecked();
                        if (configurationParameters.jpegActive) {
                            configurationParameters.deltaJpegTime = 10000;
                            configurationParameters.outputDirectory = outputDirectory;
                            configurationParameters.nextJpegTime = now + configurationParameters.deltaJpegTime / 2;
                            configurationParameters.jpegQuality = 80;
                            configurationParameters.jpegSurface = new JpegSurface(configurationParameters);
                        }

                        if (checkBoxInternalDisplay.isChecked()) {
                            configurationParameters.displaySurface = internalSurface;
                            configurationParameters.overlayView = internalOverlay;
                            configurationParameters.oppositeOverlayView = externalOverlay;
                        }

                        configurationParameters.useAutoExposure = checkBoxAutoExposureFocus.isChecked();
                        configurationParameters.useAutoFocus = checkBoxAutoExposureFocus.isChecked();
                        configurationParameters.useOpticalStabilization = checkBoxOpticalStabilization.isChecked();
                        configurationParameters.useQualcommFaceDetection = checkBoxQtiFaceDetection.isChecked();

                        anthea.simpleCameraModuleInternal = new SimpleCameraModule(internalConfigurationParameters);
                    }

                    /* change meaning of the button */
                    startInternalButton.setText("STOP");
                    startInternalButton.setTag(0);
                } else {

                    if (anthea.simpleCameraModuleInternal != null) {
                        anthea.simpleCameraModuleInternal.destroy();
                        while(anthea.simpleCameraModuleInternal.gracefulClose == false) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException ex) {
                                ex.printStackTrace();
                            }
                            textView1.setText(anthea.simpleCameraModuleInternal.statusString);
                            textView2.setText("waiting for internal camera to shut down");
                        }
                        textView1.setText("internal camera successfully closed");
                    }

                    /* change meaning of the button */
                    startInternalButton.setText("START");
                    startInternalButton.setTag(1);
                }

            }
        });

        exitButton = (Button) findViewById(R.id.exit);
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    boolean status1 = false;
    Runnable toggleStatusRunnable = new Runnable() {
        @Override
        public void run() {
            if (status1) {
                imageViewStatus.setImageBitmap(null);
            } else {
                imageViewStatus.setImageBitmap(redCircle);
            }
            status1 = !status1;
            SensorModule sensorModule = SensorModule.getInstance();
            int externalFrames = 0;
            int externalDnnCount = 0;
            if (anthea.simpleCameraModuleExternal != null) {
                externalFrames = anthea.simpleCameraModuleExternal.getFrameCounter();
            }
            int internalFrames = 0;
            int internalDnnCount = 0;
            if (anthea.simpleCameraModuleInternal != null) {
                internalFrames = anthea.simpleCameraModuleInternal.getFrameCounter();
            }
            String msg = String.format("time = %d", System.currentTimeMillis());
            if (sensorModule != null) {
                msg = String.format("Frames:%d(X)/%d(I) IMU:%d(LIN)/%d(GRV) DNN=%d/%d", externalFrames, internalFrames,
                    sensorModule.linearAccelerationPoints, sensorModule.gameRotationVectorPoints, externalDnnCount, internalDnnCount);
            } else if ((externalFrames != 0) || (internalFrames != 0)) {
                msg = String.format("Frames:%d(X)/%d(I) DNN=%d/%d", externalFrames, internalFrames, externalDnnCount, internalDnnCount);
            }
            textView1.setText(msg);
        }
    };

    public class recordingTimerTask extends TimerTask {
        public void run() {
            runOnUiThread(toggleStatusRunnable);
        }
    }

    public void displayText(final int whichTextView, final String msg) {
        TextView textView = (whichTextView == 1) ? textView1 : textView2;
        displayText(textView, msg);
    }

    public void displayText(final TextView textView, final String msg) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                textView.setText(msg);
            }
        };
        runOnUiThread(runnable);
    }

    public void displayStatus(final String msg) {
        final TextView textView = textView1;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                textView.setText(msg);
            }
        };
        runOnUiThread(runnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Utils.goodbyeThread(workerThread);
        if (sensorRecorder != null) {
            sensorRecorder.destroy();
            sensorRecorder = null;
        }
        SensorModule sensorModule = SensorModule.getInstance();
        if (sensorModule != null) {
            sensorModule.stop();
        }
        if (recordingTimer != null) {
            recordingTimer.cancel();
            recordingTimer = null;
        }
    }

    final int MY_REQUEST_CODE = 0;
    @TargetApi(Build.VERSION_CODES.M)
    private void checkPermissions() {
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion >= Build.VERSION_CODES.M) {
            boolean cameraOk = checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
            boolean sdOk = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
            String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            };
            if (!cameraOk || !sdOk) {
                requestPermissions(permissions, MY_REQUEST_CODE);
            }
//            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//                requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_REQUEST_CODE);
//            }
        } else{
            // do something for phones running an SDK before lollipop
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_REQUEST_CODE:
                if (grantResults != null && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Camera/SD access granted");
                }
                break;
        }
    }
}
