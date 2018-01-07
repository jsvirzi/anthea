package com.mam.lambo.modules.uploader;

/**
 * Created by jsvirzi on 1/27/17.
 */

public class Crc32 {

    private static final int polynomial = 0xEDB88320;
    private int[] lookupTable = new int[256];
    private static Crc32 instance;

    public static Crc32 getInstance() {
        if (instance == null) {
            instance = new Crc32();
        }
        return instance;
    }

    private Crc32() {
        for (int i = 0; i <= 0xFF; i++) {
            int crc = i;
            for (int j = 0; j < 8; j++) {
                crc = (crc >> 1) ^ (-(crc & 1) & polynomial);
            }
            lookupTable[i] = crc;
        }
    }

    public int compute(byte[] data, int length, int previousCrc32) {
        int crc = ~previousCrc32;
        for (int i = 0; i < length; i++) {
            int index = (crc & 0xff) ^ data[i];
            if (index < 0) {
                index += 256;
            }
            crc = (crc >> 8) ^ lookupTable[index];
        }
        return ~crc;
    }
}
