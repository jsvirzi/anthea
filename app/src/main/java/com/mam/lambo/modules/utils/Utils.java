package com.mam.lambo.modules.utils;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.mam.lambo.modules.camera.Common;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

/**
 * Created by jsvirzi on 12/2/16.
 */

public class Utils {
    static final String TAG = "Utils";

    private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM-dd-HH-mm-ss");;

    public static String humanReadableTime(long timestamp) {
        return simpleDateFormat.format(new Date(timestamp));
    }

    public static String humanReadableTime() {
        long timestamp = System.currentTimeMillis();
        return humanReadableTime(timestamp);
    }

    public static void post(Handler handler, Runnable runnable, boolean sync) {
        if ((handler == null) || (runnable == null)) {
            return;
        }
        if (sync) {
            handler.post(runnable);
        } else {
            runnable.run();
        }
    }

    public static void wait(int delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Log.e(TAG, "InterruptedException caught in sleep(). Really?", ex);
        }
    }

    /* terminate thread and return once thread has died */
    public static void goodbyeThread(HandlerThread thread) {
        String msg;
        Thread currentThread = Thread.currentThread();
        if (currentThread.getId() == thread.getId()) {
            msg = String.format(Common.LOCALE, "attempt to kill thread(name=%s,id=%s) from same thread", thread.getName(), thread.getId());
            Log.d(TAG, msg);
            return;
        }
        thread.quitSafely();
        try {
            thread.join();
        } catch (InterruptedException ex) {
            msg = String.format(Common.LOCALE, "exception closing thread %s", thread.getName());
            Log.e(TAG, msg, ex);
        }
    }

    public static List<InetAddress> getIpAddresses() {
        List<InetAddress> addresses = new ArrayList<>(1);
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();
                    if (inetAddress.isSiteLocalAddress()) {
                        addresses.add(inetAddress);
                        String msg = "Server running at : " + inetAddress.getHostAddress();
                        Log.d(TAG, msg);
                    }
                }
            }

        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return addresses;
    }

    public static InetAddress getIpAddress() {
        List<InetAddress> addresses = getIpAddresses();
        if (addresses.size() == 0) return null;
        return addresses.get(0);
    }

    public static BufferedWriter getBufferedWriter(String filename, int length) {
        return getBufferedWriter(filename, length, false);
    }

    public static BufferedWriter getBufferedWriter(String filename, int length, boolean append) {
        String msg;
        FileOutputStream outputStream = null;
        BufferedWriter writer = null;
        File file = new File(filename);
        return getBufferedWriter(file, length, append);
    }

    public static BufferedWriter getBufferedWriter(File file, int length, boolean append) {
        String msg;
        FileOutputStream outputStream = null;
        BufferedWriter writer = null;

        try {
            outputStream = new FileOutputStream(file, append);
        } catch (FileNotFoundException ex) {
            msg = String.format("unable to create file [%s]", file.getPath());
            Log.e(TAG, msg, ex);
        }

        if (outputStream == null) return writer;

        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        writer = new BufferedWriter(outputStreamWriter, length);
        if (writer == null) {
            msg = String.format("unable to create file [%s]", file.getPath());
            Log.e(TAG, msg);
        }

        return writer;
    }

    public static boolean writeLine(BufferedWriter bufferedWriter, String line) {
        try {
            bufferedWriter.write(line);
        } catch (IOException ex) {
            String msg = String.format("IOException writing to file [%s]", bufferedWriter.toString());
            Log.e(TAG, msg, ex);
            return false;
        }
        return true;
    }

    public static void closeStream(BufferedWriter writer) {
        String msg;
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
        } catch (IOException ex) {
            msg = String.format("IOException closing file [%s]", writer.toString());
            Log.e(TAG, msg, ex);
        } finally {
            closeSilently(writer);
            writer = null;
        }
    }

    public static boolean closeSilently(Closeable closeable) {
        boolean succeeded;
        try {
            if (closeable != null) {
                closeable.close();
            }
            succeeded = true;
        } catch (Exception exc) {
            Log.w(TAG, "Exception silenced during a close() call.", exc);
            succeeded = false;
        }
        return succeeded;
    }

    @TargetApi(Build.VERSION_CODES.M)
    public static void checkPermissions(Activity activity) {
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        final int MY_REQUEST_CODE = 0;
        if (currentapiVersion >= Build.VERSION_CODES.M) {
            boolean cameraOk = activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED;
            boolean sdOk = activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
            if (!cameraOk || !sdOk) {
                activity.requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE,}, MY_REQUEST_CODE);
            }
        }
    }

    public static short[] readStreamShorts(InputStream inputStream, int skip) {
        int length = 0;
        int size = 0;
        try {
            length = inputStream.available();
            size = length / 2 - skip;
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        short[] shorts;
        if (size != 0) {
            shorts = new short[size];
        } else {
            return null;
        }

        try {
            DataInputStream dataInputStream = new DataInputStream(inputStream);
            for (int i = 0; i < skip; i++) {
                dataInputStream.readShort();
            }
            for (int i = 0; i < size; i++) {
                shorts[i] = dataInputStream.readShort();
                byte b1 = (byte) (shorts[i] & 0xff);
                byte b2 = (byte) ((shorts[i] >> 8) & 0xff);
                short a = b1;
                a = (short) (a << 8);
                shorts[i] = (short) (a & 0xff00);
                a = b2;
                a = (short) (a & 0xff);
                shorts[i] = (short) (shorts[i] + a);
            }
            dataInputStream.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return shorts;
    }

    public static short[] readFileShorts(String filename, int skip) {
        File file = new File(filename);
        int size = (int) file.length() / 2 - skip;
        short[] shorts = new short[size];
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
            DataInputStream dataInputStream = new DataInputStream(bufferedInputStream);
            for (int i = 0; i < skip; i++) {
                dataInputStream.readShort();
            }
            for (int i = 0; i < size; i++) {
                shorts[i] = dataInputStream.readShort();
                byte b1 = (byte) (shorts[i] & 0xff);
                byte b2 = (byte) ((shorts[i] >> 8) & 0xff);
                short a = b1;
                a = (short) (a << 8);
                shorts[i] = (short) (a & 0xff00);
                a = b2;
                a = (short) (a & 0xff);
                shorts[i] = (short) (shorts[i] + a);
            }
            dataInputStream.close();
        } catch (FileNotFoundException ex) {
            String msg = String.format("error accessing file [%s]", filename);
            Log.e(TAG, msg, ex);
            return null;
        } catch (IOException ex) {
            String msg = String.format("error reading from file [%s]", filename);
            Log.e(TAG, msg, ex);
            return null;
        }
        return shorts;
    }

    public static short[] duplicateShortArray(short[] iArray, int multiplicity) {
        return duplicateShortArray(iArray, multiplicity, 0);
    }

    public static short[] duplicateShortArray(short[] iArray, int multiplicity, int gapLength) {
        int i, j, index = 0;
        short[] oArray = new short[(iArray.length + gapLength) * multiplicity];
        for(j=0;j<multiplicity;++j) {
            for (i = 0; i < iArray.length; ++i) {
                oArray[index] = iArray[i];
                ++index;
            }
            for (i = 0; i < gapLength; ++i) {
                oArray[index] = 0;
                ++index;
            }
        }
        return oArray;
    }

    public static byte[] readFileBytes(String filename) {
        File file = new File(filename);
        int size = (int) file.length();
        byte[] bytes = new byte[size];
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
            bufferedInputStream.read(bytes);
            bufferedInputStream.close();
        } catch (FileNotFoundException ex) {
            String msg = String.format("error accessing file [%s]", filename);
            Log.e(TAG, msg, ex);
            return null;
        } catch (IOException ex) {
            String msg = String.format("error reading from file [%s]", filename);
            Log.e(TAG, msg, ex);
            return null;
        }
        return bytes;
    }

    public static byte[] duplicateByteArray(byte[] iArray, int multiplicity) {
        return duplicateByteArray(iArray, multiplicity, 0);
    }

    public static byte[] duplicateByteArray(byte[] iArray, int multiplicity, int gapLength) {
        int i, j, index = 0;
        byte[] oArray = new byte[(iArray.length + gapLength) * multiplicity];
        for(j=0;j<multiplicity;++j) {
            for (i = 0; i < iArray.length; ++i) {
                oArray[index] = iArray[i];
                ++index;
            }
            for (i = 0; i < gapLength; ++i) {
                oArray[index] = 0;
                ++index;
            }
        }
        return oArray;
    }

    /*
     * code jacked from the internet
     */
    public static void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height, boolean reverseUV, boolean isRgb) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                if (isRgb) {
                    R = (argb[index] & 0xff0000) >> 16;
                    G = (argb[index] & 0xff00) >> 8;
                    B = (argb[index] & 0xff) >> 0;
                } else {
                    B = (argb[index] & 0xff0000) >> 16;
                    G = (argb[index] & 0xff00) >> 8;
                    R = (argb[index] & 0xff) >> 0;
                }

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
                //    pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    if (reverseUV) {
                        yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                        yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                    } else {
                        yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                        yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    }
                }
                index++;
            }
        }
    }

    public static byte clamp(float x) {
        return (byte) ((x < 0) ? 0 : ((x > 255) ? 255 : x));
    }

    public static byte clamp(int x) {
        return (byte) ((x < 0) ? 0 : ((x > 255) ? 255 : x));
    }

    public static void decodeYUV420sp(byte[] yuv420sp, int[] argb, int width, int height, boolean reverseUV, boolean isRgb) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;
        int row;
        int col;
        int uvStride = width; /* = (width / 2) * 2 */
        int rgbIndex = 0;

        for(row = 0; row < height; ++row) {
            for (col = 0; col < width; ++col) {
                yIndex = row * width + col;
                byte Y = yuv420sp[yIndex];
                uvIndex = (row / 2) * uvStride + (col / 2) * 2;
                byte U, V;
                if (reverseUV) {
                    V = yuv420sp[uvIndex];
                    U = yuv420sp[uvIndex + 1];
                } else {
                    U = yuv420sp[uvIndex];
                    V = yuv420sp[uvIndex + 1];
                }
                float y = Y;
                float u = U - 128.0f;
                float v = V - 128.0f;
                byte r = clamp(y + 1.370750f * v);
                byte g = clamp(y - 0.698001f * v - 0.337633f * u);
                byte b = clamp(y + 1.732446f * u);
                int color = 0xff;
                if (isRgb) {
                    color = (color << 8) | r;
                    color = (color << 8) | g;
                    color = (color << 8) | b;
                } else {
                    color = (color << 8) | b;
                    color = (color << 8) | g;
                    color = (color << 8) | r;
                }
                argb[rgbIndex++] = color;
            }
        }
    }
}
