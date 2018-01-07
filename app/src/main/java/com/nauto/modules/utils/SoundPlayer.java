package com.nauto.modules.utils;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.HandlerThread;

import com.nauto.R;

import java.io.InputStream;

/**
 * Created by jsvirzi on 2/25/17.
 */

public class SoundPlayer implements AudioTrack.OnPlaybackPositionUpdateListener {
    private String soundFile;
    private int numberOfStages;
    private short[][] soundData;
    private AudioTrack[] audioTracks;
    private int[] audioLength;
    private boolean audioBusy;
    private long lastPlayTimeout;
    private HandlerThread thread;
    private Handler handler;
    private int stage;
    private static final int DefaultNumberOfStages = 4;
    private static SoundPlayer instance;

    public static SoundPlayer getInstance(Context context) {
        if(instance == null) {
            instance = new SoundPlayer(context);
        }
        return instance;
    }

    public static SoundPlayer getInstance() {
        return instance;
    }

    private SoundPlayer(Context context) {
        this(context, DefaultNumberOfStages);
    }

    private SoundPlayer(Context context, int numberOfStages) {
        InputStream inputStream = context.getResources().openRawResource(R.raw.beep);
        final long now = System.currentTimeMillis();
        lastPlayTimeout = now + 100000000; /* me no love you long time */
        int wavHeaderOffset = 46; /* TODO could be 44 */
        wavHeaderOffset = 23; /* TODO could be 22 = 44/2 */
        int sampleRate = 44100;
        soundData = new short[numberOfStages][];
        audioLength = new int [numberOfStages];
        audioTracks = new AudioTrack[numberOfStages];
        soundData[0] = Utils.readStreamShorts(inputStream, wavHeaderOffset);
        int gapLength = sampleRate / 10;
        for (int i = 1; i < numberOfStages; i++) {
            soundData[i] = Utils.duplicateShortArray(soundData[0], i+1, gapLength);
            gapLength = gapLength / 2; /* shortens each time */
        }
        audioBusy = false;
        for (int i = 0; i < numberOfStages; i++) {
            audioLength[i] = soundData[i].length;
            audioTracks[i] = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                2 * audioLength[i], AudioTrack.MODE_STATIC);
            audioTracks[i].write(soundData[i], 0, audioLength[i]);
            float volume = audioTracks[i].getMaxVolume();
            volume = volume / 3.0f + 2.0f / 3.0f * i * volume / numberOfStages;
            audioTracks[i].setVolume(volume);
            audioTracks[i].setPlaybackPositionUpdateListener(this);
            audioTracks[i].setNotificationMarkerPosition(audioLength[i]);
        }

        thread = new HandlerThread("SoundPlayer");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    /* TODO deprecate */
    public SoundPlayer(int numberOfStages, String soundFile) {
        this.soundFile = soundFile;
        this.numberOfStages = numberOfStages;
        soundData = new short[numberOfStages][];
        audioTracks = new AudioTrack[numberOfStages];
        audioLength = new int[numberOfStages];
        audioBusy = false;
        final long now = System.currentTimeMillis();
        lastPlayTimeout = now + 100000000; /* me no love you long time */
        int wavHeaderOffset = 46; /* TODO could be 44 */
        wavHeaderOffset = 23; /* TODO could be 22 = 44/2 */
        soundData[0] = Utils.readFileShorts(soundFile, wavHeaderOffset);
        soundData[1] = Utils.duplicateShortArray(soundData[0], 2, 4410);
        soundData[2] = Utils.duplicateShortArray(soundData[0], 3, 2205);
        soundData[3] = Utils.duplicateShortArray(soundData[0], 4, 1100);
        audioBusy = false;
        int sampleRate = 44100;
        for (int i = 0; i < numberOfStages; i++) {
            audioLength[i] = soundData[i].length;
            audioTracks[i] = new  AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                2 * audioLength[i], AudioTrack.MODE_STATIC);
            audioTracks[i].write(soundData[i], 0, audioLength[i]);
            float volume = audioTracks[i].getMaxVolume();
            volume = volume / 3.0f + 2.0f / 3.0f * i * volume / numberOfStages;
            audioTracks[i].setVolume(volume);
            audioTracks[i].setPlaybackPositionUpdateListener(this);
            audioTracks[i].setNotificationMarkerPosition(audioLength[i]);
        }

        thread = new HandlerThread("SoundPlayer");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public void destroy() {
        Utils.goodbyeThread(thread);
    }

    private Runnable playAlertRunnable = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (now > lastPlayTimeout) {
                audioBusy = false; /* reset */
            }
            AudioTrack audioTrack = audioTracks[stage];
            if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                return;
            }
            lastPlayTimeout = now + 1000;
            audioTrack.reloadStaticData();
            audioTrack.play();
        }
    };

    public void killAlerts() {
        handler.removeCallbacks(playAlertRunnable);
    }

    public void playAlert(int stage) {
        this.stage = stage;
        handler.post(playAlertRunnable);
    }

    @Override
    public void onPeriodicNotification(AudioTrack audioTrack) {
    }

    @Override
    public void onMarkerReached(AudioTrack audioTrack) {
        audioTrack.stop();
    }
}
