package com.worldline.valina.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.renderscript.RSDriverException;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.TextView;

import com.worldline.valina.R;
import com.worldline.spica.helpers.services.SpicaServiceConnectionCallbackAdapter;
import com.worldline.spica.secure.PinEntryConfig;
import com.worldline.spica.secure.PinEntryEvent;
import com.worldline.spica.secure.PinSecureService;
import com.worldline.spica.secure.PinSecureServiceProvider;
import com.worldline.spica.secure.pinpad.VirtualKeyAttributes;
import com.worldline.spica.secure.pinpad.VirtualKeyPad;
import com.worldline.spica.secure.pinpad.VirtualPinPadKey;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import be.banksys.maps.CbGetBytesCompleted;
import be.banksys.maps.CbOpenCompleted;
import be.banksys.maps.MapsRuntime;
import be.banksys.maps.Request;
import be.banksys.mapsx.macq.CbCryptoData;
import be.banksys.mapsx.macq.Macq;


public class PinActivity extends AppCompatActivity {

    private static final String TAG = "PinActivity";
    public static final String EXTRA_MESSAGE = "com.worldline.sample.pinentrysample.MESSAGE";
    public static final String ACTION_PIN = "com.worldline.sample.pinentrysample.ACTION_PIN";

    private PinSecureServiceProvider mPinSecureServiceProvider;
    private PinSecureService mPinSecureService;
    private PinEntryEvent mPinEntryEvent;
    private PinEntryConfig mPinEntryConfig;
    private Map<VirtualPinPadKey, Button> mPinPadKeyButtonMap = new HashMap<>();
    private VirtualKeyPad mVirtualKeyPad = new VirtualKeyPad();
    CountDownTimer countDownTimer;
    public String pan="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);
        Log.d(TAG, "onCreate");



        countDownTimer = new CountDownTimer(5000, 1000) {
            public void onTick(long millisUntilFinished) {
                // TO DO: Do something every second
            }

            public void onFinish() {
                // when the timer expires, return to the standby screen
                finish();
            }
        };

        final TextView pinStars = (TextView) findViewById(R.id.pintext);


        mPinSecureServiceProvider = new PinSecureServiceProvider(getApplicationContext());
        mPinSecureServiceProvider.connect(new SpicaServiceConnectionCallbackAdapter<PinSecureService>() {
            @Override
            public void onConnect(PinSecureService pinService) {
                Log.d(TAG, "onConnect");
                mPinSecureService = (PinSecureService) pinService;


                final View pinCode = findViewById(R.id.keypadLayout);
                pinCode.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        storePinButtonCoordinates();
                        mPinEntryConfig = new PinEntryConfig.Builder()
                                .withPinMaximumLength(5)
                                .withPinMinimumLength(4)
                                .withVirtualKeyPad(mVirtualKeyPad)
                                .withReturnOnEmptyCorr(true)
                                .build();

                        mPinEntryEvent = new PinEntryEvent() {
                            @Override
                            public void onKeyPressOk(boolean b) {
                                Log.d(TAG, "onKeyPressOk");

                                terminatePinEntry();
                                Intent pinIntent = new Intent();
                                pinIntent.setAction(ACTION_PIN);
                                pinIntent.putExtra(EXTRA_MESSAGE, "PIN entered");

                                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(pinIntent);

                                finish("PIN entered");

                            }

                            @Override
                            public void onKeyPressStop() {
                                playKeyPressedSound(PinActivity.this);
                                terminatePinEntry();
                                finish();

                            }

                            @Override
                            public void onKeyPressCorrection(int deletionMethod, boolean returnOnEmpty) {
                                playKeyPressedSound(PinActivity.this);
                                if (returnOnEmpty) {
                                    terminatePinEntry();
                                } else if (pinStars.getText().length() != 0) {
                                    switch (deletionMethod) {
                                        case 0:
                                            removeAllEntries(pinStars);
                                            break;
                                        case 16:
                                        default:
                                            removePinEntry(pinStars);
                                            break;
                                    }
                                }

                            }

                            @Override
                            public void onKeyPressNumericKey() {
                                playKeyPressedSound(PinActivity.this);
                                final String pinCode = new StringBuilder().append(pinStars.getText()).append("*").toString();
                                pinStars.setText(pinCode);

                            }

                            public void onKeyLongPress(int i) {

                            }

                            @Override
                            public void onTimeOutEvent(int a) {
                                playKeyPressedSound(PinActivity.this);
                                Log.i(TAG, "inside onTimeOutEvent "+a);
                                terminatePinEntry();
                            }
                        };

                        try {
                            mPinSecureService.startPinEntry(mPinEntryConfig, mPinEntryEvent);
                        } catch (RSDriverException e) {
                            Log.e(TAG, "onPreDraw: ", e);
                        }

                        // Remove observer (the coordinates will always be the same.
                        pinCode.getViewTreeObserver().removeOnPreDrawListener(this);
                        return true;
                    }
                });
            }
        });



    }

    private void removeAllEntries(TextView textView) {
        while (textView.getText().length() > 0) {
            removePinEntry(textView);
        }
    }

    private void removePinEntry(TextView textView) {
        String pinCode = new StringBuilder().append(textView.getText().toString().substring(1)).toString();
        textView.setText(pinCode);
    }

    private void storePinButtonCoordinates() {
        mPinPadKeyButtonMap.put(VirtualPinPadKey.NUMBER_0, (Button) findViewById(R.id.keyPadButton0));
        mPinPadKeyButtonMap.put(VirtualPinPadKey.NUMBER_1, (Button) findViewById(R.id.keyPadButton1));
        mPinPadKeyButtonMap.put(VirtualPinPadKey.NUMBER_2, (Button) findViewById(R.id.keyPadButton2));
        mPinPadKeyButtonMap.put(VirtualPinPadKey.NUMBER_3, (Button) findViewById(R.id.keyPadButton3));
        mPinPadKeyButtonMap.put(VirtualPinPadKey.NUMBER_4, (Button) findViewById(R.id.keyPadButton4));
        mPinPadKeyButtonMap.put(VirtualPinPadKey.NUMBER_5, (Button) findViewById(R.id.keyPadButton5));
        mPinPadKeyButtonMap.put(VirtualPinPadKey.NUMBER_6, (Button) findViewById(R.id.keyPadButton6));
        mPinPadKeyButtonMap.put(VirtualPinPadKey.NUMBER_7, (Button) findViewById(R.id.keyPadButton7));
        mPinPadKeyButtonMap.put(VirtualPinPadKey.NUMBER_8, (Button) findViewById(R.id.keyPadButton8));
        mPinPadKeyButtonMap.put(VirtualPinPadKey.NUMBER_9, (Button) findViewById(R.id.keyPadButton9));
        mPinPadKeyButtonMap.put(VirtualPinPadKey.BACKSPACE, (Button) findViewById(R.id.keyPadButtonBackspace));
        mPinPadKeyButtonMap.put(VirtualPinPadKey.CANCEL, (Button) findViewById(R.id.keyPadButtonStop));
        mPinPadKeyButtonMap.put(VirtualPinPadKey.ENTER, (Button) findViewById(R.id.keyPadButtonOk));

        for (Map.Entry<VirtualPinPadKey, Button> entry : mPinPadKeyButtonMap.entrySet()) {
            int[] locationOnScreen = new int[2];
            Rect hitRect = new Rect();
            // Retrieve the view
            View button = entry.getValue();
            // Get its location and dimension
            button.getLocationOnScreen(locationOnScreen);
            button.getHitRect(hitRect);
            // Store the values in the virtualKeyPad
            VirtualKeyAttributes virtualKeyAttributes = new VirtualKeyAttributes(locationOnScreen[0], locationOnScreen[1], hitRect.width(), hitRect.height());
            mVirtualKeyPad.addVirtualKey(entry.getKey(), virtualKeyAttributes);
        }
    }

    private static void playKeyPressedSound(Context context) {
        //if (BuildConfig.BUILD_TYPE.equals("autotest")) {
        Log.d(TAG, "playKeyPressedSound");
        //} else {
        //    Log.d(TAG, "Not playKeyPressedSound");
        //Utils.playSound(MediaPlayer.create(context, R.raw.beep_100_0_1));
        //}
    }


    private void terminatePinEntry() {
        //(byte)0x30  or  (byte)0x00

        disconnectPinSecureService();
        countDownTimer.start();
        //ExtractPin((byte)0x30,"4493910007550995D2406201000089510000");
    }

    protected void disconnectPinSecureService() {
        if (mPinSecureServiceProvider != null) {
            if (mPinSecureService != null) {
                try {
                    mPinSecureService.stopPinEntry();
                    mPinSecureService = null;
                } catch (RSDriverException e) {
                    Log.e(TAG, "disconnectPinSecureService: ", e);
                }
            }
            // Close service
            mPinSecureServiceProvider.close();
            mPinSecureServiceProvider = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectPinSecureService();
    }

    @Override
    public void finish() {//FINISH CANCEL
        int PIN_RESULT_CODE = 0;
        Intent resultPin = new Intent();
        resultPin.putExtra("status","cancel");
        resultPin.putExtra("iscancel",true);
        setResult(PIN_RESULT_CODE, resultPin);

        super.finish();
    }

    public void finish(String status) {//FINISH OK
        int PIN_RESULT_CODE = 0;
        Intent resultPin = new Intent();
        resultPin.putExtra("status",status);
        resultPin.putExtra("iscancel",false);
        Log.d("staaaatus",status);
        setResult(PIN_RESULT_CODE, resultPin);
        super.finish();
    }

}
