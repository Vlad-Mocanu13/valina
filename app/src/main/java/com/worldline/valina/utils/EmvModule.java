package com.worldline.valina.utils;



import android.util.Log;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Map;

import be.banksys.maps.ByteBuffer;
import be.banksys.maps.CbCloseCompleted;
import be.banksys.maps.CbOpenCompleted;
import be.banksys.maps.Handle;
import be.banksys.maps.MapsRuntime;
import be.banksys.maps.Request;
import be.banksys.maps.StlvInputMessage;
import be.banksys.maps.berltv.EMVBertTlv;
import be.banksys.maps.berltv.EMVConstructedTLVData;
import be.banksys.maps.berltv.EMVPrimitiveTLVData;
import be.banksys.maps.berltv.EMVTag;
import be.banksys.maps.berltv.EmvEnumeration;
import be.banksys.maps.berltv.MalFormedTLVException;
import be.banksys.maps.berltv.UnsupportedTagException;
import be.banksys.maps.card.CbPowerupCompleted;
import be.banksys.maps.card.ChipCardReader;
import be.banksys.maps.card.ChipCardReaderListener;
import be.banksys.maps.emv.CbPhaseCompleted;
import be.banksys.maps.emv.EmvEngine;
import be.banksys.maps.emv.EmvEngineListener;
import be.banksys.maps.emv.EmvStatus;
import be.banksys.maps.stlv.Stlv;


public class EmvModule implements ChipCardReaderListener {

    class CachedTransResult
        {   public String message,pan,expdate,appcr,currencycode,tlv,track2,amount;
            public boolean isonline=false;
        }
    CachedTransResult cachedTransResult=null;
    String cachedKeyIndex="",cachedAppindex="";

    private ChipCardReader ccr;
    protected EmvEngine engine;
    byte[] atr=null;
    StlvInputMessage ask_pin_arg = null;
    int g_amount=9;
    boolean ispinnoline=false;

    //public EmvModule() { }




    //==================================================================PUBLIC APIs
    public void DoTransaction(int _amount)
        {   g_amount=_amount;
            ispinnoline=false;
            cachedTransResult=null;
            runOnMapsThread(new Runnable() {
                @Override
                public void run()
                {   initEngine();
                }
            });
        }
    public void EncryptOnlinePin(String pan)
        {   Log.e("MUST BE OVERWRITTEN","EncryptOnlinePin MUST BE OVERWRTTEN");

        }
    public void OnPinRequest()
        {   Log.e("MUST BE OVERWRITTEN","OnPinRequest MUST BE OVERWRTTEN");
        }
    public void OnTransactionResultEMV(boolean issuccessful,boolean isonline,String message, String pan, String expdate, String appcr, String currencycode, String tlv, String track2, String amount,String pinblock)
        {   Log.e("MUST BE OVERWRITTEN","OnTransactionRequest MUST BE OVERWRTTEN");
        }
    public void OnTransactionMessage(String message)
        {   Log.e("MUST BE OVERWRITTEN","OnTransactionMessage MUST BE OVERWRTTEN");
        }
    public void OnPinEntered(String status)
        {
            Log.d("PHASE B 3", "PIN ENTERED: "+status);
            int pinStatus=0;
            if(status.matches("PIN entered"))
            {   pinStatus = (int) 0x01400104;    /////(int) 0x01400204;
                //0x01400100=pin blocked
                //0x01400101=dupped 3 times but veriffied online
                //0x01400102=no pin verification,
                //0x01400101=dupped 3 times but veriffied online
                //0x01400104=verification but dupped
                //0x01400100=pin blocked
            }
            else{   pinStatus = 0;
            }
            Log.d("teeest","pin status:"+pinStatus+"  "+status);
            engine.replyToAskPin(ask_pin_arg, pinStatus);
        }
    public void OnPinEncrypted(String pin,boolean issuccess,String error)
        {   if(!issuccess)
                OnTransactionResultEMV(false,false,
                    "Tranzactie esuata "+error, "", "", "",
                        "", "", "", ""+g_amount,"");
            else if(cachedTransResult==null)
                OnTransactionResultEMV(false,false, "Lipsa date tranzactie", "",
                        "", "", "", "", "", ""+g_amount,"");
            else OnTransactionResultEMV(true,cachedTransResult.isonline,
                    cachedTransResult.message,
                    cachedTransResult.pan, cachedTransResult.expdate, cachedTransResult.appcr, cachedTransResult.currencycode,
                    cachedTransResult.tlv, cachedTransResult.track2, cachedTransResult.amount,pin);
            cachedTransResult=null;
        }
    public void CancelTransaction()
        {   closeccr();

        }


    //==================================================================TRANSACTION PHASE A  (hardware initialization)
    //STEP 1
    void initEngine()
        {   final long timestamp = System.currentTimeMillis();
            Log.d("PHASE A 1 START", "OPENING EMV ENGINE");
            engine = new EmvEngine();
            engine.setListener(this.myEmvEngineListener);
            engine.open(new CbOpenCompleted() {

                public void cbOpenCompleted(Request req) {
                    if(req.getError()!=0){
                        System.err.println("Error while opening EmvEngine");
                        System.exit(req.getError());
                    }
                    Log.d("PHASE A 1 END", "ENGINE OPENED   interval:"+ (System.currentTimeMillis() - timestamp)+" ms" );


                    runOnMapsThread(new Runnable() {
                        @Override
                        public void run() {
                            initCardReder();
                        }
                    });



                }
            }, null);

        }
    //STEP 2
    void initCardReder()
        {   final long timestamp = System.currentTimeMillis();
            Log.d("PHASE A 2 START", "OPENING CARD READER");
            ccr = new ChipCardReader();
            ccr.setListener(this);
            ccr.open("/dev/banksys/card/ccr0", new CbOpenCompleted() {

                public void cbOpenCompleted(Request req) {
                    if(req.getError()!=0){
                        System.err.println("Error while opening ChipCardReader");
                        System.exit(req.getError());
                    }
                    Log.d("PHASE A 2 END", "CARD READER OPENED   interval:"+ (System.currentTimeMillis() - timestamp)+" ms" );
                    powerupcardreadder();
                }

            }, this);
        }
    //STEP 3
    void powerupcardreadder()
        {   Log.d("PHASE A 3 START", "POWERING UP READER");
            final long timestamp = System.currentTimeMillis();
            ccr.powerUp(new CbPowerupCompleted()
                {   public void cbPowerupCompleted(byte[] atr, Request req)
                        {   atr     = atr;//atr is null :|  ......
                            Log.d("PHASE A 3 END", "CARD READER POWERED UP   interval:"+ (System.currentTimeMillis() - timestamp)+" ms" );
                            OnTransactionMessage("imginsertcard");
                        }
                }, this);
        }
    //STEP 4
    public void statusChanged(int status)
        {   //this.status = status;
            Log.d("CARD STATUS CHANGED","new status:"+status);
            if(status==1)Log.d("PHASE A 4 START END","CARD DETECTED");
            if(status==1)recognizingCard();// 0=card removed, 1=card detected, 32=powered up,  33=card+poweredup
        }

    //=======================================================================================================TRANSACTION PHASE B (emv communication)
    //STEP 1
    void recognizingCard()
        {   final long timestamp = System.currentTimeMillis();
            Log.d("PHASE B 1 START", "RECOGNIZING CARD");
            OnTransactionMessage("imgcardprocess");

            try {Thread.sleep(300); }//give the card time to be read after being inserted
            catch (InterruptedException e) { e.printStackTrace(); }

            engine.recognizeCard(ccr, getcardrecognitionbytebufferdata(), new CbPhaseCompleted(){
                public void cbPhaseCompleted(ByteBuffer data, int _status, Request req) {

                    int index=0;
                    byte[] selectedapp=null;
                    try {   EMVBertTlv  dataTlv = EMVBertTlv.constructEMVBertTlv(data.array());
                            EmvEnumeration emvEnumeration = dataTlv.findData(new EMVTag(DEDICATED_FILE_NAME));
                            if(emvEnumeration==null){Log.e("APPLICATIONS","Missing applications tag!!!!!");}//exitcase
                            else while(emvEnumeration.hasMoreElements())
                            {   byte[] application = ((EMVPrimitiveTLVData) ((EMVBertTlv) emvEnumeration.nextElement()).getData()).getRawData();
                                if(index<=0)selectedapp=application;
                                Log.d("PHASE B 1", "APPLICATION "+index+":"+TlvUtils.buffertostring(application,""));
                                index++;
                            }
                        }
                    catch (MalFormedTLVException | UnsupportedTagException e)
                        {   Log.e("RECOGNIZE CARD ERR",e.getMessage()); }
                    Log.d("PHASE B 1 END", "CARD RECOGNITION FINISHED status:"+_status+"   interval:"+ (System.currentTimeMillis() - timestamp)+" ms   DATA:"+TlvUtils.buffertostring(data.array(),""));
                    Hashtable<String,String> tags=TlvUtils.decodeemv(data.array());
                    cachedAppindex=tags.get("84");
                    if(cachedAppindex==null)cachedAppindex="";
                    TlvUtils.printtlv(tags,"RECOGNIZE");
                    if(index==0){   OnTransactionResultEMV(false,false,"Cardul nu are aplicatii emv.","","","","","","","","");
                                    Log.e("APPLICATIONS","No applicationns found on the card!!!!!");
                                    closeccr();
                                }//exitcase
                    else initializingTransaction(selectedapp);
                }
            }, atr);
        }
    //STEP 2
    void initializingTransaction(byte[] selectedapp)
        {   final long timestamp = System.currentTimeMillis();


            Log.d("INIT STEP SENT::", TlvUtils.buffertostring(getinitbytebufferdata(selectedapp,g_amount).array(),""));
            Hashtable<String,String> tagssent=TlvUtils.decodeemv(getinitbytebufferdata(selectedapp,g_amount).array());
            TlvUtils.printtlv(tagssent,"INIT STEP SENT");


            Log.d("PHASE B 2 START", "INITIATING TRANSACTION");
            engine.initiateTransaction(ccr, getinitbytebufferdata(selectedapp,g_amount), getinitbytebuffertags(), new CbPhaseCompleted(){
                public void cbPhaseCompleted(ByteBuffer data, int that_status, Request that_request) {
                    Log.d("PHASE B 2 END", "INIT TRANSACTION FINISHED status:"+that_status+"   interval:"+ (System.currentTimeMillis() - timestamp)+" ms  DATA:"+TlvUtils.buffertostring(data.array(),""));
                    Hashtable<String,String> tags=TlvUtils.decodeemv(data.array());
                    cachedKeyIndex=tags.get("8F");
                    if(cachedKeyIndex==null)cachedKeyIndex="";
                    TlvUtils.printtlv(tags,"INIT");
                    processtransaction();
                }
            }, this);
        }
    //STEP 3  (pin pauses this until it's sent)
    void processtransaction()
        {   final long timestamp = System.currentTimeMillis();
            Log.d("PHASE B 3 START", "PROCESSING TRANSACTION");
            ispinnoline=false;

            Log.d("PROCESS STEP SENT::", TlvUtils.buffertostring(getpreocessbytebufferdata().array(),""));
            Hashtable<String,String> tagssent=TlvUtils.decodeemv(getpreocessbytebufferdata().array());
            TlvUtils.printtlv(tagssent,"PROCESS STEP SENT");

            engine.processTransaction(getpreocessbytebufferdata(), getprocessbytebuffertags(),0/* intdata*/, new CbPhaseCompleted (){
                public void cbPhaseCompleted(ByteBuffer data, int _status, Request req) {
                    //int PIN_CORRECT=0x 01 40 01 02;
                    //if (_status == PIN_CORRECT)Log.d("PHASE B 3", "PIN introduced is correct!");
                    Hashtable<String,String> tags=TlvUtils.decodeemv(data.array());
                    String result="";
                    String pinresult="";
                    if((_status & 0x0F00)==0x0200)pinresult="(Pin Gresit/Blocat)";
                    else if((_status & 0x0F00)==0x0100)pinresult="(Pin verificat offline)";
                    else if(ispinnoline)pinresult="(Pin verificat online)";
                    else if((_status & 0x0F00)==0x0000)pinresult="(Fara verificare pin)";
                    if((_status & 0xF)==0x02||(_status & 0xF)==0x01)//online or offline approved
                        {   String[] tagcodes={"5A","5F24","9F26","5F2A","57"};
                            String[] tagnames={"PAN","EXPIRATION_DATE","APPLICATION_CRYPTOGRAM","TRANSACTION_CURRENCY_CODE","TRACK_2_DATA"};
                            String[] tagvalues={"","","","",""};
                            for(int i=0;i<tagcodes.length;i++)
                                if(tags.containsKey(tagcodes[i]))
                                    tagvalues[i]=tags.get(tagcodes[i]);
                                else{   result="Tranzactie esuata. Lipseste informatia "+tagnames[i];
                                        break;
                                    }
                            if(tagvalues[1].length()>4)tagvalues[1]=tagvalues[1].substring(0,4);//exp data must be only 4 characters
                            if(result.length()==0)
                                if(ispinnoline)
                                    {   cachedTransResult=new CachedTransResult(){};
                                        cachedTransResult.message="Tranzactie "+((_status & 0xF)==0x01?"offline":"online")+" finalizata cu success!"+(pinresult.length()>0?"\n"+pinresult:"");
                                        cachedTransResult.pan=tagvalues[0];
                                        cachedTransResult.expdate=tagvalues[1];
                                        cachedTransResult.appcr=tagvalues[2];
                                        cachedTransResult.currencycode=tagvalues[3];
                                        cachedTransResult.tlv="";
                                        cachedTransResult.track2=tagvalues[4];
                                        cachedTransResult.amount=""+g_amount;
                                        cachedTransResult.isonline=(_status & 0xF)==0x02;
                                        EncryptOnlinePin(tagvalues[0]);
                                    }
                                else OnTransactionResultEMV(true,(_status & 0xF)==0x02,
                                        "Tranzactie "+((_status & 0xF)==0x01?"offline":"online")+" finalizata cu success!"+(pinresult.length()>0?"\n"+pinresult:""),
                                        tagvalues[0], tagvalues[1], tagvalues[2], tagvalues[3], "", tagvalues[4], ""+g_amount,"");
                        }
                    else result="Tranzactie esuata. "+(((_status & 0x0F00)==0x0200)?"(Pin Blocat)":"");//not approved
                    if(result.length()>0)
                        {   OnTransactionResultEMV(false,false, result,"","", "", "", "", "", "","");
                        }
                    Log.d("PHASE B 3 END", "PROCESS TRANSACTION finished status:"+_status+"("+EmvStatus.toString(_status)+")  interval:"+(System.currentTimeMillis()-timestamp)+" ms  DATA:"+TlvUtils.buffertostring(data.array(),""));
                    TlvUtils.printtlv(tags,"PROCESS");
                    completetransation();
                }
            }, null);

        }
    //STEP 4
    void completetransation()
        {   final long timestamp = System.currentTimeMillis();
            Log.d("PHASE B 4 START", "COMPLETING TRANSACTION");
            engine.completeTransaction( getcompletebytebufferdata(), getcompletebytebuffertags(), new CbPhaseCompleted(){
                public void cbPhaseCompleted(ByteBuffer data, int _status, Request req) {
                    Log.d("PHASE B 4 END", "COMPLETE TRANSACTION finished status:"+_status+"("+EmvStatus.toString(_status)+")  interval:"+(System.currentTimeMillis()-timestamp)+" ms  DATA:"+TlvUtils.buffertostring(data.array(),""));
                    TlvUtils.printtlv(TlvUtils.decodeemv(data.array()),"COMPLETE");
                    terminatetransaction();
                }
            }, this);
        }
    //STEP 5
    void terminatetransaction()
        {   final long timestamp = System.currentTimeMillis();
            Log.d("PHASE B 5 START", "TERMINATING TRANSACTION");
            engine.terminateTransaction(getterminatebytebuffertags(), new CbPhaseCompleted(){
                public void cbPhaseCompleted(ByteBuffer data, int _status, Request req) {
                    Log.d("PHASE B 5 END", "TERMINATE TRANSACTION finished status:"+_status+"("+EmvStatus.toString(_status)+") interval:"+(System.currentTimeMillis()-timestamp)+" ms  DATA:"+TlvUtils.buffertostring(data.array(),""));
                    TlvUtils.printtlv(TlvUtils.decodeemv(data.array()),"TERMINATE");
                    closeccr();
                }
            }, null);
        }
    //=============================================================================TRASNACTION PHASE C  (closing everything)
    //STEP 1
    void closeccr()
        {   final long timestamp = System.currentTimeMillis();
            Log.d("PHASE C 1 START", "CLOSING CARD READER");
            ccr.close(new CbCloseCompleted() {
                @Override
                public void cbCloseCompleted(Handle handle, Request request)
                {   Log.d("PHASE C 1 END", "CCR Closed finished   interval:"+ (System.currentTimeMillis() - timestamp)+" ms  " );
                    closeengine();
                }
            },null);
        }
    //STEP 2
    void closeengine()
        {   final long timestamp = System.currentTimeMillis();
            Log.d("PHASE C 2 START", "CLOSING ENGINE");
            engine.close();
            Log.d("PHASE C 2 END", "ENGINE Closed finished, sending transaction result,   interval:"+ (System.currentTimeMillis() - timestamp)+" ms  " );


        }

    //=======================================================================================================UNSOLICITED DATA  (PIN CARD REMOVED GIVE MORE DATA)
    private EmvEngineListener myEmvEngineListener = new EmvEngineListener() {
        @Override
        public void dataMissing(ByteBuffer var1, StlvInputMessage var2)
            {   Log.d("PHASE B 3","MISSING DATA: StlvInputMessage  var2: Method:" + var2.getMethod()+"  QueryId:" + var2.getQueryId()+"  ReplyId:" + var2.getReplyId()+" data:"+TlvUtils.buffertostring(var1.array(),""));
                //ByteBuffer myByteBuffer = new ByteBuffer();
                //byte[] myDataBuffer = {(byte)0xe1, (byte)0x00};//9f7c
                // byte[] myDataBuffer = {(byte)0xe1, (byte)0x81, (byte)0x02, (byte)0x9f, (byte)0x7c};
               // myByteBuffer.put(myDataBuffer);
                //myByteBuffer.rewind();
                engine.replyToDataMissing(var2, null);
            }
        @Override
        public void askPin(int var1, StlvInputMessage var2)
            {   Stlv stlv = new Stlv();
                stlv.writeTag((int) 0x9f7c, 4);
                ispinnoline=((var1%(256*256))/256)==1;
                //ispinnoline=true;
                Log.d("PHASE B 3","PIN REQUESTED var1:"+var1+"  ispinonline:"+ispinnoline+"    StlvInputMessage  var2: Method:" + var2.getMethod()+
                        "  QueryId:" + var2.getQueryId()+"  ReplyId:" + var2.getReplyId()+
                        "           stlv = " + stlv.findTag((int) 0x9f7c)+"   StlvInputMessage.handleCurrentTag = " + var2.handleCurrentTag(stlv));
                //var1 = (int) 0x01 40 01 04;//what does this do here???????????

                ask_pin_arg = var2;
                OnPinRequest();
            }

        public void cardRemoved()
            {   Log.d("CARD REMOVED", "CARD REMOVED");
            }

    };

    //=======================================================================================================BUILDING TLV MESSAGES

    //=======================CARD RECOGNITION DATA
    ByteBuffer getcardrecognitionbytebufferdata()
        {   ByteBuffer buf;
            byte[] recognize;
            try {

                EMVConstructedTLVData recognizeData = new EMVConstructedTLVData();
                recognizeData.append(new EMVBertTlv(new EMVTag(SUPPORTED_OPTIONS), new EMVPrimitiveTLVData(new byte[] {(byte) 0x21})));
                recognizeData.append(new EMVBertTlv(new EMVTag(ASCII_CODE_TABLE_INDEX), new EMVPrimitiveTLVData(new byte[] {(byte) 0x01})));
                recognizeData.append(getAidTable());
                {   ArrayList<byte[]> brand_identifiers=new ArrayList<byte[]>();
                    ArrayList<byte[]> chip_flags=new ArrayList<byte[]>();
                    brand_identifiers.add(new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x09});  chip_flags.add(new byte[]{(byte)0x01});
                    brand_identifiers.add(new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x20});  chip_flags.add(new byte[]{(byte)0x01});
                    brand_identifiers.add(new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01});  chip_flags.add(new byte[]{(byte)0x01});
                    brand_identifiers.add(new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x24});  chip_flags.add(new byte[]{(byte)0x01});

                    EMVConstructedTLVData brandTableData = new EMVConstructedTLVData();
                    for(int i=0;i<brand_identifiers.size();i++)
                    {   EMVConstructedTLVData tableRecordData = new EMVConstructedTLVData();
                        tableRecordData.append(new EMVBertTlv(new EMVTag(CHIP_FLAG),        new EMVPrimitiveTLVData(chip_flags.get(i))));
                        tableRecordData.append(new EMVBertTlv(new EMVTag(BRAND_IDENTIFIER), new EMVPrimitiveTLVData(brand_identifiers.get(i))));
                        brandTableData.append(new EMVBertTlv(new EMVTag(RECORD_TABLE),tableRecordData));
                    }
                    recognizeData.append(new EMVBertTlv(new EMVTag(BRAND_TABLE), brandTableData));
                }

                EMVBertTlv emvbertlv=new EMVBertTlv(new EMVTag(CANDIDATE_LIST_GROUP), recognizeData);

                recognize = emvbertlv.getTLV();
                buf = new ByteBuffer(recognize.length);
                buf.put(recognize);
                buf.rewind();

            }catch(Exception e){
                e.printStackTrace();
                return null;
            }
            return buf;
        }

    EMVBertTlv getAidTable()throws UnsupportedTagException, MalFormedTLVException
        {   ArrayList<byte[]> appselectionindicator=new ArrayList<byte[]>();
            ArrayList<byte[]> brandaccepted=new ArrayList<byte[]>();
            ArrayList<byte[]> domestic=new ArrayList<byte[]>();
            ArrayList<byte[]> nextindexifpozitive=new ArrayList<byte[]>();
            ArrayList<byte[]> nextindexifnegative=new ArrayList<byte[]>();
            ArrayList<byte[]> brandidentifier=new ArrayList<byte[]>();
            ArrayList<byte[]> dedicatedfilename=new ArrayList<byte[]>();

            appselectionindicator.add(new byte[]{(byte)0x01});brandaccepted.add(new byte[]{(byte)0x01});domestic.add(new byte[]{(byte)0x00});
            nextindexifpozitive.add(new byte[]{(byte)0x01});nextindexifnegative.add(new byte[]{(byte)0x01});
            brandidentifier.add(new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x24});
            dedicatedfilename.add(new byte[]{(byte)0xa0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x25, (byte)0x01, (byte)0x05, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00});

            appselectionindicator.add(new byte[]{(byte)0x01});brandaccepted.add(new byte[]{(byte)0x01});domestic.add(new byte[]{(byte)0x00});
            nextindexifpozitive.add(new byte[]{(byte)0x02});nextindexifnegative.add(new byte[]{(byte)0x02});
            brandidentifier.add(new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x09});
            dedicatedfilename.add(new byte[]{(byte)0xa0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x04, (byte)0x10, (byte)0x10});

            appselectionindicator.add(new byte[]{(byte)0x01});brandaccepted.add(new byte[]{(byte)0x01});domestic.add(new byte[]{(byte)0x00});
            nextindexifpozitive.add(new byte[]{(byte)0x03});nextindexifnegative.add(new byte[]{(byte)0x03});
            brandidentifier.add(new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x20});
            dedicatedfilename.add(new byte[]{(byte)0xa0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x03, (byte)0x10, (byte)0x10});

            appselectionindicator.add(new byte[]{(byte)0x01});brandaccepted.add(new byte[]{(byte)0x01});domestic.add(new byte[]{(byte)0x00});
            nextindexifpozitive.add(new byte[]{(byte)0x04});nextindexifnegative.add(new byte[]{(byte)0x04});
            brandidentifier.add(new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01});
            dedicatedfilename.add(new byte[]{(byte)0xa0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x65, (byte)0x10, (byte)0x10});

            appselectionindicator.add(new byte[]{(byte)0x01});brandaccepted.add(new byte[]{(byte)0x01});domestic.add(new byte[]{(byte)0x00});
            nextindexifpozitive.add(new byte[]{(byte)0x05});nextindexifnegative.add(new byte[]{(byte)0x05});
            brandidentifier.add(new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x20});
            dedicatedfilename.add(new byte[]{(byte)0xa0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x03, (byte)0x20, (byte)0x10});

            appselectionindicator.add(new byte[]{(byte)0x01});brandaccepted.add(new byte[]{(byte)0x01});domestic.add(new byte[]{(byte)0x00});
            nextindexifpozitive.add(new byte[]{(byte)0x06});nextindexifnegative.add(new byte[]{(byte)0x06});
            brandidentifier.add(new byte[]{(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x09});
            dedicatedfilename.add(new byte[]{(byte)0xa0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x04, (byte)0x20, (byte)0x10});

            EMVConstructedTLVData aidTableData = new EMVConstructedTLVData();
            for(int i=0;i<dedicatedfilename.size();i++)
            {   EMVConstructedTLVData tableRecordData = new EMVConstructedTLVData();
                tableRecordData.append(new EMVBertTlv(new EMVTag(APPLICATION_SELECTION_INDICATOR), new EMVPrimitiveTLVData(appselectionindicator.get(i))));
                tableRecordData.append(new EMVBertTlv(new EMVTag(BRAND_ACCEPTED),                  new EMVPrimitiveTLVData(brandaccepted.get(i))));
                tableRecordData.append(new EMVBertTlv(new EMVTag(DOMESTIC),                        new EMVPrimitiveTLVData(domestic.get(i))));
                tableRecordData.append(new EMVBertTlv(new EMVTag(NEXT_INDEX_IF_POSITIVE),          new EMVPrimitiveTLVData(nextindexifpozitive.get(i))));
                tableRecordData.append(new EMVBertTlv(new EMVTag(NEXT_INDEX_IF_NEGATIVE),          new EMVPrimitiveTLVData(nextindexifnegative.get(i))));
                tableRecordData.append(new EMVBertTlv(new EMVTag(BRAND_IDENTIFIER),                new EMVPrimitiveTLVData(brandidentifier.get(i))));
                tableRecordData.append(new EMVBertTlv(new EMVTag(DEDICATED_FILE_NAME),             new EMVPrimitiveTLVData(dedicatedfilename.get(i))));

                aidTableData.append(new EMVBertTlv(new EMVTag(RECORD_TABLE),tableRecordData));
            }
            return new EMVBertTlv(new EMVTag(AID_TABLE), aidTableData);
        }

    //=======================INIT DATA
    ByteBuffer getinitbytebufferdata(byte[] appid,int _amount)
        {   ByteBuffer toreturn;
            //TAGS VALUE CONFIG
            ArrayList<byte[]> values=new ArrayList<byte[]>();
            ArrayList<byte[]> tags=new ArrayList<byte[]>();

//            byte[] amount=new byte[]{(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00};
//            for(int i=amount.length-1;i>=0;i--)
//                {   amount[i]=(byte)(_amount%256);
//                    _amount/=256;
//                    if(_amount==0)break;
//                }
            byte[] amount=new byte[]{(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00};
            for(int i=amount.length-1;i>=0;i--)
                {
                    amount[i]=(byte)(_amount%10+(_amount%100)/10*16);
                    _amount/=100;
                    if(_amount==0)break;
                }

            //TERMINAL_COUNTRY_CODE,TERMINAL_FLOOR_LIM,TRANSACTION_CURRENCY_CODE,amount, all tacs

            //appid:0x40 00 0x12 0x10 0x18 0x1 00 00
            //term country code:  (byte)0x00,(byte)0x56
            //amount:new byte[]{(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x09};
            //amount=new byte[]{(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x09};
            //floor limit: (byte)0x00,(byte)0x00,(byte)0x27,(byte)0x10
            //transaction currency code: (byte)0x09,(byte)0x78
            //tac default: (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00
            //denial: (byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00
            //online:(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00

            tags.add(APPLICATION_ID);              values.add(appid);
            tags.add(APPLICATION_VERSION_NUMBER);  values.add(new byte[] {(byte)0x00,(byte)0x02 });
            tags.add(MERCHANT_CAT);                values.add(new byte[] {(byte)0x89,(byte)0x99 });
            tags.add(MERCHANT_ID);     values.add(new byte[] {(byte)0x31,(byte)0x36,(byte)0x31,(byte)0x38,(byte)0x33,(byte)0x34,(byte)0x32,(byte)0x38,
                (byte)0x20,(byte)0x20,(byte)0x20,(byte)0x20,(byte)0x20,(byte)0x20,(byte)0x20});
            tags.add(TERMINAL_COUNTRY_CODE);   values.add(new byte[] {(byte)0x06,(byte)0x42 });//(byte)0x00,(byte)0x56
            tags.add(TRANSACTION_DATE);        values.add(new byte[] {(byte)0x19,(byte)0x03,(byte)0x07});//(byte)0x19,(byte)0x03,(byte)0x07
            tags.add(TERMINAL_FLOOR_LIM);      values.add(new byte[] {(byte)0x00,(byte)0x01,(byte)0x00,(byte)0x00 });//(byte)0x00,(byte)0x00,(byte)0x27,(byte)0x10 }
            tags.add(TERMINAL_ID);             values.add(new byte[] {(byte)0x30,(byte)0x31,(byte)0x30,(byte)0x32, (byte)0x30,(byte)0x33,(byte)0x30,(byte)0x34 });
            tags.add(IFD_SER_NR);              values.add(new byte[] {(byte)0x30,(byte)0x31,(byte)0x31,(byte)0x34, (byte)0x39,(byte)0x39,(byte)0x39,(byte)0x34 });
            tags.add(TRANSACTION_TIME);        values.add(new byte[] {(byte)0x17,(byte)0x53,(byte)0x11 });//(byte)0x17,(byte)0x53,(byte)0x11
            tags.add(TRANSACTION_CURRENCY_CODE);   values.add(new byte[] {(byte)0x09,(byte)0x46 });//(byte)0x09,(byte)0x78
                int _online=64,_oflineplain=128,_offlineencripted=16,_nocvm=8;
            tags.add(TERMINAL_CAPABILITIES);       values.add(new byte[] {(byte)0x20,(byte)(1*_online+1*_offlineencripted+0*_nocvm),(byte)0xFF });//20 D7 FF   60  80  00    60 90 FF     2nd:D7       //(byte)0x60,(byte)0x80,(byte)0x00
            //termcap book4 pg 131, b1:20(only icc,no mag),(if plainoffl and encroffl are both entered, dupped pin request will happen)
            // b2:128*plainofline+64*encronlinepin+16*encrofflpin+8*nocvm  [144:offline, 64:online, 208:ofl+onl,216:ofl+onl+nocvm],     b3:128*sda+64*dda+32*cardcapture+8*cda
            tags.add(TAG_DF34);                    values.add(new byte[] {(byte)0x18,(byte)0x00 });
            tags.add(TERMINAL_TYPE);               values.add(new byte[] {(byte)0x11 });//(byte)0x11    //11-<??  25 is online+offline capabilities
            tags.add(TERMINAL_SPECIFIC_CVM);       values.add(new byte[] {(byte)0x04,(byte)0x00 }); // 80 00
            tags.add(TRANSACTION_CURRENCY_EXPONENT);   values.add(new byte[] {(byte)0x02 });
            tags.add(POS_ENTRY_MODE);                  values.add(new byte[] {(byte)0x00 });
            tags.add(EMV_ENGINE_MODE);                 values.add(new byte[] {(byte)0x80 });
            tags.add(ADDITIONAL_TERMINAL_CAPABILITIES);values.add(new byte[] {(byte)0x80,(byte)0x00,(byte)0xf0,(byte)0xa0,(byte)0x01 });
            tags.add(TRANSACTION_SEQUENCE_COUNTER);    values.add(new byte[] {(byte)0x00,(byte)0x00,(byte)0x42,(byte)0x20 });
            tags.add(AUTHORIZED_AMOUNT);               values.add(amount);
            tags.add(TAC_DEFAULT);                     values.add(new byte[] {(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00 });//{(byte)0x80,(byte)0x80,0x50,(byte)0xF4,0x00 });
            tags.add(TAC_DENIAL);                      values.add(new byte[] {(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x0C });//{(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x0C });
            tags.add(TAC_ONLINE);                      values.add(new byte[] {(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x0C });//{(byte)0x80,(byte)0x80,(byte)0x50,(byte)0xF4,0x14 });
            tags.add(TARGET_PERCENTAGE_FOR_RANDOM_SELECTION);          values.add(new byte[] {(byte)0x00 });
            tags.add(MAX_PERCENTAGE_FOR_RANDOM_SELECTION);             values.add(new byte[] {(byte)0x00 });
            tags.add(THRESHOLD_VALUE_PERCENTAGE_FOR_RANDOM_SELECTION); values.add(new byte[] {(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x50,(byte)0x00 });
            tags.add(DEFAULT_DDOL);    values.add(new byte[] {(byte)0x9f,(byte)0x37,(byte)0x04,(byte)0x9f,(byte)0x47,(byte)0x01,(byte)0x8f, (byte)0x01,(byte)0x9f,(byte)0x32,(byte)0x01 });
            tags.add(DEFAULT_TDOL);    values.add(new byte[] {(byte)0x9f,(byte)0x08,(byte)0x02 });
            //tags.add(new byte[]{(byte)0x98});    values.add(new byte[] {(byte)0x01});
            try{//TAGS VALUES PROCESSING
                EMVConstructedTLVData initiateTransactionData = new EMVConstructedTLVData();
                for(int i=0;i<tags.size();i++)
                {   EMVPrimitiveTLVData tlvdata=new EMVPrimitiveTLVData(values.get(i));
                    if(tlvdata!=null)initiateTransactionData.append(new EMVBertTlv(  new EMVTag(tags.get(i)), tlvdata));
                    else Log.e("Error null buffer","Error at building init transaction buffer index:"+i+" "+tags.get(i));
                }
                //BUILDING RETURN THINGY
                byte[] initiate_transaction = new EMVBertTlv(new EMVTag(CANDIDATE_LIST_GROUP), initiateTransactionData).getTLV();
                toreturn = new ByteBuffer(initiate_transaction.length);
                toreturn.put(initiate_transaction);
                toreturn.rewind();
                return toreturn;
            }
            catch (UnsupportedTagException | MalFormedTLVException e)
            {   Log.e("HORRIBLE ERROR:",e.toString());
                e.printStackTrace();
                return new ByteBuffer();
            }
        }
    ByteBuffer getinitbytebuffertags()
        {   byte[] init_tags = new byte[]
                {   (byte)0xe0,   (byte)0x13,
                    (byte)0x9f,(byte)0x0a,        // Application Selection Registered Proprietary Data
                    (byte)0x9f,(byte)0x0b,        // Cardholder Name Extended
                    (byte)0x8f,                   // CA Public key index
                    (byte)0x95,                   // Terminal Verification Results
                    (byte)0x9f,(byte)0x19,
                    (byte)0x9b,                   // Transaction Status Information
                    (byte)0x5f,(byte)0x20,        // Cardholder name
                    (byte)0x9f,(byte)0x24,        // Payment Account Reference (MCL)
                    (byte)0x9f,(byte)0x25,
                    (byte)0x5f,(byte)0x34,        // Application Primary Account Number (PAN) Sequence Number
                    (byte)0x5a,                    // Application Primary Account Number (PAN)
                    (byte)0x8C                   // CDOL1
                };
            ByteBuffer buf = new ByteBuffer(init_tags.length);
            buf.put(init_tags);
            buf.rewind();
            return buf;
        }
    //=====================PROCESS TRANSACTION DATA
    ByteBuffer getpreocessbytebufferdata()
        {   ByteBuffer buf;
            byte[] process_transaction;
            try {   byte[] mod={};
                    byte exponent=0x03;//cachedKeyIndex="05";
                    Log.d("VALUES:",cachedKeyIndex+" "+cachedAppindex);
                    for (KeyData key:publickeysprod)
                        {   //Log.e("OPTION","expdate:"+key.expdate+" list:["+key.indexlist+"|"+cachedAppindex+"] index:["+key.index+"|"+cachedKeyIndex+"] issuer:"+
                              //  key.issuer+" type:"+key.type+" "+(key.index.equals(cachedKeyIndex))+(cachedAppindex.startsWith(key.indexlist)));
                            if(key.index.equals(cachedKeyIndex)&&cachedAppindex.startsWith(key.indexlist))
                                {   mod=key.getModulus();
                                    Log.e("KEY SELECTED","expdate:"+key.expdate+" list:"+key.indexlist+" index:"+key.index+" issuer:"+key.issuer+" type:"+key.type);
                                    exponent=(byte)key.getExponent();;
                                    break;
                                }
                        }
                    if(mod.length<3)Log.e("ERROR KEY","COULD NOT FIND KEY!!!!!!");

                EMVConstructedTLVData processTransactionData = new EMVConstructedTLVData();
                processTransactionData.append(new EMVBertTlv(new EMVTag(TRANSACTION_TYPE), new EMVPrimitiveTLVData(new byte[] {(byte)0x01 })));
                processTransactionData.append(new EMVBertTlv(new EMVTag(CA_PUBLIC_KEY_MODULUS), new EMVPrimitiveTLVData(mod
                )));
                processTransactionData.append(new EMVBertTlv(new EMVTag(CA_PUBLIC_KEY_EXPONENT), new EMVPrimitiveTLVData(new byte[] {(byte)0x00,(byte)0x00,exponent })));
                EMVBertTlv  berTLV=new EMVBertTlv(new EMVTag(CANDIDATE_LIST_GROUP), processTransactionData);
                process_transaction = berTLV.getTLV();
                buf = new ByteBuffer(process_transaction.length);
                buf.put(process_transaction);
                buf.rewind();
            }catch(Exception e){
                e.printStackTrace();
                return null;
            }
            return buf;
        }
    public ByteBuffer getprocessbytebuffertags()
        {   ByteBuffer buf;
            byte[] tags_expected = {
                    (byte)0xe0,    (byte)0x52,
                    (byte)0x9f,(byte)0x01,      // Acquirer Identifier
                    (byte)0x82,                 // Application Interchange Profile
                    (byte)0x9f,(byte)0x02,      // Amount Authorised
                    (byte)0x9f,(byte)0x03,      // Amount, Other
                    (byte)0x9f,(byte)0x07,      // Application Usage Control
                    (byte)0x9f,(byte)0x0a,      //
                    (byte)0x9f,(byte)0x0d,      // Issuer Action Code - Default
                    (byte)0x8e,                 // Cardholder Verification Method (CVM) List
                    (byte)0x9f,(byte)0x0e,      // Issuer Action Code - Denial
                    (byte)0x9f,(byte)0x0f,      // Issuer Action Code - Online
                    (byte)0x9f,(byte)0x10,      // Issuer Application Data
                    (byte)0x9f,(byte)0x15,      // Merchant Category Code
                    (byte)0x95,//23                 // Terminal Verification Results
                    (byte)0x9f,(byte)0x16,      // Merchant Identifier
                    (byte)0x9f,(byte)0x19,      //
                    (byte)0x9f,(byte)0x1a,      // Terminal Country Code
                    (byte)0x9a,                 // Transaction Date
                    (byte)0x9b,                 // Transaction Status Information
                    (byte)0x9f,(byte)0x1c,      // Terminal Identification
                    (byte)0x9c,                 // Transaction Type
                    (byte)0x9f,(byte)0x1e,      // Interface Device (IFD) Serial Number
                    (byte)0x9f,(byte)0x21,      // Transaction Time
                    (byte)0x9f,(byte)0x24,      // Payment Account Reference (MCL)
                    (byte)0x5f,(byte)0x24,      // Application Expiration Date
                    (byte)0x9f,(byte)0x25,
                    (byte)0x5f,(byte)0x25,      // Application Effective Date
                    (byte)0x9f,(byte)0x26,      // Application Cryptogram
                    (byte)0x9f,(byte)0x27,      // Cryptogram Information Data
                    (byte)0x5f,(byte)0x28,      // Issuer Country Code
                    (byte)0x5f,(byte)0x2a,      // Transaction Currency Code
                    (byte)0x9f,(byte)0x33,      // Terminal Capabilities
                    (byte)0x5f,(byte)0x34,      // Application Primary Account Number (PAN) Sequence Number
                    (byte)0x9f,(byte)0x34,      // Cardholder Verification Method (CVM) Results
                    (byte)0x9f,(byte)0x35,      // Terminal Type
                    (byte)0x9f,(byte)0x36,//64      // Application Transaction Counter (ATC)
                    (byte)0x9f,(byte)0x37,      // Unpredictable Number
                    (byte)0x9f,(byte)0x38,      // Processing Options Data Object List (PDOL)
                    (byte)0x9f,(byte)0x39,      // Point-of-Service (POS) Entry Mode
                    (byte)0x9f,(byte)0x4c,      // ICC Dynamic Number
                    (byte)0xdf,(byte)0x50,      // transaction amount
                    //(byte)0x9f,(byte)0x62,    // PIN_BLOCK!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                    (byte)0xdf,(byte)0x55,      // Issuer script results
                    (byte)0xdf,(byte)0x81,(byte)0x55,
                    (byte)0x57,                  // Track 2 Equivalent Data
                    (byte)0x5a,                  // Application Primary Account Number (PAN)
                    (byte)0x8C                   // CDOL1
            };
            buf = new ByteBuffer(tags_expected.length);
            buf.put(tags_expected);
            buf.rewind();
            return buf;
        }
    //=====================COMPLETE TRANSACTION DATA
    ByteBuffer getcompletebytebufferdata()
        {   ByteBuffer buf=null;
            byte[] completeBuff = new byte[]{//e0:9c, df53(key modulo),df54(key exponent)
                     //(byte)0xe0,(byte)0x81,(byte)0x9d,(byte)0x9c,(byte)0x01,(byte)0x00,(byte)0xdf,(byte)0x53
                    (byte)0xe0,(byte)0x81,(byte)0xa6,(byte)0x9c,(byte)0x01,(byte)0x00,(byte)0xdf,(byte)0x53//shis line is new to change the size for the last line
                    ,(byte)0x81,(byte)0x90,(byte)0x98,(byte)0xf0,(byte)0xc7,(byte)0x70,(byte)0xf2,(byte)0x38
                    ,(byte)0x64,(byte)0xc2,(byte)0xe7,(byte)0x66,(byte)0xdf,(byte)0x02,(byte)0xd1,(byte)0xe8
                    ,(byte)0x33,(byte)0xdf,(byte)0xf4,(byte)0xff,(byte)0xe9,(byte)0x2d,(byte)0x69,(byte)0x6e
                    ,(byte)0x16,(byte)0x42,(byte)0xf0,(byte)0xa8,(byte)0x8c,(byte)0x56,(byte)0x94,(byte)0xc6
                    ,(byte)0x47,(byte)0x9d,(byte)0x16,(byte)0xdb,(byte)0x15,(byte)0x37,(byte)0xbf,(byte)0xe2
                    ,(byte)0x9e,(byte)0x4f,(byte)0xdc,(byte)0x6e,(byte)0x6e,(byte)0x8a,(byte)0xfd,(byte)0x1b
                    ,(byte)0x0e,(byte)0xb7,(byte)0xea,(byte)0x01,(byte)0x24,(byte)0x72,(byte)0x3c,(byte)0x33
                    ,(byte)0x31,(byte)0x79,(byte)0xbf,(byte)0x19,(byte)0xe9,(byte)0x3f,(byte)0x10,(byte)0x65
                    ,(byte)0x8b,(byte)0x2f,(byte)0x77,(byte)0x6e,(byte)0x82,(byte)0x9e,(byte)0x87,(byte)0xda
                    ,(byte)0xed,(byte)0xa9,(byte)0xc9,(byte)0x4a,(byte)0x8b,(byte)0x33,(byte)0x82,(byte)0x19
                    ,(byte)0x9a,(byte)0x35,(byte)0x0c,(byte)0x07,(byte)0x79,(byte)0x77,(byte)0xc9,(byte)0x7a
                    ,(byte)0xff,(byte)0x08,(byte)0xfd,(byte)0x11,(byte)0x31,(byte)0x0a,(byte)0xc9,(byte)0x50
                    ,(byte)0xa7,(byte)0x2c,(byte)0x3c,(byte)0xa5,(byte)0x00,(byte)0x2e,(byte)0xf5,(byte)0x13
                    ,(byte)0xfc,(byte)0xcc,(byte)0x28,(byte)0x6e,(byte)0x64,(byte)0x6e,(byte)0x3c,(byte)0x53
                    ,(byte)0x87,(byte)0x53,(byte)0x5d,(byte)0x50,(byte)0x95,(byte)0x14,(byte)0xb3,(byte)0xb3
                    ,(byte)0x26,(byte)0xe1,(byte)0x23,(byte)0x4f,(byte)0x9c,(byte)0xb4,(byte)0x8c,(byte)0x36
                    ,(byte)0xdd,(byte)0xd4,(byte)0x4b,(byte)0x41,(byte)0x6d,(byte)0x23,(byte)0x65,(byte)0x40
                    ,(byte)0x34,(byte)0xa6,(byte)0x6f,(byte)0x40,(byte)0x3b,(byte)0xa5,(byte)0x11,(byte)0xc5
                    ,(byte)0xef,(byte)0xa3,(byte)0xdf,(byte)0x54,(byte)0x03,(byte)0x00,(byte)0x00,(byte)0x03
                    ,(byte)0x8a,(byte)0x02,(byte)0x30,(byte)0x30, (byte)0xdf,(byte)0x2e,(byte)0x02,(byte)0x30,(byte)0x30//this line is new  3030 approved, 3031 declined
                    //,(byte)0x8a,(byte)0x02,(byte)0x59,(byte)0x33, (byte)0xdf,(byte)0x82e,(byte)0x02,(byte)0x30,(byte)0x30//this line is new  3030 approved, 3031 declined
            };
            try {buf = new ByteBuffer(completeBuff.length);
                buf.put(completeBuff);
            }
            catch(Exception e)
            {   e.printStackTrace();
            }
            return buf;
        }

    ByteBuffer getcompletebytebuffertags ()
        {   byte[] init_tags = new byte[]{
                //option1: nothing
                (byte)0xe0,(byte)0x81,(byte)0x00
                //option2: what was before
//                    (byte)0xe0,(byte)0x46,(byte)0x9f,(byte)0x01,(byte)0x82,(byte)0x9f,(byte)0x02,(byte)0x9f
//                    ,(byte)0x03,(byte)0x9f,(byte)0x07,(byte)0x9f,(byte)0x0d,(byte)0x8e,(byte)0x9f,(byte)0x0e
//                    ,(byte)0x9f,(byte)0x0f,(byte)0x9f,(byte)0x10,(byte)0x9f,(byte)0x15,(byte)0x95,(byte)0x9f
//                    ,(byte)0x16,(byte)0x9f,(byte)0x1a,(byte)0x9a,(byte)0x9b,(byte)0x9f,(byte)0x1c,(byte)0x9c
//                    ,(byte)0x9f,(byte)0x1e,(byte)0x9f,(byte)0x21,(byte)0x5f,(byte)0x24,(byte)0x5f,(byte)0x25
//                    ,(byte)0x9f,(byte)0x26,(byte)0x9f,(byte)0x27,(byte)0x5f,(byte)0x28,(byte)0x5f,(byte)0x2a
//                    ,(byte)0x9f,(byte)0x33,(byte)0x5f,(byte)0x34,(byte)0x9f,(byte)0x34,(byte)0x9f,(byte)0x35
//                    ,(byte)0x9f,(byte)0x36,(byte)0x9f,(byte)0x37,(byte)0x9f,(byte)0x38,(byte)0x9f,(byte)0x39
//                    ,(byte)0x9f,(byte)0x4c,(byte)0xdf,(byte)0x50,(byte)0xdf,(byte)0x55,(byte)0x57,(byte)0x5a
                //option3: alternative from giovanni's sample
    //                 (byte)0xe0,(byte)0x81,(byte)0x9d,(byte)0x9c,(byte)0x01,(byte)0x00,(byte)0xdf,(byte)0x53
    //                ,(byte)0x81,(byte)0x90,(byte)0x98,(byte)0xf0,(byte)0xc7,(byte)0x70,(byte)0xf2,(byte)0x38
    //                ,(byte)0x64,(byte)0xc2,(byte)0xe7,(byte)0x66,(byte)0xdf,(byte)0x02,(byte)0xd1,(byte)0xe8
    //                ,(byte)0x33,(byte)0xdf,(byte)0xf4,(byte)0xff,(byte)0xe9,(byte)0x2d,(byte)0x69,(byte)0x6e
    //                ,(byte)0x16,(byte)0x42,(byte)0xf0,(byte)0xa8,(byte)0x8c,(byte)0x56,(byte)0x94,(byte)0xc6
    //                ,(byte)0x47,(byte)0x9d,(byte)0x16,(byte)0xdb,(byte)0x15,(byte)0x37,(byte)0xbf,(byte)0xe2
    //                ,(byte)0x9e,(byte)0x4f,(byte)0xdc,(byte)0x6e,(byte)0x6e,(byte)0x8a,(byte)0xfd,(byte)0x1b
    //                ,(byte)0x0e,(byte)0xb7,(byte)0xea,(byte)0x01,(byte)0x24,(byte)0x72,(byte)0x3c,(byte)0x33
    //                ,(byte)0x31,(byte)0x79,(byte)0xbf,(byte)0x19,(byte)0xe9,(byte)0x3f,(byte)0x10,(byte)0x65
    //                ,(byte)0x8b,(byte)0x2f,(byte)0x77,(byte)0x6e,(byte)0x82,(byte)0x9e,(byte)0x87,(byte)0xda
    //                ,(byte)0xed,(byte)0xa9,(byte)0xc9,(byte)0x4a,(byte)0x8b,(byte)0x33,(byte)0x82,(byte)0x19
    //                ,(byte)0x9a,(byte)0x35,(byte)0x0c,(byte)0x07,(byte)0x79,(byte)0x77,(byte)0xc9,(byte)0x7a
    //                ,(byte)0xff,(byte)0x08,(byte)0xfd,(byte)0x11,(byte)0x31,(byte)0x0a,(byte)0xc9,(byte)0x50
    //                ,(byte)0xa7,(byte)0x2c,(byte)0x3c,(byte)0xa5,(byte)0x00,(byte)0x2e,(byte)0xf5,(byte)0x13
    //                ,(byte)0xfc,(byte)0xcc,(byte)0x28,(byte)0x6e,(byte)0x64,(byte)0x6e,(byte)0x3c,(byte)0x53
    //                ,(byte)0x87,(byte)0x53,(byte)0x5d,(byte)0x50,(byte)0x95,(byte)0x14,(byte)0xb3,(byte)0xb3
    //                ,(byte)0x26,(byte)0xe1,(byte)0x23,(byte)0x4f,(byte)0x9c,(byte)0xb4,(byte)0x8c,(byte)0x36
    //                ,(byte)0xdd,(byte)0xd4,(byte)0x4b,(byte)0x41,(byte)0x6d,(byte)0x23,(byte)0x65,(byte)0x40
    //                ,(byte)0x34,(byte)0xa6,(byte)0x6f,(byte)0x40,(byte)0x3b,(byte)0xa5,(byte)0x11,(byte)0xc5
    //                ,(byte)0xef,(byte)0xa3,(byte)0xdf,(byte)0x54,(byte)0x03,(byte)0x00,(byte)0x00,(byte)0x03
            };
            ByteBuffer buf = new ByteBuffer(init_tags.length);
            buf.put(init_tags);
            return buf;
        }
    //=====================TERMINATE DATA
    ByteBuffer getterminatebytebuffertags()
        {   byte[] init_tags = new byte[]{
                    (byte)0xe0,(byte)0x0a,
                    (byte)0x9f,(byte)0x36,
                    (byte)0xdf,(byte)0x81,(byte)0x53,
                    (byte)0x9f,(byte)0x38,
                    (byte)0xdf,(byte)0x93,(byte)0x12
                //alternate from giovani's smaple
//                (byte)0xe0,(byte)0x46,(byte)0x9f,(byte)0x01,(byte)0x82,(byte)0x9f,(byte)0x02,(byte)0x9f
//                ,(byte)0x03,(byte)0x9f,(byte)0x07,(byte)0x9f,(byte)0x0d,(byte)0x8e,(byte)0x9f,(byte)0x0e
//                ,(byte)0x9f,(byte)0x0f,(byte)0x9f,(byte)0x10,(byte)0x9f,(byte)0x15,(byte)0x95,(byte)0x9f
//                ,(byte)0x16,(byte)0x9f,(byte)0x1a,(byte)0x9a,(byte)0x9b,(byte)0x9f,(byte)0x1c,(byte)0x9c
//                ,(byte)0x9f,(byte)0x1e,(byte)0x9f,(byte)0x21,(byte)0x5f,(byte)0x24,(byte)0x5f,(byte)0x25
//                ,(byte)0x9f,(byte)0x26,(byte)0x9f,(byte)0x27,(byte)0x5f,(byte)0x28,(byte)0x5f,(byte)0x2a
//                ,(byte)0x9f,(byte)0x33,(byte)0x5f,(byte)0x34,(byte)0x9f,(byte)0x34,(byte)0x9f,(byte)0x35
//                ,(byte)0x9f,(byte)0x36,(byte)0x9f,(byte)0x37,(byte)0x9f,(byte)0x38,(byte)0x9f,(byte)0x39
//                ,(byte)0x9f,(byte)0x4c,(byte)0xdf,(byte)0x50,(byte)0xdf,(byte)0x55,(byte)0x57,(byte)0x5a
            };
            ByteBuffer buf = new ByteBuffer(init_tags.length);
            buf.put(init_tags);
            buf.rewind();
            return buf;
        }









    private static void runOnMapsThread(Runnable task)
        {   MapsRuntime.getRuntime().callSerially(task);
        }






























    //--------------------------------------------
    // card recognition
    //--------------------------------------------
    public static byte[] DEDICATED_FILE_NAME             = {(byte)0x84};
    public static byte[] TRANSACTION_TYPE                = {(byte)0x9C};

    public static byte[] CANDIDATE_LIST_GROUP            = {(byte)0xE0};
    public static byte[] MESSAGE_GROUP                   = {(byte)0xE0};
    public static byte[] AID_TABLE                       = {(byte)0xE5};
    public static byte[] RECORD_TABLE                    = {(byte)0xEF};
    public static byte[] BRAND_TABLE                     = {(byte)0xFB};

    public static byte[] ASCII_CODE_TABLE_INDEX          = {(byte)0xDF, (byte)0x81, (byte)0x72};
    public static byte[] CHIP_FLAG                       = {(byte)0xDF, (byte)0x81, (byte)0x73};
    public static byte[] BRAND_ACCEPTED                  = {(byte)0xDF, (byte)0x81, (byte)0x74};
    public static byte[] APPLICATION_SELECTION_INDICATOR = {(byte)0xDF, (byte)0x81, (byte)0x75};
    public static byte[] SUPPORTED_OPTIONS               = {(byte)0xDF, (byte)0x81, (byte)0x78};

    public static byte[] NEXT_INDEX_IF_POSITIVE          = {(byte)0xDF, (byte)0x90, (byte)0x07};
    public static byte[] NEXT_INDEX_IF_NEGATIVE          = {(byte)0xDF, (byte)0x90, (byte)0x08};
    public static byte[] DOMESTIC                        = {(byte)0xDF, (byte)0x90, (byte)0x09};



    //--------------------------------------------
    // Initiate transaction
    //--------------------------------------------
    public static byte[] TRANSACTION_DATE             = {(byte)0x9a};

    public static byte[] APPLICATION_ID             = {(byte)0x9f,(byte)0x06};
    public static byte[] APPLICATION_VERSION_NUMBER = {(byte)0x9f,(byte)0x09};
    public static byte[] MERCHANT_ID                = {(byte)0x9f,(byte)0x16};
    public static byte[] MERCHANT_CAT               = {(byte)0x9f,(byte)0x15};
    public static byte[] TERMINAL_COUNTRY_CODE      = {(byte)0x9f,(byte)0x1a};
    public static byte[] TERMINAL_FLOOR_LIM         = {(byte)0x9f,(byte)0x1b};//why was this 9f1b??? should be df1b
    public static byte[] TERMINAL_ID                = {(byte)0x9f,(byte)0x1c};
    public static byte[] IFD_SER_NR                 = {(byte)0x9f,(byte)0x1e};
    public static byte[] TRANSACTION_TIME           = {(byte)0x9f,(byte)0x21};
    public static byte[] TERMINAL_CAPABILITIES      = {(byte)0x9f,(byte)0x33};
    public static byte[] TERMINAL_TYPE              = {(byte)0x9f,(byte)0x35};
    public static byte[] POS_ENTRY_MODE             = {(byte)0x9f,(byte)0x39};
    public static byte[] ADDITIONAL_TERMINAL_CAPABILITIES  = {(byte)0x9f,(byte)0x40};
    public static byte[] TRANSACTION_SEQUENCE_COUNTER      = {(byte)0x9f,(byte)0x41};

    public static byte[] TAG_DF34                        = {(byte)0xDF, (byte)0x34};
    public static byte[] EMV_ENGINE_MODE                 = {(byte)0xDF, (byte)0x3A};

    public static byte[] CA_PUBLIC_KEY_MODULUS           = {(byte)0xDF, (byte)0x53};
    public static byte[] CA_PUBLIC_KEY_EXPONENT          = {(byte)0xDF, (byte)0x54};
    public static byte[] BRAND_IDENTIFIER                = {(byte)0xDF, (byte)0x5F};

    public static byte[] TAC_DEFAULT        = {(byte)0xdf,(byte)0x56};
    public static byte[] TAC_DENIAL         = {(byte)0xdf,(byte)0x57};
    public static byte[] TAC_ONLINE         = {(byte)0xdf,(byte)0x58};

    public static byte[] TARGET_PERCENTAGE_FOR_RANDOM_SELECTION          = {(byte)0xDF,(byte)0x5A};
    public static byte[] MAX_PERCENTAGE_FOR_RANDOM_SELECTION             = {(byte)0xDF,(byte)0x5B};
    public static byte[] THRESHOLD_VALUE_PERCENTAGE_FOR_RANDOM_SELECTION = {(byte)0xDF,(byte)0x5C};
    public static byte[] DEFAULT_DDOL           = {(byte)0xdf,(byte)0x5d};
    public static byte[] DEFAULT_TDOL           = {(byte)0xdf,(byte)0x5e};

    public static byte[] OFFLINE_TOTAL_AMOUNT = {(byte)0xDF,(byte)0x52};
    public static byte[] OTHER_AMOUNT         = {(byte)0x9F,(byte)0x03};

    public static byte[] TAG_9F7C             = {(byte)0x9F,(byte)0x7c};
    // EXPTAG_INIT
    public static byte[] PAN                              = {(byte)0x5A};
    public static byte[] CA_PUBLIC_KEY                    = {(byte)0x8F};
    public static byte[] TERMINAL_VERIFICATION_RESULT     = {(byte)0x95};
    public static byte[] TRANSACTION_STATUS_INFO          = {(byte)0x9B};
    public static byte[] CARD_HOLDER_NAME_EXT             = {(byte)0x9F,(byte)0x0B};
    public static byte[] CARD_HOLDER_NAME_                = {(byte)0x5F,(byte)0x20};
    public static byte[] AAPPLICATION_PAN_SEQUENCE_NUMBER = {(byte)0x5F,(byte)0x34};
    public static byte[] PAYMENT_ACCOUNT_REFERENCE        = {(byte)0x9F,(byte)0x24};
    public static byte[] APPLICATION_SELECTION_REG_DATA   = {(byte)0x9F,(byte)0x0A};

    public static byte[] EXPIRATION_DATE                  = {(byte)0x5f,(byte)0x24};
    //   [READ_DATA_INIT_OK]



    //--------------------------------------------
    // Process transaction
    //--------------------------------------------
    //    [DATA_PROCESS]


    //    [EXPTAG_PROCESS]
    //public static byte[] TERMINAL_VERIFICATION_RESULT  = {(byte)0x95};
    //public static byte[] TRANSACTION_STATUS_INFO       = {(byte)0x9B};
    public static byte[] ICC_DYN_NUMB                    = {(byte)0x9F,(byte)0x4C};
    public static byte[] APPLICATION_TRANS_COUNTER       = {(byte)0x9F,(byte)0x36};
    public static byte[] ISSUER_APPLICATION_DATA         = {(byte)0x9F,(byte)0x10};
    public static byte[] APPLICATION_CRYPTOGRAM          = {(byte)0x9F,(byte)0x26};
    public static byte[] CRYPTOGRAM_INFO_DATA            = {(byte)0x9F,(byte)0x27};
    public static byte[] PDOL                            = {(byte)0x9F,(byte)0x38};
    public static byte[] AUTHORIZED_AMOUNT               = {(byte)0xDF,(byte)0x50};
    public static byte[] TRANSACTION_CURRENCY_CODE       = {(byte)0x5F,(byte)0x2A};
    public static byte[] TRANSACTION_CURRENCY_EXPONENT   = {(byte)0x5F,(byte)0x36};
    public static byte[] TERMINAL_SPECIFIC_CVM           = {(byte)0xDF,(byte)0x35};

    //public static byte[] ICC_DYN_NUMB                  = {(byte)0x9F,(byte)0x4C};
    public static byte[] REQUEST_ISSUER_SCRIPT_RESULT     = {(byte)0xDF,(byte)0x55};




    // READ_DATA_PROCESS_OK


    //--------------------------------------------
    // Complete transaction
    //--------------------------------------------

    // [DATA_COMPLETE]


    // [EXPTAG_COMPLETE]
    // public static byte[] TERMINAL_VERIFICATION_RESULT = {(byte)0x95};
    // public static byte[] TRANSACTION_STATUS_INFO      = {(byte)0x9B};
    // public static byte[] ICC_DYN_NUMB                 = {(byte)0x9F,(byte)0x4C};
    // public static byte[] APPLICATION_TRANS_COUNTER    = {(byte)0x9F,(byte)0x36};
    // public static byte[] ISSUER_APPLICATION_DATA      = {(byte)0x9F,(byte)0x10};
    // public static byte[] APPLICATION_CRYPTOGRAM       = {(byte)0x9F,(byte)0x26};
    // public static byte[] CRYPTOGRAM_INFO_DATA         = {(byte)0x9F,(byte)0x27};
    // public static byte[] PDOL                         = {(byte)0x9F,(byte)0x38};
    // public static byte[] AUTHORIZED_AMOUNT            = {(byte)0xDF,(byte)0x50};
    // public static byte[] ICC_DYN_NUMB                 = {(byte)0x9F,(byte)0x4C};
    // public static byte[] REQUEST_ISSUER_SCRIPT_RESULT = {(byte)0xDF,(byte)0x55};





    public static byte[] TRACK_2_DATA     = {(byte)0x57};
    public static byte[] PIN_BLOCK     = {(byte)0x9f,(byte)0x62};




    class KeyData
        {   public String issuer="",exponent="",index="",indexlist="",modulus="",length="",sha1="",type="",expdate="";

            public KeyData(String issuer, String exponent, String index, String indexlist, String modulus, String length, String sha1, String type, String expdate) {
                this.issuer = issuer;
                this.exponent = exponent;
                this.index = index;
                this.indexlist = indexlist;
                this.modulus = modulus;
                this.length = length;
                this.sha1 = sha1;
                this.type = type;
                this.expdate = expdate;
            }

            public byte[] getModulus() {
                byte[] toreturn=new byte[modulus.length()/2];
                for(int i=0;i<modulus.length();i+=2)
                    toreturn[i/2]=(byte)Integer.parseInt(modulus.substring(i,i+2),16);
                return toreturn;
            }
            public int getExponent() {
                return Integer.parseInt(exponent);
            }
        }
    public KeyData[] publickeysprod=
        {   new KeyData("MasterCard","03","00","A000000004","9E15214212F6308ACA78B80BD986AC287516846C8D548A9ED0A42E7D997C902C3E122D1B9DC30995F4E25C75DD7EE0A0CE293B8CC02B977278EF256D761194924764942FE714FA02E4D57F282BA3B2B62C9E38EF6517823F2CA831BDDF6D363D","768","8BB99ADDF7B560110955014505FB6B5F8308CE27","Live","31.12.2009"),
            new KeyData("MasterCard","03","01","A000000004","D2010716C9FB5264D8C91A14F4F32F8981EE954F20087ED77CDC5868431728D3637C632CCF2718A4F5D92EA8AB166AB992D2DE24E9FBDC7CAB9729401E91C502D72B39F6866F5C098B1243B132AFEE65F5036E168323116338F8040834B98725","768","EA950DD4234FEB7C900C0BE817F64DE66EEEF7C4","Live","31.12.2009"),
            new KeyData("MasterCard","03","02","A000000004","CF4264E1702D34CA897D1F9B66C5D63691EACC612C8F147116BB22D0C463495BD5BA70FB153848895220B8ADEEC3E7BAB31EA22C1DC9972FA027D54265BEBF0AE3A23A8A09187F21C856607B98BDA6FC908116816C502B3E58A145254EEFEE2A3335110224028B67809DCB8058E24895","896","AF1CC1FD1C1BC9BCA07E78DA6CBA2163F169CBB7","Live","31.12.2009"),
            new KeyData("MasterCard","03","03","A000000004","C2490747FE17EB0584C88D47B1602704150ADC88C5B998BD59CE043EDEBF0FFEE3093AC7956AD3B6AD4554C6DE19A178D6DA295BE15D5220645E3C8131666FA4BE5B84FE131EA44B039307638B9E74A8C42564F892A64DF1CB15712B736E3374F1BBB6819371602D8970E97B900793C7C2A89A4A1649A59BE680574DD0B60145","1024","5ADDF21D09278661141179CBEFF272EA384B13BB","Live","31.12.2009"),
            new KeyData("MasterCard","03","04","A000000004","A6DA428387A502D7DDFB7A74D3F412BE762627197B25435B7A81716A700157DDD06F7CC99D6CA28C2470527E2C03616B9C59217357C2674F583B3BA5C7DCF2838692D023E3562420B4615C439CA97C44DC9A249CFCE7B3BFB22F68228C3AF13329AA4A613CF8DD853502373D62E49AB256D2BC17120E54AEDCED6D96A4287ACC5C04677D4A5A320DB8BEE2F775E5FEC5","1152","381A035DA58B482EE2AF75F4C3F2CA469BA4AA6C","Live","31.12.2017"),
            new KeyData("MasterCard","03","05","A000000004","B8048ABC30C90D976336543E3FD7091C8FE4800DF820ED55E7E94813ED00555B573FECA3D84AF6131A651D66CFF4284FB13B635EDD0EE40176D8BF04B7FD1C7BACF9AC7327DFAA8AA72D10DB3B8E70B2DDD811CB4196525EA386ACC33C0D9D4575916469C4E4F53E8E1C912CC618CB22DDE7C3568E90022E6BBA770202E4522A2DD623D180E215BD1D1507FE3DC90CA310D27B3EFCCD8F83DE3052CAD1E48938C68D095AAC91B5F37E28BB49EC7ED597","1408","EBFA0D5D06D8CE702DA3EAE890701D45E274C845","Live","31.12.2024"),
            new KeyData("MasterCard","03","06","A000000004","CB26FC830B43785B2BCE37C81ED334622F9622F4C89AAE641046B2353433883F307FB7C974162DA72F7A4EC75D9D657336865B8D3023D3D645667625C9A07A6B7A137CF0C64198AE38FC238006FB2603F41F4F3BB9DA1347270F2F5D8C606E420958C5F7D50A71DE30142F70DE468889B5E3A08695B938A50FC980393A9CBCE44AD2D64F630BB33AD3F5F5FD495D31F37818C1D94071342E07F1BEC2194F6035BA5DED3936500EB82DFDA6E8AFB655B1EF3D0D7EBF86B66DD9F29F6B1D324FE8B26CE38AB2013DD13F611E7A594D675C4432350EA244CC34F3873CBA06592987A1D7E852ADC22EF5A2EE28132031E48F74037E3B34AB747F","1984","F910A1504D5FFB793D94F3B500765E1ABCAD72D9","Live","31.12.2028"),
            new KeyData("VISA","03","01","A000000003","C696034213D7D8546984579D1D0F0EA519CFF8DEFFC429354CF3A871A6F7183F1228DA5C7470C055387100CB935A712C4E2864DF5D64BA93FE7E63E71F25B1E5F5298575EBE1C63AA617706917911DC2A75AC28B251C7EF40F2365912490B939BCA2124A30A28F54402C34AECA331AB67E1E79B285DD5771B5D9FF79EA630B75","1024","D34A6A776011C7E7CE3AEC5F03AD2F8CFC5503CC","Live","31.12.2009"),
            new KeyData("VISA","03","07","A000000003","A89F25A56FA6DA258C8CA8B40427D927B4A1EB4D7EA326BBB12F97DED70AE5E4480FC9C5E8A972177110A1CC318D06D2F8F5C4844AC5FA79A4DC470BB11ED635699C17081B90F1B984F12E92C1C529276D8AF8EC7F28492097D8CD5BECEA16FE4088F6CFAB4A1B42328A1B996F9278B0B7E3311CA5EF856C2F888474B83612A82E4E00D0CD4069A6783140433D50725F","1152","B4BC56CC4E88324932CBC643D6898F6FE593B172","Live","31.12.2017"),
            new KeyData("VISA","03","08","A000000003","D9FD6ED75D51D0E30664BD157023EAA1FFA871E4DA65672B863D255E81E137A51DE4F72BCC9E44ACE12127F87E263D3AF9DD9CF35CA4A7B01E907000BA85D24954C2FCA3074825DDD4C0C8F186CB020F683E02F2DEAD3969133F06F7845166ACEB57CA0FC2603445469811D293BFEFBAFAB57631B3DD91E796BF850A25012F1AE38F05AA5C4D6D03B1DC2E568612785938BBC9B3CD3A910C1DA55A5A9218ACE0F7A21287752682F15832A678D6E1ED0B","1408","20D213126955DE205ADC2FD2822BD22DE21CF9A8","Live","31.12.2024"),
            new KeyData("VISA","03","09","A000000003","9D912248DE0A4E39C1A7DDE3F6D2588992C1A4095AFBD1824D1BA74847F2BC4926D2EFD904B4B54954CD189A54C5D1179654F8F9B0D2AB5F0357EB642FEDA95D3912C6576945FAB897E7062CAA44A4AA06B8FE6E3DBA18AF6AE3738E30429EE9BE03427C9D64F695FA8CAB4BFE376853EA34AD1D76BFCAD15908C077FFE6DC5521ECEF5D278A96E26F57359FFAEDA19434B937F1AD999DC5C41EB11935B44C18100E857F431A4A5A6BB65114F174C2D7B59FDF237D6BB1DD0916E644D709DED56481477C75D95CDD68254615F7740EC07F330AC5D67BCD75BF23D28A140826C026DBDE971A37CD3EF9B8DF644AC385010501EFC6509D7A41","1984","1FF80A40173F52D7D27E0F26A146A1C8CCB29046","Live","31.12.2028"),
        };

    KeyData[] publickeystest=
        {   new KeyData("MasterCard","03","00","A000000004","9C6BE5ADB10B4BE3DCE2099B4B210672B89656EBA091204F613ECC623BEDC9C6D77B660E8BAEEA7F7CE30F1B153879A4E36459343D1FE47ACDBD41FCD710030C2BA1D9461597982C6E1BDD08554B726F5EFF7913CE59E79E357295C321E26D0B8BE270A9442345C753E2AA2ACFC9D30850602FE6CAC00C6DDF6B8D9D9B4879B2826B042A07F0E5AE526A3D3C4D22C72B9EAA52EED8893866F866387AC05A1399","1280","EC0A59D35D19F031E9E8CBEC56DB80E22B1DE130","Test","N/A"),
            new KeyData("MasterCard","03","01","A000000004","C696034213D7D8546984579D1D0F0EA519CFF8DEFFC429354CF3A871A6F7183F1228DA5C7470C055387100CB935A712C4E2864DF5D64BA93FE7E63E71F25B1E5F5298575EBE1C63AA617706917911DC2A75AC28B251C7EF40F2365912490B939BCA2124A30A28F54402C34AECA331AB67E1E79B285DD5771B5D9FF79EA630B75","1024","8C05A64127485B923C94B63D264AF0BF85CB45D9","Test","N/A"),
            new KeyData("MasterCard","03","02","A000000004","A99A6D3E071889ED9E3A0C391C69B0B804FC160B2B4BDD570C92DD5A0F45F53E8621F7C96C40224266735E1EE1B3C06238AE35046320FD8E81F8CEB3F8B4C97B940930A3AC5E790086DAD41A6A4F5117BA1CE2438A51AC053EB002AED866D2C458FD73359021A12029A0C043045C11664FE0219EC63C10BF2155BB2784609A106421D45163799738C1C30909BB6C6FE52BBB76397B9740CE064A613FF8411185F08842A423EAD20EDFFBFF1CD6C3FE0C9821479199C26D8572CC8AFFF087A9C3","1536","33408B96C814742AD73536C72F0926E4471E8E47","Test","N/A"),
            new KeyData("MasterCard","03","05","A000000004","A1F5E1C9BD8650BD43AB6EE56B891EF7459C0A24FA84F9127D1A6C79D4930F6DB1852E2510F18B61CD354DB83A356BD190B88AB8DF04284D02A4204A7B6CB7C5551977A9B36379CA3DE1A08E69F301C95CC1C20506959275F41723DD5D2925290579E5A95B0DF6323FC8E9273D6F849198C4996209166D9BFC973C361CC826E1","1024","53D04903B496F59544A84309AF169251F2896874","Test","N/A"),
            new KeyData("MasterCard","03","EF","A000000004","A191CB87473F29349B5D60A88B3EAEE0973AA6F1A082F358D849FDDFF9C091F899EDA9792CAF09EF28F5D22404B88A2293EEBBC1949C43BEA4D60CFD879A1539544E09E0F09F60F065B2BF2A13ECC705F3D468B9D33AE77AD9D3F19CA40F23DCF5EB7C04DC8F69EBA565B1EBCB4686CD274785530FF6F6E9EE43AA43FDB02CE00DAEC15C7B8FD6A9B394BABA419D3F6DC85E16569BE8E76989688EFEA2DF22FF7D35C043338DEAA982A02B866DE5328519EBBCD6F03CDD686673847F84DB651AB86C28CF1462562C577B853564A290C8556D818531268D25CC98A4CC6A0BDFFFDA2DCCA3A94C998559E307FDDF915006D9A987B07DDAEB3B","1984","21766EBB0EE122AFB65D7845B73DB46BAB65427A","Test","N/A"),
            new KeyData("MasterCard","03","F1","A000000004","A0DCF4BDE19C3546B4B6F0414D174DDE294AABBB828C5A834D73AAE27C99B0B053A90278007239B6459FF0BBCD7B4B9C6C50AC02CE91368DA1BD21AAEADBC65347337D89B68F5C99A09D05BE02DD1F8C5BA20E2F13FB2A27C41D3F85CAD5CF6668E75851EC66EDBF98851FD4E42C44C1D59F5984703B27D5B9F21B8FA0D93279FBBF69E090642909C9EA27F898959541AA6757F5F624104F6E1D3A9532F2A6E51515AEAD1B43B3D7835088A2FAFA7BE7","1408","D8E68DA167AB5A85D8C3D55ECB9B0517A1A5B4BB","Test","N/A"),
            new KeyData("MasterCard","03","F3","A000000004","98F0C770F23864C2E766DF02D1E833DFF4FFE92D696E1642F0A88C5694C6479D16DB1537BFE29E4FDC6E6E8AFD1B0EB7EA0124723C333179BF19E93F10658B2F776E829E87DAEDA9C94A8B3382199A350C077977C97AFF08FD11310AC950A72C3CA5002EF513FCCC286E646E3C5387535D509514B3B326E1234F9CB48C36DDD44B416D23654034A66F403BA511C5EFA3","1152","A69AC7603DAF566E972DEDC2CB433E07E8B01A9A","Test","N/A"),
            new KeyData("MasterCard","010001","F5","A000000004","A6E6FB72179506F860CCCA8C27F99CECD94C7D4F3191D303BBEE37481C7AA15F233BA755E9E4376345A9A67E7994BDC1C680BB3522D8C93EB0CCC91AD31AD450DA30D337662D19AC03E2B4EF5F6EC18282D491E19767D7B24542DFDEFF6F62185503532069BBB369E3BB9FB19AC6F1C30B97D249EEE764E0BAC97F25C873D973953E5153A42064BBFABFD06A4BB486860BF6637406C9FC36813A4A75F75C31CCA9F69F8DE59ADECEF6BDE7E07800FCBE035D3176AF8473E23E9AA3DFEE221196D1148302677C720CFE2544A03DB553E7F1B8427BA1CC72B0F29B12DFEF4C081D076D353E71880AADFF386352AF0AB7B28ED49E1E672D11F9","1984","C2239804C8098170BE52D6D5D4159E81CE8466BF","Test","N/A"),
            new KeyData("MasterCard","03","F6","A000000004","A25A6BD783A5EF6B8FB6F83055C260F5F99EA16678F3B9053E0F6498E82C3F5D1E8C38F13588017E2B12B3D8FF6F50167F46442910729E9E4D1B3739E5067C0AC7A1F4487E35F675BC16E233315165CB142BFDB25E301A632A54A3371EBAB6572DEEBAF370F337F057EE73B4AE46D1A8BC4DA853EC3CC12C8CBC2DA18322D68530C70B22BDAC351DD36068AE321E11ABF264F4D3569BB71214545005558DE26083C735DB776368172FE8C2F5C85E8B5B890CC682911D2DE71FA626B8817FCCC08922B703869F3BAEAC1459D77CD85376BC36182F4238314D6C4212FBDD7F23D3","1792","502909ED545E3C8DBD00EA582D0617FEE9F6F684","Test","N/A"),
            new KeyData("MasterCard","010001","F7","A000000004","94EA62F6D58320E354C022ADDCF0559D8CF206CD92E869564905CE21D720F971B7AEA374830EBE1757115A85E088D41C6B77CF5EC821F30B1D890417BF2FA31E5908DED5FA677F8C7B184AD09028FDDE96B6A6109850AA800175EABCDBBB684A96C2EB6379DFEA08D32FE2331FE103233AD58DCDB1E6E077CB9F24EAEC5C25AF","1024","EEB0DD9B2477BEE3209A914CDBA94C1C4A9BDED9","Test","N/A"),
            new KeyData("MasterCard","03","F8","A000000004","A1F5E1C9BD8650BD43AB6EE56B891EF7459C0A24FA84F9127D1A6C79D4930F6DB1852E2510F18B61CD354DB83A356BD190B88AB8DF04284D02A4204A7B6CB7C5551977A9B36379CA3DE1A08E69F301C95CC1C20506959275F41723DD5D2925290579E5A95B0DF6323FC8E9273D6F849198C4996209166D9BFC973C361CC826E1","1024","F06ECC6D2AAEBF259B7E755A38D9A9B24E2FF3DD","Test","N/A"),
            new KeyData("MasterCard","03","F9","A000000004","A99A6D3E071889ED9E3A0C391C69B0B804FC160B2B4BDD570C92DD5A0F45F53E8621F7C96C40224266735E1EE1B3C06238AE35046320FD8E81F8CEB3F8B4C97B940930A3AC5E790086DAD41A6A4F5117BA1CE2438A51AC053EB002AED866D2C458FD73359021A12029A0C043045C11664FE0219EC63C10BF2155BB2784609A106421D45163799738C1C30909BB6C6FE52BBB76397B9740CE064A613FF8411185F08842A423EAD20EDFFBFF1CD6C3FE0C9821479199C26D8572CC8AFFF087A9C3","1536","336712DCC28554809C6AA9B02358DE6F755164DB","Test","N/A"),
            new KeyData("MasterCard","03","FA","A000000004","A90FCD55AA2D5D9963E35ED0F440177699832F49C6BAB15CDAE5794BE93F934D4462D5D12762E48C38BA83D8445DEAA74195A301A102B2F114EADA0D180EE5E7A5C73E0C4E11F67A43DDAB5D55683B1474CC0627F44B8D3088A492FFAADAD4F42422D0E7013536C3C49AD3D0FAE96459B0F6B1B6056538A3D6D44640F94467B108867DEC40FAAECD740C00E2B7A8852D","1152","5BED4068D96EA16D2D77E03D6036FC7A160EA99C","Test","N/A"),
            new KeyData("MasterCard","03","FE","A000000004","A653EAC1C0F786C8724F737F172997D63D1C3251C44402049B865BAE877D0F398CBFBE8A6035E24AFA086BEFDE9351E54B95708EE672F0968BCD50DCE40F783322B2ABA04EF137EF18ABF03C7DBC5813AEAEF3AA7797BA15DF7D5BA1CBAF7FD520B5A482D8D3FEE105077871113E23A49AF3926554A70FE10ED728CF793B62A1","1024","9A295B05FB390EF7923F57618A9FDA2941FC34E0","Test","N/A"),
            new KeyData("MasterCard","03","00","B012345678","9C6BE5ADB10B4BE3DCE2099B4B210672B89656EBA091204F613ECC623BEDC9C6D77B660E8BAEEA7F7CE30F1B153879A4E36459343D1FE47ACDBD41FCD710030C2BA1D9461597982C6E1BDD08554B726F5EFF7913CE59E79E357295C321E26D0B8BE270A9442345C753E2AA2ACFC9D30850602FE6CAC00C6DDF6B8D9D9B4879B2826B042A07F0E5AE526A3D3C4D22C72B9EAA52EED8893866F866387AC05A1399","1280","5D2970E64675727E60460765A8DB75342AE14783","Test","N/A"),
            new KeyData("MasterCard","03","02","B012345678","A99A6D3E071889ED9E3A0C391C69B0B804FC160B2B4BDD570C92DD5A0F45F53E8621F7C96C40224266735E1EE1B3C06238AE35046320FD8E81F8CEB3F8B4C97B940930A3AC5E790086DAD41A6A4F5117BA1CE2438A51AC053EB002AED866D2C458FD73359021A12029A0C043045C11664FE0219EC63C10BF2155BB2784609A106421D45163799738C1C30909BB6C6FE52BBB76397B9740CE064A613FF8411185F08842A423EAD20EDFFBFF1CD6C3FE0C9821479199C26D8572CC8AFFF087A9C3","1536","294BE20239AB15245A63BEA46CC6C175A25562D1","Test","N/A"),
            new KeyData("MasterCard","03","05","B012345678","A1F5E1C9BD8650BD43AB6EE56B891EF7459C0A24FA84F9127D1A6C79D4930F6DB1852E2510F18B61CD354DB83A356BD190B88AB8DF04284D02A4204A7B6CB7C5551977A9B36379CA3DE1A08E69F301C95CC1C20506959275F41723DD5D2925290579E5A95B0DF6323FC8E9273D6F849198C4996209166D9BFC973C361CC826E1","1024","B9A1D65CAFE06B054EDD7EA82597AB85F130E663","Test","N/A"),
            new KeyData("MasterCard","03","F3","B012345678","98F0C770F23864C2E766DF02D1E833DFF4FFE92D696E1642F0A88C5694C6479D16DB1537BFE29E4FDC6E6E8AFD1B0EB7EA0124723C333179BF19E93F10658B2F776E829E87DAEDA9C94A8B3382199A350C077977C97AFF08FD11310AC950A72C3CA5002EF513FCCC286E646E3C5387535D509514B3B326E1234F9CB48C36DDD44B416D23654034A66F403BA511C5EFA3","1152","13AAE850317805D1EA97453FE36057C57DD6528B","Test","N/A"),
            new KeyData("MasterCard","010001","F3","B012345678","94EA62F6D58320E354C022ADDCF0559D8CF206CD92E869564905CE21D720F971B7AEA374830EBE1757115A85E088D41C6B77CF5EC821F30B1D890417BF2FA31E5908DED5FA677F8C7B184AD09028FDDE96B6A6109850AA800175EABCDBBB684A96C2EB6379DFEA08D32FE2331FE103233AD58DCDB1E6E077CB9F24EAEC5C25AF","1024","5694B0D278481814A05E12B558CEC1234865AA5D","Test","N/A"),
            new KeyData("MasterCard","03","F5","B012345678","A25A6BD783A5EF6B8FB6F83055C260F5F99EA16678F3B9053E0F6498E82C3F5D1E8C38F13588017E2B12B3D8FF6F50167F46442910729E9E4D1B3739E5067C0AC7A1F4487E35F675BC16E233315165CB142BFDB25E301A632A54A3371EBAB6572DEEBAF370F337F057EE73B4AE46D1A8BC4DA853EC3CC12C8CBC2DA18322D68530C70B22BDAC351DD36068AE321E11ABF264F4D3569BB71214545005558DE26083C735DB776368172FE8C2F5C85E8B5B890CC682911D2DE71FA626B8817FCCC08922B703869F3BAEAC1459D77CD85376BC36182F4238314D6C4212FBDD7F23D3","1792","F75E8802855C9B14027E517345717E5C3635B91B","Test","N/A"),
            new KeyData("MasterCard","03","F6","B012345678","A1F5E1C9BD8650BD43AB6EE56B891EF7459C0A24FA84F9127D1A6C79D4930F6DB1852E2510F18B61CD354DB83A356BD190B88AB8DF04284D02A4204A7B6CB7C5551977A9B36379CA3DE1A08E69F301C95CC1C20506959275F41723DD5D2925290579E5A95B0DF6323FC8E9273D6F849198C4996209166D9BFC973C361CC826E1","1024","E9406B6510C143AB1E9B9D79A3C1DFF8909A347C","Test","N/A"),
            new KeyData("MasterCard","03","F7","B012345678","98F0C770F23864C2E766DF02D1E833DFF4FFE92D696E1642F0A88C5694C6479D16DB1537BFE29E4FDC6E6E8AFD1B0EB7EA0124723C333179BF19E93F10658B2F776E829E87DAEDA9C94A8B3382199A350C077977C97AFF08FD11310AC950A72C3CA5002EF513FCCC286E646E3C5387535D509514B3B326E1234F9CB48C36DDD44B416D23654034A66F403BA511C5EFA3","1152","F78113E860F030A872923FCE93E3381C77A42A30","Test","N/A"),
            new KeyData("MasterCard","03","F8","B012345678","A99A6D3E071889ED9E3A0C391C69B0B804FC160B2B4BDD570C92DD5A0F45F53E8621F7C96C40224266735E1EE1B3C06238AE35046320FD8E81F8CEB3F8B4C97B940930A3AC5E790086DAD41A6A4F5117BA1CE2438A51AC053EB002AED866D2C458FD73359021A12029A0C043045C11664FE0219EC63C10BF2155BB2784609A106421D45163799738C1C30909BB6C6FE52BBB76397B9740CE064A613FF8411185F08842A423EAD20EDFFBFF1CD6C3FE0C9821479199C26D8572CC8AFFF087A9C3","1536","66469C88E7DC111529C7D379D7938C8DF3E4C25E","Test","N/A"),
            new KeyData("MasterCard","010001","F9","B012345678","A6E6FB72179506F860CCCA8C27F99CECD94C7D4F3191D303BBEE37481C7AA15F233BA755E9E4376345A9A67E7994BDC1C680BB3522D8C93EB0CCC91AD31AD450DA30D337662D19AC03E2B4EF5F6EC18282D491E19767D7B24542DFDEFF6F62185503532069BBB369E3BB9FB19AC6F1C30B97D249EEE764E0BAC97F25C873D973953E5153A42064BBFABFD06A4BB486860BF6637406C9FC36813A4A75F75C31CCA9F69F8DE59ADECEF6BDE7E07800FCBE035D3176AF8473E23E9AA3DFEE221196D1148302677C720CFE2544A03DB553E7F1B8427BA1CC72B0F29B12DFEF4C081D076D353E71880AADFF386352AF0AB7B28ED49E1E672D11F9","1984","AEACA45480C8834CB0BEBDCC570B7B2B74BB4B79","Test","N/A"),
            new KeyData("VISA","03","92","A000000003","996AF56F569187D09293C14810450ED8EE3357397B18A2458EFAA92DA3B6DF6514EC060195318FD43BE9B8F0CC669E3F844057CBDDF8BDA191BB64473BC8DC9A730DB8F6B4EDE3924186FFD9B8C7735789C23A36BA0B8AF65372EB57EA5D89E7D14E9C7B6B557460F10885DA16AC923F15AF3758F0F03EBD3C5C2C949CBA306DB44E6A2C076C5F67E281D7EF56785DC4D75945E491F01918800A9E2DC66F60080566CE0DAF8D17EAD46AD8E30A247C9F","1408","429C954A3859CEF91295F663C963E582ED6EB253","Test","N/A"),
            new KeyData("VISA","03","94","A000000003","ACD2B12302EE644F3F835ABD1FC7A6F62CCE48FFEC622AA8EF062BEF6FB8BA8BC68BBF6AB5870EED579BC3973E121303D34841A796D6DCBC41DBF9E52C4609795C0CCF7EE86FA1D5CB041071ED2C51D2202F63F1156C58A92D38BC60BDF424E1776E2BC9648078A03B36FB554375FC53D57C73F5160EA59F3AFC5398EC7B67758D65C9BFF7828B6B82D4BE124A416AB7301914311EA462C19F771F31B3B57336000DFF732D3B83DE07052D730354D297BEC72871DCCF0E193F171ABA27EE464C6A97690943D59BDABB2A27EB71CEEBDAFA1176046478FD62FEC452D5CA393296530AA3F41927ADFE434A2DF2AE3054F8840657A26E0FC617","1984","C4A3C43CCF87327D136B804160E47D43B60E6E0F","Test","N/A"),
            new KeyData("VISA","03","95","A000000003","BE9E1FA5E9A803852999C4AB432DB28600DCD9DAB76DFAAA47355A0FE37B1508AC6BF38860D3C6C2E5B12A3CAAF2A7005A7241EBAA7771112C74CF9A0634652FBCA0E5980C54A64761EA101A114E0F0B5572ADD57D010B7C9C887E104CA4EE1272DA66D997B9A90B5A6D624AB6C57E73C8F919000EB5F684898EF8C3DBEFB330C62660BED88EA78E909AFF05F6DA627B","1152","EE1511CEC71020A9B90443B37B1D5F6E703030F6","Test","N/A"),
            new KeyData("VISA","03","99","A000000003","AB79FCC9520896967E776E64444E5DCDD6E13611874F3985722520425295EEA4BD0C2781DE7F31CD3D041F565F747306EED62954B17EDABA3A6C5B85A1DE1BEB9A34141AF38FCF8279C9DEA0D5A6710D08DB4124F041945587E20359BAB47B7575AD94262D4B25F264AF33DEDCF28E09615E937DE32EDC03C54445FE7E382777","1024","4ABFFD6B1C51212D05552E431C5B17007D2F5E6D","Test","N/A"),
        };
    KeyData[] publickeysunknown=
        {   new KeyData("MasterCard","03","06","A000000004","D24C24D2D7FB5509D5B26EBD4077CE74516A2B89E4062D83DC1F7E27D5E5AA6657F376DABDDB6B4251F323426E621F5DFC1DFA07C06035908B7EDF674CBEB598F59F9CCB5C55410521C1595E7BD86AD71C42C328FCD9D82C9DD68DF1E6D3F189C32F578B7E3487E84D642ED2DA3F689AA188C2A1F37E1395732E1872954FFEB19D5C404515E7C3F637E4B9E0F889887C0C43194942B3A92D43B0AB091C5510FB3C24A1264764CBEEBAFEC0AACCA6F948FC973C8950DF934140B7DF87E77193B954193EB3B75E60BBB817C4FEEAA542CE388782885B8460C4C9442937ECFDB808FD8B8979E5368EB859C9068D3D0EA91678D63BC02C87B89DB3EBE6CF1D8F6BE6","2048","7AB3722ACE9C19ACD59EDC8BF2A4847BB24A6582","",""),
            new KeyData("MasterCard","03","09","A000000004","967B6264436C96AA9305776A5919C70DA796340F9997A6C6EF7BEF1D4DBF9CB4289FB7990ABFF1F3AE692F12844B2452A50AE075FB327976A40E8028F279B1E3CCB623957D696FC1225CA2EC950E2D415E9AA931FF18B13168D661FBD06F0ABB","768","1D90595C2EF9FC6E71B0C721118333DF8A71FE21","",""),
            new KeyData("MasterCard","03","22","A000000004","BBE43877CC28C0CE1E14BC14E8477317E218364531D155BB8AC5B63C0D6E284DD24259193899F9C04C30BAF167D57929451F67AEBD3BBD0D41444501847D8F02F2C2A2D14817D97AE2625DC163BF8B484C40FFB51749CEDDE9434FB2A0A41099","768","008C39B1D119498268B07843349427AC6E98F807","",""),
            new KeyData("MasterCard","010001","52","A000000004","B831414E0B4613922BD35B4B36802BC1E1E81C95A27C958F5382003DF646154CA92FC1CE02C3BE047A45E9B02A9089B4B90278237C965192A0FCC86BB49BC82AE6FDC2DE709006B86C7676EFDF597626FAD633A4F7DC48C445D37EB55FCB3B1ABB95BAAA826D5390E15FD14ED403FA2D0CB841C650609524EC555E3BC56CA957","1024","DEB81EDB2626A4BB6AE23B77D19A77539D0E6716","",""),
            new KeyData("MasterCard","03","F0","A000000004","7563C51B5276AA6370AB8405522414645832B6BEF2A989C771475B2E8DC654DC8A5BFF9E28E31FF1A370A40DC3FFEB06BC85487D5F1CB61C2441FD71CBCD05D883F8DE413B243AFC9DCA768B061E35B884B5D21B6B016AA36BA12DABCFE49F8E528C893C34C7D4793977E4CC99AB09640D9C7AAB7EC5FF3F40E3D4D18DF7E3A7","1024","AE667445F8DE6F82C38800E5EBABA322F03F58F2","",""),
            new KeyData("MasterCard","03","F4","A000000004","9E2F74BF4AB521019735BFC7E4CBC56B6F64AFF1ED7B79998EE5B3DFFE23DFC8E2DD0025575AF94DE814264528AF6F8005A538B3D6AE881B350F89595588E51F7423E711109DEC169FDD560602D80EF46E582C8C546C8930394BD534412A88CC9FF4DFC08AE716A595EF1AF7C32EDFCF996433EB3C36BCE093E44E0BDE228E0299A0E358BF28308DB4739815DD09F1E89654CC7CC193E2AC17C4DA335D904B8EC06ACFBDE083F76933C969672E9AFEA3","1408","","",""),
            new KeyData("MasterCard","03","FA","A000000004","9C6BE5ADB10B4BE3DCE2099B4B210672B89656EBA091204F613ECC623BEDC9C6D77B660E8BAEEA7F7CE30F1B153879A4E36459343D1FE47ACDBD41FCD710030C2BA1D9461597982C6E1BDD08554B726F5EFF7913CE59E79E357295C321E26D0B8BE270A9442345C753E2AA2ACFC9D30850602FE6CAC00C6DDF6B8D9D9B4879B2826B042A07F0E5AE526A3D3C4D22C72B9EAA52EED8893866F866387AC05A1399","1280","0ABCADAD2C7558CA9C7081AE55DDDC714F8D45F8","",""),
            new KeyData("MasterCard","02","FB","A000000004","A9548DFB398B48123FAF41E6CFA4AE1E2352B518AB4BCEFECDB0B3EDEC090287D88B12259F361C1CC088E5F066494417E8EE8BBF8991E2B32FF16F994697842B3D6CB37A2BB5742A440B6356C62AA33DB3C455E59EDDF7864701D03A5B83EE9E9BD83AB93302AC2DFE63E66120B051CF081F56326A71303D952BB336FF12610D","1024","6C7289632919ABEE6E1163D7E6BF693FD88EBD35","",""),
            new KeyData("MasterCard","02","FC","A000000004","B37BFD2A9674AD6221C1A001081C62653DC280B0A9BD052C677C913CE7A0D902E77B12F4D4D79037B1E9B923A8BB3FAC3C612045BB3914F8DF41E9A1B61BFA5B41705A691D09CE6F530FE48B30240D98F4E692FFD6AADB87243BA8597AB237586ECF258F4148751BE5DA5A3BE6CC34BD","896","7FB377EEBBCF7E3A6D04015D10E1BDCB15E21B80","",""),
            new KeyData("MasterCard","02","FD","A000000004","B3572BA49AE4C7B7A0019E5189E142CFCDED9498DDB5F0470567AB0BA713B8DA226424622955B54B937ABFEFAAD97919E377621E22196ABC1419D5ADC123484209EA7CB7029E66A0D54C5B45C8AD615AEDB6AE9E0A2F75310EA8961287241245","768","23CF0D702E0AEFE518E4FA6B836D3CD45B8AAA71","",""),
            new KeyData("MasterCard","03","FF","A000000004","B855CC64313AF99C453D181642EE7DD21A67D0FF50C61FE213BCDC18AFBCD07722EFDD2594EFDC227DA3DA23ADCC90E3FA907453ACC954C47323BEDCF8D4862C457D25F47B16D7C3502BE081913E5B0482D838484065DA5F6659E00A9E5D570ADA1EC6AF8C57960075119581FC81468D","896","B4E769CECF7AAC4783F305E0B110602A07A6355B","",""),
            new KeyData("VISA","03","03","A000000003","B3E5E667506C47CAAFB12A2633819350846697DD65A796E5CE77C57C626A66F70BB630911612AD2832909B8062291BECA46CD33B66A6F9C9D48CED8B4FC8561C8A1D8FB15862C9EB60178DEA2BE1F82236FFCFF4F3843C272179DCDD384D541053DA6A6A0D3CE48FDC2DC4E3E0EEE15F","896","FE70AB3B4D5A1B9924228ADF8027C758483A8B7E","",""),
            new KeyData("VISA","03","05","A000000003","D0135CE8A4436C7F9D5CC66547E30EA402F98105B71722E24BC08DCC80AB7E71EC23B8CE6A1DC6AC2A8CF55543D74A8AE7B388F9B174B7F0D756C22CBB5974F9016A56B601CCA64C71F04B78E86C501B193A5556D5389ECE4DEA258AB97F52A3","768","86DF041E7995023552A79E2623E49180C0CD957A","",""),
            new KeyData("VISA","03","06","A000000003","F934FC032BE59B609A9A649E04446F1B365D1D23A1E6574E490170527EDF32F398326159B39B63D07E95E6276D7FCBB786925182BC0667FBD8F6566B361CA41A38DDF227091B87FA4F47BAC780AC47E15A6A0FB65393EB3473E8D193A07EB579","768","A0DF5DAA385AE3E0E21BFD34D9D8A30506B19B12","",""),
            new KeyData("VISA","03","10","A000000003","9F2701C0909CCBD8C3ED3E071C69F776160022FF3299807ED7A035ED5752770E232D56CC3BE159BD8F0CA8B59435688922F406F55C75639457BBABEFE9A86B2269EF223E34B91AA6DF2CCAD03B4AD4B443D61575CA960845E6C69040101E231D9EF811AD99B0715065A0E661449C41B4B023B7716D1E4AFF1C90704E55AE1225","1024","833B1947778036B6D759FCE3F618DDEB2749372C","",""),
            new KeyData("VISA","03","20","A000000003","998D2AD946A60FC597D93807DB54B2B0A550871E43F1779F073AF08D9B04ABD17C8A7DAA3E66EE443F30F92648FC53DA57A78364B062FEDB50F7235B937E16E5F6D9E6BA8F106FB325ECA25125111CE04B43098CDEA8A41426FC6D94F8A47619EDB12789581808692CFBA1F38E8008CC5E02066A1889D52F77B9A121E6597F39","1024","7AC3D80EF01E9A998F0A77181E64B36747DC51EB","",""),
            new KeyData("VISA","010001","50","A000000003","D11197590057B84196C2F4D11A8F3C05408F422A35D702F90106EA5B019BB28AE607AA9CDEBCD0D81A38D48C7EBB0062D287369EC0C42124246AC30D80CD602AB7238D51084DED4698162C59D25EAC1E66255B4DB2352526EF0982C3B8AD3D1CCE85B01DB5788E75E09F44BE7361366DEF9D1E1317B05E5D0FF5290F88A0DB47","1024","B769775668CACB5D22A647D1D993141EDAB7237B","",""),
            new KeyData("VISA","03","51","A000000003","BBE43877CC28C0CE1E14BC14E8477317E218364531D155BB8AC5B63C0D6E284DD24259193899F9C04C30BAF167D57929451F67AEBD3BBD0D41444501847D8F02F2C2A2D14817D97AE2625DC163BF8B484C40FFB51749CEDDE9434FB2A0A41099","768","D3D90B35BA8C48731171EAC407D89005ACF6F9DA","",""),
            new KeyData("VISA","03","51","A000000003","DB5FA29D1FDA8C1634B04DCCFF148ABEE63C772035C79851D3512107586E02A917F7C7E885E7C4A7D529710A145334CE67DC412CB1597B77AA2543B98D19CF2CB80C522BDBEA0F1B113FA2C86216C8C610A2D58F29CF3355CEB1BD3EF410D1EDD1F7AE0F16897979DE28C6EF293E0A19282BD1D793F1331523FC71A228800468C01A3653D14C6B4851A5C029478E757F","1152","969299D792D3CC08AD28F2D544CEE3309DADF1B9","",""),
            new KeyData("VISA","03","52","A000000003","AFF740F8DBE763F333A1013A43722055C8E22F41779E219B0E1C409D60AFD45C8789C57EECD71EA4A269A675916CC1C5E1A05A35BD745A79F94555CE29612AC9338769665B87C3CA8E1AC4957F9F61FA7BFFE4E17631E937837CABF43DD6183D6360A228A3EBC73A1D1CDC72BF09953C81203AB7E492148E4CB774CDDFAAC3544D0DD4F8C8A0E9C70B877EA79F2C22E4CE52C69F3EF376F61B0F43A540FE96C63F586310C3B6E39C78C4D647CADB5933","1408","D6F78FB14CB58B0E0B67BFA7870FB8DFBEE2AD01","",""),
            new KeyData("VISA","010001","52","A000000003","B831414E0B4613922BD35B4B36802BC1E1E81C95A27C958F5382003DF646154CA92FC1CE02C3BE047A45E9B02A9089B4B90278237C965192A0FCC86BB49BC82AE6FDC2DE709006B86C7676EFDF597626FAD633A4F7DC48C445D37EB55FCB3B1ABB95BAAA826D5390E15FD14ED403FA2D0CB841C650609524EC555E3BC56CA957","1024","73A7CA6BA7DB3C37B78E86952BC4EC7754925D54","",""),
            new KeyData("VISA","03","53","A000000003","BCD83721BE52CCCC4B6457321F22A7DC769F54EB8025913BE804D9EABBFA19B3D7C5D3CA658D768CAF57067EEC83C7E6E9F81D0586703ED9DDDADD20675D63424980B10EB364E81EB37DB40ED100344C928886FF4CCC37203EE6106D5B59D1AC102E2CD2D7AC17F4D96C398E5FD993ECB4FFDF79B17547FF9FA2AA8EEFD6CBDA124CBB17A0F8528146387135E226B005A474B9062FF264D2FF8EFA36814AA2950065B1B04C0A1AE9B2F69D4A4AA979D6CE95FEE9485ED0A03AEE9BD953E81CFD1EF6E814DFD3C2CE37AEFA38C1F9877371E91D6A5EB59FDEDF75D3325FA3CA66CDFBA0E57146CC789818FF06BE5FCC50ABD362AE4B80996D","1984","A84A53964513A5D9363B4BA13AF5D43B83A83CE7","",""),
            new KeyData("VISA","010001","58","A000000003","99552C4A1ECD68A0260157FC4151B5992837445D3FC57365CA5692C87BE358CDCDF2C92FB6837522842A48EB11CDFFE2FD91770C7221E4AF6207C2DE4004C7DEE1B6276DC62D52A87D2CD01FBF2DC4065DB52824D2A2167A06D19E6A0F781071CDB2DD314CB94441D8DC0E936317B77BF06F5177F6C5ABA3A3BC6AA30209C97260B7A1AD3A192C9B8CD1D153570AFCC87C3CD681D13E997FE33B3963A0A1C79772ACF991033E1B8397AD0341500E48A24770BC4CBE19D2CCF419504FDBF0389BC2F2FDCD4D44E61F","1600","E6D302EBE7DC6F267E4D00F7D488F0AB6235F105","",""),
            new KeyData("VISA","03","89","A000000003","E5E195705CE61A0672B8367E7A51713927A04289EA308328FAD28071ECEAE889B3C4F29AC3BDE46772B00D42FD05F27228820F2693990F81B0F6928E240D957EC4484354CD5E5CA9092B444741A0394D3476651232474A9B87A961DA8DD96D90F036E9B3C52FB09766BDA4D6BC3BDADBC89122B74068F8FA04026C5FA8EF398BC3AB3992A87F6A785CC779BA99F170956623D67A18EB8324263D626BE85BFF77B8B981C0A3F7849C4F3D8E20542955D19128198547B47AE34DF67F28BE433F33","1536","7170850B97F83952045CF9CA8B7612DFEB69E9EF","",""),
            new KeyData("VISA","03","90","A000000003","C26B3CB3833E42D8270DC10C8999B2DA18106838650DA0DBF154EFD51100AD144741B2A87D6881F8630E3348DEA3F78038E9B21A697EB2A6716D32CBF26086F1","512","B3AE2BC3CAFC05EEEFAA46A2A47ED51DE679F823","",""),
            new KeyData("VISA","03","96","A000000003","B74586D19A207BE6627C5B0AAFBC44A2ECF5A2942D3A26CE19C4FFAEEE920521868922E893E7838225A3947A2614796FB2C0628CE8C11E3825A56D3B1BBAEF783A5C6A81F36F8625395126FA983C5216D3166D48ACDE8A431212FF763A7F79D9EDB7FED76B485DE45BEB829A3D4730848A366D3324C3027032FF8D16A1E44D8D","1024","7616E9AC8BE014AF88CA11A8FB17967B7394030E","",""),
            new KeyData("VISA","03","97","A000000003","AF0754EAED977043AB6F41D6312AB1E22A6809175BEB28E70D5F99B2DF18CAE73519341BBBD327D0B8BE9D4D0E15F07D36EA3E3A05C892F5B19A3E9D3413B0D97E7AD10A5F5DE8E38860C0AD004B1E06F4040C295ACB457A788551B6127C0B29","768","8001CA76C1203955E2C62841CD6F201087E564BF","",""),
            new KeyData("VISA","03","98","A000000003","CA026E52A695E72BD30AF928196EEDC9FAF4A619F2492E3FB31169789C276FFBB7D43116647BA9E0D106A3542E3965292CF77823DD34CA8EEC7DE367E08070895077C7EFAD939924CB187067DBF92CB1E785917BD38BACE0C194CA12DF0CE5B7A50275AC61BE7C3B436887CA98C9FD39","896","E7AC9AA8EED1B5FF1BD532CF1489A3E5557572C1","",""),
            new KeyData("VISA","03","F3","A000000003","98F0C770F23864C2E766DF02D1E833DFF4FFE92D696E1642F0A88C5694C6479D16DB1537BFE29E4FDC6E6E8AFD1B0EB7EA0124723C333179BF19E93F10658B2F776E829E87DAEDA9C94A8B3382199A350C077977C97AFF08FD11310AC950A72C3CA5002EF513FCCC286E646E3C5387535D509514B3B326E1234F9CB48C36DDD44B416D23654034A66F403BA511C5EFA3","1152","128EB33128E63E38C9A83A2B1A9349E178F82196","",""),
        };





    //=================================================================================== EMV KEYS






}

