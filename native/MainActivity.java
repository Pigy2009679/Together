package com.milk.backupgallery;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MediaGalleryPlugin.class);
        super.onCreate(savedInstanceState);
    }
}