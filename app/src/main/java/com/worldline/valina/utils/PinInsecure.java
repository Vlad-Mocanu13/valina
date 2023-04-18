package com.worldline.valina.utils;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.worldline.valina.R;

public class PinInsecure extends AppCompatActivity {

    String pin="";
    TextView pinview;
    boolean iscancel=false;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pinnfconline);

        pinview=findViewById(R.id.pintextnfconline);

        findViewById(R.id.key0).setOnClickListener(new View.OnClickListener() {@Override public void onClick(View view) { onkeypress(0); }});
        findViewById(R.id.key1).setOnClickListener(new View.OnClickListener() {@Override public void onClick(View view) { onkeypress(1); }});
        findViewById(R.id.key2).setOnClickListener(new View.OnClickListener() {@Override public void onClick(View view) { onkeypress(2); }});
        findViewById(R.id.key3).setOnClickListener(new View.OnClickListener() {@Override public void onClick(View view) { onkeypress(3); }});
        findViewById(R.id.key4).setOnClickListener(new View.OnClickListener() {@Override public void onClick(View view) { onkeypress(4); }});
        findViewById(R.id.key5).setOnClickListener(new View.OnClickListener() {@Override public void onClick(View view) { onkeypress(5); }});
        findViewById(R.id.key6).setOnClickListener(new View.OnClickListener() {@Override public void onClick(View view) { onkeypress(6); }});
        findViewById(R.id.key7).setOnClickListener(new View.OnClickListener() {@Override public void onClick(View view) { onkeypress(7); }});
        findViewById(R.id.key8).setOnClickListener(new View.OnClickListener() {@Override public void onClick(View view) { onkeypress(8); }});
        findViewById(R.id.key9).setOnClickListener(new View.OnClickListener() {@Override public void onClick(View view) { onkeypress(9); }});
        findViewById(R.id.keydelete).setOnClickListener(new View.OnClickListener() {@Override public void onClick(View view) { onkeypress(-1); }});
        findViewById(R.id.keycancel).setOnClickListener(new View.OnClickListener() {@Override public void onClick(View view) { onkeypress(-2); }});
        findViewById(R.id.keyok).setOnClickListener(new View.OnClickListener() {@Override public void onClick(View view) { onkeypress(-3); }});

        CountDownTimer countDownTimer = new CountDownTimer(5000, 1000) {
            public void onTick(long l) {

            }
            public void onFinish() {

                finish();
            }
        };
    }

    void onkeypress(int key)
        {   if(key>-1&&key<10){if(pin.length()<17){pin+=key+"";pinview.append("*");}}
            else if(key==-1){if(pin.length()>0){pin=pin.substring(0,pin.length()-2);pinview.setText(pinview.getText().toString().substring(1));}}
            else if(key==-2){   iscancel=true;
                                finish("");}//cancel
            else if(key==-3){   finish("");}//ok
        }


    //@Override
    public void finish(String test) {
        int PIN_NFC_ONLINE_RESULT = 11;
        Intent resultPin = new Intent();
        resultPin.putExtra("pin",pin);
        resultPin.putExtra("iscancel",iscancel);
        setResult(PIN_NFC_ONLINE_RESULT, resultPin);
        super.finish();
    }
}
