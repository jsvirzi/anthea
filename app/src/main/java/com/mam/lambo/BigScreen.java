package com.mam.lambo;

/**
 * Created by jsvirzi on 7/13/16.
 */

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.nauto.modules.calibration.OrientationCalibration;
import com.nauto.modules.sensor.SensorModule;

/**
 * Created by jsvirzi on 6/10/16.
 */
public class BigScreen extends Activity {
    private static String TAG = "BigScreen";
    public static BigScreen bigScreen;
    Context context;
    private Button buttonExit;
    private Button buttonVehicle;
    private Button buttonChimera;
    private Button buttonDistraction;
    private Button buttonIlBacio;
    private Button buttonDogFood;
    private Button buttonServer;
    private Button buttonTestVectors;
    private Button buttonFanSpeeds;
    private Button buttonObd;
    private Button buttonCalibrate;
    private Button buttonVerify;
    private Button buttonOffline;
    private Button buttonImuLogger;
    Anthea anthea;
    Activity activity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final Intent intent = getIntent();
        String value = intent.getStringExtra("key"); // passing data between activities

        bigScreen = this;
        activity = this;

        anthea = Anthea.getInstance();

        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        setContentView(R.layout.big_screen);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        buttonChimera = (Button) findViewById(R.id.chimera);
        buttonChimera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(bigScreen, Chimera.class);
                startActivity(intent);
            }
        });

        buttonDogFood = (Button) findViewById(R.id.dogfood);
        buttonDogFood.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(bigScreen, DogFood.class);
                startActivity(intent);
            }
        });

        buttonImuLogger = (Button) findViewById(R.id.imulogger);
        buttonImuLogger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(bigScreen, ImuLogger.class);
                startActivity(intent);
            }
        });

        buttonIlBacio = (Button) findViewById(R.id.ilbacio);
        buttonIlBacio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(bigScreen, IlBacio.class);
                startActivity(intent);
            }
        });

        buttonServer = (Button) findViewById(R.id.server);
        buttonServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(bigScreen, Upload.class);
                startActivity(intent);
            }
        });

        buttonObd = (Button) findViewById(R.id.obdReader);
        buttonObd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(bigScreen, ObdIIReader.class);
                startActivity(intent);
            }
        });

        buttonExit = (Button) findViewById(R.id.exit);
        buttonExit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                terminate();
            }
        });

        buttonCalibrate = (Button) findViewById(R.id.calibrate);
        buttonCalibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SensorModule sensorModule = SensorModule.getInstance(context);
                sensorModule.start();
                OrientationCalibration orientationCalibration = new OrientationCalibration(sensorModule);
                orientationCalibration.addListener(sensorModule);
                orientationCalibration.oneShot(60000);
            }
        });

        buttonVerify = (Button) findViewById(R.id.verify);
        buttonVerify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SensorModule sensorModule = SensorModule.getInstance(context);
                sensorModule.start();
                OrientationCalibration orientationCalibration = new OrientationCalibration(sensorModule);
                orientationCalibration.addListener(sensorModule);
                orientationCalibration.oneShot(60000, true);
            }
        });
    }

//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        if (requestTermination) {
//            terminate();
//        }
//    }

    public void terminate() {
        int pid = android.os.Process.myPid();
        android.os.Process.killProcess(pid);
        finishAffinity();
    }
}
