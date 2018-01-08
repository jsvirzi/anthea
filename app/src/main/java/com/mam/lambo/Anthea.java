package com.mam.lambo;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.mam.lambo.AlgoWrapper;
import com.mam.lambo.commandandcontrol.RemoteHttpCommandAndControl;
import com.mam.lambo.modules.camera.Common;
import com.mam.lambo.modules.camera.SimpleCameraModule;
import com.mam.lambo.modules.nightvision.Location;
import com.mam.lambo.modules.nightvision.SunriseSunsetCalculator;
import com.mam.lambo.modules.obd.ObdModule;
import com.mam.lambo.modules.sensor.SensorModule;
import com.mam.lambo.modules.sensor.SensorRecorder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.mam.lambo.modules.server.ISimpleServerListener;
import com.mam.lambo.modules.server.SimpleServer;
import com.mam.lambo.modules.utils.SoundPlayer;
import com.mam.lambo.modules.utils.Utils;

public class Anthea extends Application {

    private static final String TAG = "Anthea";
    public static Anthea instance;
    public Context context;
    public SimpleCameraModule simpleCameraModuleExternal = null;
    public SimpleCameraModule simpleCameraModuleInternal = null;
    public final int imageWidth = 1920;
    public final int imageHeight = 1080;
    public String outputDataDirectory = null;
    public ObdModule obdModule = null;
    public AlgoWrapper algoWrapper;
    public RemoteHttpCommandAndControl remoteHttpCommandAndControl;

    public static Anthea getInstance() {
        return instance;
    }

    static {
        instance = null;
//        System.loadLibrary("CameraModule_JNI");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        context = getApplicationContext();
        instance = this;

        SoundPlayer.getInstance(context);

        Location location = new Location("39.9522222", "-75.1641667");
        SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(location, "America/New_York");
        String officialSunrise = calculator.getOfficialSunriseForDate(Calendar.getInstance());
        String officialSunset = calculator.getAstronomicalSunsetForDate(Calendar.getInstance());
        Log.d(TAG, "official sunrise = " + officialSunrise);
        Log.d(TAG, "official sunset = " + officialSunset);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");
        long now = System.currentTimeMillis();
        String sunsetString = simpleDateFormat.format(new Date(now)) + " " + officialSunset;
        String sunriseString = simpleDateFormat.format(new Date(now)) + " " + officialSunrise;
        SimpleDateFormat finalFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
        long sunsetTime = 0;
        long sunriseTime = 0;
        Date sunsetDate = null;
        Date sunriseDate = null;
        try {
            sunsetDate = finalFormat.parse(sunsetString);
            sunriseDate = finalFormat.parse(sunriseString);
        } catch (ParseException ex) {
            ex.printStackTrace();
        }
        sunsetTime = sunsetDate.getTime();
        sunriseTime = sunriseDate.getTime();

//        Date date = new Date(now);
//        int year = date.getYear();
//        int month = date.getMonth();
//        int day = date.getDay();
//        String sunsetTimeString = String.format("%04d/%02d/%02d %s:00", year, month, day, officialSunset);
//        String sunriseTimeString = String.format("%04d/%0d/%0d %s:00", year, month, day, officialSunrise);
//        Log.d(TAG, "official sunrise = " + sunriseTimeString);
//        Log.d(TAG, "official sunset = " + sunsetTimeString);

        // TODO rescue this code
//        test();

        String dataDirectory = null;

        String sdCardDirectory = null; /* LG Flex5 */
        try {

            File rootDirectory = new File("/storage");
            File[] mountPoints = rootDirectory.listFiles();
            for (File file : mountPoints) {
                if (file.isDirectory() == false) continue;
                String directoryString = file.toString();
                if (directoryString.equals("/storage/emulated")) continue;
                if (directoryString.equals("/storage/self")) continue;
                if (directoryString.equals("/storage/sdcard0")) continue;
//                if (directoryString == "/storage/external_SD") continue;
                String directoryName = String.format("%s/Android/data", directoryString);
                File tmpFile = new File(directoryName);
                if (tmpFile.isDirectory()) {
                    sdCardDirectory = directoryString;
                    break;
                }
            }

            String externalDir = context.getExternalFilesDir(null).getAbsolutePath();
            String deviceDir = Environment.getExternalStorageDirectory().getAbsolutePath();
            int pos = deviceDir.length();
            outputDataDirectory = sdCardDirectory + externalDir.substring(pos) + "/data";
            String msg = String.format(Common.LOCALE, "[%s] = output data directory", outputDataDirectory);
            Log.d(TAG, msg);
            File dir = new File(outputDataDirectory);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (sdCardDirectory == null) {
            Log.e(TAG, "unable to open output directories for application");
            return;
        }

        List<InetAddress> ipAddresses = Utils.getIpAddresses();
        if (ipAddresses.size() == 0) {
            Log.d(TAG, "unable to find a local ip address");
            return;
        }
        InetAddress ipAddress = ipAddresses.get(0);
        String ipAddressString = ipAddress.toString();
        ipAddressString = ipAddressString.substring(1);

        int port = 8080; /* TODO sync these port settings across different clients */
        remoteHttpCommandAndControl = new RemoteHttpCommandAndControl(ipAddressString);
        SimpleServer simpleServer = new SimpleServer(port);
        simpleServer.addListener(remoteHttpCommandAndControl);
    }

    // TODO shut Anthea down properly
//    public void terminate() {
//        int pid = android.os.Process.myPid();
//        android.os.Process.killProcess(pid);
//        finishAffinity();
//    }

    public void shutDown() {
//        if (sensorModule != null) {
//            sensorModule.release();
//            sensorModule = null;
//        }
        if (simpleCameraModuleExternal != null) {
            simpleCameraModuleExternal.destroy();
            simpleCameraModuleExternal = null;
        }
        if (simpleCameraModuleInternal != null) {
            simpleCameraModuleInternal.destroy();
            simpleCameraModuleInternal = null;
        }

        System.exit(0);
    }

    private class MediaMetadata {
        String filename;
    }

    MediaFormat getMediaFormat(String filename) {
        String msg;
        MediaExtractor mediaExtractor = new MediaExtractor();
        FileInputStream fileInputStream = null;
        MediaFormat mediaFormat = null;
        try {
            fileInputStream = new FileInputStream(filename);
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
            return mediaFormat;
        }

        try {
            mediaExtractor.setDataSource(fileInputStream.getFD());
            fileInputStream.close();
        } catch (IOException ex) {
            msg = String.format(Common.LOCALE, "IOException: MediaExtractor(file=[%s])", filename);
            Log.d(TAG, msg);
        }

        int numberOfTracks = mediaExtractor.getTrackCount();
        if (numberOfTracks != 1) {
            msg = String.format(Common.LOCALE, "strange: input MP4 file [%s] contains %d tracks", filename);
            Log.d(TAG, msg);
            return mediaFormat;
        }

        int trackIndex = 0;
        mediaExtractor.selectTrack(trackIndex);
        mediaFormat = mediaExtractor.getTrackFormat(trackIndex);
        mediaExtractor.release();
        mediaExtractor = null;
        return mediaFormat;
    }

    boolean test() {
        String msg;
        String filename;
        long now = System.currentTimeMillis();
        long startTimeUs = now * 1000; /* millisecs to microsecs */
        startTimeUs = 0;
        long deltaFrameTimeUs = 33333; /* 30 fps */
        File directory = context.getExternalFilesDir(null);

        String outputFilename = directory.toString() + "/new.mp4";

        List<String> inputFiles = new ArrayList<>(3);

        filename = directory.toString() + "/in1.mp4";
        inputFiles.add(filename);
        filename = directory.toString() + "/in2.mp4";
        inputFiles.add(filename);
        filename = directory.toString() + "/in3.mp4";
        inputFiles.add(filename);
        return concatenateMp4(inputFiles, outputFilename, startTimeUs, deltaFrameTimeUs);
    }

    boolean concatenateMp4(List<String> inputFiles, String outputFilename, long startTimeUs, long deltaFrameTimeUs) {
        String msg;
        MediaMuxer mediaMuxer = null;
        int format = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4;

        MediaFormat mediaFormat = getMediaFormat(inputFiles.get(0));

        try {
            mediaMuxer = new MediaMuxer(outputFilename, format);
        } catch (IllegalStateException ex) {
            msg = String.format(Common.LOCALE, "IllegalArgumentException: MediaMuxer(file=[%s], format=%d)", outputFilename, format);
            Log.e(TAG, msg, ex);
            return false;
        } catch (IOException ex) {
            msg = String.format(Common.LOCALE, "IOException: MediaMuxer(file=[%s],format=%d)", outputFilename, format);
            Log.e(TAG, msg, ex);
            return false;
        }

        /* setup output track */
        int outputTrackIndex = -1;
        try {
            outputTrackIndex = mediaMuxer.addTrack(mediaFormat); // output media format already validated
        } catch (IllegalArgumentException ex) {
            msg = String.format(Common.LOCALE, "IllegalArgumentException caught for MediaCodec.addTrack()");
            Log.e(TAG, msg, ex);
            return false;
        } catch (IllegalStateException ex) {
            msg = String.format(Common.LOCALE, "IllegalStateException caught for MediaCodec.addTrack()");
            Log.e(TAG, msg, ex);
            return false;
        }

        /* launch MediaMuxer and consume current output frame */
        try {
            mediaMuxer.start();
        } catch (IllegalStateException ex) {
            msg = String.format(Common.LOCALE, "IllegalStateException encountered MediaMuxer.start()");
            Log.e(TAG, msg, ex);
            return false;
        }

        int totalOutputSize = 0; /* statistics */

        int numberOfFrames = 0;
        boolean autoFrameTime = (deltaFrameTimeUs != 0);

        long adjustTime = 0;
        boolean firstFrame = true;
        for (String filename : inputFiles) {
            File file = new File(filename);
            long fileSize = file.length();
            int maxBufferSize = 5 * (int) fileSize / 4; /* give a little headroom */
            ByteBuffer byteBuffer = ByteBuffer.allocate(maxBufferSize);
            FileInputStream fileInputStream = null;

            try {
                fileInputStream = new FileInputStream(file);
            } catch (FileNotFoundException ex) {
                msg = String.format(Common.LOCALE, "unable to open input file [%s]", filename);
                Log.d(TAG, msg);
                continue;
            }

            MediaExtractor mediaExtractor = new MediaExtractor();

            try {
                mediaExtractor.setDataSource(fileInputStream.getFD());
                fileInputStream.close();
            } catch (IOException ex) {
                msg = String.format(Common.LOCALE, "IOException: MediaExtractor(file=[%s])", filename);
                Log.d(TAG, msg);
                continue;
            }

            int numberOfTracks = mediaExtractor.getTrackCount();
            if (numberOfTracks > 1) {
                msg = String.format(Common.LOCALE, "strange: input MP4 file [%s] contains %d tracks", filename);
                Log.d(TAG, msg);
                return false;
            }

            int offset = 0;
            int sampleSize = 0;
            int trackIndex = 0; /* we know there is only one track. if needed we can loop over track indices */
            mediaExtractor.selectTrack(trackIndex);
            do {
                long sampleTime = mediaExtractor.getSampleTime();
                if (firstFrame) {
                    adjustTime = startTimeUs - sampleTime;
                    firstFrame = false;
                }
                sampleTime += adjustTime; /* tautological for first frame = actual begin time */
                int flags = mediaExtractor.getSampleFlags();
                sampleSize = mediaExtractor.readSampleData(byteBuffer, 0);
                if (sampleSize > 0) {
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    bufferInfo.flags = flags;
                    if (autoFrameTime) {
                        bufferInfo.presentationTimeUs = numberOfFrames * deltaFrameTimeUs;
                        ++numberOfFrames;
                    } else {
                        bufferInfo.presentationTimeUs = sampleTime;
                    }
                    bufferInfo.offset = 0;
                    bufferInfo.size = sampleSize;
                    totalOutputSize += sampleSize;
                    if (sampleTime >= startTimeUs) {
                        try {
                            mediaMuxer.writeSampleData(outputTrackIndex, byteBuffer, bufferInfo);
                        } catch (IllegalArgumentException ex) {
                            msg = String.format(Common.LOCALE, "IllegalArgumentException caught");
                            Log.e(TAG, msg, ex);
                            return false;
                        } catch (IllegalStateException ex) {
                            msg = String.format(Common.LOCALE, "IllegalStateException caught");
                            Log.e(TAG, msg, ex);
                            return false;
                        }
                    }

                    offset += sampleSize;

                    msg = String.format(Common.LOCALE, "offset = %d, sample size = %d", offset, sampleSize);
                    Log.d(TAG, msg);
                }
                mediaExtractor.advance();
            } while (sampleSize > 0);

            mediaExtractor.release();
        }

        msg = String.format(Common.LOCALE, "file [%s] created with filesize = %d", outputFilename, totalOutputSize);
        Log.d(TAG, msg);

        return false;
    }
}
