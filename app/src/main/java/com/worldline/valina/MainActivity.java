package com.worldline.valina;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.Settings;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.worldline.valina.utils.BankModule;
import com.worldline.valina.utils.EmvModule;
import com.worldline.valina.utils.NfcModule;
import com.worldline.valina.utils.PinActivity;
import com.worldline.valina.utils.PinExtractor;
import com.worldline.valina.utils.TlvUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import be.banksys.maps.CbOpenCompleted;
import be.banksys.maps.CbOperationCompleted;
import be.banksys.maps.MapsRuntime;
import be.banksys.maps.Request;
import be.banksys.mapsx.generic.GenericSecureService;

/**
 * Created by A784253 on 15/10/2020.
 */
public class MainActivity extends AppCompatActivity {

    String socketMessage = null;
    String socketString = null;
    BankModule bankModule;
    NfcModule nfcModule;
    EmvModule emvModule;
    PinExtractor pinExtractor;
    //boolean paymentisnfc=true;

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    boolean settingsvisible = false;

    ConstraintLayout screensettlement, screentrans;
    Button btnsettlback, btntransok, btntranscancel, btnsettlement, btndelbatch;
    TextView txtsettl, txttrans, transactionText;
    ImageView imgtrans;
    CheckBox checkboxonline;
    BankModule.Questionnaire activequestions = null;
    Button socketbtn;

    Runnable conn = new Runnable() {
        @Override
        public void run() {
            startSocket(6666);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        runOnMapsThread(new Runnable() {
            @Override
            public void run() {
                loadCardTable();
            }
        });
        //Screen height:480  width:320
        InitBankConnector();
        InitEMV();
        InitNFC();
        InitScreens();
        InitPinExtractor();
        setTransition("init", "", 0);
        final Intent intent = new Intent(Settings.ACTION_SETTINGS);
        socketbtn = findViewById(R.id.socketbtn);
        transactionText = findViewById(R.id.transaction_text);
        socketbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(intent);
            }
        });

        new Thread(conn).start();


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String TAG = "On activity result.";
        Log.i(TAG, "inside onActivityResult " + requestCode + " " + resultCode);
        int PIN_REQUEST_CODE = 1;
        int PIN_NFC_ONLINE_REQUEST = 10;//,PIN_NFC_ONLINE_RESPONSE=11;
        int PIN_RESULT_CODE = 0;
        if (requestCode == PIN_REQUEST_CODE) {
            Log.i(TAG, "inside onActivityResult requestCode = PIN_REQUEST_CODE");
            if (resultCode == PIN_RESULT_CODE) {
                Log.i(TAG, "inside onActivityResult resultCode = PIN_RESULT_CODE");
                if (data != null) {
                    Log.i(TAG, "inside onActivityResult data != null");
                    emvModule.OnPinEntered(data.getStringExtra("status"));
                } else {
                    Log.i(TAG, "inside onActivityResult data = null");
                }
            }
        } else if (requestCode == PIN_NFC_ONLINE_REQUEST)//result code is 0 even if it is set......
        {
            if (data.getBooleanExtra("iscancel", false)) {
                nfcModule.OnPinEntered("", true, false, "");
            } else {
                pinExtractor.ExtractPin((byte) 0x30, nfcModule.getChachedPAN(), true);
            }
        }

    }


    void OnTransactionResult(boolean issuccess, boolean isemv, boolean isonline, String pan, String expdate, String cryptogram, String currencycode, String tlv, String track2, String amount, String pinblock, String message) {
        if (!issuccess) {
            setTransition("failedtrans", message, 0);
            socketString = "Tranzactie esuata";

            runOnUiThread(new Runnable() {
                public void run() {
                    updateTextUI(socketString);
                }
            });
        }

        Log.e("tags", "isemv:" + isemv + " ,  online:" + isonline + ",   pan:" + pan + ", expdate:" + expdate + ", cryptogram:" + cryptogram + ", currencyCode:" + currencycode + ", tlv:" + tlv + ", track2:" + track2 + ",   amount:" + amount + ",  pinblock:" + pinblock);
        if (pan.length() < 10 && expdate.length() < 4 && track2.length() < 14) {
            Log.e("FATAL ERR", "ERROR:Missing pan expdate and track2data.");
            setTransition("failedtrans", "Lipseste PAN-ul, expdate si tranck2data din mesaj.", 0);
            socketString = "Tranzactie esuata, Lipsesc PAN-ul, expdate si tranck2data";
            runOnUiThread(new Runnable() {
                public void run() {
                    updateTextUI(socketString);
                }
            });
            return;
        }
        if (pan.length() < 10 || expdate.length() < 4) {
            String[] lines = track2.split("d");
            if (lines.length < 2) {
                Log.e("FATAL ERR", "Unable to extract pan/expdate from track2data.");
                setTransition("failedtrans", "Nu s-a putut sxtrage Pan-ul si exp date din track2data.", 0);
                socketString = "Tranzactie esuata, Nu s-a putut sxtrage Pan-ul si exp date din track2data.";

                runOnUiThread(new Runnable() {
                    public void run() {
                        updateTextUI(socketString);
                    }
                });
                return;
            }
            String newpan = lines[0];
            String newexpdate = lines[1].substring(0, 6);
            Log.e("Pan/ExpDate from track2", "pan:" + pan + ",  expdate:" + expdate + ", track2:" + track2 + ", new pan:" + newpan + ", new expdate:" + newexpdate + ",");
            pan = newpan;
            expdate = newexpdate;

        }
        if (!checkboxonline.isChecked()) {
            setTransition("transsuccess", message, 0);//Tranzactia a fost aprobata! (Dummy mode)Puteti ridica produsul.
            socketString = "Tranzactia a fost aprobata! (Dummy mode)Puteti ridica produsul.";

            runOnUiThread(new Runnable() {
                public void run() {
                    updateTextUI(socketString);
                }
            });
            return;
        }

        setTransition("transmessage", "imgconnect", 0);
        if (isonline) {
            setTransition("transmessage", "imgconnect", 0);
            bankModule.DoOnlineTransaction(pan, expdate, amount, currencycode, track2, tlv, cryptogram, pinblock);
        } else {
            setTransition("transsuccess", message, 0);
            socketString = "Tranzactia a fost aprobata! (Dummy mode)Puteti ridica produsul.";

            runOnUiThread(new Runnable() {
                public void run() {
                    updateTextUI(socketString);
                }
            });
            bankModule.DoOfflineTransaction(pan, expdate, amount, currencycode, track2, tlv);
        }
    }

    //
    void InitScreens() {
        btnsettlement = findViewById(R.id.btnsettlement);
        btnsettlement.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setTransition("startsettlement", "", 0);
            }
        });
        btndelbatch = findViewById(R.id.btndelbatch);
        btndelbatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        bankModule.DeleteBatch();
                    }
                });
            }
        });
        checkboxonline = findViewById(R.id.checkboxonline);
        findViewById(R.id.btnsettings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        settingsvisible = !settingsvisible;
                        btnsettlement.setVisibility(settingsvisible ? View.VISIBLE : View.INVISIBLE);
                        checkboxonline.setVisibility(settingsvisible ? View.VISIBLE : View.INVISIBLE);
                        btndelbatch.setVisibility(settingsvisible ? View.VISIBLE : View.INVISIBLE);
                    }
                });
            }
        });

        runOnUiThread(new Runnable() {
            public void run() {
                btnsettlement.setVisibility(View.INVISIBLE);
                checkboxonline.setVisibility(View.INVISIBLE);
                btndelbatch.setVisibility(View.INVISIBLE);
            }
        });

        //=====SETTLEMENT
        screensettlement = findViewById(R.id.screensettlement);
        txtsettl = findViewById(R.id.txtsettl);
        btnsettlback = findViewById(R.id.btnsettlback);
        btnsettlback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setTransition("endsettlement", "", 0);
            }
        });

        //=====TRANSACTION
        screentrans = findViewById(R.id.screentransaction);
        imgtrans = findViewById(R.id.imgtrans);
        txttrans = findViewById(R.id.txttrans);
        btntransok = findViewById(R.id.btntransok);
        btntransok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setTransition("returntoitems", "", 0);
            }
        });
        btntranscancel = findViewById(R.id.btntranscancel);
        btntranscancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setTransition("canceltrans", "", 0);
            }
        });
    }


    void InitBankConnector() {
        SSLContext sslcontext = null;
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream serverInput = getResources().openRawResource(R.raw.ca);
            Certificate server;
            try {
                server = cf.generateCertificate(serverInput);
                Log.wtf("tag", ((X509Certificate) server).getSubjectDN().toString());
            } finally {
                serverInput.close();
            }
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            //Load client1.p12 which contains the client certificate and private key
            InputStream pkcs12in = getResources().openRawResource(R.raw.client1);
            try {
                keyStore.load(pkcs12in, "client1".toCharArray());
            } finally {
                pkcs12in.close();
            }
            //Set CA to keystore
            keyStore.setCertificateEntry("server", server);
            //Create a TrustManager that trusts the CAs in our KeyStore
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(keyStore);
            //Build a KeyManager for Client auth
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, null);
            sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (IOException | CertificateException | NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException | KeyManagementException e) {
            e.printStackTrace();
            //logtxt.append("Eroare la initializarea cheilor ssl:"+e.getMessage());
            Toast.makeText(getApplicationContext(), "Eroare la initializarea cheilor ssl:" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        bankModule = new BankModule(sslcontext) {
            @Override
            public void OnOnlineProcessResult(boolean status, String responseData) {   //final boolean issuccessfull=responseData.endsWith("30");
                Log.d("Trans Result", status ? "SUCCESSFULL TRANSACTION" : "failed transaction");

                //runOnUiThread(new Runnable() {
                //    public void run() {logtxt.append("\n"+(isssuccessfull?"800 OK 810 OK 200 OK 210 OK\nSUCCESSFULL TRANSACTION":"failed transaction"));}});
                setTransition(status ? "transsuccess" : "failedtrans",
                        status ? "" : responseData, 0);
            }

            @Override
            public void OnPrint(final String message) {
                Log.d("To Print", message);
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                    }
                });
                //runOnUiThread(new Runnable() {public void run() {logtxt.append("\n"+message);}});
            }

            @Override
            public void OnSettlementEvent(final boolean isresult, final String message) {   //Log.d("On settlement",message);
                setTransition(isresult ? "resultsettlement" : "settlementmessage", message, 0);
            }

            @Override
            public void OnQuestionnaire(final BankModule.Questionnaire q) {
                Log.d("New OnQuestionnaire", "");
                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        activequestions = q;
                        for (int i = 0; i < activequestions.questions.length; i++) {
                            if (activequestions.questions[i].type.equals("M")) {
                                Log.w("QUEST", "Created question " + i + "    length:" + activequestions.questions[i].options.length);
                                String[] items = activequestions.questions[i].options;
                                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                builder.setTitle(activequestions.questions[i].question);
                                builder.setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                    }
                                });
                                final int questionid = i;
                                builder.setNegativeButton("OK", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                                        if (selectedPosition > -1) {   //Log.w("selected pressed OK  ","position:"+selectedPosition+" questionid:"+questionid);
                                            activequestions.questions[questionid].response =
                                                    activequestions.questions[questionid].optioncodes[selectedPosition];
                                            activequestions.questions[questionid].ready = true;
                                            boolean ready = true;
                                            for (int k = 0; k < activequestions.questions.length; k++)
                                                if (!activequestions.questions[k].ready) {
                                                    ready = false;
                                                    break;
                                                }
                                            if (ready) {
                                                bankModule.OnFiniShedQuestionnaire(activequestions);
                                                activequestions = null;
                                            }
                                            dialog.dismiss();
                                        }
                                    }
                                });
                                Dialog dialog = builder.create();
                                dialog.setCanceledOnTouchOutside(false);
                                dialog.show();
                                dialog.getWindow().setLayout(320, 480);
                            } else if (activequestions.questions[i].type.equals("T")) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                builder.setTitle(activequestions.questions[i].question);
                                final EditText input = new EditText(MainActivity.this);
                                input.setInputType(InputType.TYPE_CLASS_TEXT);
                                builder.setView(input);
                                final int questionid = i;
                                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        String text = input.getText().toString();
                                        Log.w("selected pressed OK  ", "text:" + text + " questionid:" + questionid);
                                        activequestions.questions[questionid].response = text;
                                        activequestions.questions[questionid].ready = true;
                                        boolean ready = true;
                                        for (int k = 0; k < activequestions.questions.length; k++)
                                            if (!activequestions.questions[k].ready) {
                                                ready = false;
                                                break;
                                            }
                                        if (ready) {
                                            bankModule.OnFiniShedQuestionnaire(activequestions);
                                            activequestions = null;
                                        }
                                    }

                                    ;

                                });
                                Dialog dialog2 = builder.create();
                                dialog2.setCanceledOnTouchOutside(false);
                                dialog2.show();
                                dialog2.getWindow().setLayout(320, 480);
                            } else
                                Log.e("Eroare chestionar", "Nu exista tipul de chestionar " + activequestions.questions[i].type);
                        }
                    }
                });


                //runOnUiThread(new Runnable() {public void run() {logtxt.append("\nNew questionaiire");}});
            }

            @Override
            public void OnPopup(final String text) {
                Log.d("New Poppup", text);
                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                        alertDialog.setTitle("");
                        alertDialog.setMessage(text);
                        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        alertDialog.show();
                        alertDialog.getWindow().setLayout(320, 480);
                    }
                });
            }

            @Override
            public void OnQR(final String[] qrlines) {
                Log.d("New QR", qrlines[0]);
                //cod,titlu,qr,text,    cod si qr sunt 0 daca nu s-a primit un voucher valid
                if (qrlines[0].length() == 0 || qrlines[2].length() == 0) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Nu mai sunt Vouchere disponibile. :(", Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
                final String title = qrlines[1].length() > 0 ? qrlines[1] : "Titlu";
                final String qrdata = qrlines[2].length() > 0 ? qrlines[2] : "Cod QR invalid";
                final String content = qrlines[3].length() > 0 ? qrlines[3] : "Titlu";
                Log.d("test", "[" + qrlines[0] + "] [" + qrlines[1] + "] [" + qrlines[2] + "]");
                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
                            BitMatrix bitMatrix = multiFormatWriter.encode(qrdata, BarcodeFormat.QR_CODE, 220, 220);
                            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                            Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
                            AlertDialog builder = new AlertDialog.Builder(MainActivity.this, R.style.MyAlertDialogStyle).create();
                            builder.show();
                            ImageView imageView = new ImageView(MainActivity.this);
                            imageView.setImageBitmap(bitmap);
                            builder.addContentView(imageView, new RelativeLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.MarginLayoutParams.WRAP_CONTENT));
                            TextView text = new TextView(MainActivity.this);
                            text.setText("\n\n\n\n\n\n\n\n\n\n\n\n  " + title + "\n\n" + content + "\n\nApasati in afara pentru a inchide aceasta fereastra.\n");
                            builder.addContentView(text, new RelativeLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                            builder.getWindow().setLayout(320, 380);
                        } catch (WriterException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        };
    }

    void InitEMV() {
        final Context context = this.getApplicationContext();
        emvModule = new EmvModule() {
            @Override
            public void OnPinRequest() {
                Intent pinIntent = new Intent(context, PinActivity.class);
                final int PIN_REQUEST_CODE = 1;
                startActivityForResult(pinIntent, PIN_REQUEST_CODE);
            }

            @Override
            public void OnTransactionMessage(String message) {
                setTransition("transmessage", message, 0);
            }

            @Override
            public void OnTransactionResultEMV(boolean issuccessful, boolean isonline, String message, String pan, String expdate, String appcr, String currencycode, String tlv, String track2, String amount, String pinblock) {
                OnTransactionResult(issuccessful, true, isonline, pan, expdate, appcr, currencycode, "", track2, amount, pinblock, message);
            }

            @Override
            public void EncryptOnlinePin(String pan) {
                pinExtractor.ExtractPin((byte) 0x30, pan, false);

            }
        };

    }

    void InitNFC() {
        final Context context = this.getApplicationContext();
        nfcModule = new NfcModule() {   //tap->onresult
            //tap->OnOnlinePinRequest->nfc.onONlinePin
            @Override
            public void OnTransactionMessage(String message) {
                setTransition("transmessage", message, 0);
            }

            @Override
            public void OnTransactionResultNFC(boolean issuccessful, boolean isonline, String message, String pan, String expdate, String appcr, String currencycode, String tlv, String track2, String amount, String pinblock) {
                OnTransactionResult(issuccessful, false, isonline, pan, expdate, appcr, currencycode, "", track2, amount, pinblock, message);
            }

            @Override
            public void OnOnlinePinRequest() {
                Intent pinIntent = new Intent(context, PinActivity.class);
                final int PIN_NFC_ONLINE_REQUEST = 10;
                startActivityForResult(pinIntent, PIN_NFC_ONLINE_REQUEST);
            }
        };
    }

    void InitPinExtractor() {
        pinExtractor = new PinExtractor() {
            @Override
            public void OnPinFailed(String reason, boolean isnfc) {
                Log.e("ONPINFAILED", reason);
                if (isnfc) nfcModule.OnPinEntered("", false, false, reason);
                else emvModule.OnPinEncrypted(null, false, reason);
            }

            @Override
            public void OnPinReceived(byte[] baPinBlock, boolean isnfc) {
                if (isnfc)
                    nfcModule.OnPinEntered(TlvUtils.buffertostring(baPinBlock, ""), false, true, "");
                else emvModule.OnPinEncrypted(TlvUtils.buffertostring(baPinBlock, ""), true, "");
            }
        };
    }

    String currentstate = "none";

    void setTransition(String type, final String message, final int price)    // FSM
    {
        if (type.equals("init")) {
            runOnUiThread(new Runnable() {
                public void run() {
                    screensettlement.setVisibility(View.INVISIBLE);
                    screentrans.setVisibility(View.INVISIBLE);
                }
            });
        }
        //items->settlement->settlmentresult->items
        else if (type.equals("startsettlement")) {
            runOnUiThread(new Runnable() {
                public void run() {
                    screensettlement.setVisibility(View.VISIBLE);
                    screentrans.setVisibility(View.INVISIBLE);
                    btnsettlback.setVisibility(View.INVISIBLE);
                    txtsettl.setText("Initiere Settlement.");
                }
            });
            currentstate = "settlement";
            bankModule.DoSettlement();
        } else if (type.equals("settlementmessage")) {
            runOnUiThread(new Runnable() {
                public void run() {
                    txtsettl.append("\n" + message);
                    screensettlement.setVisibility(View.VISIBLE);
                    screentrans.setVisibility(View.INVISIBLE);
                    btnsettlback.setVisibility(View.INVISIBLE);
                }
            });
            currentstate = "settlement";
        } else if (type.equals("resultsettlement")) {
            runOnUiThread(new Runnable() {
                public void run() {
                    txtsettl.append("\n" + message);
                    screensettlement.setVisibility(View.VISIBLE);
                    screentrans.setVisibility(View.INVISIBLE);
                    btnsettlback.setVisibility(View.VISIBLE);
                }
            });
            currentstate = "settlementresult";
        } else if (type.equals("endsettlement")) {
            runOnUiThread(new Runnable() {
                public void run() {
                    screensettlement.setVisibility(View.INVISIBLE);
                    screentrans.setVisibility(View.INVISIBLE);
                }
            });
            currentstate = "items";
        }

        //items->transaction-(cancel)->items
        //items->transaction-(pay)....->outcome->items
        else if (type.equals("payment")) {
            runOnUiThread(new Runnable() {
                public void run() {
                    screensettlement.setVisibility(View.INVISIBLE);
                    screentrans.setVisibility(View.VISIBLE);
                    btntransok.setVisibility(View.INVISIBLE);
                    btntranscancel.setVisibility(View.VISIBLE);
                    txttrans.setText(message);
                    imgtrans.setImageResource(R.drawable.actionnone);
                    currentstate = "transaction";
                }
            });
            if (socketMessage.contains("NFC")) nfcModule.DoTransaction(price);
            else emvModule.DoTransaction(price);
        } else if (type.equals("transmessage")) {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (message.equals("imgconnect"))
                        imgtrans.setImageResource(R.drawable.actionconnect);
                    else if (message.equals("imgcardprocess"))
                        imgtrans.setImageResource(R.drawable.actionread);
                    else if (message.equals("imgreadok"))
                        imgtrans.setImageResource(R.drawable.actionreadok);
                    else if (message.equals("imgtapcard"))
                        imgtrans.setImageResource(R.drawable.actiontap);
                    else if (message.equals("imginsertcard"))
                        imgtrans.setImageResource(R.drawable.actioninsert);
                    else txttrans.append("\n" + message);
                }
            });
        } else if (type.equals("canceltrans")) {
            runOnUiThread(new Runnable() {
                public void run() {
                    screensettlement.setVisibility(View.INVISIBLE);
                    screentrans.setVisibility(View.INVISIBLE);
                }
            });
            currentstate = "items";
            if (socketMessage.contains("NFC")) nfcModule.CloseTransaction();
            else emvModule.CancelTransaction();

//                runOnMapsThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        ep.close();
//                    }
//                });
        } else if (type.equals("transsuccess")) {
            runOnUiThread(new Runnable() {
                public void run() {
                    screensettlement.setVisibility(View.INVISIBLE);
                    screentrans.setVisibility(View.VISIBLE);
                    btntransok.setVisibility(View.VISIBLE);
                    btntranscancel.setVisibility(View.INVISIBLE);
                    txttrans.append("\n" + message);
                    imgtrans.setImageResource(R.drawable.actionok);
                }
            });
            currentstate = "outcome";
        } else if (type.equals("failedtrans")) {
            runOnUiThread(new Runnable() {
                public void run() {
                    screensettlement.setVisibility(View.INVISIBLE);
                    screentrans.setVisibility(View.VISIBLE);
                    btntransok.setVisibility(View.VISIBLE);
                    btntranscancel.setVisibility(View.INVISIBLE);
                    txttrans.setText(message);
                    imgtrans.setImageResource(R.drawable.actiondeclined);
                }
            });
            currentstate = "outcome";
        } else if (type.equals("returntoitems")) {
            runOnUiThread(new Runnable() {
                public void run() {
                    screensettlement.setVisibility(View.INVISIBLE);
                    screentrans.setVisibility(View.INVISIBLE);
                }
            });
            currentstate = "items";
        } else {
            Log.e("FATAL ERR", "ERROR: STATE NOT RECOGNISED  " + type);
        }
    }
    //========================================================================================
    //========================================================================================
    //========================================================================================


    private GenericSecureService genericSecureService;

    private void loadCardTable() {
        final String TAG = MainActivity.class.getName();
        genericSecureService = new GenericSecureService();
        genericSecureService.open(new CbOpenCompleted() {
            @Override
            public void cbOpenCompleted(Request request) {
                if (request.getError() == 0) {
                    Log.d(TAG, "gss open");
                    final InputStream inStream = getResources().openRawResource(R.raw.cardtable);

                    try {
                        byte[] cardTable = convertStreamToByteArray(inStream);
                        genericSecureService.storeCardTable(cardTable, new CbOperationCompleted() {
                            @Override
                            public void cbOperationCompleted(Request request) {
                                try {
                                    inStream.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                if (request.getError() == 0) {
                                    Log.d(TAG, "card table loaded");
                                } else {
                                    Log.d(TAG, "card table not loaded");
                                    Log.d(TAG, request.getErrorDescription());
                                }
                            }
                        }, null);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.d(TAG, "gss is not open");
                    Log.d(TAG, request.getErrorDescription());
                }
            }
        }, null);
    }

    private static byte[] convertStreamToByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buff = new byte[new byte[is.available()].length];
        int i;
        while ((i = is.read(buff, 0, buff.length)) > 0) {
            byteArrayOutputStream.write(buff, 0, i);
        }

        return byteArrayOutputStream.toByteArray();
    }

    private static void runOnMapsThread(Runnable task) {
        MapsRuntime.getRuntime().callSerially(task);
    }

    public void startSocket(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Waiting for the client request");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                socketMessage = in.readLine();
//                System.out.println("Received message from client: " + socketMessage);
                if(socketMessage.contains("Update")) {
                        System.out.println("Received message:" + socketMessage);
                        if(socketString == null) {
                            socketString = "Pending Transaction";
                        }
                        out.println("Send message: " + socketString);
                } else {
                        String[] splitArray = socketMessage.split(":");
                        Log.e("Array", splitArray[0]);
                        setTransition(splitArray[3], "                 " + splitArray[0] + "   " + splitArray[1] + "    Pret: " + splitArray[2] + " Lei.", Integer.parseInt(splitArray[2]));
                        out.println("Message received: " + socketMessage);
                }
                // Send a message to the client
                out.flush();
                out.close();

                clientSocket.close();
            }


//                String line;
//                while((line = in.readLine()) != null) {
//                    if(line.contains("Update")) {
//                        System.out.println("Received message:" + line);
//                    } else {
//                        String[] splitArray = line.split(":");
//                        setTransition(splitArray[3], "                 " + splitArray[0] + "   " + splitArray[1] + "    Pret: " + splitArray[2] + " Lei.", Integer.parseInt(splitArray[2]));
//                    }
//                }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateTextUI(final String textUI) {
        transactionText.setText(textUI);
    }
}














