package com.worldline.valina.utils;

import android.util.Log;

import be.banksys.maps.ByteBuffer;
import be.banksys.maps.CbOpenCompleted;
import be.banksys.maps.MapsRuntime;
import be.banksys.maps.Request;
import be.banksys.maps.StlvInputMessage;
import be.banksys.maps.berltv.EMVBertTlv;
import be.banksys.maps.berltv.EMVConstructedTLVData;
import be.banksys.maps.berltv.EMVPrimitiveTLVData;
import be.banksys.maps.berltv.EMVTag;
import be.banksys.maps.ep.EntryPoint;
import be.banksys.maps.ep.EntryPointListener;
import be.banksys.maps.ep.EntryPointResponseCb;
import be.banksys.maps.ep.EntryPointTags;
import be.banksys.maps.util.Util;

public class NfcModule  implements CbOpenCompleted {

    class CachedTransResult
        {   public String message,pan,expdate,appcr,currencycode,tlv,track2,amount;
        }
    CachedTransResult cachedTransResult=null;


    final String TAG="NFC Module";
    private EntryPoint ep;
    int g_amount=0;

    public NfcModule()
        {
        }
    public void DoTransaction(int amount)
        {   g_amount=amount;
            cachedTransResult=null;
            runOnMapsThread(new Runnable()
                {   @Override
                    public void run()
                        {   ep = new EntryPoint();
                            ep.open(NfcModule.this, null);
                        }
                });
        }

    public void CloseTransaction()
        {   runOnMapsThread(new Runnable() {
                    @Override
                    public void run() {
                            ep.close();
                        }
                });
        }
    public void OnPinEntered(String pin,boolean iscancel,boolean issuccessful,String error)
        {   if(iscancel)
                OnTransactionResultNFC(false,true,"Renuntare.","","","","","","","","");
            else if(!issuccessful)
                OnTransactionResultNFC(false,true,"Eroare:"+error,"","","","","","","","");
            else if(cachedTransResult==null)
                OnTransactionResultNFC(false,true,"Lipsa date tranzactie","","","","","","","","");
            else OnTransactionResultNFC(true,true,cachedTransResult.message,cachedTransResult.pan,cachedTransResult.expdate,cachedTransResult.appcr,cachedTransResult.currencycode,
                    cachedTransResult.tlv,cachedTransResult.track2,cachedTransResult.amount,pin);
            cachedTransResult=null;
        }
    public String getChachedPAN()
        {   return cachedTransResult==null?"":cachedTransResult.pan;

        }



    //OVERWRITE
    public void OnTransactionMessage(String message)
        {   Log.e("MUST BE OVERWRITTEN","OnTransactionMessage MUST BE OVERWRTTEN");
        }
    public void OnTransactionResultNFC(boolean issuccessful,boolean isonline,String message,String pan,String expdate, String appcr,
                                    String currencycode,String tlv,String track2,String amount,String pinblock)
        {   Log.e("MUST BE OVERWRITTEN","OnTransactionResult MUST BE OVERWRTTEN");
        }
    public void OnOnlinePinRequest()
        {   Log.e("MUST BE OVERWRITTEN","OnOnlinePinRequest MUST BE OVERWRTTEN");

        }






    @Override
    public void cbOpenCompleted(Request request) {
        ep.setListener(entryPointListener);
        ep.entryPointRequest(getTransactionBuffer(g_amount), entryPointResponseCb, null);
    }

    EntryPointListener entryPointListener = new EntryPointListener() {
        @Override
        public void unsolicitedTransactionFeedback(StlvInputMessage stlvInputMessage, ByteBuffer data) {
            Log.d(TAG,"Data feedback= " + Util.toHexString(data));
            String dataStr = Util.toHexString(data);
            String[] array = dataStr.split(" ");
            String message2 = array[0];
            String status = array[1];

            Log.d(TAG,"message feedback= " + message2);
            Log.d(TAG,"status feedback= " + status);
            switch (message2) {
                case "15":
                    if (status.equals("02")) {
                        OnTransactionMessage("imgtapcard");
                        Log.d(TAG,"Present card......");
                    }
                    break;
                case "16":
                    OnTransactionMessage("imgcardprocess");
                    Log.d(TAG,"Processing......");
                    break;
                case "17":
                    OnTransactionMessage("imgreadok");
                    Log.d(TAG,"Card read ok......");
                    break;
                case "07":
                    OnTransactionMessage("Cardul nu a fost acceptat.");
                    Log.d(TAG,"Declined......");
                    break;
                case "1C":
                    OnTransactionMessage("Card diferit.");
                    Log.d(TAG,"Other card......");
                    break;
                case "1B":
                    Log.d(TAG,"GETTING AUTHORISATION......");
                    break;
                case "19":
                    OnTransactionMessage("Coliziune.");
                    Log.d(TAG,"Collision......");
                    break;
                case "03":
                    OnTransactionMessage("Success.");
                    Log.d(TAG,"Success......");
                    break;
                case "14":
                    Log.d(TAG,"Welcome......");
                    break;
                case "1e":
                    Log.d(TAG,"Clear Display......");
                    break;
                default:///09 should be enter pin!!!!!!
                    Log.e(TAG,"UNRECOGNIZE MESSAGE!!!! " +message2);
                    OnTransactionMessage("Mesaj necunoscut:"+message2);
            }
        }

        @Override
        public void unsolicitedOutcomeStatus(StlvInputMessage stlvInputMessage, ByteBuffer outcomeParam, ByteBuffer discretionaryData) {
            Log.d(TAG,"Data outcome= " + Util.toHexString(outcomeParam));
        }
    };

    EntryPointResponseCb entryPointResponseCb = new EntryPointResponseCb() {
        //@Override

        @Override
        public void performTransactionResponse(ByteBuffer data, int status, Request request) {
            TlvUtils.printtlv(TlvUtils.decodeemv(data.array()),"NFC RESPONSE");
            Log.d("Transaction data:",TlvUtils.buffertostring(data.array()," "));

            Log.d(TAG, "PerformTransactionResponse");
            Log.d(TAG, "Status : " + status);
            Log.d(TAG, "Req error: " + request.getError());

            Log.d(TAG, "Data response= " + Util.toHexString(data));

            ByteBuffer bb = ep.extractDataByTag(data.array(), EntryPointTags.OUTCOME_PARAM_SET_TAG);
            try {


                extractMessageGroup(data);
                extractKernelGroup(data);
                extractCommonGroup(data);
                extractResultGroup(data);

                if (bb != null) {
                    String Outcome = Util.toHexString(bb);
                    Log.d(TAG, "OutcomeParamSet:" + Outcome);
                    String[] messages = Outcome.split(" ");
                    String pinverification=messages[3].equals("00")?"Fara pin":(messages[3].equals("f0")?"Fara verificare":(messages[3].equals("20")?"Cu pin":messages[3]));
                    Log.d(TAG, "messages0(outcome): " + messages[0]+"  online pin:"+messages[3]);

                    if (messages[0].equals("10")) {   //setImage(APPROVE);
                        Log.d(TAG, "APPROVED");
                        ByteBuffer kernelGroup = ep.extractDataByTag(data.array(), KERNEL_GROUP);
                        ByteBuffer amountAuthorised = ep.extractDataByTag(kernelGroup.array(), AMOUNT_AUTHORISED);
                        ByteBuffer transactionCurrencyCode = ep.extractDataByTag(kernelGroup.array(), TRANSACTION_CURRENCY_CODE);
                        ByteBuffer appExpirationDate = ep.extractDataByTag(kernelGroup.array(), APP_EXPIRATION_DATE);
                        ByteBuffer appPan = ep.extractDataByTag(kernelGroup.array(), PAN);
                        ByteBuffer track2data = ep.extractDataByTag(kernelGroup.array(), TRACK_DATA2);
                        ByteBuffer appCrypto = ep.extractDataByTag(kernelGroup.array(), APPLICATION_CRYPTOGRAM);
                        String pan = appPan == null ? "" : Util.toHexString(appPan).replaceAll("\\s+", ""),
                                expdate = appExpirationDate == null ? "" : Util.toHexString(appExpirationDate).replaceAll("\\s+", ""),
                                appcr = appCrypto == null ? "" : Util.toHexString(appCrypto).replaceAll("\\s+", ""),
                                track2 = track2data == null ? "" : Util.toHexString(track2data).replaceAll("\\s+", ""),
                                amount = amountAuthorised == null ? "" : Util.toHexString(amountAuthorised).replaceAll("\\s+", ""),
                                currencycode = transactionCurrencyCode == null ? "" : Util.toHexString(transactionCurrencyCode).replaceAll("\\s+", "");

                        OnTransactionResultNFC(true,false,"Tranzactia a fost acceptata offline.\nPuteti ridica produsul.\n"+pinverification,
                                            pan, expdate, appcr, currencycode, "", track2, amount,"");
                        CloseTransaction();
                    } else if (messages[0].equals("20")) {
                        Log.d(TAG, "DECLINED");
                        OnTransactionResultNFC(false,false,"Tranzactia a fost respinsa.\n"+pinverification,"","","","","","","","");
                        CloseTransaction();
                    } else if (messages[0].equals("30")) {   //setImage(APPROVE);
                        Log.d(TAG, "ONLINE REQUEST");
                        ByteBuffer kernelGroup = ep.extractDataByTag(data.array(), KERNEL_GROUP);
                        ByteBuffer amountAuthorised = ep.extractDataByTag(kernelGroup.array(), AMOUNT_AUTHORISED);
                        ByteBuffer transactionCurrencyCode = ep.extractDataByTag(kernelGroup.array(), TRANSACTION_CURRENCY_CODE);
                        ByteBuffer appExpirationDate = ep.extractDataByTag(kernelGroup.array(), APP_EXPIRATION_DATE);
                        ByteBuffer appPan = ep.extractDataByTag(kernelGroup.array(), PAN);
                        ByteBuffer track2data = ep.extractDataByTag(kernelGroup.array(), TRACK_DATA2);
                        ByteBuffer appCrypto = ep.extractDataByTag(kernelGroup.array(), APPLICATION_CRYPTOGRAM);
                        String pan = appPan == null ? "" : Util.toHexString(appPan).replaceAll("\\s+", ""),
                                expdate = appExpirationDate == null ? "" : Util.toHexString(appExpirationDate).replaceAll("\\s+", ""),
                                appcr = appCrypto == null ? "" : Util.toHexString(appCrypto).replaceAll("\\s+", ""),
                                track2 = track2data == null ? "" : Util.toHexString(track2data).replaceAll("\\s+", ""),
                                amount = amountAuthorised == null ? "" : Util.toHexString(amountAuthorised).replaceAll("\\s+", ""),
                                currencycode = transactionCurrencyCode == null ? "" : Util.toHexString(transactionCurrencyCode).replaceAll("\\s+", "");
                        if(messages[3].equals("20"))//PIN IS REQUIRED
                            {   cachedTransResult=new CachedTransResult(){};
                                cachedTransResult.message="Tranzactia a fost acceptata ONLINE.\nPuteti ridica produsul.\n"+pinverification;
                                cachedTransResult.pan=pan;
                                cachedTransResult.expdate=expdate;
                                cachedTransResult.appcr=appcr;
                                cachedTransResult.currencycode=currencycode;
                                cachedTransResult.tlv="";
                                cachedTransResult.track2=track2;
                                cachedTransResult.amount=amount;
                                OnOnlinePinRequest();
                            }
                        else OnTransactionResultNFC(true,true,"Tranzactia a fost acceptata ONLINE.\nPuteti ridica produsul.\n"+pinverification,
                                pan, expdate, appcr, currencycode, "", track2, amount,"");
                        CloseTransaction();
                        //waitAndEndTransaction();
                    } else if (messages[0].equals("40")) {
                        Log.d(TAG, "END APPLICATION");
                        //setImage(END_APP);
//                        setTransition("failedtrans", );
                        OnTransactionResultNFC(false,false,"Cardul nu a acceptat tranzactia.\nEND APPLICATION\n"+pinverification,
                                        "","","","","","","","");
                        CloseTransaction();
                    } else if (messages[0].equals("50")) {
                        Log.d(TAG, "SELECT NEXT");
                    } else if (messages[0].toString().equals("60")) {
                        Log.d(TAG, "TRY OTHER INTERFACE");
//                        setTransition("failedtrans", );
                        OnTransactionResultNFC(false,false,"Reincercati tranzactia introducand cardul.\nTRY OTHER INTERFACE\n"+pinverification,
                                        "","","","","","","","");
                        CloseTransaction();
                    } else if (messages[0].equals("70")) {
                        Log.d(TAG, "TRY AGAIN...");
                    } else if (messages[0].equals("f0")) {   //waitAndEndTransaction();
                        Log.d(TAG, "NOT APPLICABLE");
//                        setTransition("failedtrans", );
                        OnTransactionResultNFC(false,false,"Cardul nu are o aplicatie valida.\nNOT APPLICABLE\n"+pinverification,
                                "","","","","","","","");
                        CloseTransaction();
                    } else {
                        Log.e(TAG, "MESSAGE NOT RECOGNISED: [" + messages[0] + "]" + (messages[0].equals("30")));
//                        setTransition("failedtrans", );
                        OnTransactionResultNFC(false,false,"Eroare Necunoscuta.\nCod:" + messages[0],
                                "","","","","","","","");
                        CloseTransaction();
                    }

                }
            } catch (Exception e) {
                Log.e(TAG, "No Outcome Param set");
                e.printStackTrace();
            }
        }

        @Override
        public void cleanUpResponse(ByteBuffer data, int status, Request request) {
            Log.d(TAG, "CleanUpResponse");
            Log.d(TAG, "Status : " + status);
            Log.d(TAG, "Req error: " + request.getError());
            Log.d(TAG, "Data clean up: " + Util.toHexString(data.array()));
        }

        @Override
        public void issuerUpdateProcessingResponse(ByteBuffer data, int status, Request request) {
            Log.d(TAG, "IssuerUpdateProcessingResponse");
            Log.d(TAG, "Status : " + status);
            Log.d(TAG, "Req error: " + request.getError());
            Log.d(TAG, "Data issuer: " + Util.toHexString(data.array()));
        }
    };
        private void extractMessageGroup(ByteBuffer byteBuffer) {
            ByteBuffer messageGroup = ep.extractDataByTag(byteBuffer.array(), MESSAGE_GROUP);
            if(messageGroup != null) {
                Log.d(TAG,"Message group E0 not null");
                Log.d(TAG,"messageGroup:" + Util.toHexString(messageGroup));

                ByteBuffer messageTypeIdentifier = ep.extractDataByTag(messageGroup.array(), MESSAGE_TYPE_IDENTIFIER);
                Log.d(TAG,"messageTypeIdentifier:" + Util.toHexString(messageTypeIdentifier));

                ByteBuffer appPan = ep.extractDataByTag(messageGroup.array(), PAN);
                if(appPan != null) {
                    Log.d(TAG,"appPan4:" + Util.toHexString(appPan));
                } else {
                    Log.d(TAG,"appPan4 null");
                }

            } else {
                Log.d(TAG,"Message group E0 null");
            }
        }

        private void extractKernelGroup(ByteBuffer byteBuffer) {
            ByteBuffer kernelGroup = ep.extractDataByTag(byteBuffer.array(), KERNEL_GROUP);
            if(kernelGroup != null) {
                Log.d(TAG,"Kernel group E9 not null");
                Log.d(TAG,"kernelGroup:" + Util.toHexString(kernelGroup));

                ByteBuffer transactionDate = ep.extractDataByTag(kernelGroup.array(), TRANSACTION_DATE);
                if(transactionDate != null) {
                    Log.d(TAG,"transactionDate:" + Util.toHexString(transactionDate));
                } else {
                    Log.d(TAG,"transactionDate null");
                }

                ByteBuffer amountAuthorised = ep.extractDataByTag(kernelGroup.array(), AMOUNT_AUTHORISED);
                if(amountAuthorised != null) {
                    Log.d(TAG,"amountAuthorised:" + Util.toHexString(amountAuthorised));
                } else {
                    Log.d(TAG,"amountAuthorised null");
                }

                ByteBuffer transactionCurrencyCode = ep.extractDataByTag(kernelGroup.array(), TRANSACTION_CURRENCY_CODE);
                if(transactionCurrencyCode != null) {
                    Log.d(TAG,"transactionCurrencyCode:" + Util.toHexString(transactionCurrencyCode));
                } else {
                    Log.d(TAG,"transactionCurrencyCode null");
                }

                ByteBuffer terminalCountryCode = ep.extractDataByTag(kernelGroup.array(), TERMINAL_COUNTRY_CODE);
                if(terminalCountryCode != null) {
                    Log.d(TAG,"terminalCountryCode:" + Util.toHexString(terminalCountryCode));
                } else {
                    Log.d(TAG,"terminalCountryCode null");
                }

                ByteBuffer appLabel = ep.extractDataByTag(kernelGroup.array(), APP_LABEL);
                if(appLabel != null) {
                    Log.d(TAG,"appLabel:" + Util.toHexString(appLabel));
                } else {
                    Log.d(TAG,"appLabel null");
                }

                ByteBuffer appExpirationDate = ep.extractDataByTag(kernelGroup.array(), APP_EXPIRATION_DATE);
                if(appExpirationDate != null) {
                    Log.d(TAG,"appExpirationDate:" + Util.toHexString(appExpirationDate));
                } else {
                    Log.d(TAG,"appExpirationDate null");
                }
                ByteBuffer appPan = ep.extractDataByTag(kernelGroup.array(), PAN);
                if(appPan != null) {
                    Log.d(TAG,"appPan3:" + Util.toHexString(appPan));
                } else {
                    Log.d(TAG,"appPan3 null");
                }
                ByteBuffer appCrypto = ep.extractDataByTag(kernelGroup.array(), APPLICATION_CRYPTOGRAM);
                if(appPan != null) {
                    Log.d(TAG,"appCrypto3:" + Util.toHexString(appCrypto));
                } else {
                    Log.d(TAG,"appCrypto3 null");
                }
                ByteBuffer track2data = ep.extractDataByTag(kernelGroup.array(), TRACK_DATA2);
                if(track2data != null) {
                    Log.d(TAG,"track2:" + Util.toHexString(track2data));
                } else {
                    Log.d(TAG,"track2 null");
                }

            } else {
                Log.d(TAG,"Kernel group E9 null");
            }
        }

        private void extractCommonGroup(ByteBuffer byteBuffer) {
            ByteBuffer commonGroup = ep.extractDataByTag(byteBuffer.array(), COMMON_GROUP);
            if(commonGroup != null) {
                Log.d(TAG, "commonGroup not null");
                Log.d(TAG,"commonGroup:" + Util.toHexString(commonGroup));
                ByteBuffer appPan = ep.extractDataByTag(commonGroup.array(), PAN);
                if(appPan != null) {
                    Log.d(TAG,"appPan2:" + Util.toHexString(appPan));
                } else {
                    Log.d(TAG,"appPan2 null");
                }


                ByteBuffer incidentCodeBb = ep.extractDataByTag(commonGroup.array(), INCIDENT_CODE);
                if(incidentCodeBb != null) {
                    String dataIncidentCode = Util.toHexString(incidentCodeBb);
                    Log.d(TAG,"incidentCodeBb:" + dataIncidentCode);
                    String[] array = dataIncidentCode.split(" ");

                    int incidentCode = ((Integer.valueOf(array[0]) & 0x0F) + (((Integer.valueOf(array[0]) & 0xF0) >> 4) * 10)) * 100;
                    incidentCode += ((Integer.valueOf(array[1]) & 0x0F) + (((Integer.valueOf(array[1]) & 0xF0) >> 4) * 10));

                    Log.d(TAG,"incidentCode:" + incidentCode);

                    switch (incidentCode) {
                        case 0:    Log.d(TAG,"INCIDENT CODE : OK"); break;
                        case 256:  Log.d(TAG,"INCIDENT CODE : Error during protocol activation"); break;
                        case 512:  Log.d(TAG,"INCIDENT CODE : Timeout Card Insertion"); break;
                        case 768:  Log.d(TAG,"INCIDENT CODE : Timeout Card Removal"); break;
                        case 1024:  Log.d(TAG,"INCIDENT CODE : Card inserted by power up failed"); break;
                        case 1280:  Log.d(TAG,"INCIDENT CODE : Failed to select PPSE"); break;
                        case 1792:  Log.d(TAG,"INCIDENT CODE : Failed to activate kernel"); break;
                        case 2048:  Log.d(TAG,"INCIDENT CODE : Start new Transaction before previous completed"); break;
                        case 2304:  Log.d(TAG,"INCIDENT CODE : Error in Request Received"); break;
                        case 2560:  Log.d(TAG,"INCIDENT CODE : Error during pre-processing"); break;
                        case 2816: Log.d(TAG,"INCIDENT CODE : Abort request received from application");break;
                        case 3072:  Log.d(TAG,"INCIDENT CODE : Card is not longer present"); break;
                        case 3584:  Log.d(TAG,"INCIDENT CODE : Failed to select AID"); break;
                        case 8192:  Log.d(TAG,"INCIDENT CODE : Error in response from kernel"); break;
                        default: Log.d(TAG,"INCIDENT CODE : Unknown code "+ incidentCode); break;
                    }
                } else {
                    Log.d(TAG,"incidentCode null");
                }
            } else {
                Log.d(TAG, "commonGroup null");
            }
        }

        private void extractResultGroup(ByteBuffer byteBuffer) {
            ByteBuffer resultGroup = ep.extractDataByTag(byteBuffer.array(), CANDIDATE_TABLE);
            if(resultGroup != null) {
                Log.d(TAG, "resultGroup E5 not null");
                Log.d(TAG,"resultGroup:" + Util.toHexString(resultGroup));

                ByteBuffer outcomeParameterSet = ep.extractDataByTag(resultGroup.array(), OUTCOME_PARAMETER_SET);
                if(outcomeParameterSet != null) {
                    Log.d(TAG,"outcomeParameterSet:" + Util.toHexString(outcomeParameterSet));
                } else {
                    Log.d(TAG,"outcomeParameterSet null");
                }
                ByteBuffer appPan = ep.extractDataByTag(resultGroup.array(), PAN);
                if(appPan != null) {
                    Log.d(TAG,"appPan1:" + Util.toHexString(appPan));
                } else {
                    Log.d(TAG,"appPan1 null");
                }
            } else {
                Log.d(TAG, "resultGroup E5 null");
            }
        }

    private static void runOnMapsThread(Runnable task) {
        MapsRuntime.getRuntime().callSerially(task);
    }



















//=========================================================
//=========================================================MESSAGE BUILDER
    static byte[][] supportedaids=
        {       new byte[]{(byte)0xA0,0x00,0x00,0x00,0x03,0x10,0x10,0x01}, new byte[]{(byte)0xA0,0x00,0x00,0x00,0x03,0x10,0x10,0x02},
                new byte[]{(byte)0xA0,0x00,0x00,0x00,0x03,0x10,0x10}, new byte[]{(byte)0xA0,0x00,0x00,0x00,0x03,0x10,0x20},
                new byte[]{(byte)0xA0,0x00,0x00,0x00,0x03,0x20,0x10}, new byte[]{(byte)0xA0,0x00,0x00,0x00,0x03,0x20,0x20},
                new byte[]{(byte)0xA0,0x00,0x00,0x00,0x04,0x10,0x10}, new byte[]{(byte)0xA0,0x00,0x00,0x00,0x04,0x10,0x20},
                new byte[]{(byte)0xA0,0x00,0x00,0x00,0x04,0x20,0x10}, new byte[]{(byte)0xA0,0x00,0x00,0x00,0x04,0x20,0x20},
                new byte[]{(byte)0xA0,0x00,0x00,0x00,0x04,0x10,0x10,0x12,0x13}, new byte[]{(byte)0xA0,0x00,0x00,0x00,0x04,0x10,0x10,0x12,0x15},
                new byte[]{(byte)0xA0,0x00,0x00,0x00,0x04,0x30,0x60}, new byte[]{(byte)0xA0,0x00,0x00,0x00,0x04,(byte)0x88,0x26},
                new byte[]{(byte)0x0A,0x00,0x00,0x00,0x1C}, new byte[]{(byte)0xA0,0x00,0x00,0x03,0x24,0x10,0x10},
                new byte[]{(byte)0xA0,0x00,0x00,(byte)0x99,(byte)0x99,0x01}, new byte[]{(byte)0xA0,0x00,0x00,0x00,(byte)0x99,(byte)0x90,(byte)0x90},
                new byte[]{(byte)0xA0,0x00,0x00,0x00,0x25,0x01}
        };


    ByteBuffer getTransactionBuffer(int amount){
        byte[] amountbytearray=new byte[] {0x00,0x00,0x00,0x00,0x00,0x00};
        amount*=100;
        for(int i=amountbytearray.length-1;i>=0;i--)
        {   amountbytearray[i]=(byte)((amount%10+amount/10%10*16));
            amount/=100;
            if(amount==0)break;
        }

        try {
            ///// TRANSACTION REQUEST ////
            EMVConstructedTLVData transactionRequestData = new EMVConstructedTLVData();

            //MESSAGE GROUP
            {   EMVConstructedTLVData messageGroupData = new EMVConstructedTLVData();
                messageGroupData.append(new EMVBertTlv(new EMVTag(MESSAGE_TYPE_IDENTIFIER), new EMVPrimitiveTLVData(new byte[] {0x43, 0x2B})));
                messageGroupData.append(new EMVBertTlv(new EMVTag(MESSAGE_SEQUENCE_NUMBER), new EMVPrimitiveTLVData(new byte[] {0x01})));
                transactionRequestData.append(new EMVBertTlv(new EMVTag(MESSAGE_GROUP), messageGroupData));
            }
            //KERNEL GROUP
            {   EMVConstructedTLVData kernelGroupData = new EMVConstructedTLVData();
                kernelGroupData.append(new EMVBertTlv(new EMVTag(AMOUNT_AUTHORISED), new EMVPrimitiveTLVData(amountbytearray)));
                kernelGroupData.append(new EMVBertTlv(new EMVTag(TRANSACTION_SEQUENCE_COUNTER), new EMVPrimitiveTLVData(new byte[] {0x00, 0x00, 0x00, 0x01})));
                kernelGroupData.append(new EMVBertTlv(new EMVTag(TRANSACTION_CURRENCY_CODE), new EMVPrimitiveTLVData(new byte[]  {0x09, 0x46})));
                kernelGroupData.append(new EMVBertTlv(new EMVTag(TRANSACTION_CURRENCY_EXPONENT), new EMVPrimitiveTLVData(new byte[]  {0x02})));
                kernelGroupData.append(new EMVBertTlv(new EMVTag(TRANSACTION_TIME), new EMVPrimitiveTLVData(new byte[] {0x10, 0x55, 0x00})));
                kernelGroupData.append(new EMVBertTlv(new EMVTag(TRANSACTION_DATE), new EMVPrimitiveTLVData(new byte[]  {0x21, 0x05, 0x13})));
                kernelGroupData.append(new EMVBertTlv(new EMVTag(TERMINAL_COUNTRY_CODE), new EMVPrimitiveTLVData(new byte[]  {0x06, 0x42})));
                kernelGroupData.append(new EMVBertTlv(new EMVTag(TRANSACTION_TYPE), new EMVPrimitiveTLVData(new byte[]  {0x00})));
                kernelGroupData.append(new EMVBertTlv(new EMVTag(UNPREDICTABLE_NUMBER), new EMVPrimitiveTLVData(new byte[]{(byte)1,(byte)2,(byte)3,(byte)4})));
                transactionRequestData.append(new EMVBertTlv(new EMVTag(KERNEL_GROUP), kernelGroupData));
            }
            //RESULT GROUP
            {   EMVConstructedTLVData tableData = new EMVConstructedTLVData();
                for(int i=0;i<supportedaids.length;i++)
                {   EMVConstructedTLVData tableRecordOneData = new EMVConstructedTLVData();
                    byte[][] tags=
                        {   {(byte)0xDF,(byte)0x81,0x23}, {0x10,0,0,0,0x01,00}, //DF8123 FLOOR LIMIT
                            {(byte)0xDF,(byte)0x91,0x62}, {1},              //DF9162 FLOOR LIMIT TAG
                            {(byte)0xDF,(byte)0x81,0x24}, {0x01,0,0,0x01,00,00}, //DF8124 TRANSACTION LIMIT [NO ON-DEVICE]       //end aplication if < amount
                            {(byte)0xDF,(byte)0x91,0x63}, {1},              //DF9163 TRANSACTION LIMIT [NO ON-DEVICE] FLAG
                            {(byte)0xDF,(byte)0x81,0x25}, {0x00,0,0,0x01,00,00}, //DF8125 TRANSACTION LIMIT [ON-DEVICE]
                            {(byte)0xDF,(byte)0x91,0x64}, {1},              //DF9164 TRANSACTION LIMIT [ON-DEVICE] FLAG
                            {(byte)0xDF,(byte)0x81,0x26}, {0x00,0,0,0x01,00,00}, //DF8126 CVM REQ LIMIT// max ammount for offline
                            {(byte)0xDF,(byte)0x91,0x65}, {1},              //DF9165 CVM REQ LIMIT FLAG
                            {(byte)0xDF,(byte)0x81,0x21}, {0,0,0,0,0x0C}, //DF8121 TAC DENIAL
                            {(byte)0xDF,(byte)0x81,0x22}, {(byte)0x80,(byte)0x80,(byte)0x50,(byte)0xF4,0x14},    //DF8122 TAC ONLINE
                            {(byte)0xDF,(byte)0x81,0x20}, {(byte)0x80,(byte)0x80,0x50,(byte)0xF4,0x00},    //DF8120 TAC DEFAULT
                            {(byte)0xDF,(byte)0x81,0x17},{(byte)0xE0},      //DF8117  CARD DATA INPUT CAPABILITY MASTERCARD   ->this causes try other interface instead of declined,//DF8117 ->  20:declined     E0:try other interface
                            {(byte)0xDF,(byte)0x81,0x1F},{(byte)0xD8},      //DF811F  SECURITY CAPABILITY MASTERCARD             d8   c8  00
                            {(byte)0xDF,(byte)0x81,0x18},{(byte)0xD0},      //DF8118  CVM CAPABILITY - CVM REQUIRED MASTERCARD(must be different from df8119)
                            {(byte)0xDF,(byte)0x81,0x19},{(byte)0x01},       //DF8119  CVM CAPABILITY - NO CVM REQUIRED MASTERCARD  d0
                            {(byte)0x9F,(byte)0x33},{(byte)0xE0,(byte)0x08,(byte)0xD8},       //TERM CAP  8117,8119,811F

                            //{(byte)0xDF,(byte)0x81,0x1E},{(byte)0x20},      //DF811E  Mag-stripe CVM Capability – CVM Required (MasterCard)
                            //{(byte)0xDF,(byte)0x81,0x1C},{(byte)0x20},      //DF812C  Mag-stripe CVM Capability – No CVM Required (MasterCard)
                            {(byte)0xDF,(byte)0x81,0x1A},{(byte)0x9f,(byte)0x6a,(byte)0x04},      //DF811A  Default UDOL (MasterCard)

                            APPLICATION_IDENTIFIER,    supportedaids[i],//10101111  or    11110101
                            TERMINAL_TRANSACTION_QUALIFIERS,  new byte[]{(byte)0xF4,(byte)0xFF,(byte)0xF0,(byte)0xF0}  //new byte[]{(byte)0x34,0x20,0x40,0x00}
                        };//ttq byte1: f4-ok f5-ok f6-ok f7-ok        f1-nu f2-nu f3-nu f8-nu f9-nu fa-nu fb-nu fc-nu fd-nu fe-nu ff-nu
                    for(int j=0;j<tags.length;j+=2)
                        tableRecordOneData.append(new EMVBertTlv(new EMVTag(tags[j]),new EMVPrimitiveTLVData(tags[j+1])));
                    tableData.append(new EMVBertTlv(new EMVTag(TABLE_RECORD),tableRecordOneData));
                }
                transactionRequestData.append(new EMVBertTlv(new EMVTag(CANDIDATE_TABLE), tableData));
            }
            //REQUEST DATA GROUP
            {   EMVConstructedTLVData dataGroupData = new EMVConstructedTLVData();
                dataGroupData.append(new EMVBertTlv(new EMVTag(new byte[]{(byte) 0x08}), new EMVPrimitiveTLVData( new byte[512])));//512 zeros
                dataGroupData.append(new EMVBertTlv(new EMVTag(UNPREDICTABLE_NUMBER), new EMVPrimitiveTLVData( new byte[4])));
                transactionRequestData.append(new EMVBertTlv(new EMVTag(REQUEST_DATA_GROUP), dataGroupData));
            }
            EMVBertTlv transactionRequest = new EMVBertTlv(new EMVTag(F0TAG), transactionRequestData);
            ByteBuffer transactionBuffer = new ByteBuffer(transactionRequest.getTLV().length); //CREATE CORRECT LENGTH BYTEBUFFER, OTHERWISE ENTRYPOINT WILL NOT BE ABLE TO READ IT.
            transactionBuffer.put(transactionRequest.getTLV());
            Log.d("get transaction buffer", Util.toHexString(transactionRequest.getTLV()));
            transactionBuffer.flip();// FLIP TO MAKE READABLE
            return transactionBuffer;
        } catch(Exception e){
            e.printStackTrace();
            return null;

        }
    }









//=========================================================
//=========================================================TAGS
    public static final byte[] F0TAG = { (byte) 0xF0 };
    public static final byte[] MESSAGE_GROUP = {(byte)0xE0};
    public static final byte[] MESSAGE_TYPE_IDENTIFIER =  {(byte)0xD0};
    public static final byte[] MESSAGE_SEQUENCE_NUMBER = {(byte) 0xD1};
    public static final byte[] REQUEST_DATA_GROUP = {(byte) 0xE3};
    public static final byte[] KERNEL_GROUP =  {(byte) 0xE9};
    public static final byte[] AMOUNT_AUTHORISED = { (byte) 0x9F,0x02};
    public static final byte[] TRANSACTION_SEQUENCE_COUNTER = { (byte) 0x9F , 0x41};
    public static final byte[] TRANSACTION_CURRENCY_CODE = { (byte) 0x5F, 0x2A};
    public static final byte[] TRANSACTION_CURRENCY_EXPONENT ={ (byte) 0x5F,0x36};
    public static final byte[] TRANSACTION_DATE ={ (byte) 0x9A};
    public static final byte[] TRANSACTION_TIME={ (byte) 0x9F,0x21};
    public static final byte[] TERMINAL_COUNTRY_CODE={ (byte) 0x9F,0x1A};//old:
    public static final byte[] TABLE_RECORD = { (byte) 0xEF};
    public static final byte[] CANDIDATE_TABLE = { (byte) 0xE5};
    public static final byte[] APPLICATION_IDENTIFIER = { (byte) 0x9F,0x06};
    public static final byte[] TERMINAL_TRANSACTION_QUALIFIERS = { (byte) 0x9F, 0x66};
    public static final byte[] KERNEL_ID = { (byte) 0xDF,(byte)0x81,0x0C};
    public static final byte[] COMMON_GROUP = { (byte) 0xBF,(byte)0x81,0x01};
    public static final byte[] INCIDENT_CODE = { (byte) 0xDF,0x2D};
    public static final byte[] OUTCOME_PARAMETER_SET = { (byte) 0xDF,(byte)0x81,0x29};
    public static final byte[] APP_LABEL = { (byte) 0x50};
    public static final byte[] APP_EXPIRATION_DATE = { (byte) 0x5F,0x24};
    public static final byte[] PAN = { (byte) 0x5A};
    public static final byte[] TRANSACTION_TYPE = { (byte) 0x9C};
    public static final byte[] APPLICATION_CRYPTOGRAM = { (byte) 0x9F,(byte) 0x26};
    public static final byte[] TRACK_DATA2= { (byte) 0x57};
    public static final byte[] UNPREDICTABLE_NUMBER=new byte[]{(byte) 0x9F,(byte) 0x37};


}
