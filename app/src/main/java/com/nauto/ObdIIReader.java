package com.nauto;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import com.nauto.modules.obd.ObdModule;

/**
 * Created by jsvirzi on 2/10/17.
 */

public class ObdIIReader extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = getApplicationContext();
        ObdModule obdModule = new ObdModule(context);
        obdModule.connect();

    }
}
