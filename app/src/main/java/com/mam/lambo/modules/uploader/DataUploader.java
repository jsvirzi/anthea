package com.mam.lambo.modules.uploader;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.mam.lambo.modules.server.SimpleServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ThreadFactory;

/**
 * Created by jsvirzi on 12/2/16.
 */

/*
 * http://androidsrc.net/android-client-server-using-sockets-client-implementation/
 * http://androidsrc.net/android-client-server-using-sockets-server-implementation/
 * https://developer.android.com/reference/java/net/Socket.html
 */

public class DataUploader {

    private static final String TAG = "DataUploader";
    private HandlerThread thread;
    private Handler handler;
    private Socket socket;
    private ConnectionParameters connectionParameters;
    private byte[] dataBuffer;
    private Crc32 crc32 = Crc32.getInstance();
    private String deviceId;
    private Context context;

    public DataUploader(Context context, InetAddress dstIpAddress, int port) {
        this(context, dstIpAddress.getHostAddress(), port);
    }

    public DataUploader(InetAddress dstIpAddress, int port) {
        this(null, dstIpAddress.getHostAddress(), port);
    }

    public DataUploader(Context context, String dstIpAddress, int port) {
        connectionParameters = new ConnectionParameters(dstIpAddress, port);
        this.context = context;

        if (context != null) {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            deviceId = telephonyManager.getDeviceId();
        }

        if (deviceId == null) {
            deviceId = "mam.lambobahn";
        }

        thread = new HandlerThread("DataUploader");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public int stuffIntegerIntoByteArray(byte[] array, int theInteger, int offset) {
        for (int i = 0; i < 8; i++) {
            array[offset + i] = (byte)(theInteger & 0xff);
            theInteger >>= 8;
        }
        return offset + 4;
    }

    /* wait for any byte to come back over socket */
    private boolean waitForAck(InputStream socketInputStream, int milliseconds) {
        String msg;
        byte[] ackBuff = new byte[4];
        long timeout = System.currentTimeMillis() + milliseconds;
        int nAvailable = 0;
        while ((nAvailable < 4) && (System.currentTimeMillis() < timeout)) {
            try {
                nAvailable = socketInputStream.available();
            } catch (IOException ex) {
                Log.d(TAG, "IOException waiting for socket read for ack", ex);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
        if (nAvailable >= 4) {
            try {
                socketInputStream.read(ackBuff);
                msg = String.format("ack received = 0x%02X 0x%02X 0x%02X 0x%02X ", ackBuff[0], ackBuff[1], ackBuff[2], ackBuff[3]);
                Log.d(TAG, msg);
            } catch (IOException ex) {
                Log.d(TAG, "IOException waiting for socket read for ack", ex);
            }
            return true; /* TODO not actually checking result */
        }
        return false;
    }

    private int waitForCrc(InputStream socketInputStream, int milliseconds) {
        String msg;
        byte[] crcBuff = new byte[4];
        long timeout = System.currentTimeMillis() + milliseconds;
        int nAvailable = 0;
        int crc = 0;
        while ((nAvailable < 4) && (System.currentTimeMillis() < timeout)) {
            try {
                nAvailable = socketInputStream.available();
            } catch (IOException ex) {
                Log.d(TAG, "IOException waiting for socket read for ack", ex);
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
        if (nAvailable >= 4) {
            try {
                nAvailable = socketInputStream.read(crcBuff, 0, 4);
                if (nAvailable == 4) {
                    crc = (crcBuff[0] & 0xff);
                    crc = (crc << 8) | (crcBuff[1] & 0xff);
                    crc = (crc << 8) | (crcBuff[2] & 0xff);
                    crc = (crc << 8) | (crcBuff[3] & 0xff);
                }
                msg = String.format("crc received %d = 0x%02X 0x%02X 0x%02X 0x%02X ", crc, crcBuff[0], crcBuff[1], crcBuff[2], crcBuff[3]);
                Log.d(TAG, msg);
            } catch (IOException ex) {
                Log.d(TAG, "IOException waiting for socket read for crc", ex);
            }
        }
        return crc;
    }

    public void uploadFile(final String filename) {
        uploadFile(filename, false, null);
    }

    public void uploadFile(final String filename, final boolean deleteFile, final Runnable postUploadRunnable) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                String msg;
                OutputStream socketOutputStream = null;
                InputStream socketInputStream = null;
                boolean openedSocket = false;

                /* is socket already open? */
                if ((socket == null) || (socket.isClosed())) {
                    try {
                        socket = new Socket(connectionParameters.dstIpAddress, connectionParameters.port);
                        socket.setKeepAlive(true);
                        socket.setSoLinger(true, 0);
                        openedSocket = true;
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }

                try {
                    socketOutputStream = socket.getOutputStream();
                    socketInputStream = socket.getInputStream();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                if ((socketOutputStream == null) || (socketInputStream == null)) {
                    Log.d(TAG, "unable to open socket. operation failed");
                    return;
                }

                File file = new File(filename);
                int fileSize = (int) file.length();
                int chunkSize = 65536;
                msg = String.format("sending file of length = %d. packet size = %d", fileSize, chunkSize);
                Log.d(TAG, msg);
                FileInputStream fileInputStream = null;
                try {
                     fileInputStream = new FileInputStream(file);
                } catch (FileNotFoundException ex) {
                    ex.printStackTrace();
                    return;
                }

                int offset = 0;
                int headerSize = 256;
                byte[] header = new byte[headerSize];

                /* filesize */
                offset = stuffIntegerIntoByteArray(header, fileSize, offset);

                /* length of filename */
                int filenameLength = filename.length();
                offset = stuffIntegerIntoByteArray(header, filenameLength, offset);

                /* number of packets we will be sending */
                int numberOfPackets = (fileSize + chunkSize - 1) / chunkSize;
                int packetIndex = 0;
                offset = stuffIntegerIntoByteArray(header, numberOfPackets, offset);

                /* sync word */
                int syncWord = 0xdeadbeef;
                offset = stuffIntegerIntoByteArray(header, syncWord, offset);

                byte[] array = filename.getBytes();
                for (int i = 0; i < filenameLength; i++) {
                    header[offset + i] = array[i];
                }
                offset += filenameLength;

                /* send out packet header, including filename and size, etc */
                int sendLength = offset;
                try {
                    socketOutputStream.write(header, 0, sendLength);
                    socketOutputStream.flush();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                waitForAck(socketInputStream, 1000);

                int crc = 0;
                byte[] bytes = new byte[chunkSize + headerSize];
                int readIndex = 0;
                while (readIndex < fileSize) {

                    msg = String.format("sending packet. read index = %d", readIndex);
                    Log.d(TAG, msg);

                    int numberOfBytes = chunkSize;
                    if ((readIndex + numberOfBytes) > fileSize) {
                        numberOfBytes = fileSize - readIndex;
                    }
                    try {
                        fileInputStream.read(bytes, 0, numberOfBytes);
                        crc = crc32.compute(bytes, numberOfBytes, crc);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }

                    msg = String.format("packet index = %d. crc = %d", packetIndex, crc);
                    Log.d(TAG, msg);

                    /* packet header */
                    offset = 0;
                    offset = stuffIntegerIntoByteArray(header, packetIndex, offset);
                    offset = stuffIntegerIntoByteArray(header, readIndex, offset);
                    offset = stuffIntegerIntoByteArray(header, numberOfBytes, offset);
                    sendLength = stuffIntegerIntoByteArray(header, syncWord, offset);

                    ++packetIndex;
                    readIndex += numberOfBytes;

                    try {
                        socketOutputStream.write(header, 0, sendLength);
                        socketOutputStream.flush();
                    } catch (IOException ex) {
                        Log.d(TAG, "IOException writing to socket", ex);
                    }

                    /* packet data */
                    try {
                        socketOutputStream.write(bytes, 0, numberOfBytes);
                        socketOutputStream.flush();
                    } catch (IOException ex) {
                        Log.d(TAG, "IOException writing to socket", ex);
                    }

                    waitForAck(socketInputStream, 1000);
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }

                msg = String.format("crc = %d", crc);
                Log.d(TAG, msg);

                /* the last packet is empty to indicate last packeticity */
                offset = 0;
                offset = stuffIntegerIntoByteArray(header, crc, offset);
                offset = stuffIntegerIntoByteArray(header, 0, offset);
                offset = stuffIntegerIntoByteArray(header, 0, offset);
                sendLength = stuffIntegerIntoByteArray(header, syncWord, offset);

                waitForAck(socketInputStream, 1000);

                try {
                    Log.d(TAG, "writing to socket");
                    socketOutputStream.write(header, 0, sendLength);
                    Log.d(TAG, "wrote to socket");
                } catch (IOException ex) {
                    Log.d(TAG, "IOException writing to socket", ex);
                }

                Log.d(TAG, "finished sending file");

                int remoteCrc = waitForCrc(socketInputStream, 1000);

                msg = String.format("remote crc = %d", remoteCrc);
                Log.d(TAG, msg);

                /* close file */
                try {
                    fileInputStream.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                try {
                    socketOutputStream.write(header, 0, sendLength);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                if (openedSocket) {
                    try {
                        socket.close();
                    } catch (IOException ex) {
                        Log.e(TAG, "IOException caught closing socket", ex);
                    }
                    socket = null;
                }

                if (deleteFile && (remoteCrc == crc)) {
                    msg = String.format("crc match. deleting file [%s]", filename);
                    Log.d(TAG, msg);
                    file.delete();
                }

                if (postUploadRunnable != null) {
                    postUploadRunnable.run();
                }
            }
        };
        handler.post(runnable);
    }

    public Runnable uploadBufferRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                socket = new Socket(connectionParameters.dstIpAddress, connectionParameters.port);
                OutputStream outputStream = socket.getOutputStream();
                int maxWriteSize = 100;
                int bufferLength = dataBuffer.length;
                int writeIndex = 0;
                while (writeIndex < bufferLength) {
                    String msg = String.format("SimpleServer writing %d/%d bytes", writeIndex, bufferLength);
                    Log.d(TAG, msg);
                    int numberOfBytesToWrite = maxWriteSize;
                    if ((writeIndex + numberOfBytesToWrite) > bufferLength) {
                        numberOfBytesToWrite = bufferLength - writeIndex;
                    }
                    outputStream.write(dataBuffer, writeIndex, numberOfBytesToWrite);
                    writeIndex += numberOfBytesToWrite;
                }
                outputStream.write(dataBuffer);
                outputStream.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    };

    public void uploadBuffer(byte[] buffer) {
        dataBuffer = buffer;
        handler.post(uploadBufferRunnable);
    }
}
