package com.nauto.modules.utils;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.nauto.modules.camera.*;

import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.nauto.modules.utils.Utils.getBufferedWriter;

/**
 * Created by jsvirzi on 2/4/17.
 */

public class DataLogger {
    private static final String TAG = "DataLogger";
    private File file;
    private HandlerThread thread;
    private Handler handler;
    private static DataLogger instance;

    public static DataLogger getInstance() {
        return instance;
    }

    public static DataLogger getInstance(String filename) {
        if (instance == null) {
            instance = new DataLogger(filename);
        }
        return instance;
    }

    private DataLogger(String filename) {
        file = new File(filename);
        thread = new HandlerThread(TAG);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    /* opens file, writes/appends line, closes file */
    public void record(final String line) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                BufferedWriter bufferedWriter = getBufferedWriter(file, line.length(), true);
                if (bufferedWriter == null) {
                    Log.e(TAG, "unable to record sensor data");
                    return;
                } else {
                    String msg = String.format("opened file [%s] for sensor data recording", file.getPath());
                    Log.d(TAG, msg);
                }
                Utils.writeLine(bufferedWriter, line);
                Utils.closeStream(bufferedWriter);
            }
        };
        handler.post(runnable);
    }

    /* opens file, writes/appends list of lines, closes file */
    public void record(final List<String> lines) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                int length = 0;
                for (String line : lines) {
                    length += line.length();
                }
                length += lines.size() * 16; /* just some overall headroom */
                BufferedWriter bufferedWriter = getBufferedWriter(file, length, true);
                if (bufferedWriter == null) {
                    Log.e(TAG, "unable to record sensor data");
                    return;
                } else {
                    String msg = String.format("opened file [%s] for sensor data recording", file.getPath());
                    Log.d(TAG, msg);
                }
                for (String line : lines) {
                    Utils.writeLine(bufferedWriter, line);
                }
                Utils.closeStream(bufferedWriter);
            }
        };
        handler.post(runnable);
    }

    public void destroy() {
        if (instance == null) {
            return;
        }
        if (thread != null) {
            com.nauto.modules.camera.Utils.goodbyeThread(thread);
            thread = null;
            handler = null;
        }
        instance = null;
    }
}
