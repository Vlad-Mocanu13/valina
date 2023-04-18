package com.worldline.valina.utils;

import android.util.Log;

import java.util.Hashtable;
import java.util.Map;

public class TlvUtils {

    public static Hashtable<String,String> decodeemv(byte[] tlvvalues)
        {   Hashtable<String,String> toreturn= new Hashtable<String, String>();
            StringBuilder sb=new StringBuilder();
            try{    for(int i=0;i<tlvvalues.length;)
                        {   //TAG
                            String tmp=Integer.toHexString(tlvvalues[i] & 0xFF);
                            String tag=tmp.length()<2?"0"+tmp:tmp;
                            boolean isconstructed=(tlvvalues[i]&0x20)==0x20;//6th byte right to left
                            i++;
                            if((tlvvalues[i-1]&0x1F)==0x1F)//if last 5 bits=all 1 then taglength is 1 byte longer
                                {   tmp=Integer.toHexString(tlvvalues[i] & 0xFF);
                                    tag+=tmp.length()<2?"0"+tmp:tmp;
                                    i++;
                                    if((tlvvalues[i-1]&0x80)==0x80)//if first bit=1 then tag length is 1 byte longer
                                        {   tmp=Integer.toHexString(tlvvalues[i] & 0xFF);
                                            tag+=tmp.length()<2?"0"+tmp:tmp;
                                            i++;
                                        }
                                }
                            tag=tag.toUpperCase();
                            //LENGTH
                            int len=0;
                            if((tlvvalues[i] & 0x80) != 0x80)//1 byte len// if starts with 0 bit
                                {   len=tlvvalues[i]<0?tlvvalues[i]+256:tlvvalues[i];
                                    i++;
                                }
                            else if((tlvvalues[i] & 0xFF )== 0x81)//2 byte len
                                {   len=tlvvalues[i+1]<0?tlvvalues[i+1]+256:tlvvalues[i+1];
                                    i+=2;
                                }
                            else if((tlvvalues[i] & 0xFF )== 0x82)//3 byte len
                                {   len=(tlvvalues[i+1]<0?tlvvalues[i+1]+256:tlvvalues[i+1])*256+
                                        (tlvvalues[i+2]<0?tlvvalues[i+2]+256:tlvvalues[i+2]);
                                    //Log.e("LEN3 "+tag," "+Integer.toHexString(tlvvalues[i+1] & 0xFF)+" "+ " "+Integer.toHexString(tlvvalues[i+2] & 0xFF));
                                    i+=3;
                                }
                            else Log.e("INVALID LENGTH",tag+" "+Integer.toHexString(tlvvalues[i] & 0xFF)+" "+ " "+((tlvvalues[i] & 0x80) != 0x80));
                            //VALUE
                            if(isconstructed)
                                {   toreturn.put(tag,"CONSTRUCTED TAG, "+(len<0?len+256:len)+" following bytes.");
                                }
                            else{   sb.setLength(0);
                                    for(int j=0;j<len;j++)
                                        {   String value=Integer.toHexString(tlvvalues[i+j] & 0xFF);
                                            if(value.length()<2)value="0"+value;
                                            sb.append(value.toUpperCase());
                                        }
                                    if(toreturn.containsKey(tag))
                                        {   //Log.e("COLLISSION "+tag,toreturn.get(tag)+" "+tag);
                                            int copyindex=1;
                                            while(toreturn.containsKey(tag+"_"+copyindex))copyindex++;
                                            toreturn.put(tag+"_"+copyindex,sb.toString());
                                        }
                                    else toreturn.put(tag,sb.toString());
                                    i+=len;
                                }
                            //Log.e(""+tag,tag+": isconstructed:"+isconstructed);
                            while(i<tlvvalues.length&&(tlvvalues[i] & 0xFF)==0x00)i++;
                        }
                }
            catch (Exception e)
                {   e.printStackTrace();
                    toreturn.put("ERROR",e.getMessage());
                }
            return toreturn;
        }
    static void printtlv(Hashtable<String, String> table, String title)
        {   for(Map.Entry<String, String> entry1 : table.entrySet())
                Log.d(title,"\n"+entry1.getKey() + ":"  + entry1.getValue());
        }


    public static String buffertostring(byte[] data,String sepparator)
        {   StringBuilder sb = new StringBuilder(data.length*4);
            int maxlength=data.length;
            for(int i=maxlength-1;i>=0;i--)
                if(data[i]==0)maxlength--;
                else break;
            maxlength+=4;
            if(maxlength>data.length)maxlength=data.length;

            for(int i=0;i<maxlength;i++)
                {   String val=Integer.toHexString(data[i] & 0xFF).toUpperCase();
                    if(val.length()<2)val="0"+val;
                    if(val.length()<2)val="0"+val;
                    sb.append(sepparator+val);
                }
            if(maxlength<data.length)sb.append(sepparator+" Ommited last "+(data.length-maxlength)+" zero values out of "+data.length+".");
            return sb.toString();
        }
}
