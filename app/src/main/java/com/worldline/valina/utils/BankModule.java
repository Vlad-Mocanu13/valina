package com.worldline.valina.utils;


//import android.device.SEManager;
import android.os.Environment;
        import android.util.Base64;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
        import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
        import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class BankModule {


    //int g_amount=0;
    String g_tid="",g_mid="",g_currencycode="";//"TEST0001,TESTMERC,0946
    int g_invoice=0,g_settlement_batch=0,g_settlement_sum=0,g_settlement_count=0,g_stan=0;
    int g_timeout=1,g_port=1;//read from config  5000   5080
    String g_serverip="";//pos-api.symphopay.com";//""192.168.222.78";//read from config
    Boolean icccardselected=true;
    String g_pinblock="";
    int g_offlinecount=0;
    Boolean isonline=true;
    Boolean g_issendingofflinebatch=false;
    Hashtable<Integer,String> errorvaluies=new Hashtable<Integer,String>();
    //FOR SSL
    SSLContext g_sslcontext;



    public enum SDKEvent
    {   PRINT,
        TOAST,
        TRANSACTION_SUCCESS,
        TRANSACTION_FAILED,
        CARD_INSERTED,
        CANCELED,
        QUESTIONNAIRE,
        POPUP_QR,
        POPUP
    }
    public class Questionnaire
    {   public String id;
        public Question[] questions;
        public String amount="";
    }
    public class Question
    {   public String id,type,question,response="";
        public String[] options,optioncodes;
        public Boolean ready=false;
    }



    //================================================API for OVERWRITING
    public BankModule(SSLContext _sslcontext)
    {   //emvlib=_emvlib;
        g_sslcontext=_sslcontext;
        readconfigandvars();
        g_invoice++;
        //g_stan++;

        errorvaluies.put(98,"REINCERCATI. SETTLEMENT IN CURS");
        errorvaluies.put(95,"TRIMITERE BATCH NECESARA");
        errorvaluies.put(96,"Internal error!");
        errorvaluies.put(0,"SUCCESS");

        updateconfig("stan");
        updateconfig("invoice");
        //updateinvoice();
        initvalidtags();


    }
    public void OnOnlineProcessResult(boolean status,String responseData)
    {
        Log.d("Trans Result",status?"SUCCESSFULL TRANSACTION":"failed transaction");
    }
    public void OnPrint(String message)
    {   Log.d("To Print",message);
    }
    public void OnSettlementEvent(boolean isresult,String message)
    {   Log.d("Settlement event",message);
    }

    public void OnQuestionnaire(BankModule.Questionnaire q)
    {
        Log.d("New OnQuestionnaire","");
    }
    public void OnPopup(String text)
    {
        Log.d("New Poppup",text);
    }
    public void OnQR(String[] qrlines)
    {
        Log.d("New QR",qrlines[0]);
    }




//================================================INIT + TRANSACTION DATA STORING    STAN/INVOICE/TRANSACTIONS/SETTLEMENT   IO
    Hashtable<String, Integer> validtags2=new Hashtable<String, Integer>();
    Hashtable<String, Integer> validtags4=new Hashtable<String, Integer>();
    int[] taglength,tagprefixlength,tagtype;
    int[] msg200presenttags,msg220presenttags,msg320presenttags,msg400presenttags,
            msg500initpresenttags,msg500endpresenttags,msg800contextpresenttags,msg800questpresenttags;
    void initvalidtags()
    {   String[] values2={"06","41","42","43","44","45","46","47","48","4D","4F","50","51","52",
            "53","56","57","58","59","5A","5B","5C","5D","5E","60","61","62","63",
            "64","65","66","67","68","6A","6B","6C","6D","6E","6F","70","71","72",
            "73","77","78","79","7A","7B","7D","7E","80","81","82","83","84","86",
            "87","88","89","8A","8C","8D","8E","8F","90","91","92","93","94","95",
            "97","98","99","9A","9B","9C","9D","A5","C3","C4","C5","C6","C7","C8",
            "C9","CA","CB","CD","CE","CF","D1","D2","D3","D5","D6","D7","D8","D9",
            "DA","DB","DC","DD"};
        String[] values4={"5F20","5F21","5F22","5F23","5F24","5F25","5F26","5F27","5F28","5F29",
                "5F2A","5F2B","5F2C","5F2D","5F2E","5F2F","5F30","5F32","5F33","5F34",
                "5F35","5F36","5F37","5F38","5F39","5F3A","5F3B","5F3C","5F3D","5F40",
                "5F41","5F42","5F43","5F44","5F45","5F46","5F47","5F48","5F49","5F4A",
                "5F4B","5F4C","5F4D","5F4E","5F50","5F53","5F54","5F55","5F56","5F57",
                "6080","6081","6082","6083","6084","6085","60A0","6280","6281","6282",
                "6283","6284","6285","6286","6287","6288","628A","628B","628C","628D",
                "62A0","62A1","62A2","62A5","62AB","62AC","6A80","6A81","6A82","6A83",
                "6A84","6B06","6B80","6BA0","6FA5","7186","7A80","7A81","7A82","7A83",
                "7A84","7A85","7A86","7A87","7A88","7A89","7A8A","7A8B","7A8C","7A8D",
                "7A8E","7A93","7B80","7B8A","7BAC","7BA4","7BAA","7BB4","7BB6","7BB8",
                "7D80","7D81","7D82","7D83","7D84","7D85","7D86","7D87","7D8E","7D90",
                "7D91","7D92","7D93","7D94","7D95","7D96","7D97","7D99","7D9A","7D9B",
                "7D9C","7D9D","7D9E","7DA0","7DA1","7DA2","7DA4","7DA5","7DA8","7DAA",
                "7DAB","7DAC","7DAD","7DAE","7DAF","7DB0","7DB1","7DB2","7DB3","7DB4",
                "7DB5","7DB6","7DB7","7DB8","7DB9","7DBA","7DBB","7DBC","7DBD","7DBE",
                "7F20","7F21","7F2E","7F49","7F4C","7F4E","7F60","9F01","9F02","9F03",
                "9F04","9F05","9F06","9F07","9F08","9F09","9F0B","9F0D","9F0E","9F0F",
                "9F10","9F11","9F12","9F13","9F14","9F15","9F16","9F17","9F18","9F19",
                "9F1A","9F1B","9F1C","9F1D","9F1E","9F1F","9F20","9F21","9F22","9F23",
                "9F24","9F26","9F27","9F29","9F2A","9F2D","9F2E","9F2F","9F32","9F33",
                "9F34","9F35","9F36","9F37","9F38","9F39","9F3A","9F3B","9F3C","9F3D",
                "9F40","9F41","9F42","9F43","9F44","9F45","9F46","9F47","9F48","9F49",
                "9F4A","9F4B","9F4C","9F4D","9F4E","9F4F","9F50","9F51","9F52","9F53",
                "9F54","9F55","9F56","9F57","9F58","9F59","9F5A","9F5B","9F5C","9F5D",
                "9F5E","9F5F","9F60","9F61","9F62","9F63","9F64","9F65","9F66","9F67",
                "9F68","9F69","9F6A","9F6B","9F6C","9F6D","9F6E","9F6F","9F70","9F71",
                "9F72","9F73","9F74","9F75","9F76","9F77","9F78","9F79","9F7A","9F7B",
                "9F7C","9F7D","9F7E","9F7F","BF0C","BF50","BF60","DF01","DF02","DF03",
                "DF04","DF05","DF06","DF07","DF08","DF09","DF0B","DF0C","DF0D","DF0E",
                "DF0F","DF12","DF13","DF14","DF15","DF16","DF17","DF18","DF20","DF21",
                "DF22","DF23","DF24","DF25","DF26","DF40","DF41","DF42","DF43","DF44",
                "DF47","DF48","DF49","DF4A","DF4B","DF51","DF52","DF53","DF54","DF55",
                "DF56","DF57","DF60","DF61","DF62","DF63","DF64","DF65","DF6B","DF79"};
        for(int i=0;i<values2.length;i++) validtags2.put(values2[i],0);
        for(int i=0;i<values4.length;i++) validtags4.put(values4[i],0);

        tagprefixlength=new int[]
                { 0, 0,2,0,0,0,0,0,0,0,0,     0,0,0,0,0,0,0,0,0,0,    0,0,0,0,0,0,0,0,0,0,   2,2,2,2,2,4,0,0,0,0,
                        0,0,2,2,2,4,4,4,0,0,     0,0,2,4,4,2,0,2,4,4,    4,4,4};
        taglength=new int[]
                { 4, 8,10,3,6,6,6,5,4,4,4,      3,3,2,2,3,2,2,2,2,2,            2,2,2,2,1,2,1,3,2,12,
                        99,11,11,28,37,104,12,6,2,2,   8,15,99,99,0,204,9999,9999,2,3,  3,8,48,120,255,35,3,11,9999,999,
                        9999,999,999};
        tagtype=new int[]     //0=n=bcd(halflength),1=a=ascii,2=b=binary1to1,3=z=binary2to1(halflength)
                {0,  0,0,0,0,0,0,0,0,0,0,     0,0,0,0,0,0,0,0,0,0,    0,0,0,2,0,0,0,0,0,0,   1,0,0,1,3,3,1,1,1,0,
                        1,1,1,1,1,1,1,1,0,1,     1,2,2,1,2,0,0,0,1,1,    1,1,1
                };
        //READY
        /*context */msg800contextpresenttags=    new int[]{2,3,4,7,11,14,22,24,25,35,41,42,49,55};
        /*questionnaire*/msg800questpresenttags=      new int[]{3,4,7,11,22,24,25,41,42,63};
        /*sale    */msg200presenttags=    new int[]{2,3,4,7,11,14,22,24,25,35,41,42,49,52,55,62};
        /*oflnsale*/msg220presenttags=    new int[]{2,3,4,11,12,13,14,22,24,25,35,38,41,42,49,55,62};

        /*refound */msg400presenttags=    new int[]{2,3,4,11,12,13,14,22,24,25,35,37,41,42,49,52,55,62};

        /*settlini*/msg500initpresenttags=new int[]{3,11,24,41,42,49,60,63};
        /*setlbatc*/msg320presenttags=    new int[]{2,3,4,11,12,13,14,22,24,25,35,37,38,41,42,49,55,62};
        /*settlend*/msg500endpresenttags= new int[]{3,11,24,41,42,49,60,63};
    }
    void readconfigandvars()
    {   String path= Environment.getExternalStorageDirectory()+"/transactionsdk";
        File folder1 = new File(path);
        if(!folder1.exists())folder1.mkdirs();

        File file = new File(path+"/config.txt");
        try {   BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null)
            {   if(line.startsWith("TID="))     g_tid=line.split("=",0)[1];
            else if(line.startsWith("MID="))g_mid=line.split("=",0)[1];
            else if(line.startsWith("IP="))g_serverip=line.split("=",0)[1];
            else if(line.startsWith("PORT="))g_port=Integer.parseInt(line.split("=",0)[1]);
            else if(line.startsWith("TIMEOUT="))g_timeout=Integer.parseInt(line.split("=",0)[1]);
            else if(line.startsWith("CURRENCYCODE="))g_currencycode=line.split("=",0)[1];
            }
            br.close();
        }
        catch (IOException e) {
            OnPrint("Err, file not found:config.txt");
            //SDKHandler(SDKEvent.PRINT,"Err, file not found:config.txt",null);
            //log();
            Log.e("missing","Err, file not found:config.txt"); }


        file = new File(path+"/invoice.txt");
        try {   BufferedReader br = new BufferedReader(new FileReader(file));
            g_invoice=Integer.parseInt(br.readLine());
            br.close();
        }

        catch (IOException e){  Log.e("INIT","Err, file not found:invoice");
            updateconfig("invoice"); }

        file = new File(path+"/stan.txt");
        try {   BufferedReader br = new BufferedReader(new FileReader(file));
            g_stan=Integer.parseInt(br.readLine());
            br.close();
        }
        catch (IOException e){  Log.e("INIT","Err, file not found:stan");
            updateconfig("stan"); }

        file = new File(path+"/offlinecount.txt");
        try {   BufferedReader br = new BufferedReader(new FileReader(file));
            g_offlinecount=Integer.parseInt(br.readLine());
            br.close();
        }
        catch (IOException e){  Log.e("INIT","Err, file not found:offlinecount");
            updateconfig("offlinecount"); }


        file = new File(path+"/settlement.txt");
        try {   BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null)
            {   if(line.startsWith("Batch="))g_settlement_batch=Integer.parseInt(line.split("=",0)[1]);
            else if(line.startsWith("Count="))g_settlement_count=Integer.parseInt(line.split("=",0)[1]);
            else if(line.startsWith("Sum="))g_settlement_sum=Integer.parseInt(line.split("=",0)[1]);
            }
            br.close();
        }
        catch (IOException e){ Log.e("INIT","Err, file not found:settlement");
            updateconfig("settlement"); }

        while(g_mid.length()<15)g_mid+=" ";
        while(g_tid.length()<8)g_tid="0"+g_tid;
    }
    void storetransaction(Boolean online,String pan,String expdate,String amount,String trackdata,String currencycode,String emv,String receipt,String rrn,String time,String date)
    {   String encrypted=encrypttransaction(pan+"="+expdate+"="+amount+"="+trackdata+"="+currencycode+"="+emv+"="+receipt+"="+rrn+"="+time+"="+date);
        //Log.e("stored:"+rrn,"["+pan+"="+expdate+"="+amount+"="+trackdata+"="+currencycode+"="+emv+"="+receipt+"="+rrn+"]");
        String path=Environment.getExternalStorageDirectory()+"/transactionsdk/";


        try {   BufferedWriter bw=new BufferedWriter(new FileWriter(path+(online?"transactions.txt":"offlinetransactions.txt"), true));//"transactions.txt"
            bw.write(encrypted);
            bw.newLine();
            bw.flush();
            bw.close();

            //DOME!!!!!!!!!!!! when development is over remove this backup!!!!!!!!!
            BufferedWriter bw1=new BufferedWriter(new FileWriter(path+"transactions-backup.txt", true));
            bw1.write(encrypted);
            bw1.newLine();
            bw1.flush();
            bw1.close();
        }
        catch (IOException ioe)
        {   ioe.printStackTrace();
            Log.e("error:",ioe.getMessage());
        }
    }
    String[] gettransactions(Boolean online)
    {   File file = new File(Environment.getExternalStorageDirectory()+"/transactionsdk/"+(online?"transactions.txt":"offlinetransactions.txt"));
        //log("path1 "+file.getPath()+" "+file.getAbsolutePath()+" len:"+file.length()+" lastmodif:"+file.lastModified());
        if(file.exists())
        {   try {   ArrayList<String> lines=new ArrayList<String>();
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null)
                if(line!=null)
                {   if(line.length()>30){lines.add(decrypttransaction(line));}
                }
            br.close();
            // Log.d("line","got "+lines.size()+" lines");
            String[] toreturn=new String[lines.size()];
            for(int i=0;i<lines.size();i++)
            {
                toreturn[i]=lines.get(i);
            }

            return toreturn;
        }
        catch (IOException e) {
            OnPrint("Err, file not found:"+(online?"transactions.txt":"offlinetransactions.txt"));
            //SDKHandler(SDKEvent.PRINT,"Err, file not found:"+(online?"transactions.txt":"offlinetransactions.txt"),null);
            //log();
            return new String[0];
        }
        }
        return null;
    }
    String encrypttransaction(String message)
    {   try {   String message1 = Base64.encodeToString(message.getBytes(), 0);
        String salt = "randpassasd12346";
        SecretKeySpec key = new SecretKeySpec(salt.getBytes(), "AES");
        Cipher c = Cipher.getInstance("AES");
        c.init(Cipher.ENCRYPT_MODE, key);
        byte[] encVal = c.doFinal(message1.getBytes());
        String encrypted = Base64.encodeToString(encVal, Base64.NO_WRAP);
        return encrypted;
    }
    catch(Exception e)
    {   e.printStackTrace();
    }
        return "";
    }
    String decrypttransaction(String message)
    {   try {   String salt = "randpassasd12346";
        Cipher c = Cipher.getInstance("AES");
        SecretKeySpec key = new SecretKeySpec(salt.getBytes(), "AES");
        c.init(Cipher.DECRYPT_MODE, key);
        byte[] storedstring = Base64.decode(message.getBytes(), 0);
        byte[] decoded64bytes = c.doFinal(storedstring);
        //String decodedstring64 = new String(decodedbytes);
        String decodedbytes=new String(Base64.decode(new String(decoded64bytes),0));
        //String decoded=new String();
        return decodedbytes;
    }
    catch(Exception e)
    {   e.printStackTrace();
    }
        return "";
    }
    void deletetransactions(Boolean online)
    {   String path=Environment.getExternalStorageDirectory()+"/transactionsdk/";
        try {   BufferedWriter bw=new BufferedWriter(new FileWriter(path+(online?"transactions.txt":"offlinetransactions.txt"), false));
            bw.write("");
            bw.newLine();
            bw.flush();
            bw.close();
        }
        catch (IOException ioe)
        {   ioe.printStackTrace();
        }
    }
    void updateconfig(String type)
    {   String content="";
        if(type.equals("invoice"))content=g_invoice+"";
        else if(type.equals("offlinecount")){ content=g_offlinecount+"";}
        else if(type.equals("stan")){content=g_stan+"";}
        else if(type.equals("settlement"))content="Batch="+(g_settlement_batch)+"\nCount="+g_settlement_count+"\nSum="+g_settlement_sum;
        else Log.e("EEEERRRRRR","Unrecognized config: "+type);

        try {   File file = new File(Environment.getExternalStorageDirectory(),"/transactionsdk/"+type+".txt");
            FileOutputStream fOut = new FileOutputStream(file);
            OutputStreamWriter writter = new OutputStreamWriter(fOut);
            writter.write(content);
            writter.close();
            fOut.flush();
            fOut.close();
        }
        catch (IOException e)
        {   //log();
            OnPrint("Err: cannot store transaction:"+e.getLocalizedMessage());
            //SDKHandler(SDKEvent.PRINT,"Err: cannot store transaction:"+e.getLocalizedMessage(),null);
            Log.e("Exception", "File write failed: " + e.toString());
        }

    }
    void increasebatchandsum(int sum)
    {   g_settlement_count++;
        g_settlement_sum+=sum;
        Log.w("increasebatch",g_settlement_sum+" "+sum);
        updateconfig("settlement");
    }

    //================================================
//================================================
//================================================ISO8583     ENCODER + DECODER + PRINTER+ tag63 + emv
    byte[] buildISO8583body(Hashtable<Integer,String> tags,int[] msgtagnames)
    {   boolean[] istagselected=new boolean[65];
        for(int i=0;i<65;i++)istagselected[i]=false;
        for(int i=0;i<msgtagnames.length;i++)istagselected[msgtagnames[i]]=true;
        byte[] bytemap=new byte[8];
        for(int i=0;i<8;i++)bytemap[i]=0;

        for(int i=0;i<8;i++)for(int j=0;j<8;j++)
        {   bytemap[i]+=istagselected[i*8+j+1]?Math.pow(2,7-j):0;
        }
        ArrayList<byte[]> taglist=new ArrayList<byte[]>();
        int tagslength=0;
        for(int i=1;i<65;i++)if(istagselected[i])
        {   byte[] finalbytes=new byte[0];
            if(tagprefixlength[i]==0)
            {   String tag=tags.get(i);


                if(tag==null)Log.e("null tag1",""+i);
                if(tagtype[i]==0)finalbytes=stringtobcd(tag);
                else if(tagtype[i]==1)finalbytes=stringtoascii(tag);
                else if(tagtype[i]==2)finalbytes=stringtobinary(tag);
                else if(tagtype[i]==3)finalbytes=stringtoz(tag);



                if(finalbytes.length!=taglength[i])
                {   String toshow="Invalid lengths: tag:"+i+" got::"+finalbytes.length+
                        " type:"+tagtype[i]+", should be:"+taglength[i];
                    Log.e("Invalid len",toshow);
                    //toprint+="Invalid length: "+toshow+"\n";
                    OnPrint(toshow);
                    //SDKHandler(SDKEvent.PRINT,toshow,null);
                    //log();
                }
                tagslength+=finalbytes.length;
                taglist.add(finalbytes);
            }
            else if(tagprefixlength[i]>0)
            {   String tag=tags.get(i);

                if(tag==null)Log.e("null tag2",""+i);
                byte[] bytes=new byte[0];
                if(tagtype[i]==0)bytes=stringtobcd(tag);
                else if(tagtype[i]==1)bytes=stringtoascii(tag);
                else if(tagtype[i]==2)bytes=stringtobinary(tag);
                else if(tagtype[i]==3)bytes=stringtoz(tag);
                int lenlen= tagprefixlength[i];   lenlen=(lenlen+lenlen%2)/2;
                int len=inttobcd(bytes.length*(tagtype[i]==0||tagtype[i]==3?2:1));

                finalbytes=new byte[lenlen+bytes.length];
                for(int j=lenlen-1;j>=0;j--)
                {   finalbytes[j]=(byte)(len%256);
                    len=len/256;
                }

                System.arraycopy(bytes,0,finalbytes,lenlen,bytes.length);
                tagslength+=finalbytes.length;
                //Log.e("Tag "+i,tag+" "+new String(finalbytes));
                taglist.add(finalbytes);
            }
            else Log.e("Invalid len","invalid length type for tag:"+i);
            //                    String print1=new String(finalbytes).replace("\n","N")+" "+
            //                            new String(finalbytes).length()+" "+
            //                            finalbytes.length+"  "+
            //                            bytestostring(finalbytes,"int").length()+" "+
            //                            bytestostring(finalbytes,"int");
            //toprint+="TAG "+i+": "+print1+"\n";
        }
        byte[] body=new byte[tagslength];
        int arraysize=taglist.size(),position=0;
        for(int i=0;i<arraysize;i++)
        {   byte[] tag=taglist.get(i);
            System.arraycopy(tag,0,body,position,tag.length);
            position+=tag.length;
        }
        byte[] prefix=new byte[]{0x00,0x00,   0x60,   0x00,0x02,   0x00,0x00};// size2,id1,source2,dest2
        byte[] msgtype=new byte[]{stringtobcd(tags.get(0).substring(0,2))[0],stringtobcd(tags.get(0).substring(2,4))[0]};
        byte[] tosend=joinarrays(new byte[][]{prefix,msgtype,bytemap,body});//   prefix+bytemap+tagString.getBytes();
        int len=prefix.length-2+msgtype.length+bytemap.length+body.length;
        tosend[0]=(byte)(len/256);
        tosend[1]=(byte)(len%256);
        return tosend;
    }
    Hashtable<Integer,int[]> decodeiso8583(int[] array)
    {   if(array==null){Log.e("ERR decode iso8583","Can't decode NULL string (iso8583 decoder)");return null;}
        int msglen=array.length;
        if(msglen<4){Log.e("ERR decode iso8583","invalid length of received iso8583");return null;}

        byte[] bitmaparray=new byte[8];
        for(int i=0;i<8;i++)bitmaparray[i]=(byte)array[i+9];
        String bitmapstring=bytestostring(bitmaparray,"bit"),bitmapvalues="";
        Boolean[] bitmap= new Boolean[65];
        for(int i=0;i<64;i++){bitmap[i+1]=bitmapstring.charAt(i)==49;if(bitmap[i+1])bitmapvalues+=(i+1)+",";}
        Hashtable<Integer,int[]> tags=new Hashtable<>();
        tags.put(0,new int[]{bcdtoint(array[7]),bcdtoint(array[8])});
        int start=17;
        for(int i=1;i<bitmap.length;i++)if(bitmap[i])
        {   int len=taglength[i];
            if(tagprefixlength[i]>0)
            {   if(start+(tagprefixlength[i]/2)>msglen)
            {   String errstring="Error:invalid length of tag length indicator:"+(i)+":"+tagprefixlength[i];
                OnPrint(errstring);
                //SDKHandler(SDKEvent.PRINT,errstring,null);
                //log();
                Log.e("Err",errstring);
                return null;
            }
                try {   len = bcdtoint(array[start]);
                    if(tagprefixlength[i]>2)
                        len=len*100+bcdtoint(array[start+1]);
                    if(tagprefixlength[i]>4)
                        len=len*100+bcdtoint(array[start+2]);
                    if(tagtype[i]==0||tagtype[i]==3)len=len/2;
                }
                catch(Exception e)
                {   String errstring="Error:length of tag is not anumber (tag"+(i)+"):"+e.getMessage();
                    OnPrint(errstring);
                    //SDKHandler(SDKEvent.PRINT,errstring,null);
                    //log();
                    Log.e("Err",errstring);
                    return null;
                }
                start+=(tagprefixlength[i]/2);
            }
            if(start+len>msglen)
            {   String errstring="Error:invalid length of tag "+(i)+":"+len+" "+(start+len)+" "+msglen;
                OnPrint(errstring);
                //SDKHandler(SDKEvent.PRINT,errstring,null);
                //log();
                Log.e("Err",errstring);
                return null;
            }
            int[] tag=new int[len];
            System.arraycopy(array, start, tag, 0, len);

            //for(int k=0;k<tag.length;k++)toprint111+=tag[k]+",";

            tags.put(i,tag);
            start+=len;
        }
        if(start<msglen-1)
        {   String errstring="Error:unprocessed data at the end of msg:"+start+"<"+msglen;
            OnPrint(errstring);
            //SDKHandler(SDKEvent.PRINT,errstring,null);
            //log();
            Log.e("Err",errstring);
        }
        return tags;
    }
    Hashtable<String,String> decodetag63(int[] array)
    {   Hashtable<String,String> tags=new Hashtable<>();
        for(int i=0;i<array.length;i++)
        {   int len=(array[i]-48)*100+(array[i+1]-48)*10+(array[i+2]-48);
            String val="";
            for(int j=i+5;j<i+5+len;j++)val+=(char)array[j];
            tags.put((char)array[i+3]+""+(char)array[i+4],val);
            i+=5+len-1;
        }
        return tags;
    }
    Hashtable<String,String> decodeemv(String tlvvalues)
    {   Hashtable<String,String> toreturn= new Hashtable<String, String>();
        char[] values=new char[tlvvalues.length()];
        tlvvalues.toUpperCase().getChars(0,tlvvalues.length(),values,0);
        for(int i=0;i<values.length-5;i++)
        {   int offset=2;
            String name=values[i]+""+values[i+1];
            if(!validtags2.containsKey(name))
            {   name=values[i]+""+values[i+1]+values[i+2]+values[i+3];offset=4;
                if(!validtags4.containsKey(name))
                {   OnPrint("Unable to decode tag:"+name);
                    //SDKHandler(SDKEvent.PRINT,"Unable to decode tag:"+name,null);
                    //log();
                    Log.e("Parse TVL", "Unable to parse the tag:"+name);
                }
            }
            int length= Integer.decode("0x"+values[i+offset]+values[i+offset+1])*2;
            String tagvalue="";
            for(int j=0;j<length;j++)tagvalue+=values[i+offset+2+j];
            toreturn.put(name,tagvalue);
            i+=offset+2+length-1;//-1 beacause the for has a i++
        }
        return toreturn;
    }
    void printiso8583(byte[] array,String type)
    {   int[] array2=new int[array.length];
        for(int i=0;i<array.length;i++){array2[i]=array[i];if(array2[i]<0)array2[i]+=256;}
        printiso8583(array2,type);
    }
    void printiso8583(int[] array,String type)
    {   Log.d("ISO8583print","------START "+type+"-----------------");
        if(array==null){Log.d("ISO8583print","------END---ERROR: null array");return;}
        Hashtable<Integer,int[]> res=decodeiso8583(array);
        if(res==null){Log.d("ISO8583print","------END---ERROR: null decoded iso");return;}
        String toprintmsg="",toprintmsg2="";
        for(int i=0;i<array.length;i++)
        {   String a=String.format("%x", array[i]).toUpperCase();
            if(a.length()==1)a="0"+a;
            toprintmsg+=array[i]+"["+((char)array[i])+"],";
            toprintmsg2+=a+" ";
        }//"["+((char)array[i])+"],"
        Log.d("decode 0",toprintmsg);
        Log.d("decode 1",toprintmsg2);
        Log.d("decode 2","Len:"+(array[0]*256+array[1]));
        Log.d("decode 3","Type:"+bcdtostring(array[7])+bcdtostring(array[8]));
        toprintmsg="";
        for(int i=0;i<128;i++)if(res.containsKey(i))toprintmsg+=i+",";
        Log.d("decode 4","Bitmap:"+toprintmsg);
        for(int i=0;i<128;i++)if(res.containsKey(i))
        {   String toprint1="",toprint2="";
            int[] value=res.get(i);
            for(int j=0;j<value.length;j++)
            {   toprint1+=(char)value[j];
                String a=String.format("%x", value[j]).toUpperCase();
                if(a.length()==1)a="0"+a;
                toprint2+=a+(j==value.length-1?"":" ");   }
            Log.d("DE tag: "+i,"[len:"+value.length+(tagprefixlength[i]>0?"(var":"(fix")+")]["+toprint1+"]  ["+toprint2+"]");
        }
        if(res.containsKey(63))
        {   if(res.get(0)[0]!=5)
        {   Hashtable<String,String> tag63pieces=decodetag63(res.get(63));
            Enumeration enu = tag63pieces.keys();
            while (enu.hasMoreElements()) {
                String key=enu.nextElement().toString();
                Log.d("Tag63: "+key,tag63pieces.get(key));
            }
        }
        }
        Log.d("ISO8583print","------END "+type+"-------------------");
    }


    //200 ONLINE SALE
    Hashtable<Integer,String> build200tags(String pan,String expirationdate,String amount,String trackdata,
                                           String currencycode,String pinblock,String emv,String receipt)
    {   //2,3,4,11,14,22,24,25,35,41,42,49,52,55,62
        String stan=g_stan+"";     while(stan.length()<6)stan="0"+stan;
        g_stan++;updateconfig("stan");
        if(expirationdate.length()>4)expirationdate=expirationdate.substring(0,4);
        if(currencycode.length()>3)currencycode=currencycode.substring(1,4);
        while(amount.length()<taglength[4]*2)amount="0"+amount;
        while(pinblock.length()<16)pinblock="0"+pinblock;
        while(receipt.length()<6)receipt="0"+receipt;

        //Log.d("200 info","receipt: "+receipt+" tid:"+g_tid.length()+"   mid:"+g_mid.length()+" pinblock:"+pinblock.length()+" trackdata:"+trackdata);
        Hashtable<Integer,String> message =new Hashtable<Integer,String>();
        message.put(0,"0200");//msg type
        message.put(2,pan);//(PAN)   [5A]
        message.put(3,"000000");//Processing code
        message.put(4,amount);//Amount, transaction    [9F02]
        message.put(7,gettime("MMddHHmmss"));
        message.put(11,stan);//System trace audit number (STAN)
        message.put(14,expirationdate);//Expiration date  [5F24]
        message.put(22,"051");//Point of service entry mode
        message.put(24,"0002");//Network something6000000002
        message.put(25,"0");//Point of service condition code
        message.put(35,trackdata);//trackdata 2
        message.put(41,g_tid+"");
        message.put(42,g_mid+"");
        message.put(49,currencycode);//Currency code, transaction   [9F2A]
        message.put(52,pinblock+"");//pinblock
        message.put(55,emv);//emv data
        message.put(62,receipt);//receiptnr

        //Log.w("OFFLINE : TAG 4",amount+","+amount.length()+","+taglength[4]);
        return message;
    }
    //220 OFFLINE SALE
    Hashtable<Integer,String> build220tags(String pan,String expirationdate,String amount,String trackdata,
                                           String currencycode,String emv,String receipt,String time,String date)
    {   //2,3,4,11,12,13,14,22,24,25,35,38,41,42,49,55,62
        String stan=g_stan+"";     while(stan.length()<6)stan="0"+stan;
        g_stan++;updateconfig("stan");
        if(expirationdate.length()>4)expirationdate=expirationdate.substring(0,4);
        if(currencycode.length()>3)currencycode=currencycode.substring(1,4);
        while(amount.length()<taglength[4]*2)amount="0"+amount;
        while(receipt.length()<6)receipt="0"+receipt;

        Hashtable<Integer,String> message =new Hashtable<Integer,String>();
        message.put(0,"0220");//msg type
        message.put(2,pan);//(PAN)   [5A]
        message.put(3,"000000");//Processing code
        message.put(4,amount);//Amount, transaction    [9F02]
        //message.put(7,gettime("MMddHHmmss"));//Transmission date & time
        message.put(11,stan);//System trace audit number (STAN)
        message.put(12,time);//gettime("hhmmss")
        message.put(13,date);//gettime("MMdd")
        message.put(14,expirationdate);//Expiration date  [5F24]
        message.put(22,"051");//Point of service entry mode
        message.put(24,"0002");//920000 or 920????//Function code (ISO 8583:1993), or network international identifier (NII)
        message.put(25,"0");//Point of service condition code
        message.put(35,trackdata);//trackdata 2
        message.put(38,"000000");
        message.put(41,g_tid+"");
        message.put(42,g_mid+"");
        message.put(49,currencycode);//Currency code, transaction   [9F2A]
        message.put(55,emv);//emv data
        message.put(62,receipt);//receiptnr
        return message;
    }
    //320 BATCH UPLOAD ADVICE
    Hashtable<Integer,String> build320tags(String pan,String expdate,String amount,String trackdata,String currencycode,String emv,
                                           String receipt,String rrn,String time,String date)
    {   // 2,3,4,11,12,13,14,22,24,25,35,37,38,41,42,49,55,62
        String stan=g_stan+"";    while(stan.length()<6)stan="0"+stan;
        g_stan++;updateconfig("stan");
        if(expdate.length()>4)expdate=expdate.substring(0,4);
        if(currencycode.length()>3)currencycode=currencycode.substring(1,4);
        //Log.e("amount1 ",amount);
        while(amount.length()<taglength[4]*2)amount="0"+amount;
        //Log.e("amount2 ",amount);
        while(receipt.length()<6)receipt="0"+receipt;

        Hashtable<Integer,String> message =new Hashtable<Integer,String>();
        message.put(0,"0320");//msg type
        message.put(2,pan);//msg type
        message.put(3,"000000");//Processing code
        message.put(4,amount);//Amount, transaction    [9F02]
        message.put(7,gettime("MMddHHmmss"));//Transmission date & time
        message.put(11,stan);//System trace audit number (STAN)
        message.put(12,time);//gettime("hhmmss")
        message.put(13,date);//gettime("MMdd")
        message.put(14,expdate);
        message.put(22,"051");//Point of service entry mode
        message.put(24,"0002");//920000 or 920????//Function code (ISO 8583:1993), or network international identifier (NII)
        message.put(25,"0");//Point of service condition code
        message.put(35,trackdata);//trackdata 2
        message.put(37,rrn);//Retrieval reference number
        message.put(38,"000000");
        message.put(41,g_tid+"");
        message.put(42,g_mid+"");
        message.put(49,currencycode);//Currency code, transaction   [9F2A]
        message.put(55,emv);//emv data
        message.put(62,receipt);//receiptnr

        return message;
    }
    //400 UNIVERSAL REVERSAL
    Hashtable<Integer,String> build400tags(String pan,String expirationdate,String amount,String trackdata,
                                           String currencycode,String pinblock,String emv,String receipt,String rrn,String time,String date)

    {   //2,3,4,11,12,13,14,22,24,25,35,37,41,42,49,52,55,62
        String stan=g_stan+"";   while(stan.length()<6)stan="0"+stan;
        g_stan++;updateconfig("stan");
        if(expirationdate.length()>4)expirationdate=expirationdate.substring(0,4);
        if(currencycode.length()>3)currencycode=currencycode.substring(1,4);
        while(amount.length()<taglength[4]*2)amount="0"+amount;
        while(pinblock.length()<16)pinblock="0"+pinblock;
        while(receipt.length()<6)receipt="0"+receipt;

        Hashtable<Integer,String> message =new Hashtable<Integer,String>();
        message.put(0,"0400");//msg type
        message.put(2,pan);//(PAN)   [5A]
        message.put(3,"000000");//Processing code
        message.put(4,amount);//Amount, transaction    [9F02]
        message.put(7,gettime("MMddHHmmss"));//Transmission date & time
        message.put(11,stan);//System trace audit number (STAN)
        message.put(12,time);//gettime("hhmmss")
        message.put(13,date);//gettime("MMdd")
        message.put(14,expirationdate);//Expiration date  [5F24]
        message.put(22,"051");//Point of service entry mode
        message.put(24,"0002");//400 or 401?????
        message.put(25,"0");//Point of service condition code
        message.put(35,trackdata);//trackdata 2
        message.put(37,rrn);//trackdata 2
        message.put(41,g_tid+"");
        message.put(42,g_mid+"");
        message.put(49,currencycode);//Currency code, transaction   [9F2A]
        message.put(52,pinblock+"");//pinblock
        message.put(55,emv);//emv data
        message.put(62,receipt);//receiptnr

        return message;
    }
    //500 SETTLEMENT INIT  (RECONCILIATION)
    Hashtable<Integer,String> build500inittags()
    {   //2,3,11,24,41,42,49,60,63
        String stan=g_stan+"";      while(stan.length()<6)stan="0"+stan;
        g_stan++;updateconfig("stan");
        String batchnr=g_settlement_batch+"";
        String batchsum=(g_settlement_sum)+"";  while(batchsum.length()<12)batchsum="0"+batchsum;
        String batchcount=g_settlement_count+"";  while(batchcount.length()<3)batchcount="0"+batchcount;
        String currencycode=g_currencycode; while(currencycode.length()<3)currencycode="0"+currencycode;
        String zeros15="000000000000000",zeros30=zeros15+zeros15;
        String batchtotals=zeros30+batchcount+batchsum+zeros30+zeros15;

        //Log.w("totals:","count:["+batchcount+"] sum:["+batchsum+"]");

        Hashtable<Integer,String> message =new Hashtable<Integer,String>();
        message.put(0,"0500");//msg type
        message.put(3,"920000");//Processing code
        message.put(11,stan);//System trace audit number (STAN)
        message.put(24,"0002");//Network something6000000002
        message.put(41,g_tid+"");
        message.put(42,g_mid+"");
        message.put(49,g_currencycode);
        message.put(60,batchnr);
        message.put(63,batchtotals);
        return message;
    }
    //500 SETTLMENT FINSIHED SENDING (RECONCILIATION ADVICE)
    Hashtable<Integer,String> build500endtags()
    {   //3,11,24,41,42,49,60,63
        String stan=g_stan+"";   while(stan.length()<6)stan="0"+stan;
        g_stan++;updateconfig("stan");
        String batchnr=g_settlement_batch+"";
        String batchsum=g_settlement_sum+"";  while(batchsum.length()<12)batchsum="0"+batchsum;
        String batchcount=g_settlement_count+"";  while(batchcount.length()<3)batchcount="0"+batchcount;
        String currencycode=g_currencycode; while(currencycode.length()<3)currencycode="0"+currencycode;
        String zeros15="000000000000000",zeros30=zeros15+zeros15;
        String batchtotals=zeros30+batchcount+batchsum+zeros30+zeros15;

        Hashtable<Integer,String> message =new Hashtable<Integer,String>();
        message.put(0,"0500");//msg type
        message.put(3,"960000");//Processing code
        message.put(11,stan);//System trace audit number (STAN)
        message.put(24,"0002");//Network something6000000002
        message.put(41,g_tid+"");
        message.put(42,g_mid+"");
        message.put(49,g_currencycode);
        message.put(60,batchnr);//batch number
        message.put(63,batchtotals);
        return message;
    }
    //800 GET CONTEXT
    Hashtable<Integer,String> build800contexttags(String pan,String expirationdate,String amount,String currencycode,
                                                  String trackdata,String emv)
    {   //{2,3,4,11,14,22,24,25,35,41,42,49,55};
        String stan=g_stan+"";while(stan.length()<6)stan="0"+stan;
        g_stan++;updateconfig("stan");
        if(expirationdate.length()>4)expirationdate=expirationdate.substring(0,4);
        if(currencycode.length()>3)currencycode=currencycode.substring(1,4);
        while(amount.length()<taglength[4]*2)amount="0"+amount;

        Hashtable<Integer,String> message =new Hashtable<Integer,String>();
        message.put(0,"0800");//msg type
        message.put(2,pan);//(PAN)   [5A]
        message.put(3,"770000");//Processing code
        message.put(4,amount);//Amount, transaction    [9F02]
        message.put(7,gettime("MMddHHmmss"));
        message.put(11,stan);//System trace audit number (STAN)
        message.put(14,expirationdate);//Expiration date  [5F24]
        message.put(22,"051");//Point of service entry mode
        message.put(24,"0002");//Network something
        message.put(25,"8");//Point of service condition code, 00-card present, 08-missing card
        message.put(35,trackdata);//trackdata 2
        message.put(41,g_tid+"");
        message.put(42,g_mid+"");
        message.put(44,"T000000120191210143818");
        message.put(49,currencycode);//Currency code, transaction   [9F2A]
        message.put(55,emv);//emv data
        return message;
    }
    //800 QUESTIONARE RESPONSE
    Hashtable<Integer,String> build800questtags(String amount,String tag63)
    {   //{3,4,11,22,24,25,41,42,63};
        String stan=g_stan+"";while(stan.length()<6)stan="0"+stan;
        g_stan++;updateconfig("stan");
        while(amount.length()<taglength[4]*2)amount="0"+amount;

        Hashtable<Integer,String> message =new Hashtable<Integer,String>();
        message.put(0,"0800");//msg type
        message.put(3,"790000");//Processing code
        message.put(4,amount);//Amount, transaction    [9F02]
        message.put(7,gettime("MMddHHmmss"));
        message.put(11,stan);//System trace audit number (STAN)
        message.put(22,"051");//Point of service entry mode
        message.put(24,"0002");//Network something
        message.put(25,"8");//Point of service condition code, 00-card present, 08-missing card
        message.put(41,g_tid+"");
        message.put(42,g_mid+"");
        message.put(63,tag63);
        //Log.w("QUEST RESPONSE: TAG 4",amount+","+amount.length());
        return message;
    }
    String gettime(String format)
    {   Calendar calendar = Calendar.getInstance();
        SimpleDateFormat mdformat = new SimpleDateFormat(format);
        return mdformat.format(calendar.getTime());
    }



    //================================================FORTMAT CONVERTERS
    int bcdtoint(int val)
    {   return (val/16)*10+(val%16);
    }
    int inttobcd(int val)
    {   return (val%10000/1000)*16*256+(val%1000/100)*256+(val%100/10)*16+(val%10);
    }
    String bcdtostring(int val)
    {   String a=((val/16)*10+(val%16))+"";
        if(a.length()<2)a="0"+a;
        return a;
    }
    byte[] stringtobcd(String msg)
    {   if(msg.length()%2==1)msg="0"+msg;
        int len=msg.length()/2;
        byte[] bytes=new byte[len];
        for(int i=0;i<len;i++)
        {   bytes[i]=(byte)Integer.parseInt(msg.substring(i*2,i*2+2),16);
            if(bytes[i]<0)bytes[i]+=256;
        }
        return bytes;
    }
    byte[] stringtoascii(String msg)
    {   return msg.getBytes();
    }
    byte[] stringtobinary(String msg)
    {   if(msg.length()%2==1)msg="0"+msg;
        int len=msg.length()/2;
        byte[] bytes=new byte[len];
        for(int i=0;i<len;i++)
        {   bytes[i]=(byte)Integer.parseInt(msg.substring(i*2,i*2+2),16);
            if(bytes[i]<0)bytes[i]+=256;
        }
        return bytes;
    }
    byte[] stringtoz(String msg)
    {   if(msg.length()%2==1)msg="0"+msg;
        int len=msg.length()/2;
        byte[] bytes=new byte[len];

        for(int i=0;i<len;i++)
        {   bytes[i]=(byte)Integer.parseInt(msg.substring(i*2,i*2+2),16);
            if(bytes[i]<0)bytes[i]+=256;
        }
        return bytes;
    }
    String bytestostring(int[] msg,String format)
    {   String toreturn="";
        for(int i=0;i<msg.length;i++)
            if(format.equals("int"))toreturn+=(msg[i]& 0xFF)+",";
            else if(format.equals("raw"))toreturn+=msg[i]+",";
            else if(format.equals("char"))toreturn+=(char)msg[i];
            else if(format.equals("byte"))toreturn+=String.format("%x", (msg[i]& 0xFF))+",";
            else if(format.equals("bit"))
            {   int a=msg[i];if(a<0)a=256+a;
                String val="";
                for(int j=0;j<8;j++)
                {   val=(a%2==1?"1":"0")+val;
                    a/=2;}
                toreturn+=val;//String.format("%08s", Integer.toBinaryString(100));//Integer.toString(msg[i],2)+",";
            }
        return toreturn;
    }
    String bytestostring(byte[] msg,String format)
    {   String toreturn="";
        for(int i=0;i<msg.length;i++)
            if(format.equals("int"))toreturn+=(msg[i]& 0xFF)+",";
            else if(format.equals("raw"))toreturn+=msg[i]+",";
            else if(format.equals("char"))toreturn+=(char)msg[i];
            else if(format.equals("byte"))toreturn+=String.format("%x", (msg[i]& 0xFF))+",";
            else if(format.equals("bit"))
            {   int a=msg[i];if(a<0)a=256+a;
                String val="";
                for(int j=0;j<8;j++)
                {   val=(a%2==1?"1":"0")+val;
                    a/=2;}
                toreturn+=val;//String.format("%08s", Integer.toBinaryString(100));//Integer.toString(msg[i],2)+",";
            }
        return toreturn;
    }
    byte[] joinarrays(byte[][] arrays)
    {   int len=0,sumlen=0;
        for(int i=0;i<arrays.length;i++)
            if(arrays[i]==null)
            {   //log();
                OnPrint("ERROR, null byte array "+i+" [1174]");
                //SDKHandler(SDKEvent.PRINT,"ERROR, null byte array "+i+" [1174]",null);
                Log.e("Error","null byte arrray "+i+" [1175]");
                return null;
            }
            else len+=arrays[i].length;
        byte[] toreturn=new byte[len];
        for(int i=0;i<arrays.length;i++)
        {   System.arraycopy(arrays[i],0,toreturn,sumlen,arrays[i].length);
            sumlen+=arrays[i].length;
        }
        return toreturn;
    }


//================================================SERVER COMMUNICATION

    public void DoOnlineTransaction(final String pan, final String expdate, final String amount, final String currencycode,
                             final String trackdata, final String emv, final String cryptogram, final String pinblock)//CRYPTOGRAM NOT USED!!!!! ACCORDING TO SYMPHOPAY DOCS
    {   //Hashtable<Integer,String> msg800tags= build800contexttags(pan,expdate,amount,currencycode,trackdata,emv);
        //Hashtable<Integer,String> msg200tags= build200tags(pan,expdate,amount,trackdata,currencycode,pinblock,emv,g_invoice+"");

        final byte[] msg800=buildISO8583body(build800contexttags(pan,expdate,amount,currencycode,trackdata,emv),
                                             msg800contextpresenttags);
        final byte[] msg200=buildISO8583body(build200tags(pan,expdate,amount,trackdata,currencycode,pinblock,emv,g_invoice+""),
                                             msg200presenttags);
        int[] msg200int=new int[msg200.length];
        for(int i=0;i<msg200.length;i++){msg200int[i]=msg200[i]; if(msg200int[i]<0)msg200int[i]+=256;}

        printiso8583(msg800,"800");
        printiso8583(msg200int,"200");


        Thread thread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try {   Thread.sleep(50);
                    Log.d("CONNECTION 1","########SERVER CALL START");

                    //SSLSocket socket=(SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket(g_serverip, g_port);
                    SSLSocket socket=getsocket();

                    Log.d("connect data:","["+g_serverip+":"+g_port+"|"+g_timeout+"]");
                    //socket.setSoTimeout(5000);
                    OutputStream out = socket.getOutputStream();
                    InputStream in = new BufferedInputStream(socket.getInputStream());
                    String success="init";

                    out.write(msg800,0,msg800.length);out.flush();

                    Log.d("CONNECTION 2","Sent 800");

                    int[] rec=readresponse(in);
                    if(rec==null)
                    {   Log.d("ERROR"," branch timeout for 800");
                        success="Timeout from server on msg 800.";
                    }
                    if(success.equals("init"))
                    {   Log.d("CONNECTION 3","Received 810 response   len:"+rec.length);
                        printiso8583(rec,"810");
                        out.write(msg200,0,msg200.length);out.flush();
                        Log.d("CONNECTION 4","Sent 200");
                        rec=readresponse(in);

                        if(rec==null)
                        {   Log.d("ERROR"," branch timeout for 200");
                            success="Timeout from server on msg 200.";
                        }
                        else{
                            Log.d("CONNECTION 5","Received 210 response");
                            Hashtable<Integer,int[]> resulttags=decodeiso8583(rec);
                            int[] code39=resulttags.get(39);
                            int resultcode=(code39[0]-48)*10+(code39[1]-48);
                            if(resultcode==0)success="SUCCESS";
                            else success=""+resultcode+(errorvaluies.containsKey(resultcode)?"["+errorvaluies.get(resultcode)+"]":"");
                            printiso8583(rec,"210");
                        }
                    }

                    Log.d("tag","########SERVER CALL END");
                    Log.d("data",pan+"|"+expdate+"|"+amount+"|"+trackdata+"|"+currencycode+"|"+emv+"|"+g_invoice+"|"+"000000000000");
                    if(success.equals("SUCCESS"))
                    {   Hashtable<Integer,int[]> tags=decodeiso8583(rec);
                        if(tags.containsKey(63))
                        {   Hashtable<String,String> tags63=decodetag63(tags.get(63));
                            if(tags63.containsKey("15"))
                            {   String[] lines=tags63.get("15").split(((char)28)+"");
                                //                                    for(int i=0;i<lines.length;i++)
                                //                                        Log.e(""+i,lines[i]);
                                //SDKHandler(SDKEvent.PRINT,"\n15:"+tags63.get("15")+"\n",null);
                                OnQR(lines);
                                //SDKHandler(SDKEvent.POPUP_QR,"",lines);
                            }
                            if(tags63.containsKey("16"))
                            {   //SDKHandler(SDKEvent.PRINT,"\n16:"+tags63.get("16")+"\n",null);
                                String[] lines=tags63.get("16").split(((char)28)+"");
                                //for(int i=0;i<lines.length;i++)Log.w("lines."+i,"["+lines[i]+"]");
                                if(lines.length<3)OnPrint("Eroare:Chestionarul nu are "+
                                        "numarul minim de campuri:"+lines.length);
//                                        SDKHandler(SDKEvent.PRINT,"Eroare:Chestionarul nu are "+
//                                        "numarul minim de campuri:"+lines.length,null);
                                else{   Questionnaire q=new Questionnaire();
                                    q.id=lines[0]+((char)28)+lines[1];
                                    q.questions=new Question[lines.length-2];
                                    for(int i=2;i<lines.length;i++)
                                    {   String[] sublines=lines[i].split(((char)30)+"");
                                        Question question=new Question();
                                        question.id=sublines[0];
                                        question.type=sublines[1];
                                        question.question=sublines[2];
                                        question.options=new String[sublines.length-3];
                                        question.optioncodes=new String[sublines.length-3];
                                        for(int j=3;j<sublines.length;j++)
                                        {   //Log.w("sublines "+j,sublines[j]+" /"+sublines.length);
                                            String[] subsublines=sublines[j].split(((char)31)+"");
                                            question.options[j-3]=subsublines[1];
                                            question.optioncodes[j-3]=subsublines[0];
                                        }
                                        q.questions[i-2]=question;

                                    }
                                    q.amount=amount;
                                    OnQuestionnaire(q);
                                    //SDKHandler(SDKEvent.QUESTIONNAIRE,"",q);


                                }
                                //SDKHandler(SDKEvent.POPUP,"TEEESTpiojrvpmwiejpcwie4pijpeija[wiecj[0WJC[AJC[WCEJFCMOAIWJPEIFJCPAIWEJFMCPAIWEJPCAIWJEFPCIAWJCPIAWCA",null);


                            }
                            if(tags63.containsKey("17"))
                                OnPopup(tags63.get("17"));
                            //SDKHandler(SDKEvent.POPUP,tags63.get("17"),null);
                            //
                        }
                        else //SDKHandler(SDKEvent.PRINT,"Lipseste tagul 63.",null);
                            OnPrint("Lipseste tagul 63.");
                        //log();
                        String rrn=bytestostring(tags.get(37),"char");
                        //Log.e("rrn","["+rrn+"]");
                        if(rrn.length()<1){Log.d("ERR RRN","received invalid rrn:["+rrn+"]");rrn="000000000000";}
                        increasebatchandsum(Integer.parseInt(amount));
                        storetransaction(true,pan,expdate,amount,trackdata,currencycode,emv,g_invoice+"",rrn,gettime("hhmmss"),gettime("MMdd"));
                        Log.w("transaction ok","Stored online transaction");
                        sendofflinebatch();
                    }
                    else Log.e("TRANSACTION FAILED","REASON:"+success);
                    OnOnlineProcessResult(success.equals("SUCCESS"),success.equals("SUCCESS")?"":success);
                    //OnOnlineProcessResult(true,success.equals("SUCCESS")?"8A023030":"8A023035");
                    //mEmvApi.sendOnlineProcessResult(0,success.equals("SUCCESS")?"8A023030":"8A023035");


                    out.close();
                    socket.close();
                }
                catch (IOException | InterruptedException  e)
                {   e.printStackTrace();
                    OnPrint("#Eroare:"+e.getMessage());
                    //SDKHandler(SDKEvent.PRINT,"#Eroare:"+e.getMessage(),null);
                    String responseData = "8A023035";//rejected
                    Log.d("tag","#transaction failed, error:"+e.getMessage());
                    Log.d("tag","########SERVER CALL END");
                    OnOnlineProcessResult(false,e.getMessage());
                    OnOnlineProcessResult(false,responseData);
                    //mEmvApi.sendOnlineProcessResult(0,responseData);
                    doautoreversal(pan,expdate,amount,currencycode,trackdata,emv,pinblock,g_invoice+"","000000000000");
                }
            }
        });
        thread.start();
    }
    public void DoOfflineTransaction(final String pan,final String expdate,final String amount,final String currencycode,
                              final String trackdata,final String emv)
    {   final String time=gettime("hhmmss"),date=gettime("MMdd");
        //Hashtable<Integer,String> msg800tags= build800contexttags(pan,expdate,amount,currencycode,trackdata,emv);
        //Hashtable<Integer,String> msg220tags= build220tags(pan,expdate,amount,trackdata,currencycode,emv,g_invoice+"",time,date);



        final byte[] msg800=buildISO8583body(build800contexttags(pan,expdate,amount,currencycode,trackdata,emv),
                                             msg800contextpresenttags);
        final byte[] msg220=buildISO8583body(build220tags(pan,expdate,amount,trackdata,currencycode,emv,g_invoice+"",time,date),
                                             msg220presenttags);
        int[] msg220int=new int[msg220.length];
        for(int i=0;i<msg220.length;i++){msg220int[i]=msg220[i]; if(msg220int[i]<0)msg220int[i]+=256;}

        printiso8583(msg220int,"220");


        Thread thread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try {   Thread.sleep(50);
                    Log.d("CONNECTION 1","########SERVER CALL START");


                    SSLSocket socket=getsocket();


                    //SSLSocket socket=(SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket(g_serverip, g_port);
                    Log.d("connect data:","["+g_serverip+":"+g_port+"|"+g_timeout+"]");
                    //socket.setSoTimeout(5000);
                    OutputStream out = socket.getOutputStream();
                    InputStream in = new BufferedInputStream(socket.getInputStream());
                    String success="init";

                    out.write(msg800,0,msg800.length);out.flush();
                    Log.d("CONNECTION 2","Sent 800");

                    int[] rec=readresponse(in);
                    if(rec==null)
                    {   Log.d("ERROR"," branch timeout for 800");
                        success="Timeout from server on msg 800.";
                    }
                    if(success.equals("init"))
                    {   Log.d("CONNECTION 3","Received 810 response   len:"+rec.length);
                        printiso8583(rec,"810");
                        out.write(msg220,0,msg220.length);out.flush();
                        Log.d("CONNECTION 4","Sent 220");
                        rec=readresponse(in);

                        if(rec==null)
                        {   Log.d("ERROR"," branch timeout for 230");
                            success="Timeout from server on msg 230.";
                        }
                        else{   Log.d("CONNECTION 5","Received 230 response");
                            Hashtable<Integer,int[]> resulttags=decodeiso8583(rec);
                            int[] code39=resulttags.get(39);
                            int resultcode=(code39[0]-48)*10+(code39[1]-48);
                            if(resultcode==0)success="SUCCESS";
                            else success=""+resultcode+(errorvaluies.containsKey(resultcode)?"["+errorvaluies.get(resultcode)+"]":"");
                            printiso8583(rec,"230");
                        }
                    }


                    if(success.equals("SUCCESS"))
                    {   Hashtable<Integer,int[]> tags=decodeiso8583(rec);
                        String rrn=bytestostring(tags.get(37),"char");;//"000000000000";//bcdtostring(tags.get(37));
                        if(rrn.length()<1){Log.e("ERR RRN","received invalid rrn:["+rrn+"]");rrn="000000000000";}
                        increasebatchandsum(Integer.parseInt(amount));
                        storetransaction(true,pan,expdate,amount,trackdata,currencycode,emv,g_invoice+"",rrn,time,date);
                        Log.d("CONNECTION 3","Received 230 response   len:"+rec.length);
                        sendofflinebatch();
                    }
                    else{  storetransaction(false,pan,expdate,amount,trackdata,currencycode,emv,g_invoice+"","000000000000",time,date);
                        Log.d("CONNECTION 3","Failed to send offline, 220 transaction stored");
                    }
                    //mEmvApi.sendOnlineProcessResult(success.length()>0?"8A023035":"8A023030");
                    Log.d("tag","########SERVER CALL END");
                }
                catch (IOException | InterruptedException e)
                {   e.printStackTrace();
                    OnPrint("#Eroare:"+e.getMessage());
                    //SDKHandler(SDKEvent.PRINT,"#Eroare:"+e.getMessage(),null);
                    storetransaction(false,pan,expdate,amount,trackdata,currencycode,emv,g_invoice+"","000000000000",time,date);
                    //String responseData = "8A023035";//rejected
                    Log.d("tag","#transaction failed, error:"+e.getMessage());
                    Log.d("tag","########SERVER CALL END");
                    //mEmvApi.sendOnlineProcessResult(responseData);
                }
            }
        });
        thread.start();
    }
    void sendofflinebatch()
    {   if(g_offlinecount==0){g_issendingofflinebatch=false; return;}
        g_issendingofflinebatch=true;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try { Log.d("CON 1","\n######## BATCH 220 RESEND START");
                    //SOCKET INIT

                    SSLSocket socket=getsocket();

                    //SSLSocket socket = (SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket(g_serverip, g_port);
                    Log.d("connect data:", "[" + g_serverip + ":" + g_port + "|" + g_timeout + "]");
                    //socket.setSoTimeout(5000);
                    OutputStream out = socket.getOutputStream();
                    InputStream in = new BufferedInputStream(socket.getInputStream());
                    String success = "READY";
                    int[] rec=new int[0];

                    //SEND OFFLINE TRANSACTIONS
                    if(g_offlinecount>0)
                    {   String[] transactions = gettransactions(false);
                        if (g_offlinecount != transactions.length) Log.e("ERRORRR!!!!!!",
                                "INVALID 220 offline COUNT COMPARED TO STORED MSG:g_offl.:" + g_offlinecount + " msgs:" + transactions.length);
                        for (int i = 0; i < transactions.length; i++)if (transactions[i] != null) if (transactions[i].length() > 4)
                        {    String[] lines=transactions[i].split("=");
                            //Log.e("trans ",transactions[i]);/*rrn:lines[7],*/
                            if(lines.length<10){Log.e("invalid transaction ","Invalid decoded transaction("+lines.length+"):"+transactions[i]); continue;}
                            //Hashtable<Integer,String> tags=build220tags(lines[0],lines[1],lines[2],lines[3],lines[4],lines[5],lines[6],lines[8],lines[9]);

                            byte[] msg=buildISO8583body(build220tags(lines[0],lines[1],lines[2],lines[3],lines[4],lines[5],lines[6],lines[8],lines[9]),
                                                        msg220presenttags);
                            out.write(msg, 0, msg.length);
                            out.flush();
                            Log.d("CONN 4", "Sent 220 " + (i + 1) + "/" + transactions.length);
                            printiso8583(msg, "220");

                            rec = readresponse(in);
                            if (rec == null)
                            {   Log.d("ERROR", " branch timeout for 230");
                                success = "Timeout from server on msg 230.";
                            }
                            else{   Log.d("CONN 5", "Rec 230 Confirm OK. (" + (i + 1) + "/" + transactions.length + ")");
                                printiso8583(rec, "230");
                                Hashtable<Integer,int[]> rectags=decodeiso8583(rec);
                                String rrn=bytestostring(rectags.get(37),"char");;//"000000000000";//bcdtostring(tags.get(37));
                                if(rrn.length()<1){Log.e("ERR RRN2","received invalid rrn:["+rrn+"]");rrn="000000000000";}
                                increasebatchandsum(Integer.parseInt(lines[2]));
                                storetransaction(true,lines[0],lines[1],lines[2],lines[3],lines[4],lines[5],lines[6],rrn,lines[8],lines[9]);
                            }
                        }
                        if(success.equals("READY"))
                        {   Log.d("CON 6", "Finished sending offline transactions.");
                            deletetransactions(false);
                            g_offlinecount=0;
                            updateconfig("offlinecount");
                        }
                    }
                    g_issendingofflinebatch=false;
                }
                catch (IOException e)
                {   e.printStackTrace();
                    OnPrint("#Batch 220 failed, error:"+e.getMessage());
                    OnPrint("######## BATCH 220 END");
                    g_issendingofflinebatch=false;
                }

            }
        });
        thread.start();

    }
    void doautoreversal(String pan, String expdate,final String amount,final String currencycode,String trackdata,String emv,String pinblock,String receiptnr,String rrn)
    {   final String time=gettime("hhmmss"),date=gettime("MMdd");
        //Hashtable<Integer,String> msg400tags= build400tags(pan,expdate,amount,trackdata,currencycode,pinblock,emv,receiptnr,rrn,time,date);



        final byte[] msg400=buildISO8583body(build400tags(pan,expdate,amount,trackdata,currencycode,pinblock,emv,receiptnr,rrn,time,date),
                                             msg400presenttags);
        int[] msg400int=new int[msg400.length];
        for(int i=0;i<msg400.length;i++){msg400int[i]=msg400[i]; if(msg400int[i]<0)msg400int[i]+=256;}

        printiso8583(msg400int,"400");


        Thread thread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try {   Thread.sleep(50);
                    Log.d("CONNECTION 1","########SERVER CALL START");






                    SSLSocket socket=getsocket();



                    //SSLSocket socket=(SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket(g_serverip, g_port);
                    Log.d("connect data:","["+g_serverip+":"+g_port+"|"+g_timeout+"]");
                    //socket.setSoTimeout(5000);
                    OutputStream out = socket.getOutputStream();
                    InputStream in = new BufferedInputStream(socket.getInputStream());

                    String success="";

                    out.write(msg400,0,msg400.length);out.flush();
                    Log.d("CONNECTION 2","Sent 400");

                    int[] rec=readresponse(in);
                    if(rec==null)
                    {   Log.d("ERROR"," branch timeout for 400");
                        success="Timeout from server on msg 400.";
                    }
                    else{   Log.d("CONNECTION 3","Received 410 response   len:"+rec.length);
                        printiso8583(rec,"410");
                    }
                    out.close();
                    socket.close();

                    Log.d("tag","########SERVER CALL END");
                }
                catch (IOException | InterruptedException e)
                {   e.printStackTrace();
                    Log.d("tag","#Autoreversal failed, error:"+e.getMessage());
                    OnPrint("#Autoreversal nu a reusit, eroare:"+e.getMessage());
                    //SDKHandler(SDKEvent.PRINT,"#Autoreversal nu a reusit, eroare:"+e.getMessage(),null);
                    Log.d("tag","########SERVER CALL END");
                }
            }
        });
        thread.start();
    }
    public void DeleteBatch()
    {   g_settlement_count=0;
        g_settlement_sum=0;
        g_settlement_batch++;
        g_offlinecount=0;
        updateconfig("offlinecount");
        updateconfig("settlement");
        deletetransactions(false);
        deletetransactions(true);
        Log.d("DELETE BATCH","BATCH WAS DELETED MANUALLY WITHOUT SETTLEMENT");
        OnPrint("Batch was deleted manually.");
    }
    public void DoSettlement()
    {   Thread thread = new Thread(new Runnable() {
        @Override
        public void run()
        {
            try{
                OnSettlementEvent(false,"######## SETTLEMENT START");
                Log.d("CONNN 1","\n######## SETTLEMENT START");
                //SOCKET INIT

                //SSLSocket socket = (SSLSocket) SSLContext.getDefault().getSocketFactory().createSocket(g_serverip, g_port);
                Log.d("connect data:", "[" + g_serverip + ":" + g_port + "|" + g_timeout + "]");
                //socket.setSoTimeout(5000);

                SSLSocket socket=getsocket();
                OutputStream out = socket.getOutputStream();
                InputStream in = new BufferedInputStream(socket.getInputStream());
                String success = "OFFLINE CHECK";
                int[] rec=new int[0];

                //SEND OFFLINE TRANSACTIONS
                if(g_offlinecount>0)
                {   for(int i=0;i<10;i++)
                        {   if(g_offlinecount>0)
                            if(!g_issendingofflinebatch)
                                sendofflinebatch();
                            Thread.sleep(1000);
                            if(g_offlinecount==0){success = "READY";break;}
                        }
                    if(g_offlinecount>0)success="OFFLINE RESEND FAIL";
                }
                else success = "READY";

                //BUILD 320 msgs
                byte[][] msg320=new byte[0][0];
                {   int batchsum=0;
                    String[] transactions=gettransactions(true);
                    msg320=new byte[transactions.length][0];
                    if (g_settlement_count != transactions.length)
                    {   Log.e("ERRORRR!!!!!!", "INVALID SETTLMENT COUNT COMPARED TO STORED MSG:settl:" + g_settlement_count + " msgs:" + transactions.length);
                        OnSettlementEvent(false,"INVALID SETTLMENT COUNT COMPARED TO STORED MSG:settl:" + g_settlement_count + " msgs:" + transactions.length);
                        g_settlement_count=transactions.length;
                        updateconfig("settlement");
                    }
                    for(int i=0;i<transactions.length;i++)if(transactions[i].length()>2)
                    {   String[] lines=transactions[i].split("=");
                        if(lines.length<10){Log.e("invalid transaction ","Invalid decoded transaction("+lines.length+"):"+transactions[i]); continue;}
                        //Hashtable<Integer,String> tags=build320tags(lines[0],lines[1],lines[2],lines[3],lines[4],lines[5],lines[6],lines[7],lines[8],lines[9]);
                        batchsum+=Integer.parseInt(lines[2]);
                        msg320[i]=buildISO8583body(build320tags(lines[0],lines[1],lines[2],lines[3],lines[4],lines[5],lines[6],lines[7],lines[8],lines[9]),
                                                   msg320presenttags);

                        String toprint="";
                        for(int j=0;j<msg320[i].length;j++){int a=msg320[i][j];if(a<0)a+=256;toprint+=a+",";}
                        Log.w("320 "+i,toprint);

                    }
                    Log.w("MSG sum:",batchsum+" "+g_settlement_sum);
                    if(batchsum==0)
                        {   //success="Settlementul nu este necesar, nu sunt tranzactii de trimis.";
                            Log.e("Settlement", "SETTLEMENT CANCELED, no transactions");
                            OnSettlementEvent(true,"######## SETTLEMENT END (Not needed, no transactions to send.)(SUCCESS)");
                            return;
                        }
                    else if (g_settlement_sum != batchsum)
                        {   Log.e("ERRORRR!!!!!!", "INVALID SETTLMENT SUM:settl:" + g_settlement_sum + " msgs:" + batchsum);
                            OnSettlementEvent(false,"INVALID SETTLMENT SUM:settl:" + g_settlement_sum + " msgs:" + batchsum);
                            g_settlement_sum=batchsum;
                            updateconfig("settlement");
                        }
                }

                //SEND 500 INIT
                if(success.equals("READY"))
                {   //g_settlement_sum--;
                    byte[] msg500init = buildISO8583body(build500inittags(), msg500initpresenttags);
                    // g_settlement_sum++;
                    out.write(msg500init, 0, msg500init.length);
                    out.flush();
                    printiso8583(msg500init, "500init");
                    Log.d("CONNECTION 2", "Sent 500init");
                    OnSettlementEvent(false,"Sent 500init");
                    //REC 510
                    rec = readresponse(in);
                    if (rec == null)
                        {  Log.d("ERROR", " branch timeout for 500init");
                            success = "Timeout from server on msg 500init.";
                            OnSettlementEvent(false," branch timeout for 500init");}
                    else {  Log.d("CONNECTION 3", "Received 510init response   len:" + rec.length);
                            OnSettlementEvent(false,"Received 510init response   len:" + rec.length);
                        printiso8583(rec, "510init");
                        Hashtable<Integer, int[]> resulttags = decodeiso8583(rec);
                        if (!resulttags.containsKey(39))
                        {   Log.e("Err", "Missing response for settlement.");
                            OnSettlementEvent(false,"Missing response for settlement.");
                            resulttags.put(39,new int[]{8,8});}
                        else{   int[] code39 = resulttags.get(39);
                            int resultcode = (code39[0] - 48) * 10 + (code39[1] - 48);
                            if (resultcode == 0)
                            {   success = "SUCCESS";
                                Log.d("CONN 4", "Status 00, all OK, end settlement.");
                                OnSettlementEvent(false,"Status 00, all OK, end settlement.");}
                            else {  String toprint1=resultcode+
                                        (errorvaluies.containsKey(resultcode) ? "[" + errorvaluies.get(resultcode) + "]" : "");
                                    Log.d("CONN 4", "Response: " +toprint1);
                                    success = "" + toprint1;
                                    OnSettlementEvent(false,"Response: " +toprint1);

//                                    if(resultcode==96)
//                                        {   Log.d("ERROR", success);
//                                            OnSettlementEvent(true,"######## SETTLMENT END (Falure:"+success+")");
//                                            return;
//
//                                        }
                                 }
                        }
                    }
                }

                Thread.sleep(1000);

                //SEND 320  BATCH
                if (success.startsWith("95"))
                {
                    for(int i=0;i<msg320.length;i++)
                    {   out.write(msg320[i], 0, msg320[i].length);
                        out.flush();
                        Log.d("CONN 4", "Sent 320 " + (i + 1) + "/" + msg320.length);
                        OnSettlementEvent(false,"Sent 320 " + (i + 1) + "/" + msg320.length);
                        printiso8583(msg320[i], "320");
                        //Thread.sleep(400);
                        rec = readresponse(in);
                        if (rec == null)
                        {   Log.d("ERROR", " branch timeout for 330");
                            OnSettlementEvent(false," branch timeout for 330");
                            success = "Timeout from server on msg 330.";
                            OnSettlementEvent(true,"######## SETTLMENT END (Falure:Timeout for transaction "+i+")");
                            return;
                        }
                        Hashtable<Integer,int[]> restags=decodeiso8583(rec);
                        if(restags==null)success="Invalid response for 320.(length<4)";
                        else{   int[] code39=restags.get(39);
                                int resultcode=(code39[0]-48)*10+(code39[1]-48);
                                if(resultcode!=0){success="Error from server at batch 330 "+i+"/"+msg320.length+" code:"+resultcode;}
                            }
                        if(success.startsWith("95"))
                            {   Log.d("CONN 5", "Rec 330 Confirm OK. (" + (i + 1) + "/" + msg320.length + ")");
                                OnSettlementEvent(false,"Rec 330 Confirm OK. (" + (i + 1) + "/" + msg320.length + ")");
                                printiso8583(rec, "330");
                            }
                        else{   Log.d("ERROR", success);
                                OnSettlementEvent(true,"######## SETTLMENT END (Falure:"+success+")");
                                return;
                            }
                        //Thread.sleep(1000);
                    }
                    Log.d("CON 6", "Finished sending transactions.");
                    OnSettlementEvent(false,"Finished sending transactions.");
                    success = "FINISH";
                }
                Thread.sleep(1000);
                if (!success.equals("SUCCESS"))
                {//SEND 500 END
                    byte[] msg500end=buildISO8583body(build500endtags(),msg500endpresenttags);
                    out.write(msg500end,0,msg500end.length);
                    out.flush();
                    printiso8583(msg500end, "500end");
                    Log.d("CONNECTION 7","Sent 500end");
                    OnSettlementEvent(false,"Sent 500end");
                    //REC 510
                    rec=readresponse(in);
                    if(rec==null)
                    {   Log.d("ERROR"," branch timeout for 500end");
                        OnSettlementEvent(false," branch timeout for 500end");
                        success="Timeout from server on msg 500end.";
                    }
                    else{   Log.d("CONN 8","Received 510end response   len:"+rec.length);
                            OnSettlementEvent(false,"Received 510end response   len:"+rec.length);
                            Hashtable<Integer,int[]> restags=decodeiso8583(rec);
                            int[] code39=restags.get(39);
                            int resultcode=(code39[0]-48)*10+(code39[1]-48);
                            if(resultcode==0){success="SUCCESS+RESEND";}
                            else{   Log.d("CONN 4","Response: "+(errorvaluies.containsKey(resultcode)?"["+errorvaluies.get(resultcode)+"]":""));
                                    success=""+resultcode+(errorvaluies.containsKey(resultcode)?"["+errorvaluies.get(resultcode)+"]":"");
                                    OnSettlementEvent(false,"Response: "+(errorvaluies.containsKey(resultcode)?"["+errorvaluies.get(resultcode)+"]":""));
                                }
                            printiso8583(rec,"510end");
                    }
                }
                //CLEARING SETTLEMENT BATCH
                if(success.equals("SUCCESS")||success.equals("SUCCESS+RESEND"))
                {   g_settlement_count=0;
                    g_settlement_sum=0;
                    g_settlement_batch++;
                    updateconfig("settlement");
                    deletetransactions(true);
                    Log.d("CONN 9","RESULT:"+success+", CLEARED SETTLMENT DATA");
                    //OnPrint("######## SETTLMENT END");
                    OnSettlementEvent(true,"######## SETTLMENT END     SUCCESS");
                    //SDKHandler(SDKEvent.PRINT,"######## SETTLMENT END",null);
                }
                else{   Log.d("CONN 9","FAILED REASON:"+success);
                        //OnPrint("######## SETTLMENT END (Falure:"+success+")");
                        OnSettlementEvent(true,"######## SETTLMENT END (Falure:"+success+")");
                        //SDKHandler(SDKEvent.PRINT,"######## SETTLMENT END",null);
                }
            }
            catch (IOException | InterruptedException e)
            {   e.printStackTrace();
                Log.e("Settlement failed","#Settlment failed, error:"+e.getMessage());
                Log.e("Settlement failed","######## SETTLMENT END (Falure:"+e.getMessage()+")");
                OnSettlementEvent(true,"######## SETTLMENT END (Falure:"+e.getMessage()+")");
//                SDKHandler(SDKEvent.PRINT,"#Settlment failed, error:"+e.getMessage(),null);
//                SDKHandler(SDKEvent.PRINT,"######## SETTLMENT END",null);
            }

        }
    });
        thread.start();
    }
    public void OnFiniShedQuestionnaire(Questionnaire q)
    {   String tag63=q.id;
        for(int i=0;i<q.questions.length;i++)
        {   tag63+=((char)0x1C)+q.questions[i].id+((char)0x1D)+q.questions[i].type+((char)0x1D)+q.questions[i].response;
        }

        String len=tag63.length()+"";
        while(len.length()<3)len="0"+len;
        Log.e("QUEST 63/18",len+"18"+tag63+" len of bytes:"+tag63.getBytes().length);
        //Hashtable<Integer,String> msg800questtags= build800questtags(q.amount,len+"18"+tag63);//+"00799Aprobat006SC001SRN003505120025147"
        final byte[] msg800quest=buildISO8583body(build800questtags(q.amount,len+"18"+tag63),msg800questpresenttags);
        int[] msg800int=new int[msg800quest.length];
        for(int i=0;i<msg800int.length;i++){msg800int[i]=msg800quest[i]; if(msg800int[i]<0)msg800int[i]+=256;}
        printiso8583(msg800int,"800 Quest");

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try {   Thread.sleep(50);
                    Log.d("CONNECTION 1","########SERVER CALL START");

                    SSLSocket socket=getsocket();


                    Log.d("connect data:","["+g_serverip+":"+g_port+"|"+g_timeout+"]");
                    //socket.setSoTimeout(5000);
                    OutputStream out = socket.getOutputStream();
                    InputStream in = new BufferedInputStream(socket.getInputStream());

                    out.write(msg800quest,0,msg800quest.length);out.flush();
                    Log.d("CONNECTION 2","Sent 800 questionnaire");

                    int[] rec=readresponse(in);
                    if(rec==null)
                    {   Log.d("ERROR"," branch timeout for 800quest");
                        Log.e("Timeout","Timeout when trying to send questionnaire result.");
                        OnPrint("#Eroare: Nu s-a putut trimite rezultatul chestionarului:");
                        //SDKHandler(SDKEvent.PRINT,"#Eroare: Nu s-a putut trimite rezultatul chestionarului:",null);
                    }
                    else{   Log.d("CONNECTION 3","Received 810 response   len:"+rec.length);
                        printiso8583(rec,"810");
                    }
                    Log.d("tag","########SERVER CALL END");
                }
                catch (IOException | InterruptedException e)
                {   e.printStackTrace();
                    OnPrint("#Eroare:"+e.getMessage());
                    //SDKHandler(SDKEvent.PRINT,"#Eroare:"+e.getMessage(),null);
                    Log.d("tag","#transaction failed, error:"+e.getMessage());
                    Log.d("tag","########SERVER CALL END");
                }
            }
        });
        thread.start();

    }
    int[] readresponse(InputStream in)
    {   try {//LENGTH READ
        byte[] array = new byte[2];
        in.read(array,0,2);
        int l1=(int)array[0],l2=(int)array[1];
        if(l1<0)l1+=256;if(l2<0)l2+=256;
        int len=l1*256+l2+2;
        //RESPONSE READ
        array = new byte[len];
        in.read(array,2,len-2);
        int[] rec=new int[len];
        rec[0]=l1;rec[1]=l2;
        for(int i=2;i<len;i++)
        {   rec[i]=array[i];
            if(rec[i]<0)rec[i]+=256;
        }
        return rec;
    }
    catch (IOException e)
    {   e.printStackTrace();
        OnPrint("Eroare conexiune:"+e.getMessage());
        //SDKHandler(SDKEvent.PRINT,"Eroare conexiune:"+e.getMessage(),null);
        return null;
    }
    }
    SSLSocket getsocket() throws IOException
    {
        SSLSocketFactory socketFactory = new YumiCustomSSLSocketFactory(g_sslcontext.getSocketFactory());
        SSLSocket socket=(SSLSocket) socketFactory.createSocket(g_serverip, g_port);
        socket.setSoTimeout(g_timeout);
        return socket;
    }



}
