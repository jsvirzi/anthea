package com.mam.lambo.modules.uploader;

/**
 * Created by jsvirzi on 12/2/16.
 */

public class ConnectionParameters {
    String dstIpAddress;
    int port;

    public ConnectionParameters(String dstIpAddress, int port) {
        this.dstIpAddress = dstIpAddress;
        this.port = port;
    }
}
