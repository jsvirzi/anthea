package com.mam.lambo.modules.calibration;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.mam.lambo.modules.sensor.SensorModule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.abs;
import static java.lang.Math.acos;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

/**
 * Created by jsvirzi on 3/13/17.
 */

public class OrientationCalibration {

    private static final String TAG = "OrientationCalibration";
    SensorModule sensorModule;
    private HandlerThread thread;
    private Handler handler;
    private float[][] rotationMatrix;
    List<IDeviceOrientationCalibrationListener> listeners;
    ScheduledExecutorService scheduledExecutorService;
    double onGoingCalibrationAcc0;
    double[] onGoingCalibrationAcc1;
    double[] onGoingCalibrationAcc2;
    private float significance2Threshold;

    public OrientationCalibration(SensorModule sensorModule) {
        this.sensorModule = sensorModule;
        thread = new HandlerThread(TAG);
        thread.start();
        handler = new Handler(thread.getLooper());
        rotationMatrix = new float[][]{{1.0f, 0.0f, 0.0f},{0.0f,1.0f,0.0f},{0.0f,0.0f,1.0f}};
        listeners = new ArrayList<>();
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        onGoingCalibrationAcc0 = 0.0;
        onGoingCalibrationAcc1 = new double[]{0.0, 0.0, 0.0};
        onGoingCalibrationAcc2 = new double[]{0.0, 0.0, 0.0};
        significance2Threshold = 9.0f; /* 3 sigma */
    }

    private Runnable onGoingCalibrationRunnable = new Runnable() {
        @Override
        public void run() {
            int pos = (sensorModule.accelerometerBufferHead - 1 + sensorModule.accelerometerBufferSize) % sensorModule.accelerometerBufferSize;
            SensorModule.Space datum = sensorModule.accelerometerBuffer[pos];
            float x = datum.x;
            float y = datum.y;
            float z = datum.z;
            onGoingCalibrationAcc0 += 1.0;
            onGoingCalibrationAcc1[0] += x;
            onGoingCalibrationAcc1[1] += y;
            onGoingCalibrationAcc1[2] += z;
            onGoingCalibrationAcc2[0] += x * x;
            onGoingCalibrationAcc2[1] += y * y;
            onGoingCalibrationAcc2[2] += z * z;
            double[] mean = new double[3];
            double[] sigma = new double[3];
            mean[0] = onGoingCalibrationAcc1[0] / onGoingCalibrationAcc0;
            mean[1] = onGoingCalibrationAcc1[1] / onGoingCalibrationAcc0;
            mean[2] = onGoingCalibrationAcc1[2] / onGoingCalibrationAcc0;
            sigma[0] = sqrt(abs(onGoingCalibrationAcc2[0] / onGoingCalibrationAcc0 - mean[0] * mean[0]));
            sigma[1] = sqrt(abs(onGoingCalibrationAcc2[1] / onGoingCalibrationAcc0 - mean[1] * mean[1]));
            sigma[2] = sqrt(abs(onGoingCalibrationAcc2[2] / onGoingCalibrationAcc0 - mean[2] * mean[2]));
            double[] gHat = new double[3];
            double gravityMagnitude = sqrt(mean[0] * mean[0] + mean[1] * mean[1] + mean[2] * mean[2]);
            /* gHat is unit vector */
            gHat[0] = (float) (mean[0] / gravityMagnitude);
            gHat[1] = (float) (mean[1] / gravityMagnitude);
            gHat[2] = (float) (mean[2] / gravityMagnitude);
            /* g0Hat is old unit vector. by construction points downward */
            double[] g0Hat = new double[] {0, 0, -1};
            /* calculate significance of deviation */
            double ax = (g0Hat[0] - gHat[0]);
            double ay = (g0Hat[1] - gHat[1]);
            double az = (g0Hat[2] - gHat[2]);
            double b = (sigma[0] * sigma[0] + sigma[1] * sigma[1] + sigma[2] * sigma[2]) / gravityMagnitude;
            double significance2 = (ax * ax + ay * ay + az * az) / b;
            if (significance2 > significance2Threshold) {
                Log.d(TAG, "significance threshold exceeded");
            }

        }
    };

    public void addListener(IDeviceOrientationCalibrationListener listener) {
        listeners.add(listener);
    }

    public void oneShot(long timeToCalibrate) {
        oneShot(timeToCalibrate, false);
    }

    public void onGoingCalibration(long samplePeriod, long reportPeriod) {
        scheduledExecutorService.scheduleAtFixedRate(onGoingCalibrationRunnable, samplePeriod, samplePeriod, TimeUnit.MILLISECONDS);
    }

    public void oneShot(long timeToCalibrate, final boolean verify) {
        final long stopTime = System.currentTimeMillis() + timeToCalibrate;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                int tail = sensorModule.accelerometerBufferHead;
                double[] gravityAcc1 = new double[3];
                double[] gravityAcc2 = new double[3];
                gravityAcc1[0] = 0.0;
                gravityAcc1[1] = 0.0;
                gravityAcc1[2] = 0.0;
                gravityAcc2[0] = 0.0;
                gravityAcc2[1] = 0.0;
                gravityAcc2[2] = 0.0;
                int accelerometerReadingsLength = 0;
                while (System.currentTimeMillis() < stopTime) {
                    while (tail != sensorModule.accelerometerBufferHead) {
                        SensorModule.Space datum =
                            verify ? sensorModule.calibratedAccelerometerBuffer[tail] : sensorModule.accelerometerBuffer[tail];
                        tail = (tail + 1) % sensorModule.accelerometerBufferSize;
                        float x = datum.x;
                        float y = datum.y;
                        float z = datum.z;
                        gravityAcc1[0] += x;
                        gravityAcc2[0] += x * x;
                        gravityAcc1[1] += y;
                        gravityAcc2[1] += y * y;
                        gravityAcc1[2] += z;
                        gravityAcc2[2] += z * z;
                        ++accelerometerReadingsLength;
                    }
                }
                double[] mean = new double[3];
                double[] sigma = new double[3];
                mean[0] = gravityAcc1[0] / accelerometerReadingsLength;
                mean[1] = gravityAcc1[1] / accelerometerReadingsLength;
                mean[2] = gravityAcc1[2] / accelerometerReadingsLength;
                sigma[0] = sqrt(abs(gravityAcc2[0] / accelerometerReadingsLength - mean[0] * mean[0]));
                sigma[1] = sqrt(abs(gravityAcc2[1] / accelerometerReadingsLength - mean[1] * mean[1]));
                sigma[2] = sqrt(abs(gravityAcc2[2] / accelerometerReadingsLength - mean[2] * mean[2]));
                double[] gHat = new double[3];
                double gravityMagnitude = sqrt(mean[0] * mean[0] + mean[1] * mean[1] + mean[2] * mean[2]);
                gHat[0] = (float) (mean[0] / gravityMagnitude);
                gHat[1] = (float) (mean[1] / gravityMagnitude);
                gHat[2] = (float) (mean[2] / gravityMagnitude);
                double[] g0Hat = new double[] {0, 0, -1};
                double cosTheta = cosAngle(gHat, g0Hat);
                double theta = acos(cosTheta); /* 0 to pi */
                double[] n = unitCrossProduct(gHat, g0Hat);
                calculateRotationMatrix(n, theta);
                for (IDeviceOrientationCalibrationListener listener : listeners) {
                    listener.onCalibrationChanged(rotationMatrix);
                }
            }
        };
        handler.post(runnable);
    }

    private double cosAngle(double[] a, double[] b) {
        double acc = 0.0;
        double aMag = 0.0;
        double bMag = 0.0;
        if(a.length != b.length) {
            return -99999.0;
        }
        for (int i = 0; i < a.length; i++) {
            acc += a[i] * b[i];
            aMag += a[i] * a[i];
            bMag += b[i] * b[i];
        }
        return acc / sqrt(aMag * bMag);
    }

    private double[] unitCrossProduct(double[] a, double[] b) {
        double[] n = new double[3];
        n[0] = a[1] * b[2] - a[2] * b[1];
        n[1] = a[2] * b[0] - a[0] * b[2];
        n[2] = a[0] * b[1] - a[1] * b[0];
        double mag = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        n[0] /= mag;
        n[1] /= mag;
        n[2] /= mag;
        return n;
    }

    private void calculateRotationMatrix(double[] n, double theta) {
        double c = cos(theta);
        double s = sin(theta);
        double a = (1.0 - c);
        double nx = n[0];
        double ny = n[1];
        double nz = n[2];
        rotationMatrix[0][0] = (float) (nx * nx * a + c);
        rotationMatrix[0][1] = (float) (nx * ny * a - nz * s);
        rotationMatrix[0][2] = (float) (nx * nz * a + ny * s);
        rotationMatrix[1][0] = (float) (ny * nx * a + nz * s);
        rotationMatrix[1][1] = (float) (ny * ny * a + c);
        rotationMatrix[1][2] = (float) (ny * nz * a - nx * s);
        rotationMatrix[2][0] = (float) (nz * nx * a - ny * s);
        rotationMatrix[2][1] = (float) (nz * ny * a + nx * s);
        rotationMatrix[2][2] = (float) (nz * nz * a + c);
    }

    public float[][] getRotationMatrix() {
        return rotationMatrix;
    }
}