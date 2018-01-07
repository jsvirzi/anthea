package com.nauto;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

//import com.nauto.modules.camera.CameraModule;
//import com.nauto.modules.camera.CameraModuleSentinel;
//import com.nauto.modules.camera.VideoSnippet;
//import com.nauto.modules.camera.VideoStreamEncoderSentinel;
//import com.nauto.modules.facedetection.GoogleFaceDetectionData;
//import com.nauto.modules.sensor.SensorModule;

import com.mam.lambo.CustomSurfaceView;
import com.nauto.modules.camera.ConfigurationParameters;
import com.nauto.modules.camera.SimpleCameraModule;
import com.nauto.modules.server.SimpleServer;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by jsvirzi on 6/10/16.
 */
public class IlBacio extends Activity {
    private static String TAG = "IlBacio";
    public static IlBacio ilBacio;
    SurfaceView externalSurfaceView;
    SurfaceView internalSurfaceView;
    CustomSurfaceView externalOverlay;
    CustomSurfaceView internalOverlay;
    Context context;
    private Button exitButton;
    private Button startButton;
    private SurfaceHolder externalSurfaceHolder;
    private SurfaceHolder internalSurfaceHolder;
    private Surface externalSurface = null;
    private Surface internalSurface = null;
    private ImageView imageViewStatus;
    private CheckBox checkBoxExternalDnn;
    private CheckBox checkBoxInternalDnn;
    Bitmap redCircle;
    Bitmap blueCircle;
    Bitmap greenCircle;
    TextView textView1;
    TextView textView2;
    Timer recordingTimer;
    int imageWidth = 1920;
    int imageHeight = 1080;
    public ConfigurationParameters internalConfigurationParameters = new ConfigurationParameters();
    public ConfigurationParameters externalConfigurationParameters = new ConfigurationParameters();
    SimpleServer simpleServer;
    Anthea anthea;
    Activity activity;
    boolean requestTermination = false;

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
        final Intent intent = getIntent();
        String value = intent.getStringExtra("key"); // passing data between activities

        ilBacio = this;
        activity = this;

        anthea = Anthea.getInstance();

        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        setContentView(R.layout.ilbacio);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

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

        redCircle = BitmapFactory.decodeResource(getResources(), R.drawable.redcircle);
        blueCircle = BitmapFactory.decodeResource(getResources(), R.drawable.bluecircle);
        greenCircle = BitmapFactory.decodeResource(getResources(), R.drawable.greencircle);

        imageViewStatus = (ImageView) findViewById(R.id.status1);
        imageViewStatus.setImageBitmap(null);

        checkBoxExternalDnn = (CheckBox) findViewById(R.id.externalDnn);
        checkBoxInternalDnn = (CheckBox) findViewById(R.id.internalDnn);

        startButton = (Button) findViewById(R.id.start);
        startButton.setTag(1);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                int action = (int) startButton.getTag();

                if (action == 1) {
                    String outputDirectory = String.format("%s/%s", anthea.outputDataDirectory, humanReadableTime());
                    File dir = new File(outputDirectory);
                    dir.mkdirs();
                    String outputFilename;
                    textView1.setText(outputDirectory);

                    long now = System.currentTimeMillis();

                    externalSurface = externalSurfaceHolder.getSurface();
                    internalSurface = internalSurfaceHolder.getSurface();

                    ConfigurationParameters parameters;

                    parameters = externalConfigurationParameters;

                    parameters.useEncoder = false;
                    parameters.imageHeight = anthea.imageHeight;
                    parameters.imageWidth = anthea.imageWidth;
                    parameters.frameRate = 30;
                    parameters.context = context;
                    parameters.deviceName = "external";
                    parameters.useJpeg = false;
                    parameters.displaySurface = externalSurface;
                    parameters.overlayView = externalOverlay;
                    parameters.oppositeOverlayView = internalOverlay;
//                    parameters.id = "0";

                    parameters.useH264 = false;
                    parameters.useAutoExposure = true;
                    parameters.useAutoFocus = true;
                    parameters.useOpticalStabilization = true;
                    parameters.useQualcommFaceDetection = false;
                    parameters.useGoogleFaceDetection = false;
                    parameters.instance = Anthea.getInstance();
                    parameters.activity = activity;
                    parameters.useAlgo = true;
                    parameters.useAttentionDeficit = false;

                    anthea.simpleCameraModuleExternal = new SimpleCameraModule(externalConfigurationParameters);

                    parameters = internalConfigurationParameters;

                    parameters.useEncoder = false;
                    parameters.imageHeight = anthea.imageHeight;
                    parameters.imageWidth = anthea.imageWidth;
                    parameters.frameRate = 30;
                    parameters.context = context;
                    parameters.deviceName = "internal";
                    parameters.outputDirectory = null;
                    parameters.displaySurface = internalSurface;
                    parameters.overlayView = internalOverlay;
                    parameters.oppositeOverlayView = externalOverlay;
//                    parameters.id = "1";

                    parameters.dnnWidth = 1152; /* 60% of 1920 */
                    parameters.dnnHeight = 1080;
                    parameters.useH264 = false;
                    parameters.useAutoExposure = true;
                    parameters.useAutoFocus = true;
                    parameters.useOpticalStabilization = true;
                    parameters.useQualcommFaceDetection = false;
                    parameters.useGoogleFaceDetection = false;
                    parameters.instance = Anthea.getInstance();
                    parameters.activity = activity;
                    parameters.useAlgo = true;
                    parameters.useAttentionDeficit = true;

                    anthea.simpleCameraModuleInternal = new SimpleCameraModule(internalConfigurationParameters);

//                    nautobahn.sensorRecorder = null;

                    /* change meaning of the button */
                    startButton.setText("STOP");
                    startButton.setTag(0);
                } else {

                    if (simpleServer != null) {
                        simpleServer.destroy();
                    }

//                    nautobahn.sensorModule.stop();
//                    if(nautobahn.sensorRecorder != null) {
//                        nautobahn.sensorRecorder.destroy();
//                        nautobahn.sensorRecorder = null;
//                        if (recordingTimer != null) {
//                            recordingTimer.cancel();
//                        }
//                    }

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
                        textView2.setText("external camera successfully closed");
                    }
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
                        textView2.setText("internal camera successfully closed");
                    }

                    /* change meaning of the button */
                    startButton.setText("START");
                    startButton.setTag(1);
                }
            }
        });

        exitButton = (Button) findViewById(R.id.exit);
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestTermination = true;
                anthea.shutDown();
                finishAffinity();
            }
        });

        textView1 = (TextView) findViewById(R.id.text1);
        textView1.setText("");
        textView2 = (TextView) findViewById(R.id.text2);
        textView2.setText("");
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
            String statusMsg = String.format("Frames:%d(X)/%d(I)", anthea.simpleCameraModuleExternal.getFrameCounter(),
                anthea.simpleCameraModuleInternal.getFrameCounter());
            textView2.setText(statusMsg);
        }
    };

    public void toggleRecordingStatus() {
        runOnUiThread(toggleStatusRunnable);
    }

    public class recordingTimerTask extends TimerTask {
        public void run() {
            toggleRecordingStatus();
        }
    }

    public void displayText(final int whichTextView, final String msg) {
        TextView textView = (whichTextView == 1) ? textView1 : textView2;
        displayText(textView, msg);
    }

    public void displayText(final TextView textview, final String msg) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                textview.setText(msg);
            }
        };
        runOnUiThread(runnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (requestTermination) {
            terminate();
        }
    }

    public void terminate() {
        int pid = android.os.Process.myPid();
        android.os.Process.killProcess(pid);
        finishAffinity();
    }
}
