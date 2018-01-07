package com.nauto;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.widget.TextView;

import com.nauto.modules.server.ISimpleServerListener;
import com.nauto.modules.server.SimpleServer;
import com.nauto.modules.uploader.DataUploader;

import java.io.File;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jsvirzi on 1/29/17.
 */

public class Upload extends Activity implements ISimpleServerListener {
    private static final String TAG = "Upload";
    private TextView textIncoming;
    SimpleServer simpleServer;
    int port = 8080;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.server_test);
        textIncoming = (TextView) findViewById(R.id.textIncoming);
        simpleServer = new SimpleServer(port);
        simpleServer.addListener(this);
    }

    public void displayText(final String text) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                textIncoming.setText(text);
            }
        };
        runOnUiThread(runnable);
    }

    public List<File> listDataFiles(final String path) {
        final File directory = new File(path);
        ArrayList<File> files = new ArrayList<>();
        File[] filesInPath = directory.listFiles();
        /*
         * loop through files in each subdirectory, recursing into subdirectories
         */
        for (File file : filesInPath) {
            if (file.isDirectory()) {
                List<File> newList = listDataFiles(file.toString());
                if (newList.size() != 0) { // no point in adding 0 files
                    files.addAll(newList);
                }
            } else {
                files.add(file);
            }
        }
        return files;
    }

    public boolean processSimpleServerRequest(final Socket socket, InetAddress inetAddress, String page, List<Pair<String, String>> keyValuePairs, boolean sentHttpReply) {

        if(page == null) {
            return sentHttpReply;
        }

        if (page.equals("/listFiles")) {
            List<File> files = listDataFiles(Anthea.getInstance().outputDataDirectory);
            String listing = null; // TODO needed? = new String(Anthea.getInstance().outputDataDirectory);
            for (File file : files) {
                if (listing == null) {
                    listing = new String(file.toString());
                } else {
                    listing += "," + file.toString();
                }
                String msg = String.format("data file = [%s]", file.toString());
                Log.d(TAG, msg);
            }
            if (sentHttpReply == false) {
                SimpleServer.sendMinimalHttpReply(socket, listing.getBytes());
                sentHttpReply = true;
            }
        } else if(page.equals("/upload")) {
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
                DataUploader dataUploader = new DataUploader(getApplicationContext(), inetAddress, port);
                dataUploader.uploadFile(filename, deleteFile, runnable);
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
        displayText(data);
        return sentHttpReply;
    }

    public boolean processRawPacket(Socket socket, String data, boolean sentHttpReply) {
        return sentHttpReply;
    }
}
