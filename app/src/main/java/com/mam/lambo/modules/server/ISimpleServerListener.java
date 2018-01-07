package com.mam.lambo.modules.server;

import android.util.Pair;

import java.net.InetAddress;
import java.net.Socket;
import java.util.List;

/**
 * Created by jsvirzi on 1/29/17.
 */

public interface ISimpleServerListener {
    // public void processSimpleServerRequest(String page, String[] keys, String[] values);
    boolean processSimpleServerRequest(Socket socket, InetAddress inetAddress, String page, List<Pair<String, String>> keyValuePairs, boolean sentHttpReply);
    boolean processRawPacket(Socket socket, String data, boolean sentHttpReply);
    boolean processRawPacketHeader(Socket socket, String data, boolean sentHttpReply);
    boolean processRawPacketData(Socket socket, String data, boolean sentHttpReply);
}
