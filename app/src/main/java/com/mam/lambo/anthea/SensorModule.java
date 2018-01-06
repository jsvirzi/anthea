package com.mam.lambo.anthea.sensor;

/*
 * TODO implement destroy() that frees up the static buffers
 */

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Handler;
import android.util.Log;

import static android.os.Process.THREAD_PRIORITY_DEFAULT;
import static android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY;

/* Created by Joseph Virzi May 17, 2016
 * Its job is to collect IMU and GPS data
 * Any module can consume its data, simply by initializing a tail = head
 * so long as tail != head, there is new data for consumption
 * the math is straightforward (where size = length of circular buffer):
 * nAvailable = (head - tail + size) % size;
 */

public class SensorModule implements SensorEventListener, IDeviceOrientationCalibrationListener {

    private static final String TAG = "SensorModule";
    private static SensorModule instance = null;
    private Context context;
    private Handler handler;
    private HandlerThread thread;

    public static class Space {
        public float x;
        public float y;
        public float z;
        public long t;
        public long sysTime;
    }

    public static class Attitude {
        public float yaw;
        public float pitch;
        public float roll;
        public long t;
        public long sysTime;
    }

    public static class Gps {
        public float latitude;
        public float longitude;
        public float altitude;
        public float accuracy;
        public float bearing;
        public int numberOfSatellites;
        public float hdop;
        public float vdop;
        public float pdop;
        public float speed;
        public long sensortimestamp;
        public long eventTimestamp;
    }

    public int gpsBufferSize;
    public Gps[] gpsBuffer;
    public float latitude;
    public float longitude;
    public int gpsBufferHead;
    public int gpsPoints;

    public int gameRotationVectorBufferSize;
    public Space[] gameRotationVectorBuffer;
    public Space[] calibratedGameRotationVectorBuffer;
    public int gameRotationVectorBufferHead;
    public int gameRotationVectorPoints;

    public int gameRotationAttitudeBufferSize;
    public Attitude[] gameRotationAttitudeBuffer;
    public Attitude[] calibratedGameRotationAttitudeBuffer;
    public int gameRotationAttitudeBufferHead;
    public int gameRotationAttitudePoints;

    public int linearAccelerationBufferSize;
    public Space[] linearAccelerationBuffer;
    public int linearAccelerationBufferHead;
    public int linearAccelerationPoints;

    public int accelerometerBufferSize;
    public Space[] accelerometerBuffer;
    public Space[] calibratedAccelerometerBuffer;
    public int accelerometerBufferHead;
    public int accelerometerPoints;

    public int gyroscopeBufferSize;
    public Space[] gyroscopeBuffer;
    public Space[] calibratedGyroscopeBuffer;
    public int gyroscopeBufferHead;
    public int gyroscopePoints;

    public int magnetometerBufferSize;
    public Space[] magnetometerBuffer;
    public int magnetometerBufferHead;
    public long magnetometerPoints;

    public Gps getCurrentGps() {
        int pos = (gpsBufferHead - 1 + gpsBufferSize) % gpsBufferSize;
        return gpsBuffer[pos];
    }

    public LocationListener locationListener;
    public static final double RADIANS_TO_DEGREES = 180.0 / 3.1415926535;
    private static final int DefaultPeriod = 60000;

    private SensorManager sensorManager;
    private Sensor gameRotationVector;
    private Sensor linearAcceleration;
    private Sensor gyroscope;
    private Sensor accelerometer;
    private Sensor magnetometer;
    /* for private use, avoiding constant new, new, new, new... */
    private float[] rotationMatrix = new float[9];
    private float[][] calibrationMatrix;
    private float[] quaternion = new float[4];
    private float[] eulerAngles = new float[3];

    private boolean linearAccelerationActive = false;
    private boolean magnetometerActive = false;

    public static SensorModule getInstance(Context context) {
        if (instance == null) {
            instance = new SensorModule(context, DefaultPeriod);
        }
        return instance;
    }

    public static SensorModule getInstance() {
        return instance;
    }

    private SensorModule(Context context, int period) {

        calibrationMatrix = new float[][] {{1.0f,0.0f,0.0f},{0.0f,1.0f,0.0f},{0.0f,0.0f,1.0f}};

        this.context = context;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        int numberOfSecondsInPeriod = period / 1000;
        int maximumSamples = 4 * numberOfSecondsInPeriod * 200;

        gpsBufferSize = 4 * numberOfSecondsInPeriod;
        gameRotationVectorBufferSize = maximumSamples;
        gameRotationAttitudeBufferSize = maximumSamples;
        linearAccelerationBufferSize = maximumSamples;
        accelerometerBufferSize = maximumSamples;
        gyroscopeBufferSize = maximumSamples;
        magnetometerBufferSize = maximumSamples;

        gameRotationVectorBufferHead = 0;
        gameRotationVectorPoints = 0;
        linearAccelerationBufferHead = 0;
        linearAccelerationPoints = 0;
        accelerometerBufferHead = 0;
        accelerometerPoints = 0;
        gyroscopeBufferHead = 0;
        gyroscopePoints = 0;
        magnetometerBufferHead = 0;
        magnetometerPoints = 0;
        gpsBufferHead = 0;
        gpsPoints = 0;
        gameRotationAttitudeBufferHead = 0;
        gameRotationAttitudePoints = 0;

        gpsBuffer = new Gps[gpsBufferSize];
        for (int i = 0; i < gpsBufferSize; i++) {
            gpsBuffer[i] = new Gps();
        }

        gameRotationVectorBuffer = new Space[gameRotationVectorBufferSize];
        for (int i = 0; i < gameRotationVectorBufferSize; ++i) {
            gameRotationVectorBuffer[i] = new Space();
        }

        calibratedGameRotationVectorBuffer = new Space[gameRotationVectorBufferSize];
        for (int i = 0; i < gameRotationVectorBufferSize; ++i) {
            calibratedGameRotationVectorBuffer[i] = new Space();
        }

        gameRotationAttitudeBuffer = new Attitude[gameRotationAttitudeBufferSize];
        for (int i = 0; i < gameRotationAttitudeBufferSize; ++i) {
            gameRotationAttitudeBuffer[i] = new Attitude();
        }

        calibratedGameRotationAttitudeBuffer = new Attitude[gameRotationAttitudeBufferSize];
        for (int i = 0; i < gameRotationAttitudeBufferSize; ++i) {
            calibratedGameRotationAttitudeBuffer[i] = new Attitude();
        }

        if (linearAccelerationActive) {
            linearAccelerationBuffer = new Space[linearAccelerationBufferSize];
            for (int i = 0; i < linearAccelerationBufferSize; ++i) {
                linearAccelerationBuffer[i] = new Space();
            }
        }

        accelerometerBuffer = new Space[accelerometerBufferSize];
        for (int i = 0; i < accelerometerBufferSize; ++i) {
            accelerometerBuffer[i] = new Space();
        }

        calibratedAccelerometerBuffer = new Space[accelerometerBufferSize];
        for (int i = 0; i < accelerometerBufferSize; ++i) {
            calibratedAccelerometerBuffer[i] = new Space();
        }

        gyroscopeBuffer = new Space[gyroscopeBufferSize];
        for (int i = 0; i < gyroscopeBufferSize; ++i) {
            gyroscopeBuffer[i] = new Space();
        }

        calibratedGyroscopeBuffer = new Space[gyroscopeBufferSize];
        for (int i = 0; i < gyroscopeBufferSize; ++i) {
            calibratedGyroscopeBuffer[i] = new Space();
        }

        if (magnetometerActive) {
            magnetometerBuffer = new Space[magnetometerBufferSize];
            for (int i = 0; i < magnetometerBufferSize; ++i) {
                magnetometerBuffer[i] = new Space();
            }
        }

        gameRotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (linearAccelerationActive) {
            linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        }
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if(magnetometerActive) {
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        thread = new HandlerThread(TAG, THREAD_PRIORITY_DEFAULT);
        thread.start();
        handler = new Handler(thread.getLooper());

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                final long now = System.currentTimeMillis();
                latitude = (float) location.getLatitude();
                longitude = (float) location.getLongitude();
                Gps gps = gpsBuffer[gpsBufferHead];
                gps.latitude = latitude;
                gps.longitude = longitude;
                gps.pdop = 0; // TODO wtf? no such information. accuracy?
                gps.hdop = 0; // TODO wtf? no such information. accuracy?
                gps.vdop = 0; // TODO wtf? no such information. accuracy?
                if (location.hasAltitude()) {
                    gps.altitude = (float) location.getAltitude();
                } else {
                    gps.altitude = -20000.0f * 5556.0f; // 20 thousand leagues under the sea
                }
                if (location.hasBearing()) {
                    gps.bearing = location.getBearing();
                } else {
                    gps.bearing = 0.0f;
                }
                if (location.hasSpeed()) {
                    gps.speed = location.getSpeed();
                } else {
                    gps.speed = 0.0f;
                }
                if (location.hasAccuracy()) {
                    gps.accuracy = location.getAccuracy();
                } else {
                    gps.accuracy = 0.0f;
                }
                gps.eventTimestamp = now;
                gps.sensortimestamp = location.getTime();
                gpsBufferHead = (gpsBufferHead + 1) % gpsBufferSize;

                try {
                    if (location.getExtras().containsKey("satellites")) {
                        gps.numberOfSatellites = location.getExtras().getInt("satellites");
                    } else {
                        gps.numberOfSatellites = 0;
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };
    }

    public void start() {
        start(true);
    }

    public void start(boolean fastMode) {
        if (fastMode) {
            sensorManager.registerListener(this, gameRotationVector, SensorManager.SENSOR_DELAY_FASTEST, handler);
            if (linearAccelerationActive) {
                sensorManager.registerListener(this, linearAcceleration, SensorManager.SENSOR_DELAY_FASTEST, handler);
            }
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST, handler);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST, handler);
            if (magnetometerActive) {
                sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST, handler);
            }
        } else {
            sensorManager.registerListener(this, gameRotationVector, SensorManager.SENSOR_DELAY_GAME, handler);
            if (linearAccelerationActive) {
                sensorManager.registerListener(this, linearAcceleration, SensorManager.SENSOR_DELAY_GAME, handler);
            }
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME, handler);
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME, handler);
            if (magnetometerActive) {
                sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME, handler);
            }
        }
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);
        } catch (SecurityException ex) {
            ex.printStackTrace();
        }
    }

    public void stop() {
        sensorManager.unregisterListener(this);
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        try {
            locationManager.removeUpdates(locationListener);
        } catch (SecurityException ex) {
            Log.d(TAG, "SecurityException caught stopping GPS", ex);
        }
    }

    public void release() {
        sensorManager.unregisterListener(this);
    }

    public void onCalibrationChanged(float[][] matrix) {
        calibrationMatrix = matrix;
    }

    private void calibrate(Space oldDatum, Space newDatum) {
        float x = oldDatum.x;
        float y = oldDatum.y;
        float z = oldDatum.z;
        newDatum.x = calibrationMatrix[0][0] * x + calibrationMatrix[0][1] * y + calibrationMatrix[0][2] * z;
        newDatum.y = calibrationMatrix[1][0] * x + calibrationMatrix[1][1] * y + calibrationMatrix[1][2] * z;
        newDatum.z = calibrationMatrix[2][0] * x + calibrationMatrix[2][1] * y + calibrationMatrix[2][2] * z;
        newDatum.t = oldDatum.t;
        newDatum.sysTime = oldDatum.sysTime;
    }

    public void onSensorChanged(SensorEvent event) {

        int type = event.sensor.getType();
        final long now = System.currentTimeMillis();

        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
                accelerometerBuffer[accelerometerBufferHead].x = event.values[0];
                accelerometerBuffer[accelerometerBufferHead].y = event.values[1];
                accelerometerBuffer[accelerometerBufferHead].z = event.values[2];
                accelerometerBuffer[accelerometerBufferHead].t = event.timestamp;
                accelerometerBuffer[accelerometerBufferHead].sysTime = now;
                calibrate(accelerometerBuffer[accelerometerBufferHead], calibratedAccelerometerBuffer[accelerometerBufferHead]);
                accelerometerBufferHead = (accelerometerBufferHead + 1) % accelerometerBufferSize;
                ++accelerometerPoints;
                break;
            case Sensor.TYPE_GYROSCOPE:
                gyroscopeBuffer[gyroscopeBufferHead].x = event.values[0];
                gyroscopeBuffer[gyroscopeBufferHead].y = event.values[1];
                gyroscopeBuffer[gyroscopeBufferHead].z = event.values[2];
                gyroscopeBuffer[gyroscopeBufferHead].t = event.timestamp;
                gyroscopeBuffer[gyroscopeBufferHead].sysTime = now;
                calibrate(gyroscopeBuffer[gyroscopeBufferHead], calibratedGyroscopeBuffer[gyroscopeBufferHead]);
                gyroscopeBufferHead = (gyroscopeBufferHead + 1) % gyroscopeBufferSize;
                ++gyroscopePoints;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magnetometerBuffer[magnetometerBufferHead].x = event.values[0];
                magnetometerBuffer[magnetometerBufferHead].y = event.values[1];
                magnetometerBuffer[magnetometerBufferHead].z = event.values[2];
                magnetometerBuffer[magnetometerBufferHead].t = event.timestamp;
                magnetometerBuffer[magnetometerBufferHead].sysTime = now;
                magnetometerBufferHead = (magnetometerBufferHead + 1) % magnetometerBufferSize;
                ++magnetometerPoints;
                break;
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                gameRotationVectorBuffer[gameRotationVectorBufferHead].x = x;
                gameRotationVectorBuffer[gameRotationVectorBufferHead].y = y;
                gameRotationVectorBuffer[gameRotationVectorBufferHead].z = z;
                gameRotationVectorBuffer[gameRotationVectorBufferHead].t = event.timestamp;
                gameRotationVectorBuffer[gameRotationVectorBufferHead].sysTime = now;
                calibrate(gameRotationVectorBuffer[gameRotationVectorBufferHead],
                    calibratedGameRotationVectorBuffer[gameRotationVectorBufferHead]);
                gameRotationVectorBufferHead = (gameRotationVectorBufferHead + 1) % gameRotationVectorBufferSize;
                ++gameRotationVectorPoints;

                // double sinHalfTheta = sqrt(x * x + y * y + z * z);
                // double cosHalfTheta = event.values[3];
                quaternion[0] = x;
                quaternion[1] = y;
                quaternion[2] = z;
                quaternion[3] = event.values[3];
                SensorManager.getRotationMatrixFromVector(rotationMatrix, quaternion); // calculate rotation matrix from rotation vector first
                SensorManager.getOrientation(rotationMatrix, eulerAngles); // calculate Euler angles now
                gameRotationAttitudeBuffer[gameRotationAttitudeBufferHead].yaw = eulerAngles[0]; // yaw
                gameRotationAttitudeBuffer[gameRotationAttitudeBufferHead].pitch = eulerAngles[1]; // pitch
                gameRotationAttitudeBuffer[gameRotationAttitudeBufferHead].roll = eulerAngles[2]; // roll
                gameRotationAttitudeBuffer[gameRotationAttitudeBufferHead].t = event.timestamp;
                gameRotationAttitudeBuffer[gameRotationAttitudeBufferHead].sysTime = now;

                /* now process calibrated quantities. rotate quaternion */
                quaternion[0] = calibrationMatrix[0][0] * x + calibrationMatrix[0][1] * y + calibrationMatrix[0][2] * z;
                quaternion[1] = calibrationMatrix[1][0] * x + calibrationMatrix[1][1] * y + calibrationMatrix[1][2] * z;
                quaternion[2] = calibrationMatrix[2][0] * x + calibrationMatrix[2][1] * y + calibrationMatrix[2][2] * z;

                SensorManager.getRotationMatrixFromVector(rotationMatrix, quaternion); // calculate rotation matrix from rotation vector first
                SensorManager.getOrientation(rotationMatrix, eulerAngles); // calculate Euler angles now
                calibratedGameRotationAttitudeBuffer[gameRotationAttitudeBufferHead].yaw = eulerAngles[0]; // yaw
                calibratedGameRotationAttitudeBuffer[gameRotationAttitudeBufferHead].pitch = eulerAngles[1]; // pitch
                calibratedGameRotationAttitudeBuffer[gameRotationAttitudeBufferHead].roll = eulerAngles[2]; // roll
                calibratedGameRotationAttitudeBuffer[gameRotationAttitudeBufferHead].t = event.timestamp;
                calibratedGameRotationAttitudeBuffer[gameRotationAttitudeBufferHead].sysTime = now;

                gameRotationAttitudeBufferHead = (gameRotationAttitudeBufferHead + 1) % gameRotationAttitudeBufferSize;
                ++gameRotationAttitudePoints;
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                linearAccelerationBuffer[linearAccelerationBufferHead].x = event.values[0];
                linearAccelerationBuffer[linearAccelerationBufferHead].y = event.values[1];
                linearAccelerationBuffer[linearAccelerationBufferHead].z = event.values[2];
                linearAccelerationBuffer[linearAccelerationBufferHead].t = event.timestamp;
                linearAccelerationBuffer[linearAccelerationBufferHead].sysTime = now;
                linearAccelerationBufferHead = (linearAccelerationBufferHead + 1) % linearAccelerationBufferSize;
                ++linearAccelerationPoints;
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void destroy() {
        if (thread != null) {
            thread.quitSafely();
            try {
                thread.join();
            } catch (InterruptedException ex) {
                Log.e(TAG, "error shutting down SensorModule thread", ex);
            }
            thread = null;
        }
    }
}
