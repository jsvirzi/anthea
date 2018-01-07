package com.mam.lambo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.TextView;

import com.nauto.modules.sensor.SensorModule;
import com.nauto.modules.sensor.SensorRecorder;
import com.nauto.modules.utils.DataLogger;
import com.nauto.modules.utils.Utils;

import java.io.File;
import java.util.Random;

import static com.nauto.modules.utils.Utils.humanReadableTime;

/**
 * Created by jsvirzi on 2/5/17.
 */

public class SweepFanSpeeds extends Activity {
    private static final String TAG = "SweepFanSpeeds";
    private Context context;
    private Anthea anthea;
    private Activity activity;
    private HandlerThread workerThread;
    private Handler workerHandler;
    private SensorRecorder sensorRecorder;
    private SensorModule sensorModule;
    private String outputDirectory;
    private TextView textViewStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        setContentView(R.layout.sweep_fan_speeds);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        Utils.checkPermissions(this);

        Random rndm = new Random();
        int randomInteger = rndm.nextInt();
        if (randomInteger < 0) randomInteger = -randomInteger;

        anthea = Anthea.getInstance();
        outputDirectory = String.format("%s/%s-%d", anthea.outputDataDirectory, humanReadableTime(), randomInteger);
        File dir = new File(outputDirectory);
        boolean status = dir.mkdirs();
        if (status == false) {
            String msg = String.format("unable to create directory [%s] or directory already existed", outputDirectory);
            Log.e(TAG, msg);
            return;
        }

        textViewStatus = (TextView) findViewById(R.id.status);

        sensorRecorder = null;

        String threadName = TAG;
        workerThread = new HandlerThread(threadName);
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
    }

    @Override
    protected void onStart() {
        super.onStart();
        sensorModule = SensorModule.getInstance(context);
        sensorModule.start();
        workerHandler.post(runnable);
    }

    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            int fanSpeedIndex;
            int period = 1000; /* once per second */
            int phase;
            DataLogger dataLogger = null;
            for(phase=31;phase>=0;--phase) {
                for (fanSpeedIndex = 7; fanSpeedIndex >= 2; --fanSpeedIndex) {
                    final int fanSpeed = fanSpeedIndex * 32 + phase;
                    if (sensorRecorder != null) {
                        sensorRecorder.destroy();
                    }
                    if (dataLogger != null) {
                        dataLogger.destroy();
                    }

                /* set fan speed */
                    Intent intent = new Intent();
                    intent.setAction("com.nautobahn.fanspeed");
                    intent.putExtra("speed", fanSpeed);
                    context.sendBroadcast(intent);
                    Utils.wait(5000); /* just wait five seconds to make sure new fan speed is good */

                    String filename = String.format("%s/sensorData_fanSpeed_%d.csv", outputDirectory, fanSpeed);
                    dataLogger = DataLogger.getInstance(filename);
                    Log.d(TAG, String.format("output file = [%s]", filename));
                    sensorRecorder = new SensorRecorder(sensorModule, period, dataLogger);
                    Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            textViewStatus.setText(String.format("fan speed = %d", fanSpeed));
                        }
                    };
                    runOnUiThread(runnable);
                    Utils.wait(100000); /* roughly 100 seconds of data per point */
                }
            }
            if (sensorRecorder != null) {
                sensorRecorder.destroy();
            }
            if (dataLogger != null) {
                dataLogger.destroy();
            }
            finish();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Utils.goodbyeThread(workerThread);
        sensorModule.stop();
        sensorModule.destroy();
    }
}
