package com.nauto;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.nauto.modules.camera.Common;
import com.nauto.modules.camera.ConfigurationParameters;
import com.nauto.modules.camera.H264Surface;
import com.nauto.modules.camera.ICameraModuleStatusListener;
import com.nauto.modules.camera.SimpleCameraModule;
import com.nauto.modules.sensor.SensorModule;
import com.nauto.modules.sensor.SensorRecorder;
import com.nauto.modules.utils.DataLogger;
import com.nauto.modules.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import static com.nauto.modules.utils.Utils.humanReadableTime;

/**
 * Created by jsvirzi on 1/15/17.
 */

public class DogFood extends Activity implements MediaPlayer.OnCompletionListener, ICameraModuleStatusListener {
    private static String TAG = "DogFood";
    public static DogFood dogFood;
    private Context context;
    private Anthea anthea;
    public ConfigurationParameters internalConfigurationParameters;
    public ConfigurationParameters externalConfigurationParameters;
    private Activity activity;
    private SensorRecorder sensorRecorder;
    private SensorModule sensorModule;
    private String outputDirectory;
    private int timerPhase = 0;
    private Button buttonStop;
    private TextView textViewDistress;
    private TextView textViewStatus;
    private TextView textViewExposure;
    TimerTask hereIAmTimerTask;
    Timer hereIAmTimer;
    MediaPlayer mediaPlayer;
    private static final int ExternalDnnMask = 1;
    private static final int InternalDnnMask = 2;

    public void displayStatus(final String msg) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                textViewStatus.setText(msg);
            }
        };
        runOnUiThread(runnable);
    }

    public void displayDistress(final String msg) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                textViewDistress.setText(msg);
            }
        };
        runOnUiThread(runnable);
    }

    public void sendDistress(final String msg, final int level) {
        Intent intent = new Intent();
        intent.setAction("com.nautobahn.distress");
        intent.putExtra("message", msg);
        intent.putExtra("level", level);
        context.sendBroadcast(intent);
        displayDistress(msg);
    }

    public void sendStatus(final String msg, final int whichCamera) {
        Intent intent = new Intent();
        intent.setAction("com.nautobahn.status");
        intent.putExtra("message", msg);
        intent.putExtra("led", whichCamera);
        context.sendBroadcast(intent);
        displayStatus(msg);
    }

    public void onCompletion(MediaPlayer mediaPlayer) {
        mediaPlayer.stop();
        mediaPlayer.release();
    }

    private class HereIAmTimerTask extends TimerTask {
        public void run() {

            final long exposureTime = internalConfigurationParameters.h264Surface.getExposureTime() *
                internalConfigurationParameters.frameRate; /* total exposure per second */
            final long exposureTimeThreshold = 500000000L; /* total nanoseconds exposure per second */

            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    String msg = String.format("%d/%d", exposureTime, exposureTimeThreshold);
                    textViewExposure.setText(msg);
                }
            };
            runOnUiThread(runnable);

            if (exposureTime > exposureTimeThreshold) {
                Intent intent = new Intent();
                intent.setAction("com.nautobahn.itstoodark");
                Log.d(TAG, "I'm scared of the dark");
                context.sendBroadcast(intent);
            } else {
                Intent intent = new Intent();
                intent.setAction("com.nautobahn.itslight");
                Log.d(TAG, "Good Morning Sunshine");
                context.sendBroadcast(intent);
            }

            if ((timerPhase & 1) == 0) {
                final boolean playSound = false;
                if (playSound) {
                    long now = System.currentTimeMillis();
                    String msg = String.format("current time = %d", now);
                    displayStatus(msg);
                    mediaPlayer = MediaPlayer.create(context, R.raw.beep);
                    mediaPlayer.setOnCompletionListener((MediaPlayer.OnCompletionListener) activity);
                    mediaPlayer.start();
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        setContentView(R.layout.dogfood);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        Intent intent = getIntent();
        boolean nightMode = (intent.getIntExtra("night", 0) == 1);
        int resolution = intent.getIntExtra("resolution", Common.IMAGE_HEIGHT_LD);
        int dnnMask = intent.getIntExtra("dnnmask", 0);
        boolean vehicleDetection = ((dnnMask & ExternalDnnMask) != 0);

        Utils.checkPermissions(this);

        dogFood = this;
        activity = this;

        buttonStop = (Button) findViewById(R.id.stop);
        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                destroy();
                finish();
            }
        });

        textViewStatus = (TextView) findViewById(R.id.status);
        textViewDistress = (TextView) findViewById(R.id.distress);
        textViewExposure = (TextView) findViewById(R.id.exposure);

        anthea = Anthea.getInstance();

        Random rndm = new Random();
        int randomInteger = rndm.nextInt();
        if (randomInteger < 0) randomInteger = -randomInteger;

        outputDirectory = String.format("%s/%s-%d", anthea.outputDataDirectory, humanReadableTime(), randomInteger);
        File dir = new File(outputDirectory);
        dir.mkdirs();

        String msg = String.format(Common.LOCALE, "output directory = [%s]", outputDirectory);
        displayStatus(msg);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        try {
            if ((resolution == 0) || (resolution == Common.IMAGE_HEIGHT_LD)) {
                externalConfigurationParameters = (ConfigurationParameters) ConfigurationParameters.DEFAULT_EXTERNAL_CONFIGURATION_LD.clone();
            } else if (resolution == Common.IMAGE_HEIGHT_HD) {
                externalConfigurationParameters = (ConfigurationParameters) ConfigurationParameters.DEFAULT_EXTERNAL_CONFIGURATION_HD.clone();
            }
        } catch (CloneNotSupportedException ex) {
            ex.printStackTrace();
        }
        ConfigurationParameters configurationParameters = externalConfigurationParameters;
        configurationParameters.bitRate = 20000000;
        configurationParameters.context = context;
        configurationParameters.statusListeners = new ArrayList<IStatusListener>(1);
        configurationParameters.instance = Anthea.getInstance();
        configurationParameters.activity = activity;
        configurationParameters.useAutoExposure = true;
        configurationParameters.useAutoFocus = true;
        configurationParameters.useOpticalStabilization = true;
        configurationParameters.videoOutputDirectory = outputDirectory;
        configurationParameters.useH264 = false;
        configurationParameters.videoOutputFile = String.format("%s/external_video.mp4", outputDirectory);
        configurationParameters.h264Surface = new H264Surface(configurationParameters);
        configurationParameters.cameraModuleStatusListener = this;
        configurationParameters.jpegActive = false;

        try {
            if ((resolution == 0) || (resolution == Common.IMAGE_HEIGHT_LD)) {
                internalConfigurationParameters = (ConfigurationParameters) ConfigurationParameters.DEFAULT_INTERNAL_CONFIGURATION_LD.clone();
            } else if (resolution == Common.IMAGE_HEIGHT_HD) {
                internalConfigurationParameters = (ConfigurationParameters) ConfigurationParameters.DEFAULT_INTERNAL_CONFIGURATION_HD.clone();
            }
        } catch (CloneNotSupportedException ex) {
            ex.printStackTrace();
        }
        configurationParameters = internalConfigurationParameters;
        configurationParameters.bitRate = 20000000;
        configurationParameters.context = context;
        configurationParameters.statusListeners = new ArrayList<IStatusListener>(1);
        configurationParameters.instance = Anthea.getInstance();
        configurationParameters.activity = activity;
        configurationParameters.useAutoExposure = true;
        configurationParameters.useAutoFocus = true;
        configurationParameters.useOpticalStabilization = true;
        configurationParameters.videoOutputDirectory = outputDirectory;
        configurationParameters.useH264 = false;
        configurationParameters.videoOutputFile = String.format("%s/internal_video.mp4", outputDirectory);
        configurationParameters.h264Surface = new H264Surface(configurationParameters);
        configurationParameters.cameraModuleStatusListener = this;
        configurationParameters.jpegActive = false;
        configurationParameters.nightMode = nightMode;

        displayStatus("configuration parameters complete");

        anthea.simpleCameraModuleExternal = new SimpleCameraModule(externalConfigurationParameters);

        displayStatus("external camera complete");

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        anthea.simpleCameraModuleInternal = new SimpleCameraModule(internalConfigurationParameters);

        displayStatus("internal camera complete");

        sensorModule = SensorModule.getInstance(context);
        sensorModule.start();
        String outputFilename = String.format("%s/sensor_data.csv", outputDirectory);
        DataLogger dataLogger = DataLogger.getInstance(outputFilename);
        sensorRecorder = new SensorRecorder(sensorModule, 1000, dataLogger);

        displayStatus("sensor module complete");

        hereIAmTimerTask = new DogFood.HereIAmTimerTask();
        hereIAmTimer = new Timer("HereIAm");
        hereIAmTimer.schedule(hereIAmTimerTask, 5000, 30000);

        displayStatus("timer task complete");
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    public void destroy() {
        if (anthea.simpleCameraModuleExternal != null) {
            anthea.simpleCameraModuleExternal.destroy();
            while(anthea.simpleCameraModuleExternal.gracefulClose == false) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
            anthea.simpleCameraModuleExternal = null;
        }

        if (anthea.simpleCameraModuleInternal != null) {
            anthea.simpleCameraModuleInternal.destroy();
            while(anthea.simpleCameraModuleInternal.gracefulClose == false) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
            anthea.simpleCameraModuleInternal = null;
        }

        if (sensorRecorder != null) {
            sensorRecorder.destroy();
            sensorRecorder = null;
        }

        if (sensorModule != null) {
            sensorModule.stop();
            sensorModule = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroy();
        int pid = android.os.Process.myPid();
        android.os.Process.killProcess(pid);
        finishAffinity();
    }
}
