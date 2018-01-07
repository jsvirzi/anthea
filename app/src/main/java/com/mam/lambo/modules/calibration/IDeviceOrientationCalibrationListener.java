package com.mam.lambo.modules.calibration;

/**
 * Created by jsvirzi on 3/13/17.
 */

public interface IDeviceOrientationCalibrationListener {
    void onCalibrationChanged(float[][] calibrationMatrix);
}
