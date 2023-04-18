package com.worldline.valina.utils;

import android.util.Log;

import java.io.UnsupportedEncodingException;

import be.banksys.maps.CbGetBytesCompleted;
import be.banksys.maps.CbOpenCompleted;
import be.banksys.maps.CbOperationCompleted;
import be.banksys.maps.MapsRuntime;
import be.banksys.maps.Request;
import be.banksys.mapsx.macq.CbAcquirerList;
import be.banksys.mapsx.macq.CbCryptoData;
import be.banksys.mapsx.macq.Macq;
import be.banksys.mapsx.macq.MacqAcquirer;


public class PinExtractor {

    private Macq mySecureService = null;
    boolean isnfc=false;
    //this is called with  pinformat=(byte)0x30,  pin="5331551000564998"//5331 5510 0056 4998
    //after the pin was entered by the user using another secure service
    public void ExtractPin1(final byte pinformat,final String pin,Boolean _isnfc)
        {   isnfc=_isnfc;
            synchronized (this) {
                //CREATE SERVICE
                mySecureService = new Macq();
                try {   MapsRuntime.getRuntime().callSerially(new Runnable() {
                            public void run() {
                            try {   //OPEN SERVICE
                                    mySecureService.open(new CbOpenCompleted()
                                        {   public void cbOpenCompleted(be.banksys.maps.Request req)
                                                {   String pinsequence=pin.trim();
                                                    if(pinsequence.length()<16)pinsequence=("000000000000"+pinsequence);//last12 chars except the last one encoded in 6 bytes
                                                    pinsequence=pinsequence.substring(pinsequence.length()-13,pinsequence.length()-1);
                                                    try {   MapsRuntime.getRuntime().callSerially(new RunnerPinEncrypt(pinsequence.getBytes("ISO-8859-1"), pinformat));
                                                        }
                                                    catch (UnsupportedEncodingException e)
                                                        {   e.printStackTrace();
                                                            OnPinFailed(e.getMessage(),isnfc);
                                                            mySecureService.close();
                                                        }
                                                }
                                        }, null); }
                            catch (Exception e)
                                {   e.printStackTrace();
                                    OnPinFailed(e.getMessage(),isnfc);
                                    //mySecureService.close(); it didn't open
                                }
                            }
                        });
                    }
                catch (Exception e)
                    {   e.printStackTrace();
                        OnPinFailed(e.getMessage(),isnfc);
                        //mySecureService.close();
                    }
            }
        }
    public void OnPinReceived(byte[] baPinBlock,boolean isnfc)
        {   Log.e("MUST BE OVERWRITTEN","OnPinReceived MUST BE OVERWRTTEN");
        }
    public void OnPinFailed(String reason,boolean isnfc)
        {   Log.e("MUST BE OVERWRITTEN","OnPinFailed MUST BE OVERWRTTEN, "+reason);
        }
    private class RunnerPinEncrypt implements Runnable {
        private byte[] baCardData = null;
        private byte bPinFormat;
        private RunnerPinEncrypt(byte[] baCardData, byte bPinFormat)
            {   super();
                this.baCardData = baCardData;
                this.bPinFormat = bPinFormat;
            }
        public void run()
            {   //The last 6 bytes (12 digits) of the Partial Primary Account Number (PAN), which is the FULL PAN but with the luhn-check digit removed (=last digit). Left padded with 0 if less then 12 digits. The pan is only needed for PIN_ISO_FORMAT0 and PIN_ISO_FORMAT3.
                //PAN 123456abcdefghiZ   1->6 are bank   a->i are account number, Z is check,   we need 456abcdefghi encoded in 6 bytes
                try {   //GET AQUIRERS
                        mySecureService.getAcquirerList(new CbAcquirerList() {
                            @Override
                            public void cbAcquirerList(MacqAcquirer[] macqAcquirers, Request request) {
                                if(request.getError()!=0){OnPinFailed("getAcquirerList returned error: "+request.getErrorDescription(),isnfc);mySecureService.close();}
                                else if(macqAcquirers==null){OnPinFailed("getAcquirerList returned null",isnfc);mySecureService.close();}
                                else if(macqAcquirers.length<1){OnPinFailed("getAcquirerList returned a empty array",isnfc);mySecureService.close();}
                                else
                                //SELECT ACQUIRER
                                mySecureService.selectAcquirerSlot((byte)0, new CbOperationCompleted() {
                                    @Override
                                    public void cbOperationCompleted(Request request) {
                                        if(request.getError()!=0){OnPinFailed("selectAcquirerSlot returned error: "+request.getErrorDescription(),isnfc);mySecureService.close();}
                                        else
                                        //GENERATE KEYS
                                        mySecureService.generateKey(new CbGetBytesCompleted() {
                                            @Override
                                            public void cbGetBytesCompleted(byte[] key, Request request) {
                                                if(request.getError()!=0){OnPinFailed("generateKey returned error: "+request.getErrorDescription(),isnfc);mySecureService.close();}
                                                else if(key==null){OnPinFailed("generateKey returned null",isnfc);mySecureService.close();}
                                                else if(key.length<1){OnPinFailed("generateKey returned empty array",isnfc);mySecureService.close();}
                                                else
                                                //ENCRYPT PIN
                                                mySecureService.encryptIsoPin(baCardData, bPinFormat, new CbCryptoData()
                                                    {    public void cbCryptoData(byte[] baPinBlock, byte[] baKeySerialNumber, Request request)
                                                            {   if(request.getError()!=0){OnPinFailed("encryptIsoPin returned error: "+request.getErrorDescription()+" null:"+(baPinBlock==null),isnfc);mySecureService.close();}
                                                                else if(baPinBlock==null){OnPinFailed("encryptIsoPin returned null",isnfc);mySecureService.close();}
                                                                else if(baPinBlock.length<1){OnPinFailed("encryptIsoPin returned empty array",isnfc);mySecureService.close();}
                                                                else {OnPinReceived(baPinBlock,isnfc);mySecureService.close();}
                                                            }
                                                    }, null);

                                            }
                                        },null);
                                    }
                                },null);

                            }
                        },null );


                    }
                catch (Exception e) {
                    e.printStackTrace();
                    OnPinFailed("Exception in RunnerPinEncrypt.run(), exc: " + e.getMessage(),isnfc);
                    mySecureService.close();
                    Log.e("ERR encrypt pin", "Exception in RunnerPinEncrypt.run() " + e.toString());

                }
            }
    }





public void ExtractPin(final byte pinformat,final String pan,Boolean _isnfc)
    {   isnfc=_isnfc;
        String pansequence=pan.trim();
        if(pansequence.length()<16)pansequence=("000000000000"+pansequence);//last12 chars except the last one encoded in 6 bytes
        pansequence=pansequence.substring(pansequence.length()-13,pansequence.length()-1);
        final byte[] bytes;
        try {bytes = pansequence.getBytes("ISO-8859-1"); }
        catch (UnsupportedEncodingException e)
            {   e.printStackTrace();
                OnPinFailed(e.getMessage(),isnfc);
                return;
            }
        Log.d("ENCRYPTED PIN step 1","asd");
        //CREATE SERVICE
        mySecureService = new Macq();
        MapsRuntime.getRuntime().callSerially(new Runnable()
            {   public void run()
                {   //OPEN SERVICE
                    mySecureService.open(new CbOpenCompleted()
                    {   @Override public void cbOpenCompleted(be.banksys.maps.Request req)
                        {   //GET ACQUIRERS
                            mySecureService.getAcquirerList(new CbAcquirerList()
                            {   @Override public void cbAcquirerList(MacqAcquirer[] macqAcquirers, Request request)
                                {   if(request.getError()!=0){OnPinFailed("getAcquirerList returned error: "+request.getErrorDescription(),isnfc);mySecureService.close();}
                                    else if(macqAcquirers==null){OnPinFailed("getAcquirerList returned null",isnfc);mySecureService.close();}
                                    else if(macqAcquirers.length<1){OnPinFailed("getAcquirerList returned a empty array",isnfc);mySecureService.close();}
                                    else//SELECT ACQUIRER
                                        mySecureService.selectAcquirerSlot((byte)0, new CbOperationCompleted()
                                        {   @Override public void cbOperationCompleted(Request request)
                                            {   if(request.getError()!=0){OnPinFailed("selectAcquirerSlot returned error: "+request.getErrorDescription(),isnfc);mySecureService.close();}
                                                else//GENERATE KEYS
                                                    mySecureService.generateKey(new CbGetBytesCompleted()
                                                    {   @Override public void cbGetBytesCompleted(byte[] key, Request request)
                                                        {   if(request.getError()!=0){OnPinFailed("generateKey returned error: "+request.getErrorDescription(),isnfc);mySecureService.close();}
                                                            else if(key==null){OnPinFailed("generateKey returned null",isnfc);mySecureService.close();}
                                                            else if(key.length<1){OnPinFailed("generateKey returned empty array",isnfc);mySecureService.close();}
                                                            else//ENCRYPT PIN
                                                                mySecureService.encryptIsoPin(bytes, pinformat, new CbCryptoData()
                                                                {   @Override public void cbCryptoData(byte[] PinBlock, byte[] baKeySerialNumber, Request request)
                                                                    {   if(request.getError()!=0){OnPinFailed("encryptIsoPin returned error: "+request.getErrorDescription()+" null:"+(PinBlock==null),isnfc);mySecureService.close();}
                                                                        else if(PinBlock==null){OnPinFailed("encryptIsoPin returned null",isnfc);mySecureService.close();}
                                                                        else if(PinBlock.length<1){OnPinFailed("encryptIsoPin returned empty array",isnfc);mySecureService.close();}
                                                                        else {  Log.d("ENCRYPTED PIN","SUCCESSFULLY GENERATED THE ENCRYPTED PIN len:"+PinBlock.length);
                                                                                OnPinReceived(PinBlock,isnfc);
                                                                                mySecureService.close();}
                                                                    }
                                                                }, null);
                                                        }
                                                    },null);
                                            }
                                        },null);
                                }
                            },null );
                        }
                    }, null);
                }
            });
    }



}
