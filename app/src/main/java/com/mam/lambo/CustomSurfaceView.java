package com.mam.lambo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Created by jsvirzi on 11/17/16.
 */

public class CustomSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private static String TAG = "CustomSurfaceView";
    public SurfaceHolder surfaceHolder; // TODO make private
    int transparency = 255;
    long hysterisisTimeout;
    Rect cropRectangle;
    Rect dimsRectangle;
    Rect activeArraySize;
    TextPaint textPaint;
    Paint cropPaint;
    Point gazeAngle;
    Point poseFocus;
    Rect poseRectangle;
    Rect faceRectangle;
    Activity activity;
    CustomSurfaceView instance;
    Point leftEye;
    Point rightEye;
    float imageWidth;
    float imageHeight;
    float leftEyeOpen;
    float rightEyeOpen;
    public Paint redPaint; // TODO make private
    Paint bluePaint;
    Paint greenPaint;
    Paint boundingBoxPaint;
    Point mouth;
    float smiling;
    float yaw = -999.0f;
    final int maxBoundingBoxes = 56 * 48; // TODO overkill
    RectF[] boundingBoxes;
    int boundingBoxIndex = 0;
    float horizontalFieldOfView = 60.0f; // TODO different for each camera
    float verticalFieldOfView = 40.0f; // TODO different for each camera
    String screenText;

    public void setImageDimensions(int width, int height) {
        imageWidth = (float) width;
        imageHeight = (float) height;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public void setBoundingBox(final RectF box) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                boundingBoxIndex = 0;
                boundingBoxes[boundingBoxIndex].set(box);
                ++boundingBoxIndex;
            }
        };
        activity.runOnUiThread(runnable);
    }

    public void setBoundingBox(final float xMin, final float yMin, final float xMax, final float yMax) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                boundingBoxIndex = 0;
                boundingBoxes[boundingBoxIndex].set(xMin, yMin, xMax, yMax);
                ++boundingBoxIndex;
            }
        };
        activity.runOnUiThread(runnable);
    }

    public void addBoundingBox(final float xMin, final float yMin, final float xMax, final float yMax) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                boundingBoxes[boundingBoxIndex].set(xMin, yMin, xMax, yMax);
                ++boundingBoxIndex;
            }
        };
        activity.runOnUiThread(runnable);
    }

    /* this is strictly for debugging. do not try to wrap your head around what I'm doing */
    public void setRawBoundingBoxes(final float[] dimensions, final int rectangleOffset, final int probabilityOffset) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                int nRows = 48;
                int nCols = 56;
                float rowStride = 3.0f;
                float colStride = 4.0f;
                float rowOffset = 20.0f;
                float colOffset = 5.0f;
                float dnnWidth = 224.0f; // TODO specify in a more principled fashion
                float dnnHeight = 168.0f;
                float probabilityThreshold = 0.45f;
                //
                rowStride = 4.0f;
                colStride = 4.0f;
                rowOffset = 4.0f;
                colOffset = 4.0f;
                //
                int bbIndex = 0;

                for(int row=0;row<nRows;++row) {
                    for(int col=0;col<nCols;++col,++bbIndex) {
                        float probability = dimensions[probabilityOffset + bbIndex * 2 + 1];
                        if(probability > probabilityThreshold) {
                            float bbxmin = dimensions[rectangleOffset + bbIndex * 4 + 0];
                            float bbymin = dimensions[rectangleOffset + bbIndex * 4 + 1];
                            float bbxmax = dimensions[rectangleOffset + bbIndex * 4 + 2];
                            float bbymax = dimensions[rectangleOffset + bbIndex * 4 + 3];
                            float xMin = (colOffset + bbxmin + colStride * col) / dnnWidth;
                            float yMin = (rowOffset + bbymin + rowStride * row) / dnnHeight;
                            float xMax = (colOffset + bbxmax + colStride * col) / dnnWidth;
                            float yMax = (rowOffset + bbymax + rowStride * row) / dnnHeight;
                            boundingBoxes[bbIndex].set(xMin, yMin, xMax, yMax);
                        } else {
                            boundingBoxes[bbIndex].set(0.0f, 0.0f, 0.0f, 0.0f);
                        }
                    }
                }
                boundingBoxIndex = bbIndex;
            }
        };
        activity.runOnUiThread(runnable);
    }

    public void setBoundingBoxes(final int numberOfBoxes, final float[] dimensions, final int groupedRectangleOffset) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                int maxIndex = (numberOfBoxes > maxBoundingBoxes) ? maxBoundingBoxes : numberOfBoxes;
                float dnnWidth = 224.0f; // TODO specify in a more principled fashion
                float dnnHeight = 168.0f;
                for (boundingBoxIndex = 0; boundingBoxIndex < maxIndex; boundingBoxIndex++) {
                        float xMin = dimensions[groupedRectangleOffset + boundingBoxIndex * 4 + 0] / dnnWidth;
                        float yMin = dimensions[groupedRectangleOffset + boundingBoxIndex * 4 + 1] / dnnHeight;
                        float xMax = dimensions[groupedRectangleOffset + boundingBoxIndex * 4 + 2] / dnnWidth;
                        float yMax = dimensions[groupedRectangleOffset + boundingBoxIndex * 4 + 3] / dnnHeight;
                        boundingBoxes[boundingBoxIndex].set(xMin, yMin, xMax, yMax);
                }
            }
        };
        activity.runOnUiThread(runnable);
    }

    public void clearBoundingBoxes() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                boundingBoxIndex = 0;
            }
        };
        activity.runOnUiThread(runnable);
    }

    void setActiveArraySize(Rect rect) {
        activeArraySize = new Rect(rect);
    }

    private Runnable clearRunnable = new Runnable() {
        @Override
        public void run() {
            instance.setVisibility(VISIBLE);
            Canvas canvas = surfaceHolder.lockCanvas();
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    };

    private Runnable redrawRunnable = new Runnable() {
        @Override
        public void run() {
            instance.setVisibility(VISIBLE);
            Canvas canvas = surfaceHolder.lockCanvas();
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            int width = canvas.getWidth();
            float hScale = (float) width;
            int height = canvas.getHeight();
            float vScale = (float) height;

            /* TODO just a sanity check */
            if (screenText == null) {
                screenText = "--:--:--";
            }

            if (screenText != null) {
                textPaint.setAlpha(transparency);
                canvas.drawText(screenText, 220, 60, textPaint);
            }

            if (faceRectangle != null) {
                float tFraction = (float) faceRectangle.top / imageHeight;
                float bFraction = (float) faceRectangle.bottom / imageHeight;
                float lFraction = (float) faceRectangle.left / imageWidth;
                float rFraction = (float) faceRectangle.right / imageWidth;
                float t = vScale * tFraction;
                float l = hScale * lFraction;
                float r = hScale * rFraction;
                float b = vScale * bFraction;
                canvas.drawLine(r, t, r, b, cropPaint); // only one line here
                canvas.drawLine(l, t, l, b, cropPaint); // only one line here
                canvas.drawLine(r, t, l, t, cropPaint); // only one line here
                canvas.drawLine(r, b, l, b, cropPaint); // only one line here
            }

            if (leftEye != null) {
                float hFraction = (float) leftEye.x / imageWidth;
                float vFraction = (float) leftEye.y / imageHeight;
                float x = hScale * hFraction;
                float y = vScale * vFraction;
                float r = 20.0f;
                if (leftEyeOpen > 0.5f) {
                    canvas.drawCircle(x, y, r, redPaint);
                } else {
                    canvas.drawCircle(x, y, r, bluePaint);
                }
            }

            if (rightEye != null) {
                float hFraction = (float) rightEye.x / imageWidth;
                float vFraction = (float) rightEye.y / imageHeight;
                float x = hScale * hFraction;
                float y = vScale * vFraction;
                float r = 20.0f;
                if (rightEyeOpen > 0.5f) {
                    canvas.drawCircle(x, y, r, redPaint);
                } else {
                    canvas.drawCircle(x, y, r, bluePaint);
                }
            }

            if (mouth != null) {
                float hFraction = (float) mouth.x / imageWidth;
                float vFraction = (float) mouth.y / imageHeight;
                float x = hScale * hFraction;
                float y = vScale * vFraction;
//                float r = 20.0f;
                if (smiling < 0.5f) {
                    canvas.drawArc(x - 50.0f, y - 25.0f, x + 50.0f, y + 25.0f, 180.0f, 180.0f, false, redPaint);
                } else {
                    canvas.drawArc(x - 50.0f, y - 25.0f, x + 50.0f, y + 25.0f, 0.0f, 180.0f, false, greenPaint);
                }
            }

            if ((-90 < yaw) && (yaw < 90)) {
                float x = width * (2.0f * yaw + 180.0f) / 360.0f;
                float y = 0.80f * height;
                float r = 20.0f;
                canvas.drawCircle(x, y, r, bluePaint);
            }

            float hfov = 0.5f * horizontalFieldOfView;
            boolean hfovOk = ((-hfov < gazeAngle.x) && (gazeAngle.x < hfov));
            float vfov = 0.5f * verticalFieldOfView;
            boolean vfovOk = ((-vfov < gazeAngle.y) && (gazeAngle.y < vfov));
            if (hfovOk && vfovOk) {
                float x = 0.5f * (1.0f + gazeAngle.x / hfov) * hScale;
                float y = 0.5f * (1.0f + gazeAngle.y / vfov) * vScale;
                float r = 20.0f;
                canvas.drawCircle(x, y, r, redPaint);
            }

            if ((boundingBoxes != null) && (boundingBoxIndex > 0)) {
                for (int i = 0; i < boundingBoxIndex; i++) {
                    RectF box = boundingBoxes[i];
                    RectF rect = new RectF(box.left * width, box.top * height, box.right * width, box.bottom * height);
                    canvas.drawRect(rect, boundingBoxPaint);
                }
            }

            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    };

    public void setText(String text) {
        screenText = text;
    }

    public void redraw() {
        if (activity != null) {
            redrawRunnable.run();
//            activity.runOnUiThread(redrawRunnable);
        }
    }

    public void clear() {
        if (activity != null) {
            activity.runOnUiThread(clearRunnable);
        }
    }

//    public static void DRAWME(Rect rect) {
//        float a;
//        a = rect.top;
//        a = a / activeArraySize.bottom;
//        rect.top = (int) a;
//        a = rect.left;
//        a = a / activeArraySize.right;
//        rect.left = (int) a;
//        a = rect.bottom;
//        a = a / activeArraySize.bottom;
//        rect.bottom = (int) a;
//        a = rect.right;
//        a = a / activeArraySize.right;
//        rect.right = (int) a;
//        faceRectangle = new Rect(rect);
//        String msg = String.format("face at (%d,%d)-(%d,%d)", rect.left, rect.top, rect.right, rect.bottom);
//        Log.d(TAG, msg);
//    }

    public void setTransparency(int inputTransparency) {
        transparency = inputTransparency;
    }

    public void setCropRectangle(Rect rect) {
        cropRectangle = new Rect(rect);
    }

    public void setDimensions(Rect rect) {
        dimsRectangle = new Rect(rect);
    }

    public void setTransparency(float inputTransparency) {
        Float tmp = inputTransparency * 255.0f;
        transparency = tmp.intValue();
    }

    public void redraw(boolean aboveThreshold) {
        this.setVisibility(VISIBLE);
        Canvas canvas = surfaceHolder.lockCanvas();
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);

        long now = System.currentTimeMillis();
        if (aboveThreshold) {
            hysterisisTimeout = now + 250; /* 1/4 second */
        }

        if (aboveThreshold || (now < hysterisisTimeout)) {
            hysterisisTimeout = now + 250; /* 1/4 second */
            textPaint.setAlpha(transparency);
            canvas.drawText("DISTRACTED", 220, 60, textPaint);
        }

        if (cropRectangle != null) {
            int width = canvas.getWidth();
            int height = canvas.getHeight();
            float t = cropRectangle.top;
            float l = cropRectangle.left;
            float r = (float) width;
            float b = (float) height;
            r = r * cropRectangle.right / dimsRectangle.right;
            b = b * cropRectangle.bottom / dimsRectangle.bottom;
            canvas.drawLine(r, 0.0f, r, b, cropPaint); // only one line here
        }

        if (faceRectangle != null) {
            int width = canvas.getWidth();
            float hScale = (float) width;
            int height = canvas.getHeight();
            float vScale = (float) height;
            float tFraction = (float) faceRectangle.top / (float) activeArraySize.bottom;
            float bFraction = (float) faceRectangle.bottom / (float) activeArraySize.bottom;
            float lFraction = 1.0f - (float) faceRectangle.left / (float) activeArraySize.right;
            float rFraction = 1.0f - (float) faceRectangle.right / (float) activeArraySize.right;
            float t = vScale * tFraction;
            float l = hScale * lFraction;
            float r = hScale * rFraction;
            float b = vScale * bFraction;
            canvas.drawLine(r, t, r, b, cropPaint); // only one line here
            canvas.drawLine(l, t, l, b, cropPaint); // only one line here
            canvas.drawLine(r, t, l, t, cropPaint); // only one line here
            canvas.drawLine(r, b, l, b, cropPaint); // only one line here
        }
        surfaceHolder.unlockCanvasAndPost(canvas);
    }

    void drawRectangle(Canvas canvas, Rect rect) {
        int width = canvas.getWidth();
        float hScale = (float) width;
        int height = canvas.getHeight();
        float vScale = (float) height;
        float tFraction = (float) rect.top / (float) activeArraySize.bottom;
        float bFraction = (float) rect.bottom / (float) activeArraySize.bottom;
        float lFraction = 1.0f - (float) rect.left / (float) activeArraySize.right;
        float rFraction = 1.0f - (float) rect.right / (float) activeArraySize.right;
        float t = vScale * tFraction;
        float l = hScale * lFraction;
        float r = hScale * rFraction;
        float b = vScale * bFraction;
        canvas.drawLine(r, t, r, b, cropPaint); // only one line here
        canvas.drawLine(l, t, l, b, cropPaint); // only one line here
        canvas.drawLine(r, t, l, t, cropPaint); // only one line here
        canvas.drawLine(r, b, l, b, cropPaint); // only one line here
    }

    void init() {
        instance = this;
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);

        redPaint = new Paint();
        redPaint.setStyle(Paint.Style.FILL);
        redPaint.setColor(Color.RED);
        redPaint.setStrokeWidth(30.0f);

        bluePaint = new Paint();
        bluePaint.setStyle(Paint.Style.FILL);
        bluePaint.setColor(Color.BLUE);

        greenPaint = new Paint();
        greenPaint.setStyle(Paint.Style.FILL);
        greenPaint.setColor(Color.GREEN);

        textPaint = new TextPaint();
        textPaint.setTextSize(70);
        textPaint.setStrokeWidth(30);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.RED);
        textPaint.setFlags(Paint.FAKE_BOLD_TEXT_FLAG);

        cropPaint = new Paint();
        cropPaint.setColor(Color.YELLOW);
        cropPaint.setStrokeWidth(3.0f);
        cropPaint.setAlpha(128);

        boundingBoxPaint = new Paint();
        boundingBoxPaint.setColor(Color.GREEN);
        boundingBoxPaint.setStyle(Paint.Style.STROKE);
        boundingBoxPaint.setStrokeWidth(3.0f);

        boundingBoxes = new RectF[maxBoundingBoxes];
        for (int i = 0; i < maxBoundingBoxes; i++) {
            boundingBoxes[i] = new RectF();
        }

        hysterisisTimeout = System.currentTimeMillis() + 100000000; /* no love you long time */

        gazeAngle = new Point();
    }

    public CustomSurfaceView(Context context) {
        super(context);
        init();
    }

    public CustomSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
//        Canvas canvas = holder.lockCanvas();
//        int width = canvas.getWidth();
//        int height = canvas.getHeight();
//        TextPaint textPaint = new TextPaint();
//        textPaint.setTextSize(70);
//        textPaint.setStrokeWidth(30);
//        textPaint.setTextAlign(Paint.Align.CENTER);
//        textPaint.setStyle(Paint.Style.FILL);
//        textPaint.setColor(Color.RED);
//        textPaint.setAlpha(255);
//        textPaint.setFlags(Paint.FAKE_BOLD_TEXT_FLAG);
//        canvas.drawText("DISTRACTED", 220, 60, textPaint);
//        Paint paint = new Paint();
//        paint.setColor(Color.YELLOW);
//        paint.setStrokeWidth(30.0f);
//        paint.setAlpha(255);
//        float r = width * (1152.0f / 1920.0f);
//        float b = height * 1.0f;
//        canvas.drawLine(r, 0.0f, r, b, paint);
//        surfaceHolder.unlockCanvasAndPost(canvas);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

//    public CameraCaptureSession.CaptureCallback getCaptureCallback() {
//        return captureCallback;
//    }
}
