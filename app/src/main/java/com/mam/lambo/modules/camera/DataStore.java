package com.nauto.modules.camera;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by jsvirzi on 6/3/16.
 */

public class DataStore {

    public static final String TAG = "DataStore";

    /* the camera module handles all SD access on this thread */
    private Handler handler;
    private HandlerThread handlerThread;

    private int dataBufferSize;
    private int dataBufferHead;
    private int dataBufferTail;
    ByteBuffer[] data;
    private ByteBuffer currentData;
    private long numberOfBytesWritten;
    private long startTimestamp;
    private long finalTimestamp;
    boolean sync = false;

    DataStore(int index, int numberOfBuffers, int payloadBufferSize) {
        String name;
        if (index < 0) {
            name = String.format(Common.LOCALE, "DataStore");
        } else {
            name = String.format(Common.LOCALE, "DataStore%d", index);
        }
        handlerThread = new HandlerThread(name, Common.MEDIUM_THREAD_PRIORITY);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        dataBufferSize = numberOfBuffers;

        data = new ByteBuffer[dataBufferSize];
        for (int i = 0; i < dataBufferSize; i++) {
            data[i] = ByteBuffer.allocateDirect(payloadBufferSize);
        }
        dataBufferHead = 0;
        dataBufferTail = 0;

        /* kickstart the process */
        currentData = data[dataBufferHead];
        currentData.rewind();
        dataBufferHead = (dataBufferHead + 1) % dataBufferSize;
    }

    public void write(ByteBuffer[] byteBuffers, final long time) {
        write(byteBuffers, time, false, null);
    }

    public void write(ByteBuffer[] byteBuffers, final long time, final boolean openNewFile, final String filename) {
        boolean first = true;
        for (ByteBuffer byteBuffer : byteBuffers) {
            if (first) {
                write(byteBuffer, time, openNewFile, filename);
                first = false;
            } else {
                write(byteBuffer, time);
            }
        }
    }

    public void write(ByteBuffer src, final long time) {
        write(src, time, false, null);
    }

    public void write(ByteBuffer src, final long time, final boolean openNewFile, final String filename) {
        if (openNewFile) {
            startTimestamp = time;
            final int mark = dataBufferHead;

            /* we will be writing out old data, so better switch to a new buffer for new data */
            currentData = data[dataBufferHead];
            currentData.rewind();
            dataBufferHead = (dataBufferHead + 1) % dataBufferSize;

            /* write to SD will occur on another thread */
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    FileOutputStream fileOutputStream = null;
                    if (dataBufferTail == mark) {
                        return; /* nothing to do */
                    }
                    if (filename == null) {
                        Log.d(TAG, "attempt to commit data to (null) file");
                        return;
                    }
                    try {
                        fileOutputStream = new FileOutputStream(filename);
                    } catch (FileNotFoundException ex) {
                        String msg = String.format(Common.LOCALE, "FileNotFoundException caught opening file [%s]", filename);
                        Log.e(TAG, msg, ex);
                    }
                    if (fileOutputStream == null) {
                        String msg = String.format(Common.LOCALE, "error opening file [%s]", filename);
                        Log.d(TAG, msg);
                        return;
                    }
                    FileChannel fileChannel = fileOutputStream.getChannel();
                    int numberOfBytesToWrite = 0;
                    numberOfBytesWritten = 0;
                    try {
                        int pos = dataBufferTail;
                        while (pos != mark) {
                            data[pos].flip();
                            int buffSize = data[pos].limit();
                            numberOfBytesToWrite += buffSize;
                            pos = (pos + 1) % dataBufferSize;
                        }
                        if (dataBufferTail < mark) {
                            numberOfBytesWritten = fileChannel.write(data, dataBufferTail, mark - dataBufferTail);
                        } else { /* dataBufferTail == mark handled above */
                            numberOfBytesWritten = fileChannel.write(data, dataBufferTail, dataBufferSize - dataBufferTail);
                            numberOfBytesWritten += fileChannel.write(data, 0, mark);
                        }
                    } catch (IOException ex) {
                        String msg = String.format(Common.LOCALE, "IOException caught writing to file [%s]", filename);
                        Log.e(TAG, msg, ex);
                    }
                    if (numberOfBytesToWrite == numberOfBytesWritten) {
                        String msg = String.format(Common.LOCALE, "wrote %d bytes to file [%s] tail=%d/mark=%d",
                            numberOfBytesWritten, filename, dataBufferTail, mark);
                        Log.d(TAG, msg);
                    } else {
                        String msg = String.format(Common.LOCALE, "error writing to file [%s]. %d bytes written/expected %d",
                            filename, numberOfBytesWritten, numberOfBytesToWrite);
                        Log.d(TAG, msg);
                    }
                    try {
                        fileOutputStream.flush();
                        fileChannel.close();
                        fileOutputStream.close(); // TODO necessary?
                    } catch (IOException ex) {
                        String msg = String.format(Common.LOCALE, "error closing file channel [%s]", filename);
                        Log.e(TAG, msg, ex);
                    } finally {
                        dataBufferTail = mark;
                    }
                }
            };
            Utils.post(handler, runnable, sync);
        }

        finalTimestamp = time; /* continually update the last time buffer was written */
        int numberOfBytesRemaining = src.limit() - src.position(); /* how much data to write out */
        int srcOffset = src.position(); /* source always start at 0 */
        ByteBuffer byteBuffer = src.duplicate();
        while (numberOfBytesRemaining > 0) {
            int dstSize = currentData.remaining(); /* how much space is available in current buffer */
            if (numberOfBytesRemaining <= dstSize) { /* does the source buffer fit entirely into output buffer */
                byteBuffer.position(srcOffset);
                byteBuffer.limit(srcOffset + numberOfBytesRemaining);
                currentData.put(byteBuffer);
                numberOfBytesRemaining = 0; /* we are done */
            } else {
                byteBuffer.position(srcOffset);
                byteBuffer.limit(srcOffset + dstSize);
                currentData.put(byteBuffer);
                srcOffset += dstSize; /* for next copy, offset by what we've already written */
                numberOfBytesRemaining -= dstSize; /* we just wrote some data */
                currentData = data[dataBufferHead]; /* get the next buffer in queue */
                currentData.rewind();
                dataBufferHead = (dataBufferHead + 1) % dataBufferSize;
            }
        }
    }

    public void destroy() {
        destroy(true);
    }

    void destroy(boolean sync) {
        synchronized (DataStore.this) {
            if (handlerThread != null) {
                Utils.goodbyeThread(handlerThread);
                handlerThread = null;
                handler = null;
            }
            if (data != null) {
                for (int i = 0; i < dataBufferSize; i++) {
                    data[i] = null;
                }
                data = null;
            }
        }
    }
}
