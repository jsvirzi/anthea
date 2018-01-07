package com.mam.lambo.modules.camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by jsvirzi on 10/20/16.
 */

public class Utils {
    static final String TAG = "Utils";

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

    public static Bitmap makeBitmap(int width, int height, ByteBuffer Y) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Y.rewind();
        for(int row=0;row<height;++row) {
            for(int col=0;col<width;++col) {
                byte pixel = Y.get();
                int r = pixel;
                int g = pixel;
                int b = pixel;
                int alpha = 255;
                bitmap.setPixel(col, row, Color.argb(alpha, r, g, b));
            }
        }
        return bitmap;
    }

    public static void drawBoundingBoxes(Bitmap bitmap, int numberOfBoxes, float[] array, int boxOffset, int dnnWidth, int dnnHeight) {
        Canvas canvas = new Canvas(bitmap);
        int width = bitmap.getWidth();
        float hScale = width;
        hScale = hScale / dnnWidth;
        int height = bitmap.getHeight();
        float vScale = height;
        vScale = vScale / dnnHeight;
        int offset = boxOffset;
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStrokeWidth(3.0f);
        paint.setStyle(Paint.Style.STROKE);
        for(int box=0;box<numberOfBoxes;++box) {
//            for (int row = 0; row < height; ++row) {
//                for (int col = 0; col < width; ++col) {
                    int l = (int) (array[offset++] * hScale);
                    int t = (int) (array[offset++] * vScale);
                    int r = (int) (array[offset++] * hScale);
                    int b = (int) (array[offset++] * vScale);
                    canvas.drawRect(l, t, r, b, paint);
//                }
//            }
        }
    }

    public static boolean readFloatsFromFile(String s, float[] array, int n, int offset, int stride) {
        java.io.File file = new java.io.File(s);
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        int i, j;
        float a;
        try {
            FileInputStream fis = new FileInputStream(file);
            DataInputStream dis = new DataInputStream(fis);
            for(i=0;i<offset;++i) dis.readFloat();
            for(i=0;i<n;++i) {
                a = dis.readFloat();
                byteBuffer.position(0);
                byteBuffer.order(ByteOrder.BIG_ENDIAN);
                byteBuffer.putFloat(a);
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                byteBuffer.position(0);
                a = byteBuffer.getFloat();
                array[i] = a;
                for(j=0;j<(stride-1);++j) dis.readFloat();
            }
            dis.close();
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
        }
        return true;
    }
}
