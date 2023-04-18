package com.worldline.valina;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;


//import com.worldline.valina.utils.MapsServices;
import com.worldline.spica.helpers.services.SpicaServiceConnectionCallback;
import com.worldline.spica.msmd.MsmdService;
import com.worldline.spica.msmd.MsmdServiceProvider;
import com.worldline.spica.util.AssetBridge;

import java.io.IOException;

/**
 * Created by A784253 on 15/10/2020.
 */
public class MyStartService extends Service {

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        try {
            AssetBridge.copyAssetFolder(getApplicationContext(), "usr");
            System.out.println("AssetFolder copied");
        } catch (IOException e) {
            Log.e("Service", "Could not copy asset folder", e);
        }




        MsmdServiceProvider mMsmdServiceProvider = new MsmdServiceProvider(this);
        mMsmdServiceProvider.connect(connectionCallback);
        return START_NOT_STICKY;
    }

    private final SpicaServiceConnectionCallback<MsmdService> connectionCallback = new SpicaServiceConnectionCallback<MsmdService>() {
        @Override
        public void onConnect(MsmdService service) {
//            MapsServices.create(getApplicationContext());
//            MapsServices.getInstance().init();

            Log.i("Service", "MSMD connected");
            Intent intentAct = new Intent(getApplicationContext(), MainActivity.class);
            intentAct.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intentAct);
        }

        @Override
        public void onDisconnect() {
            Log.i("Service", "MSMD disconnected");
        }
    };
}
