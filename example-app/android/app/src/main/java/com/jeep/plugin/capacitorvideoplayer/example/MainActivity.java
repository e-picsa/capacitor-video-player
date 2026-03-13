package com.jeep.plugin.capacitorvideoplayer.example;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.google.android.gms.cast.framework.CastContext;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Google Cast framework for Chromecast support
        CastContext.getSharedInstance(this);
    }
}
