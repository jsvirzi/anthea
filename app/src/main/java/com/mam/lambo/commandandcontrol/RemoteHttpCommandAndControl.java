package com.mam.lambo.commandandcontrol;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.util.Log;
import android.util.Pair;

import com.mam.lambo.BigScreen;
import com.mam.lambo.DogFood;
import com.mam.lambo.Anthea;
import com.nauto.modules.server.ISimpleServerListener;
import com.nauto.modules.server.SimpleServer;
import com.nauto.modules.uploader.DataUploader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;

/**
 * Created by jsvirzi on 3/10/17.
 */

public class RemoteHttpCommandAndControl implements ISimpleServerListener {

    private static final String TAG = "HttpCommandAndControl";
    byte[] indexHtmlResponse;
    byte[] launchHtmlResponse;

    public RemoteHttpCommandAndControl(String ipAddress) {
        indexHtmlResponse = readHtmlFile("index_html", ipAddress);
        launchHtmlResponse = readHtmlFile("launch_html", ipAddress);
    }

    private int findFirstIndex(byte[] haystack, byte[] needle) {
        int searchLength = haystack.length - needle.length;
        for(int pos=0;pos<searchLength;++pos) {
            boolean found = true;
            for(int i=0;i<needle.length;++i) {
                if(haystack[pos+i] != needle[i]) {
                    found = false;
                    break;
                }
            }
            if (found == true) {
                return pos;
            } else {
                continue;
            }
        }
        return -1;
    }

    private byte[] replace(byte[] haystack, byte[] dirtyNeedle, byte[] cleanNeedle) {
        int begin = findFirstIndex(haystack, dirtyNeedle);
        if (begin < 0) return null; /* nothing to do */
        int length = haystack.length + cleanNeedle.length - dirtyNeedle.length;
        byte[] result = new byte[length];
        int index = 0;
        int nCopy = begin;
        System.arraycopy(haystack, 0, result, 0, nCopy);
        index += nCopy;
        nCopy = cleanNeedle.length;
        System.arraycopy(cleanNeedle, 0, result, index, nCopy);
        index += nCopy;
        nCopy = length - index; /* what's left */
        System.arraycopy(haystack, begin + dirtyNeedle.length, result, index, nCopy);
        return result;
    }

    private byte[] readHtmlFile(String name, String ipAddress) {
        Resources resources = Anthea.getInstance().getResources();
        String packageName = Anthea.getInstance().getPackageName();

        int resourceId = resources.getIdentifier(name, "raw", packageName);
        InputStream inputStream = resources.openRawResource(resourceId);
        int size = 0;
        try {
            size = inputStream.available();
        } catch (IOException ex) {
            String msg = String.format("error reading length of resource %d", resourceId);
            Log.e(TAG, msg, ex);
        }
        byte[] response = new byte[size];
        try {
            inputStream.read(response, 0, size);
            inputStream.close();
        } catch (IOException ex) {
            String msg = String.format("error reading resource %d", resourceId);
            Log.e(TAG, msg, ex);
        }
        String replaceString = "nautobahnipaddress";
        int length = replaceString.length();
        byte[] oldArray = replaceString.getBytes();
        length = oldArray.length;
        byte[] newArray = ipAddress.getBytes();
        length = newArray.length;
        byte[] newResponse = replace(response, oldArray, newArray);
        while (newResponse != null) {
            response = newResponse;
            newResponse = replace(response, oldArray, newArray);
        }
        return response;
    }

    public boolean processSimpleServerRequest(final Socket socket, InetAddress inetAddress, String page, List<Pair<String, String>> keyValuePairs, boolean sentHttpReply) {

        if(page == null) {
            return sentHttpReply;
        }

        if (page.equals("/index.html")) {
            if (sentHttpReply == false) {
                SimpleServer.sendMinimalHttpReply(socket, indexHtmlResponse);
                sentHttpReply = true;
            }
        } else if(page.equals("/launch.html")) {
            /* send reply now */
            if (sentHttpReply == false) {
                SimpleServer.sendMinimalHttpReply(socket);
                sentHttpReply = true;
            }
            /* then do action */
            for (Pair<String, String> keyValuePair : keyValuePairs) {
                String key = keyValuePair.first;
                String value = keyValuePair.second;
                Anthea anthea = Anthea.getInstance();
                Context context = anthea.getApplicationContext();
                if (key.equals("class")) {
                    if (value.equals("dogfood")) {
                        Intent intent = new Intent(BigScreen.bigScreen, DogFood.class);
                        BigScreen.bigScreen.startActivity(intent);
                    }
                }
            }
        } else if(page.equals("/calibrate.html")) {
        } else if(page.equals("/set")) {
        } else if(page.equals("/get")) {
            int port = 0;
            String filename = null;
            boolean deleteFile = false;
            for (Pair<String, String> keyValuePair : keyValuePairs) {
                String key = keyValuePair.first;
                String value = keyValuePair.second;
                if (key.equals("port")) {
                    port = Integer.parseInt(value);
                } else if (key.equals("filename")) {
                    filename = value;
                } else if (key.equals("delete")) {
                    deleteFile = value.equals("true");
                }
            }
//            if (sentHttpReply == false) {
//                SimpleServer.sendMinimalHttpReply(socket);
//                sentHttpReply = true;
//            }
            if ((port != 0) && (filename != null)) {
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        SimpleServer.sendMinimalHttpReply(socket);
                    }
                };
//                DataUploader dataUploader = new DataUploader(getApplicationContext(), inetAddress, port);
//                dataUploader.uploadFile(filename, deleteFile, runnable);
            }
        } else {
            if (sentHttpReply == false) {
                SimpleServer.sendMinimalHttpReply(socket);
                sentHttpReply = true;
            }
        }
        return sentHttpReply;
    }

    public boolean processRawPacketData(Socket socket, String data, boolean sentHttpReply) {
        return sentHttpReply;
    }

    public boolean processRawPacketHeader(Socket socket, String data, boolean sentHttpReply) {
        return sentHttpReply;
    }

    public boolean processRawPacket(Socket socket, String data, boolean sentHttpReply) {
        return sentHttpReply;
    }
}
