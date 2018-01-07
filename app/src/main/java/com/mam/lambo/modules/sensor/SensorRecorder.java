package com.mam.lambo.modules.sensor;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.mam.lambo.modules.utils.DataLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static com.mam.lambo.modules.utils.Utils.getBufferedWriter;

/**
 * Created by jsvirzi on 6/12/16.
 */
public class SensorRecorder {

    public static String TAG = "SensorRecorder";

    private int deltaRecordingTime = 0;
    private int accelerometerBufferTail = 0;
    private int gyroscopeBufferTail = 0;
    private int magnetometerBufferTail = 0;
    private int gpsBufferTail = 0;
    private int linearAccelerationBufferTail = 0;
    private int gameRotationVectorBufferTail = 0;
    private int gameRotationAttitudeBufferTail = 0;
    private SensorModule sensorModule;
    private HandlerThread thread;
    private Handler handler;
    private Timer timer = null;
    private DataLogger dataLogger;

    public void destroy() {
        timer.cancel();
        timer.purge();
        timer = null;
        if (thread != null) {
            thread.quitSafely();
            try {
                thread.join();
            } catch (InterruptedException ex) {
                String msg = String.format("exception closing thread %s", thread.getName());
                Log.e(TAG, msg, ex);
            }
            thread = null;
        }
    }

    public SensorRecorder(SensorModule sensorModule, int period, DataLogger dataLogger) {
        this.sensorModule = sensorModule;
        this.dataLogger = dataLogger;

        if (period > 0) {
            deltaRecordingTime = period;
            timer = new Timer();
            SensorRecorderTask sensorRecorderTask = new SensorRecorderTask();
            timer.schedule(sensorRecorderTask, deltaRecordingTime, deltaRecordingTime);
        }
        thread = new HandlerThread(TAG);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    private class SensorRecorderTask extends TimerTask {
        @Override
        public void run() {
            handler.post(recordRunnable); /* put on known thread */
        }
    }

    Runnable recordRunnable = new Runnable() {

        @Override
        public void run() {

            final long now = System.currentTimeMillis();

            int length;
            String line;
            boolean dataPresent = false;
            int numberOfLines = 0;

            int gpsBufferSize = sensorModule.gpsBufferSize;
            length = sensorModule.gpsBufferHead - gpsBufferTail + gpsBufferSize;
            final int gpsPoints = length % gpsBufferSize;
            numberOfLines += gpsPoints;
            dataPresent = dataPresent || (gpsPoints > 0);

            int accelerometerBufferSize = sensorModule.accelerometerBufferSize;
            length = sensorModule.accelerometerBufferHead - accelerometerBufferTail + accelerometerBufferSize;
            final int accelerometerPoints = length % accelerometerBufferSize;
            numberOfLines += accelerometerPoints;
            dataPresent = dataPresent || (accelerometerPoints > 0);

            int gyroscopeBufferSize = sensorModule.gyroscopeBufferSize;
            length = sensorModule.gyroscopeBufferHead - gyroscopeBufferTail + gyroscopeBufferSize;
            final int gyroscopePoints = length % gyroscopeBufferSize;
            numberOfLines += gyroscopePoints;
            dataPresent = dataPresent || (gyroscopePoints > 0);

            int magnetometerBufferSize = sensorModule.magnetometerBufferSize;
            length = sensorModule.magnetometerBufferHead - magnetometerBufferTail + magnetometerBufferSize;
            final int magnetometerPoints = length % magnetometerBufferSize;
            numberOfLines += magnetometerPoints;
            dataPresent = dataPresent || (magnetometerPoints > 0);

            int linearAccelerometerBufferSize = sensorModule.linearAccelerationBufferSize;
            length = sensorModule.linearAccelerationBufferHead - linearAccelerationBufferTail + linearAccelerometerBufferSize;
            final int linearAccelerationPoints = length % linearAccelerometerBufferSize;
            numberOfLines += linearAccelerationPoints;
            dataPresent = dataPresent || (linearAccelerationPoints > 0);

            int gameRotationVectorBufferSize = sensorModule.gameRotationVectorBufferSize;
            length = sensorModule.gameRotationVectorBufferHead - gameRotationVectorBufferTail + gameRotationVectorBufferSize;
            final int gameRotationVectorPoints = length % gameRotationVectorBufferSize;
            numberOfLines += gameRotationVectorPoints;
            dataPresent = dataPresent || (gameRotationVectorPoints > 0);

            int gameRotationAttitudeBufferSize = sensorModule.gameRotationAttitudeBufferSize;
            length = sensorModule.gameRotationAttitudeBufferHead - gameRotationAttitudeBufferTail + gameRotationAttitudeBufferSize;
            final int gameRotationAttitudePoints = length % gameRotationAttitudeBufferSize;
            numberOfLines += gameRotationAttitudePoints;
            dataPresent = dataPresent || (gameRotationAttitudePoints > 0);

            if (dataPresent == false) {
                return;
            }

            List<String> lines = new ArrayList<>(numberOfLines);

            // Gps
            if (gpsPoints > 0) {
                String msg = String.format("recording %d gps points", gpsPoints);
                Log.d(TAG, msg);
                for (int i = 0; i < gpsPoints; i++) {
                    SensorModule.Gps gps = sensorModule.gpsBuffer[gpsBufferTail];
                    gpsBufferTail = (gpsBufferTail + 1) % sensorModule.gpsBufferSize;
                    line = String.format("gps,%f,%f,%f,%f,%f,%f,%f,%f,%f,%d,%d,%d\n",
                        gps.latitude, gps.longitude, gps.altitude,
                        gps.hdop, gps.vdop, gps.pdop, gps.accuracy, gps.speed, gps.bearing,
                        gps.numberOfSatellites, gps.sensortimestamp, gps.eventTimestamp);
                    lines.add(line);
                }
            }

            // Game Rotation Vector
            if (gameRotationVectorPoints > 0) {
                String msg = String.format("recording %d game rotation vector points", gameRotationVectorPoints);
                Log.d(TAG, msg);
                for (int i = 0; i < gameRotationVectorPoints; ++i) {
                    SensorModule.Space grv = sensorModule.gameRotationVectorBuffer[gameRotationVectorBufferTail];
                    gameRotationVectorBufferTail = (gameRotationVectorBufferTail + 1) % gameRotationVectorBufferSize;
                    line = String.format("grv,%f,%f,%f,%d,%d\n", grv.x, grv.y, grv.z, grv.t, grv.sysTime);
                    lines.add(line);
                }
            }

            // Game Rotation Attitude
            if (gameRotationAttitudePoints > 0) {
                String msg = String.format("recording %d game rotation attitude points", gameRotationAttitudePoints);
                Log.d(TAG, msg);
                for (int i = 0; i < gameRotationAttitudePoints; ++i) {
                    SensorModule.Attitude gra = sensorModule.gameRotationAttitudeBuffer[gameRotationAttitudeBufferTail];
                    gameRotationAttitudeBufferTail = (gameRotationAttitudeBufferTail + 1) % gameRotationAttitudeBufferSize;
                    line = String.format("gra,%f,%f,%f,%d,%d\n", gra.yaw, gra.pitch, gra.roll, gra.t, gra.sysTime);
                    lines.add(line);
                }
            }

            // LinearAcceleration
            if (linearAccelerationPoints > 0) {
                String msg = String.format("recording %d linear acceleration points", linearAccelerationPoints);
                Log.d(TAG, msg);
                for (int i = 0; i < linearAccelerationPoints; ++i) {
                    SensorModule.Space acc = sensorModule.linearAccelerationBuffer[linearAccelerationBufferTail];
                    linearAccelerationBufferTail = (linearAccelerationBufferTail + 1) % linearAccelerometerBufferSize;
                    line = String.format("lin,%f,%f,%f,%d,%d\n", acc.x, acc.y, acc.z, acc.t,acc.sysTime);
                    lines.add(line);
                }
            }

            // Accelerometer
            if (accelerometerPoints > 0) {
                String msg = String.format("recording %d accelerometer points", accelerometerPoints);
                Log.d(TAG, msg);
                for (int i = 0; i < accelerometerPoints; ++i) {
                    SensorModule.Space acc = sensorModule.accelerometerBuffer[accelerometerBufferTail];
                    accelerometerBufferTail = (accelerometerBufferTail + 1) % accelerometerBufferSize;
                    line = String.format("acc,%f,%f,%f,%d,%d\n", acc.x, acc.y, acc.z, acc.t, acc.sysTime);
                    lines.add(line);
                }
            }

            // Gyroscope
            if (gyroscopePoints > 0) {
                String msg = String.format("recording %d gyroscope points", gyroscopePoints);
                Log.d(TAG, msg);
                for (int i = 0; i < gyroscopePoints; ++i) {
                    SensorModule.Space gyr = sensorModule.gyroscopeBuffer[gyroscopeBufferTail];
                    gyroscopeBufferTail = (gyroscopeBufferTail + 1) % gyroscopeBufferSize;
                    line = String.format("gyr,%f,%f,%f,%d,%d\n", gyr.x, gyr.y, gyr.z, gyr.t, gyr.sysTime);
                    lines.add(line);
                }
            }

            // Magnetometer
            if (magnetometerPoints > 0) {
                String msg = String.format("recording %d magnetometer points", magnetometerPoints);
                Log.d(TAG, msg);
                for (int i = 0; i < magnetometerPoints; ++i) {
                    SensorModule.Space mag = sensorModule.magnetometerBuffer[magnetometerBufferTail];
                    magnetometerBufferTail = (magnetometerBufferTail + 1) % magnetometerBufferSize;
                    line = String.format("mag,%f,%f,%f,%d,%d\n", mag.x, mag.y, mag.z, mag.t, mag.sysTime);
                    lines.add(line);
                }
            }

            line = String.format("time,%d\n", now);
            lines.add(line);

            dataLogger.record(lines);
        }
    };
}
