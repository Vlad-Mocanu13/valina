package com.worldline.valina;

import android.app.Application;

import be.banksys.maps.sync.EventLoop;

/**
 * Created by A784253 on 15/10/2020.
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        EventLoop.start();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        EventLoop.stop();
    }
}
