package com.mam.lambo;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.nauto.modules.camera.Common;
import com.nauto.modules.camera.ConfigurationParameters;
import com.nauto.modules.camera.H264Surface;
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
 * Created by jsvirzi on 5/6/17.
 */

public class ImuLogger extends Activity {
    private static String TAG = "ImuLogger";
    public static ImuLogger imuLogger;
    private Context context;
    private Anthea anthea;
    public ConfigurationParameters internalConfigurationParameters;
    public ConfigurationParameters externalConfigurationParameters;
    private Activity activity;
    private SensorRecorder sensorRecorder;
    private SensorModule sensorModule;
    private String outputDirectory;
    private Button buttonStop;
    TimerTask hereIAmTimerTask;
    Timer hereIAmTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        setContentView(R.layout.dogfood);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        Utils.checkPermissions(this);

        imuLogger = this;
        activity = this;

        buttonStop = (Button) findViewById(R.id.stop);
        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                destroy();
                finish();
            }
        });

        anthea = Anthea.getInstance();

        Random rndm = new Random();
        int randomInteger = rndm.nextInt();
        if (randomInteger < 0) randomInteger = -randomInteger;

        outputDirectory = String.format("%s/%s-%d", anthea.outputDataDirectory, humanReadableTime(), randomInteger);
        File dir = new File(outputDirectory);
        dir.mkdirs();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }

        sensorModule = SensorModule.getInstance(context);
        sensorModule.start();
        String outputFilename = String.format("%s/sensor_data.csv", outputDirectory);
        DataLogger dataLogger = DataLogger.getInstance(outputFilename);
        sensorRecorder = new SensorRecorder(sensorModule, 10000, dataLogger);
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
