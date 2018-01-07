package com.mam.lambo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by jsvirzi on 1/16/17.
 */

public class ExternalIntentProcessor extends BroadcastReceiver {
    private static final String TAG = "ExternalIntentProcessor";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == "com.mam.lambo.dogfood.stop") {
            Log.d(TAG, "received intent: com.mam.lambo.dogfood.stop");
            DogFood instance = DogFood.dogFood;
            if (instance != null) {
                instance.onDestroy();
            }
        } else if (intent.getAction() == "com.mam.lambo.imulogger.stop") {
            Log.d(TAG, "received intent: com.mam.lambo.imulogger.stop");
            ImuLogger instance = ImuLogger.imuLogger;
            if (instance != null) {
                instance.onDestroy();
            }
        }
    }
}
