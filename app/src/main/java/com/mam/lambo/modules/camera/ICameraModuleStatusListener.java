package com.mam.lambo.modules.camera;

/**
 * Created by jsvirzi on 1/19/17.
 */

public interface ICameraModuleStatusListener {
    public void sendDistress(String msg, int level);
    public void sendStatus(String msg, int level);
}
